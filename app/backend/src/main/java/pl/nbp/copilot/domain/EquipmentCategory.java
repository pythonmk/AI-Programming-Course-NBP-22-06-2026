package pl.nbp.copilot.domain;

/**
 * Classifies the type of electronic equipment covered by a service request.
 *
 * <p>Each constant carries a Polish display label accessible via {@link #labelPl()},
 * used by the {@code GET /api/meta/form-options} endpoint so the frontend renders
 * localised option text without hardcoding strings.
 */
public enum EquipmentCategory {

    /** Laptops and notebook computers. */
    LAPTOP("Laptopy"),

    /** Desktop computers and workstations. */
    DESKTOP("Komputery stacjonarne"),

    /** Computer monitors and displays. */
    MONITOR("Monitory"),

    /** Keyboards, mice, and other peripheral input devices. */
    PERIPHERALS("Peryferia (klawiatury/myszy)"),

    /** Internal PC components (CPUs, GPUs, RAM, storage, etc.). */
    PC_COMPONENTS("Komponenty PC"),

    /** Networking equipment such as routers and switches. */
    NETWORKING("Sieci (routery)"),

    /** Cables, adapters, cases, and other accessories. */
    ACCESSORIES("Akcesoria"),

    /** Equipment that does not fit any other category. */
    OTHER("Inne");

    private final String labelPl;

    EquipmentCategory(String labelPl) {
        this.labelPl = labelPl;
    }

    /**
     * Returns the Polish display label for this equipment category.
     *
     * @return Polish label, never {@code null}
     */
    public String labelPl() {
        return labelPl;
    }
}
