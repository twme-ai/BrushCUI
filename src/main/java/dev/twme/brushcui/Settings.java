package dev.twme.brushcui;

import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import dev.twme.brushcui.cui.PreviewStyle;
import dev.twme.brushcui.preview.ThrottleSettings;
import dev.twme.brushcui.preview.UpdateMode;
import dev.twme.brushcui.worldedit.BrushShapes;

/**
 * Values read from {@code config.yml}.
 */
public record Settings(
        boolean defaultEnabled,
        UpdateMode defaultMode,
        int pollIntervalTicks,
        int resendDelayTicks,
        BrushShapes.Fallback fallback,
        ThrottleSettings throttle,
        PreviewStyle style) {

    public static Settings load(FileConfiguration config, Logger logger) {
        UpdateMode mode = UpdateMode.parse(config.getString("default-mode")).orElseGet(() -> {
            logger.warning("Unknown default-mode '" + config.getString("default-mode") + "', using follow");
            return UpdateMode.FOLLOW;
        });
        ThrottleSettings throttle = new ThrottleSettings(
                config.getInt("throttle.min-send-interval-ticks", ThrottleSettings.DEFAULT.minSendIntervalTicks()),
                config.getInt("throttle.settle-ticks", ThrottleSettings.DEFAULT.settleTicks()),
                config.getInt("throttle.max-messages-per-second", ThrottleSettings.DEFAULT.maxMessagesPerSecond()));
        return new Settings(
                config.getBoolean("default-enabled", true),
                mode,
                Math.max(1, config.getInt("poll-interval-ticks", 2)),
                Math.max(0, config.getInt("resend-delay-ticks", 5)),
                loadFallback(config.getString("unknown-brushes", "sphere"), logger),
                throttle,
                loadStyle(config.getConfigurationSection("style"), logger));
    }

    private static BrushShapes.Fallback loadFallback(String value, Logger logger) {
        try {
            return BrushShapes.Fallback.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warning("Unknown unknown-brushes '" + value + "', using sphere");
            return BrushShapes.Fallback.SPHERE;
        }
    }

    private static PreviewStyle loadStyle(ConfigurationSection section, Logger logger) {
        PreviewStyle fallback = PreviewStyle.DEFAULT;
        if (section == null) return fallback;
        try {
            return new PreviewStyle(
                    section.getString("line", fallback.line()),
                    section.getString("grid", fallback.grid()),
                    section.getString("center", fallback.center()),
                    section.getString("fill", fallback.fill()));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid style in config.yml (" + e.getMessage() + "), using defaults");
            return fallback;
        }
    }
}
