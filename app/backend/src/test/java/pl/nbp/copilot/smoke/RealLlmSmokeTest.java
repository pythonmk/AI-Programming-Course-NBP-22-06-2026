package pl.nbp.copilot.smoke;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pl.nbp.copilot.domain.*;
import pl.nbp.copilot.llm.LlmClient;
import pl.nbp.copilot.policy.PolicyProvider;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real OpenRouter integration smoke test.
 *
 * <p>Requires {@code OPENROUTER_API_KEY} to be set in the environment.
 * Annotated with {@code @EnabledIfEnvironmentVariable} so the test is
 * <em>skipped</em> (not failed) when the key is absent — it only runs
 * when explicitly triggered (e.g. {@code mvn test -Dgroups=smoke}).
 *
 * <p>Run with:
 * <pre>
 *   OPENROUTER_API_KEY=&lt;key&gt; ./mvnw test -Dgroups=smoke
 * </pre>
 */
@Tag("smoke")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class RealLlmSmokeTest {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private PolicyProvider policyProvider;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a minimal 10×10 white JPEG encoded as a base64 data URL.
     * Small enough to stay well within the model's context limits.
     */
    private static String minimalJpegDataUrl() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "JPEG", baos);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private static CaseData smokeComplaintCase() {
        return new CaseData(
                RequestType.COMPLAINT,
                EquipmentCategory.LAPTOP,
                "ThinkPad X1 — smoke test",
                LocalDate.now().minusDays(30),
                "Ekran przestał działać"
        );
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void analyzeImage_realCall_returnsValidAnalysis() throws Exception {
        String dataUrl = minimalJpegDataUrl();
        CaseData caseData = smokeComplaintCase();

        ImageAnalysis result = llmClient.analyzeImage(RequestType.COMPLAINT, caseData, dataUrl);

        assertThat(result).isNotNull();
        assertThat(result.confidence()).isNotNull();
        assertThat(result.summary()).isNotBlank();
    }

    @Test
    void decide_realCall_returnsValidDecision() throws Exception {
        String dataUrl = minimalJpegDataUrl();
        CaseData caseData = smokeComplaintCase();

        ImageAnalysis analysis = llmClient.analyzeImage(RequestType.COMPLAINT, caseData, dataUrl);

        // 30 days since purchase: outside 14-day withdrawal, within 2-year non-conformity
        EligibilityWindows windows = new EligibilityWindows(30, false, true);

        String policyDoc = policyProvider.getPolicyDocument(RequestType.COMPLAINT);
        String legalRules = policyProvider.getLegalRules();

        Decision decision = llmClient.decide(
                RequestType.COMPLAINT, caseData, analysis, windows, policyDoc, legalRules
        );

        assertThat(decision).isNotNull();
        assertThat(decision.category()).isNotNull();
        assertThat(decision.justificationMarkdown()).isNotBlank();
        assertThat(decision.firstMessageMarkdown()).isNotBlank();
    }
}
