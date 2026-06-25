package pl.nbp.copilot.web.dto;

/**
 * View DTO representing a single chat message in the session view response.
 *
 * @param role    author role: {@code "USER"} or {@code "ASSISTANT"}
 * @param content message text
 */
public record MessageView(String role, String content) {}
