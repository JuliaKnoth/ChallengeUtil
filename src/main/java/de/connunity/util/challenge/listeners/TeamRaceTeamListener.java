package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Prevents players on the same team from damaging each other in Team Race mode
 */
public class TeamRaceTeamListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public TeamRaceTeamListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled == null || !teamRaceEnabled) {
            return;
        }
        
        // Check if friendly fire challenge is enabled - if so, allow team damage
        Boolean friendlyFireEnabled = plugin.getDataManager().getSavedChallenge("friendly_fire_item");
        if (friendlyFireEnabled != null && friendlyFireEnabled) {
            return; // Allow damage when friendly fire challenge is active
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        // Check if both entities are players
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();
        
        // Get teams
        String victimTeam = plugin.getDataManager().getPlayerTeam(victim.getUniqueId());
        String attackerTeam = plugin.getDataManager().getPlayerTeam(attacker.getUniqueId());
        
        // If both are on the same team, cancel damage
        if (victimTeam != null && victimTeam.equals(attackerTeam)) {
            event.setCancelled(true);
            attacker.sendMessage(lang.getComponent("restrictions.cannot-damage-teammate-teamrace"));
        }
    }
}
