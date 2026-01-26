package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command to join the speedrun world from the waiting room
 */
public class JoinCommand implements CommandExecutor {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public JoinCommand(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.getComponent("commands.only-players"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if a reset is in progress
        if (plugin.isResetInProgress()) {
            player.sendMessage(lang.getComponent("commands.reset-in-progress"));
            player.sendMessage(lang.getComponent("commands.reset-in-progress-wait"));
            return true;
        }
        
        // Get world names from config
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        
        // Check if player is in waiting room
        if (!player.getWorld().getName().equals(waitingRoomName)) {
            player.sendMessage(lang.getComponent("join.already-in-speedrun"));
            return true;
        }
        
        // Get or create speedrun world
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);
        
        if (speedrunWorld == null) {
            player.sendMessage(lang.getComponent("join.world-not-exist"));
            player.sendMessage(lang.getComponent("join.world-not-exist-hint"));
            return true;
        }
        
        // Use the world's spawn location (safe spawn set during /fullreset or startup)
        Location safeSpawn = speedrunWorld.getSpawnLocation();
        
        // Make sure chunk is loaded
        if (!safeSpawn.getChunk().isLoaded()) {
            safeSpawn.getChunk().load(true);
        }
        
        // Check if timer is running - if so, put player in spectator mode
        if (plugin.getTimerManager().isRunning() && !plugin.getTimerManager().isPaused()) {
            player.teleport(safeSpawn);
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.sendMessage(lang.getComponent("join.teleported"));
            player.sendMessage(lang.getComponent("join.watch-mode"));
        } else {
            // Normal join - survival mode
            player.teleport(safeSpawn);
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.sendMessage(lang.getComponent("join.teleported"));
            player.sendMessage(lang.getComponent("join.good-luck-emoji"));
        }
        
        return true;
    }
}
