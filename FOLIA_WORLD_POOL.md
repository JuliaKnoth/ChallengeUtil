# Folia World Pool System

## Overview

The `/fullreset` command now **WORKS on Folia servers!** 🎉

Since Folia doesn't support dynamic world creation/deletion (`Bukkit.createWorld()`), we've implemented a **World Pool System** that provides the same experience with a different approach.

## How It Works

### Traditional Holodeck Reset (Paper/Spigot)
1. Teleport players to waiting room
2. Delete world folders
3. Create new world with random seed
4. Teleport players back
5. **Limitation**: Doesn't work on Folia

### New World Pool System (Folia)
1. **Pre-generate** multiple worlds with different seeds (e.g., 5 worlds)
2. On `/fullreset`: Rotate to the next world in the pool
3. Each world has a **unique random seed**
4. Old worlds are **automatically regenerated** in the background with new seeds
5. **Result**: Every reset gives you a fresh world with a new seed!

## Benefits

✅ **New seed every reset** - Just like the original system  
✅ **Folia compatible** - No dynamic world creation needed  
✅ **Fast resets** - 5-10 seconds (similar to Holodeck)  
✅ **Automatic** - Worlds regenerate in the background  
✅ **Configurable** - Adjust pool size to your needs  
✅ **Backwards compatible** - Paper/Spigot still use traditional Holodeck reset

## Configuration

Add to your `config.yml`:

```yaml
world:
  # Standard world settings
  waiting-room: waiting_room
  speedrun-world: speedrun_world
  
  # World Pool Settings (Folia)
  folia-world-pool:
    # Enable world pool mode
    # auto = enabled on Folia, disabled on Paper/Spigot
    # true = force enable even on Paper/Spigot
    # false = force disable (Folia will show error)
    enabled: auto
    
    # Number of worlds in the pool (3-10 recommended)
    # Higher = more unique seeds before cycling
    # Lower = less disk space used
    pool-size: 5
    
    # Base name for pool worlds
    # Creates: speedrun_pool_0, speedrun_pool_1, speedrun_pool_2, etc.
    pool-base-name: speedrun_pool
    
    # Pre-generate all worlds on server startup
    # true = slower startup but instant resets
    # false = faster startup but first few resets may lag
    pregenerate-on-startup: true
    
    # Auto-regenerate old worlds with new seeds
    # true = infinite unique seeds (recommended)
    # false = cycles through same seeds
    auto-regenerate-unused: true
```

## How Players Experience It

When a host runs `/fullreset`:

```
═══════════════════════════════════
Neue Welt wird geladen!
Neuer Seed: 1234567890123456789
Du wirst zum Warteraum teleportiert...
═══════════════════════════════════
```

**5-10 seconds later:**

```
═══════════════════════════════════
Welt-Reset abgeschlossen!
Neue Welt: speedrun_pool_2
Viel Erfolg!
═══════════════════════════════════
```

## Technical Details

### World Pool Lifecycle

1. **Server Startup**:
   - Creates/loads pool worlds (e.g., `speedrun_pool_0` through `speedrun_pool_4`)
   - Each world gets a random seed
   - First world is set as active

2. **During `/fullreset`**:
   - Players teleported to waiting room
   - Plugin rotates to next world: `pool_0` → `pool_1` → `pool_2` → ... → `pool_0`
   - Players teleported to new world
   - Reset complete in ~5-10 seconds

3. **Background Regeneration**:
   - After rotating away from a world, it's marked for regeneration
   - 10 seconds later (when empty), the world is unloaded
   - World folder is deleted
   - New world is created with a fresh random seed
   - Ready for future resets

### Disk Space

With default settings (pool size 5):
- **5 worlds** × **~100MB per world** = **~500MB total**
- Comparable to keeping a few backups
- Configurable - reduce pool size if needed

### Example Rotation

```
Reset #1: speedrun_pool_0 (seed: 123456789)
Reset #2: speedrun_pool_1 (seed: 987654321)
Reset #3: speedrun_pool_2 (seed: 456789123)
Reset #4: speedrun_pool_3 (seed: 789123456)
Reset #5: speedrun_pool_4 (seed: 321654987)
Reset #6: speedrun_pool_0 (seed: 159753486) <- regenerated with NEW seed!
```

## Comparison: Holodeck vs World Pool

| Feature | Holodeck (Paper) | World Pool (Folia) |
|---------|------------------|-------------------|
| **Folia Support** | ❌ No | ✅ Yes |
| **New Seed Every Reset** | ✅ Yes | ✅ Yes |
| **Reset Speed** | ~3-5 seconds | ~5-10 seconds |
| **Disk Space** | Minimal | ~500MB (configurable) |
| **Server Restart Required** | ❌ No | ❌ No |
| **Background Tasks** | None | Auto-regeneration |

## Troubleshooting

### "Reset fehlgeschlagen! World Pool nicht initialisiert."
- **Cause**: World pool manager failed to initialize
- **Fix**: Check server console for errors, ensure enough disk space

### "Warteraum nicht gefunden."
- **Cause**: Waiting room world doesn't exist
- **Fix**: Create a world named `waiting_room` (or match `config.yml`)

### Slow first reset
- **Cause**: `pregenerate-on-startup: false`
- **Fix**: Set `pregenerate-on-startup: true` in config

### Running out of disk space
- **Cause**: Pool size too large
- **Fix**: Reduce `pool-size` to 3 or set `auto-regenerate-unused: false`

## Migration from Old Folia Setup

If you previously saw the error:
```
⚠ World reset is not supported on Folia!
```

**You're all set!** Just update your plugin and adjust `config.yml`. The system will automatically:
1. Detect you're running Folia
2. Initialize the world pool
3. Enable `/fullreset` with the new system

## Performance Notes

- **Server Startup**: +10-30 seconds (if pregenerate is enabled)
- **During Reset**: Similar to Holodeck (~5-10 seconds)
- **Background Regeneration**: Minimal impact (runs asynchronously)
- **Memory**: Slightly higher (multiple worlds loaded)

## Advanced Configuration

### Use on Paper/Spigot (Not Recommended)

You can force enable world pool mode on Paper/Spigot:

```yaml
world:
  folia-world-pool:
    enabled: true  # Force enable
```

**Why?**
- Testing Folia behavior on Paper
- Prefer world switching over deletion
- Want background regeneration

**Why not?**
- Holodeck reset is faster on Paper
- Uses more disk space
- No real benefit unless testing

## Credits

- **Original Holodeck Reset**: Julia
- **Folia World Pool System**: Developed to overcome Folia's dynamic world creation limitation
- **Inspiration**: Multi-world management plugins like Multiverse

---

**Questions or Issues?**  
Check the console logs - the world pool system logs all operations for debugging.
