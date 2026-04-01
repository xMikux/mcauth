# MinecraftAuthentication (Fork)

> [!WARNING]
> This fork was developed with AI-assisted (vibe coding) using Claude and Gemini. Code quality and stability are **not guaranteed**. Use at your own risk.

This is a fork of the official plugin for [minecraftauth.me](https://minecraftauth.me), extended with broader platform support, code improvements, and an E2E testing infrastructure. Developed by xMikux with assistance from Claude and Gemini.

> 繁體中文說明請見 [README.zh-TW.md](README.zh-TW.md)

---

## Changes from Upstream

### Platform Support

| Platform | Upstream | This Fork |
|----------|----------|-----------|
| Bukkit / Spigot / Paper | ✅ | ✅ |
| Folia | ❌ | ✅ |
| BungeeCord | ✅ | ❌ (removed) |
| Velocity | ✅ | ✅ |
| Sponge | ✅ | ❌ (removed) |
| Forge 1.16.5 | ✅ | ❌ (removed) |
| Forge 1.18.2 | ✅ | ✅ |
| Forge 1.19.3 | ✅ | ❌ (removed) |
| Forge 1.20.1 | ✅ | ✅ |
| Forge 1.21.1 | ❌ | ✅ |
| Fabric (MC 1.18 – 1.21.1, universal) | ❌ | ✅ |
| Fabric 1.21.11 | ❌ | ✅ |
| Fabric 26.1 | ❌ | ✅ |
| NeoForge 1.21.1 | ❌ | ✅ |

### Improvements

- **Folia support**: Improved thread safety in the Bukkit module to support Folia servers
- **Bug fix**: Fixed `ArrayIndexOutOfBoundsException` when running `/minecraftauth` without arguments
- **Java 25**: Upgraded to Java 25 (required for Minecraft 26.1 / Fabric Loom 1.15+)
- **Minecraft 26.1**: Added Fabric support for the new non-obfuscated Minecraft 26.1 release
- **Cleanup**: Removed BungeeCord and Sponge modules to reduce maintenance overhead

### E2E Testing Infrastructure

Added a `test-server-runner` module for end-to-end integration testing against real server cores:

- Automatically downloads Paper, Fabric, and NeoForge server jars
- Automatically resolves the latest Fabric Loader and NeoForge versions
- Tests across multiple MC versions using JUnit `@ParameterizedTest`
- Simulates real player connection behavior
- Supports parallel execution with dynamic port allocation
- Unified log monitoring across all platforms for error detection

---

## Module Structure

```
minecraftauthentication/
├── common/              # Shared logic (Gatekeeper, AuthenticationService)
├── server/
│   ├── bukkit/          # Bukkit / Spigot / Paper / Folia
│   ├── fabric/
│   │   ├── 1.21.1/      # Universal module for MC 1.18 – 1.21.1
│   │   ├── 1.21.11/     # Separate module for MC 1.21.11 API changes
│   │   └── 26.1/        # Separate module for non-obfuscated MC 26.1 (Java 25)
│   ├── forge/           # Standalone Gradle 8.14 project (ForgeGradle 6.x)
│   │   ├── 1.18.2/
│   │   ├── 1.20.1/
│   │   └── 1.21.1/
│   └── neoforge/
│       └── 1.21.1/
├── proxy/
│   └── velocity/
└── test-server-runner/  # E2E integration tests
```

---

## Building

### Requirements

- **Main project** (Fabric, NeoForge, Bukkit, Velocity): Java 25
- **Forge modules** (`server/forge/`): Java 21 — ForgeGradle 6.x requires Gradle 8.x which is incompatible with the Gradle 9.x used by the rest of the project

### Build Commands

```bash
# 1. Build all modules except Forge (requires Java 25)
./gradlew build

# 2. Build Forge modules separately (requires Java 21)
cd server/forge
./gradlew build
```

Build outputs are located in each module's `build/libs/` directory.

---

## Upstream

- Website: https://minecraftauth.me
