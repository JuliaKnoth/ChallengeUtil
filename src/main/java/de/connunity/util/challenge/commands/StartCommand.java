package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import de.connunity.util.challenge.timer.TimerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StartCommand implements CommandExecutor {
    
    private final ChallengeUtil plugin;
    private final TimerManager timerManager;
    private final LanguageManager lang;
    
    public StartCommand(ChallengeUtil plugin, TimerManager timerManager) {
        this.plugin = plugin;
        this.timerManager = timerManager;
        this.lang = plugin.getLanguageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("challenge.start") && !sender.hasPermission("challenge.host")) {
            sender.sendMessage(lang.getComponent("commands.no-permission"));
            return true;
        }
        
        // Check if reset is in progress
        if (plugin.isResetInProgress()) {
            sender.sendMessage(lang.getComponent("start.reset-in-progress"));
            sender.sendMessage(lang.getComponent("start.wait-for-reset"));
            return true;
        }
        
        if (timerManager.isRunning() && !timerManager.isPaused()) {
            sender.sendMessage(lang.getComponent("start.already-running"));
            return true;
        }
        
        // Check if manhunt mode is enabled and all players have selected teams
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        
        // Prevent both modes from being enabled at the same time
        if (manhuntEnabled != null && manhuntEnabled && teamRaceEnabled != null && teamRaceEnabled) {
            sender.sendMessage(lang.getComponent("start.both-modes-enabled"));
            sender.sendMessage(lang.getComponent("start.deactivate-one-mode"));
            return true;
        }
        
        if (manhuntEnabled != null && manhuntEnabled) {
            // Get all online players
            Collection<? extends Player> allPlayers = Bukkit.getOnlinePlayers();
            java.util.List<String> playersWithoutTeam = new java.util.ArrayList<>();
            
            for (Player player : allPlayers) {
                String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
                if (team == null) {
                    playersWithoutTeam.add(player.getName());
                }
            }
            
            if (!playersWithoutTeam.isEmpty()) {
                sender.sendMessage(lang.getComponent("start.manhunt-no-team-header"));
                sender.sendMessage(lang.getComponent("start.manhunt-all-must-choose"));
                sender.sendMessage(Component.text(""));
                
                for (String playerName : playersWithoutTeam) {
                    Map<String, String> placeholders = new java.util.HashMap<>();
                    placeholders.put("player", playerName);
                    placeholders.put("command", "/team <runner|hunter|spectator>");
                    sender.sendMessage(lang.getComponent("start.player-needs-team", placeholders));
                }
                
                sender.sendMessage(Component.text(""));
                
                // Also notify the players without teams
                for (String playerName : playersWithoutTeam) {
                    Player p = Bukkit.getPlayer(playerName);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(lang.getComponent("start.must-choose-team-warning"));
                        p.sendMessage(lang.getComponent("start.manhunt-team-command-hint"));
                    }
                }
                
                return true;
            }
            
            // Check if there's at least one runner and one hunter
            Set<UUID> runners = plugin.getDataManager().getPlayersInTeam("runner");
            Set<UUID> hunters = plugin.getDataManager().getPlayersInTeam("hunter");
            
            if (runners.isEmpty()) {
                sender.sendMessage(lang.getComponent("start.no-runners"));
                sender.sendMessage(lang.getComponent("start.need-runner-hint"));
                return true;
            }
            
            if (hunters.isEmpty()) {
                sender.sendMessage(lang.getComponent("start.no-hunters"));
                sender.sendMessage(lang.getComponent("start.need-hunter-hint"));
                return true;
            }
        }
        
        // Check if team race mode is enabled and validate team setup
        if (teamRaceEnabled != null && teamRaceEnabled) {
            // Get all online players
            Collection<? extends Player> allPlayers = Bukkit.getOnlinePlayers();
            java.util.List<String> playersWithoutTeam = new java.util.ArrayList<>();
            
            for (Player player : allPlayers) {
                String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
                if (team == null) {
                    playersWithoutTeam.add(player.getName());
                }
            }
            
            if (!playersWithoutTeam.isEmpty()) {
                sender.sendMessage(lang.getComponent("start.teamrace-no-team-header"));
                sender.sendMessage(lang.getComponent("start.teamrace-all-must-choose"));
                sender.sendMessage(Component.text(""));
                
                for (String playerName : playersWithoutTeam) {
                    Map<String, String> placeholders = new java.util.HashMap<>();
                    placeholders.put("player", playerName);
                    placeholders.put("command", "/team <Teamname>");
                    sender.sendMessage(lang.getComponent("start.player-needs-team", placeholders));
                }
                
                sender.sendMessage(Component.text(""));
                
                // Also notify the players without teams
                for (String playerName : playersWithoutTeam) {
                    Player p = Bukkit.getPlayer(playerName);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(lang.getComponent("start.must-choose-team-warning"));
                        p.sendMessage(lang.getComponent("start.teamrace-team-command-hint"));
                    }
                }
                
                return true;
            }
            
            // Validate that we have 2-10 teams
            if (!plugin.getTeamRaceManager().validateTeamSetup()) {
                int teamCount = plugin.getTeamRaceManager().getActiveTeamCount();
                sender.sendMessage(lang.getComponent("start.invalid-team-config"));
                
                if (teamCount < 2) {
                    Map<String, String> placeholders = new java.util.HashMap<>();
                    placeholders.put("count", String.valueOf(teamCount));
                    sender.sendMessage(lang.getComponent("start.min-teams-needed", placeholders));
                } else if (teamCount > 10) {
                    Map<String, String> placeholders = new java.util.HashMap<>();
                    placeholders.put("count", String.valueOf(teamCount));
                    sender.sendMessage(lang.getComponent("start.max-teams-exceeded", placeholders));
                }
                
                return true;
            }
        }
        
        // If paused, just resume (no countdown)
        if (timerManager.isPaused()) {
            timerManager.resume();
            
            // Broadcast to all players
            Bukkit.broadcast(lang.getComponent("start.timer-resumed"));
            return true;
        }
        
        // Starting fresh timer - get worlds
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        
        World waitingRoom = Bukkit.getWorld(waitingRoomName);
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);
        
        if (speedrunWorld == null) {
            sender.sendMessage(lang.getComponent("start.world-not-exist"));
            sender.sendMessage(lang.getComponent("start.run-fullreset-first"));
            return true;
        }
        
        // Get all players in the waiting room/lobby
        Collection<? extends Player> allPlayers = Bukkit.getOnlinePlayers();
        
        // Use the world's spawn location (safe spawn set during /fullreset or startup)
        Location spawnLoc = speedrunWorld.getSpawnLocation();
        
        // Teleport all players in the waiting room to speedrun world
        int playerCount = 0;
        for (Player player : allPlayers) {
            if (waitingRoom != null && player.getWorld().getName().equals(waitingRoomName)) {
                player.teleport(spawnLoc);
                player.setGameMode(GameMode.SURVIVAL);
                
                // Reset hunger/saturation to normal starting values
                // This fixes the bug where players don't get hungry after game starts
                // (they had max saturation from waiting room which prevented hunger depletion)
                player.setFoodLevel(20);
                player.setSaturation(5.0f); // Normal starting saturation
                player.setExhaustion(0.0f);
                
                playerCount++;
            }
        }
        
        if (playerCount > 0) {
            Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("count", String.valueOf(playerCount));
            sender.sendMessage(lang.getComponent("start.teleported-players", placeholders));
        }
        
        // Apply saved gamerules to all worlds to ensure they're active
        for (World world : Bukkit.getWorlds()) {
            // Skip the waiting room
            if (!world.getName().equals(waitingRoomName)) {
                plugin.applySavedGamerulesToWorld(world);
            }
        }
        
        // Set time to day and weather to clear (initial start only)
        speedrunWorld.setTime(1000L); // Morning time (1000 ticks)
        speedrunWorld.setStorm(false); // No rain
        speedrunWorld.setThundering(false); // No thunder
        speedrunWorld.setWeatherDuration(999999); // Keep clear for a long time
        
        // Start 5-second countdown for all players
        startCountdown();
        return true;
    }
    
    /**
     * Start a 5-second countdown before starting the timer
     * Broadcasts to all players and plays sounds
     */
    private void startCountdown() {
        // Broadcast starting message
        Bukkit.broadcast(lang.getComponent("start.countdown-starting"));
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> showCountdownNumber(5), 0L);
    }
    
    private void showCountdownNumber(int secondsLeft) {
        if (secondsLeft <= 0) {
            // Countdown finished - start timer!
            timerManager.start();
            
            // Start manhunt mechanics if enabled
            plugin.getManhuntManager().start();
            
            // Start team race mechanics if enabled
            plugin.getTeamRaceManager().start();
            
            // Start chunk item challenge if enabled
            plugin.getChunkItemChallengeListener().start();
            
            // Notify hunters about their restrictions
            Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
            if (manhuntEnabled != null && manhuntEnabled) {
                for (UUID hunterId : plugin.getDataManager().getPlayersInTeam("hunter")) {
                    Player hunter = Bukkit.getPlayer(hunterId);
                    if (hunter != null && hunter.isOnline()) {
                        hunter.sendMessage(Component.text(""));
                        hunter.sendMessage(lang.getComponent("start.hunter-restrictions-header"));
                        hunter.sendMessage(lang.getComponent("start.hunter-headstart"));
                        hunter.sendMessage(lang.getComponent("start.hunter-compass-unlock"));
                        hunter.sendMessage(Component.text(""));
                    }
                }
            }
            
            // Broadcast to all players
            Title goTitle = Title.title(
                lang.getComponent("start.go-title"),
                lang.getComponent("start.good-luck-subtitle"),
                Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ofMillis(500))
            );
            
            // Show title and play sound for all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(goTitle);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            
            Bukkit.broadcast(lang.getComponent("start.timer-started"));
            return;
        }
        
        // Show countdown number
        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("seconds", String.valueOf(secondsLeft));
        
        Title countdownTitle = Title.title(
            lang.getComponent(secondsLeft <= 2 ? "start.countdown-number-red" : "start.countdown-number-yellow", placeholders),
            lang.getComponent("start.get-ready-subtitle"),
            Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(800), Duration.ofMillis(200))
        );
        
        // Determine sound pitch based on countdown
        float pitch = secondsLeft <= 2 ? 1.5f : 1.0f;
        Sound sound = secondsLeft <= 2 ? Sound.BLOCK_NOTE_BLOCK_HAT : Sound.BLOCK_NOTE_BLOCK_PLING;
        
        // Show title and play sound for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(countdownTitle);
            player.playSound(player.getLocation(), sound, 1.0f, pitch);
        }
        
        // Schedule next number (1 second = 20 ticks)
        Bukkit.getScheduler().runTaskLater(plugin, () -> showCountdownNumber(secondsLeft - 1), 20L);
    }
}
