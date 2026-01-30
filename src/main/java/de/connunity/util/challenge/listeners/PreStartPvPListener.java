package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Prevents all player-vs-player combat before the timer has started
 */
public class PreStartPvPListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public PreStartPvPListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Only check if timer is not running
        if (plugin.getTimerManager().isRunning()) {
            return; // Timer is running, allow normal combat rules
        }
        
        // Check if both entities are players
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        
        // Timer hasn't started yet - prevent all PvP
        event.setCancelled(true);
        
        Player attacker = (Player) event.getDamager();
    }
}
