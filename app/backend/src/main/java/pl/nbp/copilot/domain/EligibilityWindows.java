package pl.nbp.copilot.domain;

/**
 * Immutable value object capturing the computed eligibility time-windows for a
 * service request, derived from the equipment purchase date and the current date.
 *
 * <p>Computed by {@code EligibilityService} and stored in the session so both
 * the decision agent and the chat agent can reference the values without
 * re-computing them.
 *
 * @param daysSincePurchase        number of calendar days elapsed since purchase
 * @param withinWithdrawalWindow   {@code true} if {@code daysSincePurchase ≤ 14}
 *                                 (statutory consumer-withdrawal right)
 * @param withinNonConformityWindow {@code true} if {@code daysSincePurchase ≤ 730}
 *                                 (2-year non-conformity / warranty window)
 */
public record EligibilityWindows(
        int daysSincePurchase,
        boolean withinWithdrawalWindow,
        boolean withinNonConformityWindow
) {}
