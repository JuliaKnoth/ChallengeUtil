ChallengeUtil - Advanced Speedrunning & Challenge Plugin

**Version:** 1.3.19  
**Platform:** Minecraft 1.21.11 (Paper/Spigot)  
**Dependencies:** PlaceholderAPI (optional, for team chat prefixes)

---

**Overview**

ChallengeUtil is a comprehensive Minecraft plugin designed for speedrunning servers and challenge gamemodes. It features **instant world resets** (3-5 seconds, no server restart!), a built-in timer system, multiple game modes, and various challenge mechanics to create unique and engaging gameplay experiences.

Perfect for **Velocity/BungeeCord proxy networks** - players stay connected during world regeneration!

---

**Key Features**

Instant World Reset System
- **3-5 second world regeneration** without server restart
- **Holodeck Strategy**: Players are smoothly transferred to a waiting room while the speedrun world regenerates
- **Random seed generation** for each reset
- **Proxy-friendly**: Full Velocity/BungeeCord support with auto-reconnect
- **No disconnections**: Players remain connected throughout the entire reset process
- **Async world deletion**: Zero lag during world regeneration

Advanced Timer System
- Real-time timer display above the hotbar (action bar)
- Format: `HH:MM:SS` with visual indicators
- Color-coded states:
  - Light Purple when running
  - Red with pause symbol (⏸) when paused
- Start, pause, reset, and resume functionality
- Persistent through pause/resume cycles
- PlaceholderAPI support for external plugins

Multiple Game Modes

**Manhunt Mode**
Classic speedrunner vs hunters gameplay:
- **Runner Team**: 1+ speedrunners trying to defeat the Ender Dragon
- **Hunter Team**: 1+ hunters trying to stop the runners
- **Tracking Compass**: Hunters receive a compass that tracks the nearest runner
- **Team Protection**: Friendly fire protection within teams (configurable)
- **Spectator Mode**: Spectators can watch without interfering
- **Dynamic Spawning**: Hunters spawn 10 minutes after `/start` for balanced gameplay

**Team Race Mode**
Competitive team-based speedrunning:
- **2-10 Teams**: Customizable team count with German names and colors
- **Available Teams**: Rot, Blau, Grün, Gelb, Lila, Aqua, Weiß, Orange, Pink, Grau
- **Team Tracking Compass**: Each team tracks the nearest enemy team member
- **Color-coded UI**: Team names appear in their respective colors
- **Win Condition**: First team to defeat the Ender Dragon wins
- **Scoreboard Integration**: Real-time team tracking and status

Challenge Modes

**Chunk Items Challenge**
- Receive random items when entering new chunks
- **Player-specific**: Different players get different items from the same chunk
- **Persistent**: Each chunk gives the same item to a player on re-entry
- **Manhunt Integration**: Hunters receive items only after 10 minutes
- **Configurable Exclusions**: Customize which items can drop
- **Default Exclusions**: Overpowered items (Elytra, Netherite gear, Totems) excluded by default

**Friendly Fire = OP Items Challenge**
Damage teammates, get rewards:
- **Damage Sync**: Both attacker and victim take the same damage
- **No Natural Regen**: Natural health regeneration disabled
- **Health-Based Rewards**: Lower health = better items
  - **80-100% HP**: Basic items (Golden Apples, Arrows, Iron)
  - **50-79% HP**: Medium items (Diamonds, Enchanted Iron gear)
  - **25-49% HP**: Strong items (Diamond gear with Sharpness V, Totems)
  - **1-24% HP**: Extremely OP items (Netherite, fully enchanted diamond gear, multiple totems)
- **Team Integration**: Works with both Manhunt and Team Race modes

Advanced Configuration

**Dual-World System**
- **Waiting Room**: Lobby world where players wait during resets (peaceful, frozen time)
- **Speedrun World**: Main world that gets regenerated with new seeds
- **Automatic Gamerule Management**: Different gamerules for each world
- **Smart Teleportation**: Surface-detection spawn system

**World Generation Settings**
- World types: NORMAL, FLAT, LARGE_BIOMES, AMPLIFIED
- Structure generation toggle
- Configurable difficulty
- Random or fixed seed generation
- Custom spawn coordinates

**Proxy Integration**
- Full BungeeCord/Velocity support
- Auto-reconnect during world resets
- Configurable server names
- Companion proxy plugin included

---

**Commands**

Main Commands

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/start` | - | Start or resume the timer | `challenge.start` |
| `/pause` | - | Pause the timer | `challenge.pause` |
| `/reset` | - | Reset timer only (no world reset) | `challenge.reset` |
| `/fullreset` | `/resetworld`, `/worldreset` | Holodeck reset - regenerate world instantly (3-5 sec) | `challenge.fullreset` |
| `/join` | `/play`, `/go` | Join the speedrun world from waiting room | `challenge.join` |
| `/settings` | `/config`, `/cfg` | Open settings GUI (waiting room only) | `challenge.settings` |
| `/team <name>` | - | Join a team (Manhunt or Team Race) | `challenge.team` |

Team Command Examples
**Manhunt Mode:**
- `/team runner` - Join the runner team
- `/team hunter` - Join the hunter team
- `/team spectator` - Join spectators

**Team Race Mode:**
- `/team` - List all available teams and member counts
- `/team Rot` - Join Team Rot (Red)
- `/team Blau` - Join Team Blau (Blue)
- `/team Grün` - Join Team Grün (Green)
- etc.

---

**Permissions**

Main Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `challenge.host` | **Master permission** - grants all admin commands | OP |
| `challenge.start` | Start/resume the timer | OP |
| `challenge.pause` | Pause the timer | OP |
| `challenge.reset` | Reset timer only | OP |
| `challenge.fullreset` | Execute Holodeck reset (world regeneration) | OP |
| `challenge.settings` | Open settings GUI | OP |
| `challenge.join` | Join speedrun world from waiting room | True (all players) |
| `challenge.team` | Join teams in Manhunt/Team Race | True (all players) |
| `challenge.*` | Grant all permissions | OP |

Permission Hierarchy
The `challenge.host` permission includes:
- `challenge.start`
- `challenge.pause`
- `challenge.reset`
- `challenge.fullreset`
- `challenge.settings`

---

Host Control Features

Players with the `challenge.host` permission receive special tools:

**Host Control Item**
- **Enchanted Nether Star** automatically given on join
- **Right-click** to open Host Control GUI
- Quick access to all host commands without typing

**Host Control GUI**
Interactive menu with three options:
1. **Start Challenge** (Green Wool) - Executes `/start`
2. **Settings** (Comparator) - Opens `/settings` GUI
3. **⚠ FULL RESET ⚠** (Barrier) - Executes `/fullreset` with warning

---

Configuration

The plugin features extensive configuration through `config.yml`:

Proxy Settings
```yaml
proxy:
  lobby-server: lobby1          # Lobby server name in proxy config
  this-server-name: challenge1  # This server's name in proxy config
```

World Settings
```yaml
world:
  waiting-room: waiting_room       # Fallback world (lobby)
  speedrun-world: speedrun_world   # World that gets regenerated
  
  generation:
    type: NORMAL                   # NORMAL, FLAT, LARGE_BIOMES, AMPLIFIED
    generate-structures: true
    random-seed: true
  
  difficulty: NORMAL               # PEACEFUL, EASY, NORMAL, HARD
  
  teleport:
    waiting-room-spawn:
      x: 0
      y: 65
      z: 0
    speedrun-spawn:
      x: 0
      y: 100  # Auto-finds surface
      z: 0
```

Gamerules
Separate gamerule configurations for waiting room (peaceful, frozen time) and speedrun world (normal gameplay).

Challenge Settings
```yaml
challenge:
  allow-respawn: true  # false = hardcore mode (spectator on death)
  
  chunk_items:
    excluded:  # Items to exclude from random rewards
      # - DIAMOND
      # - EMERALD
  
  friendly_fire_item:
    enabled: false  # Toggle via GUI
```

---

**PlaceholderAPI Integration** (optional)

Available Placeholders

| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%ch_prefix%` | Team prefix with color | `[RUNNER]`, `[HUNTER]`, `[Red]` |
| `%ch_suffix%` | Team suffix (reserved) | - |
| `%ch_team%` | Raw team name | `runner`, `hunter`, `Rot` |
| `%ch_team_color%` | Legacy team color code | `§d`, `§6`, `§c` |

Usage Example
**EssentialsX Chat:**
```yaml
format: '{ch_prefix}{DISPLAYNAME}{ch_suffix}§r: {MESSAGE}'
```

---

**Game Modes Comparison**

Mode Compatibility Matrix

| Feature | Manhunt | Team Race | Solo |
|---------|---------|-----------|------|
| Timer System | ✅ | ✅ | ✅ |
| Instant Reset | ✅ | ✅ | ✅ |
| Team System | ✅ | ✅ | ❌ |
| Tracking Compass | ✅ (Hunters only) | ✅ (All teams) | ❌ |
| Chunk Items Challenge | ✅ | ✅ | ✅ |
| Friendly Fire Challenge | ✅ | ✅ | ❌ |
| Multiple Teams | ❌ (2 teams) | ✅ (2-10 teams) | ❌ |
| Can Run Simultaneously | ❌ | ❌ | ✅ |

**Note:** Manhunt and Team Race cannot be enabled simultaneously.

---

**Technical Details**

Performance
- **Reset Time**: 3-5 seconds typical
- **World Deletion**: Async (non-blocking)
- **World Creation**: Sync (main thread, optimized)
- **Timer Updates**: Every second (20 ticks)
- **Compass Updates**: Every second (20 ticks)

---

**Installation**

Basic Installation
1. Download `ChallengeUtil.jar`
2. Place in your server's `plugins` folder
3. Configure `server.properties`:
   ```properties
   level-name=waiting_room
   ```
4. Restart your server
5. Configure `plugins/ChallengeUtil/config.yml`
6. Reload or restart

Optional: PlaceholderAPI
1. Install PlaceholderAPI from SpigotMC
2. Restart server
3. Placeholders automatically register
4. Use in chat plugins (EssentialsX, ChatControl, etc.)

---

**Usage Examples**

Basic Speedrun Setup
1. Server starts with players in waiting room
2. Admin: `/settings` - Configure challenge settings
3. Players: `/join` - Enter speedrun world
4. Admin: `/start` - Begin timer
5. Players: Complete speedrun
6. Admin: `/fullreset` - Instant 3-5 second reset for next run

Manhunt Setup
1. Enable Manhunt mode in `/settings > Challenges`
2. Players join teams:
   - `/team runner` (speedrunners)
   - `/team hunter` (hunters)
3. Admin: `/start`
4. Runners get 10-minute head start
5. Hunters spawn and receive tracking compass
6. First team to defeat Ender Dragon wins

Team Race Setup
1. Enable Team Race in `/settings > Challenges`
2. Players: `/team` to see available teams
3. Players join teams: `/team Rot`, `/team Blau`, etc.
4. Admin: `/start`
5. All teams receive tracking compasses
6. Race to defeat the Ender Dragon
7. First team wins!

---

**Troubleshooting**

Common Issues

**"Waiting room not found"**
- Verify `level-name=waiting_room` in server.properties
- Ensure waiting room world folder exists

**Slow resets (>10 seconds)**
- Use Paper instead of Spigot
- Use SSD instead of HDD
- Reduce world size/pregenerate less
- Check server TPS

**Players fall into void**
- Increase `speedrun-spawn.y` to 120+ in config.yml
- Plugin auto-finds surface, but high Y helps

**Same seed every reset**
- Set `random-seed: false` in config.yml

**Compass not working**
- Ensure players are in the same dimension
- Check that target players are online
- Verify team assignments with `/team`

**Chat prefixes not showing**
- Install PlaceholderAPI
- Check if expansion is registered: `/papi list`
- Reload PlaceholderAPI: `/papi reload`
- Verify chat plugin configuration

---

**Perfect For**

- **Speedrunning Servers**: Instant resets for practice or races
- **Challenge Servers**: Unique gameplay mechanics
- **Manhunt Servers**: Classic hunter vs speedrunner
- **Team Competition**: Multi-team races
- **Practice Servers**: Quick reset for skill improvement
- **Content Creation**: Streamers and YouTubers
- **Proxy Networks**: Multiple parallel speedrun instances

---

**License**

MIT License - Free to use for any server!

---

**Why Choose ChallengeUtil?
**
**Instant Resets** - 3-5 seconds, no server restart  
**Unique Challenges** - Chunk Items, Friendly Fire  
**Proxy Optimized** - Perfect for networks  
**Highly Configurable** - Customize everything  
**PlaceholderAPI** - Full integration  
**Performance Focused** - Optimized and tested  
**User-Friendly** - GUI-based configuration  
**Active Development** - Regular updates  

---

**Built with ❤️ for the Minecraft speedrunning community**

**Download now and revolutionize your speedrunning server!**
