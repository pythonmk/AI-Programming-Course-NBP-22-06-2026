package pl.nbp.copilot.web.dto;

import pl.nbp.copilot.domain.DecisionCategory;

/**
 * Response payload returned by the {@code POST /api/cases} endpoint after a case
 * has been fully processed (image analysis + decision + session creation).
 *
 * <p>Carries everything the frontend needs to render the initial chat screen:
 * the session identifier for follow-up calls, the decision outcome, the complete
 * first assistant message already formatted in Markdown, and a compact summary
 * for the chat-header badge.
 *
 * @param sessionId            opaque UUID string identifying the server-side session;
 *                             must be passed in the {@code X-Session-Id} header for
 *                             subsequent chat requests
 * @param decisionCategory     advisory outcome of the case
 * @param firstMessageMarkdown the complete opening chat bubble rendered as Polish Markdown
 *                             (greeting → decision → justification → next steps → disclaimer)
 * @param caseSummary          compact case details for the chat-screen header
 */
public record CaseResult(
        String sessionId,
        DecisionCategory decisionCategory,
        String firstMessageMarkdown,
        CaseSummaryDto caseSummary
) {}
