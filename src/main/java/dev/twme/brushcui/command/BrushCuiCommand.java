package dev.twme.brushcui.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import dev.twme.brushcui.BrushCUI;
import dev.twme.brushcui.Messages;
import dev.twme.brushcui.preview.PlayerPreferences;
import dev.twme.brushcui.preview.UpdateMode;

/**
 * {@code /brushcui [on|off|toggle|mode <follow|settle>|status|reload]}
 */
public final class BrushCuiCommand implements TabExecutor {

    private static final String RELOAD_PERMISSION = "brushcui.reload";

    private final BrushCUI plugin;
    private final PlayerPreferences preferences;
    private final Messages messages;

    public BrushCuiCommand(BrushCUI plugin, PlayerPreferences preferences, Messages messages) {
        this.plugin = plugin;
        this.preferences = preferences;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String @NotNull [] args) {
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                messages.send(sender, "no-permission");
                return true;
            }
            plugin.reload();
            messages.send(sender, "reloaded");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }

        boolean defaultEnabled = plugin.settings().defaultEnabled();
        switch (sub) {
            case "on" -> setEnabled(player, true);
            case "off" -> setEnabled(player, false);
            case "toggle" -> setEnabled(player, !preferences.isEnabled(player, defaultEnabled));
            case "mode" -> {
                Optional<UpdateMode> mode = args.length > 1 ? UpdateMode.parse(args[1]) : Optional.empty();
                if (mode.isEmpty()) {
                    messages.send(player, "mode-invalid");
                    return true;
                }
                preferences.setMode(player, mode.get());
                messages.send(player, "mode-set", "mode", mode.get().id());
            }
            case "status" -> messages.send(player, "status",
                    "state", preferences.isEnabled(player, defaultEnabled) ? "on" : "off",
                    "mode", preferences.getMode(player, plugin.settings().defaultMode()).id());
            default -> messages.send(player, "usage", "label", label);
        }
        return true;
    }

    private void setEnabled(Player player, boolean enabled) {
        preferences.setEnabled(player, enabled);
        messages.send(player, enabled ? "enabled" : "disabled");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                                      String @NotNull [] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("on", "off", "toggle", "mode", "status"));
            if (sender.hasPermission(RELOAD_PERMISSION)) options.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            for (UpdateMode mode : UpdateMode.values()) options.add(mode.id());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(prefix));
        return options;
    }
}
