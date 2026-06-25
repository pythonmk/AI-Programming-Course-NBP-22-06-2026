package pl.nbp.copilot.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.domain.CaseData;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.Decision;
import pl.nbp.copilot.domain.DecisionCategory;
import pl.nbp.copilot.domain.EligibilityWindows;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.ImageAnalysis;
import pl.nbp.copilot.domain.RequestType;
import pl.nbp.copilot.domain.Session;
import pl.nbp.copilot.service.ChatService;
import pl.nbp.copilot.session.SessionNotFoundException;
import pl.nbp.copilot.session.SessionRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link ChatController}.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer. {@link ChatService} and
 * {@link SessionRepository} are mocked with {@code @MockBean}.
 */
@WebMvcTest(ChatController.class)
@DisplayName("ChatController")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private SessionRepository sessionRepository;

    // -----------------------------------------------------------------------
    // POST /api/sessions/{id}/messages — happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /messages returns 200 with text/event-stream content type")
    void sendMessage_validRequest_returns200WithSseContentType() throws Exception {
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            emitter.complete();
            return null;
        }).when(chatService).chat(eq("session-1"), eq("Dzień dobry"), any(SseEmitter.class));

        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Dzień dobry"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    @DisplayName("POST /messages — ChatService.chat() completes normally, emitter is completed")
    void sendMessage_chatServiceCompletesNormally_emitterCompletes() throws Exception {
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            emitter.complete();
            return null;
        }).when(chatService).chat(eq("session-1"), eq("Hello"), any(SseEmitter.class));

        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Hello"}
                                """))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // POST /api/sessions/{id}/messages — validation failures (400)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /messages returns 400 when message is blank")
    void sendMessage_blankMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /messages returns 400 when message field is missing")
    void sendMessage_missingMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /messages returns 400 when message is whitespace only")
    void sendMessage_whitespaceMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // -----------------------------------------------------------------------
    // POST /api/sessions/{id}/messages — SessionNotFoundException → 404
    // (tested via GlobalExceptionHandler, which handles the synchronous path)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GlobalExceptionHandler handles SessionNotFoundException with 404 and SESSION_NOT_FOUND code")
    void sessionNotFoundException_handledWith404() throws Exception {
        // Verify that GlobalExceptionHandler correctly maps SessionNotFoundException
        // to 404. We test this via the standalone approach consistent with
        // GlobalExceptionHandlerTest — but here we verify via WebMvcTest by
        // triggering the exception synchronously through a @GetMapping handler.
        // The GET endpoint itself exercises the same handler.
        when(sessionRepository.findById("missing-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/missing-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.error.messagePl").value("Sesja nie istnieje lub wygasła."));
    }

    // -----------------------------------------------------------------------
    // GET /api/sessions/{id} — happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /sessions/{id} returns 200 with sessionId field")
    void getSession_existingSession_returns200WithSessionId() throws Exception {
        Session session = buildStubSession("session-abc", DecisionCategory.APPROVE);
        when(sessionRepository.findById("session-abc")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/session-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-abc"));
    }

    @Test
    @DisplayName("GET /sessions/{id} returns decisionCategory matching current session decision")
    void getSession_existingSession_returnsDecisionCategory() throws Exception {
        Session session = buildStubSession("session-xyz", DecisionCategory.REJECT);
        when(sessionRepository.findById("session-xyz")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/session-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionCategory").value("REJECT"));
    }

    @Test
    @DisplayName("GET /sessions/{id} returns 200 with empty messages list when no messages")
    void getSession_existingSession_returnsEmptyMessages() throws Exception {
        Session session = buildStubSession("session-1", DecisionCategory.APPROVE);
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages.length()").value(0));
    }

    @Test
    @DisplayName("GET /sessions/{id} returns messages with correct role and content fields")
    void getSession_sessionWithMessages_returnsMessagesWithRoleAndContent() throws Exception {
        Session session = buildStubSession("session-2", DecisionCategory.APPROVE);
        session.addMessage(new ChatMessage(ChatMessage.Role.USER, "Mam pytanie", Instant.now()));
        session.addMessage(new ChatMessage(ChatMessage.Role.ASSISTANT, "Oczywiście, pomogę Panu.", Instant.now()));
        when(sessionRepository.findById("session-2")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/session-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("Mam pytanie"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].content").value("Oczywiście, pomogę Panu."));
    }

    // -----------------------------------------------------------------------
    // GET /api/sessions/{id} — not found (404)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /sessions/{id} returns 404 when session does not exist")
    void getSession_sessionNotFound_returns404() throws Exception {
        when(sessionRepository.findById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    // -----------------------------------------------------------------------
    // GET /api/sessions/{id} — caseSummary field
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /sessions/{id} returns caseSummary with correct fields")
    void getSession_existingSession_returnsCaseSummary() throws Exception {
        Session session = buildStubSession("session-3", DecisionCategory.ESCALATE);
        when(sessionRepository.findById("session-3")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/session-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseSummary").exists())
                .andExpect(jsonPath("$.caseSummary.requestType").value("COMPLAINT"))
                .andExpect(jsonPath("$.caseSummary.equipmentCategory").value("LAPTOP"))
                .andExpect(jsonPath("$.caseSummary.equipmentName").value("Test Laptop"))
                .andExpect(jsonPath("$.caseSummary.decisionCategory").value("ESCALATE"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a minimal {@link Session} stub suitable for controller tests.
     *
     * @param sessionId          the session id
     * @param decisionCategory   the current decision category
     * @return a fully-constructed, non-null session
     */
    private static Session buildStubSession(String sessionId, DecisionCategory decisionCategory) {
        CaseData caseData = new CaseData(
                RequestType.COMPLAINT,
                EquipmentCategory.LAPTOP,
                "Test Laptop",
                LocalDate.now().minusMonths(3),
                "Test reason"
        );
        EligibilityWindows windows = new EligibilityWindows(90, true, true);
        ImageAnalysis imageAnalysis = new ImageAnalysis(
                null, null, null, null, null, null, null, null, null, "Test image analysis");
        Decision decision = new Decision(
                decisionCategory,
                "Test justification",
                "Test next steps",
                List.of(),
                "## Wiadomość testowa"
        );
        return new Session(
                sessionId,
                caseData,
                windows,
                imageAnalysis,
                decision,
                decisionCategory,
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
    }
}
