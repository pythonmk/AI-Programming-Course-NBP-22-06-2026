package pl.nbp.copilot.domain;

import java.time.Instant;

/**
 * Immutable value object representing a single turn in the chat conversation.
 *
 * <p>Messages are stored in an ordered list within {@link Session}. Assistant
 * messages carry markdown-formatted content; user messages carry plain text.
 *
 * @param role      the author of the message
 * @param content   message text; markdown for {@link Role#ASSISTANT},
 *                  plain text for {@link Role#USER}
 * @param timestamp the moment the message was created or received
 */
public record ChatMessage(
        Role role,
        String content,
        Instant timestamp
) {

    /**
     * Author role for a chat message.
     */
    public enum Role {

        /** Message authored by the human customer. */
        USER,

        /** Message authored by the AI assistant. */
        ASSISTANT,

        /** System-level instruction message (not visible to the customer). */
        SYSTEM
    }
}
