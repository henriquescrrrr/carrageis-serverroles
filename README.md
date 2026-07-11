# ServerRoles

A modern, lightweight role-based permission management plugin for **Paper/Purpur 1.21.11** servers (Java 21).

ServerRoles lets you define roles with prefixes, hex colors, permissions, inheritance, and operator status — all managed in-game via a single `/role` command. It includes temporary (timed) role assignments, full PlaceholderAPI support, and multiple storage backends.

---

## Features

- **Role system** — create, delete, and edit roles with display names, hex-colored prefixes, priorities, and permission inheritance.
- **Permission management** — per-role permission nodes with inheritance and negation (`-permission.node`).
- **Operator control** — roles can optionally grant/revoke operator status automatically.
- **Temporary roles** — assign roles with a duration (`7d`, `24h`, `1d12h30m`). On expiry, the player reverts to the default role.
- **Default role** — configurable default role (auto-created if missing, cannot be deleted). New players receive it automatically.
- **Multiple storage backends** — YAML (default), SQLite, or MySQL (HikariCP connection pool).
- **PlaceholderAPI integration** — 9 placeholders for use with TAB, scoreboards, holograms, and more.
- **Hex color support** — all colors are defined using hex format (`#rrggbb`) for full color control.
- **Chat formatting** — automatic chat prefix and color application (toggleable).
- **Localisation** — all player-facing messages are translatable (`en_US`, `pt_PT` included).
- **Developer API** — other plugins can query and modify roles via `ServerRolesProvider.get()`.

---

## Installation

1. Download the latest `ServerRoles-x.x.x.jar` from the releases page.
2. Place it in your server's `plugins/` folder.
3. Start (or restart) the server.
4. Configuration files will be generated in `plugins/ServerRoles/`:
   - `config.yml` — main configuration
   - `roles.yml` — role definitions
   - `lang/en_US.yml` — English messages
   - `lang/pt_PT.yml` — Portuguese messages

### Requirements

| Requirement     | Version      |
|-----------------|--------------|
| Paper or Purpur | **1.21.11**  |
| Java            | **21+**      |

**Optional:** [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) for placeholder support.

---

## Configuration

### config.yml

```yaml
# Language for player-facing messages.
# Bundled options: en_US, pt_PT
language: en_US

# The role given to new players and used as the reset target.
default-role: player

storage:
  # Backend: yaml | sqlite | mysql
  type: yaml
  mysql:
    host: localhost
    port: 3306
    database: serverroles
    username: root
    password: password

features:
  # Format chat messages with the player's role prefix and color.
  chat-format: true
```

### Storage Backends

#### YAML (default)

No configuration needed beyond `storage.type: yaml`. Data is stored in `plugins/ServerRoles/roles.yml` and `plugins/ServerRoles/players.yml`. Best for small servers.

#### SQLite

```yaml
storage:
  type: sqlite
```

Creates a `serverroles.db` file in the plugin folder. Good for medium-sized servers — no external database needed.

#### MySQL

```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: serverroles
    username: root
    password: password
```

Best for networks or large servers. Uses HikariCP for connection pooling.

---

## Default Role

ServerRoles has a built-in default role system:

- The default role ID is set in `config.yml` via `default-role` (defaults to `player`).
- If the default role does not exist in `roles.yml`, it is **automatically created** on startup with sensible defaults (`priority: 0`, no permissions, gray color `#aaaaaa`).
- When a player joins for the **first time** (or has no stored role), they are **automatically assigned** the default role.
- The default role **cannot be deleted** via `/role delete`.
- When a **temporary role expires**, the player is automatically **reverted to the default role**.
- Permissions and prefix/color are applied **immediately on join**.

---

## Roles (roles.yml)

Roles are defined in `roles.yml`. Each role has an ID, display name, prefix, hex color, priority, op flag, parent inheritance, and permission list.

### Example

```yaml
roles:
  owner:
    displayName: "Owner"
    prefix: "[OWNER]"
    color: "#ffaa00"
    priority: 100
    isOp: true
    inheritFrom: "admin"
    permissions: []
  admin:
    displayName: "Admin"
    prefix: "[ADM]"
    color: "#ff5555"
    priority: 90
    isOp: false
    inheritFrom: "mod"
    permissions:
      - "bukkit.command.op"
      - "essentials.ban"
  mod:
    displayName: "Mod"
    prefix: "[MOD]"
    color: "#5555ff"
    priority: 50
    isOp: false
    inheritFrom: "player"
    permissions:
      - "bukkit.command.ban"
      - "bukkit.command.kick"
      - "bukkit.command.mute"
  player:
    displayName: "Player"
    prefix: "[PLAYER]"
    color: "#aaaaaa"
    priority: 0
    isOp: false
    inheritFrom: ""
    permissions: []
```

### Role Fields

| Field         | Description                                                              |
|---------------|--------------------------------------------------------------------------|
| `displayName` | Human-readable name shown to players.                                    |
| `prefix`      | Plain text prefix (e.g. `[ADM]`). Color is applied from the `color` field.|
| `color`       | Hex color code (e.g. `#ff5555`) applied to the prefix and chat messages. |
| `priority`    | Numeric weight (higher = more powerful). Used for sorting.               |
| `isOp`        | `true` to grant operator status to players with this role.               |
| `inheritFrom` | ID of the parent role to inherit permissions from (or `""` for none).    |
| `permissions` | List of permission nodes. Prefix with `-` to negate (e.g. `-some.perm`).|

### Color Format

All colors use **hex format** (`#rrggbb`). Examples:

| Color   | Hex Code    |
|---------|-------------|
| Red     | `#ff5555`   |
| Green   | `#55ff55`   |
| Blue    | `#5555ff`   |
| Gold    | `#ffaa00`   |
| Gray    | `#aaaaaa`   |
| White   | `#ffffff`   |
| Yellow  | `#ffff55`   |
| Aqua    | `#55ffff`   |
| Purple  | `#aa00aa`   |
| Pink    | `#ff55ff`   |

### Permission Inheritance

Permissions are resolved bottom-up: parent permissions are applied first, then child permissions override. Circular inheritance is detected and prevented.

### Permission Negation

Prefix a permission with `-` to deny it:

```yaml
permissions:
  - "some.permission"     # granted
  - "-some.other.perm"    # denied (overrides parent)
```

---

## Commands

All commands require the `serverroles.admin` permission (default: op).

| Command                                           | Description                                         |
|---------------------------------------------------|-----------------------------------------------------|
| `/role list`                                      | List all roles sorted by priority.                  |
| `/role info <id>`                                 | Show detailed information about a role.             |
| `/role create <id> <displayName> <priority>`      | Create a new role.                                  |
| `/role delete <id>`                               | Delete a role (cannot delete the default role).     |
| `/role setprefix <id> <prefix>`                   | Set a role's prefix (plain text).                   |
| `/role setcolor <id> <#rrggbb>`                   | Set a role's hex color (e.g. `#ff5555`).            |
| `/role setop <id> <true\|false>`                  | Set whether a role grants operator status.          |
| `/role setpriority <id> <number>`                 | Set a role's priority.                              |
| `/role setparent <id> <parentId\|none>`           | Set a role's parent (inheritance).                  |
| `/role addperm <id> <permission>`                 | Add a permission to a role.                         |
| `/role removeperm <id> <permission>`              | Remove a permission from a role.                    |
| `/role listperms <id>`                            | List a role's own and effective (inherited) perms.  |
| `/role assign <player> <roleId>`                  | Assign a permanent role to a player.                |
| `/role assigntemp <player> <roleId> <duration>`   | Assign a temporary role to a player.                |
| `/role remove <player>`                           | Reset a player to the default role.                 |
| `/role check <player>`                            | Show a player's current role and temp status.       |
| `/role reload`                                    | Reload all configuration, roles, and players.       |

**Aliases:** `/roles`, `/sr`

### Permissions

| Permission          | Description                                  | Default |
|---------------------|----------------------------------------------|---------|
| `serverroles.admin` | Access to all `/role` admin commands.         | op      |
| `serverroles.user`  | Basic ServerRoles user permission.            | true    |

---

## Temporary Roles

Temporary roles automatically expire after a set duration, reverting the player to the default role.

### Duration Format

Combine any of the following units:

| Unit | Meaning  | Example   |
|------|----------|-----------|
| `d`  | Days     | `7d`      |
| `h`  | Hours    | `24h`     |
| `m`  | Minutes  | `30m`     |

**Combinations are supported:**

| Duration    | Meaning                  |
|-------------|--------------------------|
| `7d`        | 7 days                   |
| `24h`       | 24 hours                 |
| `30m`       | 30 minutes               |
| `1d12h`     | 1 day and 12 hours       |
| `1d12h30m`  | 1 day, 12 hours, 30 min  |

### Example

```
/role assigntemp Steve admin 7d
/role assigntemp Steve vip 1d12h30m
```

### Expiry Behavior

- A background task checks for expired roles every **5 minutes**.
- Expired roles are also checked on **player join**.
- On expiry, the player is **reverted to the default role**, permissions are recalculated, and the player is notified.

---

## PlaceholderAPI Integration

ServerRoles integrates with [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) to expose player role data as placeholders for use in any compatible plugin.

> **Important:** PlaceholderAPI is **optional**. ServerRoles works perfectly without it. If PlaceholderAPI is not installed, the plugin simply skips placeholder registration — no errors, no configuration needed.

### Setup

1. Install [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) on your server.
2. That's it! ServerRoles automatically detects PlaceholderAPI and registers its expansion. No extra configuration or `/papi ecloud` downloads needed — the expansion is built in.

### Available Placeholders

| Placeholder                        | Description                                          | Example Output     |
|------------------------------------|------------------------------------------------------|--------------------|
| `%serverroles_role_id%`            | Internal role ID                                     | `admin`            |
| `%serverroles_role_name%`          | Display name of the role                             | `Admin`            |
| `%serverroles_prefix%`             | Plain text prefix (no color)                         | `[ADM]`            |
| `%serverroles_prefix_formatted%`   | Prefix with hex color applied (MiniMessage format)   | `<color:#ff5555>[ADM]</color:#ff5555>` |
| `%serverroles_color%`              | Hex color code                                       | `#ff5555`          |
| `%serverroles_priority%`           | Role priority number                                 | `90`               |
| `%serverroles_is_op%`              | Whether the role grants op                           | `true` / `false`   |
| `%serverroles_is_temp%`            | Whether the player's role is temporary               | `true` / `false`   |
| `%serverroles_expiry%`             | Expiry date/time, or `permanent`                     | `2026-03-11 14:30` |

### Using with TAB Plugin

[TAB](https://github.com/NEZNAMY/TAB) is one of the most popular tablist/nametag plugins. Here's how to use ServerRoles placeholders with it:

#### Show role prefix in tablist

In TAB's `config.yml`, set the tab format:

```yaml
header-footer:
  enabled: true

tablist-name-formatting:
  enabled: true
  # Use the plain text prefix with the player's name
  tabprefix: "%serverroles_prefix% "
  tabsuffix: ""
```

#### Show role prefix in nametag (above player heads)

```yaml
nametags:
  enabled: true
  tagprefix: "%serverroles_prefix% "
  tagsuffix: ""
```

#### Show role name in the player list

```yaml
tablist-name-formatting:
  enabled: true
  customtabname: "%serverroles_prefix% &f%player%"
```

#### Sort players by role priority in TAB

```yaml
sorting:
  sorting-types:
    - "PLACEHOLDER_LOW_TO_HIGH:%serverroles_priority%"
```

> **Tip:** TAB also supports per-group or per-world overrides. See the [TAB wiki](https://github.com/NEZNAMY/TAB/wiki) for advanced configuration.

#### Other compatible plugins

Any plugin that supports PlaceholderAPI can use these placeholders, including:
- **DeluxeChat** — `%serverroles_prefix% %player%: %message%`
- **Essentials** — via PAPI formatting in chat
- **HolographicDisplays / DecentHolograms** — `%serverroles_role_name%`
- **Scoreboard plugins** (e.g. AnimatedScoreboard) — `%serverroles_role_name%`

---

## Developer API

Other plugins can interact with ServerRoles programmatically:

```java
import pt.henrique.serverroles.api.ServerRolesAPI;
import pt.henrique.serverroles.api.ServerRolesProvider;
import pt.henrique.serverroles.model.Role;

// Get the API instance
ServerRolesAPI api = ServerRolesProvider.get();

// Get a player's role
Role role = api.getPlayerRole(player.getUniqueId());

// Assign a permanent role
api.setPlayerRole(player.getUniqueId(), "admin");

// Assign a temporary role (expiry as Unix timestamp in milliseconds)
long expiry = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000); // 7 days
api.setPlayerRoleTemp(player.getUniqueId(), "vip", expiry);

// Check permissions
boolean canBan = api.hasPermission(player.getUniqueId(), "bukkit.command.ban");

// Register a custom role at runtime
Role custom = new Role("vip", "VIP", "[VIP]", "#55ff55", 10, false, "player", List.of("some.perm"));
api.registerRole(custom);
```

### Events

- **`PlayerRoleChangeEvent`** — fired when a player's role changes. Cancellable. Only fires when the player is online.

---

## Troubleshooting

### Common Issues

#### "PlaceholderAPI placeholders show as literal text"

This means the placeholder text (e.g. `%serverroles_role_name%`) appears un-replaced. There are two possible causes:

**1. The expansion is not registered:**
- Make sure PlaceholderAPI is installed and enabled.
- Check the console for `ServerRoles PAPI expansion registered successfully` on startup.
- Run `/role papi` in-game to see full diagnostics (installed, enabled, registered, and test values).
- Try `/papi parse me %serverroles_role_name%` to verify the placeholder resolves directly via PAPI.
- If it still shows literally, run `/role reload` and try again.
- Enable `debug.papi: true` in `config.yml` and restart for detailed registration logs.

**2. Your chat plugin is not parsing PAPI placeholders:**
- ServerRoles correctly registers its placeholders with PlaceholderAPI, but **your chat plugin** must call `PlaceholderAPI.setPlaceholders(player, text)` to replace them.
- If you use BetterChat, DeluxeChat, EssentialsChat, or similar, check that plugin's config to ensure PAPI support is enabled.
- You can verify this by using `/papi parse me %serverroles_role_name%` — if this resolves correctly but chat still shows literals, the issue is your chat plugin, not ServerRoles.

#### "Player doesn't get permissions on join"

- Check that the role exists in `roles.yml` and the player is assigned to it (use `/role check <player>`).
- Run `/role reload` to reload configuration.
- Ensure no other permission plugin is conflicting (e.g. LuckPerms). ServerRoles is designed to be the sole permission provider.

#### "Temporary role didn't expire"

- Expiry is checked every 5 minutes and on player join. There can be up to a 5-minute delay.
- Verify the role is actually temporary with `/role check <player>`.

#### "Cannot delete the default role"

- This is intentional. The default role (configured in `config.yml` as `default-role`) cannot be deleted because it's the fallback for all players.

#### "Unknown storage type, falling back to YAML"

- Check `config.yml` → `storage.type`. Valid values are `yaml`, `sqlite`, or `mysql`.

#### "MySQL connection failed"

- Verify the host, port, database name, username, and password in `config.yml`.
- Ensure the MySQL server is running and the database exists.
- Check that the MySQL user has sufficient privileges.

#### "Roles not showing in chat"

- Ensure `features.chat-format: true` in `config.yml`.
- If using another chat plugin (e.g. EssentialsChat), it may override ServerRoles' chat formatting. Use PlaceholderAPI placeholders in the other plugin's format instead.

#### "Reload doesn't apply changes"

- Use `/role reload` — this reloads config, roles, players, and recalculates all permissions.
- For `paper-plugin.yml` changes, a full server restart is required.

#### "NoClassDefFoundError for PlaceholderAPI"

- This was fixed in the latest version. ServerRoles now uses reflection to hook into PlaceholderAPI, so it will never crash if PAPI is not installed. Make sure you're using the latest build.

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/henriquescrrrr/carrageis-serverroles.git
cd ServerRoles

# Build the shadow JAR
./gradlew shadowJar

# Output: build/libs/ServerRoles-1.0.0.jar
```

Requires **Java 21**. The build uses Gradle Kotlin DSL with the Shadow plugin (HikariCP is shaded and relocated).

---

## License

See the project license file for details.
