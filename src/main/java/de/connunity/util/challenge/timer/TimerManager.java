package de.connunity.util.challenge.timer;

import de.connunity.util.challenge.data.DataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class TimerManager {
    
    private final Plugin plugin;
    private final DataManager dataManager;
    private BukkitTask timerTask;
    private BukkitTask blinkTask;
    private BukkitTask saveTask;
    
    private long totalSeconds = 0;
    private boolean running = false;
    private boolean paused = false;
    private boolean blinkState = false; // For blinking effect when paused
    
    // Real-world time tracking (not game ticks)
    private long lastUpdateTime = 0;
    
    public TimerManager(Plugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        
        // Load saved state on initialization
        loadSavedState();
    }
    
    /**
     * Load timer state from disk (called on server start)
     */
    private void loadSavedState() {
        totalSeconds = dataManager.getTimerSeconds();
        boolean wasRunning = dataManager.wasTimerRunning();
        boolean wasPaused = dataManager.wasTimerPaused();
        
        if (wasRunning) {
            if (wasPaused) {
                running = true;
                paused = true;
                startBlinking();
                updateActionBar();
                plugin.getLogger().info("Restored paused timer: " + formatTime(totalSeconds));
            } else {
                // Timer was running when server stopped - resume it
                start();
                plugin.getLogger().info("Restored running timer: " + formatTime(totalSeconds));
            }
        } else if (totalSeconds > 0) {
            // Timer was stopped but had time - just display it
            updateActionBar();
            plugin.getLogger().info("Restored stopped timer: " + formatTime(totalSeconds));
        }
    }
    
    /**
     * Start auto-save task to persist timer state every 10 seconds (real-world time)
     */
    private void startAutoSave() {
        if (saveTask != null) {
            return; // Already running
        }
        
        // Use async task for file I/O to not block main thread
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            dataManager.saveTimerState(totalSeconds, running, paused);
        }, 200L, 200L); // Check every 10 seconds (200 ticks) - but uses real time
    }
    
    /**
     * Stop auto-save task
     */
    private void stopAutoSave() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }
    
    /**
     * Start the timer from current time (uses real-world milliseconds, not game ticks)
     */
    public void start() {
        if (running && !paused) {
            return; // Already running
        }
        
        running = true;
        paused = false;
        stopBlinking();
        startAutoSave(); // Start auto-saving
        
        // Track real-world time instead of relying on ticks
        lastUpdateTime = System.currentTimeMillis();
        
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!paused) {
                long currentTime = System.currentTimeMillis();
                long elapsedMillis = currentTime - lastUpdateTime;
                
                // Add elapsed seconds (allows for lag compensation)
                long elapsedSeconds = elapsedMillis / 1000;
                if (elapsedSeconds >= 1) {
                    totalSeconds += elapsedSeconds;
                    lastUpdateTime = currentTime - (elapsedMillis % 1000); // Keep remainder for accuracy
                    updateActionBar();
                }
            }
        }, 0L, 10L); // Check every 0.5 seconds, but uses real milliseconds
    }
    
    /**
     * Pause the timer
     */
    public void pause() {
        if (!running || paused) {
            return;
        }
        paused = true;
        startBlinking();
    }
    
    /**
     * Resume the timer from pause
     */
    public void resume() {
        if (!running || !paused) {
            return;
        }
        paused = false;
        
        // CRITICAL: Reset lastUpdateTime to prevent skipping paused time
        lastUpdateTime = System.currentTimeMillis();
        
        stopBlinking();
        updateActionBar();
    }
    
    /**
     * Stop the timer completely
     */
    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        stopBlinking();
        stopAutoSave(); // Stop auto-saving
        dataManager.saveTimerState(totalSeconds, false, false); // Final save
        running = false;
        paused = false;
    }
    
    /**
     * Reset the timer to 0 and stop it
     */
    public void reset() {
        stop();
        totalSeconds = 0;
        dataManager.clearTimerData(); // Clear saved data
        clearActionBar();
    }
    
    /**
     * Restart the timer (reset and start)
     */
    public void restart() {
        stop();
        totalSeconds = 0;
        start();
    }
    
    /**
     * Start blinking effect when paused (real-world timing)
     */
    private void startBlinking() {
        if (blinkTask != null) {
            return; // Already blinking
        }
        
        blinkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            blinkState = !blinkState;
            updateActionBar();
        }, 0L, 10L); // Blink every 0.5 seconds - uses real time
    }
    
    /**
     * Stop blinking effect
     */
    private void stopBlinking() {
        if (blinkTask != null) {
            blinkTask.cancel();
            blinkTask = null;
        }
        blinkState = false;
    }
    
    /**
     * Update the action bar for all online players
     */
    private void updateActionBar() {
        String timeString = formatTime(totalSeconds);
        
        Component message;
        
        if (paused) {
            // Blinking red when paused
            if (blinkState) {
                message = Component.text()
                        .append(Component.text("⏸ ", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text(timeString, NamedTextColor.RED, TextDecoration.BOLD))                        
                        .build();
            } else {
                // Invisible/empty during blink off state for dramatic effect
                message = Component.text()
                        .append(Component.text("⏸ ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                        .append(Component.text(timeString, NamedTextColor.DARK_RED, TextDecoration.BOLD))                        
                        .build();
            }
        } else {
            // Normal green timer when running
            message = Component.text()
                    .append(Component.text(timeString, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .build();
        }
        
        Bukkit.getOnlinePlayers().forEach(player -> player.sendActionBar(message));
    }
    
    /**
     * Clear the action bar for all players
     */
    private void clearActionBar() {
        Component empty = Component.empty();
        Bukkit.getOnlinePlayers().forEach(player -> player.sendActionBar(empty));
    }
    
    /**
     * Format seconds into HH:MM:SS
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    // Getters
    public boolean isRunning() {
        return running;
    }
    
    public boolean isPaused() {
        return paused;
    }
    
    public long getTotalSeconds() {
        return totalSeconds;
    }
}
