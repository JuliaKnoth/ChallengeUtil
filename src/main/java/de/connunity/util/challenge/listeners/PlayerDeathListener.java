package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Handles player death based on respawn settings
 */
public class PlayerDeathListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public PlayerDeathListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        
        // Check if manhunt mode is enabled and timer is running
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        boolean isTimerRunning = plugin.getTimerManager().isRunning();
        
        if (manhuntEnabled != null && manhuntEnabled && isTimerRunning) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            
            // Spectators should remain in spectator mode (they shouldn't die anyway, but just in case)
            if ("spectator".equals(team)) {
                // Keep spectators in spectator mode
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                }, 1L);
                return;
            }
            
            // If a runner dies, put them in spectator mode at their death location
            if ("runner".equals(team)) {
                // Store death location
                final org.bukkit.Location deathLocation = player.getLocation().clone();
                
                // Keep drops and experience as normal
                event.setCancelled(false);
                
                // Set to spectator mode at death location with a short delay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Teleport to death location and set spectator mode
                    player.teleport(deathLocation);
                    player.setGameMode(GameMode.SPECTATOR);
                    
                    player.sendMessage(lang.getComponent("death.divider"));
                    player.sendMessage(lang.getComponent("death.you-are-dead"));
                    player.sendMessage(lang.getComponent("death.now-spectator"));
                    player.sendMessage(lang.getComponent("death.divider"));
                    
                    // Check if all runners are dead
                    checkManhuntWinCondition();
                }, 10L); // 10 ticks = 0.5 seconds delay
                
                return;
            }
        }
        
        // Check if respawn is disabled (hardcore mode)
        boolean allowRespawn = plugin.getConfig().getBoolean("challenge.allow-respawn", true);
        
        if (!allowRespawn) {
            // Get speedrun world name
            String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
            
            // Only apply in speedrun world
            if (player.getWorld().getName().equals(speedrunWorldName)) {
                // Schedule spectator mode change after respawn
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendMessage(lang.getComponent("death.divider"));
                    player.sendMessage(lang.getComponent("death.you-are-dead"));
                    player.sendMessage(lang.getComponent("death.respawn-disabled-spectator"));
                    player.sendMessage(lang.getComponent("death.divider"));
                }, 1L);
            }
        }
    }
    
    /**
     * Check if all runners are dead (hunters win)
     */
    private void checkManhuntWinCondition() {
        Set<UUID> runners = plugin.getDataManager().getPlayersInTeam("runner");
        
        boolean allRunnersDead = true;
        for (UUID runnerId : runners) {
            Player runner = Bukkit.getPlayer(runnerId);
            if (runner != null && runner.isOnline() && runner.getGameMode() != GameMode.SPECTATOR) {
                allRunnersDead = false;
                break;
            }
        }
        
        if (allRunnersDead) {
            // Hunters win!
            announceWinner("HUNTER");
        }
    }
    
    /**
     * Announce the winning team
     */
    private void announceWinner(String team) {
        // Stop the timer
        plugin.getTimerManager().stop();
        
        // Stop manhunt manager
        plugin.getManhuntManager().stop();
        
        Component title;
        Component subtitle;
        NamedTextColor color;
        
        if (team.equals("HUNTER")) {
            title = lang.getComponent("death.hunters-won-title");
            subtitle = lang.getComponent("death.hunters-won-subtitle");
            color = NamedTextColor.GOLD;
        } else {
            title = lang.getComponent("death.runners-won-title");
            subtitle = lang.getComponent("death.runners-won-subtitle");
            color = NamedTextColor.LIGHT_PURPLE;
        }
        
        // Create title with timings
        Title gameTitle = Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(500),  // Fade in
                Duration.ofSeconds(5),    // Stay
                Duration.ofSeconds(2)     // Fade out
            )
        );
        
        // Show to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(gameTitle);
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(lang.getComponent("dragon.game-over"));
            player.sendMessage(Component.text(""));
            // Localize team display
            String teamLabel = team.equals("HUNTER") ? lang.getMessage("death.winner-hunters") : lang.getMessage("death.winner-runners");
            player.sendMessage(lang.getComponent("dragon.winner")
                    .append(Component.text("TEAM " + teamLabel, color, TextDecoration.BOLD)));
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(Component.text(""));
            
            // Play victory sound
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
}
