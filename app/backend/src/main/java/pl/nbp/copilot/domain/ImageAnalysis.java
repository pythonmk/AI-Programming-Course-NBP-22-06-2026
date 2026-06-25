package pl.nbp.copilot.domain;

/**
 * Immutable value object holding the structured output of the multimodal
 * image-analysis LLM call.
 *
 * <p>This is a superset of all fields that may be populated; individual fields
 * are {@code null} when not applicable to the current scenario. For example,
 * {@code resellableAsNew} is relevant only for return requests, while
 * {@code damageType} is relevant only for complaint requests.
 *
 * <p>The raw image bytes are <strong>never</strong> stored here or anywhere
 * in the session — they are discarded after the LLM call that produces this record.
 *
 * @param damaged           tri-state: whether the equipment shows visible damage
 * @param damageType        free-text description of the damage type, or {@code null}
 * @param damageLocation    location on the equipment where damage is visible, or {@code null}
 * @param likelyCause       the analyzer's assessment of the damage cause, or {@code null}
 * @param resellableAsNew   tri-state: whether the item could be resold as new (return scenario)
 * @param signsOfUse        visible signs of wear or use, or {@code null}
 * @param missingElements   description of any missing parts/accessories, or {@code null}
 * @param packagingDamage   description of packaging damage, or {@code null}
 * @param confidence        overall confidence level of the analysis
 * @param summary           brief plain-text summary of the entire analysis
 */
public record ImageAnalysis(
        TriState damaged,
        String damageType,
        String damageLocation,
        String likelyCause,
        TriState resellableAsNew,
        String signsOfUse,
        String missingElements,
        String packagingDamage,
        Confidence confidence,
        String summary
) {}
