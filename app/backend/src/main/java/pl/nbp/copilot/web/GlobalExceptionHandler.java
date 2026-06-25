package pl.nbp.copilot.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.nbp.copilot.image.ImageTooLargeException;
import pl.nbp.copilot.llm.LlmParseException;
import pl.nbp.copilot.session.SessionNotFoundException;
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
 *   <li>{@link ImageTooLargeException} — 400, {@code IMAGE_TOO_LARGE}</li>
 *   <li>{@link pl.nbp.copilot.session.SessionNotFoundException} — 404, {@code SESSION_NOT_FOUND}</li>
 *   <li>{@link LlmParseException} — 502, {@code LLM_ERROR}</li>
 *   <li>Fallback {@link RuntimeException} — 503, {@code SERVICE_UNAVAILABLE} (SDK/network errors)</li>
 *   <li>Fallback {@link Exception} — 500, {@code INTERNAL_ERROR}</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String CODE_VALIDATION = "VALIDATION_ERROR";
    private static final String CODE_INTERNAL = "INTERNAL_ERROR";
    private static final String CODE_IMAGE_TOO_LARGE = "IMAGE_TOO_LARGE";
    private static final String CODE_LLM_ERROR = "LLM_ERROR";
    private static final String CODE_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    private static final String CODE_INVALID_ENUM_VALUE = "INVALID_ENUM_VALUE";
    private static final String CODE_SESSION_NOT_FOUND = "SESSION_NOT_FOUND";

    private static final String MSG_VALIDATION = "Formularz zawiera błędy walidacji.";
    private static final String MSG_INTERNAL = "Wystąpił nieoczekiwany błąd.";
    private static final String MSG_IMAGE_TOO_LARGE =
            "Skompresowany obraz przekracza dozwolony rozmiar. Prześlij mniejszy lub mniej szczegółowy obraz.";
    private static final String MSG_LLM_ERROR =
            "Nie udało się przetworzyć odpowiedzi asystenta. Spróbuj ponownie.";
    private static final String MSG_SERVICE_UNAVAILABLE =
            "Usługa tymczasowo niedostępna. Spróbuj ponownie za chwilę.";
    private static final String MSG_SESSION_NOT_FOUND = "Sesja nie istnieje lub wygasła.";

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
     * Handles missing required request parameters (e.g. absent {@code @RequestParam}).
     *
     * @param ex the missing parameter exception from Spring MVC binding
     * @return {@code 400 Bad Request} with {@code VALIDATION_ERROR} code and field-level detail
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        List<FieldError> fieldErrors = List.of(
                new FieldError(ex.getParameterName(), "Pole jest wymagane."));
        return badRequest(fieldErrors);
    }

    /**
     * Handles controller-level field validation failures not covered by Bean Validation
     * (e.g. future purchase date, conditional reason requirement).
     *
     * @param ex the validation exception with field name and Polish message
     * @return {@code 400 Bad Request} with {@code VALIDATION_ERROR} code and field-level detail
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        List<FieldError> fieldErrors = List.of(new FieldError(ex.getField(), ex.getMessage()));
        return badRequest(fieldErrors);
    }

    /**
     * Handles invalid enum values in request parameters.
     *
     * @param ex the exception with the rejected field and value
     * @return {@code 400 Bad Request} with {@code INVALID_ENUM_VALUE} code
     */
    @ExceptionHandler(InvalidEnumValueException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEnumValue(InvalidEnumValueException ex) {
        log.warn("Invalid enum value '{}' for field '{}'", ex.getRejectedValue(), ex.getField());
        ErrorBody body = new ErrorBody(CODE_INVALID_ENUM_VALUE,
                "Nieprawidłowa wartość '%s' dla pola '%s'.".formatted(ex.getRejectedValue(), ex.getField()),
                null);
        return ResponseEntity.badRequest().body(new ErrorResponse(body));
    }

    /**
     * Handles compressed image size exceeded errors.
     *
     * @param ex the image-too-large exception thrown by the image compressor
     * @return {@code 400 Bad Request} with {@code IMAGE_TOO_LARGE} code
     */
    @ExceptionHandler(ImageTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleImageTooLarge(ImageTooLargeException ex) {
        log.warn("Image too large: {}", ex.getMessage());
        ErrorBody body = new ErrorBody(CODE_IMAGE_TOO_LARGE, MSG_IMAGE_TOO_LARGE, null);
        return ResponseEntity.badRequest().body(new ErrorResponse(body));
    }

    /**
     * Handles cases where the LLM returned unparseable output after all retry attempts.
     *
     * @param ex the parse exception thrown by the LLM client
     * @return {@code 502 Bad Gateway} with {@code LLM_ERROR} code
     */
    @ExceptionHandler(LlmParseException.class)
    public ResponseEntity<ErrorResponse> handleLlmParseException(LlmParseException ex) {
        log.error("LLM parse error: {}", ex.getMessage());
        ErrorBody body = new ErrorBody(CODE_LLM_ERROR, MSG_LLM_ERROR, null);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(body));
    }

    /**
     * Handles a request for a session that does not exist or has expired.
     *
     * <p>Also covers the synchronous validation path for
     * {@code GET /api/sessions/{id}} and any other controller that throws
     * {@link SessionNotFoundException} before the response is committed.
     * For the SSE endpoint, errors after the emitter is returned are handled
     * by the virtual thread in the controller itself.
     *
     * @param ex the session-not-found exception
     * @return {@code 404 Not Found} with {@code SESSION_NOT_FOUND} code
     */
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException ex) {
        log.warn("Session not found: {}", ex.getMessage());
        ErrorBody body = new ErrorBody(CODE_SESSION_NOT_FOUND, MSG_SESSION_NOT_FOUND, null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(body));
    }

    /**
     * Fallback handler for SDK/network runtime errors (e.g. timeouts, connection failures).
     *
     * <p>More specific handlers ({@link ImageTooLargeException}, {@link LlmParseException})
     * take precedence. This handler catches the remaining {@link RuntimeException} subtypes
     * (typically thrown by the OpenAI SDK or other service-layer infrastructure).
     *
     * <p>Logs the exception at ERROR level and returns a generic Polish message
     * without leaking internal details to the client.
     *
     * @param ex the runtime exception
     * @return {@code 503 Service Unavailable}
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Service error", ex);
        ErrorBody body = new ErrorBody(CODE_SERVICE_UNAVAILABLE, MSG_SERVICE_UNAVAILABLE, null);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(body));
    }

    /**
     * Fallback handler for any unhandled checked exception.
     *
     * <p>Logs the exception at ERROR level and returns a generic Polish message
     * without leaking internal details to the client.
     *
     * @param ex the unexpected checked exception
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
