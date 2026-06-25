package pl.nbp.copilot.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link MetaController}.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer. Verifies that
 * {@code GET /api/meta/form-options} returns HTTP 200 with the correct
 * {@code requestTypes} and {@code equipmentCategories} arrays.
 */
@WebMvcTest(MetaController.class)
@DisplayName("MetaController")
class MetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/meta/form-options returns 200 with 2 requestTypes")
    void getFormOptions_returns200_with2RequestTypes() throws Exception {
        mockMvc.perform(get("/api/meta/form-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestTypes").isArray())
                .andExpect(jsonPath("$.requestTypes.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/meta/form-options includes COMPLAINT with Polish label")
    void getFormOptions_includesComplaint_withPolishLabel() throws Exception {
        mockMvc.perform(get("/api/meta/form-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestTypes[?(@.value == 'COMPLAINT')].labelPl").value("Reklamacja"));
    }

    @Test
    @DisplayName("GET /api/meta/form-options includes RETURN with Polish label")
    void getFormOptions_includesReturn_withPolishLabel() throws Exception {
        mockMvc.perform(get("/api/meta/form-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestTypes[?(@.value == 'RETURN')].labelPl").value("Zwrot"));
    }

    @Test
    @DisplayName("GET /api/meta/form-options returns 200 with 8 equipmentCategories")
    void getFormOptions_returns200_with8EquipmentCategories() throws Exception {
        mockMvc.perform(get("/api/meta/form-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentCategories").isArray())
                .andExpect(jsonPath("$.equipmentCategories.length()").value(8));
    }

    @Test
    @DisplayName("GET /api/meta/form-options includes LAPTOP with Polish label")
    void getFormOptions_includesLaptop_withPolishLabel() throws Exception {
        mockMvc.perform(get("/api/meta/form-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentCategories[?(@.value == 'LAPTOP')].labelPl").value("Laptopy"));
    }

    @Test
    @DisplayName("GET /api/meta/form-options includes all 8 equipment category values")
    void getFormOptions_includesAllEquipmentCategoryValues() throws Exception {
        mockMvc.perform(get("/api/meta/form-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentCategories[?(@.value == 'DESKTOP')]").exists())
                .andExpect(jsonPath("$.equipmentCategories[?(@.value == 'MONITOR')]").exists())
                .andExpect(jsonPath("$.equipmentCategories[?(@.value == 'PERIPHERALS')]").exists())
                .andExpect(jsonPath("$.equipmentCategories[?(@.value == 'PC_COMPONENTS')]").exists())
                .andExpect(jsonPath("$.equipmentCategories[?(@.value == 'NETWORKING')]").exists())
                .andExpect(jsonPath("$.equipmentCategories[?(@.value == 'ACCESSORIES')]").exists())
                .andExpect(jsonPath("$.equipmentCategories[?(@.value == 'OTHER')]").exists());
    }
}
