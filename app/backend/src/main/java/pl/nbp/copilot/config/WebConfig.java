package pl.nbp.copilot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * Spring MVC configuration: CORS mappings for the {@code /api/**} path.
 *
 * <p>Allowed origins are read from {@code CORS_ALLOWED_ORIGINS} (default
 * {@code http://localhost:4200}). Supports multiple origins if the env var
 * contains a comma-separated list.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    /**
     * Creates a {@code WebConfig} with the injected application properties.
     *
     * @param appProperties bound configuration properties
     */
    public WebConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Provides a {@link Clock} bean backed by the JVM default time zone.
     *
     * <p>Injected into {@code EligibilityService} (and any other time-aware
     * service) so that tests can substitute a fixed clock without touching
     * static state.
     *
     * @return system-default-zone clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registers CORS rules for {@code /api/**}: allows origins from
     * {@code CORS_ALLOWED_ORIGINS}, methods GET/POST/OPTIONS, all headers,
     * and credentials. Uses a 1-hour pre-flight cache.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = appProperties.cors().allowedOrigins().split(",");
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
