package pl.nbp.copilot.domain;

/**
 * Three-valued boolean used for image-analysis fields whose value cannot
 * always be determined with certainty from the available photo.
 *
 * <p>Used for fields such as {@link ImageAnalysis#damaged()} and
 * {@link ImageAnalysis#resellableAsNew()} where the analyzer may not be able
 * to give a definitive yes or no answer.
 */
public enum TriState {

    /** The property is definitively true. */
    TRUE,

    /** The property is definitively false. */
    FALSE,

    /** The property cannot be determined from the available information. */
    UNCERTAIN
}
