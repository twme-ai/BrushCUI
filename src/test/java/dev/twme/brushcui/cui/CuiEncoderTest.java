package dev.twme.brushcui.cui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.twme.brushcui.geometry.BlockPos;
import dev.twme.brushcui.geometry.BrushShape;

class CuiEncoderTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Test
    void sphereIsAnOutlinedEllipsoid() {
        List<String> messages = CuiEncoder.encode(ID,
                BrushShape.Ellipsoid.sphere(new BlockPos(5, -60, -3), 4), PreviewStyle.DEFAULT);

        assertEquals(List.of(
                "+s|ellipsoid|" + ID,
                "+e|0|5|-60|-3",
                "+e|1|4|4|4",
                "+col|#FFAA00FF|#FFFFFFFF|#FFFFFFFF|#00000000",
                "+wed|outline"), messages);
    }

    @Test
    void fractionalRadiiAreKept() {
        List<String> messages = CuiEncoder.encode(ID,
                new BrushShape.Ellipsoid(new BlockPos(0, 0, 0), 2.5, 1, 0.25), PreviewStyle.DEFAULT);

        assertEquals("+e|1|2.5|1|0.25", messages.get(2));
    }

    @Test
    void cylinderSendsCentreRadiusAndLayers() {
        List<String> messages = CuiEncoder.encode(ID,
                new BrushShape.Cylinder(new BlockPos(1, 64, 2), 3, 3, 64, 68), PreviewStyle.DEFAULT);

        assertEquals(List.of(
                "+s|cylinder|" + ID,
                "+cyl|1|64|2|3|3",
                "+mm|64|68",
                "+col|#FFAA00FF|#FFAA0060|#FFFFFFFF|#00000000",
                "+wed|outline"), messages);
    }

    @Test
    void cuboidHidesGridAndPoints() {
        List<String> messages = CuiEncoder.encode(ID,
                new BrushShape.Cuboid(new BlockPos(3, 65, 4), new BlockPos(-1, 64, 2)), PreviewStyle.DEFAULT);

        assertEquals(List.of(
                "+s|cuboid|" + ID,
                "+p|0|-1|64|2|30",
                "+p|1|3|65|4|30",
                "+col|#FFAA00FF|#00000000|#00000000|#00000000",
                "+grid|0"), messages);
    }

    @Test
    void everyPreviewHasTheSameMessageCount() {
        for (BrushShape shape : List.<BrushShape>of(
                BrushShape.Ellipsoid.sphere(new BlockPos(0, 0, 0), 1),
                new BrushShape.Cylinder(new BlockPos(0, 0, 0), 1, 1, 0, 0),
                new BrushShape.Cuboid(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1)))) {
            assertEquals(CuiEncoder.MESSAGES_PER_PREVIEW, CuiEncoder.encode(ID, shape, PreviewStyle.DEFAULT).size());
        }
    }

    @Test
    void clearOnlyRemovesThisRegion() {
        assertEquals("+s|clear|" + ID, CuiEncoder.clear(ID));
    }

    @Test
    void coloursAreNormalised() {
        PreviewStyle style = new PreviewStyle("ffaa00", "#12345678", "#abcdefFF", "00000000");

        assertEquals("#FFAA00FF", style.line());
        assertEquals("#12345678", style.grid());
        assertEquals("#ABCDEFFF", style.center());
        assertEquals("#00000000", style.fill());
        assertThrows(IllegalArgumentException.class, () -> PreviewStyle.normalise("red"));
    }
}
