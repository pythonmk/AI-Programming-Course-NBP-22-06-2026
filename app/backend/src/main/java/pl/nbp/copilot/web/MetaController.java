package pl.nbp.copilot.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.RequestType;
import pl.nbp.copilot.web.dto.FormOptionsResponse;
import pl.nbp.copilot.web.dto.OptionDto;

import java.util.Arrays;
import java.util.List;

/**
 * Metadata endpoint: supplies localised form option lists to the frontend.
 *
 * <p>Exposes {@code GET /api/meta/form-options} returning all {@link RequestType}
 * and {@link EquipmentCategory} values with their Polish display labels.
 * The lists are built dynamically from enum {@code values()} so adding a new
 * enum constant automatically appears in the response without any changes here.
 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    /**
     * Returns all available form options with Polish labels.
     *
     * <p>Both lists are ordered by their natural enum declaration order.
     *
     * @return {@code 200 OK} with {@link FormOptionsResponse}
     */
    @GetMapping("/form-options")
    public ResponseEntity<FormOptionsResponse> formOptions() {
        List<OptionDto> requestTypes = Arrays.stream(RequestType.values())
                .map(rt -> new OptionDto(rt.name(), rt.labelPl()))
                .toList();

        List<OptionDto> equipmentCategories = Arrays.stream(EquipmentCategory.values())
                .map(ec -> new OptionDto(ec.name(), ec.labelPl()))
                .toList();

        return ResponseEntity.ok(new FormOptionsResponse(requestTypes, equipmentCategories));
    }
}
