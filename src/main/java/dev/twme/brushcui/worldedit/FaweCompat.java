package dev.twme.brushcui.worldedit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.session.ClipboardHolder;

/**
 * FastAsyncWorldEdit specifics, accessed reflectively so the plugin also runs on WorldEdit.
 */
final class FaweCompat {

    private static final String MULTI_CLIPBOARD_HOLDER =
            "com.fastasyncworldedit.core.extent.clipboard.MultiClipboardHolder";
    /** {@code LocalSession.getTool(BaseItem, Player)}, only present on FAWE. */
    private static final Method FAWE_GET_TOOL = faweGetTool();

    private FaweCompat() {
    }

    /** The brush and size used by the primary action (right click). */
    record Stroke(Object brush, double size) {
    }

    /**
     * FAWE binds tools to the item stack ({@code getTool(BaseItem, Player)}), which also covers
     * its persistent brushes; WorldEdit only knows {@code getTool(ItemType)}.
     */
    static Tool tool(LocalSession session, Player actor, BaseItem item) {
        if (FAWE_GET_TOOL == null) return session.getTool(item.getType());
        try {
            return FAWE_GET_TOOL.invoke(session, item, actor) instanceof Tool tool ? tool : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method faweGetTool() {
        try {
            return LocalSession.class.getMethod("getTool", BaseItem.class, Player.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * FAWE keeps separate primary and secondary brush settings and {@code getBrush()} returns
     * whichever was used last, so the primary settings are read explicitly when present.
     */
    static Stroke primaryStroke(BrushTool tool) {
        Object primary = invoke(tool, "getPrimary");
        if (primary != null) {
            Object brush = invoke(primary, "getBrush");
            if (invoke(primary, "getSize") instanceof Double size) return new Stroke(brush, size);
        }
        return new Stroke(tool.getBrush(), tool.getSize());
    }

    /** FAWE's clipboard brush ignores the holder transform; it is told apart by its extra field. */
    static boolean isFaweClipboardBrush(Object brush) {
        try {
            brush.getClass().getDeclaredField("randomRotate");
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * Every clipboard a paste may use. FAWE's {@code MultiClipboardHolder.getClipboard()} picks a
     * random one on every call, so its full list is read instead.
     */
    static List<Clipboard> clipboards(ClipboardHolder holder) {
        if (isInstance(holder, MULTI_CLIPBOARD_HOLDER)) {
            List<Clipboard> clipboards = new ArrayList<>();
            if (invoke(holder, "getClipboards") instanceof Iterable<?> iterable) {
                for (Object clipboard : iterable) {
                    if (clipboard instanceof Clipboard c) clipboards.add(c);
                }
            }
            return clipboards;
        }
        return List.of(holder.getClipboard());
    }

    private static boolean isInstance(Object object, String className) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) return true;
        }
        return false;
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
