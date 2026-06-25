package pl.nbp.copilot.llm;

/**
 * Unchecked exception thrown by {@link OutputParser} when the LLM response
 * cannot be parsed into a valid {@link pl.nbp.copilot.domain.Decision}.
 *
 * <p>The caller must map this exception to an HTTP 502 response — it signals
 * that the LLM returned unusable output after all retry attempts have been
 * exhausted.
 */
public class LlmParseException extends RuntimeException {

    /**
     * Creates a new {@code LlmParseException} with the given message.
     *
     * @param message description of the parse failure
     */
    public LlmParseException(String message) {
        super(message);
    }

    /**
     * Creates a new {@code LlmParseException} with the given message and cause.
     *
     * @param message description of the parse failure
     * @param cause   the underlying exception that caused the failure
     */
    public LlmParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
