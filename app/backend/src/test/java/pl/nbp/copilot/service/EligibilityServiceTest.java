package pl.nbp.copilot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.nbp.copilot.domain.EligibilityWindows;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Table-driven unit tests for {@link EligibilityService} boundary conditions.
 *
 * <p>All cases pass explicit {@code today} and {@code purchaseDate} values so
 * results are deterministic regardless of when the test suite runs.
 */
@DisplayName("EligibilityService — date-math boundary tests")
class EligibilityServiceTest {

    private final EligibilityService service = new EligibilityService();

    // -----------------------------------------------------------------------
    // Test data
    // -----------------------------------------------------------------------

    /**
     * Each row: description, purchaseDate, today, expectedDays,
     * expectedWithdrawal, expectedNonConformity.
     *
     * <p>Using a fixed "today" of 2026-06-25 for all cases except the
     * leap-year span which uses 2026-02-28 to exercise the Feb-29 roll-down.
     */
    static Stream<Arguments> eligibilityCases() {
        LocalDate baseToday = LocalDate.of(2026, 6, 25);

        return Stream.of(

                // ── 0 days ago: both windows open ──────────────────────────
                Arguments.of(
                        "Purchase today (0 days): both windows true",
                        baseToday,               // purchaseDate == today
                        baseToday,               // today
                        0,                       // expectedDays
                        true,                    // expectedWithdrawal
                        true                     // expectedNonConformity
                ),

                // ── 14 days ago: withdrawal window inclusive boundary ───────
                Arguments.of(
                        "14 days ago: withdrawal window inclusive (day 14 = true)",
                        baseToday.minusDays(14),
                        baseToday,
                        14,
                        true,
                        true
                ),

                // ── 15 days ago: one day past withdrawal window ─────────────
                Arguments.of(
                        "15 days ago: withdrawal window closed (day 15 = false)",
                        baseToday.minusDays(15),
                        baseToday,
                        15,
                        false,
                        true
                ),

                // ── Exactly 2 years ago: non-conformity inclusive boundary ──
                // today=2026-06-25, purchase=2024-06-25 → exactly 2 calendar years
                // ChronoUnit.DAYS: 2024-06-25 → 2025-06-25 = 365 (Feb-29 2024 is before June 25)
                //                  2025-06-25 → 2026-06-25 = 365
                //                  total = 730
                Arguments.of(
                        "Exactly 2 years ago (2024-06-25 → 2026-06-25): non-conformity inclusive (true)",
                        LocalDate.of(2024, 6, 25),
                        baseToday,
                        730,
                        false,
                        true
                ),

                // ── 2 years + 1 day ago: non-conformity window closed ───────
                // purchase=2024-06-24, today=2026-06-25
                // 2024-06-24 + 2 years = 2026-06-24, today is after that date → false
                // daysSincePurchase: 730 + 1 = 731
                Arguments.of(
                        "2 years + 1 day ago (2024-06-24 → 2026-06-25): non-conformity closed (false)",
                        LocalDate.of(2024, 6, 24),
                        baseToday,
                        731,
                        false,
                        false
                ),

                // ── Leap-year span: purchase on leap-day 2024-02-29 ─────────
                // today=2026-02-28, purchaseDate=2024-02-29
                // plusYears(2) from 2024-02-29 → 2026-02-28 (last valid Feb day in non-leap year)
                // today IS that boundary date → withinNonConformity inclusive = true
                // ChronoUnit.DAYS.between(2024-02-29, 2026-02-28):
                //   2024-02-29 → 2025-02-28 = 365 days (2024 has leap day, but we start after it)
                //   2025-02-28 → 2026-02-28 = 365 days
                //   total = 730
                Arguments.of(
                        "Leap-year purchase 2024-02-29, today=2026-02-28: non-conformity inclusive (true)",
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2026, 2, 28),
                        730,
                        false,
                        true
                )
        );
    }

    // -----------------------------------------------------------------------
    // Parameterized test
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("eligibilityCases")
    @DisplayName("compute() boundary cases")
    void compute_boundaryCase(
            String description,
            LocalDate purchaseDate,
            LocalDate today,
            int expectedDays,
            boolean expectedWithdrawal,
            boolean expectedNonConformity
    ) {
        EligibilityWindows result = service.compute(purchaseDate, today);

        assertAll(description,
                () -> assertEquals(expectedDays, result.daysSincePurchase(),
                        "daysSincePurchase"),
                () -> assertEquals(expectedWithdrawal, result.withinWithdrawalWindow(),
                        "withinWithdrawalWindow"),
                () -> assertEquals(expectedNonConformity, result.withinNonConformityWindow(),
                        "withinNonConformityWindow")
        );
    }
}
