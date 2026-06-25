package pl.nbp.copilot.llm;

import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.domain.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Assembles LLM message lists from externalized prompt templates.
 *
 * <p>Templates are loaded from {@code classpath:/prompts/} at startup via
 * {@link PostConstruct}. Placeholder substitution uses simple string
 * replacement of {@code {placeholder}} tokens.
 *
 * <p>For the multimodal vision call, the user message contains two content
 * parts: an image part (base64 data URL) and a text part (the filled
 * template).
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private String visionComplaintTemplate;
    private String visionReturnTemplate;
    private String decisionComplaintTemplate;
    private String decisionReturnTemplate;
    private String chatSystemTemplate;

    /**
     * Loads all prompt templates from the classpath at application startup.
     *
     * @throws IllegalStateException if any template file cannot be read
     */
    @PostConstruct
    public void loadTemplates() {
        visionComplaintTemplate   = readTemplate("prompts/vision-complaint.txt");
        visionReturnTemplate      = readTemplate("prompts/vision-return.txt");
        decisionComplaintTemplate = readTemplate("prompts/decision-complaint.txt");
        decisionReturnTemplate    = readTemplate("prompts/decision-return.txt");
        chatSystemTemplate        = readTemplate("prompts/chat-system.txt");
        log.info("PromptBuilder: all prompt templates loaded successfully");
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Builds the message list for the multimodal image-analysis call.
     *
     * <p>Returns {@code [systemMessage, userMessage]} where the user message
     * contains two content parts: the base64 image and the text instruction.
     *
     * @param requestType  whether this is a complaint or a return analysis
     * @param caseData     the customer's submitted form data
     * @param imageDataUrl base64 data URL, e.g. {@code data:image/jpeg;base64,...}
     * @return immutable list with system message and multimodal user message
     */
    public List<ChatCompletionMessageParam> buildVisionPrompt(
            RequestType requestType,
            CaseData caseData,
            String imageDataUrl
    ) {
        String template = requestType == RequestType.COMPLAINT
                ? visionComplaintTemplate
                : visionReturnTemplate;

        String filledText = template
                .replace("{equipmentCategory}", caseData.equipmentCategory().labelPl())
                .replace("{equipmentName}", caseData.equipmentName())
                .replace("{customerReason}", nvl(caseData.reason()));

        ChatCompletionSystemMessageParam systemMessage = ChatCompletionSystemMessageParam.builder()
                .content("Jesteś specjalistycznym analizatorem obrazów urządzeń elektronicznych. Odpowiadaj wyłącznie w formacie JSON.")
                .build();

        // Build multimodal user message: image part + text part
        ChatCompletionContentPart imagePart = ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder()
                        .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                .url(imageDataUrl)
                                .build())
                        .build()
        );

        ChatCompletionContentPart textPart = ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder()
                        .text(filledText)
                        .build()
        );

        ChatCompletionUserMessageParam userMessage = ChatCompletionUserMessageParam.builder()
                .contentOfArrayOfContentParts(List.of(imagePart, textPart))
                .build();

        return List.of(
                ChatCompletionMessageParam.ofSystem(systemMessage),
                ChatCompletionMessageParam.ofUser(userMessage)
        );
    }

    /**
     * Builds the message list for the decision-making call.
     *
     * <p>Returns {@code [systemMessage, userMessage]} where the user message
     * contains the fully populated decision template.
     *
     * @param requestType    whether this is a complaint or a return decision
     * @param caseData       the customer's submitted form data
     * @param analysis       structured result from the preceding vision call
     * @param windows        computed eligibility time windows
     * @param policyDocument relevant policy document text
     * @param legalRules     summary of applicable legal rules
     * @return immutable list with system message and user message
     */
    public List<ChatCompletionMessageParam> buildDecisionPrompt(
            RequestType requestType,
            CaseData caseData,
            ImageAnalysis analysis,
            EligibilityWindows windows,
            String policyDocument,
            String legalRules
    ) {
        String template = requestType == RequestType.COMPLAINT
                ? decisionComplaintTemplate
                : decisionReturnTemplate;

        String imageAnalysisText = formatImageAnalysis(analysis);

        String filledText = template
                .replace("{equipmentName}", caseData.equipmentName())
                .replace("{equipmentCategory}", caseData.equipmentCategory().labelPl())
                .replace("{purchaseDate}", caseData.purchaseDate().toString())
                .replace("{customerReason}", nvl(caseData.reason()))
                .replace("{imageAnalysis}", imageAnalysisText)
                .replace("{withinWithdrawalWindow}", String.valueOf(windows.withinWithdrawalWindow()))
                .replace("{withinNonConformityWindow}", String.valueOf(windows.withinNonConformityWindow()))
                .replace("{policyDocument}", nvl(policyDocument))
                .replace("{legalRules}", nvl(legalRules));

        ChatCompletionSystemMessageParam systemMessage = ChatCompletionSystemMessageParam.builder()
                .content("Jesteś doradczym systemem decyzyjnym dla działu serwisu sprzętu elektronicznego. Odpowiadaj wyłącznie w formacie JSON.")
                .build();

        ChatCompletionUserMessageParam userMessage = ChatCompletionUserMessageParam.builder()
                .content(filledText)
                .build();

        return List.of(
                ChatCompletionMessageParam.ofSystem(systemMessage),
                ChatCompletionMessageParam.ofUser(userMessage)
        );
    }

    /**
     * Builds the system message for the chat interaction.
     *
     * <p>Returns a single-element list containing the system message.
     * The caller appends the conversation history and the latest user turn
     * before submitting to the LLM.
     *
     * @param caseData       the customer's submitted form data
     * @param analysis       structured result from the vision call
     * @param decision       the initial advisory decision
     * @param policyDocument relevant policy document text
     * @param legalRules     summary of applicable legal rules
     * @return single-element list containing the chat system message
     */
    public List<ChatCompletionMessageParam> buildChatSystemPrompt(
            CaseData caseData,
            ImageAnalysis analysis,
            Decision decision,
            String policyDocument,
            String legalRules
    ) {
        String filledText = chatSystemTemplate
                .replace("{equipmentName}", caseData.equipmentName())
                .replace("{equipmentCategory}", caseData.equipmentCategory().labelPl())
                .replace("{decisionCategory}", decision.category().labelPl())
                .replace("{firstMessageMarkdown}", nvl(decision.firstMessageMarkdown()))
                .replace("{policyDocument}", nvl(policyDocument))
                .replace("{legalRules}", nvl(legalRules))
                .replace("{imageAnalysisSummary}", nvl(analysis.summary()));

        ChatCompletionSystemMessageParam systemMessage = ChatCompletionSystemMessageParam.builder()
                .content(filledText)
                .build();

        return List.of(ChatCompletionMessageParam.ofSystem(systemMessage));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String readTemplate(String classpathPath) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathPath);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load prompt template from classpath: " + classpathPath, e);
        }
    }

    private String formatImageAnalysis(ImageAnalysis analysis) {
        if (analysis == null) {
            return "Brak analizy obrazu.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Pewność analizy: ").append(analysis.confidence()).append("\n");
        sb.append("Podsumowanie: ").append(nvl(analysis.summary())).append("\n");
        if (analysis.damaged() != null) {
            sb.append("Uszkodzenie: ").append(analysis.damaged()).append("\n");
        }
        if (analysis.damageType() != null && !analysis.damageType().isBlank()) {
            sb.append("Rodzaj uszkodzenia: ").append(analysis.damageType()).append("\n");
        }
        if (analysis.damageLocation() != null && !analysis.damageLocation().isBlank()) {
            sb.append("Lokalizacja: ").append(analysis.damageLocation()).append("\n");
        }
        if (analysis.likelyCause() != null && !analysis.likelyCause().isBlank()) {
            sb.append("Prawdopodobna przyczyna: ").append(analysis.likelyCause()).append("\n");
        }
        if (analysis.resellableAsNew() != null) {
            sb.append("Nadaje się do odsprzedaży jako nowy: ").append(analysis.resellableAsNew()).append("\n");
        }
        if (analysis.signsOfUse() != null) {
            sb.append("Ślady użytkowania: ").append(analysis.signsOfUse()).append("\n");
        }
        if (analysis.missingElements() != null) {
            sb.append("Brakujące elementy: ").append(analysis.missingElements()).append("\n");
        }
        if (analysis.packagingDamage() != null) {
            sb.append("Uszkodzenie opakowania: ").append(analysis.packagingDamage()).append("\n");
        }
        return sb.toString().trim();
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
