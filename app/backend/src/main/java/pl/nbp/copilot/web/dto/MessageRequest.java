package pl.nbp.copilot.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/sessions/{id}/messages}.
 *
 * @param message the customer's chat message; must not be blank
 */
public record MessageRequest(@NotBlank String message) {}
