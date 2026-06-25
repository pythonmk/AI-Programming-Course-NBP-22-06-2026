package pl.nbp.copilot.policy;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.domain.RequestType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Provides policy documents and legal-rules text for use in LLM prompts.
 *
 * <p>Documents are loaded from {@code classpath:policies/} on startup via
 * {@link PostConstruct} and cached in memory. Repeated calls return the same
 * pre-loaded string without re-reading the classpath.
 *
 * <p>Policy documents:
 * <ul>
 *   <li>{@code regulamin-reklamacji.md} — injected for {@link RequestType#COMPLAINT} requests</li>
 *   <li>{@code regulamin-zwrotow.md}   — injected for {@link RequestType#RETURN} requests</li>
 *   <li>{@code legal-rules-pl.md}      — Polish consumer-law summary, injected for all requests</li>
 * </ul>
 */
@Component
public class PolicyProvider {

    private static final Logger log = LoggerFactory.getLogger(PolicyProvider.class);

    private static final String POLICY_COMPLAINT  = "classpath:policies/regulamin-reklamacji.md";
    private static final String POLICY_RETURN     = "classpath:policies/regulamin-zwrotow.md";
    private static final String POLICY_LEGAL_RULES = "classpath:policies/legal-rules-pl.md";

    private final ResourceLoader resourceLoader;

    /** Cached policy documents keyed by request type. */
    private final Map<RequestType, String> policyCache = new EnumMap<>(RequestType.class);

    /** Cached legal-rules document. */
    private String legalRulesCache;

    /**
     * Constructs a {@code PolicyProvider} with the given resource loader.
     *
     * @param resourceLoader Spring resource loader used to read classpath resources
     */
    public PolicyProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Loads all policy documents into the in-memory cache on application startup.
     *
     * @throws IllegalStateException if any policy resource cannot be found or read
     */
    @PostConstruct
    void loadAll() {
        policyCache.put(RequestType.COMPLAINT, loadResource(POLICY_COMPLAINT));
        policyCache.put(RequestType.RETURN,    loadResource(POLICY_RETURN));
        legalRulesCache = loadResource(POLICY_LEGAL_RULES);
        log.info("PolicyProvider: loaded {} policy documents and legal-rules", policyCache.size());
    }

    /**
     * Returns the full text of the policy document for the given request type.
     *
     * <p>The content is loaded once on startup and returned from the cache on
     * subsequent calls.
     *
     * @param requestType the type of customer request; must not be {@code null}
     * @return full Markdown text of the applicable policy document
     * @throws IllegalStateException if the resource was not loaded (should not happen after startup)
     */
    public String getPolicyDocument(RequestType requestType) {
        String cached = policyCache.get(requestType);
        if (cached == null) {
            throw new IllegalStateException(
                    "Policy resource not found for request type: " + requestType);
        }
        return cached;
    }

    /**
     * Returns the full text of the Polish consumer-law summary used in LLM prompts.
     *
     * <p>Covers: 14-day withdrawal right, 2-year non-conformity liability, remedy
     * order, 14-day seller response window, and gwarancja vs. ustawowa
     * odpowiedzialność distinction.
     *
     * @return full Markdown text of {@code legal-rules-pl.md}
     * @throws IllegalStateException if the resource was not loaded (should not happen after startup)
     */
    public String getLegalRules() {
        if (legalRulesCache == null) {
            throw new IllegalStateException(
                    "Policy resource not found: legal-rules-pl.md");
        }
        return legalRulesCache;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Reads a classpath resource into a UTF-8 string.
     *
     * @param location Spring resource location string (e.g. {@code classpath:...})
     * @return content of the resource as a string
     * @throws IllegalStateException if the resource does not exist or cannot be read
     */
    private String loadResource(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Policy resource not found: " + location);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Policy resource not found: " + location, e);
        }
    }
}
