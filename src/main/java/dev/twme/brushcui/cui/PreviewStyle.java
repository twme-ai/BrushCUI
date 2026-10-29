package dev.twme.brushcui.cui;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Colours sent with each preview, in the CUI {@code #RRGGBBAA} format. A fully transparent
 * colour hides that part.
 *
 * @param line   outline of the brush shape
 * @param grid   inner grid lines; WorldEditCUI draws them for cylinders, WorldEditDisplay hides
 *               them because the preview is sent as an outline
 * @param center marker on the targeted block (spheres and cylinders)
 * @param fill   translucent surface, drawn by WorldEditDisplay when the player has fill enabled
 */
public record PreviewStyle(String line, String grid, String center, String fill) {

    public static final String TRANSPARENT = "#00000000";
    private static final Pattern COLOUR = Pattern.compile("#[0-9A-F]{8}");

    public static final PreviewStyle DEFAULT =
            new PreviewStyle("#FFAA00FF", "#FFAA0060", "#FFFFFFFF", TRANSPARENT);

    public PreviewStyle {
        line = normalise(line);
        grid = normalise(grid);
        center = normalise(center);
        fill = normalise(fill);
    }

    /**
     * Accepts {@code RRGGBB}, {@code RRGGBBAA}, with or without {@code #}.
     *
     * @throws IllegalArgumentException for anything else
     */
    public static String normalise(String colour) {
        if (colour == null) throw new IllegalArgumentException("Colour is missing");
        String c = colour.trim().toUpperCase(Locale.ROOT);
        if (!c.startsWith("#")) c = "#" + c;
        if (c.length() == 7) c = c + "FF";
        if (!COLOUR.matcher(c).matches()) {
            throw new IllegalArgumentException("Invalid colour '" + colour + "', expected #RRGGBB or #RRGGBBAA");
        }
        return c;
    }
}
