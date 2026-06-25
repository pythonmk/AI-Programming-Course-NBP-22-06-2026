package pl.nbp.copilot.web;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.Session;
import pl.nbp.copilot.service.ChatService;
import pl.nbp.copilot.session.SessionNotFoundException;
import pl.nbp.copilot.session.SessionRepository;
import pl.nbp.copilot.web.dto.CaseSummaryDto;
import pl.nbp.copilot.web.dto.MessageRequest;
import pl.nbp.copilot.web.dto.MessageView;
import pl.nbp.copilot.web.dto.SessionView;

import java.io.IOException;
import java.util.List;

/**
 * REST controller that handles real-time chat and session-inspection endpoints.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/sessions/{id}/messages} — streams an assistant reply as
 *       Server-Sent Events over a virtual thread.</li>
 *   <li>{@code GET /api/sessions/{id}} — returns the current session view including
 *       messages and the current decision category.</li>
 * </ul>
 *
 * <h2>Error handling — SSE endpoint</h2>
 * Because {@link SseEmitter} is returned before the virtual thread executes,
 * the HTTP status is always {@code 200} for {@code POST /messages}. Errors are
 * communicated through SSE frames:
 * <ul>
 *   <li>{@link SessionNotFoundException} — an error JSON frame is emitted, then
 *       {@link SseEmitter#completeWithError(Throwable)} is called.</li>
 *   <li>Other exceptions — {@link SseEmitter#completeWithError(Throwable)} is called.</li>
 * </ul>
 *
 * <p>{@link jakarta.validation.Valid} failures on {@code @RequestBody}
 * (e.g. blank {@code message}) are handled synchronously by
 * {@link GlobalExceptionHandler} before the emitter is returned, and produce a
 * {@code 400 Bad Request} response.
 */
@RestController
@RequestMapping("/api/sessions")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final SessionRepository sessionRepository;

    /**
     * Constructs the controller with its required collaborators.
     *
     * @param chatService       handles one chat turn for a session
     * @param sessionRepository session store for retrieving sessions
     */
    public ChatController(ChatService chatService, SessionRepository sessionRepository) {
        this.chatService = chatService;
        this.sessionRepository = sessionRepository;
    }

    // -----------------------------------------------------------------------
    // POST /api/sessions/{id}/messages
    // -----------------------------------------------------------------------

    /**
     * Handles a chat message for the given session and streams the assistant reply
     * as Server-Sent Events.
     *
     * <p>The emitter is returned immediately; the actual LLM call runs on a virtual
     * thread so the servlet thread is not blocked. The SSE wire format (frozen —
     * must match the frontend exactly):
     * <pre>{@code
     * Token:        data:{"t":"<delta>"}\n\n
     * State change: data:{"decisionCategory":"ESCALATE"}\n\n
     * Terminal:     data:[DONE]\n\n
     * }</pre>
     *
     * @param sessionId the session to chat in
     * @param request   validated request body containing the user message
     * @return an {@link SseEmitter} that will receive the streamed reply
     */
    @PostMapping(
            value = "/{sessionId}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter sendMessage(
            @PathVariable String sessionId,
            @RequestBody @Valid MessageRequest request
    ) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = no timeout

        // Run on a virtual thread so the servlet thread is not blocked during streaming
        Thread.ofVirtual().start(() -> {
            try {
                chatService.chat(sessionId, request.message(), emitter);
            } catch (SessionNotFoundException e) {
                log.warn("Session not found during chat: {}", sessionId);
                // Cannot return a 404 HTTP status from the async thread —
                // the response has already been committed. Emit an error frame
                // and then close the stream.
                try {
                    emitter.send(SseEmitter.event()
                            .data("{\"error\":{\"code\":\"SESSION_NOT_FOUND\",\"messagePl\":\"Sesja nie istnieje lub wygasła.\"}}"));
                } catch (IOException ex) {
                    log.debug("Could not send SESSION_NOT_FOUND error frame for session {}", sessionId, ex);
                }
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("Unexpected error during chat for session {}", sessionId, e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // -----------------------------------------------------------------------
    // GET /api/sessions/{id}
    // -----------------------------------------------------------------------

    /**
     * Returns the current state of the given session.
     *
     * <p>Includes the session identifier, the current (possibly updated) decision
     * category, the full message history, and a compact case summary for the
     * chat-screen header.
     *
     * @param sessionId the session identifier
     * @return a {@link SessionView} DTO
     * @throws SessionNotFoundException if the session does not exist or has expired
     */
    @GetMapping("/{sessionId}")
    public SessionView getSession(@PathVariable String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        return toView(session);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Converts a {@link Session} domain object into a {@link SessionView} DTO.
     *
     * @param session the session to convert, must not be {@code null}
     * @return the session view
     */
    private SessionView toView(Session session) {
        List<MessageView> messageViews = session.getMessages().stream()
                .filter(msg -> msg.role() != ChatMessage.Role.SYSTEM)
                .map(msg -> new MessageView(msg.role().name(), msg.content()))
                .toList();

        CaseSummaryDto caseSummary = new CaseSummaryDto(
                session.getCaseData().requestType().name(),
                session.getCaseData().equipmentCategory().name(),
                session.getCaseData().equipmentName(),
                session.getCurrentDecisionCategory().name()
        );

        return new SessionView(
                session.getSessionId(),
                session.getCurrentDecisionCategory().name(),
                messageViews,
                caseSummary
        );
    }
}
