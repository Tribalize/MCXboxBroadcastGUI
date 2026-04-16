# MCXboxBroadcastGUI
MCXboxBroadcastGUI for self hosting on Windows
<img width="946" height="673" alt="MXGUI" src="https://github.com/user-attachments/assets/bf2529d2-e2f5-45b6-92b2-b57cdc4cfab2" />


# 1. Put MCXboxBroadcastGUI.java in the same folder as your MCXboxBroadcastExtension.jar
# 2. Open Command Prompt in that folder, then:
Enter this  ```javac MCXboxBroadcastGUI.java```
This will compile the GUI
#3. To start the program Open Command Prompt in that folder then:
Enter this  ```java MCXboxBroadcastGUI```

This will start the MCXboxBroadcastGUI. With auto session restart. 

Add AUTO_RESTART_PATCH.md to the same folder as the Jar
### Auto-Restart explained

The GUI watchdog operates at the **process level** — it watches for the child JVM to exit and relaunches it. This complements the built-in `restart` command (which operates inside the JVM) because it handles situations the internal restart cannot:

| Scenario | Internal `restart` command | GUI watchdog |
|----------|---------------------------|--------------|
| Token refresh / Xbox session reconnect | ✅ Works | Not needed |
| RTC/WebSocket permanently wedged (Issue #184) | ❌ Process hangs | ✅ Detects exit, relaunches |
| JVM OutOfMemoryError / fatal crash | ❌ JVM is dead | ✅ Relaunches |
| Network interface change (laptop sleep/wake) | ⚠️ Partial | ✅ Full relaunch |

---

## 2 · Server-Side Auto-Restart (no GUI — headless / Docker)

If you are running the standalone JAR on a headless server or in Docker and want automatic restarts without the GUI, use one of the following approaches:

### Option A — Windows Task Scheduler restart loop

Create a `.cmd` wrapper:

```bat
@echo off
:loop
echo [%DATE% %TIME%] Starting MCXboxBroadcast…
java -Xms64m -Xmx256m -jar MCXboxBroadcastStandalone.jar
echo [%DATE% %TIME%] Process exited (code %ERRORLEVEL%). Restarting in 30 s…
timeout /t 30 /nobreak
goto loop
```

Save as `start-broadcaster.cmd` and run it, or register it as a Windows Service via NSSM (Non-Sucking Service Manager):

```
nssm install MCXboxBroadcast "C:\path\to\start-broadcaster.cmd"
nssm set MCXboxBroadcast AppRestartDelay 30000
nssm start MCXboxBroadcast
```

### Option B — Docker restart policy (already works)

```yaml
# docker-compose.yml
services:
  broadcaster:
    image: ghcr.io/mcxboxbroadcast/standalone:latest
    restart: unless-stopped          # ← this is the auto-restart
    volumes:
      - ./config:/opt/app/config
```

`restart: unless-stopped` means Docker will relaunch the container whenever it exits unexpectedly.

---

## 3 · Scheduled connection refresh (inside the JAR config)

The standalone `config.yml` already has a `reconnect-every` or similar interval key depending on the version. In recent builds the relevant key is:

```yaml
# How often (in minutes) to perform a full session reconnect
# Set to 0 to disable. Recommended: 60–120 minutes on unstable networks.
reconnect-every: 60
```

If your build does not have this key, you can add it manually — the `SessionManager` reads it via `StandaloneConfig` and schedules a `restart()` call on the `ScheduledExecutorService`.

---

## 4 · Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Auth code never appears | MSA token cache stale | Delete `cache/` folder next to `config.yml` |
| "Failed to ping server" loop | Wrong IP/port in `config.yml` | Edit `remote-address` and `remote-port` |
| "Failed to restart session: Unable to get connectionId" | Xbox Live rate-limit | Increase cooldown to 120 s |
| GUI shows "Stopped" immediately | Wrong JAR path or Java not on PATH | Use "Browse…" to locate JAR; ensure `java -version` works in CMD |<img width="946" height="673" alt="MXGUI" src="https://github.com/user-attachments/assets/5b7f90b3-b838-465d-94a3-6f8a451954b1" />
