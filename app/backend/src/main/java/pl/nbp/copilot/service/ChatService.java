package pl.nbp.copilot.service;

import com.openai.client.OpenAIClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.config.AppProperties;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.DecisionCategory;
import pl.nbp.copilot.domain.Session;
import pl.nbp.copilot.llm.PromptBuilder;
import pl.nbp.copilot.policy.PolicyProvider;
import pl.nbp.copilot.session.SessionNotFoundException;
import pl.nbp.copilot.session.SessionRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles a single chat turn for a session.
 *
 * <p>For each turn the service:
 * <ol>
 *   <li>Loads and validates the session.</li>
 *   <li>Persists the user message.</li>
 *   <li>Assembles the full LLM message list (system prompt + history + new user turn).</li>
 *   <li>Calls the async streaming LLM via {@link OpenAIClientAsync}.</li>
 *   <li>Accumulates the reply, strips the {@code ##ESCALATE##} marker, emits SSE frames.</li>
 *   <li>Emits an optional {@code {"decisionCategory":"ESCALATE"}} state frame if applicable.</li>
 *   <li>Emits {@code [DONE]} and calls {@link SseEmitter#complete()}.</li>
 *   <li>Saves the assistant reply to the session.</li>
 * </ol>
 *
 * <h2>SSE wire format (frozen — must match FE exactly)</h2>
 * <pre>{@code
 * Token:        data:{"t":"<delta>"}\n\n
 * State change: data:{"decisionCategory":"ESCALATE"}\n\n
 * Terminal:     data:[DONE]\n\n
 * }</pre>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Marker the model emits to signal a REJECT → ESCALATE transition. */
    static final String ESCALATE_MARKER = "##ESCALATE##";

    private final OpenAIClientAsync asyncClient;
    private final SessionRepository sessionRepository;
    private final PromptBuilder promptBuilder;
    private final PolicyProvider policyProvider;
    private final AppProperties appProperties;

    /**
     * Constructs the service with its required collaborators.
     *
     * @param asyncClient       async OpenAI client for streaming calls
     * @param sessionRepository session store
     * @param promptBuilder     assembles LLM message lists from templates
     * @param policyProvider    provides policy and legal-rules documents
     * @param appProperties     application configuration (model id etc.)
     */
    public ChatService(
            OpenAIClientAsync asyncClient,
            SessionRepository sessionRepository,
            PromptBuilder promptBuilder,
            PolicyProvider policyProvider,
            AppProperties appProperties
    ) {
        this.asyncClient = asyncClient;
        this.sessionRepository = sessionRepository;
        this.promptBuilder = promptBuilder;
        this.policyProvider = policyProvider;
        this.appProperties = appProperties;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Handles one chat turn for the given session.
     *
     * <p>Emits SSE token frames to the provided emitter, then {@code [DONE]}.
     * Saves the user message and the assistant reply to the session.
     *
     * <p>On any exception during streaming, calls
     * {@link SseEmitter#completeWithError(Throwable)} so the client knows the
     * stream is over.
     *
     * @param sessionId the session to chat in
     * @param userText  the user's message text
     * @param emitter   the {@link SseEmitter} to stream tokens to
     * @throws SessionNotFoundException if the session does not exist or has expired
     * @throws IllegalArgumentException if {@code userText} is blank
     */
    public void chat(String sessionId, String userText, SseEmitter emitter) {
        // 1. Load session
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // 2. Validate input
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        // 3. Persist user message
        session.addMessage(new ChatMessage(ChatMessage.Role.USER, userText, Instant.now()));
        sessionRepository.save(session);

        // 4. Build LLM message list
        List<ChatCompletionMessageParam> messages = buildMessages(session, userText);

        // 5. Call streaming LLM
        String model = appProperties.llm().modelDecision();
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages)
                .build();

        try {
            StringBuilder accumulator = new StringBuilder();

            asyncClient.chat().completions()
                    .createStreaming(params)
                    .subscribe(new AsyncStreamResponse.Handler<>() {
                        @Override
                        public void onNext(ChatCompletionChunk chunk) {
                            chunk.choices().forEach(choice -> {
                                String delta = choice.delta().content().orElse("");
                                if (!delta.isEmpty()) {
                                    accumulator.append(delta);
                                }
                            });
                        }

                        @Override
                        public void onComplete(Optional<Throwable> error) {
                            if (error.isPresent()) {
                                log.error("LLM stream error for session {}", sessionId, error.get());
                                emitter.completeWithError(error.get());
                                return;
                            }
                            // Stream finished — post-process and emit
                            try {
                                postProcess(session, accumulator.toString(), emitter);
                            } catch (Exception e) {
                                log.error("Error post-processing LLM reply for session {}",
                                        sessionId, e);
                                emitter.completeWithError(e);
                            }
                        }
                    })
                    .onCompleteFuture()
                    .join();

        } catch (Exception e) {
            log.error("Error during LLM streaming call for session {}", sessionId, e);
            emitter.completeWithError(e);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Post-processes the accumulated LLM reply: strips the escalation marker,
     * emits token frame(s), optionally emits the state-change frame, emits
     * {@code [DONE]}, completes the emitter, and saves the assistant reply.
     *
     * @param session     the current session
     * @param rawReply    the complete accumulated reply text from the model
     * @param emitter     the SSE emitter to write frames to
     */
    private void postProcess(Session session, String rawReply, SseEmitter emitter)
            throws IOException {
        // Detect escalation
        boolean escalateDetected = rawReply.contains(ESCALATE_MARKER);

        // Strip the marker (and any surrounding blank lines) from the visible text
        String strippedReply = stripEscalateMarker(rawReply);

        // 6. Emit the visible reply as a single token frame
        if (!strippedReply.isEmpty()) {
            emitToken(emitter, strippedReply);
        }

        // 7. Emit state-change frame if REJECT → ESCALATE (never APPROVE → ESCALATE)
        if (escalateDetected && session.getCurrentDecisionCategory() == DecisionCategory.REJECT) {
            session.setCurrentDecisionCategory(DecisionCategory.ESCALATE);
            emitData(emitter, "{\"decisionCategory\":\"ESCALATE\"}");
        }

        // Emit [DONE] terminal frame
        emitData(emitter, "[DONE]");
        emitter.complete();

        // 8. Save assistant reply to session (without marker)
        session.addMessage(
                new ChatMessage(ChatMessage.Role.ASSISTANT, strippedReply, Instant.now()));
        sessionRepository.save(session);
    }

    /**
     * Builds the full message list for the LLM: system prompt, then all prior
     * session messages as USER/ASSISTANT turns, then the new user turn.
     *
     * @param session  the session carrying history and context
     * @param userText the new user message (already saved to the session)
     * @return ordered list of {@link ChatCompletionMessageParam}s
     */
    private List<ChatCompletionMessageParam> buildMessages(Session session, String userText) {
        String policyDocument = policyProvider.getPolicyDocument(
                session.getCaseData().requestType());
        String legalRules = policyProvider.getLegalRules();

        List<ChatCompletionMessageParam> messages = new ArrayList<>(
                promptBuilder.buildChatSystemPrompt(
                        session.getCaseData(),
                        session.getImageAnalysis(),
                        session.getDecision(),
                        policyDocument,
                        legalRules
                )
        );

        // Append prior conversation history (excluding the user message we just saved)
        List<ChatMessage> history = session.getMessages();
        // The last message is the user message we just added — include all including it
        for (ChatMessage msg : history) {
            ChatCompletionMessageParam param = switch (msg.role()) {
                case USER -> ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(msg.content())
                                .build()
                );
                case ASSISTANT -> ChatCompletionMessageParam.ofAssistant(
                        ChatCompletionAssistantMessageParam.builder()
                                .content(msg.content())
                                .build()
                );
                case SYSTEM -> null; // system messages are handled via buildChatSystemPrompt
            };
            if (param != null) {
                messages.add(param);
            }
        }

        return messages;
    }

    /**
     * Removes the {@code ##ESCALATE##} marker and any immediately surrounding
     * blank lines from the given text.
     *
     * @param text the raw model reply
     * @return the text with the marker stripped
     */
    static String stripEscalateMarker(String text) {
        if (!text.contains(ESCALATE_MARKER)) {
            return text;
        }
        // Remove the marker and any adjacent newlines
        return text
                .replace("\n" + ESCALATE_MARKER + "\n", "\n")
                .replace("\n" + ESCALATE_MARKER,       "")
                .replace(ESCALATE_MARKER + "\n",       "")
                .replace(ESCALATE_MARKER,               "")
                .stripTrailing();
    }

    /**
     * Emits a token frame {@code {"t":"<escaped>"}} to the SSE emitter.
     *
     * @param emitter the emitter to write to
     * @param delta   the token text to emit
     * @throws IOException if the emitter cannot accept the frame
     */
    private void emitToken(SseEmitter emitter, String delta) throws IOException {
        emitter.send(SseEmitter.event().data(tokenFrame(delta)));
    }

    /**
     * Emits a raw data string as an SSE frame.
     *
     * @param emitter the emitter to write to
     * @param data    the raw data string (not JSON-escaped)
     * @throws IOException if the emitter cannot accept the frame
     */
    private void emitData(SseEmitter emitter, String data) throws IOException {
        emitter.send(SseEmitter.event().data(data));
    }

    /**
     * Builds a JSON token frame string for the given delta content.
     *
     * <p>Applies minimal JSON escaping: backslash, double-quote, newline,
     * carriage-return.
     *
     * @param delta the raw token text
     * @return a string of the form {@code {"t":"<escaped>"}}
     */
    static String tokenFrame(String delta) {
        String escaped = delta
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "{\"t\":\"" + escaped + "\"}";
    }
}
