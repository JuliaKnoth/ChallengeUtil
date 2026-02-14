package de.connunity.util.challenge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.TimeUnit;

/**
 * Utility class to handle scheduling in a Folia-compatible way.
 * This class provides methods that work on both Folia and Paper/Spigot servers.
 */
public class FoliaSchedulerUtil {

    private static final boolean IS_FOLIA = checkFolia();

    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Runs a task on the global region scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     * Use this for tasks that don't interact with specific entities or locations.
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a delayed task on the global region scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Runs a repeating task on the global region scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static Object runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks);
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * Runs an async task on the async scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static void runTaskAsynchronously(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Runs a delayed async task on the async scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static void runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            long delayMs = delayTicks * 50; // Convert ticks to milliseconds
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMs, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    /**
     * Runs a repeating async task on the async scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static Object runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            long delayMs = delayTicks * 50;
            long periodMs = periodTicks * 50;
            return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * Runs a task for a specific entity on the entity scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static void runAtEntity(Plugin plugin, Entity entity, Runnable task) {
        if (IS_FOLIA) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a delayed task for a specific entity on the entity scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static void runAtEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Runs a task at a specific location on the region scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a delayed task at a specific location on the region scheduler (Folia) or Bukkit scheduler (Paper/Spigot).
     */
    public static void runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Cancels a task. Works with both Folia and Bukkit task types.
     */
    public static void cancelTask(Object task) {
        if (task == null) return;
        
        if (IS_FOLIA) {
            if (task instanceof ScheduledTask) {
                ((ScheduledTask) task).cancel();
            }
        } else {
            if (task instanceof org.bukkit.scheduler.BukkitTask) {
                ((org.bukkit.scheduler.BukkitTask) task).cancel();
            }
        }
    }

    /**
     * Returns whether the server is running Folia.
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Teleports a player to a location in a Folia-compatible way.
     * On Folia, uses teleportAsync(). On Paper/Spigot, uses synchronous teleport.
     * 
     * @param player The player to teleport
     * @param location The destination location
     */
    public static void teleport(org.bukkit.entity.Player player, org.bukkit.Location location) {
        if (IS_FOLIA) {
            // Folia requires async teleport
            player.teleportAsync(location);
        } else {
            // Paper/Spigot can use sync teleport
            player.teleport(location);
        }
    }
}
