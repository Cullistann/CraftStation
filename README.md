# CraftStation

**Minecraft server control panel** — Swing-based, lightweight, portable.

![Java](https://img.shields.io/badge/Java-25-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)
![Platform](https://img.shields.io/badge/Platform-Windows%2010%2F11-lightgrey)

---

## Features

- **Dashboard** — Server status, TPS, RAM usage, online players
- **Console** — Real-time log monitoring and command execution
- **Player Management** — View online players
- **Backup** — ZIP, incremental (Fast), and TAR (.csbak) strategies with automatic backup and restore
- **Settings** — Edit `server.properties`, `spigot.yml`, `bukkit.yml`, `purpur.yml` and configure RAM allocation
- **Speed Test** — Server internet speed measurement
- **Multi-Language** — Turkish and English interface
- **Portable** — Bundled JRE, no installation required

## Supported Server Types

- Vanilla
- Paper / Purpur
- Spigot / CraftBukkit
- Forge / NeoForge

---

## Quick Start

### Pre-built Release (Recommended)

1. Download the latest release from [Releases](../../releases)
2. Extract the ZIP and place the folder next to your Minecraft server files
3. Run `CraftStation.exe`

### Building from Source

**Requirements:**
- Java 25+ (JDK)
- Windows 10/11

```bash
# Clone the repository
git clone https://github.com/Cullistann/CraftStation.git
cd CraftStation

# Build
cd panel
build.bat

# Run (from the server directory)
cd ..
java\bin\javaw.exe -cp "panel\out;panel\lib\*" Main
```

### Creating a Release Package

```bash
cd panel
cook.bat
```

This creates a complete distribution package (including the bundled JRE) in the `CraftStation-Release/` directory.

---

## Project Structure

```
CraftStation/
├── CraftStation.exe          # Windows launcher
├── CraftStation.ico          # Application icon
├── CraftStationLauncher.cs   # Launcher source code (C#)
├── LICENSE                   # Apache 2.0
├── panel/
│   ├── src/
│   │   ├── main/java/
│   │   │   ├── Main.java           # Entry point
│   │   │   ├── core/               # Business logic layer
│   │   │   │   ├── ServerManager    # Server process management
│   │   │   │   ├── BackupManager    # Backup management
│   │   │   │   ├── ConfigManager    # Configuration read/write
│   │   │   │   └── ...
│   │   │   └── ui/                  # Swing UI layer
│   │   │       ├── MainFrame        # Main window
│   │   │       ├── DashboardPanel   # Dashboard tab
│   │   │       ├── ConsolePanel     # Console tab
│   │   │       └── ...
│   │   └── test/java/               # Unit tests
│   ├── assets/                      # Graphics and fonts
│   ├── lib/                         # Dependencies
│   │   └── flatlaf-3.5.4.jar        # FlatLaf Look & Feel
│   ├── build.bat                    # Build script
│   └── cook.bat                     # Release packaging script
└── java/                            # Bundled JRE (not tracked in git)
```

---

## Dependencies

| Library | Version | Usage |
|---------|---------|-------|
| [FlatLaf](https://www.formdev.com/flatlaf/) | 3.5.4 | Modern Swing Look & Feel |

---

## License

[Apache License 2.0](LICENSE)
