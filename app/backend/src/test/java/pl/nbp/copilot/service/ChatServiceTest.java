package pl.nbp.copilot.service;

import com.openai.core.http.AsyncStreamResponse;
import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.async.chat.ChatCompletionServiceAsync;
import com.openai.services.async.ChatServiceAsync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.config.AppProperties;
import pl.nbp.copilot.domain.*;
import pl.nbp.copilot.llm.PromptBuilder;
import pl.nbp.copilot.policy.PolicyProvider;
import pl.nbp.copilot.session.SessionNotFoundException;
import pl.nbp.copilot.session.SessionRepository;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChatService}.
 *
 * <p>All collaborators are mocked with Mockito. The {@link OpenAIClientAsync}
 * call chain is stubbed to replay a pre-built {@link ChatCompletionChunk} via
 * a captured {@link AsyncStreamResponse.Handler}. SSE emission is intercepted
 * by a {@link RecordingSseEmitter} subclass that collects sent data strings.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService — streaming chat with escalation detection")
class ChatServiceTest {

    // -----------------------------------------------------------------------
    // Mocks
    // -----------------------------------------------------------------------

    @Mock
    private OpenAIClientAsync asyncClient;

    @Mock
    private ChatServiceAsync chatServiceAsync;

    @Mock
    private ChatCompletionServiceAsync completionServiceAsync;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private PolicyProvider policyProvider;

    @Mock
    private AppProperties appProperties;

    // -----------------------------------------------------------------------
    // Subject under test
    // -----------------------------------------------------------------------

    private ChatService chatService;

    // -----------------------------------------------------------------------
    // Shared fixtures
    // -----------------------------------------------------------------------

    private static final String SESSION_ID = "sess-001";
    private static final String USER_TEXT  = "Mam dodatkowe dowody uszkodzenia.";

    @BeforeEach
    void setUp() {
        AppProperties.Llm llmProps = new AppProperties.Llm(
                "anthropic/claude-sonnet-4-6",
                "anthropic/claude-sonnet-4-6"
        );
        // lenient: appProperties.llm() is not called in tests that throw before building params
        lenient().when(appProperties.llm()).thenReturn(llmProps);

        // lenient: async client chain is not reached in tests 1 and 2 which throw early
        lenient().when(asyncClient.chat()).thenReturn(chatServiceAsync);
        lenient().when(chatServiceAsync.completions()).thenReturn(completionServiceAsync);

        chatService = new ChatService(
                asyncClient, sessionRepository, promptBuilder, policyProvider, appProperties
        );
    }

    // -----------------------------------------------------------------------
    // Test 1: SessionNotFoundException when session not found
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Throws SessionNotFoundException when session does not exist")
    void chat_unknownSession_throwsSessionNotFoundException() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.chat(SESSION_ID, USER_TEXT, new RecordingSseEmitter()))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining(SESSION_ID);
    }

    // -----------------------------------------------------------------------
    // Test 2: IllegalArgumentException when userText is blank
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Throws IllegalArgumentException when userText is blank")
    void chat_blankUserText_throwsIllegalArgumentException() {
        Session session = buildSession(DecisionCategory.REJECT);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> chatService.chat(SESSION_ID, "   ", new RecordingSseEmitter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    // -----------------------------------------------------------------------
    // Test 3: User message is saved to session before streaming
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("User message is saved to session before the LLM call")
    void chat_savesUserMessageBeforeStreaming() {
        Session session = buildSession(DecisionCategory.REJECT);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubPolicyProvider();
        stubPromptBuilder();
        stubAsyncStream("Odpowiedź asystenta.");

        chatService.chat(SESSION_ID, USER_TEXT, new RecordingSseEmitter());

        // Capture all save calls
        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository, atLeast(1)).save(captor.capture());

        // The first save must contain the USER message
        Session firstSaved = captor.getAllValues().get(0);
        boolean userMsgPresent = firstSaved.getMessages().stream()
                .anyMatch(m -> m.role() == ChatMessage.Role.USER
                        && m.content().equals(USER_TEXT));
        assertThat(userMsgPresent)
                .as("First saved session must contain the user message")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Test 4: Reply tokens are emitted as {"t":"..."} SSE data frames
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Tokens are emitted as {\"t\":\"...\"} SSE frames")
    void chat_emitsTokenFrames() {
        Session session = buildSession(DecisionCategory.REJECT);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubPolicyProvider();
        stubPromptBuilder();
        stubAsyncStream("Odpowiedź.");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        chatService.chat(SESSION_ID, USER_TEXT, emitter);

        boolean hasTokenFrame = emitter.data().stream()
                .anyMatch(d -> d.startsWith("{\"t\":"));
        assertThat(hasTokenFrame).as("At least one {\"t\":...} frame must be emitted").isTrue();
    }

    // -----------------------------------------------------------------------
    // Test 5: [DONE] is emitted as the terminal frame
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("[DONE] is emitted as the last SSE frame")
    void chat_emitsDoneFrame() {
        Session session = buildSession(DecisionCategory.REJECT);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubPolicyProvider();
        stubPromptBuilder();
        stubAsyncStream("Odpowiedź.");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        chatService.chat(SESSION_ID, USER_TEXT, emitter);

        List<String> data = emitter.data();
        assertThat(data).isNotEmpty();
        assertThat(data.get(data.size() - 1))
                .as("Last emitted frame must be [DONE]")
                .isEqualTo("[DONE]");
    }

    // -----------------------------------------------------------------------
    // Test 6: ##ESCALATE## marker is stripped from the visible reply
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("##ESCALATE## marker is stripped from the visible reply text")
    void chat_escalateMarkerStrippedFromReply() {
        Session session = buildSession(DecisionCategory.REJECT);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubPolicyProvider();
        stubPromptBuilder();
        stubAsyncStream("Rozumiem Twój problem.\n##ESCALATE##\nPrzekazuję do konsultanta.");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        chatService.chat(SESSION_ID, USER_TEXT, emitter);

        // All token frames combined must not contain the marker
        String allTokenContent = emitter.data().stream()
                .filter(d -> d.startsWith("{\"t\":"))
                .collect(Collectors.joining());

        assertThat(allTokenContent)
                .as("Visible reply tokens must not contain ##ESCALATE##")
                .doesNotContain("##ESCALATE##");
    }

    // -----------------------------------------------------------------------
    // Test 7: REJECT + ##ESCALATE## → {"decisionCategory":"ESCALATE"} frame emitted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("REJECT + ##ESCALATE## → decisionCategory:ESCALATE state frame emitted")
    void chat_escalateMarker_rejectToEscalate_emitsStateFrame() {
        Session session = buildSession(DecisionCategory.REJECT);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubPolicyProvider();
        stubPromptBuilder();
        stubAsyncStream("Decyzja zmieniona.\n##ESCALATE##");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        chatService.chat(SESSION_ID, USER_TEXT, emitter);

        boolean hasEscalateFrame = emitter.data().stream()
                .anyMatch(d -> d.equals("{\"decisionCategory\":\"ESCALATE\"}"));
        assertThat(hasEscalateFrame)
                .as("Must emit {\"decisionCategory\":\"ESCALATE\"} when REJECT + ##ESCALATE##")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Test 8: APPROVE + ##ESCALATE## → NO decisionCategory frame emitted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("APPROVE + ##ESCALATE## → no decisionCategory frame (APPROVE→ESCALATE forbidden)")
    void chat_escalateMarker_approveUnchanged_noStateFrame() {
        Session session = buildSession(DecisionCategory.APPROVE);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubPolicyProvider();
        stubPromptBuilder();
        stubAsyncStream("Informacja.\n##ESCALATE##");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        chatService.chat(SESSION_ID, USER_TEXT, emitter);

        boolean hasEscalateFrame = emitter.data().stream()
                .anyMatch(d -> d.equals("{\"decisionCategory\":\"ESCALATE\"}"));
        assertThat(hasEscalateFrame)
                .as("Must NOT emit decisionCategory frame when current decision is APPROVE")
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // Test 9: Assistant reply (without marker) is saved to session
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Assistant reply (without ##ESCALATE##) is saved to session after streaming")
    void chat_assistantReplySavedWithoutMarker() {
        Session session = buildSession(DecisionCategory.REJECT);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubPolicyProvider();
        stubPromptBuilder();
        stubAsyncStream("Odpowiedź asystenta.\n##ESCALATE##\nDodatkowy tekst.");

        chatService.chat(SESSION_ID, USER_TEXT, new RecordingSseEmitter());

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository, atLeast(2)).save(captor.capture());

        // Find the save that contains an ASSISTANT message
        String assistantContent = captor.getAllValues().stream()
                .flatMap(s -> s.getMessages().stream())
                .filter(m -> m.role() == ChatMessage.Role.ASSISTANT)
                .map(ChatMessage::content)
                .findFirst()
                .orElse(null);

        assertThat(assistantContent)
                .as("Saved assistant reply must not contain ##ESCALATE## marker")
                .isNotNull()
                .doesNotContain("##ESCALATE##");
    }

    // -----------------------------------------------------------------------
    // Test 10: emitter.completeWithError() called on streaming exception
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("emitter.completeWithError() is called when streaming throws an exception")
    void chat_streamingException_callsCompleteWithError() {
        Session session = buildSession(DecisionCategory.REJECT);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubPolicyProvider();
        stubPromptBuilder();

        RuntimeException streamError = new RuntimeException("LLM stream failed");
        when(completionServiceAsync.createStreaming(any(ChatCompletionCreateParams.class))).thenThrow(streamError);

        SseEmitter spyEmitter = spy(new SseEmitter());
        chatService.chat(SESSION_ID, USER_TEXT, spyEmitter);

        verify(spyEmitter).completeWithError(streamError);
    }

    // -----------------------------------------------------------------------
    // Stub helpers
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void stubAsyncStream(String fullText) {
        ChatCompletionChunk chunk = buildChunk(fullText);

        AsyncStreamResponse<ChatCompletionChunk> asyncStreamResponse = mock(AsyncStreamResponse.class);
        when(completionServiceAsync.createStreaming(any(ChatCompletionCreateParams.class))).thenReturn(asyncStreamResponse);

        // Drive handler synchronously on subscribe
        when(asyncStreamResponse.subscribe(any(AsyncStreamResponse.Handler.class))).thenAnswer(inv -> {
            AsyncStreamResponse.Handler<ChatCompletionChunk> handler = inv.getArgument(0);
            handler.onNext(chunk);
            handler.onComplete(Optional.empty());
            return asyncStreamResponse;
        });

        when(asyncStreamResponse.onCompleteFuture())
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void stubPolicyProvider() {
        when(policyProvider.getPolicyDocument(any())).thenReturn("Polityka serwisu.");
        when(policyProvider.getLegalRules()).thenReturn("Przepisy prawne.");
    }

    private void stubPromptBuilder() {
        when(promptBuilder.buildChatSystemPrompt(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    // -----------------------------------------------------------------------
    // Fixture builders
    // -----------------------------------------------------------------------

    private static Session buildSession(DecisionCategory category) {
        CaseData caseData = new CaseData(
                RequestType.COMPLAINT,
                EquipmentCategory.LAPTOP,
                "Dell XPS 15",
                LocalDate.of(2025, 3, 10),
                "Ekran nie działa."
        );
        ImageAnalysis analysis = new ImageAnalysis(
                TriState.TRUE, "pęknięcie", "ekran", "wada produkcyjna",
                null, null, null, null,
                Confidence.HIGH, "Uszkodzony ekran."
        );
        Decision decision = new Decision(
                category, "Uzasadnienie.", "Następne kroki.", List.of(), "Pierwsza wiadomość."
        );
        return new Session(
                SESSION_ID, caseData,
                new EligibilityWindows(100, true, true),
                analysis, decision, category,
                Instant.now(), Instant.now().plusSeconds(3600)
        );
    }

    private static ChatCompletionChunk buildChunk(String content) {
        String json = """
                {
                  "id": "chunk-1",
                  "object": "chat.completion.chunk",
                  "created": 1700000000,
                  "model": "anthropic/claude-sonnet-4-6",
                  "choices": [{
                    "index": 0,
                    "delta": { "role": "assistant", "content": %s },
                    "finish_reason": null
                  }]
                }
                """.formatted(toJsonString(content));
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, ChatCompletionChunk.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build ChatCompletionChunk", e);
        }
    }

    private static String toJsonString(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // -----------------------------------------------------------------------
    // RecordingSseEmitter — captures data payloads sent via SseEmitter
    // -----------------------------------------------------------------------

    /**
     * An {@link SseEmitter} subclass that records every data string emitted
     * via {@link #send(SseEmitter.SseEventBuilder)}.
     *
     * <p>Uses {@link SseEmitter.SseEventBuilder#build()} to extract the
     * {@link org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType}
     * items, then calls {@code getData()} on each to get the payload object.
     *
     * <p>Does NOT call {@code super.send()} to avoid
     * "ResponseBodyEmitter is already complete" errors in a test context where
     * there is no real HTTP response.
     */
    static class RecordingSseEmitter extends SseEmitter {

        private final List<String> recorded = new ArrayList<>();

        /** Returns the list of data strings in the order they were emitted. */
        List<String> data() {
            return recorded;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            // build() returns Set<DataWithMediaType>.
            // The SSE format inserts "data:" prefix (TEXT_PLAIN) and "\n\n" separator (TEXT_PLAIN).
            // The actual payload items have getMediaType() == null.
            builder.build().forEach(item -> {
                if (item.getMediaType() == null) {
                    Object val = item.getData();
                    if (val != null) {
                        recorded.add(val.toString());
                    }
                }
            });
            // Do NOT call super.send() — no real HTTP response in test context.
        }

        @Override
        public void complete() {
            // no-op in test context
        }

        @Override
        public void completeWithError(Throwable ex) {
            // no-op in test context (test_10 uses a spy on a real SseEmitter)
        }
    }
}
