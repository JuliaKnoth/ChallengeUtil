package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Tracks kills in Team Race mode and updates player suffixes with kill count
 */
public class TeamRaceKillListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public TeamRaceKillListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled == null || !teamRaceEnabled) {
            return;
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        
        // Only track if there was a killer (PvP kill)
        if (killer == null) {
            return;
        }
        
        // Get teams
        String victimTeam = plugin.getDataManager().getPlayerTeam(victim.getUniqueId());
        String killerTeam = plugin.getDataManager().getPlayerTeam(killer.getUniqueId());
        
        // Only count kills between different teams
        if (victimTeam == null || killerTeam == null || victimTeam.equals(killerTeam)) {
            return;
        }
        
        // Increment kill count for killer
        plugin.getDataManager().incrementTeamRaceKills(killer.getUniqueId());
        
        // Update killer's suffix
        plugin.getTeamRaceManager().updatePlayerSuffix(killer);
    }
}
