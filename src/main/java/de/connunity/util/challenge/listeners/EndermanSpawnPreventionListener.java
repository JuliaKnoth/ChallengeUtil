package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Prevents enderman spawns in the End dimension
 */
public class EndermanSpawnPreventionListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public EndermanSpawnPreventionListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Cancel enderman spawns in the End dimension
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Check if it's an enderman
        if (event.getEntityType() != EntityType.ENDERMAN) {
            return;
        }
        
        // Check if spawning in the End dimension
        if (event.getEntity().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        
        // Cancel the spawn
        event.setCancelled(true);
    }
}
