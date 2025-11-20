# Hardcore World Reset

When one player dies, everyone dies. 60 seconds to ghost, then the world resets.

## What It Does

1. Player dies
2. 3 seconds later → everyone else dies
3. All players ghost at death location
4. 60 second countdown
5. Server stops, deletes worlds, restarts fresh

## Quick Start

```bash
# 1. Build plugin
cd hardcore-server/HardcoreReset
mvn clean package
cd ..

# 2. Download Paper
curl -Lo paper.jar https://api.papermc.io/v2/projects/paper/versions/1.21.10/builds/113/downloads/paper-1.21.10-113.jar

# 3. Run
chmod +x start.sh
./start.sh
```

Server runs on `localhost:25565`

## Requirements

- Java 17+
- Maven (for building plugin)

## Configuration

Edit environment variables before running:

```bash
export MEMORY_MIN=4G
export MEMORY_MAX=8G
./start.sh
```

Defaults:

- Memory: 4G min/max
- Kill delay: 3 seconds
- Countdown: 60 seconds

## Troubleshooting

**Server won't start:** Check if Paper jar exists and Java 17+ installed

**Plugin not loading:** Run `mvn clean package` first

**World not resetting:** Check server has write permissions

## License

MIT
