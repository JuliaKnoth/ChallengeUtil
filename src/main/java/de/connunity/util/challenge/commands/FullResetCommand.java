package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.ColorUtil;
import de.connunity.util.challenge.timer.TimerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Holodeck" Strategy Reset Command
 * 
 * This uses a waiting room architecture to reset the speedrun world WITHOUT server restart.
 * Players are teleported to a waiting room, the speedrun world is deleted/regenerated,
 * then players are teleported back. Total time: ~3-5 seconds.
 * 
 * This is MUCH faster than restarting the server and works perfectly with Velocity/BungeeCord.
 */
public class FullResetCommand implements CommandExecutor {
    
    private final ChallengeUtil plugin;
    private final TimerManager timerManager;
    
    public FullResetCommand(ChallengeUtil plugin, TimerManager timerManager) {
        this.plugin = plugin;
        this.timerManager = timerManager;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        
    if (!sender.hasPermission("challenge.fullreset") && !sender.hasPermission("challenge.host")) {
        sender.sendMessage(Component.text("Du hast keine Berechtigung, diesen Befehl zu verwenden!", 
            NamedTextColor.RED));
            return true;
        }
        
        if (plugin.isResetInProgress()) {
            sender.sendMessage(Component.text("Ein Reset läuft bereits!", NamedTextColor.RED));
            return true;
        }
        
        // Start the "Holodeck" reset process
        performHolodeckReset(sender.getName());
        
        return true;
    }
    
    /**
     * The "Holodeck" Strategy - Instant reset without server restart
     */
    private void performHolodeckReset(String initiator) {
        plugin.setResetInProgress(true);
        
        // Get configuration
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        int countdown = plugin.getConfig().getInt("reset.countdown", 3);
        
        // Generate new seed
        long newSeed = new Random().nextLong();
        
        // Get waiting room world (must exist!)
        World waitingRoom = Bukkit.getWorld(waitingRoomName);
    if (waitingRoom == null) {
        plugin.getLogger().severe("FATAL: Waiting room '" + waitingRoomName + "' does not exist!");
        plugin.getLogger().severe("Please create the waiting room world first. See setup guide.");
        Bukkit.broadcast(Component.text("Reset fehlgeschlagen! Warteraum nicht gefunden. Bitte Admin kontaktieren.", 
            NamedTextColor.RED));
            plugin.setResetInProgress(false);
            return;
        }
        
        // Reset timer and clear all persistent data
        timerManager.reset();
        plugin.getDataManager().clearAllData();
        
        // Reset all players (clear inventory except host item, reset HP, level, achievements)
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayer(player);
        }
        plugin.getLogger().info("Reset all players for full reset (" + Bukkit.getOnlinePlayers().size() + " players)");
        
        // Log
        plugin.getLogger().info("═══════════════════════════════════");
        plugin.getLogger().info("HOLODECK RESET initiated by " + initiator);
        plugin.getLogger().info("New seed: " + newSeed);
        plugin.getLogger().info("═══════════════════════════════════");
        
        // PHASE 1: Teleport all players to waiting room (on this server)
        List<Player> playersToTeleport = new ArrayList<>(Bukkit.getOnlinePlayers());
        
        plugin.getLogger().info("Teleporting " + playersToTeleport.size() + " players to waiting room");
        
        Location waitingRoomSpawn = getWaitingRoomSpawn(waitingRoom);
        
        for (Player player : playersToTeleport) {
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Neue Welt wird generiert!", NamedTextColor.RED, TextDecoration.BOLD));
            player.sendMessage(Component.text("Du wirst zum Warteraum teleportiert...", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Nach ca. 30 Sekunden kannst du eine neue Runde starten!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
            
            // Teleport to waiting room
            player.teleport(waitingRoomSpawn);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        }
        
        plugin.getLogger().info("Players teleported to waiting room. Starting world reset...");
        
        // PHASE 2: Small delay, then delete and regenerate
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deleteAndRegenerateWorld(speedrunWorldName, newSeed, playersToTeleport);
        }, 40L); // 2 second delay to ensure teleportation is complete
    }
    
    /**
     * Send a player to the lobby server via proxy plugin messaging
     */
    private void sendToLobby(Player player, String serverName) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);
            
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send " + player.getName() + " to lobby: " + e.getMessage());
            plugin.getLogger().warning("Player will be teleported to waiting room instead.");
            
            // Fallback: teleport to waiting room
            World waitingRoom = Bukkit.getWorld(plugin.getConfig().getString("world.waiting-room", "waiting_room"));
            if (waitingRoom != null) {
                player.teleport(getWaitingRoomSpawn(waitingRoom));
                // Set player to adventure mode in waiting room
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        }
    }
    
    /**
     * Countdown before world deletion (REMOVED - now sends to lobby instead)
     */
    private void runCountdown(int seconds, Runnable onComplete) {
        if (seconds <= 0) {
            onComplete.run();
            return;
        }
        
        String countdownMsg = plugin.getConfig().getString("reset.messages.reset-countdown",
                "<yellow><bold>Reset in {seconds} seconds...");
        countdownMsg = countdownMsg.replace("{seconds}", String.valueOf(seconds));
        Bukkit.broadcast(ColorUtil.parse(countdownMsg));
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            runCountdown(seconds - 1, onComplete);
        }, 20L);
    }
    
    /**
     * Delete and regenerate the speedrun world ASYNCHRONOUSLY
     */
    private void deleteAndRegenerateWorld(String worldName, long seed, List<Player> players) {
        plugin.getLogger().info("Starting world deletion for: " + worldName);
        
        World speedrunWorld = Bukkit.getWorld(worldName);
        
        // Unload the world if it exists
        if (speedrunWorld != null) {
            plugin.getLogger().info("Unloading world: " + worldName);
            Bukkit.unloadWorld(speedrunWorld, false); // Don't save - we're deleting it
        }
        
        // Delete world folders ASYNCHRONOUSLY
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File serverRoot = Bukkit.getWorldContainer();
            
            try {
                deleteWorldFolder(new File(serverRoot, worldName));
                deleteWorldFolder(new File(serverRoot, worldName + "_nether"));
                deleteWorldFolder(new File(serverRoot, worldName + "_the_end"));
                
                plugin.getLogger().info("World folders deleted successfully.");
                
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to delete world folders: " + e.getMessage());
                e.printStackTrace();
                
                // Reset flag on error
                Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.setResetInProgress(false);
            Bukkit.broadcast(Component.text("Welt-Löschung fehlgeschlagen! Prüfe die Konsole auf Fehler.", 
                NamedTextColor.RED));
                });
                return;
            }
            
            // PHASE 4: Regenerate world (must be on main thread)
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    regenerateWorld(worldName, seed, players);
                } catch (Exception e) {
            plugin.getLogger().severe("Failed to regenerate world: " + e.getMessage());
            e.printStackTrace();
            plugin.setResetInProgress(false);
            Bukkit.broadcast(Component.text("Welt-Generierung fehlgeschlagen! Prüfe die Konsole.", 
                NamedTextColor.RED));
                }
            });
        });
    }
    
    /**
     * Regenerate the speedrun world with new seed
     */
    private void regenerateWorld(String worldName, long seed, List<Player> players) {
        plugin.getLogger().info("Regenerating world: " + worldName + " with seed: " + seed);
        
        try {
            // Create world creator
            WorldCreator creator = new WorldCreator(worldName);
            creator.seed(seed);
            
            // Get world type from config
            String worldType = plugin.getConfig().getString("world.generation.type", "NORMAL");
            creator.type(WorldType.valueOf(worldType.toUpperCase()));
            
            // Generate structures? - Use persistent setting if available
            Boolean savedStructures = plugin.getDataManager().getSavedStructures();
            boolean generateStructures = savedStructures != null ? savedStructures : 
                    plugin.getConfig().getBoolean("world.generation.generate-structures", true);
            creator.generateStructures(generateStructures);
            
            plugin.getLogger().info("Creating world (structures: " + generateStructures + ")...");
            
            // Create the world (Overworld)
            // NOTE: To reduce freezing, set "initial-world-border-size=0" in server.properties!
            World newWorld = creator.createWorld();
            
                if (newWorld == null) {
                plugin.getLogger().severe("FAILED to create world!");
                Bukkit.broadcast(Component.text("Welt-Erstellung fehlgeschlagen! Bitte Admin kontaktieren.", NamedTextColor.RED));
                plugin.setResetInProgress(false);
                return;
            }
            
            plugin.getLogger().info("Overworld created successfully!");
            
            // Create the Nether dimension for this world
            WorldCreator netherCreator = new WorldCreator(worldName + "_nether");
            netherCreator.seed(seed);
            netherCreator.environment(World.Environment.NETHER);
            netherCreator.generateStructures(generateStructures);
            World netherWorld = netherCreator.createWorld();
            
            if (netherWorld != null) {
                netherWorld.setKeepSpawnInMemory(false);
                plugin.getLogger().info("Nether dimension created successfully!");
            } else {
                plugin.getLogger().warning("Failed to create Nether dimension!");
            }
            
            // Create the End dimension for this world
            WorldCreator endCreator = new WorldCreator(worldName + "_the_end");
            endCreator.seed(seed);
            endCreator.environment(World.Environment.THE_END);
            endCreator.generateStructures(generateStructures);
            World endWorld = endCreator.createWorld();
            
            if (endWorld != null) {
                endWorld.setKeepSpawnInMemory(false);
                plugin.getLogger().info("End dimension created successfully!");
            } else {
                plugin.getLogger().warning("Failed to create End dimension!");
            }
            
            // IMPORTANT: Disable spawn chunk loading to prevent server lag
            newWorld.setKeepSpawnInMemory(false);
            
            // Set time to day
            newWorld.setTime(1000L); // Morning time (1000 ticks)
            plugin.getLogger().info("Set world time to day (1000 ticks)");
            
            // Apply difficulty setting from persistent data (if available)
            String savedDifficulty = plugin.getDataManager().getSavedDifficulty();
            String difficultyStr = savedDifficulty != null ? savedDifficulty : 
                    plugin.getConfig().getString("world.difficulty", "NORMAL");
            try {
                Difficulty difficulty = Difficulty.valueOf(difficultyStr.toUpperCase());
                newWorld.setDifficulty(difficulty);
                
                // Apply same difficulty to Nether and End
                if (netherWorld != null) {
                    netherWorld.setDifficulty(difficulty);
                }
                if (endWorld != null) {
                    endWorld.setDifficulty(difficulty);
                }
                
                plugin.getLogger().info("Set world difficulty to: " + difficulty + " (from persistent settings)");
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid difficulty: " + difficultyStr + ", using NORMAL");
                newWorld.setDifficulty(Difficulty.NORMAL);
                if (netherWorld != null) netherWorld.setDifficulty(Difficulty.NORMAL);
                if (endWorld != null) endWorld.setDifficulty(Difficulty.NORMAL);
            }
            
            // Apply saved gamerules to all three dimensions (persistent settings)
            // Note: We ONLY apply saved gamerules, not config gamerules, to ensure persistence
            plugin.applySavedGamerulesToWorld(newWorld);
            if (netherWorld != null) {
                plugin.applySavedGamerulesToWorld(netherWorld);
            }
            if (endWorld != null) {
                plugin.applySavedGamerulesToWorld(endWorld);
            }
            
            // Save seed to persistence
            plugin.getDataManager().saveWorldSeed(seed);
            
            // PHASE 5: Load spawn chunks and set proper spawn point
            loadSpawnChunksAndSetSpawn(newWorld, players);
            
            // CRITICAL: Re-apply gamerules AFTER spawn chunks are loaded
            // This ensures they don't get reset by chunk loading
            plugin.getLogger().info("Re-applying gamerules after spawn chunk loading...");
            plugin.applySavedGamerulesToWorld(newWorld);
            if (netherWorld != null) {
                plugin.applySavedGamerulesToWorld(netherWorld);
            }
            if (endWorld != null) {
                plugin.applySavedGamerulesToWorld(endWorld);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error during world regeneration: " + e.getMessage());
            e.printStackTrace();
            Bukkit.broadcast(Component.text("World regeneration failed! Contact admin.", NamedTextColor.RED));
            plugin.setResetInProgress(false);
        }
    }
    
    /**
     * Load spawn chunks asynchronously and set proper spawn location
     */
    private void loadSpawnChunksAndSetSpawn(World world, List<Player> players) {
        plugin.getLogger().info("Pre-generating spawn area to prevent loading lag...");
        
        // Get base spawn location from config
        Location baseSpawn = getSpeedrunSpawn(world);
        Chunk spawnChunk = baseSpawn.getChunk();
        
        // Pre-generate a LARGER area (11x11 = 121 chunks) to prevent any loading lag
        // This ensures smooth experience when players first join
        final int radius = 5; // 11x11 grid (5 chunks in each direction)
        final int totalChunks = (radius * 2 + 1) * (radius * 2 + 1); // 121 chunks
        final int[] loadedChunks = {0};
        
        plugin.getLogger().info("Pre-generating " + totalChunks + " chunks (11x11 grid)...");
        
        // Load chunks ASYNCHRONOUSLY
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                final int chunkX = spawnChunk.getX() + x;
                final int chunkZ = spawnChunk.getZ() + z;
                
                world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
                    // Force the chunk to stay loaded
                    chunk.setForceLoaded(true);
                    chunk.addPluginChunkTicket(plugin);
                    
                    loadedChunks[0]++;
                    
                    if (loadedChunks[0] % 25 == 0 || loadedChunks[0] == totalChunks) {
                        plugin.getLogger().info("Pre-generated " + loadedChunks[0] + "/" + totalChunks + " chunks...");
                    }
                    
                    // When all chunks are loaded, set spawn and bring players back
                    if (loadedChunks[0] == totalChunks) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getLogger().info("All spawn chunks loaded! Finding safe spawn point...");
                            
                            // Find safe spawn on solid ground
                            Location safeSpawn = findSafeSurfaceLocation(baseSpawn);
                            plugin.getLogger().info("Safe spawn found at X=" + safeSpawn.getBlockX() + 
                                    " Y=" + safeSpawn.getBlockY() + " Z=" + safeSpawn.getBlockZ());
                            
                            // Set world spawn to the safe location
                            world.setSpawnLocation(safeSpawn);
                            plugin.getLogger().info("World spawn point set!");
                            
                            // Notify players that the world is ready (but don't teleport them back)
                            notifyPlayersResetComplete(players);
                        });
                    }
                });
            }
        }
    }
    
    /**
     * Notify players that the reset is complete (but keep them in waiting room)
     */
    private void notifyPlayersResetComplete(List<Player> players) {
        plugin.getLogger().info("World reset complete! Notifying players in waiting room...");
        
        // Wait a moment for everything to stabilize
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            
            // Also broadcast to everyone on the server
            Bukkit.broadcast(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
            Bukkit.broadcast(Component.text("WORLD RESET COMPLETE!", NamedTextColor.GREEN, TextDecoration.BOLD));
            Bukkit.broadcast(Component.text("Ihr könnt eine neue Runde starten!", NamedTextColor.AQUA));
            Bukkit.broadcast(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
            
            plugin.getLogger().info("═══════════════════════════════════");
            plugin.getLogger().info("RESET COMPLETE! Players can now use /start or /join");
            plugin.getLogger().info("═══════════════════════════════════");
            
            // Reset complete - clear the flag
            plugin.setResetInProgress(false);
            
        }, 60L); // 3 second delay for world to stabilize
    }
    
    /**
     * OLD METHOD - Send players back via proxy (NO LONGER USED)
     */
    @Deprecated
    private void bringPlayersBackFromLobby(List<String> playerNames) {
        plugin.getLogger().info("World reset complete! Bringing players back from lobby...");
        
        String thisServerName = plugin.getConfig().getString("proxy.this-server-name", "speedrun");
        
        // Wait a moment for everything to stabilize
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            
            for (String playerName : playerNames) {
                // Try to send them back via proxy
                sendPlayerToServer(playerName, thisServerName);
            }
            
            plugin.getLogger().info("Sent connection requests for " + playerNames.size() + " players.");
            plugin.getLogger().info("═══════════════════════════════════");
            plugin.getLogger().info("RESET COMPLETE! Players can now rejoin.");
            plugin.getLogger().info("═══════════════════════════════════");
            
            // Reset complete - clear the flag
            plugin.setResetInProgress(false);
            
        }, 60L); // 3 second delay for world to stabilize
    }
    
    /**
     * Send a player to a specific server via proxy messaging
     * Note: This sends a request to the proxy, but only works if the player is online in the network
     */
    private void sendPlayerToServer(String playerName, String serverName) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);
            
            out.writeUTF("ConnectOther");
            out.writeUTF(playerName);
            out.writeUTF(serverName);
            
            // Need a player to send the message - use first online player or wait for next join
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                Player sender = Bukkit.getOnlinePlayers().iterator().next();
                sender.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            } else {
                plugin.getLogger().warning("No online players to send proxy message. Players must rejoin manually.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send connection request for " + playerName + ": " + e.getMessage());
        }
    }
    
    /**
     * Countdown before world deletion (REMOVED - now sends to lobby instead)
     */
    private void teleportPlayersToSpeedrunWorld(World world, List<Player> players) {
        plugin.getLogger().info("Pre-loading chunks ASYNCHRONOUSLY around spawn...");
        
        // Get base spawn location
        Location spawnLocation = getSpeedrunSpawn(world);
        Chunk spawnChunk = spawnLocation.getChunk();
        
        // Track async chunk loading progress
        final int totalChunks = 7 * 7; // 49 chunks
        final int[] loadedChunks = {0};
        
        // Load 7x7 chunk grid ASYNCHRONOUSLY (won't freeze server!)
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                final int chunkX = spawnChunk.getX() + x;
                final int chunkZ = spawnChunk.getZ() + z;
                
                // Load chunk asynchronously - this doesn't block the server thread!
                world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
                    loadedChunks[0]++;
                    
                    // Log progress every 10 chunks
                    if (loadedChunks[0] % 10 == 0 || loadedChunks[0] == totalChunks) {
                        plugin.getLogger().info("Loaded " + loadedChunks[0] + "/" + totalChunks + " chunks...");
                    }
                    
                    // When all chunks are loaded, proceed to teleportation
                    if (loadedChunks[0] == totalChunks) {
                        onAllChunksLoaded(world, spawnLocation, players);
                    }
                });
            }
        }
    }
    
    /**
     * Called when all chunks are loaded - proceeds with player teleportation
     */
    private void onAllChunksLoaded(World world, Location spawnLocation, List<Player> players) {
        plugin.getLogger().info("All 49 chunks loaded! Finding safe solid ground...");
        
        // Find actual safe spawn location on SOLID GROUND (not water!)
        Location safeSpawn = findSafeSurfaceLocation(spawnLocation);
        plugin.getLogger().info("Safe spawn found at X=" + safeSpawn.getBlockX() + " Y=" + safeSpawn.getBlockY() + " Z=" + safeSpawn.getBlockZ());
        
        // Small delay, then teleport players (1 second)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            
            plugin.getLogger().info("Starting player teleportation...");
            
            // Teleport players one by one with staggered delays
            int successCount = 0;
            for (Player player : players) {
                if (player.isOnline()) {
                    try {
                        // Stagger teleports to prevent server overload
                        final int delay = successCount * 5; // 5 ticks (0.25s) between each player
                        final Location finalSpawn = safeSpawn.clone();
                        
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            player.teleport(finalSpawn);
                            plugin.getLogger().info("✓ Teleported " + player.getName() + " to spawn");
                        }, delay);
                        
                        successCount++;
                        
                    } catch (Exception e) {
                        plugin.getLogger().severe("✗ Failed to teleport " + player.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            plugin.getLogger().info("Scheduled " + successCount + " players for teleportation");
            
            // Broadcast completion message after all players teleported
            final int totalDelay = (successCount * 5) + 20; // Add 1 second after last player
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                
                String completeMsg = plugin.getConfig().getString("reset.messages.reset-complete",
                        "<gold><bold>═══════════════════════════════════\n<green><bold>WORLD RESET COMPLETE!\n<yellow>Teleporting you back...\n<gold><bold>═══════════════════════════════════");
                Bukkit.broadcast(ColorUtil.parse(completeMsg));
                
                plugin.getLogger().info("═══════════════════════════════════");
                plugin.getLogger().info("RESET COMPLETE! All players teleported.");
                plugin.getLogger().info("═══════════════════════════════════");
                
                // Reset complete - clear the flag
                plugin.setResetInProgress(false);
                
            }, totalDelay);
            
        }, 20L); // 1 second delay for safety
    }
    
    /**
     * Find a safe location on the surface starting from the given location
     * Enhanced to avoid water, lava, and steep terrain
     */
    private Location findSafeSurfaceLocation(Location start) {
        World world = start.getWorld();
        int x = start.getBlockX();
        int z = start.getBlockZ();
        
        plugin.getLogger().info("Searching for safe spawn point (checking for water, lava, steep terrain)...");
        
        // Search in a spiral pattern to find safe, flat ground
        int searchRadius = 0;
        int maxRadius = 500; // Search up to 500 blocks away for better spawn
        int stepSize = 8; // Check every 8 blocks for better coverage
        
        Location bestCandidate = null;
        int bestScore = -1;
        
        while (searchRadius <= maxRadius) {
            // Try center first
            Location safe = checkSafeSpawnLocation(world, x, z);
            if (safe != null) {
                int score = evaluateSpawnQuality(world, x, z);
                if (score >= 80) { // Excellent spawn found
                    plugin.getLogger().info("Found excellent spawn at X=" + x + " Y=" + safe.getBlockY() + " Z=" + z + " (score: " + score + ")");
                    return safe;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = safe;
                }
            }
            
            // Spiral search pattern
            searchRadius += stepSize;
            for (int dx = -searchRadius; dx <= searchRadius; dx += stepSize) {
                for (int dz = -searchRadius; dz <= searchRadius; dz += stepSize) {
                    // Only check edge of the current ring (optimization)
                    if (Math.abs(dx) != searchRadius && Math.abs(dz) != searchRadius) {
                        continue;
                    }
                    
                    safe = checkSafeSpawnLocation(world, x + dx, z + dz);
                    if (safe != null) {
                        int score = evaluateSpawnQuality(world, x + dx, z + dz);
                        
                        // If we found an excellent spawn, use it immediately
                        if (score >= 80) {
                            plugin.getLogger().info("Found excellent spawn at X=" + (x+dx) + " Y=" + safe.getBlockY() + " Z=" + (z+dz) + " (score: " + score + ", offset: " + dx + "," + dz + ")");
                            return safe;
                        }
                        
                        // Keep track of best candidate
                        if (score > bestScore) {
                            bestScore = score;
                            bestCandidate = safe;
                        }
                    }
                }
            }
            
            // If we have a decent spawn after searching 200 blocks, use it
            if (searchRadius >= 200 && bestScore >= 60) {
                plugin.getLogger().info("Found good spawn at distance " + searchRadius + " (score: " + bestScore + ")");
                return bestCandidate;
            }
        }
        
        // Use best candidate if we found one
        if (bestCandidate != null) {
            plugin.getLogger().warning("Using best available spawn (score: " + bestScore + ")");
            return bestCandidate;
        }
        
        // Fallback: find any non-water surface
        plugin.getLogger().warning("Could not find ideal spawn within " + maxRadius + " blocks! Using fallback.");
        int surfaceY = world.getHighestBlockYAt(x, z);
        Location fallback = new Location(world, x + 0.5, surfaceY + 1, z + 0.5);
        fallback.setPitch(0);
        fallback.setYaw(0);
        return fallback;
    }
    
    /**
     * Evaluate spawn quality (0-100 score)
     * Higher score = better spawn
     */
    private int evaluateSpawnQuality(World world, int x, int z) {
        int score = 100;
        
        int centerY = world.getHighestBlockYAt(x, z);
        
        // Check 5x5 area around spawn for hazards and terrain flatness
        int waterCount = 0;
        int lavaCount = 0;
        int maxHeightDiff = 0;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int checkX = x + dx;
                int checkZ = z + dz;
                int checkY = world.getHighestBlockYAt(checkX, checkZ);
                
                // Check for water/lava nearby
                org.bukkit.Material material = world.getBlockAt(checkX, checkY, checkZ).getType();
                String name = material.name();
                
                if (material == org.bukkit.Material.WATER || name.contains("WATER")) {
                    waterCount++;
                }
                if (material == org.bukkit.Material.LAVA || name.contains("LAVA")) {
                    lavaCount += 2; // Lava is worse
                }
                
                // Check terrain flatness
                int heightDiff = Math.abs(checkY - centerY);
                maxHeightDiff = Math.max(maxHeightDiff, heightDiff);
            }
        }
        
        // Penalize based on nearby water
        if (waterCount > 0) {
            score -= waterCount * 15; // -15 per water block
        }
        
        // Heavy penalty for lava
        if (lavaCount > 0) {
            score -= lavaCount * 25; // -25 per lava block
        }
        
        // Penalize steep terrain (more than 3 block difference = steep)
        if (maxHeightDiff > 3) {
            score -= (maxHeightDiff - 3) * 10;
        }
        
        // Penalize extreme heights
        if (centerY < 62) {
            score -= (62 - centerY) * 2; // Below sea level
        }
        if (centerY > 120) {
            score -= (centerY - 120) * 2; // Too high (mountains)
        }
        
        return Math.max(0, score);
    }
    
    /**
     * Check if a location is a safe spawn (no nearby hazards)
     */
    private Location checkSafeSpawnLocation(World world, int x, int z) {
        // First do basic solid ground check
        Location basic = checkSolidGround(world, x, z);
        if (basic == null) {
            return null;
        }
        
        int surfaceY = basic.getBlockY() - 1; // Ground level
        
        // Check 3x3 area around spawn for immediate hazards
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int checkX = x + dx;
                int checkZ = z + dz;
                
                // Check surface and one block above
                for (int dy = 0; dy <= 1; dy++) {
                    org.bukkit.Material material = world.getBlockAt(checkX, surfaceY + dy, checkZ).getType();
                    String name = material.name();
                    
                    // Reject if water or lava immediately adjacent
                    if (material == org.bukkit.Material.WATER || 
                        material == org.bukkit.Material.LAVA ||
                        name.contains("WATER") || 
                        name.contains("LAVA")) {
                        return null;
                    }
                }
            }
        }
        
        return basic;
    }
    
    /**
     * Check if a location has solid ground (not water/lava/air/leaves/etc)
     * Returns a safe spawn location if found, null otherwise
     */
    private Location checkSolidGround(World world, int x, int z) {
        // Get the highest block at this location
        int surfaceY = world.getHighestBlockYAt(x, z);
        
        if (surfaceY < 60 || surfaceY > 250) {
            return null; // Too low (void) or too high
        }
        
        Location check = new Location(world, x, surfaceY, z);
        org.bukkit.Material surfaceMaterial = check.getBlock().getType();
        
        // REJECT all unsafe spawn blocks
        if (isUnsafeSpawnBlock(surfaceMaterial)) {
            return null; // Not safe ground
        }
        
        // Make sure the block is actually solid
        if (!surfaceMaterial.isSolid()) {
            return null; // Not solid (flowers, grass, etc.)
        }
        
        // Create spawn location ON TOP of the solid block
        Location spawn = new Location(world, x + 0.5, surfaceY + 1, z + 0.5);
        
        // Verify there's space for the player (2 blocks air)
        if (!spawn.getBlock().getType().isAir() || 
            !spawn.clone().add(0, 1, 0).getBlock().getType().isAir()) {
            
            // Try to find air space above
            for (int y = surfaceY + 1; y < surfaceY + 10; y++) {
                spawn.setY(y);
                if (spawn.getBlock().getType().isAir() && 
                    spawn.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                    break;
                }
            }
        }
        
        spawn.setPitch(0);
        spawn.setYaw(0);
        
        return spawn;
    }
    
    /**
     * Check if a block material is unsafe to spawn on
     */
    private boolean isUnsafeSpawnBlock(org.bukkit.Material material) {
        String name = material.name();
        
        // Liquids
        if (material == org.bukkit.Material.WATER || 
            material == org.bukkit.Material.LAVA ||
            name.contains("WATER") || 
            name.contains("LAVA")) {
            return true;
        }
        
        // Air and non-solid
        if (material == org.bukkit.Material.AIR || 
            material == org.bukkit.Material.CAVE_AIR || 
            material == org.bukkit.Material.VOID_AIR) {
            return true;
        }
        
        // Foliage (leaves, etc.)
        if (name.contains("LEAVES") || name.contains("LEAF")) {
            return true;
        }
        
        // Dangerous plants and damaging blocks
        if (material == org.bukkit.Material.SWEET_BERRY_BUSH || 
            material == org.bukkit.Material.CACTUS ||
            material == org.bukkit.Material.MAGMA_BLOCK ||
            material == org.bukkit.Material.CAMPFIRE ||
            material == org.bukkit.Material.SOUL_CAMPFIRE ||
            material == org.bukkit.Material.WITHER_ROSE) {
            return true;
        }
        
        // Falling blocks (unstable)
        if (material == org.bukkit.Material.SAND || 
            material == org.bukkit.Material.RED_SAND ||
            material == org.bukkit.Material.GRAVEL ||
            material == org.bukkit.Material.ANVIL ||
            material == org.bukkit.Material.CHIPPED_ANVIL ||
            material == org.bukkit.Material.DAMAGED_ANVIL ||
            name.contains("CONCRETE_POWDER")) {
            return true;
        }
        
        // All types of ice (slippery/unsafe - prevents spawning in ice spike ocean)
        if (material == org.bukkit.Material.ICE || 
            material == org.bukkit.Material.PACKED_ICE ||
            material == org.bukkit.Material.BLUE_ICE ||
            material == org.bukkit.Material.FROSTED_ICE ||
            name.contains("ICE")) {
            return true;
        }
        
        // Powder snow (players fall through and freeze)
        if (material == org.bukkit.Material.POWDER_SNOW) {
            return true;
        }
        
        // Dripstone (sharp and damaging)
        if (material == org.bukkit.Material.POINTED_DRIPSTONE ||
            material == org.bukkit.Material.DRIPSTONE_BLOCK ||
            name.contains("DRIPSTONE")) {
            return true;
        }
        
        // Big dripleaf (players fall through when standing on it)
        if (material == org.bukkit.Material.BIG_DRIPLEAF ||
            material == org.bukkit.Material.BIG_DRIPLEAF_STEM ||
            name.contains("DRIPLEAF")) {
            return true;
        }
        
        // Farmland and paths (can change)
        if (material == org.bukkit.Material.FARMLAND || 
            name.contains("PATH")) {
            return true;
        }
        
        // Sculk and other hazards
        if (material == org.bukkit.Material.SCULK_SENSOR ||
            material == org.bukkit.Material.SCULK_SHRIEKER ||
            material == org.bukkit.Material.SCULK_CATALYST ||
            material == org.bukkit.Material.SCULK_VEIN ||
            name.contains("FIRE") ||
            name.contains("SOUL")) {
            return true;
        }
        
        // Honey blocks (slow movement and sticky)
        if (material == org.bukkit.Material.HONEY_BLOCK) {
            return true;
        }
        
        // Slime blocks (bouncy and unsafe)
        if (material == org.bukkit.Material.SLIME_BLOCK) {
            return true;
        }
        
        return false; // Safe block
    }
    
    /**
     * Get waiting room spawn location from config
     */
    private Location getWaitingRoomSpawn(World waitingRoom) {
        double x = plugin.getConfig().getDouble("world.teleport.waiting-room-spawn.x", 0);
        double y = plugin.getConfig().getDouble("world.teleport.waiting-room-spawn.y", 1);
        double z = plugin.getConfig().getDouble("world.teleport.waiting-room-spawn.z", 0);
        return new Location(waitingRoom, x, y, z);
    }
    
    /**
     * Get speedrun spawn location from config
     */
    private Location getSpeedrunSpawn(World speedrunWorld) {
        double x = plugin.getConfig().getDouble("world.teleport.speedrun-spawn.x", 0);
        double y = plugin.getConfig().getDouble("world.teleport.speedrun-spawn.y", 100);
        double z = plugin.getConfig().getDouble("world.teleport.speedrun-spawn.z", 0);
        return new Location(speedrunWorld, x, y, z);
    }
    
    /**
     * Reset a player: clear inventory (except host item), reset HP, level, and achievements
     */
    private void resetPlayer(Player player) {
        // Save host item if player has one
        ItemStack hostItem = null;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isHostControlItem(item)) {
                hostItem = item.clone();
                break;
            }
        }
        
        // Clear inventory
        player.getInventory().clear();
        
        // Restore host item to slot 8 if it was present
        if (hostItem != null) {
            player.getInventory().setItem(8, hostItem);
        }
        
        // Reset health
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        
        // Reset level and experience
        player.setLevel(0);
        player.setExp(0.0f);
        
        // Reset achievements/advancements
        Bukkit.advancementIterator().forEachRemaining(advancement -> {
            if (advancement != null) {
                advancement.getCriteria().forEach(criteria -> {
                    if (player.getAdvancementProgress(advancement).getAwardedCriteria().contains(criteria)) {
                        player.getAdvancementProgress(advancement).revokeCriteria(criteria);
                    }
                });
            }
        });
        
        plugin.getLogger().info("Reset player: " + player.getName());
    }
    
    /**
     * Check if an item is the host control item
     */
    private boolean isHostControlItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }
        
        if (!item.hasItemMeta()) {
            return false;
        }
        
        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) {
            return false;
        }
        
        String name = PlainTextComponentSerializer.plainText().serialize(displayName);
        return name.equals("Host Controls");
    }
    
    /**
     * OLD METHOD - Save the seed to a file for use after server restart (LEGACY - NOT USED IN HOLODECK)
     */
    @Deprecated
    private void saveSeedForRestart(long seed) {
        try {
            File seedFile = new File(plugin.getDataFolder(), "pending_seed.txt");
            Files.write(seedFile.toPath(), String.valueOf(seed).getBytes());
            plugin.getLogger().info("Saved pending seed to file: " + seed);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save pending seed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Recursively delete a world folder using NIO for better error handling
     */
    private void deleteWorldFolder(File worldFolder) {
        if (!worldFolder.exists()) {
            plugin.getLogger().info("World folder doesn't exist: " + worldFolder.getName());
            return;
        }
        
        plugin.getLogger().info("Deleting world folder: " + worldFolder.getAbsolutePath());
        
        try {
            Files.walkFileTree(worldFolder.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            plugin.getLogger().info("Successfully deleted: " + worldFolder.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to delete world folder: " + worldFolder.getName());
            e.printStackTrace();
        }
    }
}
