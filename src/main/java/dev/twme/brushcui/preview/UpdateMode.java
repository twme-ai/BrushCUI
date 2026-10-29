package dev.twme.brushcui.preview;

import java.util.Locale;
import java.util.Optional;

/**
 * How eagerly a moving preview follows the crosshair.
 */
public enum UpdateMode {
    /** Re-send while moving, at most once per minimum interval. */
    FOLLOW,
    /** Wait until the target stops moving; shape changes are still sent at once. */
    SETTLE;

    public static Optional<UpdateMode> parse(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
