package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.endfight.CustomEndFightManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * Prevents natural regeneration for the egg holder during custom end fight
 */
public class EggHolderRegenerationListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final CustomEndFightManager endFightManager;
    
    public EggHolderRegenerationListener(ChallengeUtil plugin, CustomEndFightManager endFightManager) {
        this.plugin = plugin;
        this.endFightManager = endFightManager;
    }
    
    /**
     * Cancel natural regeneration for the egg holder
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        // Only handle player entities
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // Check if custom end fight is active and this is the egg holder
        if (!endFightManager.isActive() || 
            endFightManager.getEggHolder() == null ||
            !endFightManager.getEggHolder().equals(player)) {
            return;
        }
        
        // Cancel natural regeneration (SATIATED) and peaceful regeneration (REGEN)
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED ||
            event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }
}
