# MCXboxBroadcastGUI

MCXboxBroadcastGUI is a Windows GUI wrapper for self-hosting with MCXboxBroadcast.

<img width="1036" height="708" alt="Screenshot 2026-05-10 102418" src="https://github.com/user-attachments/assets/dd1bd464-f432-416d-8728-bf9bdd184cb8" />

This project is only a GUI wrapper. Full credit for the broadcaster itself goes to [MCXboxBroadcast/Broadcaster](https://github.com/MCXboxBroadcast/Broadcaster).

## Download

The easiest way to use the launcher is from the GitHub Releases page.

1. Open the repository's **Releases** page.
2. Download `MCXboxBroadcast-Windows.zip` from the latest release assets.
3. Extract the ZIP file.
4. Run the MCXboxBroadcast launcher from the extracted folder.

The release package includes the GUI app image and the bundled MCXboxBroadcast standalone JAR built by GitHub Actions.

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
