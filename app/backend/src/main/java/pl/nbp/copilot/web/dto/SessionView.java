package pl.nbp.copilot.web.dto;

import java.util.List;

/**
 * View DTO representing the full state of a session, returned by
 * {@code GET /api/sessions/{id}}.
 *
 * @param sessionId        opaque session identifier
 * @param decisionCategory current decision category name (e.g. {@code "APPROVE"}, {@code "REJECT"},
 *                         {@code "ESCALATE"}) — may differ from the initial decision if the session
 *                         has transitioned from {@code REJECT} to {@code ESCALATE}
 * @param messages         ordered list of chat messages exchanged in this session
 * @param caseSummary      compact summary of the submitted case for display in the chat header
 */
public record SessionView(
        String sessionId,
        String decisionCategory,
        List<MessageView> messages,
        CaseSummaryDto caseSummary
) {}
