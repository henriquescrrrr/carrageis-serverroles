package pt.henrique.serverroles.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for handling MiniMessage text formatting.
 * Provides methods for parsing MiniMessage strings into Adventure Components
 * and for stripping tags to produce plain text.
 */
public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private MessageUtil() {
        // utility class
    }

    /**
     * Parses a MiniMessage string into an Adventure Component.
     *
     * @param message the MiniMessage-formatted string
     * @return the parsed Component
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * Parses a MiniMessage string with placeholder replacements.
     * Placeholders use curly-brace syntax: {key} → value.
     *
     * @param message      the MiniMessage-formatted string
     * @param replacements map of placeholder keys to replacement values (e.g. "player" → "Steve")
     * @return the parsed Component with placeholders replaced
     */
    public static Component parse(String message, Map<String, String> replacements) {
        return parse(message, replacements, Collections.emptySet());
    }

    /**
     * Parses a MiniMessage string with placeholder replacements, escaping the
     * substituted values so any MiniMessage tags they contain render as literal
     * text instead of being interpreted (prevents tag injection from ids, names,
     * or error strings). Keys listed in {@code rawKeys} are inserted verbatim —
     * use them only for values the plugin itself built as trusted MiniMessage
     * (e.g. a hex-coloured prefix sample).
     *
     * @param message      the MiniMessage-formatted template (must be plugin-authored)
     * @param replacements map of placeholder keys to replacement values
     * @param rawKeys      keys whose values are trusted MiniMessage and left un-escaped
     * @return the parsed Component with placeholders replaced
     */
    public static Component parse(String message, Map<String, String> replacements, Set<String> rawKeys) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        String result = message;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String value = entry.getValue();
            if (value == null) {
                value = "";
            } else if (rawKeys == null || !rawKeys.contains(entry.getKey())) {
                // Treat the value as data: neutralise any MiniMessage tags in it.
                value = MINI_MESSAGE.escapeTags(value);
            }
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return MINI_MESSAGE.deserialize(result);
    }

    /**
     * Strips MiniMessage tags from a string, returning plain text.
     *
     * @param miniMessageString the MiniMessage-formatted string
     * @return the plain text without any formatting tags
     */
    public static String stripTags(String miniMessageString) {
        if (miniMessageString == null || miniMessageString.isEmpty()) {
            return "";
        }
        return PLAIN.serialize(MINI_MESSAGE.deserialize(miniMessageString));
    }

    /**
     * Serializes a Component to a MiniMessage string.
     *
     * @param component the component to serialize
     * @return the MiniMessage representation
     */
    public static String serialize(Component component) {
        if (component == null) {
            return "";
        }
        return MINI_MESSAGE.serialize(component);
    }

    /**
     * Converts a hex color string (e.g. "#ff5555") into a MiniMessage opening color tag
     * (e.g. "&lt;color:#ff5555&gt;"). If the input is null, empty, or not a valid hex color,
     * returns an empty string.
     *
     * @param hex the hex color string (must start with '#' and be 7 characters)
     * @return the MiniMessage color tag, or empty string if invalid
     */
    public static String hexToMiniMessage(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        // Normalise: strip whitespace
        hex = hex.trim();
        // Accept with or without '#'
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }
        if (!hex.matches("#[0-9a-fA-F]{6}")) {
            return "";
        }
        return "<color:" + hex.toLowerCase() + ">";
    }

    /**
     * Wraps the given text in a MiniMessage hex color tag.
     * E.g. hexColorText("#ff5555", "Hello") → "&lt;color:#ff5555&gt;Hello&lt;/color:#ff5555&gt;"
     *
     * @param hex  the hex color (e.g. "#ff5555")
     * @param text the text to color
     * @return the MiniMessage-wrapped string, or just the text if hex is invalid
     */
    public static String hexColorText(String hex, String text) {
        String tag = hexToMiniMessage(hex);
        if (tag.isEmpty()) {
            return text;
        }
        // closing tag: </color:#rrggbb>
        String closeTag = tag.replace("<", "</");
        return tag + text + closeTag;
    }

    /**
     * @return the shared MiniMessage instance
     */
    public static MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }
}

