package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.endfight.CustomEndFightManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Tracks damage to the egg holder during custom end fight
 */
public class EndFightDamageListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final CustomEndFightManager endFightManager;
    
    public EndFightDamageListener(ChallengeUtil plugin, CustomEndFightManager endFightManager) {
        this.plugin = plugin;
        this.endFightManager = endFightManager;
    }
    
    /**
     * Track when a player damages the egg holder
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Check if custom end fight is active
        if (!endFightManager.isActive() || !endFightManager.isEggCollected()) {
            return;
        }
        
        // Check if the damaged entity is a player
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player damagedPlayer = (Player) event.getEntity();
        
        // Check if the damaged player is the egg holder
        if (endFightManager.getEggHolder() == null || 
            !endFightManager.getEggHolder().equals(damagedPlayer)) {
            return;
        }
        
        // Check if the damager is a player
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player damager = (Player) event.getDamager();
        
        // Don't track self-damage
        if (damager.equals(damagedPlayer)) {
            return;
        }
        
        // Track this player as the last damager
        endFightManager.setLastDamager(damager);
    }
}
