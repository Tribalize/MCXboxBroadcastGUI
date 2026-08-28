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

## Download

The easiest way to use the launcher is from the GitHub Releases page.

1. Open the repository's **Releases** page.
2. Download `MCXboxBroadcast-Windows.zip` from the release that includes the Bedrock version you need.
3. Extract the whole ZIP to a normal writable folder, such as your Downloads folder. Do not run it from inside the ZIP.
4. Open the extracted `MCXboxBroadcast` folder and run `MCXboxBroadcast.exe`.

The extracted folder is an application bundle. Start only `MCXboxBroadcast.exe`; do not run the JAR files inside `app` directly.

Keep the following files and folders together in the extracted `MCXboxBroadcast` folder:

- `MCXboxBroadcast.exe` -- starts the GUI.
- `app` -- contains the GUI files and the bundled `MCXboxBroadcastStandalone.jar` that does the broadcasting.
- `runtime` (if included) -- the Java runtime used by the packaged launcher.

Moving, deleting, or copying only the `.exe` will prevent the GUI from starting correctly. If the package is moved to another PC, copy the entire extracted `MCXboxBroadcast` folder, including `app` and `runtime`.

## First Start

1. Start `MCXboxBroadcast.exe` from the extracted application folder.
2. On the **Server** tab, enter the Bedrock server address and port, then save the server configuration.
3. Leave **Query server before broadcasting** enabled when this PC can reach the server. Turn it off if the server cannot be pinged from this PC (for example, because it is on a different network or blocks status queries).
4. Start the broadcast. On the first run, the log displays a Microsoft device-login code. Follow the prompt, sign in with the Xbox account that will host the broadcast, and enter that code in the browser.
5. After authentication completes, leave the GUI running. Players can then join the broadcast session from Minecraft/Xbox.

The release package includes the GUI app image and the matching MCXboxBroadcast standalone JAR built by GitHub Actions. When making a new GUI release, first ensure the broadcaster build is successful; the GUI workflow downloads and bundles that updated standalone JAR automatically.

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

### Xbox login expired

If the launcher shows `Re-auth required`, the saved Microsoft/Xbox login has expired and Microsoft requires the account to sign in again. This cannot be completed silently in the background.

When MCXboxBroadcast prints a Microsoft device-code login message, the launcher shows the auth banner and opens the Microsoft login page. Sign in with the Xbox account and enter the displayed code. After the log shows the account was successfully authenticated, the launcher waits briefly for the broadcast session to resume and restarts the session automatically if needed.

### Session says online but players cannot join

If the Xbox websocket reconnects but MCXboxBroadcast does not publish a fresh session update, players may see the account as offline or fail to connect even though the process still appears to be running. With auto-restart enabled, the launcher watches for that stale reconnect state and restarts the broadcast session automatically if it does not recover.
