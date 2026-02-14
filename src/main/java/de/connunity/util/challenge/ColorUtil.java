package de.connunity.util.challenge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility class for handling color conversions and MiniMessage parsing
 */
public class ColorUtil {
    
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    
    /**
     * Parse a string with MiniMessage format
     * Also supports legacy color codes (& and §) for backwards compatibility
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        
        // First, check if it contains legacy color codes
        if (text.contains("&") || text.contains("§")) {
            // Convert legacy to MiniMessage
            text = convertLegacyToMiniMessage(text);
        }
        
        // Parse with MiniMessage
        return MINI_MESSAGE.deserialize(text);
    }
    
    /**
     * Serialize a Component to MiniMessage string
     */
    public static String serialize(Component component) {
        return MINI_MESSAGE.serialize(component);
    }
    
    /**
     * Convert legacy color codes to MiniMessage format
     * Supports both & and § prefixes
     */
    public static String convertLegacyToMiniMessage(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return legacy;
        }
        
        // Replace & with § for uniform handling
        legacy = legacy.replace('&', '§');
        
        // Convert color codes
        legacy = legacy.replace("§0", "<black>");
        legacy = legacy.replace("§1", "<dark_blue>");
        legacy = legacy.replace("§2", "<dark_green>");
        legacy = legacy.replace("§3", "<dark_aqua>");
        legacy = legacy.replace("§4", "<dark_red>");
        legacy = legacy.replace("§5", "<dark_purple>");
        legacy = legacy.replace("§6", "<gold>");
        legacy = legacy.replace("§7", "<gray>");
        legacy = legacy.replace("§8", "<dark_gray>");
        legacy = legacy.replace("§9", "<blue>");
        legacy = legacy.replace("§a", "<green>");
        legacy = legacy.replace("§b", "<aqua>");
        legacy = legacy.replace("§c", "<red>");
        legacy = legacy.replace("§d", "<light_purple>");
        legacy = legacy.replace("§e", "<yellow>");
        legacy = legacy.replace("§f", "<white>");
        
        // Convert formatting codes
        legacy = legacy.replace("§k", "<obfuscated>");
        legacy = legacy.replace("§l", "<bold>");
        legacy = legacy.replace("§m", "<strikethrough>");
        legacy = legacy.replace("§n", "<underlined>");
        legacy = legacy.replace("§o", "<italic>");
        legacy = legacy.replace("§r", "<reset>");
        
        return legacy;
    }
    
    /**
     * Get MiniMessage color tag for a team
     */
    public static String getTeamColorTag(String team) {
        if (team == null) {
            return "<white>";
        }
        
        switch (team.toLowerCase()) {
            case "runner":
                return "<light_purple>";
            case "hunter":
                return "<gold>";
            case "spectator":
                return "<gray>";
            // Team Race colors
            case "rot":
                return "<red>";
            case "blau":
                return "<blue>";
            case "grün":
                return "<green>";
            case "gelb":
                return "<yellow>";
            case "lila":
                return "<dark_purple>";
            case "aqua":
                return "<aqua>";
            case "weiß":
                return "<white>";
            case "orange":
                return "<gold>";
            case "pink":
                return "<light_purple>";
            case "grau":
                return "<gray>";
            default:
                return "<white>";
        }
    }
    
    /**
     * Convert MiniMessage to legacy color codes (for backwards compatibility)
     */
    public static String miniMessageToLegacy(String miniMessage) {
        Component component = MINI_MESSAGE.deserialize(miniMessage);
        return LEGACY_SECTION.serialize(component);
    }
}
