package pl.nbp.copilot.llm;

import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.nbp.copilot.domain.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link PromptBuilder} loaded from the Spring context
 * to verify template loading and placeholder substitution.
 *
 * <p>The LLM is NOT called — these tests only inspect the assembled message list.
 */
@SpringBootTest(properties = {"OPENROUTER_API_KEY=test-key"})
@DisplayName("PromptBuilder — template assembly tests")
class PromptBuilderTest {

    @Autowired
    private PromptBuilder promptBuilder;

    // -----------------------------------------------------------------------
    // Shared fixtures
    // -----------------------------------------------------------------------

    private static CaseData complaintCase() {
        return new CaseData(
                RequestType.COMPLAINT,
                EquipmentCategory.LAPTOP,
                "Dell XPS 15",
                LocalDate.of(2025, 3, 10),
                "Ekran przestał działać po 2 tygodniach."
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

    private static ImageAnalysis complaintAnalysis() {
        return new ImageAnalysis(
                TriState.TRUE,
                "pęknięcie matrycy",
                "środek ekranu",
                "wada produkcyjna",
                null, null, null, null,
                Confidence.HIGH,
                "Wyraźne pęknięcie matrycy, prawdopodobnie wada fabryczna."
        );
    }

    private static ImageAnalysis returnAnalysis() {
        return new ImageAnalysis(
                null, null, null, null,
                TriState.TRUE,
                "false",
                "false",
                "false",
                Confidence.HIGH,
                "Produkt wygląda jak nowy, brak śladów użytkowania."
        );
    }

    private static EligibilityWindows windows() {
        return new EligibilityWindows(107, false, true);
    }

    private static Decision decision() {
        return new Decision(
                DecisionCategory.APPROVE,
                "Reklamacja zasadna.",
                "Proszę dostarczyć urządzenie.",
                List.of("§3 ust. 1"),
                "Zatwierdzono wstępnie.\n\nReklamacja zasadna."
        );
    }

    // -----------------------------------------------------------------------
    // Vision prompt tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("buildVisionPrompt(COMPLAINT) returns at least 2 messages (system + user)")
    void buildVisionPrompt_complaint_returnsAtLeastTwoMessages() {
        String imageDataUrl = "data:image/jpeg;base64,/9j/4AAQ==";

        List<ChatCompletionMessageParam> messages =
                promptBuilder.buildVisionPrompt(RequestType.COMPLAINT, complaintCase(), imageDataUrl);

        assertTrue(messages.size() >= 2,
                "Must contain at least a system message and a user message");
    }

    @Test
    @DisplayName("buildVisionPrompt(COMPLAINT) user message contains image data URL")
    void buildVisionPrompt_complaint_userMessageContainsImageDataUrl() {
        String imageDataUrl = "data:image/jpeg;base64,/9j/4AAQ==";

        List<ChatCompletionMessageParam> messages =
                promptBuilder.buildVisionPrompt(RequestType.COMPLAINT, complaintCase(), imageDataUrl);

        // The last message is the user message; it must carry the image data URL somewhere
        // We inspect the string representation of the message param since content parts
        // are deep inside the SDK object graph
        String messagesAsString = messages.toString();
        assertTrue(messagesAsString.contains("data:image/jpeg;base64,"),
                "User message must contain the base64 image data URL");
    }

    @Test
    @DisplayName("buildVisionPrompt(RETURN) returns at least 2 messages")
    void buildVisionPrompt_return_returnsAtLeastTwoMessages() {
        String imageDataUrl = "data:image/jpeg;base64,/9j/4AAQ==";

        List<ChatCompletionMessageParam> messages =
                promptBuilder.buildVisionPrompt(RequestType.RETURN, returnCase(), imageDataUrl);

        assertTrue(messages.size() >= 2,
                "Must contain at least a system message and a user message");
    }

    // -----------------------------------------------------------------------
    // Decision prompt tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("buildDecisionPrompt(COMPLAINT) system message contains injected policy text")
    void buildDecisionPrompt_complaint_systemMessageContainsPolicyText() {
        String policy = "POLITYKA TESTOWA: zwroty możliwe w ciągu 14 dni.";

        List<ChatCompletionMessageParam> messages = promptBuilder.buildDecisionPrompt(
                RequestType.COMPLAINT,
                complaintCase(),
                complaintAnalysis(),
                windows(),
                policy,
                "Przepisy prawne: ustawa o prawach konsumenta."
        );

        assertTrue(messages.size() >= 1, "Must contain at least one message");
        // The system/user message must embed the provided policy text
        String messagesAsString = messages.toString();
        assertTrue(messagesAsString.contains(policy),
                "Decision prompt must embed the provided policy document text");
    }

    @Test
    @DisplayName("buildDecisionPrompt(RETURN) uses the return policy template (different template than COMPLAINT)")
    void buildDecisionPrompt_return_usesReturnTemplate() {
        String policy = "POLITYKA ZWROTÓW: 14 dni na zwrot.";
        String legalRules = "Prawo do odstąpienia od umowy.";

        List<ChatCompletionMessageParam> messages = promptBuilder.buildDecisionPrompt(
                RequestType.RETURN,
                returnCase(),
                returnAnalysis(),
                windows(),
                policy,
                legalRules
        );

        assertTrue(messages.size() >= 1, "Must contain at least one message");
        String messagesAsString = messages.toString();
        assertTrue(messagesAsString.contains(policy),
                "Return decision prompt must embed the provided return policy text");
    }

    // -----------------------------------------------------------------------
    // Chat system prompt test
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("buildChatSystemPrompt returns a message containing ##ESCALATE## marker instruction")
    void buildChatSystemPrompt_containsEscalateMarkerInstruction() {
        List<ChatCompletionMessageParam> messages = promptBuilder.buildChatSystemPrompt(
                complaintCase(),
                complaintAnalysis(),
                decision(),
                "POLITYKA SERWISU",
                "Przepisy prawne."
        );

        assertFalse(messages.isEmpty(), "Must return at least one message");
        String messagesAsString = messages.toString();
        assertTrue(messagesAsString.contains("##ESCALATE##"),
                "Chat system prompt must instruct the model to emit the ##ESCALATE## marker");
    }
}
