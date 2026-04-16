# MCXboxBroadcastGUI
MCXboxBroadcastGUI for self hosting on Windows
<img width="966" height="693" alt="Screenshot 2026-04-16 162531" src="https://github.com/user-attachments/assets/1229b822-3b0e-4872-bc11-0e2ca9d7fe4c" />



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

