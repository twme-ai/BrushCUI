package dev.twme.brushcui.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.command.tool.brush.ClipboardBrush;
import com.sk89q.worldedit.command.tool.brush.CylinderBrush;
import com.sk89q.worldedit.command.tool.brush.GravityBrush;
import com.sk89q.worldedit.command.tool.brush.OperationFactoryBrush;
import com.sk89q.worldedit.command.tool.brush.SmoothBrush;
import com.sk89q.worldedit.command.tool.brush.SphereBrush;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.factory.CuboidRegionFactory;
import com.sk89q.worldedit.regions.factory.CylinderRegionFactory;
import com.sk89q.worldedit.regions.factory.SphereRegionFactory;
import com.sk89q.worldedit.session.ClipboardHolder;

import dev.twme.brushcui.geometry.BlockPos;
import dev.twme.brushcui.geometry.BrushShape;
import dev.twme.brushcui.worldedit.BrushShapes.Fallback;

class BrushShapesTest {

    private static final BlockPos TARGET = new BlockPos(10, 64, -20);
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 319;

    @Test
    void sphereBrushIsASphereOfTheBrushSize() {
        assertEquals(BrushShape.Ellipsoid.sphere(TARGET, 5), resolve(new SphereBrush(), 5));
    }

    @Test
    void cylinderBrushGrowsUpFromTheTarget() {
        assertEquals(new BrushShape.Cylinder(TARGET, 3, 3, 64, 68), resolve(new CylinderBrush(5), 3));
    }

    @Test
    void negativeCylinderHeightGrowsDown() {
        assertEquals(new BrushShape.Cylinder(TARGET, 3, 3, 60, 63), resolve(new CylinderBrush(-4), 3));
    }

    @Test
    void cylinderIsClampedToTheWorld() {
        // Layers -67..-64 (below the target), of which only -64 is inside the world.
        BlockPos nearBottom = new BlockPos(0, -63, 0);
        assertEquals(new BrushShape.Cylinder(nearBottom, 2, 2, -64, -64),
                BrushShapes.resolve(new CylinderBrush(-4), nearBottom, 2, MIN_Y, MAX_Y, Fallback.SPHERE));
    }

    @Test
    void smoothBrushUsesItsHeightMapRegion() {
        assertEquals(new BrushShape.Cuboid(new BlockPos(6, 60, -24), new BlockPos(14, 78, -16)),
                resolve(new SmoothBrush(4), 4));
    }

    @Test
    void gravityBrushCoversColumnsWithinSize() {
        assertEquals(new BrushShape.Cuboid(new BlockPos(7, 61, -23), new BlockPos(13, 67, -17)),
                resolve(new GravityBrush(null), 3));
        assertEquals(new BrushShape.Cuboid(new BlockPos(7, 54, -23), new BlockPos(13, 74, -17)),
                resolve(new GravityBrush(10), 3));
    }

    @Test
    void operationBrushUsesItsRegionFactory() {
        assertEquals(BrushShape.Ellipsoid.sphere(TARGET, 4),
                resolve(new OperationFactoryBrush(null, new SphereRegionFactory()), 4));
        // CylinderRegionFactory(height): layers y - height/2 .. y + height/2.
        assertEquals(new BrushShape.Cylinder(TARGET, 4, 4, 61, 67),
                resolve(new OperationFactoryBrush(null, new CylinderRegionFactory(6)), 4));
        assertEquals(new BrushShape.Cuboid(new BlockPos(6, 60, -24), new BlockPos(14, 68, -16)),
                resolve(new OperationFactoryBrush(null, new CuboidRegionFactory()), 4));
    }

    @Test
    void clipboardBrushIsCentredOnTheTarget() {
        // 5 x 3 x 9 clipboard; its centre block (2, 1, 4) is pasted at the target.
        ClipboardHolder holder = new ClipboardHolder(clipboard(
                BlockVector3.at(0, 0, 0), BlockVector3.at(4, 2, 8), BlockVector3.at(0, 0, 0)));

        assertEquals(new BrushShape.Cuboid(new BlockPos(8, 63, -24), new BlockPos(12, 65, -16)),
                resolve(new ClipboardBrush(holder, false, false), 0));
    }

    @Test
    void clipboardBrushWithOriginPastesTheOriginAtTheTarget() {
        ClipboardHolder holder = new ClipboardHolder(clipboard(
                BlockVector3.at(0, 0, 0), BlockVector3.at(4, 2, 8), BlockVector3.at(0, -1, 0)));

        assertEquals(new BrushShape.Cuboid(new BlockPos(10, 65, -20), new BlockPos(14, 67, -12)),
                resolve(new ClipboardBrush(holder, false, true), 0));
    }

    @Test
    void clipboardBrushFollowsTheHolderRotation() {
        ClipboardHolder holder = new ClipboardHolder(clipboard(
                BlockVector3.at(0, 0, 0), BlockVector3.at(4, 2, 8), BlockVector3.at(0, 0, 0)));
        holder.setTransform(new AffineTransform().rotateY(90));

        BrushShape.Cuboid cuboid = (BrushShape.Cuboid) resolve(new ClipboardBrush(holder, false, true), 0);
        assertEquals(new BlockPos(8, 2, 4), cuboid.max().subtract(cuboid.min()));
    }

    @Test
    void unknownBrushesFallBackToASphereOrNothing() {
        Brush custom = (editSession, position, pattern, size) -> { };

        assertEquals(BrushShape.Ellipsoid.sphere(TARGET, 7), resolve(custom, 7));
        assertNull(BrushShapes.resolve(custom, TARGET, 7, MIN_Y, MAX_Y, Fallback.NONE));
    }

    @Test
    void invalidSizesShowNothing() {
        assertNull(resolve(new SphereBrush(), Double.NaN));
        assertNull(resolve(new SphereBrush(), -1));
    }

    private static BrushShape resolve(Object brush, double size) {
        return BrushShapes.resolve(brush, TARGET, size, MIN_Y, MAX_Y, Fallback.SPHERE);
    }

    /** A clipboard that only knows its region and origin, which is all a paste footprint needs. */
    private static Clipboard clipboard(BlockVector3 min, BlockVector3 max, BlockVector3 origin) {
        CuboidRegion region = new CuboidRegion(min, max);
        return (Clipboard) Proxy.newProxyInstance(Clipboard.class.getClassLoader(), new Class<?>[] {Clipboard.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRegion" -> region;
                    case "getOrigin" -> origin;
                    case "getMinimumPoint" -> min;
                    case "getMaximumPoint" -> max;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
