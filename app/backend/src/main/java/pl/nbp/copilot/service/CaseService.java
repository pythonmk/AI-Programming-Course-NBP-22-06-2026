package pl.nbp.copilot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.nbp.copilot.domain.*;
import pl.nbp.copilot.image.ImageCompressor;
import pl.nbp.copilot.image.ImageTooLargeException;
import pl.nbp.copilot.llm.LlmClient;
import pl.nbp.copilot.llm.LlmParseException;
import pl.nbp.copilot.policy.PolicyProvider;
import pl.nbp.copilot.session.SessionRepository;
import pl.nbp.copilot.web.dto.CaseResult;
import pl.nbp.copilot.web.dto.CaseSummaryDto;

import java.time.Instant;

/**
 * Spring service that orchestrates the full case-submission pipeline:
 * image compression → multimodal image analysis → eligibility-window computation →
 * policy loading → advisory decision → first-message assembly → session creation.
 *
 * <p>The pipeline is deliberately synchronous and linear (ADR-003 §6): two sequential
 * Chat Completions calls produce {@link ImageAnalysis} then {@link Decision}; their
 * results are combined into a {@link pl.nbp.copilot.domain.Session} which is persisted
 * via the {@link SessionRepository} abstraction.
 *
 * <p>Exception policy:
 * <ul>
 *   <li>{@link ImageTooLargeException} — propagates unchanged; the controller
 *       maps it to HTTP 400 (Bad Request).</li>
 *   <li>{@link LlmParseException} — propagates unchanged; the controller maps it
 *       to HTTP 502 (Bad Gateway) since the LLM returned unusable output.</li>
 * </ul>
 */
@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    /**
     * Mandatory advisory disclaimer text (PRD §11.6).
     * Every first chat message must include this verbatim.
     */
    private static final String MANDATORY_DISCLAIMER =
            "Niniejsza ocena ma charakter wstępny i niewiążący. Została wygenerowana automatycznie " +
            "i nie stanowi ostatecznej decyzji w sprawie reklamacji/zwrotu ani porady prawnej. " +
            "Nie ogranicza ustawowych praw konsumenta.";

    private final LlmClient llmClient;
    private final ImageCompressor imageCompressor;
    private final PolicyProvider policyProvider;
    private final EligibilityService eligibilityService;
    private final SessionRepository sessionRepository;

    /**
     * Creates a new {@code CaseService} with all required collaborators.
     *
     * @param llmClient          LLM client for image analysis and decision calls
     * @param imageCompressor    compresses raw image bytes into a JPEG data URL
     * @param policyProvider     provides policy and legal-rules documents
     * @param eligibilityService computes eligibility time-windows from the purchase date
     * @param sessionRepository  persists the session after the pipeline completes
     */
    public CaseService(
            LlmClient llmClient,
            ImageCompressor imageCompressor,
            PolicyProvider policyProvider,
            EligibilityService eligibilityService,
            SessionRepository sessionRepository
    ) {
        this.llmClient = llmClient;
        this.imageCompressor = imageCompressor;
        this.policyProvider = policyProvider;
        this.eligibilityService = eligibilityService;
        this.sessionRepository = sessionRepository;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Processes a customer service case end-to-end and returns the initial result.
     *
     * <p>Orchestration steps:
     * <ol>
     *   <li>Compress the raw image bytes into a JPEG data URL.</li>
     *   <li>Analyze the image using the multimodal LLM call.</li>
     *   <li>Compute eligibility windows from the purchase date.</li>
     *   <li>Load the applicable policy document and legal-rules text.</li>
     *   <li>Invoke the decision LLM call.</li>
     *   <li>Assemble the {@code firstMessageMarkdown} (ADR-003 §7.5, PRD §11.5–11.6).</li>
     *   <li>Create and persist a {@link Session} containing the case data, analysis,
     *       decision, and the first assistant message.</li>
     * </ol>
     *
     * @param caseData   validated form data submitted by the customer
     * @param imageBytes raw image bytes from the multipart upload (before compression)
     * @return {@link CaseResult} carrying the session ID, decision category,
     *         first message markdown, and case summary
     * @throws ImageTooLargeException if the compressed image still exceeds the configured limit
     * @throws LlmParseException      if the decision LLM returns unparseable output after retry
     */
    public CaseResult processCase(CaseData caseData, byte[] imageBytes) {
        log.info("processCase: requestType={} equipment='{}' purchaseDate={}",
                caseData.requestType(), caseData.equipmentName(), caseData.purchaseDate());

        // Step 1 — compress image (propagates ImageTooLargeException on failure)
        String imageDataUrl = imageCompressor.compress(imageBytes);

        // Step 2 — multimodal image analysis
        ImageAnalysis analysis = llmClient.analyzeImage(
                caseData.requestType(), caseData, imageDataUrl);

        // Step 3 — eligibility windows
        EligibilityWindows windows = eligibilityService.compute(caseData.purchaseDate());

        // Step 4 — load policy and legal rules
        String policyDocument = policyProvider.getPolicyDocument(caseData.requestType());
        String legalRules = policyProvider.getLegalRules();

        // Step 5 — advisory decision (propagates LlmParseException after retry)
        Decision decision = llmClient.decide(
                caseData.requestType(), caseData, analysis, windows, policyDocument, legalRules);

        // Step 6 — assemble firstMessageMarkdown
        String firstMessageMarkdown = assembleFirstMessage(caseData, decision);

        // Step 7 — create and persist session
        Session session = new Session(
                null,                           // ID assigned by repository
                caseData,
                windows,
                analysis,
                decision,
                decision.category(),
                null,                           // createdAt assigned by repository
                null                            // expiresAt assigned by repository
        );
        session.addMessage(new ChatMessage(
                ChatMessage.Role.ASSISTANT,
                firstMessageMarkdown,
                Instant.now()
        ));
        Session savedSession = sessionRepository.save(session);

        log.info("processCase: session created sessionId={} decision={}",
                savedSession.getSessionId(), decision.category());

        // Step 8 — return
        return new CaseResult(
                savedSession.getSessionId(),
                decision.category(),
                firstMessageMarkdown,
                new CaseSummaryDto(
                        caseData.requestType().name(),
                        caseData.equipmentCategory().name(),
                        caseData.equipmentName(),
                        decision.category().name()
                )
        );
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Assembles the opening chat message in Polish Markdown, per ADR-003 §7.5 and PRD §11.5–11.6.
     *
     * <p>Structure (in order):
     * <ol>
     *   <li>Greeting with equipment name and request type label.</li>
     *   <li>Decision label in bold.</li>
     *   <li>Justification markdown from the decision.</li>
     *   <li>Next steps markdown from the decision.</li>
     *   <li>Mandatory advisory disclaimer (PRD §11.6).</li>
     * </ol>
     *
     * @param caseData customer form data
     * @param decision LLM-produced advisory decision
     * @return complete first message markdown string, never {@code null}
     */
    private String assembleFirstMessage(CaseData caseData, Decision decision) {
        String requestTypeLabel = requestTypeLabel(caseData.requestType());
        String decisionLabel = decisionLabel(decision.category());

        return "## Dzień dobry!\n\n" +
               "Dziękujemy za przesłanie " + requestTypeLabel +
               " dla urządzenia **" + caseData.equipmentName() + "**.\n\n" +
               "### Wstępna ocena: " + decisionLabel + "\n\n" +
               decision.justificationMarkdown() + "\n\n" +
               "### Następne kroki\n\n" +
               decision.nextStepsMarkdown() + "\n\n" +
               "---\n\n" +
               "*" + MANDATORY_DISCLAIMER + "*";
    }

    /**
     * Returns the Polish genitive label used in the greeting for the request type.
     *
     * @param requestType the type of request
     * @return Polish genitive label string
     */
    private String requestTypeLabel(RequestType requestType) {
        return switch (requestType) {
            case COMPLAINT -> "reklamacji";
            case RETURN -> "zwrotu";
        };
    }

    /**
     * Returns the bold Polish label for the decision category, as defined in PRD §11.5.
     *
     * @param category the advisory decision category
     * @return bold markdown Polish label
     */
    private String decisionLabel(DecisionCategory category) {
        return "**" + category.labelPl() + "**";
    }
}
