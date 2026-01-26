package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Handles Ender Dragon death in Team Race mode
 * Determines which team killed the dragon based on who dealt damage
 */
public class TeamRaceEnderDragonListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public TeamRaceEnderDragonListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnderDragonDeath(EntityDeathEvent event) {
        // Check if the entity is an Ender Dragon
        if (event.getEntityType() != EntityType.ENDER_DRAGON) {
            return;
        }
        
        // Check if team race mode is enabled and timer is running
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        boolean isTimerRunning = plugin.getTimerManager().isRunning();
        
        if (teamRaceEnabled == null || !teamRaceEnabled || !isTimerRunning) {
            return;
        }
        
        // Determine which team killed the dragon
        String winningTeam = determineWinningTeam(event);
        
        if (winningTeam != null) {
            // Team wins!
            plugin.getTeamRaceManager().checkTeamWin(winningTeam);
        }
    }
    
    /**
     * Determine which team killed the dragon
     * Uses the killer if available, otherwise finds nearby players
     */
    private String determineWinningTeam(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        
        if (killer != null) {
            // Direct killer - get their team
            String team = plugin.getDataManager().getPlayerTeam(killer.getUniqueId());
            if (team != null) {
                return team;
            }
        }
        
        // No direct killer - find nearest player to the dragon
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(event.getEntity().getWorld())) {
                double distance = player.getLocation().distance(event.getEntity().getLocation());
                if (distance < nearestDistance && distance < 100) { // Within 100 blocks
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }
        }
        
        if (nearestPlayer != null) {
            String team = plugin.getDataManager().getPlayerTeam(nearestPlayer.getUniqueId());
            if (team != null) {
                return team;
            }
        }
        
        // Fallback - return first active team (shouldn't normally happen)
        java.util.List<String> activeTeams = plugin.getTeamRaceManager().getActiveTeamNames();
        return activeTeams.isEmpty() ? null : activeTeams.get(0);
    }
}
