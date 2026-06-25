package pl.nbp.copilot.session;

import pl.nbp.copilot.domain.Session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory implementation of {@link SessionRepository} with
 * lazy TTL eviction.
 *
 * <h2>Storage</h2>
 * Sessions are stored in a {@link ConcurrentHashMap} keyed by {@code sessionId}.
 * All mutating operations are performed through the map's atomic methods, making
 * the implementation safe for concurrent access without additional synchronization.
 *
 * <h2>TTL eviction</h2>
 * Each session carries an {@code expiresAt} timestamp set on save. When
 * {@link #findById(String)} is called, it checks the timestamp against the
 * current clock instant. If expired, the entry is removed atomically from the
 * map and {@link Optional#empty()} is returned. No background sweep thread is
 * used; eviction is purely lazy (TAC-105).
 *
 * <h2>ID assignment</h2>
 * If a {@link Session} is saved with a {@code null} or blank {@code sessionId},
 * the repository generates a random UUID and returns a new {@link Session}
 * instance (via {@link Session#withIdAndTimestamps}) with the assigned values.
 * Similarly, {@code createdAt} and {@code expiresAt} are always set/refreshed
 * on save using the injected {@link Clock}.
 */
public class InMemorySessionRepository implements SessionRepository {

    private final ConcurrentHashMap<String, Session> store = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    /**
     * Creates a new repository.
     *
     * @param clock      clock used to derive {@code now} for timestamp stamping and
     *                   expiry checks — inject a controllable clock in tests
     * @param ttlMinutes how long a session is retained; must be positive
     */
    public InMemorySessionRepository(Clock clock, int ttlMinutes) {
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stamps {@code createdAt} (if not already set) and {@code expiresAt}
     * using the injected clock. If no {@code sessionId} is set, a UUID is
     * generated. The caller should use the <em>returned</em> session, not the
     * one passed in, since a new instance may have been created to carry the
     * assigned id/timestamps.
     */
    @Override
    public Session save(Session session) {
        Instant now = clock.instant();

        String id = (session.getSessionId() == null || session.getSessionId().isBlank())
                ? UUID.randomUUID().toString()
                : session.getSessionId();

        Instant createdAt = (session.getCreatedAt() != null) ? session.getCreatedAt() : now;
        Instant expiresAt = now.plus(ttl);

        Session stamped = session.withIdAndTimestamps(id, createdAt, expiresAt);
        store.put(id, stamped);
        return stamped;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs lazy eviction: if the stored entry has expired (i.e.
     * {@code expiresAt} is not after {@code now}), it is removed atomically
     * and {@link Optional#empty()} is returned.
     */
    @Override
    public Optional<Session> findById(String sessionId) {
        Session session = store.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (!session.getExpiresAt().isAfter(clock.instant())) {
            // lazy eviction — remove atomically only if the entry hasn't been replaced
            store.remove(sessionId, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@link ConcurrentHashMap#remove(Object)} which is a no-op for
     * unknown keys.
     */
    @Override
    public void deleteById(String sessionId) {
        store.remove(sessionId);
    }
}
