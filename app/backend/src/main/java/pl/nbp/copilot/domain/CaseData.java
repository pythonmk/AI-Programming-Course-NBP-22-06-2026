package pl.nbp.copilot.domain;

import java.time.LocalDate;

/**
 * Immutable value object representing the customer's submitted service request form.
 *
 * <p>Populated from the {@code POST /api/cases} multipart request and stored in
 * the session for the lifetime of the interaction. Raw image bytes are never stored
 * here — the image is compressed, sent to the multimodal model, and discarded.
 *
 * @param requestType       type of request: complaint or return
 * @param equipmentCategory category of the affected equipment
 * @param equipmentName     free-text name/model of the equipment
 * @param purchaseDate      date the equipment was purchased; must not be in the future
 * @param reason            description of the issue; required for {@link RequestType#COMPLAINT},
 *                          optional (nullable) for {@link RequestType#RETURN}
 */
public record CaseData(
        RequestType requestType,
        EquipmentCategory equipmentCategory,
        String equipmentName,
        LocalDate purchaseDate,
        String reason
) {}
