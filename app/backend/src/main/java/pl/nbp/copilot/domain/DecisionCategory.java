package pl.nbp.copilot.domain;

/**
 * The advisory outcome of the AI decision agent for a service request.
 *
 * <p>Each constant carries a Polish display label accessible via {@link #labelPl()}.
 * The label is rendered in the chat UI as the decision badge and in the
 * {@code GET /api/meta/form-options} metadata response.
 *
 * <p>State-machine constraint (enforced by the service layer):
 * a session may transition {@code REJECT → ESCALATE} during chat, but
 * never {@code REJECT → APPROVE} or {@code ESCALATE → APPROVE}.
 */
public enum DecisionCategory {

    /** Request preliminarily approved — customer qualifies under policy. */
    APPROVE("Zatwierdzono wstępnie"),

    /** Request rejected — does not meet policy criteria. */
    REJECT("Odrzucono"),

    /** Forwarded to a human consultant for further review. */
    ESCALATE("Przekazanie do konsultanta");

    private final String labelPl;

    DecisionCategory(String labelPl) {
        this.labelPl = labelPl;
    }

    /**
     * Returns the Polish display label for this decision category.
     *
     * @return Polish label, never {@code null}
     */
    public String labelPl() {
        return labelPl;
    }
}
