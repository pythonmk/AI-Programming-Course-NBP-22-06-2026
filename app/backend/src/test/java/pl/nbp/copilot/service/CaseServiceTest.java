package pl.nbp.copilot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nbp.copilot.domain.*;
import pl.nbp.copilot.image.ImageCompressor;
import pl.nbp.copilot.image.ImageTooLargeException;
import pl.nbp.copilot.llm.LlmClient;
import pl.nbp.copilot.llm.LlmParseException;
import pl.nbp.copilot.policy.PolicyProvider;
import pl.nbp.copilot.session.SessionRepository;
import pl.nbp.copilot.web.dto.CaseResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CaseService} orchestration logic.
 * All collaborators are mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private ImageCompressor imageCompressor;

    @Mock
    private PolicyProvider policyProvider;

    @Mock
    private EligibilityService eligibilityService;

    @Mock
    private SessionRepository sessionRepository;

    private CaseService caseService;

    // --- shared test fixtures ---

    private static final byte[] FAKE_IMAGE_BYTES = new byte[]{1, 2, 3};
    private static final String FAKE_DATA_URL = "data:image/jpeg;base64,AAEC";

    private static final ImageAnalysis FAKE_ANALYSIS = new ImageAnalysis(
            TriState.TRUE,
            "pęknięcie obudowy",
            "narożnik lewy dolny",
            "uszkodzenie mechaniczne",
            TriState.FALSE,
            null,
            null,
            null,
            Confidence.HIGH,
            "Urządzenie wykazuje uszkodzenie mechaniczne obudowy."
    );

    private static final EligibilityWindows FAKE_WINDOWS = new EligibilityWindows(
            200, false, true
    );

    private static final String FAKE_POLICY = "Regulamin reklamacji - treść przykładowa.";
    private static final String FAKE_LEGAL = "Przepisy prawne - podsumowanie.";

    @BeforeEach
    void setUp() {
        caseService = new CaseService(
                llmClient, imageCompressor, policyProvider, eligibilityService, sessionRepository
        );
    }

    // -----------------------------------------------------------------------
    // Happy path: COMPLAINT → APPROVE
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Happy path COMPLAINT→APPROVE: CaseResult has non-null sessionId and category APPROVE")
    void processCase_complaintApprove_returnsApproveResult() {
        // Arrange
        CaseData caseData = complaintCaseData();
        Decision decision = decision(DecisionCategory.APPROVE,
                "Reklamacja spełnia warunki polityki.", "Skontaktuj się z serwisem.");

        stubCollaborators(caseData, decision);

        // Act
        CaseResult result = caseService.processCase(caseData, FAKE_IMAGE_BYTES);

        // Assert
        assertThat(result.sessionId()).isNotNull();
        assertThat(result.decisionCategory()).isEqualTo(DecisionCategory.APPROVE);
    }

    @Test
    @DisplayName("Happy path RETURN→REJECT: firstMessageMarkdown contains 'Odrzucono'")
    void processCase_returnReject_firstMessageContainsOdrzucono() {
        // Arrange
        CaseData caseData = returnCaseData();
        Decision decision = decision(DecisionCategory.REJECT,
                "Zwrot poza oknem 14-dniowym.", "Złóż reklamację.");

        stubCollaborators(caseData, decision);

        // Act
        CaseResult result = caseService.processCase(caseData, FAKE_IMAGE_BYTES);

        // Assert
        assertThat(result.firstMessageMarkdown()).contains("Odrzucono");
    }

    @Test
    @DisplayName("ESCALATE decision: firstMessageMarkdown contains 'Przekazanie do konsultanta'")
    void processCase_escalate_firstMessageContainsEscalateLabel() {
        // Arrange
        CaseData caseData = complaintCaseData();
        Decision decision = decision(DecisionCategory.ESCALATE,
                "Wymagana weryfikacja konsultanta.", "Poczekaj na kontakt.");

        stubCollaborators(caseData, decision);

        // Act
        CaseResult result = caseService.processCase(caseData, FAKE_IMAGE_BYTES);

        // Assert
        assertThat(result.firstMessageMarkdown()).contains("Przekazanie do konsultanta");
    }

    // -----------------------------------------------------------------------
    // firstMessageMarkdown content
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("firstMessageMarkdown contains mandatory disclaimer text")
    void processCase_firstMessage_containsMandatoryDisclaimer() {
        // Arrange
        CaseData caseData = complaintCaseData();
        Decision decision = decision(DecisionCategory.APPROVE,
                "Uzasadnienie decyzji.", "Następne kroki.");

        stubCollaborators(caseData, decision);

        // Act
        CaseResult result = caseService.processCase(caseData, FAKE_IMAGE_BYTES);

        // Assert: must contain a key substring from the mandatory PRD §11.6 disclaimer
        assertThat(result.firstMessageMarkdown())
                .contains("charakter wstępny");
    }

    @Test
    @DisplayName("firstMessageMarkdown contains the decision justification")
    void processCase_firstMessage_containsJustification() {
        // Arrange
        CaseData caseData = complaintCaseData();
        String justification = "Uzasadnienie: produkt niespełniający normy.";
        Decision decision = decision(DecisionCategory.APPROVE, justification, "Następne kroki.");

        stubCollaborators(caseData, decision);

        // Act
        CaseResult result = caseService.processCase(caseData, FAKE_IMAGE_BYTES);

        // Assert
        assertThat(result.firstMessageMarkdown()).contains(justification);
    }

    @Test
    @DisplayName("firstMessageMarkdown contains the next steps text")
    void processCase_firstMessage_containsNextSteps() {
        // Arrange
        CaseData caseData = complaintCaseData();
        String nextSteps = "Następne kroki: skontaktuj się z serwisem pod numerem 123.";
        Decision decision = decision(DecisionCategory.APPROVE, "Uzasadnienie.", nextSteps);

        stubCollaborators(caseData, decision);

        // Act
        CaseResult result = caseService.processCase(caseData, FAKE_IMAGE_BYTES);

        // Assert
        assertThat(result.firstMessageMarkdown()).contains(nextSteps);
    }

    // -----------------------------------------------------------------------
    // Session persistence
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("sessionRepository.save() is called exactly once")
    void processCase_savesSessionExactlyOnce() {
        // Arrange
        CaseData caseData = complaintCaseData();
        Decision decision = decision(DecisionCategory.APPROVE, "Uzasadnienie.", "Kroki.");

        stubCollaborators(caseData, decision);

        // Act
        caseService.processCase(caseData, FAKE_IMAGE_BYTES);

        // Assert
        verify(sessionRepository, times(1)).save(any(Session.class));
    }

    @Test
    @DisplayName("Saved session has one ASSISTANT ChatMessage with content == firstMessageMarkdown")
    void processCase_savedSession_hasAssistantMessageWithFirstMessage() {
        // Arrange
        CaseData caseData = complaintCaseData();
        Decision decision = decision(DecisionCategory.APPROVE, "Uzasadnienie.", "Kroki.");

        stubCollaborators(caseData, decision);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);

        // Act
        CaseResult result = caseService.processCase(caseData, FAKE_IMAGE_BYTES);

        // Assert
        verify(sessionRepository).save(sessionCaptor.capture());
        Session savedSession = sessionCaptor.getValue();

        assertThat(savedSession.getMessages()).hasSize(1);
        ChatMessage firstMsg = savedSession.getMessages().get(0);
        assertThat(firstMsg.role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(firstMsg.content()).isEqualTo(result.firstMessageMarkdown());
    }

    // -----------------------------------------------------------------------
    // Exception propagation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ImageTooLargeException from imageCompressor propagates out of processCase")
    void processCase_imageTooLarge_propagatesException() {
        // Arrange
        CaseData caseData = complaintCaseData();
        when(imageCompressor.compress(FAKE_IMAGE_BYTES))
                .thenThrow(new ImageTooLargeException(6_000_000L, 5_242_880L));

        // Act & Assert
        assertThatThrownBy(() -> caseService.processCase(caseData, FAKE_IMAGE_BYTES))
                .isInstanceOf(ImageTooLargeException.class);
    }

    @Test
    @DisplayName("LlmParseException from llmClient.decide propagates out of processCase")
    void processCase_llmParseExceptionOnDecide_propagatesException() {
        // Arrange
        CaseData caseData = complaintCaseData();

        when(imageCompressor.compress(FAKE_IMAGE_BYTES)).thenReturn(FAKE_DATA_URL);
        when(llmClient.analyzeImage(any(), any(), any())).thenReturn(FAKE_ANALYSIS);
        when(eligibilityService.compute(any())).thenReturn(FAKE_WINDOWS);
        when(policyProvider.getPolicyDocument(any())).thenReturn(FAKE_POLICY);
        when(policyProvider.getLegalRules()).thenReturn(FAKE_LEGAL);
        when(llmClient.decide(any(), any(), any(), any(), any(), any()))
                .thenThrow(new LlmParseException("Nie można sparsować odpowiedzi LLM"));

        // Act & Assert
        assertThatThrownBy(() -> caseService.processCase(caseData, FAKE_IMAGE_BYTES))
                .isInstanceOf(LlmParseException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private CaseData complaintCaseData() {
        return new CaseData(
                RequestType.COMPLAINT,
                EquipmentCategory.LAPTOP,
                "Dell XPS 15",
                LocalDate.of(2025, 6, 1),
                "Klawiatura przestała działać po 3 miesiącach użytkowania."
        );
    }

    private CaseData returnCaseData() {
        return new CaseData(
                RequestType.RETURN,
                EquipmentCategory.MONITOR,
                "LG 27UK850",
                LocalDate.of(2026, 6, 20),
                null
        );
    }

    private Decision decision(DecisionCategory category, String justification, String nextSteps) {
        return new Decision(category, justification, nextSteps, List.of("§1", "§2"), "");
    }

    /**
     * Stubs all collaborators for a standard successful flow.
     */
    private void stubCollaborators(CaseData caseData, Decision decision) {
        when(imageCompressor.compress(FAKE_IMAGE_BYTES)).thenReturn(FAKE_DATA_URL);
        when(llmClient.analyzeImage(eq(caseData.requestType()), eq(caseData), eq(FAKE_DATA_URL)))
                .thenReturn(FAKE_ANALYSIS);
        when(eligibilityService.compute(caseData.purchaseDate())).thenReturn(FAKE_WINDOWS);
        when(policyProvider.getPolicyDocument(caseData.requestType())).thenReturn(FAKE_POLICY);
        when(policyProvider.getLegalRules()).thenReturn(FAKE_LEGAL);
        when(llmClient.decide(
                eq(caseData.requestType()),
                eq(caseData),
                eq(FAKE_ANALYSIS),
                eq(FAKE_WINDOWS),
                eq(FAKE_POLICY),
                eq(FAKE_LEGAL)
        )).thenReturn(decision);

        // sessionRepository.save returns a session stamped with an ID
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            return s.withIdAndTimestamps(
                    "test-session-id-" + System.nanoTime(),
                    Instant.now(),
                    Instant.now().plusSeconds(3600)
            );
        });
    }
}
