package pl.nbp.copilot.service;

import org.springframework.stereotype.Service;
import pl.nbp.copilot.domain.EligibilityWindows;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure date-math service that derives {@link EligibilityWindows} from an
 * equipment purchase date.
 *
 * <h2>Window semantics</h2>
 * <ul>
 *   <li><b>14-day withdrawal window</b> (right of withdrawal, art. 27 UOKIK) —
 *       day 0 through day 14 are inclusive; day 15 is outside.</li>
 *   <li><b>2-year non-conformity window</b> (seller liability, art. 43d UOKIK) —
 *       the calendar date exactly 2 years after purchase is inclusive;
 *       one day past that date is outside. Calendar-year semantics handle
 *       leap years correctly (e.g. 2024-02-29 + 2 years = 2026-02-28).</li>
 * </ul>
 *
 * <h2>Testing</h2>
 * Use {@link #compute(LocalDate, LocalDate)} to pass an explicit {@code today}
 * value in unit tests. The single-argument {@link #compute(LocalDate)} variant
 * derives {@code today} from the injected {@link Clock} and is the intended
 * production entry point.
 */
@Service
public class EligibilityService {

    private final Clock clock;

    /**
     * Creates a service that reads the current date from the supplied clock.
     *
     * @param clock clock used to derive {@code today}; typically
     *              {@link Clock#systemDefaultZone()} in production
     */
    public EligibilityService(Clock clock) {
        this.clock = clock;
    }

    /**
     * No-arg constructor for use in unit tests that call
     * {@link #compute(LocalDate, LocalDate)} directly and therefore do not
     * need a real clock.
     *
     * <p>The clock field is set to {@link Clock#systemDefaultZone()} so the
     * service can also be instantiated without Spring in fast unit tests.
     */
    EligibilityService() {
        this(Clock.systemDefaultZone());
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Computes eligibility windows using the current wall-clock date.
     *
     * @param purchaseDate date the equipment was purchased; must not be
     *                     in the future relative to {@code today}
     * @return computed eligibility windows
     */
    public EligibilityWindows compute(LocalDate purchaseDate) {
        return compute(purchaseDate, LocalDate.now(clock));
    }

    /**
     * Computes eligibility windows against an explicit reference date.
     *
     * <p>This overload is the deterministic entry point used in unit tests.
     *
     * @param purchaseDate date the equipment was purchased
     * @param today        reference date to use as "now"
     * @return computed eligibility windows
     */
    public EligibilityWindows compute(LocalDate purchaseDate, LocalDate today) {
        int daysSincePurchase = (int) ChronoUnit.DAYS.between(purchaseDate, today);

        // Day 14 is inclusive: NOT after purchaseDate + 14 days
        boolean withinWithdrawal = !today.isAfter(purchaseDate.plusDays(14));

        // Calendar-year semantics: day of 2-year anniversary is inclusive
        boolean withinNonConformity = !today.isAfter(purchaseDate.plusYears(2));

        return new EligibilityWindows(daysSincePurchase, withinWithdrawal, withinNonConformity);
    }
}
