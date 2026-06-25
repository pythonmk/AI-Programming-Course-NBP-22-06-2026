package pl.nbp.copilot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.Confidence;
import pl.nbp.copilot.domain.DecisionCategory;
import pl.nbp.copilot.domain.ImageAnalysis;
import pl.nbp.copilot.domain.TriState;
import pl.nbp.copilot.domain.Decision;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link OutputParser} — no Spring context needed.
 *
 * <p>Covers: JSON extraction from clean strings, prose-wrapped strings,
 * code-fenced strings, decision category mapping (including unknown/lowercase),
 * and failure-mode fallbacks.
 */
@DisplayName("OutputParser — JSON extraction and mapping")
class OutputParserTest {

    private OutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new OutputParser(new ObjectMapper());
    }

    // -----------------------------------------------------------------------
    // ImageAnalysis — complaint variant
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Clean complaint JSON → correct ImageAnalysis fields")
    void parseImageAnalysis_cleanComplaintJson() {
        String raw = """
                {
                  "damaged": "true",
                  "damageType": "pęknięcie obudowy",
                  "damageLocation": "lewy narożnik",
                  "likelyCause": "uszkodzenie mechaniczne przez użytkownika",
                  "confidence": "HIGH",
                  "summary": "Urządzenie wykazuje wyraźne pęknięcie."
                }
                """;

        ImageAnalysis result = parser.parseImageAnalysis(raw);

        assertAll(
                () -> assertEquals(TriState.TRUE, result.damaged()),
                () -> assertEquals("pęknięcie obudowy", result.damageType()),
                () -> assertEquals("lewy narożnik", result.damageLocation()),
                () -> assertEquals("uszkodzenie mechaniczne przez użytkownika", result.likelyCause()),
                () -> assertEquals(Confidence.HIGH, result.confidence()),
                () -> assertEquals("Urządzenie wykazuje wyraźne pęknięcie.", result.summary())
        );
    }

    @Test
    @DisplayName("Complaint JSON wrapped in prose → extracted correctly")
    void parseImageAnalysis_jsonInProse() {
        String raw = """
                Oto wynik analizy:
                {
                  "damaged": "false",
                  "damageType": "",
                  "damageLocation": "",
                  "likelyCause": "",
                  "confidence": "MEDIUM",
                  "summary": "Brak widocznych uszkodzeń."
                }
                Mam nadzieję, że to pomoże.
                """;

        ImageAnalysis result = parser.parseImageAnalysis(raw);

        assertAll(
                () -> assertEquals(TriState.FALSE, result.damaged()),
                () -> assertEquals(Confidence.MEDIUM, result.confidence()),
                () -> assertEquals("Brak widocznych uszkodzeń.", result.summary())
        );
    }

    @Test
    @DisplayName("Complaint JSON in ```json code fence``` → extracted correctly")
    void parseImageAnalysis_jsonInCodeFence() {
        String raw = """
                ```json
                {
                  "damaged": "uncertain",
                  "damageType": "nieznany",
                  "damageLocation": "nieznana",
                  "likelyCause": "nieznana",
                  "confidence": "LOW",
                  "summary": "Zdjęcie jest niewyraźne."
                }
                ```
                """;

        ImageAnalysis result = parser.parseImageAnalysis(raw);

        assertAll(
                () -> assertEquals(TriState.UNCERTAIN, result.damaged()),
                () -> assertEquals(Confidence.LOW, result.confidence())
        );
    }

    @Test
    @DisplayName("Return variant JSON → correct resellableAsNew / signsOfUse fields")
    void parseImageAnalysis_returnVariantJson() {
        String raw = """
                {
                  "resellableAsNew": "false",
                  "signsOfUse": "true",
                  "missingElements": "false",
                  "packagingDamage": "true",
                  "confidence": "HIGH",
                  "summary": "Produkt nosi ślady użytkowania."
                }
                """;

        ImageAnalysis result = parser.parseImageAnalysis(raw);

        assertAll(
                () -> assertEquals(TriState.FALSE, result.resellableAsNew()),
                () -> assertEquals("true", result.signsOfUse()),
                () -> assertEquals("false", result.missingElements()),
                () -> assertEquals("true", result.packagingDamage()),
                () -> assertEquals(Confidence.HIGH, result.confidence()),
                () -> assertEquals("Produkt nosi ślady użytkowania.", result.summary())
        );
    }

    @Test
    @DisplayName("Malformed JSON on parseImageAnalysis → LOW-confidence fallback (no exception)")
    void parseImageAnalysis_malformedJson_returnsFallback() {
        String raw = "To nie jest JSON.";

        ImageAnalysis result = parser.parseImageAnalysis(raw);

        assertAll(
                () -> assertEquals(TriState.UNCERTAIN, result.damaged()),
                () -> assertEquals(Confidence.LOW, result.confidence()),
                () -> assertTrue(result.summary().contains("Analiza obrazu nie powiodła się"),
                        "Fallback summary must indicate parse failure")
        );
    }

    // -----------------------------------------------------------------------
    // Decision parsing
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Decision 'APPROVE' → DecisionCategory.APPROVE")
    void parseDecision_approve() {
        String raw = """
                {
                  "category": "APPROVE",
                  "justification": "Spełnione kryteria polityki.",
                  "nextSteps": "Prosimy dostarczyć urządzenie do serwisu.",
                  "citedRules": ["§3 ust. 1"]
                }
                """;

        Decision decision = parser.parseDecision(raw);

        assertEquals(DecisionCategory.APPROVE, decision.category());
    }

    @Test
    @DisplayName("Decision 'REJECT' → DecisionCategory.REJECT")
    void parseDecision_reject() {
        String raw = """
                {
                  "category": "REJECT",
                  "justification": "Uszkodzenie mechaniczne.",
                  "nextSteps": "Brak dalszych kroków.",
                  "citedRules": []
                }
                """;

        Decision decision = parser.parseDecision(raw);

        assertEquals(DecisionCategory.REJECT, decision.category());
    }

    @Test
    @DisplayName("Decision 'ESCALATE' → DecisionCategory.ESCALATE")
    void parseDecision_escalate() {
        String raw = """
                {
                  "category": "ESCALATE",
                  "justification": "Sprawa wymaga konsultanta.",
                  "nextSteps": "Skontaktuj się z konsultantem.",
                  "citedRules": []
                }
                """;

        Decision decision = parser.parseDecision(raw);

        assertEquals(DecisionCategory.ESCALATE, decision.category());
    }

    @Test
    @DisplayName("Decision 'UNKNOWN_VALUE' (unrecognised category) → DecisionCategory.ESCALATE")
    void parseDecision_unknownCategory_fallsBackToEscalate() {
        String raw = """
                {
                  "category": "UNKNOWN_VALUE",
                  "justification": "...",
                  "nextSteps": "...",
                  "citedRules": []
                }
                """;

        Decision decision = parser.parseDecision(raw);

        assertEquals(DecisionCategory.ESCALATE, decision.category());
    }

    @Test
    @DisplayName("Decision 'approve' (lowercase) → DecisionCategory.APPROVE")
    void parseDecision_lowercaseApprove() {
        String raw = """
                {
                  "category": "approve",
                  "justification": "OK.",
                  "nextSteps": "Proszę przynieść urządzenie.",
                  "citedRules": []
                }
                """;

        Decision decision = parser.parseDecision(raw);

        assertEquals(DecisionCategory.APPROVE, decision.category());
    }

    @Test
    @DisplayName("Malformed JSON on parseDecision → LlmParseException thrown")
    void parseDecision_malformedJson_throwsLlmParseException() {
        String raw = "To zdecydowanie nie jest JSON.";

        assertThrows(LlmParseException.class, () -> parser.parseDecision(raw));
    }
}
