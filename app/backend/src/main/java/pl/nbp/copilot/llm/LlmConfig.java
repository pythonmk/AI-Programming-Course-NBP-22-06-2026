package pl.nbp.copilot.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.nbp.copilot.config.AppProperties;

/**
 * Spring configuration that registers OpenAI client beans pointed at
 * the OpenRouter API base URL.
 *
 * <p>Both a synchronous {@link OpenAIClient} (used for image analysis and
 * decision calls) and an asynchronous {@link OpenAIClientAsync} (used for
 * streamed chat) are registered.
 *
 * <p>Per ADR-003: attribution headers ({@code HTTP-Referer}, {@code X-Title})
 * are NOT set on the client; they are added per-request via
 * {@code putAdditionalHeader} on the params builder.
 */
@Configuration
public class LlmConfig {

    /**
     * Creates a synchronous OpenAI-compatible client targeting OpenRouter.
     *
     * @param props application properties supplying the base URL and API key
     * @return configured synchronous {@link OpenAIClient}
     */
    @Bean
    public OpenAIClient openAIClient(AppProperties props) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(props.openrouter().baseUrl())
                .apiKey(props.openrouter().apiKey())
                .build();
    }

    /**
     * Creates an asynchronous OpenAI-compatible client targeting OpenRouter,
     * used for streamed chat completions.
     *
     * @param props application properties supplying the base URL and API key
     * @return configured asynchronous {@link OpenAIClientAsync}
     */
    @Bean
    public OpenAIClientAsync openAIClientAsync(AppProperties props) {
        return OpenAIOkHttpClientAsync.builder()
                .baseUrl(props.openrouter().baseUrl())
                .apiKey(props.openrouter().apiKey())
                .build();
    }
}
