package dev.twme.brushcui.geometry;

/**
 * The space a brush stroke would affect, in the forms a CUI multi-region can draw.
 */
public sealed interface BrushShape {

    /** Whether this shape equals {@code other} moved by a whole number of blocks. */
    boolean isTranslationOf(BrushShape other);

    /**
     * Sphere or ellipsoid, using WorldEdit's ellipsoid convention: blocks whose centres lie within
     * the radii of the centre block's centre (radius 0 is the centre block only).
     */
    record Ellipsoid(BlockPos center, double radiusX, double radiusY, double radiusZ) implements BrushShape {

        public static Ellipsoid sphere(BlockPos center, double radius) {
            return new Ellipsoid(center, radius, radius, radius);
        }

        @Override
        public boolean isTranslationOf(BrushShape other) {
            return other instanceof Ellipsoid e
                    && radiusX == e.radiusX && radiusY == e.radiusY && radiusZ == e.radiusZ;
        }
    }

    /**
     * Vertical cylinder covering block layers {@code minY..maxY}. {@code center} is the block the
     * brush targets; only its x and z place the cylinder.
     */
    record Cylinder(BlockPos center, double radiusX, double radiusZ, int minY, int maxY) implements BrushShape {

        public Cylinder {
            if (minY > maxY) {
                int swap = minY;
                minY = maxY;
                maxY = swap;
            }
        }

        @Override
        public boolean isTranslationOf(BrushShape other) {
            return other instanceof Cylinder c
                    && radiusX == c.radiusX && radiusZ == c.radiusZ
                    && maxY - minY == c.maxY - c.minY
                    && minY - center.y() == c.minY - c.center.y();
        }
    }

    /** Box of blocks from {@code min} to {@code max}, both inclusive. */
    record Cuboid(BlockPos min, BlockPos max) implements BrushShape {

        public Cuboid {
            BlockPos lo = min.min(max);
            BlockPos hi = min.max(max);
            min = lo;
            max = hi;
        }

        public long volume() {
            return (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
        }

        @Override
        public boolean isTranslationOf(BrushShape other) {
            return other instanceof Cuboid c && max.subtract(min).equals(c.max.subtract(c.min));
        }
    }
}
