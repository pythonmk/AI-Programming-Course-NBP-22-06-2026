package pl.nbp.copilot.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable session entity that holds the full server-side state for one customer
 * interaction from initial submission through all follow-up chat messages.
 *
 * <p>Sessions are stored in the in-memory {@code SessionRepository} keyed by
 * {@link #getSessionId()} and are evicted after a configurable TTL.
 *
 * <p>Raw image bytes are <strong>never</strong> stored in a session — they are
 * discarded after the {@link ImageAnalysis} is produced (TAC-05).
 *
 * <p>The {@link #currentDecisionCategory} field is mutable because the decision
 * may transition from {@link DecisionCategory#REJECT} to
 * {@link DecisionCategory#ESCALATE} during chat. The transition to
 * {@link DecisionCategory#APPROVE} is never permitted by the service layer
 * after an initial {@code REJECT} or {@code ESCALATE} (TAC-08).
 */
public class Session {

    private final String sessionId;
    private final CaseData caseData;
    private final EligibilityWindows eligibilityWindows;
    private final ImageAnalysis imageAnalysis;
    private final Decision decision;
    private DecisionCategory currentDecisionCategory;
    private final List<ChatMessage> messages;
    private final Instant createdAt;
    private final Instant expiresAt;

    /**
     * Constructs a new session with all fields set at creation time.
     *
     * @param sessionId                unique session identifier (UUID string)
     * @param caseData                 the submitted form data
     * @param eligibilityWindows       pre-computed eligibility time-windows
     * @param imageAnalysis            structured output from the multimodal call
     * @param decision                 the initial AI decision
     * @param currentDecisionCategory  initial decision category (typically equals
     *                                 {@code decision.category()})
     * @param createdAt                timestamp when the session was created
     * @param expiresAt                timestamp after which the session is evicted
     */
    public Session(
            String sessionId,
            CaseData caseData,
            EligibilityWindows eligibilityWindows,
            ImageAnalysis imageAnalysis,
            Decision decision,
            DecisionCategory currentDecisionCategory,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.sessionId = sessionId;
        this.caseData = caseData;
        this.eligibilityWindows = eligibilityWindows;
        this.imageAnalysis = imageAnalysis;
        this.decision = decision;
        this.currentDecisionCategory = currentDecisionCategory;
        this.messages = new ArrayList<>();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // -----------------------------------------------------------------------
    // Accessors — immutable fields
    // -----------------------------------------------------------------------

    /**
     * Returns the unique session identifier.
     *
     * @return session ID, never {@code null}
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Returns the customer's submitted form data.
     *
     * @return case data, never {@code null}
     */
    public CaseData getCaseData() {
        return caseData;
    }

    /**
     * Returns the pre-computed eligibility time-windows for this case.
     *
     * @return eligibility windows, never {@code null}
     */
    public EligibilityWindows getEligibilityWindows() {
        return eligibilityWindows;
    }

    /**
     * Returns the structured result of the multimodal image-analysis call.
     *
     * @return image analysis, never {@code null}
     */
    public ImageAnalysis getImageAnalysis() {
        return imageAnalysis;
    }

    /**
     * Returns the AI-generated decision produced at case submission time.
     *
     * @return decision, never {@code null}
     */
    public Decision getDecision() {
        return decision;
    }

    /**
     * Returns the session creation timestamp.
     *
     * @return creation instant, never {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the session expiry timestamp.
     *
     * @return expiry instant, never {@code null}
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    // -----------------------------------------------------------------------
    // Mutable state
    // -----------------------------------------------------------------------

    /**
     * Returns the current decision category, which may have been updated
     * during the chat (e.g. {@code REJECT → ESCALATE}).
     *
     * @return current decision category, never {@code null}
     */
    public DecisionCategory getCurrentDecisionCategory() {
        return currentDecisionCategory;
    }

    /**
     * Updates the current decision category.
     *
     * <p>The service layer is responsible for enforcing the allowed transitions
     * (TAC-08): {@code REJECT → ESCALATE} is permitted; {@code * → APPROVE}
     * after an initial non-approval is not.
     *
     * @param currentDecisionCategory new decision category, must not be {@code null}
     */
    public void setCurrentDecisionCategory(DecisionCategory currentDecisionCategory) {
        this.currentDecisionCategory = currentDecisionCategory;
    }

    /**
     * Returns an unmodifiable view of the chat message list.
     *
     * <p>Use {@link #addMessage(ChatMessage)} to append messages.
     *
     * @return unmodifiable ordered list of chat messages
     */
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Appends a chat message to the end of the message list.
     *
     * @param message message to add, must not be {@code null}
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    // -----------------------------------------------------------------------
    // Repository support
    // -----------------------------------------------------------------------

    /**
     * Returns a new {@code Session} that is identical to this one but with the
     * supplied {@code sessionId}, {@code createdAt}, and {@code expiresAt}
     * values applied, and with the current message list copied over.
     *
     * <p>Used by {@link pl.nbp.copilot.session.InMemorySessionRepository} when a
     * session arrives without a pre-assigned id or without timestamps: the
     * repository generates the values and calls this method to stamp the session
     * before storing it. The original session is not mutated.
     *
     * @param newSessionId UUID string to assign, must not be {@code null}
     * @param newCreatedAt creation timestamp to assign, must not be {@code null}
     * @param newExpiresAt expiry timestamp to assign, must not be {@code null}
     * @return a new session instance with the supplied fields set
     */
    public Session withIdAndTimestamps(String newSessionId, Instant newCreatedAt, Instant newExpiresAt) {
        Session copy = new Session(
                newSessionId,
                this.caseData,
                this.eligibilityWindows,
                this.imageAnalysis,
                this.decision,
                this.currentDecisionCategory,
                newCreatedAt,
                newExpiresAt
        );
        // copy messages so they are preserved
        this.messages.forEach(copy::addMessage);
        return copy;
    }
}
