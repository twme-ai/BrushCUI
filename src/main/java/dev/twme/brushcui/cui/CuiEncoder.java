package dev.twme.brushcui.cui;

import java.util.List;
import java.util.UUID;

import dev.twme.brushcui.geometry.BlockPos;
import dev.twme.brushcui.geometry.BrushShape;

/**
 * Encodes brush shapes as WorldEditCUI multi-region messages ({@code +} prefix).
 *
 * <p>Multi-region messages leave WorldEdit's own selection alone. They are rendered natively
 * by WorldEditCUI, or by WorldEditDisplay when the client has no CUI mod.</p>
 */
public final class CuiEncoder {

    /** Every preview takes exactly this many messages. */
    public static final int MESSAGES_PER_PREVIEW = 5;

    /**
     * WorldEditDisplay 2.9.0+ hint to draw a round region as an outline only (three great circles,
     * or two circles and four sides) instead of a full grid. WorldEditCUI ignores unknown event
     * keys; {@code +grid} cannot be used because WorldEditCUI throws on it for non-cuboids.
     */
    static final String OUTLINE_HINT = "+wed|outline";

    private CuiEncoder() {
    }

    public static List<String> encode(UUID regionId, BrushShape shape, PreviewStyle style) {
        return switch (shape) {
            case BrushShape.Ellipsoid e -> List.of(
                    "+s|ellipsoid|" + regionId,
                    "+e|0|" + pos(e.center()),
                    "+e|1|" + number(e.radiusX()) + "|" + number(e.radiusY()) + "|" + number(e.radiusZ()),
                    // WorldEditCUI: [rings, centre]; WorldEditDisplay: [lines, -, centre, fill].
                    "+col|" + style.line() + "|" + style.center() + "|" + style.center() + "|" + style.fill(),
                    OUTLINE_HINT);
            case BrushShape.Cylinder c -> List.of(
                    "+s|cylinder|" + regionId,
                    "+cyl|" + pos(c.center()) + "|" + number(c.radiusX()) + "|" + number(c.radiusZ()),
                    "+mm|" + c.minY() + "|" + c.maxY(),
                    // Both clients: [circles, grid, centre]; WorldEditDisplay also [fill].
                    "+col|" + style.line() + "|" + style.grid() + "|" + style.center() + "|" + style.fill(),
                    OUTLINE_HINT);
            case BrushShape.Cuboid c -> List.of(
                    "+s|cuboid|" + regionId,
                    "+p|0|" + pos(c.min()) + "|" + c.volume(),
                    "+p|1|" + pos(c.max()) + "|" + c.volume(),
                    // [edge, grid, point1, point2]; WorldEditDisplay fills with the grid colour.
                    "+col|" + style.line() + "|" + style.fill() + "|" + PreviewStyle.TRANSPARENT + "|"
                            + PreviewStyle.TRANSPARENT,
                    "+grid|0");
        };
    }

    /**
     * Removes only this region. A bare {@code +s|clear} would wipe every multi region the
     * client has, including those of other plugins.
     */
    public static String clear(UUID regionId) {
        return "+s|clear|" + regionId;
    }

    // Integer coordinates only: both clients parse block positions as integers.
    private static String pos(BlockPos pos) {
        return pos.x() + "|" + pos.y() + "|" + pos.z();
    }

    static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e9) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
