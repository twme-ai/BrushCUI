package dev.twme.brushcui.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrushShapeTest {

    @Test
    void movedSphereIsATranslation() {
        BrushShape a = BrushShape.Ellipsoid.sphere(new BlockPos(0, 64, 0), 5);
        BrushShape b = BrushShape.Ellipsoid.sphere(new BlockPos(7, 60, -3), 5);

        assertTrue(b.isTranslationOf(a));
        assertFalse(BrushShape.Ellipsoid.sphere(new BlockPos(0, 64, 0), 6).isTranslationOf(a));
    }

    @Test
    void cylinderTranslationKeepsHeightAndBaseOffset() {
        BrushShape a = new BrushShape.Cylinder(new BlockPos(0, 64, 0), 3, 3, 64, 68);

        assertTrue(new BrushShape.Cylinder(new BlockPos(2, 70, 1), 3, 3, 70, 74).isTranslationOf(a));
        // Clamped at the world bottom: same radius, different height.
        assertFalse(new BrushShape.Cylinder(new BlockPos(2, -64, 1), 3, 3, -64, -62).isTranslationOf(a));
    }

    @Test
    void cuboidCornersAreOrdered() {
        BrushShape.Cuboid cuboid = new BrushShape.Cuboid(new BlockPos(5, 1, -2), new BlockPos(1, 3, -6));

        assertEquals(new BlockPos(1, 1, -6), cuboid.min());
        assertEquals(new BlockPos(5, 3, -2), cuboid.max());
        assertEquals(5 * 3 * 5, cuboid.volume());
    }

    @Test
    void differentShapeTypesAreNeverTranslations() {
        assertFalse(new BrushShape.Cuboid(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1))
                .isTranslationOf(BrushShape.Ellipsoid.sphere(new BlockPos(0, 0, 0), 1)));
    }
}
