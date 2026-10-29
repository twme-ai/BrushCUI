package dev.twme.brushcui.preview;

import java.util.UUID;

import dev.twme.brushcui.geometry.BrushShape;

/**
 * What should currently be displayed for a player.
 */
public record PreviewTarget(UUID worldId, BrushShape shape) {

    boolean isTranslationOf(PreviewTarget other) {
        return other != null && worldId.equals(other.worldId) && shape.isTranslationOf(other.shape);
    }
}
