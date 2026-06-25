package pl.nbp.copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code app.*} configuration namespace.
 *
 * <p>All values default via Spring placeholders in {@code application.yml};
 * the only mandatory runtime value is {@code OPENROUTER_API_KEY}.
 *
 * @param openrouter OpenRouter connection settings
 * @param llm        LLM model identifiers
 * @param publicUrl  SPA public URL sent as HTTP-Referer for OpenRouter attribution
 * @param title      Application title sent as X-Title for OpenRouter attribution
 * @param cors       CORS configuration
 * @param session    Session TTL settings
 * @param image      Image upload limits
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Openrouter openrouter,
        Llm llm,
        String publicUrl,
        String title,
        Cors cors,
        Session session,
        Image image
) {

    /**
     * OpenRouter connection settings.
     *
     * @param apiKey  API key (mapped from {@code OPENROUTER_API_KEY})
     * @param baseUrl base URL of the OpenRouter API
     */
    public record Openrouter(String apiKey, String baseUrl) {}

    /**
     * LLM model identifiers.
     *
     * @param modelVision   model used for multimodal image analysis
     * @param modelDecision model used for the decision and chat
     */
    public record Llm(String modelVision, String modelDecision) {}

    /**
     * CORS configuration.
     *
     * @param allowedOrigins comma-separated list of allowed SPA origins
     */
    public record Cors(String allowedOrigins) {}

    /**
     * Session configuration.
     *
     * @param ttlMinutes how long sessions are retained in-memory before eviction
     */
    public record Session(int ttlMinutes) {}

    /**
     * Image upload limits.
     *
     * @param maxBytes maximum accepted image size in bytes (default 5 MB)
     */
    public record Image(long maxBytes) {}
}
