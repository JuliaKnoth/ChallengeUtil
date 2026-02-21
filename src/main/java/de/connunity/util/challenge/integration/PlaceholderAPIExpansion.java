package de.connunity.util.challenge.integration;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.ColorUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI Expansion for ChallengeUtil
 * Provides placeholders for team prefixes and suffixes using MiniMessage
 */
public class PlaceholderAPIExpansion extends PlaceholderExpansion {
    
    private final ChallengeUtil plugin;
    
    public PlaceholderAPIExpansion(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @Override
    @NotNull
    public String getIdentifier() {
        return "ch";
    }
    
    @Override
    @NotNull
    public String getAuthor() {
        return "Julia";
    }
    
    @Override
    @NotNull
    public String getVersion() {
        return "1.1.2";
    }
    
    @Override
    public boolean persist() {
        return true; // Required to stay loaded
    }
    
    @Override
    @Nullable
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }
        
        // Get player's team
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        
        // %ch_prefix%
        if (identifier.equals("prefix")) {
            return getPrefix(team);
        }
        
        // %ch_suffix%
        if (identifier.equals("suffix")) {
            return getSuffix(team);
        }
        
        // %ch_team%
        if (identifier.equals("team")) {
            return team != null ? team : "";
        }
        
        // %ch_team_color%
        if (identifier.equals("team_color")) {
            return getTeamColorCode(team);
        }
        
        return null;
    }
    
    /**
     * Get the prefix for a team
     */
    private String getPrefix(String team) {
        if (team == null) {
            return "";
        }
        
        String prefix;
        switch (team.toLowerCase()) {
            case "runner":
                prefix = "<light_purple><bold>[RUNNER]</bold> <reset>";
                break;
            case "hunter":
                prefix = "<gold><bold>[HUNTER]</bold> <reset>";
                break;
            case "spectator":
                prefix = "<gray><bold>[SPECTATOR]</bold> <reset>";
                break;
            case "streamer":
                prefix = "<#9146ff><bold>[STREAMER]</bold> <reset>";
                break;
            case "viewer":
                prefix = "<gray><bold>[VIEWER]</bold> <reset>";
                break;
            default:
                // Team Race mode - get color from TeamRaceManager
                String colorTag = ColorUtil.getTeamColorTag(team);
                prefix = colorTag + "<bold>[" + team.toUpperCase() + "]</bold> <reset>";
                break;
        }
        
        // Return MiniMessage format directly for modern proxy/plugin compatibility
        // This allows Velocitab and other MiniMessage-compatible plugins to parse it correctly
        return prefix;
    }
    
    /**
     * Get the suffix for a team (currently empty, but available for future use)
     */
    private String getSuffix(String team) {
        // Can be extended in the future if suffixes are needed
        return "";
    }
    
    /**
     * Get the color code for a team (MiniMessage format for modern proxy support)
     */
    private String getTeamColorCode(String team) {
        if (team == null) {
            return "<white>"; // White
        }
        
        switch (team.toLowerCase()) {
            case "runner":
                return "<light_purple>"; // Light purple
            case "hunter":
                return "<gold>"; // Gold
            case "spectator":
                return "<gray>"; // Gray
            case "streamer":
                return "<#9146ff>"; // Purple
            case "viewer":
                return "<gray>"; // Gray
            default:
                // Team Race mode - get color tag from ColorUtil
                return ColorUtil.getTeamColorTag(team);
        }
    }
}
