package pl.nbp.copilot.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that {@link LlmConfig} registers both OpenAI client beans in the
 * Spring application context when {@code OPENROUTER_API_KEY} is supplied.
 */
@SpringBootTest(properties = {"OPENROUTER_API_KEY=test-key"})
@DisplayName("LlmConfig — bean registration")
class LlmConfigTest {

    @Autowired
    private OpenAIClient openAIClient;

    @Autowired
    private com.openai.client.OpenAIClientAsync openAIClientAsync;

    @Test
    @DisplayName("OpenAIClient (synchronous) bean is present in context")
    void synchronousClientBeanExists() {
        assertNotNull(openAIClient, "OpenAIClient bean must be registered");
    }

    @Test
    @DisplayName("OpenAIClientAsync bean is present in context")
    void asyncClientBeanExists() {
        assertNotNull(openAIClientAsync, "OpenAIClientAsync bean must be registered");
    }
}
