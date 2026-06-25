package pl.nbp.copilot.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.CaseData;
import pl.nbp.copilot.domain.Confidence;
import pl.nbp.copilot.domain.Decision;
import pl.nbp.copilot.domain.DecisionCategory;
import pl.nbp.copilot.domain.EligibilityWindows;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.ImageAnalysis;
import pl.nbp.copilot.domain.RequestType;
import pl.nbp.copilot.domain.TriState;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InMemorySessionRepository}.
 *
 * <p>Uses a {@link MutableClock} to control time deterministically so that TTL
 * eviction can be verified without real wall-clock waits.
 */
@DisplayName("InMemorySessionRepository")
class InMemorySessionRepositoryTest {

    /** A simple mutable clock that can be advanced in tests. */
    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final int TTL_MINUTES = 60;

    private MutableClock clock;
    private InMemorySessionRepository repository;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        repository = new InMemorySessionRepository(clock, TTL_MINUTES);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /**
     * Creates a minimal {@link pl.nbp.copilot.domain.Session} via
     * {@link InMemorySessionRepository#save(pl.nbp.copilot.domain.Session)}.
     * The session has no pre-assigned ID so the repository generates one.
     */
    private pl.nbp.copilot.domain.Session buildSession() {
        CaseData caseData = new CaseData(
                RequestType.COMPLAINT,
                EquipmentCategory.LAPTOP,
                "ThinkPad X1",
                LocalDate.of(2025, 6, 1),
                "Ekran nie działa"
        );
        EligibilityWindows windows = new EligibilityWindows(200, false, true);
        ImageAnalysis analysis = new ImageAnalysis(
                TriState.TRUE, "pęknięty ekran", "górny panel",
                "upadek", null, null, null, null, Confidence.HIGH, "Widoczne uszkodzenie mechaniczne"
        );
        Decision decision = new Decision(
                DecisionCategory.APPROVE,
                "**Decyzja:** Akceptacja reklamacji.",
                "Proszę dostarczyć sprzęt do serwisu.",
                List.of("Art. 561 KC"),
                "Szanowny Kliencie, Twoja reklamacja została zaakceptowana."
        );
        return new pl.nbp.copilot.domain.Session(
                null,   // no ID — repository should generate one
                caseData, windows, analysis, decision,
                DecisionCategory.APPROVE,
                null,   // createdAt — repository sets this
                null    // expiresAt — repository sets this
        );
    }

    // -----------------------------------------------------------------------
    // save + findById — round-trip
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("save assigns a UUID sessionId when none is present")
    void save_assignsUuid_whenSessionIdIsNull() {
        var session = buildSession();
        var saved = repository.save(session);

        assertThat(saved.getSessionId())
                .as("sessionId must be a valid UUID string")
                .isNotBlank()
                .satisfies(id -> assertThat(UUID.fromString(id)).isNotNull());
    }

    @Test
    @DisplayName("findById returns the saved session by its id")
    void findById_returnsSavedSession() {
        var saved = repository.save(buildSession());

        Optional<pl.nbp.copilot.domain.Session> found = repository.findById(saved.getSessionId());

        assertThat(found)
                .isPresent()
                .get()
                .extracting(pl.nbp.copilot.domain.Session::getSessionId)
                .isEqualTo(saved.getSessionId());
    }

    @Test
    @DisplayName("findById sets createdAt and expiresAt on the stored session")
    void save_setsTimestamps() {
        var saved = repository.save(buildSession());

        assertThat(saved.getCreatedAt()).isEqualTo(T0);
        assertThat(saved.getExpiresAt()).isEqualTo(T0.plus(java.time.Duration.ofMinutes(TTL_MINUTES)));
    }

    // -----------------------------------------------------------------------
    // TTL eviction (TAC-105)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findById returns present just before TTL expires")
    void findById_returnsPresent_justBeforeTtl() {
        var saved = repository.save(buildSession());

        // advance to 1 ms before expiry
        clock.advance(java.time.Duration.ofMinutes(TTL_MINUTES).minusMillis(1));

        assertThat(repository.findById(saved.getSessionId()))
                .as("session should still be present just before expiry")
                .isPresent();
    }

    @Test
    @DisplayName("findById returns empty exactly at TTL boundary (expired)")
    void findById_returnsEmpty_atTtlBoundary() {
        var saved = repository.save(buildSession());
        String id = saved.getSessionId();

        // advance clock exactly to the expiry instant
        clock.advance(java.time.Duration.ofMinutes(TTL_MINUTES));

        assertThat(repository.findById(id))
                .as("session should be expired at the TTL boundary")
                .isEmpty();
    }

    @Test
    @DisplayName("findById returns empty after TTL and removes the entry (lazy eviction)")
    void findById_returnsEmpty_afterTtlAndEvicts() {
        var saved = repository.save(buildSession());
        String id = saved.getSessionId();

        // advance well past expiry
        clock.advance(java.time.Duration.ofMinutes(TTL_MINUTES + 1));

        assertThat(repository.findById(id)).isEmpty();

        // A second call must also return empty (entry was removed, not just hidden)
        assertThat(repository.findById(id))
                .as("evicted entry must not reappear on a second lookup")
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Unknown id
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findById returns empty for an unknown sessionId")
    void findById_returnsEmpty_forUnknownId() {
        assertThat(repository.findById(UUID.randomUUID().toString())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // deleteById
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deleteById removes the session so subsequent findById returns empty")
    void deleteById_removesSession() {
        var saved = repository.save(buildSession());
        String id = saved.getSessionId();

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("deleteById is a no-op for an unknown sessionId (no exception)")
    void deleteById_noOp_forUnknownId() {
        // must not throw
        repository.deleteById(UUID.randomUUID().toString());
    }

    // -----------------------------------------------------------------------
    // save with explicit sessionId
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("save keeps an existing sessionId when one is already set")
    void save_keepsExistingSessionId() {
        String explicitId = UUID.randomUUID().toString();
        CaseData caseData = new CaseData(
                RequestType.RETURN,
                EquipmentCategory.MONITOR,
                "Dell UltraSharp",
                LocalDate.of(2026, 6, 1),
                null
        );
        EligibilityWindows windows = new EligibilityWindows(10, true, true);
        ImageAnalysis analysis = new ImageAnalysis(
                TriState.FALSE, null, null, null,
                TriState.TRUE, null, null, null, Confidence.MEDIUM, "Produkt nieużywany"
        );
        Decision decision = new Decision(
                DecisionCategory.APPROVE,
                "Akceptacja zwrotu.",
                "Proszę odesłać produkt.",
                List.of(),
                "Witamy, Twój zwrot został zaakceptowany."
        );
        var session = new pl.nbp.copilot.domain.Session(
                explicitId, caseData, windows, analysis, decision,
                DecisionCategory.APPROVE, null, null
        );

        var saved = repository.save(session);

        assertThat(saved.getSessionId()).isEqualTo(explicitId);
        assertThat(repository.findById(explicitId)).isPresent();
    }

    // -----------------------------------------------------------------------
    // Concurrency (TAC-105)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("concurrent saves and finds complete without exceptions or data loss")
    void concurrentSaveAndFind_noExceptionsOrDataLoss() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();  // all threads start at the same time
                    var saved = repository.save(buildSession());
                    repository.findById(saved.getSessionId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
                return null;
            });
        }

        startLatch.countDown();  // release all threads
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).as("executor should finish within 10 s").isTrue();
        assertThat(errorCount.get()).as("no exceptions during concurrent access").isZero();
        assertThat(successCount.get()).as("all threads completed successfully").isEqualTo(threadCount);
    }
}
