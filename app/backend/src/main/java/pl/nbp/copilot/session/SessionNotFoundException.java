package pl.nbp.copilot.session;

/**
 * Thrown by {@link pl.nbp.copilot.service.ChatService} when the requested
 * session does not exist in the repository or has expired.
 */
public class SessionNotFoundException extends RuntimeException {

    /**
     * Constructs a new exception for the given session identifier.
     *
     * @param sessionId the identifier that could not be resolved
     */
    public SessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
    }
}
