package dev.twme.brushcui.listener;

import java.nio.charset.StandardCharsets;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import dev.twme.brushcui.preview.PreviewService;

/**
 * Starts and stops per-player polling and notices when the client dropped its regions.
 */
public final class PlayerListener implements Listener, PluginMessageListener {

    private final PreviewService service;

    public PlayerListener(PreviewService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.start(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.stop(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        service.forget(event.getPlayer());
    }

    /**
     * WorldEditCUI clears all regions and re-sends {@code v|<version>} whenever its world or
     * player object changes. WorldEditDisplay emulates the same handshake.
     */
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!PreviewService.CUI_CHANNEL.equals(channel)) return;
        if (new String(message, StandardCharsets.UTF_8).startsWith("v|")) {
            service.requestResend(player);
        }
    }
}
