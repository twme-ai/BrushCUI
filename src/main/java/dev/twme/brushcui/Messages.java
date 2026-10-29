package dev.twme.brushcui;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Bundled translations, chosen by the client locale.
 */
public final class Messages {

    private static final String FALLBACK = "en_us";
    private static final String[] LANGUAGES = {"en_us", "zh_tw"};

    private final Map<String, YamlConfiguration> bundles = new HashMap<>();

    public Messages(JavaPlugin plugin) {
        for (String language : LANGUAGES) {
            try (InputStream in = plugin.getResource("lang/" + language + ".yml")) {
                if (in == null) continue;
                bundles.put(language, YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("Cannot read lang/" + language + ".yml: " + e.getMessage());
            }
        }
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            resolver.resolver(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize(raw(sender, key), resolver.build()));
    }

    private String raw(CommandSender sender, String key) {
        String language = FALLBACK;
        if (sender instanceof Player player) {
            String locale = player.locale().toString().toLowerCase(Locale.ROOT);
            if (locale.startsWith("zh_tw") || locale.startsWith("zh_hk") || locale.startsWith("zh_hant")) {
                language = "zh_tw";
            }
        }
        YamlConfiguration bundle = bundles.getOrDefault(language, bundles.get(FALLBACK));
        String value = bundle == null ? null : bundle.getString(key);
        if (value == null && bundles.get(FALLBACK) != null) value = bundles.get(FALLBACK).getString(key);
        return value == null ? key : value;
    }
}
