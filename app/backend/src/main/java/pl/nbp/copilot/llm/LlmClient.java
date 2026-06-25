package pl.nbp.copilot.llm;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.nbp.copilot.config.AppProperties;
import pl.nbp.copilot.domain.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring service wrapping the synchronous {@link OpenAIClient} for the two
 * non-streaming LLM calls: multimodal image analysis and advisory decision.
 *
 * <p>Per ADR-003:
 * <ul>
 *   <li>Both calls use the Chat Completions API — never the Responses API.</li>
 *   <li>Attribution headers ({@code HTTP-Referer}, {@code X-Title}) are set
 *       per-request on the params builder, not on the client bean.</li>
 *   <li>On parse failure a single re-ask is made with a stricter "JSON only"
 *       instruction. After two failures: {@link #analyzeImage} returns a
 *       LOW-confidence fallback; {@link #decide} propagates
 *       {@link LlmParseException} (→ HTTP 502).</li>
 * </ul>
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    /** Appended as an extra user turn when the first parse attempt fails. */
    private static final String RE_ASK_MESSAGE =
            "Odpowiedz wyłącznie poprawnym JSON-em bez żadnego dodatkowego tekstu.";

    private final OpenAIClient client;
    private final PromptBuilder promptBuilder;
    private final OutputParser outputParser;
    private final AppProperties appProperties;

    /**
     * Creates a new {@code LlmClient}.
     *
     * @param client         synchronous OpenAI-compatible client targeting OpenRouter
     * @param promptBuilder  assembles message lists from templates
     * @param outputParser   parses raw LLM text into domain objects
     * @param appProperties  application properties (model IDs, attribution metadata)
     */
    public LlmClient(
            OpenAIClient client,
            PromptBuilder promptBuilder,
            OutputParser outputParser,
            AppProperties appProperties
    ) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.outputParser = outputParser;
        this.appProperties = appProperties;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Calls the vision model to analyze the submitted image.
     *
     * <p>On parse failure one re-ask is made with a stricter JSON-only
     * instruction. If the second call also yields a LOW-confidence result the
     * fallback is returned rather than throwing — LOW confidence automatically
     * drives an {@code ESCALATE} decision downstream.
     *
     * @param requestType  whether this is a complaint or a return
     * @param caseData     customer's submitted form data
     * @param imageDataUrl base64 data URL, e.g. {@code data:image/jpeg;base64,...}
     * @return parsed {@link ImageAnalysis}, or a LOW-confidence fallback on persistent failure
     */
    public ImageAnalysis analyzeImage(
            RequestType requestType,
            CaseData caseData,
            String imageDataUrl
    ) {
        List<ChatCompletionMessageParam> messages =
                promptBuilder.buildVisionPrompt(requestType, caseData, imageDataUrl);

        String model = appProperties.llm().modelVision();
        String content = callCompletions(model, messages);
        ImageAnalysis result = outputParser.parseImageAnalysis(content);

        if (result.confidence() == Confidence.LOW) {
            log.warn("analyzeImage: first attempt returned LOW confidence, re-asking");
            List<ChatCompletionMessageParam> messagesWithReAsk = appendReAsk(messages);
            String reAskContent = callCompletions(model, messagesWithReAsk);
            result = outputParser.parseImageAnalysis(reAskContent);
            if (result.confidence() == Confidence.LOW) {
                log.warn("analyzeImage: second attempt also returned LOW confidence, returning fallback");
            }
        }

        return result;
    }

    /**
     * Calls the decision model to produce an advisory APPROVE / REJECT / ESCALATE outcome.
     *
     * <p>On parse failure one re-ask is made. If the second call also fails,
     * {@link LlmParseException} is thrown so the caller can return HTTP 502.
     *
     * @param requestType    whether this is a complaint or a return
     * @param caseData       customer's submitted form data
     * @param analysis       structured result from the preceding vision call
     * @param windows        computed eligibility time windows
     * @param policyDocument relevant policy document text
     * @param legalRules     summary of applicable legal rules
     * @return parsed {@link Decision}
     * @throws LlmParseException if parsing fails after the re-ask
     */
    public Decision decide(
            RequestType requestType,
            CaseData caseData,
            ImageAnalysis analysis,
            EligibilityWindows windows,
            String policyDocument,
            String legalRules
    ) {
        List<ChatCompletionMessageParam> messages = promptBuilder.buildDecisionPrompt(
                requestType, caseData, analysis, windows, policyDocument, legalRules);

        String model = appProperties.llm().modelDecision();
        String content = callCompletions(model, messages);

        try {
            return outputParser.parseDecision(content);
        } catch (LlmParseException firstFailure) {
            log.warn("decide: first parse attempt failed ({}), re-asking", firstFailure.getMessage());
            List<ChatCompletionMessageParam> messagesWithReAsk = appendReAsk(messages);
            String reAskContent = callCompletions(model, messagesWithReAsk);
            // Second failure propagates to the caller → HTTP 502
            return outputParser.parseDecision(reAskContent);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Invokes the Chat Completions endpoint synchronously and returns the
     * assistant message content of the first choice.
     *
     * <p>Attribution headers are attached to this request if the corresponding
     * property values are non-blank. IO errors propagate as-is.
     *
     * @param model    model identifier string (e.g. {@code anthropic/claude-sonnet-4.6})
     * @param messages the message list to send
     * @return raw content string from the first choice
     */
    private String callCompletions(String model, List<ChatCompletionMessageParam> messages) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages);

        String publicUrl = appProperties.publicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            builder.putAdditionalHeader("HTTP-Referer", publicUrl);
        }

        String title = appProperties.title();
        if (title != null && !title.isBlank()) {
            builder.putAdditionalHeader("X-Title", title);
        }

        ChatCompletionCreateParams params = builder.build();
        ChatCompletion completion = client.chat().completions().create(params);
        return completion.choices().get(0).message().content().orElse("");
    }

    /**
     * Returns a new message list that appends the re-ask instruction as an
     * extra user turn after the existing messages.
     *
     * @param original the original message list
     * @return a new list with the re-ask user message appended
     */
    private List<ChatCompletionMessageParam> appendReAsk(List<ChatCompletionMessageParam> original) {
        List<ChatCompletionMessageParam> extended = new ArrayList<>(original);
        ChatCompletionUserMessageParam reAskParam = ChatCompletionUserMessageParam.builder()
                .content(RE_ASK_MESSAGE)
                .build();
        extended.add(ChatCompletionMessageParam.ofUser(reAskParam));
        return extended;
    }
}
