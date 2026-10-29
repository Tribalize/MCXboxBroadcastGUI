# MCXboxBroadcastGUI

MCXboxBroadcastGUI is a Windows GUI wrapper for self-hosting with MCXboxBroadcast.

<img width="1036" height="708" alt="Screenshot 2026-05-10 102418" src="https://github.com/user-attachments/assets/dd1bd464-f432-416d-8728-bf9bdd184cb8" />

This project is only a GUI wrapper. Full credit for the broadcaster itself goes to [MCXboxBroadcast/Broadcaster](https://github.com/MCXboxBroadcast/Broadcaster).

## Features

- Windows launcher for the MCXboxBroadcast standalone JAR.
- Built-in `config.yml` editor for server, friends, and webhook settings.
- Bundled JAR/runtime detection for packaged releases.
- Auto-restart watchdog for crashes and normal process exits.
- Stale session recovery when the Xbox websocket reconnects but the broadcast session does not refresh.
- Microsoft/Xbox sign-in helper with device-code detection and a one-click login page.
- Expired Xbox login recovery that pauses restart loops, asks for re-auth, and resumes the session after sign-in.
- After the account is successfully authenticated, the launcher waits briefly, then automatically restarts the broadcast session to refresh NetherNet.

## Download and Launch on Windows

Use the ready-to-run Windows package. You do not need to build the project or install Java separately when using the complete release package.

1. Open [Releases](https://github.com/Tribalize/MCXboxBroadcastGUI/releases) and select a release that supports your Minecraft Bedrock version.
2. Under **Assets**, download **MCXboxBroadcast-Windows.zip**. The **Source code** downloads are for building the project, not launching it.
3. In File Explorer, right-click the downloaded ZIP and choose **Extract All...**, then **Extract**. Choose a folder you can write to, such as a folder inside Downloads or Documents.
4. Open the extracted folder and find **MCXboxBroadcast.exe**. Depending on the extraction folder you chose, you may need to open another folder first. The EXE should be beside the **app** and **runtime** folders.
5. Double-click **MCXboxBroadcast.exe** to open the launcher. Do not launch it while browsing inside the ZIP.

The extracted folder is an application bundle. Start only `MCXboxBroadcast.exe`; do not run the JAR files inside `app` directly.

Keep the following files and folders together in the extracted `MCXboxBroadcast` folder:

- `MCXboxBroadcast.exe` -- starts the GUI.
- `app` -- contains the GUI files and the bundled `MCXboxBroadcastStandalone.jar` that does the broadcasting.
- `runtime` -- the bundled Java runtime used by the packaged launcher.

Moving, deleting, or copying only the `.exe` will prevent the GUI from starting correctly. If the package is moved to another PC, copy the entire extracted `MCXboxBroadcast` folder, including `app` and `runtime`.

For easier access, right-click the extracted EXE, choose **Show more options** if needed, then **Send to** -> **Desktop (create shortcut)**. Keep the original application folder in place.

## First Start

1. Start `MCXboxBroadcast.exe` from the extracted application folder.
2. On the **Server** tab, enter your Bedrock server's address and port. In releases with **Host Name** and **World Name** fields, set the names you want to advertise. Click **Save Server Config**.
3. Leave **Query server before broadcasting** enabled when this PC can reach the server. Turn it off if the server cannot be pinged from this PC (for example, because it is on a different network or blocks status queries).
4. Open the **Launcher** tab and click **Start**. The bundled standalone JAR should be detected automatically.
5. On the first run, the log displays a Microsoft device-login code. Follow the prompt or click **Open Microsoft Login Page**, sign in with the Xbox account that will host the broadcast, and enter the displayed code.
6. Wait for the log to confirm that the Xbox Live session was created. Leave the launcher open and the broadcast PC awake while players use the broadcast session. Your Bedrock server must also remain running.

On later launches, open the same extracted EXE and click **Start**. Sign in again if the launcher requests it. Use **Stop** when you want to end the broadcast.

The release package includes the launcher, broadcaster, and Java runtime together. Editing the repository's source files does not update an already downloaded EXE; install a newly built release to receive those changes.

## Forking This Repository

If you want your own build, fork this repository first.

1. Click **Fork** on GitHub.
2. Open your fork in GitHub Desktop.
3. Make any changes you want to `MCXboxBroadcastGUI.java` or the workflow.
4. Commit and push your changes to your fork.

GitHub Actions must be enabled in your fork. If releases fail to publish, check:

1. Go to **Settings** -> **Actions** -> **General**.
2. Under **Workflow permissions**, select **Read and write permissions**.
3. Save the change.

That permission lets the workflow create or update GitHub Releases in your fork.

## Building A Release From A Fork

The Windows package is built manually from the Actions page. It does not build on every file change or tag push.

1. Go to **Actions** in your fork.
2. Select **Build Windows EXE**.
3. Click **Run workflow**.
4. Enter a release tag, for example `v1.0.0`.
5. Choose whether the release should be marked as a prerelease.
6. Click **Run workflow**.

When the workflow finishes, it uploads `MCXboxBroadcast-Windows.zip` to the Releases page using the release tag you entered.

## Troubleshooting

### The launcher will not open or cannot find its files

Extract the complete release ZIP again into a new folder. Open the EXE from that folder and check that **app** and **runtime** are beside it. Do not copy the EXE out on its own.

If the GUI opens but says the standalone JAR is missing, use **Browse...** on the **Launcher** tab to select `app/MCXboxBroadcastStandalone.jar` inside the same extracted package.

If double-clicking the EXE does nothing, run **Launch-with-diagnostics.cmd** from the same extracted folder (included in newly built releases). It keeps a console open after the launcher exits and saves `launcher-startup.log` beside the EXE. Share that log when reporting a startup problem.

### Minecraft says the host is using an older version

The broadcaster must support your Minecraft version as well as your hosted server. Updating the hosted server alone does not update the broadcaster bundled with this launcher. Install a newly built compatible Windows release into a new folder, and check the startup log for the broadcaster's Bedrock version and protocol number. If you previously selected a custom JAR, use **Browse...** to select the standalone JAR inside the new package's **app** folder.

### Xbox login expired

If the launcher shows `Re-auth required`, the saved Microsoft/Xbox login has expired and Microsoft requires the account to sign in again. This cannot be completed silently in the background.

When MCXboxBroadcast prints a Microsoft device-code login message, the launcher shows the auth banner and opens the Microsoft login page. Sign in with the Xbox account and enter the displayed code. After the log shows the account was successfully authenticated, the launcher waits briefly for the broadcast session to resume and restarts the session automatically if needed.

### Session says online but players cannot join

If the Xbox websocket reconnects but MCXboxBroadcast does not publish a fresh session update, players may see the account as offline or fail to connect even though the process still appears to be running. With auto-restart enabled, the launcher watches for that stale reconnect state and restarts the broadcast session automatically if it does not recover.
