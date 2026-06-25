package pl.nbp.copilot.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.nbp.copilot.web.dto.ErrorBody;
import pl.nbp.copilot.web.dto.ErrorResponse;
import pl.nbp.copilot.web.dto.FieldError;

import java.util.List;

/**
 * Global exception handler producing the Polish error envelope defined in ADR-000 §6.
 *
 * <p>Contract: {@code { "error": { "code": "...", "messagePl": "...", "fieldErrors": [...] } }}.
 *
 * <p>Current handlers:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} / {@link BindException} — 400, {@code VALIDATION_ERROR}</li>
 *   <li>{@link ConstraintViolationException} — 400, {@code VALIDATION_ERROR}</li>
 *   <li>Fallback {@link Exception} — 500, {@code INTERNAL_ERROR}</li>
 * </ul>
 *
 * <p>Future handlers (not-found, LLM-failure) can be added as additional
 * {@code @ExceptionHandler} methods without modifying the existing ones.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String CODE_VALIDATION = "VALIDATION_ERROR";
    private static final String CODE_INTERNAL = "INTERNAL_ERROR";
    private static final String MSG_VALIDATION = "Formularz zawiera błędy walidacji.";
    private static final String MSG_INTERNAL = "Wystąpił nieoczekiwany błąd.";

    /**
     * Handles Bean Validation failures on {@code @RequestBody} parameters.
     *
     * @param ex the validation exception
     * @return {@code 400 Bad Request} with field-level errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = mapBindingErrors(ex);
        return badRequest(fieldErrors);
    }

    /**
     * Handles binding failures on {@code @ModelAttribute} or form parameters.
     *
     * @param ex the binding exception
     * @return {@code 400 Bad Request} with field-level errors
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex) {
        List<FieldError> fieldErrors = mapBindingErrors(ex);
        return badRequest(fieldErrors);
    }

    /**
     * Handles JSR-380 constraint violations raised outside the MVC binding pipeline
     * (e.g. method-level or service-level validation).
     *
     * @param ex the constraint violation exception
     * @return {@code 400 Bad Request} with field-level errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    // Use the leaf property name from the path (e.g. "name" from "method.name")
                    String path = cv.getPropertyPath().toString();
                    String field = path.contains(".")
                            ? path.substring(path.lastIndexOf('.') + 1)
                            : path;
                    return new FieldError(field, cv.getMessage());
                })
                .toList();
        return badRequest(fieldErrors);
    }

    /**
     * Fallback handler for any unhandled exception.
     *
     * <p>Logs the exception at ERROR level and returns a generic Polish message
     * without leaking internal details to the client.
     *
     * @param ex the unexpected exception
     * @return {@code 500 Internal Server Error}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorBody body = new ErrorBody(CODE_INTERNAL, MSG_INTERNAL, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(body));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Maps Spring binding errors to {@link FieldError} DTOs.
     *
     * @param ex a {@link BindException} or its subclass
     * @return list of field errors (never {@code null})
     */
    private List<FieldError> mapBindingErrors(BindException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
    }

    /**
     * Builds a {@code 400 Bad Request} response with validation field errors.
     *
     * @param fieldErrors non-null list of field errors
     * @return the response entity
     */
    private ResponseEntity<ErrorResponse> badRequest(List<FieldError> fieldErrors) {
        ErrorBody body = new ErrorBody(CODE_VALIDATION, MSG_VALIDATION, fieldErrors);
        return ResponseEntity.badRequest().body(new ErrorResponse(body));
    }
}
