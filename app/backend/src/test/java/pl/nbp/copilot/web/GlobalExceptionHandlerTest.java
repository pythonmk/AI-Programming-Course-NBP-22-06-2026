package pl.nbp.copilot.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit test for {@link GlobalExceptionHandler}.
 *
 * <p>Uses {@code MockMvcBuilders.standaloneSetup} to wire the advice and the
 * {@link StubController} together without loading a Spring application context.
 * This is the preferred pattern for testing {@code @RestControllerAdvice}
 * in isolation when the controller under test is a test-only stub.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    // -----------------------------------------------------------------------
    // Stub controller — lives in test sources only, never in production
    // -----------------------------------------------------------------------

    /**
     * Minimal REST controller used only in tests to trigger exception paths.
     */
    @RestController
    @RequestMapping("/test-stub")
    static class StubController {

        record ValidatedBody(@NotBlank(message = "Pole jest wymagane.") String name) {}

        /**
         * Triggers a {@code MethodArgumentNotValidException} when {@code name} is blank.
         */
        @PostMapping("/validate")
        public String validate(@Valid @RequestBody ValidatedBody body) {
            return "ok";
        }

        /**
         * Always throws an unexpected {@link RuntimeException}.
         */
        @PostMapping("/explode")
        public String explode() {
            throw new RuntimeException("Unexpected kaboom");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // -----------------------------------------------------------------------
    // Tests — validation failure (400)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST with blank name returns 400 with VALIDATION_ERROR code")
    void postInvalidBody_returns400_withValidationErrorCode() throws Exception {
        mockMvc.perform(post("/test-stub/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST with blank name returns Polish messagePl")
    void postInvalidBody_returnsPolishMessagePl() throws Exception {
        mockMvc.perform(post("/test-stub/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.messagePl").value("Formularz zawiera błędy walidacji."));
    }

    @Test
    @DisplayName("POST with blank name returns fieldErrors with field name and Polish message")
    void postInvalidBody_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test-stub/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors").isArray())
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.error.fieldErrors[0].messagePl").value("Pole jest wymagane."));
    }

    // -----------------------------------------------------------------------
    // Tests — RuntimeException (503 SERVICE_UNAVAILABLE)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST to exploding endpoint returns 503 with SERVICE_UNAVAILABLE code")
    void postExplodingEndpoint_returns503_withServiceUnavailableCode() throws Exception {
        mockMvc.perform(post("/test-stub/explode")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("POST to exploding endpoint returns Polish messagePl for SERVICE_UNAVAILABLE")
    void postExplodingEndpoint_returns503_withPolishMessagePl() throws Exception {
        mockMvc.perform(post("/test-stub/explode")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.messagePl").value("Usługa tymczasowo niedostępna. Spróbuj ponownie za chwilę."));
    }

    @Test
    @DisplayName("POST to exploding endpoint does not expose fieldErrors")
    void postExplodingEndpoint_doesNotExposeFieldErrors() throws Exception {
        mockMvc.perform(post("/test-stub/explode")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.fieldErrors").doesNotExist());
    }
}
