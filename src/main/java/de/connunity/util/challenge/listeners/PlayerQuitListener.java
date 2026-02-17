package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles cleanup when players leave the server
 */
public class PlayerQuitListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public PlayerQuitListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Player cleanup on quit
        plugin.logDebug(event.getPlayer().getName() + " left the server");
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        // Player cleanup on kick
        plugin.logDebug(event.getPlayer().getName() + " was kicked from the server");
    }
}
