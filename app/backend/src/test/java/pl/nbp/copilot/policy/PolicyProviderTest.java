package pl.nbp.copilot.policy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.nbp.copilot.domain.RequestType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link PolicyProvider}.
 *
 * <p>Uses a real Spring context to verify that classpath resources are found,
 * loaded, and contain expected content. No mocking — the resources are the
 * actual test boundary.
 */
@SpringBootTest
class PolicyProviderTest {

    @Autowired
    private PolicyProvider policyProvider;

    // -------------------------------------------------------------------------
    // getPolicyDocument — non-empty sanity checks
    // -------------------------------------------------------------------------

    @Test
    void getPolicyDocument_complaint_returnsNonEmptyString() {
        String result = policyProvider.getPolicyDocument(RequestType.COMPLAINT);
        assertNotNull(result);
        assertFalse(result.isBlank(), "Complaint policy document must not be blank");
    }

    @Test
    void getPolicyDocument_return_returnsNonEmptyString() {
        String result = policyProvider.getPolicyDocument(RequestType.RETURN);
        assertNotNull(result);
        assertFalse(result.isBlank(), "Return policy document must not be blank");
    }

    // -------------------------------------------------------------------------
    // getPolicyDocument — content probes
    // -------------------------------------------------------------------------

    @Test
    void getPolicyDocument_complaint_containsWordReklamacja() {
        String result = policyProvider.getPolicyDocument(RequestType.COMPLAINT);
        assertTrue(
                result.toLowerCase().contains("reklamacja"),
                "Complaint policy document must contain 'reklamacja' (case-insensitive)"
        );
    }

    @Test
    void getPolicyDocument_return_containsWordZwrot() {
        String result = policyProvider.getPolicyDocument(RequestType.RETURN);
        assertTrue(
                result.toLowerCase().contains("zwrot"),
                "Return policy document must contain 'zwrot' (case-insensitive)"
        );
    }

    // -------------------------------------------------------------------------
    // getLegalRules — non-empty sanity check
    // -------------------------------------------------------------------------

    @Test
    void getLegalRules_returnsNonEmptyString() {
        String result = policyProvider.getLegalRules();
        assertNotNull(result);
        assertFalse(result.isBlank(), "Legal rules document must not be blank");
    }

    // -------------------------------------------------------------------------
    // getLegalRules — content probes for key legal facts
    // -------------------------------------------------------------------------

    @Test
    void getLegalRules_contains14DayWithdrawalMention() {
        String result = policyProvider.getLegalRules();
        assertTrue(
                result.contains("14"),
                "Legal rules must reference the 14-day withdrawal right"
        );
    }

    @Test
    void getLegalRules_contains2YearNonConformityMention() {
        String result = policyProvider.getLegalRules();
        assertTrue(
                result.contains("2"),
                "Legal rules must reference the 2-year non-conformity period"
        );
    }

    // -------------------------------------------------------------------------
    // Caching: repeated calls must return the same content
    // -------------------------------------------------------------------------

    @Test
    void getPolicyDocument_complaint_isCached_returnsSameContent() {
        String first = policyProvider.getPolicyDocument(RequestType.COMPLAINT);
        String second = policyProvider.getPolicyDocument(RequestType.COMPLAINT);
        assertEquals(first, second, "Repeated calls should return the same cached content");
    }

    @Test
    void getPolicyDocument_return_isCached_returnsSameContent() {
        String first = policyProvider.getPolicyDocument(RequestType.RETURN);
        String second = policyProvider.getPolicyDocument(RequestType.RETURN);
        assertEquals(first, second, "Repeated calls should return the same cached content");
    }

    @Test
    void getLegalRules_isCached_returnsSameContent() {
        String first = policyProvider.getLegalRules();
        String second = policyProvider.getLegalRules();
        assertEquals(first, second, "Repeated getLegalRules calls should return the same cached content");
    }
}
