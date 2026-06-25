package pl.nbp.copilot.web;

/**
 * Thrown by {@link CaseController} when a request parameter that maps to an
 * enum contains an unrecognised value.
 *
 * <p>Handled by {@link GlobalExceptionHandler#handleInvalidEnumValue(InvalidEnumValueException)}
 * which maps it to a {@code 400 Bad Request} with {@code code="INVALID_ENUM_VALUE"}.
 */
public class InvalidEnumValueException extends RuntimeException {

    private final String field;
    private final String rejectedValue;

    /**
     * Creates a new {@code InvalidEnumValueException} for the given field and rejected value.
     *
     * @param field         name of the request parameter with the invalid value
     * @param rejectedValue the raw string value that was rejected
     */
    public InvalidEnumValueException(String field, String rejectedValue) {
        super("Invalid enum value '%s' for field '%s'".formatted(rejectedValue, field));
        this.field = field;
        this.rejectedValue = rejectedValue;
    }

    /**
     * Returns the name of the field with the invalid enum value.
     *
     * @return field name, never {@code null}
     */
    public String getField() {
        return field;
    }

    /**
     * Returns the rejected raw string value.
     *
     * @return rejected value, never {@code null}
     */
    public String getRejectedValue() {
        return rejectedValue;
    }
}
