package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
        Player killer = player.getKiller();
        
        boolean isTimerRunning = plugin.getTimerManager().isRunning();
        boolean isTimerPaused = plugin.getTimerManager().isPaused();
        
        // Check if custom end fight is active and this is the egg holder dying
        Boolean customEndFightEnabled = plugin.getDataManager().getSavedChallenge("custom_end_fight");
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (customEndFightEnabled != null && customEndFightEnabled && 
            teamRaceEnabled != null && teamRaceEnabled && isTimerRunning && !isTimerPaused) {
            if (plugin.getCustomEndFightManager().isActive() && 
                plugin.getCustomEndFightManager().getEggHolder() != null &&
                plugin.getCustomEndFightManager().getEggHolder().equals(player)) {
                
                // Egg holder died - determine who gets the egg
                Player newHolder = null;
                
                if (killer != null && !killer.equals(player)) {
                    // Killed by another player directly
                    newHolder = killer;
                } else {
                    // Check if egg holder died to void or other environmental damage
                    // Use the last player who damaged them
                    Player lastDamager = plugin.getCustomEndFightManager().getLastDamager();
                    if (lastDamager != null && lastDamager.isOnline() && !lastDamager.equals(player)) {
                        newHolder = lastDamager;
                    }
                }
                
                if (newHolder != null) {
                    // Transfer egg holder to the new holder
                    plugin.getCustomEndFightManager().onEggHolderKilled(newHolder);
                } else {
                    // No killer found - egg should respawn
                    plugin.getCustomEndFightManager().onEggHolderDied();
                }
                return; // Don't apply other death logic
            }
        }
        
        // Check if manhunt mode is enabled and timer is running
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        
        if (manhuntEnabled != null && manhuntEnabled && isTimerRunning && !isTimerPaused) {
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
                // Don't process death multiple times if already in spectator
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
                
                // Store death location
                final org.bukkit.Location deathLocation = player.getLocation().clone();
                
                // Keep drops and experience as normal
                event.setCancelled(false);
                
                // Set to spectator mode IMMEDIATELY to prevent death loops
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Don't set spectator if player is already spectator (prevent loops)
                    if (player.getGameMode() != GameMode.SPECTATOR) {
                        // Teleport to death location and set spectator mode
                        player.teleport(deathLocation);
                        player.setGameMode(GameMode.SPECTATOR);
                        
                        player.sendMessage(lang.getComponent("death.divider"));
                        player.sendMessage(lang.getComponent("death.you-are-dead"));
                        player.sendMessage(lang.getComponent("death.now-spectator"));
                        player.sendMessage(lang.getComponent("death.divider"));
                        
                        // Check if all runners are dead
                        checkManhuntWinCondition();
                    }
                }, 1L); // 1 tick delay (faster than 10 ticks to prevent death loops)
                
                return;
            }
        }
        
        // Check if connunity hunt mode is enabled and timer is running
        Boolean connunityHuntEnabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        
        if (connunityHuntEnabled != null && connunityHuntEnabled && isTimerRunning && !isTimerPaused) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            
            // If a streamer dies, put them in spectator mode at their death location
            if ("Streamer".equals(team)) {
                // Don't process death multiple times if already in spectator
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
                
                // Store death location
                final org.bukkit.Location deathLocation = player.getLocation().clone();
                
                // Keep drops and experience as normal
                event.setCancelled(false);
                
                // Set to spectator mode IMMEDIATELY to prevent death loops
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Don't set spectator if player is already spectator (prevent loops)
                    if (player.getGameMode() != GameMode.SPECTATOR) {
                        // Teleport to death location and set spectator mode
                        player.teleport(deathLocation);
                        player.setGameMode(GameMode.SPECTATOR);
                        
                        player.sendMessage(lang.getComponent("death.divider"));
                        player.sendMessage(lang.getComponent("death.you-are-dead"));
                        player.sendMessage(lang.getComponent("death.now-spectator"));
                        player.sendMessage(lang.getComponent("death.divider"));
                        
                        // Check if all streamers are dead
                        checkConnunityHuntWinCondition();
                    }
                }, 1L); // 1 tick delay (faster than 10 ticks to prevent death loops)
                
                return;
            }
        }
        
        // Check if respawn is disabled (hardcore mode)
        boolean allowRespawn = plugin.getConfig().getBoolean("challenge.allow-respawn", true);
        
        if (!allowRespawn) {
            // Don't process death multiple times if already in spectator
            if (player.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            
            // Get speedrun world name
            String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
            
            // Only apply in speedrun world
            if (player.getWorld().getName().equals(speedrunWorldName)) {
                // Schedule spectator mode change after respawn
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Don't set spectator if player is already spectator (prevent loops)
                    if (player.getGameMode() != GameMode.SPECTATOR) {
                        player.setGameMode(GameMode.SPECTATOR);
                        player.sendMessage(lang.getComponent("death.divider"));
                        player.sendMessage(lang.getComponent("death.you-are-dead"));
                        player.sendMessage(lang.getComponent("death.respawn-disabled-spectator"));
                        player.sendMessage(lang.getComponent("death.divider"));
                    }
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
    private void announceWinner(String winningTeam) {
        // Stop the timer
        plugin.getTimerManager().stop();
        
        // Stop manhunt manager
        plugin.getManhuntManager().stop();
        
        Component subtitle;
        NamedTextColor winColor = NamedTextColor.GREEN;
        NamedTextColor loseColor = NamedTextColor.RED;
        
        if (winningTeam.equals("HUNTER")) {
            subtitle = lang.getComponent("death.hunters-eliminated-subtitle");
        } else {
            subtitle = lang.getComponent("death.dragon-slain-subtitle");
        }
        
        // Show personalized messages to each player
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            boolean isWinner = (winningTeam.equals("HUNTER") && "Hunter".equals(playerTeam)) ||
                              (winningTeam.equals("RUNNER") && "Runner".equals(playerTeam));
            
            Component title = isWinner ? 
                lang.getComponent("death.you-win-title") : 
                lang.getComponent("death.you-lose-title");
            NamedTextColor color = isWinner ? winColor : loseColor;
            
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
            
            player.showTitle(gameTitle);
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(lang.getComponent("dragon.game-over"));
            player.sendMessage(Component.text(""));
            player.sendMessage(subtitle);
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(Component.text(""));
            
            // Play victory sound
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
    
    /**
     * Check if all streamers are dead (viewers win)
     */
    private void checkConnunityHuntWinCondition() {
        Set<UUID> streamers = plugin.getDataManager().getPlayersInTeam("Streamer");
        
        boolean allStreamersDead = true;
        for (UUID streamerId : streamers) {
            Player streamer = Bukkit.getPlayer(streamerId);
            if (streamer != null && streamer.isOnline() && streamer.getGameMode() != GameMode.SPECTATOR) {
                allStreamersDead = false;
                break;
            }
        }
        
        if (allStreamersDead) {
            // Viewers win!
            announceConnunityHuntWinner("VIEWER");
        }
    }
    
    /**
     * Announce the winning team for Connunity Hunt
     */
    private void announceConnunityHuntWinner(String winningTeam) {
        // Stop the timer
        plugin.getTimerManager().stop();
        
        // Stop connunity hunt manager
        plugin.getConnunityHuntManager().stop();
        
        Component subtitle;
        NamedTextColor winColor = NamedTextColor.GREEN;
        NamedTextColor loseColor = NamedTextColor.RED;
        
        if (winningTeam.equals("VIEWER")) {
            subtitle = lang.getComponent("death.streamers-eliminated-subtitle");
        } else {
            subtitle = lang.getComponent("death.dragon-slain-subtitle");
        }
        
        // Show personalized messages to each player
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            boolean isWinner = (winningTeam.equals("VIEWER") && "Viewer".equals(playerTeam)) ||
                              (winningTeam.equals("STREAMER") && "Streamer".equals(playerTeam));
            
            Component title = isWinner ? 
                lang.getComponent("death.you-win-title") : 
                lang.getComponent("death.you-lose-title");
            NamedTextColor color = isWinner ? winColor : loseColor;
            
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
            
            player.showTitle(gameTitle);
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(lang.getComponent("dragon.game-over"));
            player.sendMessage(Component.text(""));
            player.sendMessage(subtitle);
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(Component.text(""));
            
            // Play victory sound
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
}
