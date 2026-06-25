package pl.nbp.copilot.web.dto;

/**
 * Top-level error envelope returned by the API on all error responses.
 *
 * <p>Conforms to the contract defined in ADR-000 §6:
 * <pre>{@code { "error": { "code": "...", "messagePl": "...", "fieldErrors": [...] } }}</pre>
 *
 * @param error the error body containing the code, Polish message, and optional field errors
 */
public record ErrorResponse(ErrorBody error) {}
