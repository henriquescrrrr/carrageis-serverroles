package pt.henrique.serverroles.util;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages localised messages for ServerRoles.
 * <p>
 * On {@link #load(String)}, the requested language file is sourced from the plugin
 * data folder (e.g. {@code plugins/ServerRoles/lang/pt_PT.yml}).  If the file does
 * not yet exist it is extracted from the jar.  Lookups that miss in the active
 * language fall back to {@code en_US}.
 */
public final class LangManager {

    /** Canonical default + hard-fallback language (European Portuguese). */
    private static final String DEFAULT_LANGUAGE = "pt_PT";

    private final Plugin plugin;
    private YamlConfiguration langConfig;
    private YamlConfiguration fallbackConfig;
    private String currentLanguage = DEFAULT_LANGUAGE;

    public LangManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or re-loads) messages for the supplied language code.
     * Always call this before {@link #get} is first used and again after a reload.
     *
     * @param language the BCP-47-style language tag used as the file name stem,
     *                 e.g. {@code "en_US"} or {@code "pt_PT"}
     */
    public void load(String language) {
        if (language == null || language.isBlank()) {
            language = DEFAULT_LANGUAGE;
        }
        this.currentLanguage = language;

        // Always ensure the default language (pt_PT) is on disk — it is the hard fallback.
        extractDefault(DEFAULT_LANGUAGE);
        if (!language.equals(DEFAULT_LANGUAGE)) {
            extractDefault(language);
        }

        // Load the fallback config (pt_PT).
        fallbackConfig = loadFile(DEFAULT_LANGUAGE);

        // Load the requested language.
        langConfig = language.equals(DEFAULT_LANGUAGE) ? fallbackConfig : loadFile(language);

        plugin.getLogger().info("Language loaded: " + currentLanguage);
    }

    /**
     * Returns the currently active language code.
     *
     * @return the language code, e.g. {@code "en_US"}
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Returns the raw (un-parsed) message string for the given key.
     * Falls back to {@code en_US} and then to a built-in error string if missing.
     *
     * @param key the message key as defined in the lang file
     * @return the raw MiniMessage string
     */
    public String getRaw(String key) {
        String value = langConfig != null ? langConfig.getString(key) : null;
        if (value == null && langConfig != fallbackConfig && fallbackConfig != null) {
            value = fallbackConfig.getString(key);
        }
        if (value == null) {
            return "<color:#ff5555>[Missing lang key: " + key + "]";
        }
        return value;
    }

    /**
     * Returns the message for {@code key} as a parsed MiniMessage {@link Component}.
     *
     * @param key the message key
     * @return the formatted component
     */
    public Component get(String key) {
        return MessageUtil.parse(getRaw(key));
    }

    /**
     * Returns the message for {@code key} with placeholder substitutions applied,
     * as a parsed MiniMessage {@link Component}.
     * <p>
     * Placeholders in the lang file are written as {@code {name}} (curly-brace
     * syntax, distinct from MiniMessage tags).  Each entry in {@code replacements}
     * maps a placeholder name to its replacement value.  For example, passing
     * {@code "player" → "Steve"} will replace every {@code {player}} occurrence.
     *
     * @param key          the message key
     * @param replacements placeholder → replacement value map
     * @return the formatted component with placeholders substituted
     */
    public Component get(String key, Map<String, String> replacements) {
        return MessageUtil.parse(getRaw(key), replacements);
    }

    /**
     * Same as {@link #get(String, Map)}, but the placeholder keys listed in
     * {@code rawKeys} are inserted verbatim (trusted MiniMessage) while all other
     * substituted values are escaped. Use for messages that embed a
     * plugin-built formatted fragment (e.g. a coloured prefix sample).
     *
     * @param key          the message key
     * @param replacements placeholder → replacement value map
     * @param rawKeys      keys whose values are trusted MiniMessage
     * @return the formatted component
     */
    public Component get(String key, Map<String, String> replacements, java.util.Set<String> rawKeys) {
        return MessageUtil.parse(getRaw(key), replacements, rawKeys);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Saves the bundled {@code lang/{language}.yml} resource to the plugin data
     * folder if it is not already present.
     */
    private void extractDefault(String language) {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        File target = new File(langDir, language + ".yml");
        if (!target.exists()) {
            String resourcePath = "lang/" + language + ".yml";
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                plugin.getLogger().warning("No bundled lang file for '" + language +
                        "'; messages may be missing.");
            }
        }
    }

    /**
     * Loads a {@link YamlConfiguration} for the given language, preferring the
     * data-folder file but falling back to the jar resource.
     *
     * @param language the language code
     * @return the loaded configuration, or an empty configuration if not found
     */
    private YamlConfiguration loadFile(String language) {
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }

        // Attempt to read directly from the jar resource as a last resort.
        InputStream stream = plugin.getResource("lang/" + language + ".yml");
        if (stream != null) {
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to read lang resource for '" + language + "' from jar", e);
            }
        }

        plugin.getLogger().warning("Lang file not found for '" + language +
                "'; falling back to " + DEFAULT_LANGUAGE + " defaults.");
        return fallbackConfig != null ? fallbackConfig : new YamlConfiguration();
    }
}
