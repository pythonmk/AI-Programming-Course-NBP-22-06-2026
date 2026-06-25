package pl.nbp.copilot.web.dto;

/**
 * Single field-level validation error in the error envelope.
 *
 * @param field      name of the invalid request field
 * @param messagePl  Polish description of the constraint violation
 */
public record FieldError(String field, String messagePl) {}
