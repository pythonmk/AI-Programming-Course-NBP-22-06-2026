package pl.nbp.copilot.domain;

/**
 * Confidence level reported by the multimodal image-analysis agent.
 *
 * <p>Used in {@link ImageAnalysis#confidence()} to indicate how certain the
 * analyzer is about its assessment. A {@code LOW} confidence is one of the
 * conditions that can trigger an {@link DecisionCategory#ESCALATE} outcome.
 */
public enum Confidence {

    /** Low confidence — image unclear or ambiguous. */
    LOW,

    /** Moderate confidence — some uncertainty remains. */
    MEDIUM,

    /** High confidence — clear, unambiguous image assessment. */
    HIGH
}
