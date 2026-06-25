package pl.nbp.copilot.domain;

/**
 * Distinguishes the two main customer-service request types.
 *
 * <p>Each constant carries a Polish display label accessible via {@link #labelPl()},
 * used by the {@code GET /api/meta/form-options} endpoint so the frontend renders
 * localised option text without hardcoding strings.
 */
public enum RequestType {

    /** Hardware complaint (reklamacja) — non-conformity or defect claim. */
    COMPLAINT("Reklamacja"),

    /** Product return (zwrot) — typically within the statutory withdrawal window. */
    RETURN("Zwrot");

    private final String labelPl;

    RequestType(String labelPl) {
        this.labelPl = labelPl;
    }

    /**
     * Returns the Polish display label for this request type.
     *
     * @return Polish label, never {@code null}
     */
    public String labelPl() {
        return labelPl;
    }
}
