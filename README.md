# Oxide

<p align="center">
  <img src="./icon.png" alt="Oxide logo" width="180">
</p>

<p align="center">
  A polished Android launcher for Minecraft: Java Edition, built for people who actually manage multiple instances, modpacks, worlds, shaders, and resource packs on one device.
</p>

## What Oxide Is

Oxide is an Android Minecraft: Java Edition launcher focused on day-to-day usability.

It keeps the power of the classic Android Java launcher ecosystem, but pushes much harder on the parts that matter once you start living in the launcher:

- clean instance management
- proper per-instance content folders
- built-in mod, resource pack, and shaderpack handling
- dependency-aware downloads
- smoother navigation and a more intentional UI
- practical tools for exporting, importing, updating, and organizing setups

This repository is the standalone Android source tree for Oxide.

## Why It Exists

Most Android Java launchers are good at getting Minecraft to start.

Oxide is meant to be good at everything around that too:

- building a modded setup without chaos
- keeping different playthroughs isolated
- managing worlds and content without digging through folders
- making the launcher feel fast, clean, and pleasant instead of purely functional

## Highlights

### Real Instance Management

Each instance gets its own folder structure, including its own:

- `mods`
- `resourcepacks`
- `shaderpacks`
- `saves`

That means cleaner setups, fewer collisions, and much less manual cleanup.

### Built-In Content Management

Oxide can manage:

- mods
- resource packs
- shaderpacks
- worlds

from inside the launcher.

You can import files, browse content online, open the correct folder, and keep everything tied to the selected instance.

### Dependency-Aware Mod Downloads

When downloading mods, Oxide resolves required dependencies so a pack is much more likely to launch the first time instead of sending you into missing-library hell.

### Mod Update Checking

Installed mods can be checked for newer compatible versions, with update actions available from inside the launcher.

### Instance Export and Import

Oxide can package an instance into an archive and restore it later, making backups, migration, and sharing much easier.

### Instance Icons

Instances can use built-in icons or custom cropped icons, so different setups are easier to spot at a glance.

### Smooth UI

Oxide includes:

- smoother fragment transitions
- better button feedback
- cleaner instance creation and management flows
- an interface tuned for repeated use, not just first launch

### Modern Runtime Support

Oxide includes support for bundled Java runtimes used by modern Minecraft versions, including:

- Java 8
- Java 17
- Java 21
- Java 25

### Account Flexibility

Oxide supports Microsoft accounts for owned copies of Minecraft Java Edition while also allowing local account flows where appropriate for testing and offline-style setups.

## Feature Overview

| Area | What Oxide adds |
|---|---|
| Instances | Separate game directories, icons, export/import, profile-focused management |
| Mods | In-launcher browsing, dependency handling, updates, enable/disable flow |
| Resource Packs | Per-instance browsing, import, folder access |
| Shaderpacks | Per-instance browsing, import, folder access |
| Worlds | World management directly from the instance tools |
| UI | Cleaner navigation, smoother transitions, stronger visual hierarchy |
| Runtime Support | Java 8, 17, 21, and 25 support for a wide range of game versions |

## Project Identity

- Name: `Oxide`
- Creator: `CogiArt`
- Android app ID: `com.cogiart.oxide`

## Source Tree

This project contains the Android launcher source and supporting modules needed to build the app:

- `app_pojavlauncher`
- `jre_lwjgl3glfw`
- `arc_dns_injector`
- `methods_injector_agent`
- `forge_installer`

The debug APK output is generated at:

```text
app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk
```

## Building

### Requirements

- Android SDK
- Java 21 available on your machine
- a valid `local.properties` pointing to the Android SDK

Example:

```properties
sdk.dir=/Users/yourname/Library/Android/sdk
```

### Build Command

From the repository root:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app_pojavlauncher:assembleDebug
```

### Bundled Runtime Assets

This source tree now includes bundled Android Java runtime asset packs for modern versions:

- `app_pojavlauncher/src/main/assets/components/jre-new` for Java 17
- `app_pojavlauncher/src/main/assets/components/jre-21` for Java 21
- `app_pojavlauncher/src/main/assets/components/jre-25` for Java 25

Java 25 currently ships bundled binpacks for:

- `arm`
- `arm64`
- `x86_64`

If you want to refresh or replace a bundled runtime pack later, copy the prepared assets in with:

```bash
./scripts/install_bundled_runtime.sh 25 /path/to/runtime-pack
```

The source directory must contain:

- `version`
- `universal.tar.xz`
- one or more `bin-*.tar.xz` files

Use Android-compatible `pojav` runtime packs for these assets. Desktop Linux or macOS JDK/JRE archives are not suitable replacements.

### Output

The debug APK will be written to:

```text
app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk
```

## Current Focus

Oxide is aimed at being a serious daily-use Android launcher for Minecraft Java players who want:

- stable instance separation
- strong modded play support
- better launcher-side management tools
- a cleaner experience than the usual Android Java launcher baseline

## Credits

Oxide builds on years of work from the Android Minecraft Java launcher scene.

Major upstream and ecosystem influences include:

- Boardwalk
- PojavLauncher
- Amethyst

This repository also includes or depends on a range of open-source components used for rendering, Java runtime support, audio, input, and platform integration.

## License

Oxide currently ships with the same license text included in the source tree inherited from the Amethyst-based project tree: GNU LGPL v3.

See [LICENSE](./LICENSE).
