package dev.twme.brushcui.worldedit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.factory.RegionFactory;
import com.sk89q.worldedit.session.ClipboardHolder;

import dev.twme.brushcui.geometry.BlockPos;
import dev.twme.brushcui.geometry.BrushShape;

/**
 * Works out the space a brush stroke at a target block would affect, following what each brush's
 * {@code build} method does in WorldEdit and FastAsyncWorldEdit.
 *
 * <p>Brushes are matched by class name so that FAWE-only brushes need no compile-time dependency;
 * private fields are read reflectively and never written.</p>
 */
public final class BrushShapes {

    /** What to show for brushes this class does not know. */
    public enum Fallback {
        /** A sphere of the brush size, which matches most FAWE brushes. */
        SPHERE,
        /** Nothing. */
        NONE
    }

    private BrushShapes() {
    }

    /**
     * @param brush  the brush bound to the tool
     * @param target the block the brush would be applied to
     * @param size   the brush size (radius)
     * @param minY   lowest block layer of the world
     * @param maxY   highest block layer of the world
     * @return the shape, or {@code null} for nothing to show
     */
    public static BrushShape resolve(Object brush, BlockPos target, double size, int minY, int maxY,
                                     Fallback fallback) {
        if (brush == null || !Double.isFinite(size) || size < 0) return null;
        return switch (brush.getClass().getSimpleName()) {
            case "SphereBrush", "HollowSphereBrush", "MorphBrush", "SplatterBrush" ->
                    BrushShape.Ellipsoid.sphere(target, size);
            case "CylinderBrush", "HollowCylinderBrush" -> cylinder(brush, target, size, minY, maxY);
            case "SmoothBrush", "SnowSmoothBrush" -> smooth(target, size);
            case "GravityBrush" -> gravity(brush, target, size, minY, maxY);
            case "ButcherBrush" -> {
                // CylinderRegion.createRadius: the whole world height.
                double radius = Math.max(1, size);
                yield new BrushShape.Cylinder(target, radius, radius, minY, maxY);
            }
            case "ImageHeightmapBrush" -> {
                int half = (int) Math.ceil(size);
                yield new BrushShape.Cuboid(offset(target, -half, -half, -half), offset(target, half, half, half));
            }
            case "OperationFactoryBrush" -> {
                Object factory = field(brush, "regionFactory");
                yield factory instanceof RegionFactory f
                        ? fromRegion(f.createCenteredAt(toVector(target), size), target)
                        : fallback(fallback, target, size);
            }
            case "ClipboardBrush" -> clipboard(brush, target);
            default -> fallback(fallback, target, size);
        };
    }

    /** Converts a WorldEdit region; regions without a CUI shape become their bounding box. */
    static BrushShape fromRegion(Region region, BlockPos target) {
        if (region instanceof EllipsoidRegion ellipsoid) {
            Vector3 r = ellipsoid.getRadius();
            return new BrushShape.Ellipsoid(toPos(ellipsoid.getCenter().toBlockPoint()), r.x(), r.y(), r.z());
        }
        if (region instanceof CylinderRegion cylinder) {
            BlockVector3 center = cylinder.getCenter().toBlockPoint();
            int minY = cylinder.getMinimumY();
            int maxY = cylinder.getMaximumY();
            // Keep the centre marker on the target block when it is inside the cylinder.
            int y = target.y() >= minY && target.y() <= maxY ? target.y() : center.y();
            return new BrushShape.Cylinder(new BlockPos(center.x(), y, center.z()),
                    cylinder.getRadius().x(), cylinder.getRadius().z(), minY, maxY);
        }
        return new BrushShape.Cuboid(toPos(region.getMinimumPoint()), toPos(region.getMaximumPoint()));
    }

    /** {@code EditSession.makeCylinder(pos, size, size, height)}: layers from the target up (or down). */
    private static BrushShape cylinder(Object brush, BlockPos target, double size, int minY, int maxY) {
        int height = field(brush, "height") instanceof Integer h ? h : 1;
        if (height == 0) return null;
        int bottom = height > 0 ? target.y() : target.y() + height;
        int top = bottom + Math.abs(height) - 1;
        bottom = Math.max(bottom, minY);
        top = Math.min(top, maxY);
        if (bottom > top) return null;
        return new BrushShape.Cylinder(target, size, size, bottom, top);
    }

    /** The height map region: {@code target - size} to {@code target + (size, size + 10, size)}. */
    private static BrushShape smooth(BlockPos target, double size) {
        BlockPos min = floor(target.x() - size, target.y() - size, target.z() - size);
        BlockPos max = floor(target.x() + size, target.y() + size + 10, target.z() + size);
        return new BrushShape.Cuboid(min, max);
    }

    /**
     * Columns within {@code size} of the target; layers within the height offset (WorldEdit) or
     * down to the world bottom with FAWE's full-height flag.
     */
    private static BrushShape gravity(Object brush, BlockPos target, double size, int minY, int maxY) {
        double offset = size;
        if (Boolean.TRUE.equals(field(brush, "overrideHeight")) && field(brush, "heightOffset") instanceof Integer h) {
            offset = h;
        }
        int bottom = Boolean.TRUE.equals(field(brush, "fullHeight"))
                ? minY
                : (int) Math.max(minY, Math.floor(target.y() - offset));
        int top = (int) Math.min(maxY, Math.floor(target.y() + offset));
        int half = (int) Math.floor(size);
        return new BrushShape.Cuboid(
                new BlockPos(target.x() - half, bottom, target.z() - half),
                new BlockPos(target.x() + half, Math.max(bottom, top), target.z() + half));
    }

    /**
     * The paste footprint: the clipboard is pasted with its centre (or, with {@code -o}, its
     * origin) at the target. Transforms that are not multiples of 90 degrees are approximated by
     * the bounding box of the transformed corners.
     */
    private static BrushShape clipboard(Object brush, BlockPos target) {
        if (!(field(brush, "holder") instanceof ClipboardHolder holder)) return null;
        boolean usingOrigin = Boolean.TRUE.equals(field(brush, "usingOrigin"));

        List<Transform> transforms = new ArrayList<>(4);
        if (FaweCompat.isFaweClipboardBrush(brush)) {
            // FAWE pastes without the holder transform, or with a random Y rotation.
            if (Boolean.TRUE.equals(field(brush, "randomRotate"))) {
                for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
                    transforms.add(new AffineTransform().rotateY(90 * quarterTurns));
                }
            } else {
                transforms.add(new AffineTransform());
            }
        } else {
            transforms.add(holder.getTransform());
        }

        BlockPos min = null;
        BlockPos max = null;
        for (Clipboard clipboard : FaweCompat.clipboards(holder)) {
            Region region = clipboard.getRegion();
            BlockVector3 origin = clipboard.getOrigin();
            BlockVector3 centerOffset = region.getCenter().toBlockPoint().subtract(origin);
            BlockVector3 to = usingOrigin ? toVector(target) : toVector(target).subtract(centerOffset);
            BlockVector3 lo = region.getMinimumPoint();
            BlockVector3 hi = region.getMaximumPoint();
            for (Transform transform : transforms) {
                for (int corner = 0; corner < 8; corner++) {
                    BlockVector3 p = BlockVector3.at(
                            (corner & 1) == 0 ? lo.x() : hi.x(),
                            (corner & 2) == 0 ? lo.y() : hi.y(),
                            (corner & 4) == 0 ? lo.z() : hi.z());
                    Vector3 moved = transform.apply(p.subtract(origin).toVector3());
                    BlockPos placed = floor(to.x() + moved.x(), to.y() + moved.y(), to.z() + moved.z());
                    min = min == null ? placed : min.min(placed);
                    max = max == null ? placed : max.max(placed);
                }
            }
        }
        return min == null ? null : new BrushShape.Cuboid(min, max);
    }

    private static BrushShape fallback(Fallback fallback, BlockPos target, double size) {
        return fallback == Fallback.SPHERE ? BrushShape.Ellipsoid.sphere(target, size) : null;
    }

    static Object field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                // try the superclass
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static BlockPos offset(BlockPos pos, int dx, int dy, int dz) {
        return new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
    }

    private static BlockPos floor(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    static BlockVector3 toVector(BlockPos pos) {
        return BlockVector3.at(pos.x(), pos.y(), pos.z());
    }

    static BlockPos toPos(BlockVector3 v) {
        return new BlockPos(v.x(), v.y(), v.z());
    }
}
