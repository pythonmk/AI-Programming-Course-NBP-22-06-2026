package pl.nbp.copilot.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that every {@link RequestType}, {@link EquipmentCategory}, and
 * {@link DecisionCategory} value exposes the correct Polish display label via
 * {@code labelPl()}.
 */
@DisplayName("Domain enum Polish labels")
class EnumLabelsTest {

    // -----------------------------------------------------------------------
    // RequestType
    // -----------------------------------------------------------------------

    static Stream<Arguments> requestTypeLabels() {
        return Stream.of(
                Arguments.of(RequestType.COMPLAINT, "Reklamacja"),
                Arguments.of(RequestType.RETURN,    "Zwrot")
        );
    }

    @ParameterizedTest(name = "RequestType.{0} → \"{1}\"")
    @MethodSource("requestTypeLabels")
    @DisplayName("RequestType.labelPl() matches Polish strings")
    void requestType_labelPl_matchesPolish(RequestType type, String expectedLabel) {
        assertEquals(expectedLabel, type.labelPl());
    }

    // -----------------------------------------------------------------------
    // EquipmentCategory
    // -----------------------------------------------------------------------

    static Stream<Arguments> equipmentCategoryLabels() {
        return Stream.of(
                Arguments.of(EquipmentCategory.LAPTOP,        "Laptopy"),
                Arguments.of(EquipmentCategory.DESKTOP,       "Komputery stacjonarne"),
                Arguments.of(EquipmentCategory.MONITOR,       "Monitory"),
                Arguments.of(EquipmentCategory.PERIPHERALS,   "Peryferia (klawiatury/myszy)"),
                Arguments.of(EquipmentCategory.PC_COMPONENTS, "Komponenty PC"),
                Arguments.of(EquipmentCategory.NETWORKING,    "Sieci (routery)"),
                Arguments.of(EquipmentCategory.ACCESSORIES,   "Akcesoria"),
                Arguments.of(EquipmentCategory.OTHER,         "Inne")
        );
    }

    @ParameterizedTest(name = "EquipmentCategory.{0} → \"{1}\"")
    @MethodSource("equipmentCategoryLabels")
    @DisplayName("EquipmentCategory.labelPl() matches Polish strings")
    void equipmentCategory_labelPl_matchesPolish(EquipmentCategory category, String expectedLabel) {
        assertEquals(expectedLabel, category.labelPl());
    }

    // -----------------------------------------------------------------------
    // DecisionCategory
    // -----------------------------------------------------------------------

    static Stream<Arguments> decisionCategoryLabels() {
        return Stream.of(
                Arguments.of(DecisionCategory.APPROVE,   "Zatwierdzono wstępnie"),
                Arguments.of(DecisionCategory.REJECT,    "Odrzucono"),
                Arguments.of(DecisionCategory.ESCALATE,  "Przekazanie do konsultanta")
        );
    }

    @ParameterizedTest(name = "DecisionCategory.{0} → \"{1}\"")
    @MethodSource("decisionCategoryLabels")
    @DisplayName("DecisionCategory.labelPl() matches Polish strings")
    void decisionCategory_labelPl_matchesPolish(DecisionCategory category, String expectedLabel) {
        assertEquals(expectedLabel, category.labelPl());
    }
}
