package pl.nbp.copilot.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness endpoint used by local development tooling and orchestration.
 *
 * <p>Exposes {@code GET /api/health} returning {@code {"status":"UP"}}.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Returns a simple liveness indicator.
     *
     * @return {@code 200 OK} with body {@code {"status":"UP"}}
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
