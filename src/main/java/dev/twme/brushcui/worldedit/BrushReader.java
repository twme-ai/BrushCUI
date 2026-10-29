package dev.twme.brushcui.worldedit;

import org.bukkit.entity.Player;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.util.Location;

import dev.twme.brushcui.geometry.BlockPos;
import dev.twme.brushcui.geometry.BrushShape;
import dev.twme.brushcui.preview.PreviewTarget;

/**
 * Reads the brush in a player's main hand and where it would hit, without changing any session
 * state.
 */
public final class BrushReader {

    /** Result of reading a session. */
    public record Reading(boolean cuiSupported, PreviewTarget target) {
        static final Reading NO_CUI = new Reading(false, null);
        static final Reading NOTHING = new Reading(true, null);
    }

    public Reading read(Player player, BrushShapes.Fallback fallback) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(actor);
        // hasCUISupport is set by a real WorldEditCUI handshake and by WorldEditDisplay's
        // emulated one. Without either, nobody would render the messages.
        if (session == null || !session.hasCUISupport()) return Reading.NO_CUI;

        BaseItemStack item = actor.getItemInHand(HandSide.MAIN_HAND);
        if (item == null || item.getType() == null) return Reading.NOTHING;
        Tool tool = FaweCompat.tool(session, actor, item);
        if (!(tool instanceof BrushTool brushTool) || !brushTool.canUse(actor)) return Reading.NOTHING;

        FaweCompat.Stroke stroke = FaweCompat.primaryStroke(brushTool);
        if (stroke.brush() == null) return Reading.NOTHING;

        // The same trace BrushTool uses before building the brush.
        Location hit = actor.getBlockTrace(brushTool.getRange(), true, brushTool.getTraceMask());
        if (hit == null) return Reading.NOTHING;

        BlockPos target = BrushShapes.toPos(hit.toVector().toBlockPoint());
        BrushShape shape = BrushShapes.resolve(stroke.brush(), target, stroke.size(),
                player.getWorld().getMinHeight(), player.getWorld().getMaxHeight() - 1, fallback);
        if (shape == null) return Reading.NOTHING;
        return new Reading(true, new PreviewTarget(player.getWorld().getUID(), shape));
    }
}
