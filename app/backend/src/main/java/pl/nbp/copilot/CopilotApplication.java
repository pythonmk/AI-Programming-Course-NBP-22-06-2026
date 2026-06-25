package pl.nbp.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Hardware Service Decision Copilot backend.
 *
 * <p>Spring Boot 3.5.x application with Java 21 virtual threads enabled
 * (see {@code spring.threads.virtual.enabled=true} in {@code application.yml}).
 */
@SpringBootApplication
public class CopilotApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments passed by the JVM launcher
     */
    public static void main(String[] args) {
        SpringApplication.run(CopilotApplication.class, args);
    }
}
