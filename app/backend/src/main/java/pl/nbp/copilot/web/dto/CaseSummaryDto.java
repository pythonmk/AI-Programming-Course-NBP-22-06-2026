package pl.nbp.copilot.web.dto;

/**
 * Compact summary of a processed service case, sent to the frontend as part of
 * {@link CaseResult} to populate the chat-screen header badge.
 *
 * @param requestType       enum name of the request type (e.g. {@code "COMPLAINT"}, {@code "RETURN"})
 * @param equipmentCategory enum name of the equipment category (e.g. {@code "LAPTOP"})
 * @param equipmentName     free-text name/model of the equipment entered by the customer
 * @param decisionCategory  enum name of the advisory decision (e.g. {@code "APPROVE"})
 */
public record CaseSummaryDto(
        String requestType,
        String equipmentCategory,
        String equipmentName,
        String decisionCategory
) {}
