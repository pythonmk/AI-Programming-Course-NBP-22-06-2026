package pl.nbp.copilot.session;

import pl.nbp.copilot.domain.Session;

import java.util.Optional;

/**
 * Persistence abstraction for {@link Session} objects.
 *
 * <p>The only persistence touchpoint in the application: all components that need
 * to read or write session state go through this interface, enabling the planned
 * SQLite implementation ({@code SqliteSessionRepository}) to replace
 * {@link InMemorySessionRepository} without changing any callers.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #save(Session)} stores (or replaces) the session by its
 *       {@code sessionId}. If the session has no {@code sessionId} set, the
 *       implementation assigns a UUID. The returned session is the canonical
 *       stored copy (useful if the id was generated).</li>
 *   <li>{@link #findById(String)} returns an empty {@code Optional} for both
 *       unknown and <em>expired</em> sessions; expired entries are removed
 *       lazily on access.</li>
 *   <li>{@link #deleteById(String)} is a no-op if the id is unknown.</li>
 * </ul>
 *
 * <h2>TTL</h2>
 * The implementation is responsible for enforcing the session lifetime
 * configured via {@code SESSION_TTL_MINUTES}. The lifetime is stamped on the
 * session's {@code expiresAt} field at save time; expired sessions MUST NOT be
 * returned by {@link #findById(String)}.
 */
public interface SessionRepository {

    /**
     * Stores or replaces a session.
     *
     * <p>If {@code session.getSessionId()} is {@code null} or blank, the
     * implementation generates a UUID and returns a session with that id set.
     * The {@code createdAt} and {@code expiresAt} timestamps are set (or
     * refreshed) by the implementation using its injected {@link java.time.Clock}.
     *
     * @param session session to store, must not be {@code null}
     * @return the stored session (same object or a new instance with the assigned
     *         {@code sessionId}), never {@code null}
     */
    Session save(Session session);

    /**
     * Looks up a session by its identifier.
     *
     * <p>Returns {@link Optional#empty()} if the session is unknown or has
     * expired. Expired sessions are removed from the store on access (lazy
     * eviction).
     *
     * @param sessionId the session identifier to look up, must not be {@code null}
     * @return an {@code Optional} containing the live session, or empty if absent
     *         or expired
     */
    Optional<Session> findById(String sessionId);

    /**
     * Deletes a session by its identifier.
     *
     * <p>No-op if the session does not exist or has already been evicted.
     *
     * @param sessionId the session identifier to delete, must not be {@code null}
     */
    void deleteById(String sessionId);
}
