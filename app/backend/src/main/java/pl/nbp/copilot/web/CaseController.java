package pl.nbp.copilot.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nbp.copilot.domain.CaseData;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.RequestType;
import pl.nbp.copilot.service.CaseService;
import pl.nbp.copilot.web.dto.CaseResult;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for case submission.
 *
 * <p>Accepts multipart form submissions at {@code POST /api/cases}, validates all
 * input server-side, and delegates to {@link CaseService} for the full processing
 * pipeline (image compression → image analysis → eligibility check → advisory
 * decision → session creation).
 *
 * <p>All user-facing validation messages are in Polish. Error responses follow the
 * envelope defined in ADR-000 §6 and produced by {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/cases")
@Validated
public class CaseController {

    private static final List<String> ALLOWED_IMAGE_TYPES =
            List.of("image/jpeg", "image/png", "application/octet-stream");

    private final CaseService caseService;

    /**
     * Creates a new {@code CaseController} with the required service dependency.
     *
     * @param caseService orchestrates the full case-submission pipeline
     */
    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    /**
     * Submits a new hardware service case (complaint or return) for an advisory decision.
     *
     * <p>Validation rules enforced:
     * <ol>
     *   <li>All required fields must be present and non-blank.</li>
     *   <li>{@code purchaseDate} must not be in the future.</li>
     *   <li>{@code reason} is required when {@code requestType == COMPLAINT}.</li>
     *   <li>The image must not be empty and must be JPEG or PNG.</li>
     *   <li>{@code requestType} and {@code equipmentCategory} must be valid enum values.</li>
     * </ol>
     *
     * @param requestType       request type string (COMPLAINT or RETURN)
     * @param equipmentCategory equipment category string (enum constant name)
     * @param equipmentName     free-text name/model of the equipment (1–200 chars)
     * @param purchaseDate      date the equipment was purchased; must not be in the future
     * @param reason            description of the issue; required for COMPLAINT
     * @param image             product image (JPEG or PNG, non-empty)
     * @return the case result with session ID, decision, and first message markdown
     * @throws IOException if reading the image bytes fails
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResult submitCase(
            @RequestParam @NotBlank String requestType,
            @RequestParam @NotBlank String equipmentCategory,
            @RequestParam @NotBlank @Size(min = 1, max = 200) String equipmentName,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate,
            @RequestParam(required = false) String reason,
            @RequestParam("image") MultipartFile image
    ) throws IOException {

        // Parse and validate enums
        RequestType rt = parseRequestType(requestType);
        EquipmentCategory ec = parseEquipmentCategory(equipmentCategory);

        // Validate purchaseDate is not in the future
        if (purchaseDate.isAfter(LocalDate.now())) {
            throw new ValidationException("purchaseDate", "Data zakupu nie może być w przyszłości.");
        }

        // Validate reason is present for COMPLAINT
        if (rt == RequestType.COMPLAINT && (reason == null || reason.isBlank())) {
            throw new ValidationException("reason",
                    "Opis problemu jest wymagany dla reklamacji.");
        }

        // Validate image is present and non-empty
        if (image == null || image.isEmpty()) {
            throw new ValidationException("image", "Obraz nie może być pusty.");
        }

        // Validate image content type
        String contentType = image.getContentType();
        if (!isAllowedImageType(contentType)) {
            throw new ValidationException("image",
                    "Dozwolone formaty obrazu to: JPEG, PNG.");
        }

        CaseData caseData = new CaseData(rt, ec, equipmentName, purchaseDate, reason);
        byte[] imageBytes = image.getBytes();

        return caseService.processCase(caseData, imageBytes);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Parses the request type string into a {@link RequestType} enum.
     *
     * @param value the raw string value from the request parameter
     * @return the parsed enum constant
     * @throws InvalidEnumValueException if the value is not a valid {@link RequestType}
     */
    private RequestType parseRequestType(String value) {
        try {
            return RequestType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidEnumValueException("requestType", value);
        }
    }

    /**
     * Parses the equipment category string into an {@link EquipmentCategory} enum.
     *
     * @param value the raw string value from the request parameter
     * @return the parsed enum constant
     * @throws InvalidEnumValueException if the value is not a valid {@link EquipmentCategory}
     */
    private EquipmentCategory parseEquipmentCategory(String value) {
        try {
            return EquipmentCategory.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidEnumValueException("equipmentCategory", value);
        }
    }

    /**
     * Checks whether the given content type is an allowed image type.
     *
     * @param contentType the MIME type from the uploaded file
     * @return {@code true} if the type is JPEG, PNG, or {@code application/octet-stream}
     */
    private boolean isAllowedImageType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase());
    }
}
