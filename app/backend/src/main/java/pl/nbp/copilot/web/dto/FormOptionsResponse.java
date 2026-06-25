package pl.nbp.copilot.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/meta/form-options}.
 *
 * <p>Provides localised option lists for the intake form so the frontend can
 * render Polish labels without hardcoding them.
 *
 * @param requestTypes         available request types ({@link pl.nbp.copilot.domain.RequestType} values)
 * @param equipmentCategories  available equipment categories ({@link pl.nbp.copilot.domain.EquipmentCategory} values)
 */
public record FormOptionsResponse(List<OptionDto> requestTypes, List<OptionDto> equipmentCategories) {}
