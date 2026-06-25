package pl.nbp.copilot.domain;

import java.util.List;

/**
 * Immutable value object representing the AI-generated advisory decision on a
 * service request.
 *
 * <p>Produced by the decision LLM call and stored in the session. The
 * {@code firstMessageMarkdown} field is the assembled greeting + decision +
 * justification + next-steps + disclaimer rendered as markdown; it is sent
 * directly to the frontend as the opening chat message.
 *
 * @param category               the advisory decision outcome
 * @param justificationMarkdown  policy-grounded justification rendered as markdown
 * @param nextStepsMarkdown      recommended customer next steps rendered as markdown
 * @param citedRules             list of policy rule identifiers cited in the justification
 * @param firstMessageMarkdown   the complete opening chat bubble (greeting + all sections)
 */
public record Decision(
        DecisionCategory category,
        String justificationMarkdown,
        String nextStepsMarkdown,
        List<String> citedRules,
        String firstMessageMarkdown
) {}
