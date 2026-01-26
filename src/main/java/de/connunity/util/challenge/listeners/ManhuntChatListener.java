package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.ColorUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Adds team prefixes to player chat messages using PlaceholderAPI with MiniMessage
 */
public class ManhuntChatListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public ManhuntChatListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        
        // Only apply prefix if player has a team
        if (team == null) {
            return;
        }
        
        // Check if PlaceholderAPI is available
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            // Fallback to old behavior if PlaceholderAPI is not installed
            return;
        }
        
        // Get prefix and suffix from PlaceholderAPI
        String prefixString = PlaceholderAPI.setPlaceholders(player, "%ch_prefix%");
        String suffixString = PlaceholderAPI.setPlaceholders(player, "%ch_suffix%");
        
        // Parse with ColorUtil which handles both legacy and MiniMessage
        Component prefix = ColorUtil.parse(prefixString);
        Component suffix = suffixString.isEmpty() ? Component.empty() : ColorUtil.parse(suffixString);
        
        // Modify the chat renderer to include the prefix and suffix
        event.renderer((source, sourceDisplayName, message, viewer) -> 
            prefix
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(suffix)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(Component.text().color(NamedTextColor.WHITE).append(message))
        );
    }
}
