package pl.nbp.copilot.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import pl.nbp.copilot.domain.DecisionCategory;
import pl.nbp.copilot.image.ImageTooLargeException;
import pl.nbp.copilot.llm.LlmParseException;
import pl.nbp.copilot.service.CaseService;
import pl.nbp.copilot.web.dto.CaseResult;
import pl.nbp.copilot.web.dto.CaseSummaryDto;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link CaseController}.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer. {@link CaseService} is
 * mocked with {@code @MockBean}. All validation failures must be returned as 400
 * without calling the service (TAC-101).
 */
@WebMvcTest(CaseController.class)
@DisplayName("CaseController")
class CaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CaseService caseService;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a minimal valid JPEG byte array (SOI + EOI markers).
     * Good enough for content-type validation tests — not a real image,
     * but has the correct magic bytes that identify it as JPEG.
     */
    private static byte[] createMinimalJpegBytes() {
        // FF D8 = SOI marker, FF D9 = EOI marker — smallest valid JPEG
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    }

    private MockMultipartFile validJpegImage() {
        return new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", createMinimalJpegBytes());
    }

    private CaseResult sampleCaseResult() {
        return new CaseResult(
                "test-session-123",
                DecisionCategory.APPROVE,
                "## Dzień dobry!\n\nDziękujemy za przesłanie reklamacji...",
                new CaseSummaryDto("COMPLAINT", "LAPTOP", "Dell XPS 15", "APPROVE")
        );
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Valid COMPLAINT with all fields returns 201 with sessionId")
    void submitCase_validComplaint_returns201WithSessionId() throws Exception {
        when(caseService.processCase(any(), any())).thenReturn(sampleCaseResult());

        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString())
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("test-session-123"))
                .andExpect(jsonPath("$.decisionCategory").value("APPROVE"));
    }

    @Test
    @DisplayName("Valid RETURN without reason returns 201")
    void submitCase_validReturn_noReason_returns201() throws Exception {
        when(caseService.processCase(any(), any())).thenReturn(sampleCaseResult());

        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusDays(5).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // Validation failures — required fields
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Missing equipmentName returns 400 with fieldErrors — no LLM call")
    void submitCase_missingEquipmentName_returns400() throws Exception {
        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString())
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).processCase(any(), any());
    }

    @Test
    @DisplayName("Missing purchaseDate returns 400 — no LLM call")
    void submitCase_missingPurchaseDate_returns400() throws Exception {
        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isBadRequest());

        verify(caseService, never()).processCase(any(), any());
    }

    @Test
    @DisplayName("Future purchaseDate returns 400 — no LLM call")
    void submitCase_futurePurchaseDate_returns400() throws Exception {
        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().plusDays(1).toString())
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).processCase(any(), any());
    }

    // -----------------------------------------------------------------------
    // Conditional reason validation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("COMPLAINT without reason returns 400 with field error on reason — no LLM call")
    void submitCase_complaintWithoutReason_returns400() throws Exception {
        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("reason"));

        verify(caseService, never()).processCase(any(), any());
    }

    @Test
    @DisplayName("RETURN without reason returns 201 — reason is optional for RETURN")
    void submitCase_returnWithoutReason_returns201() throws Exception {
        when(caseService.processCase(any(), any())).thenReturn(sampleCaseResult());

        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusDays(5).toString()))
                .andExpect(status().isCreated());
    }

    // -----------------------------------------------------------------------
    // Enum validation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Invalid requestType enum value returns 400 — no LLM call")
    void submitCase_invalidRequestTypeEnum_returns400() throws Exception {
        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "INVALID")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString())
                        .param("reason", "Opis problemu."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENUM_VALUE"));

        verify(caseService, never()).processCase(any(), any());
    }

    // -----------------------------------------------------------------------
    // Image validation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Empty image returns 400 — no LLM call")
    void submitCase_emptyImage_returns400() throws Exception {
        MockMultipartFile emptyImage = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/cases")
                        .file(emptyImage)
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString())
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).processCase(any(), any());
    }

    @Test
    @DisplayName("Wrong image content type (text/plain) returns 400 — no LLM call")
    void submitCase_wrongImageContentType_returns400() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "image", "file.txt", "text/plain", "not an image".getBytes());

        mockMvc.perform(multipart("/api/cases")
                        .file(textFile)
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString())
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(caseService, never()).processCase(any(), any());
    }

    // -----------------------------------------------------------------------
    // Service exception mapping
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ImageTooLargeException from CaseService returns 400 with IMAGE_TOO_LARGE code")
    void submitCase_imageTooLarge_returns400WithImageTooLargeCode() throws Exception {
        when(caseService.processCase(any(), any()))
                .thenThrow(new ImageTooLargeException(6_000_000L, 5_242_880L));

        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString())
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMAGE_TOO_LARGE"));
    }

    @Test
    @DisplayName("LlmParseException from CaseService returns 502 with LLM_ERROR code")
    void submitCase_llmParseException_returns502WithLlmErrorCode() throws Exception {
        when(caseService.processCase(any(), any()))
                .thenThrow(new LlmParseException("Cannot parse LLM response"));

        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString())
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("LLM_ERROR"));
    }

    @Test
    @DisplayName("Generic RuntimeException from CaseService returns 503 with SERVICE_UNAVAILABLE code")
    void submitCase_genericRuntimeException_returns503WithServiceUnavailableCode() throws Exception {
        when(caseService.processCase(any(), any()))
                .thenThrow(new RuntimeException("Network timeout"));

        mockMvc.perform(multipart("/api/cases")
                        .file(validJpegImage())
                        .param("requestType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("equipmentName", "Dell XPS 15")
                        .param("purchaseDate", LocalDate.now().minusMonths(6).toString())
                        .param("reason", "Klawiatura przestała działać."))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }
}
