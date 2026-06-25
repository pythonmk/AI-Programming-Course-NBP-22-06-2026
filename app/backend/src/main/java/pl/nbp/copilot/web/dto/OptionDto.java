package pl.nbp.copilot.web.dto;

/**
 * Single selectable option for a form field.
 *
 * <p>Carries the stable machine-readable {@code value} (enum name) and the
 * localised Polish display label returned by the metadata endpoint.
 *
 * @param value   enum name (e.g. {@code "COMPLAINT"}, {@code "LAPTOP"})
 * @param labelPl Polish display label (e.g. {@code "Reklamacja"})
 */
public record OptionDto(String value, String labelPl) {}
