# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the shadow JAR (output: build/libs/ServerRoles-1.0.0.jar)
./gradlew shadowJar

# Full build (same — shadowJar is wired into the build task)
./gradlew build

# Run a local Paper server for testing (downloads Paper automatically)
./gradlew runServer

# Compile only, no jar
./gradlew classes
```

The build uses **Gradle Kotlin DSL** (`build.gradle.kts` + `settings.gradle.kts`). There are no Groovy `.gradle` files — delete any that appear. Java toolchain is Java 21. HikariCP is shaded and relocated to `pt.henrique.serverroles.libs.hikari`.

## Architecture

### Plugin bootstrap (`ServerRoles.java`)
The main class implements `ServerRolesAPI` directly and wires all subsystems together:
1. `LangManager` — initialised first, before any other component that sends messages
2. `StorageManager` — one of `YamlStorage` / `SQLiteStorage` / `MySQLStorage` (config-driven)
3. `RoleManager` — loads roles from storage; auto-creates the default role if missing
4. `PlayerRoleManager` — loads player→role mappings; creates entries for unknown players on join
5. `PermissionAttachmentManager` — applies Bukkit `PermissionAttachment` per online player
6. `TempRoleManager` — starts a 5-minute repeating main-thread task for expiry checks

On **reload** (`/role reload`), the sequence is: `reloadConfig()` → `lang.load()` → `roleManager.loadRoles()` → `playerRoleManager.loadPlayers()` → `permissionManager.recalculateAll()`.

### Localisation (`LangManager`)
All player-facing strings live in `src/main/resources/lang/en_US.yml` (default) and `lang/pt_PT.yml`. Keys use MiniMessage syntax; placeholders are `<name>` tokens replaced via `Map<String, String>`. The active language is set in `config.yml` under `language:`. On first run, lang files are extracted from the jar to `plugins/ServerRoles/lang/`. Lookups fall through to `en_US` if a key is missing in the active language.

### Permission resolution (`RoleManager.resolvePermissions`)
Builds a `Map<String, Boolean>` by walking the inheritance chain bottom-up (parent first, child overrides). Negated permissions are prefixed with `-`. Circular inheritance is detected via a visited-set both at load time and when `/role setparent` is issued.

### Storage interface (`StorageManager`)
Three implementations share the same interface. SQLite and MySQL use HikariCP pools (shaded). YAML storage re-reads the entire file on every `saveRole` / `savePlayer` call — acceptable for small servers; use SQLite/MySQL for production loads.

### Events and API
- `PlayerRoleChangeEvent` — cancellable; fired from `PlayerRoleManager.setPlayerRole` only when the player is currently online.
- External plugins access the system through `ServerRolesProvider.get()` which returns the `ServerRolesAPI` instance registered at enable time.

### Commands (`RoleCommand`)
Registered via `LifecycleEvents.COMMANDS` (Paper Brigadier). Implements `BasicCommand` for compatibility with Paper 1.21+. Tab completion is driven by live role IDs and online player names. All `lang.get(key)` / `lang.get(key, map)` calls replace the old `config.getString("messages.*")` pattern.

## Key Constraints

- **Never call Bukkit API from async threads.** The temp-role check task and all permission attachment operations run on the main thread.
- **`paper-plugin.yml` is the plugin descriptor** (not `plugin.yml`). The `version` field is expanded at build time via `processResources`.
- Role IDs are always lower-cased before storage/lookup. Display names are free-form.
- The default role ID comes from `config.yml → default-role` (default: `"player"`). It is auto-created if absent and cannot be deleted.
- Expiry timestamps are Unix milliseconds; `-1` means permanent.
