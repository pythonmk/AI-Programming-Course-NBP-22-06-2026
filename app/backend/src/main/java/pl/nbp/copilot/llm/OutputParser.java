package pl.nbp.copilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.domain.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw LLM text output into structured domain objects.
 *
 * <p>Tolerant JSON extraction: strips surrounding prose and {@code ```} code
 * fences before parsing. Uses a simple first-{@code {}–last-{@code }} brace-
 * matching strategy to locate the JSON object.
 *
 * <p>Failure semantics (per ADR-003 TAC-304):
 * <ul>
 *   <li>{@link #parseImageAnalysis(String)} — on failure returns a
 *       LOW-confidence, all-uncertain {@link ImageAnalysis} that drives
 *       an {@code ESCALATE} outcome. Never throws.</li>
 *   <li>{@link #parseDecision(String)} — on failure throws
 *       {@link LlmParseException}; the caller maps this to HTTP 502.</li>
 * </ul>
 */
@Component
public class OutputParser {

    private static final Logger log = LoggerFactory.getLogger(OutputParser.class);

    private final ObjectMapper objectMapper;

    /**
     * Creates an {@code OutputParser} with the supplied Jackson mapper.
     *
     * @param objectMapper Jackson mapper used for JSON deserialization
     */
    public OutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parses raw LLM content into an {@link ImageAnalysis}.
     *
     * <p>On any parse failure a LOW-confidence, all-uncertain fallback is
     * returned so the downstream decision call escalates automatically.
     *
     * @param rawContent raw text from the LLM (may contain prose/code fences)
     * @return parsed {@link ImageAnalysis}, or a safe fallback on failure
     */
    public ImageAnalysis parseImageAnalysis(String rawContent) {
        try {
            String json = extractJson(rawContent);
            JsonNode node = objectMapper.readTree(json);

            TriState damaged = parseTriState(node, "damaged");
            String damageType = textOrEmpty(node, "damageType");
            String damageLocation = textOrEmpty(node, "damageLocation");
            String likelyCause = textOrEmpty(node, "likelyCause");

            TriState resellableAsNew = parseTriState(node, "resellableAsNew");
            String signsOfUse = textOrNull(node, "signsOfUse");
            String missingElements = textOrNull(node, "missingElements");
            String packagingDamage = textOrNull(node, "packagingDamage");

            Confidence confidence = parseConfidence(node);
            String summary = textOrEmpty(node, "summary");

            return new ImageAnalysis(
                    damaged, damageType, damageLocation, likelyCause,
                    resellableAsNew, signsOfUse, missingElements, packagingDamage,
                    confidence, summary
            );

        } catch (Exception e) {
            log.warn("Failed to parse ImageAnalysis from LLM output, returning fallback. Cause: {}", e.getMessage());
            return imageAnalysisFallback();
        }
    }

    /**
     * Parses raw LLM content into a {@link Decision}.
     *
     * <p>An unrecognised or missing category is mapped to
     * {@link DecisionCategory#ESCALATE}. On any structural parse failure a
     * {@link LlmParseException} is thrown so the caller can return HTTP 502.
     *
     * @param rawContent raw text from the LLM (may contain prose/code fences)
     * @return parsed {@link Decision}
     * @throws LlmParseException if the JSON cannot be extracted or parsed
     */
    public Decision parseDecision(String rawContent) {
        String json;
        try {
            json = extractJson(rawContent);
        } catch (Exception e) {
            throw new LlmParseException("Unable to extract JSON from decision response: " + e.getMessage(), e);
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            DecisionCategory category = parseDecisionCategory(node);
            String justification = textOrEmpty(node, "justification");
            String nextSteps = textOrEmpty(node, "nextSteps");
            List<String> citedRules = parseCitedRules(node);

            // firstMessageMarkdown is assembled by the service layer (ADR-003 §7.5),
            // not by the LLM — set to empty here; the caller fills it in.
            return new Decision(category, justification, nextSteps, citedRules, "");

        } catch (LlmParseException rethrow) {
            throw rethrow;
        } catch (Exception e) {
            throw new LlmParseException("Failed to map decision JSON to domain object: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // JSON extraction
    // -----------------------------------------------------------------------

    /**
     * Extracts the first complete JSON object ({@code { ... }}) from a string
     * that may be surrounded by prose or enclosed in Markdown code fences.
     *
     * @param raw input text possibly containing a JSON object
     * @return the extracted JSON substring
     * @throws LlmParseException if no balanced JSON object can be found
     */
    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmParseException("LLM returned blank content");
        }

        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');

        if (start == -1 || end == -1 || end <= start) {
            throw new LlmParseException("No JSON object found in LLM response: " + truncate(raw));
        }

        return raw.substring(start, end + 1);
    }

    // -----------------------------------------------------------------------
    // Field helpers
    // -----------------------------------------------------------------------

    private TriState parseTriState(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        String value = node.get(fieldName).asText("").strip().toLowerCase();
        return switch (value) {
            case "true" -> TriState.TRUE;
            case "false" -> TriState.FALSE;
            default -> TriState.UNCERTAIN;
        };
    }

    private Confidence parseConfidence(JsonNode node) {
        if (!node.has("confidence")) {
            return Confidence.LOW;
        }
        String value = node.get("confidence").asText("").strip().toUpperCase();
        return switch (value) {
            case "MEDIUM" -> Confidence.MEDIUM;
            case "HIGH" -> Confidence.HIGH;
            default -> Confidence.LOW;
        };
    }

    private DecisionCategory parseDecisionCategory(JsonNode node) {
        if (!node.has("category")) {
            return DecisionCategory.ESCALATE;
        }
        String value = node.get("category").asText("").strip().toUpperCase();
        return switch (value) {
            case "APPROVE" -> DecisionCategory.APPROVE;
            case "REJECT" -> DecisionCategory.REJECT;
            default -> DecisionCategory.ESCALATE;
        };
    }

    private List<String> parseCitedRules(JsonNode node) {
        List<String> rules = new ArrayList<>();
        if (node.has("citedRules") && node.get("citedRules").isArray()) {
            node.get("citedRules").forEach(element -> rules.add(element.asText()));
        }
        return rules;
    }

    private String textOrEmpty(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText("");
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asText(null);
    }

    // -----------------------------------------------------------------------
    // Fallback
    // -----------------------------------------------------------------------

    private ImageAnalysis imageAnalysisFallback() {
        return new ImageAnalysis(
                TriState.UNCERTAIN,
                "",
                "",
                "",
                TriState.UNCERTAIN,
                null,
                null,
                null,
                Confidence.LOW,
                "Analiza obrazu nie powiodła się."
        );
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
