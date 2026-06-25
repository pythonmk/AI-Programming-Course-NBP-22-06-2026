package pl.nbp.copilot.web;

/**
 * Thrown by {@link CaseController} when a field-level validation rule fails
 * that cannot be expressed with Bean Validation annotations alone (e.g.
 * conditional validation, manual date checks).
 *
 * <p>Handled by {@link GlobalExceptionHandler#handleValidationException(ValidationException)}
 * which maps it to a {@code 400 Bad Request} with the standard validation error envelope.
 */
public class ValidationException extends RuntimeException {

    private final String field;

    /**
     * Creates a new {@code ValidationException} for a specific field.
     *
     * @param field     name of the invalid request field
     * @param messagePl Polish description of the constraint violation
     */
    public ValidationException(String field, String messagePl) {
        super(messagePl);
        this.field = field;
    }

    /**
     * Returns the name of the invalid field.
     *
     * @return field name, never {@code null}
     */
    public String getField() {
        return field;
    }
}
