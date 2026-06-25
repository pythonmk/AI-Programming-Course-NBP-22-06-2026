package pl.nbp.copilot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.config.AppProperties;
import pl.nbp.copilot.domain.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link LlmClient} using {@link MockWebServer} to mock
 * the HTTP boundary. The {@link PromptBuilder} and {@link OutputParser} beans
 * are real instances; only the HTTP transport is intercepted.
 *
 * <p>Covers:
 * <ul>
 *   <li>Happy-path analyzeImage and decide calls.</li>
 *   <li>Re-ask on first malformed response.</li>
 *   <li>Fallback / exception when both calls fail.</li>
 *   <li>Attribution headers ({@code HTTP-Referer}, {@code X-Title}).</li>
 *   <li>TAC-306: no request path contains {@code /responses}.</li>
 * </ul>
 */
@DisplayName("LlmClient — synchronous calls (MockWebServer)")
class LlmClientTest {

    private MockWebServer mockWebServer;
    private LlmClient llmClient;
    private PromptBuilder promptBuilder;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        OpenAIClient mockClient = OpenAIOkHttpClient.builder()
                .baseUrl(mockWebServer.url("/v1").toString())
                .apiKey("test-key")
                .build();

        promptBuilder = new PromptBuilder();
        promptBuilder.loadTemplates();

        OutputParser outputParser = new OutputParser(new ObjectMapper());

        AppProperties props = testProperties(
                "https://copilot.example.com",
                "Hardware Copilot"
        );

        llmClient = new LlmClient(mockClient, promptBuilder, outputParser, props);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // -----------------------------------------------------------------------
    // analyzeImage — happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("analyzeImage happy path: valid JSON response → ImageAnalysis with HIGH confidence")
    void analyzeImage_happyPath_returnsHighConfidence() throws InterruptedException {
        String analysisJson = """
                {
                  "damaged": "true",
                  "damageType": "pęknięcie matrycy",
                  "damageLocation": "środek ekranu",
                  "likelyCause": "wada produkcyjna",
                  "confidence": "HIGH",
                  "summary": "Wyraźne pęknięcie matrycy."
                }
                """;
        mockWebServer.enqueue(chatResponse(analysisJson));

        ImageAnalysis result = llmClient.analyzeImage(
                RequestType.COMPLAINT, complaintCase(), "data:image/jpeg;base64,/9j/4AAQ==");

        assertEquals(Confidence.HIGH, result.confidence());
        assertEquals(TriState.TRUE, result.damaged());
        assertEquals("pęknięcie matrycy", result.damageType());

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("/chat/completions"),
                "Request path must use /chat/completions, was: " + request.getPath());
    }

    // -----------------------------------------------------------------------
    // analyzeImage — re-ask on first malformed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("analyzeImage re-ask: first response malformed, second valid → returns correct ImageAnalysis")
    void analyzeImage_firstMalformed_reAsksAndReturnsValidResult() throws InterruptedException {
        // First response: no JSON → parser returns LOW confidence fallback
        mockWebServer.enqueue(chatResponse("Przepraszam, nie mogę teraz odpowiedzieć."));

        // Second response: valid JSON
        String analysisJson = """
                {
                  "damaged": "false",
                  "confidence": "MEDIUM",
                  "summary": "Brak widocznych uszkodzeń."
                }
                """;
        mockWebServer.enqueue(chatResponse(analysisJson));

        ImageAnalysis result = llmClient.analyzeImage(
                RequestType.COMPLAINT, complaintCase(), "data:image/jpeg;base64,/9j/4AAQ==");

        assertEquals(Confidence.MEDIUM, result.confidence());
        assertEquals(TriState.FALSE, result.damaged());

        // Two requests must have been made
        assertEquals(2, mockWebServer.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // analyzeImage — both calls malformed → fallback
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("analyzeImage both malformed: returns LOW-confidence fallback, no exception thrown")
    void analyzeImage_bothMalformed_returnsFallback() {
        mockWebServer.enqueue(chatResponse("nie ma tu żadnego JSONa"));
        mockWebServer.enqueue(chatResponse("też nie ma"));

        ImageAnalysis result = llmClient.analyzeImage(
                RequestType.COMPLAINT, complaintCase(), "data:image/jpeg;base64,/9j/4AAQ==");

        assertEquals(Confidence.LOW, result.confidence(),
                "Must return LOW-confidence fallback, not throw");
        assertEquals(2, mockWebServer.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // decide — happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("decide happy path: valid JSON response → Decision with APPROVE")
    void decide_happyPath_returnsApproveDecision() throws InterruptedException {
        String decisionJson = """
                {
                  "category": "APPROVE",
                  "justification": "Reklamacja zasadna w świetle polityki serwisu.",
                  "nextSteps": "Proszę dostarczyć urządzenie do serwisu.",
                  "citedRules": ["§3 ust. 1", "art. 43b"]
                }
                """;
        mockWebServer.enqueue(chatResponse(decisionJson));

        Decision result = llmClient.decide(
                RequestType.COMPLAINT,
                complaintCase(),
                highConfidenceAnalysis(),
                windows(),
                "POLITYKA SERWISU",
                "Przepisy prawne."
        );

        assertEquals(DecisionCategory.APPROVE, result.category());
        assertFalse(result.justificationMarkdown().isBlank());
        assertEquals(2, result.citedRules().size());

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("/chat/completions"),
                "Request path must use /chat/completions, was: " + request.getPath());
    }

    // -----------------------------------------------------------------------
    // decide — re-ask on first malformed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("decide re-ask: first response malformed, second valid → returns correct Decision")
    void decide_firstMalformed_reAsksAndReturnsDecision() {
        // First response: prose, no JSON
        mockWebServer.enqueue(chatResponse("Przepraszam, odpowiedź jest niemożliwa."));

        // Second response: valid JSON
        String decisionJson = """
                {
                  "category": "REJECT",
                  "justification": "Poza oknem rękojmi.",
                  "nextSteps": "Brak dalszych kroków.",
                  "citedRules": []
                }
                """;
        mockWebServer.enqueue(chatResponse(decisionJson));

        Decision result = llmClient.decide(
                RequestType.COMPLAINT,
                complaintCase(),
                highConfidenceAnalysis(),
                windows(),
                "POLITYKA",
                "PRZEPISY"
        );

        assertEquals(DecisionCategory.REJECT, result.category());
        assertEquals(2, mockWebServer.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // decide — both calls malformed → LlmParseException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("decide both malformed: throws LlmParseException")
    void decide_bothMalformed_throwsLlmParseException() {
        mockWebServer.enqueue(chatResponse("błędna odpowiedź numer 1"));
        mockWebServer.enqueue(chatResponse("błędna odpowiedź numer 2"));

        assertThrows(LlmParseException.class, () ->
                llmClient.decide(
                        RequestType.COMPLAINT,
                        complaintCase(),
                        highConfidenceAnalysis(),
                        windows(),
                        "POLITYKA",
                        "PRZEPISY"
                )
        );
        assertEquals(2, mockWebServer.getRequestCount());
    }

    // -----------------------------------------------------------------------
    // Attribution headers
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Attribution headers: HTTP-Referer and X-Title are present when configured")
    void attributionHeaders_presentWhenConfigured() throws InterruptedException {
        String analysisJson = """
                { "confidence": "HIGH", "summary": "OK", "damaged": "false" }
                """;
        mockWebServer.enqueue(chatResponse(analysisJson));

        llmClient.analyzeImage(
                RequestType.COMPLAINT, complaintCase(), "data:image/jpeg;base64,/9j/4AAQ==");

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("https://copilot.example.com", request.getHeader("HTTP-Referer"),
                "HTTP-Referer header must match APP_PUBLIC_URL");
        assertEquals("Hardware Copilot", request.getHeader("X-Title"),
                "X-Title header must match APP_TITLE");
    }

    @Test
    @DisplayName("Attribution headers: omitted when APP_PUBLIC_URL and APP_TITLE are blank")
    void attributionHeaders_omittedWhenBlank() throws InterruptedException {
        // Re-create llmClient with blank attribution properties
        AppProperties blankProps = testProperties("", "");
        OpenAIClient mockClient = OpenAIOkHttpClient.builder()
                .baseUrl(mockWebServer.url("/v1").toString())
                .apiKey("test-key")
                .build();
        LlmClient clientWithBlankProps = new LlmClient(
                mockClient,
                promptBuilder,
                new OutputParser(new ObjectMapper()),
                blankProps
        );

        String analysisJson = """
                { "confidence": "HIGH", "summary": "OK", "damaged": "false" }
                """;
        mockWebServer.enqueue(chatResponse(analysisJson));

        clientWithBlankProps.analyzeImage(
                RequestType.COMPLAINT, complaintCase(), "data:image/jpeg;base64,/9j/4AAQ==");

        RecordedRequest request = mockWebServer.takeRequest();
        assertNull(request.getHeader("HTTP-Referer"),
                "HTTP-Referer must be absent when APP_PUBLIC_URL is blank");
        assertNull(request.getHeader("X-Title"),
                "X-Title must be absent when APP_TITLE is blank");
    }

    // -----------------------------------------------------------------------
    // TAC-306: no /responses path used
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TAC-306: request path uses /chat/completions, not /responses")
    void tac306_usesOnlyChatCompletionsPath() throws InterruptedException {
        String analysisJson = """
                { "confidence": "HIGH", "summary": "test", "damaged": "false" }
                """;
        mockWebServer.enqueue(chatResponse(analysisJson));

        llmClient.analyzeImage(
                RequestType.RETURN, returnCase(), "data:image/jpeg;base64,abc123==");

        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();
        assertNotNull(path);
        assertFalse(path.contains("/responses"),
                "Must not use the Responses API — path was: " + path);
        assertTrue(path.contains("/chat/completions"),
                "Must use Chat Completions API — path was: " + path);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a mock HTTP response that wraps the given content string in a
     * standard Chat Completion JSON envelope.
     *
     * @param content the assistant message content to embed
     * @return a {@link MockResponse} returning 200 with the envelope body
     */
    private static MockResponse chatResponse(String content) {
        String escapedContent = escapeJsonString(content);
        String body = """
                {
                  "id": "test-id",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "anthropic/claude-sonnet-4-6",
                  "choices": [{
                    "index": 0,
                    "message": { "role": "assistant", "content": %s },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 10,
                    "total_tokens": 20
                  }
                }
                """.formatted(escapedContent);
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /**
     * Escapes a raw string value into a JSON string literal (with surrounding
     * double-quotes). Handles newlines, tabs, and backslashes.
     *
     * @param value the raw string to escape
     * @return JSON-encoded string literal including surrounding quotes
     */
    private static String escapeJsonString(String value) {
        // Use Jackson to produce a properly-escaped JSON string
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to JSON-escape string", e);
        }
    }

    private static CaseData complaintCase() {
        return new CaseData(
                RequestType.COMPLAINT,
                EquipmentCategory.LAPTOP,
                "Dell XPS 15",
                LocalDate.of(2025, 3, 10),
                "Ekran przestał działać."
        );
    }

    private static CaseData returnCase() {
        return new CaseData(
                RequestType.RETURN,
                EquipmentCategory.LAPTOP,
                "Lenovo ThinkPad X1",
                LocalDate.of(2026, 6, 20),
                null
        );
    }

    private static ImageAnalysis highConfidenceAnalysis() {
        return new ImageAnalysis(
                TriState.TRUE,
                "pęknięcie matrycy",
                "środek ekranu",
                "wada produkcyjna",
                null, null, null, null,
                Confidence.HIGH,
                "Wyraźne uszkodzenie."
        );
    }

    private static EligibilityWindows windows() {
        return new EligibilityWindows(107, false, true);
    }

    private static AppProperties testProperties(String publicUrl, String title) {
        return new AppProperties(
                new AppProperties.Openrouter("test-key", "http://localhost"),
                new AppProperties.Llm(
                        "anthropic/claude-sonnet-4-6",
                        "anthropic/claude-sonnet-4-6"
                ),
                publicUrl,
                title,
                new AppProperties.Cors("http://localhost:4200"),
                new AppProperties.Session(60),
                new AppProperties.Image(5_242_880L)
        );
    }
}
