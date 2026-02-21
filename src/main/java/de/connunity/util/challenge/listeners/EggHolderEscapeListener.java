package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.endfight.CustomEndFightManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Handles when the egg holder escapes from the End to the Overworld
 * This is a backup/fallback listener to ensure we catch all world changes
 */
public class EggHolderEscapeListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final CustomEndFightManager endFightManager;
    
    public EggHolderEscapeListener(ChallengeUtil plugin, CustomEndFightManager endFightManager) {
        this.plugin = plugin;
        this.endFightManager = endFightManager;
    }
    
    /**
     * Check if the egg holder escaped to the overworld
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        // Don't kill players who are already in spectator or creative mode
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || 
            player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        
        // Check if custom end fight is active
        if (!endFightManager.isActive()) {
            return;
        }
        
        // Check if this is the egg holder
        if (endFightManager.getEggHolder() == null || 
            !endFightManager.getEggHolder().getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        
        // Check if player moved from End to Overworld
        World fromWorld = event.getFrom();
        World toWorld = player.getWorld();
        
        if (fromWorld.getEnvironment() == World.Environment.THE_END && 
            toWorld.getEnvironment() == World.Environment.NORMAL) {
            
            // Check if portal is unlocked (team completed 10 minutes)
            if (endFightManager.isPortalUnlocked()) {
                // Portal is unlocked - egg holder can escape and win
                plugin.logInfo(player.getName() + " (egg holder) escaped to Overworld via world change - team wins!");
                endFightManager.onEggHolderEscapedToOverworld();
            } else {
                // Portal is NOT unlocked - egg holder escaped illegally, kill them
                plugin.logWarning(player.getName() + " (egg holder) entered Overworld BEFORE completing 10 minutes - killing player and respawning egg");
                
                // Send message to all players
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    p.sendMessage(Component.text(""));
                    p.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.RED));
                    p.sendMessage(Component.text("⚠ ", NamedTextColor.RED)
                        .append(Component.text(player.getName() + " tried to escape before completing 10 minutes!", NamedTextColor.YELLOW)));
                    p.sendMessage(Component.text("The egg has respawned in the End!", NamedTextColor.GOLD));
                    p.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.RED));
                    p.sendMessage(Component.text(""));
                }
                
                // Kill the player
                player.setHealth(0.0);
                
                // The death event will handle egg respawn via onEggHolderDied()
            }
        }
    }
}

