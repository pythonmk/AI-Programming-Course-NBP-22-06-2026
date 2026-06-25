package pl.nbp.copilot.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Inner body of the API error envelope.
 *
 * <p>The {@code fieldErrors} field is omitted from JSON serialisation when
 * {@code null} (non-validation errors do not have field-level details).
 *
 * @param code        machine-readable error code (e.g. {@code "VALIDATION_ERROR"}, {@code "INTERNAL_ERROR"})
 * @param messagePl   human-readable Polish error description
 * @param fieldErrors per-field validation errors; {@code null} for non-validation failures
 */
public record ErrorBody(
        String code,
        String messagePl,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldError> fieldErrors) {}
