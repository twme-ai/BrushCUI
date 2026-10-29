package dev.twme.brushcui.preview;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Per-player toggles, stored in the player's persistent data so no extra files are needed.
 */
public final class PlayerPreferences {

    private final NamespacedKey enabledKey;
    private final NamespacedKey modeKey;

    public PlayerPreferences(Plugin plugin) {
        this.enabledKey = new NamespacedKey(plugin, "enabled");
        this.modeKey = new NamespacedKey(plugin, "mode");
    }

    public boolean isEnabled(Player player, boolean defaultValue) {
        Byte value = player.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE);
        return value == null ? defaultValue : value != 0;
    }

    public void setEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    public UpdateMode getMode(Player player, UpdateMode defaultValue) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        return UpdateMode.parse(data.get(modeKey, PersistentDataType.STRING)).orElse(defaultValue);
    }

    public void setMode(Player player, UpdateMode mode) {
        player.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.id());
    }
}
