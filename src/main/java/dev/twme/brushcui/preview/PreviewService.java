package dev.twme.brushcui.preview;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import dev.twme.brushcui.Settings;
import dev.twme.brushcui.cui.CuiEncoder;
import dev.twme.brushcui.worldedit.BrushReader;

/**
 * Polls each online player's brush on that player's own scheduler (Folia safe) and sends
 * CUI messages when the throttle allows.
 */
public final class PreviewService {

    public static final String CUI_CHANNEL = "worldedit:cui";
    public static final String USE_PERMISSION = "brushcui.use";
    /** Multi-region id of the preview. Every client only sees its own, so one id suffices. */
    public static final UUID REGION_ID =
            UUID.nameUUIDFromBytes("BrushCUI:brush".getBytes(StandardCharsets.UTF_8));

    private static final long ERROR_LOG_INTERVAL_MS = 10_000L;

    private final Plugin plugin;
    private final BrushReader reader;
    private final PlayerPreferences preferences;
    private final Supplier<Settings> settings;
    private final Map<UUID, PlayerPreview> previews = new ConcurrentHashMap<>();
    private volatile long lastErrorLog;

    public PreviewService(Plugin plugin, BrushReader reader, PlayerPreferences preferences,
                          Supplier<Settings> settings) {
        this.plugin = plugin;
        this.reader = reader;
        this.preferences = preferences;
        this.settings = settings;
    }

    public void start(Player player) {
        stop(player.getUniqueId());
        PlayerPreview preview = new PlayerPreview();
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, ignored -> tick(player, preview),
                null, 1L, settings.get().pollIntervalTicks());
        if (task == null) return; // player already removed
        preview.task = task;
        previews.put(player.getUniqueId(), preview);
    }

    public void stop(UUID playerId) {
        PlayerPreview preview = previews.remove(playerId);
        if (preview != null && preview.task != null) preview.task.cancel();
    }

    /** Restarts every task, e.g. after the poll interval changed on reload. */
    public void restartAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, ignored -> start(player), null);
        }
    }

    /** The client re-sent its CUI handshake, which drops every multi region it had. */
    public void requestResend(Player player) {
        PlayerPreview preview = previews.get(player.getUniqueId());
        if (preview == null) return;
        synchronized (preview) {
            preview.throttle.requestResend(Bukkit.getCurrentTick(), settings.get().resendDelayTicks());
        }
    }

    /** The client forgot its regions (world change, respawn). */
    public void forget(Player player) {
        PlayerPreview preview = previews.get(player.getUniqueId());
        if (preview == null) return;
        synchronized (preview) {
            preview.throttle.reset();
        }
    }

    /** Clears every visible preview, used on disable. Must run where sending is allowed. */
    public void clearAll() {
        for (Map.Entry<UUID, PlayerPreview> entry : previews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            PlayerPreview preview = entry.getValue();
            if (preview.task != null) preview.task.cancel();
            if (player != null && preview.throttle.isShown()) {
                send(player, CuiEncoder.clear(REGION_ID));
            }
        }
        previews.clear();
    }

    private void tick(Player player, PlayerPreview preview) {
        try {
            synchronized (preview) {
                tickLocked(player, preview);
            }
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLog >= ERROR_LOG_INTERVAL_MS) {
                lastErrorLog = now;
                plugin.getLogger().log(Level.WARNING, "Brush preview failed for " + player.getName(), e);
            }
        }
    }

    private void tickLocked(Player player, PlayerPreview preview) {
        Settings current = settings.get();
        long tick = Bukkit.getCurrentTick();
        boolean enabled = player.hasPermission(USE_PERMISSION)
                && preferences.isEnabled(player, current.defaultEnabled());

        PreviewTarget target = null;
        if (enabled) {
            BrushReader.Reading reading = reader.read(player, current.fallback());
            if (!reading.cuiSupported()) {
                // Nothing can render it, and the client has no region to clear either.
                preview.throttle.reset();
                return;
            }
            target = reading.target();
        }

        UpdateMode mode = preferences.getMode(player, current.defaultMode());
        List<String> messages = target == null ? List.of() : CuiEncoder.encode(REGION_ID, target.shape(), current.style());
        switch (preview.throttle.decide(tick, target, mode, current.throttle(), Math.max(1, messages.size()))) {
            case SEND -> {
                for (String message : messages) send(player, message);
                preview.throttle.markSent(tick, target, messages.size());
            }
            case CLEAR -> {
                send(player, CuiEncoder.clear(REGION_ID));
                preview.throttle.markCleared();
            }
            case NONE -> {
            }
        }
    }

    private void send(Player player, String message) {
        player.sendPluginMessage(plugin, CUI_CHANNEL, message.getBytes(StandardCharsets.UTF_8));
    }

    private static final class PlayerPreview {
        final PreviewThrottle throttle = new PreviewThrottle();
        volatile ScheduledTask task;
    }
}
