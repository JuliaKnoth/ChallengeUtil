# ChallengeUtil - Folia Migration

## Overview
This plugin has been successfully migrated to be **Folia-compatible** while maintaining backward compatibility with Paper and Spigot servers.

## What is Folia?
Folia is a multi-threaded Minecraft server software developed by PaperMC that divides the world into regions, allowing multiple regions to tick in parallel. This significantly improves performance on servers with many players or large worlds.

## Changes Made

### 1. Dependency Updates
- **Updated `pom.xml`**: Replaced Paper API with Folia API
- **Repository**: Uses PaperMC repository for Folia artifacts
- **API Version**: `1.20.4-R0.1-SNAPSHOT`

### 2. FoliaSchedulerUtil Class
Created a new utility class that provides a compatibility layer between Folia and Paper/Spigot:
- **Location**: `de.connunity.util.challenge.FoliaSchedulerUtil`
- **Purpose**: Automatically detects if running on Folia and uses appropriate scheduler
- **Features**:
  - Global region scheduler support (for tasks not tied to specific locations/entities)
  - Entity scheduler support (for entity-specific tasks)
  - Location/region scheduler support (for location-specific tasks)
  - Async scheduler support
  - Automatic fallback to Bukkit scheduler on Paper/Spigot

### 3. Scheduler Replacements
All scheduler calls have been updated throughout the plugin:

#### Files Updated:
- ✅ **TimerManager.java** - Timer updates, save tasks, blink effects
- ✅ **ManhuntManager.java** - Blindness tasks, compass tracking, glass placement
- ✅ **TeamRaceManager.java** - Compass update tasks
- ✅ **All Listener Files** (6 files):
  - WaitingRoomListener.java
  - TimedRandomItemListener.java
  - PlayerRespawnListener.java
  - PlayerJoinListener.java
  - PlayerDeathListener.java
  - FriendlyFireItemListener.java
- ✅ **All Command Files** (2 files):
  - StartCommand.java
  - FullResetCommand.java
- ✅ **ChallengeUtil.java** - Main plugin class startup tasks
- ✅ **VersionChecker.java** - Update check tasks

#### Migration Patterns:
```java
// OLD (Paper/Spigot)
Bukkit.getScheduler().runTask(plugin, () -> { ... });
Bukkit.getScheduler().runTaskLater(plugin, () -> { ... }, delay);
Bukkit.getScheduler().runTaskTimer(plugin, () -> { ... }, delay, period);
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { ... });

// NEW (Folia-compatible)
FoliaSchedulerUtil.runTask(plugin, () -> { ... });
FoliaSchedulerUtil.runTaskLater(plugin, () -> { ... }, delay);
FoliaSchedulerUtil.runTaskTimer(plugin, () -> { ... }, delay, period);
FoliaSchedulerUtil.runTaskAsynchronously(plugin, () -> { ... });
```

### 4. Task Management
- Changed task types from `BukkitTask` to `Object` for compatibility
- Updated task cancellation to use `FoliaSchedulerUtil.cancelTask()`

### 5. Plugin Metadata
- Updated `plugin.yml` with `folia-supported: true` flag

## Performance Benefits on Folia

### Multi-threaded Execution
- Timer updates run on global region scheduler
- Entity-specific operations (player tracking, compass updates) can run in parallel across regions
- Reduced tick lag during high player counts

### Optimized Scheduling
- Real-time tracking uses `System.currentTimeMillis()` instead of tick counts
- Independent of server TPS (ticks per second) fluctuations
- More accurate timing even under server load

### Thread-safe Operations
- All world/entity modifications use appropriate region schedulers
- No cross-region race conditions
- Proper synchronization for shared data access

## Compatibility

### Supported Server Types
- ✅ **Folia** (1.20.4+) - Full multi-threaded support
- ✅ **Paper** (1.20.4+) - Falls back to Paper scheduler
- ✅ **Spigot** (1.20.4+) - Falls back to Bukkit scheduler

### Runtime Detection
The plugin automatically detects which server software it's running on:
```java
// Checks for Folia-specific classes
Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
```

## Building

```bash
mvn clean package
```

The compiled JAR will be in `target/ChallengeUtil.jar`

## Installation

1. Download/build the plugin JAR
2. Place in your server's `plugins/` folder
3. Restart the server
4. Plugin works on both Folia and Paper/Spigot servers

## Testing

### On Folia
- Verify multi-region performance improvements
- Test with high player counts across multiple regions
- Monitor thread usage in timings/spark

### On Paper/Spigot
- Verify backward compatibility
- Ensure all features work as expected
- Check for performance regressions

## Technical Notes

### Thread Safety Considerations
- Timer state is managed with proper synchronization
- Compass tracking uses concurrent data structures
- World modifications are scheduled on appropriate regions

### Region Scheduler Usage
- **Global tasks**: Timer updates, scheduled saves
- **Entity tasks**: Player-specific operations (compass updates, teleportation)
- **Location tasks**: Block placement/removal (glass blocks in manhunt)

### Migration Best Practices Applied
1. ✅ Minimal code changes - used wrapper utility
2. ✅ Backward compatibility maintained
3. ✅ No breaking changes to plugin functionality
4. ✅ Performance optimizations preserved
5. ✅ Real-time tracking independent of server ticks

## Known Limitations

### Folia-specific Constraints
- **✅ World Reset NOW SUPPORTED on Folia**: The `/fullreset` command now works on Folia using a world pool system!
  - Instead of dynamic world creation, we use a rotating pool of pre-generated worlds
  - Each world in the pool has a different seed, giving you a new seed every reset
  - Pool size is configurable (default: 5 worlds)
  - Worlds auto-regenerate in the background when not in use
  - **This provides the same experience as the original Holodeck reset, but Folia-compatible!**
- World modifications must be done on the region owning that location
- Entity access restricted to owning region
- Some cross-world operations may have slight delays

### World Pool System (Folia)
On Folia servers (or when manually enabled), the plugin uses a world pool strategy:
1. **Pre-generated Worlds**: Creates multiple worlds on startup (e.g., `speedrun_pool_0`, `speedrun_pool_1`, etc.)
2. **Rotation**: Each `/fullreset` rotates to the next world with a different seed
3. **Auto-Regeneration**: Old worlds are regenerated in the background with new random seeds
4. **Configuration**: Fully customizable in `config.yml` under `world.folia-world-pool`

### Configuration Options
```yaml
world:
  folia-world-pool:
    enabled: auto  # auto, true, or false
    pool-size: 5   # Number of worlds in rotation
    pool-base-name: speedrun_pool
    pregenerate-on-startup: true
    auto-regenerate-unused: true
```

### Compatibility Notes
- Requires Java 17+
- Minimum Minecraft version: 1.20.4
- Some PlaceholderAPI features may have limited support on Folia
- `/fullreset` command is disabled on Folia (displays error message to players)

## Future Improvements

### Potential Optimizations
- Further region-specific optimizations for manhunt tracking
- Enhanced parallel processing for multiple teams
- Region-aware world generation and reset

### Monitoring
- Add metrics for Folia-specific performance
- Track region thread distribution
- Monitor cross-region operation latency

## Support

For issues related to Folia compatibility:
1. Verify you're running a supported Folia version
2. Check server console for any scheduler-related errors
3. Compare behavior on Paper vs Folia
4. Report issues with server version and Folia build number

## Credits

- Original Plugin: Julia
- Folia: PaperMC Team

## License

Same as original ChallengeUtil plugin license
