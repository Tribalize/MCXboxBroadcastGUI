import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/**
 * MCXboxBroadcast Windows GUI Launcher
 *
 * This version includes:
 *   - Integrated config.yml editor (no manual YAML editing needed)
 *   - Auto-detect bundled JAR for jpackage EXE builds
 *   - Bundled JRE support (no Java install needed for end users)
 *   - Auto-restart watchdog
 *   - Auth code detection with one-click browser open
 */
public class MCXboxBroadcastGUI extends JFrame {

    // ── Colours ──────────────────────────────────────────────────────────────
    private static final Color BG_DARK       = new Color(0x1A1D23);
    private static final Color BG_PANEL      = new Color(0x22262F);
    private static final Color BG_INPUT      = new Color(0x2A2F3A);
    private static final Color ACCENT_GREEN  = new Color(0x4CAF50);
    private static final Color ACCENT_YELLOW = new Color(0xFFC107);
    private static final Color ACCENT_RED    = new Color(0xF44336);
    private static final Color ACCENT_BLUE   = new Color(0x42A5F5);
    private static final Color TEXT_PRIMARY   = new Color(0xECEFF4);
    private static final Color TEXT_MUTED     = new Color(0x8892A4);
    private static final Color TEXT_INFO      = new Color(0x88C0D0);
    private static final Color TEXT_WARN      = new Color(0xEBCB8B);
    private static final Color TEXT_ERROR     = new Color(0xBF616A);

    // ── UI components ── main ────────────────────────────────────────────────
    private JTextPane  logPane;
    private StyledDocument logDoc;
    private JTextField cmdField;
    private JLabel     statusLabel;
    private JLabel     statusDot;
    private JButton    startStopBtn;
    private JButton    restartBtn;
    private JButton    openBrowserBtn;
    private JTextField jarPathField;
    private JSpinner   heapSpinner;
    private JCheckBox  autoRestartCb;
    private JSpinner   cooldownSpinner;
    private JPanel     authBarRef;

    // ── UI components ── config editor ───────────────────────────────────────
    private JTextField cfgRemoteAddress;
    private JTextField cfgRemotePort;
    private JSpinner   cfgUpdateInterval;
    private JSpinner   cfgFriendSyncInterval;
    private JCheckBox  cfgAutoFollow;
    private JCheckBox  cfgAutoUnfollow;
    private JCheckBox  cfgInitialInvite;
    private JCheckBox  cfgShouldExpire;
    private JSpinner   cfgExpireDays;
    private JSpinner   cfgExpireCheck;
    private JCheckBox  cfgSlackEnabled;
    private JTextField cfgSlackUrl;

    // ── Tabbed pane for sidebar ──────────────────────────────────────────────
    private JTabbedPane sidebarTabs;

    // ── Process management ───────────────────────────────────────────────────
    private Process process;
    private PrintWriter processStdin;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final AtomicBoolean running  = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private ScheduledFuture<?> countdownFuture;

    // ── Auth detection ───────────────────────────────────────────────────────
    private static final Pattern AUTH_CODE_PATTERN =
            Pattern.compile("enter the code ([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private String lastAuthUrl = "https://www.microsoft.com/link";

    // ── Preferences ──────────────────────────────────────────────────────────
    private static final String PREFS_FILE = "mcxboxbroadcast-gui.properties";
    private final Properties prefs = new Properties();

    // ── Time formatter ───────────────────────────────────────────────────────
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Bundled JAR name ─────────────────────────────────────────────────────
    private static final String STANDALONE_JAR_NAME = "MCXboxBroadcastStandalone.jar";
    private static final String CONFIG_FILE_NAME    = "config.yml";

    // ════════════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MCXboxBroadcastGUI().setVisible(true));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ════════════════════════════════════════════════════════════════════════

    public MCXboxBroadcastGUI() {
        super("MCXboxBroadcast \u2014 Windows Launcher");
        loadPrefs();
        buildUI();
        loadConfigFromDisk();
        setSize(1060, 720);
        setMinimumSize(new Dimension(800, 520));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onClose(); }
        });
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTO-DETECT BUNDLED JAR
    // ════════════════════════════════════════════════════════════════════════

    private String resolveJarPath() {
        String saved = prefs.getProperty("jar.path", "");
        if (!saved.isEmpty() && new File(saved).exists()) return saved;

        String classDir = getAppDirectory();
        if (classDir != null) {
            File candidate = new File(classDir, STANDALONE_JAR_NAME);
            if (candidate.exists()) return candidate.getAbsolutePath();
            candidate = new File(new File(classDir, "app"), STANDALONE_JAR_NAME);
            if (candidate.exists()) return candidate.getAbsolutePath();
        }

        File cwd = new File(System.getProperty("user.dir"), STANDALONE_JAR_NAME);
        if (cwd.exists()) return cwd.getAbsolutePath();

        return saved.isEmpty() ? STANDALONE_JAR_NAME : saved;
    }

    private String getAppDirectory() {
        try {
            String appDir = System.getProperty("jpackage.app-path");
            if (appDir != null) return new File(appDir).getParent();
            var location = MCXboxBroadcastGUI.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            if (location != null) {
                File f = new File(location.toURI());
                return f.isDirectory() ? f.getAbsolutePath() : f.getParent();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private File getConfigDirectory() {
        String jar = jarPathField.getText().trim();
        File jarFile = new File(jar);
        if (jarFile.exists() && jarFile.getParentFile() != null) {
            return jarFile.getParentFile();
        }
        return new File(System.getProperty("user.dir"));
    }

    private File getConfigFile() {
        return new File(getConfigDirectory(), CONFIG_FILE_NAME);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════════════════════

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        // ── Top bar ──────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBackground(BG_PANEL);
        topBar.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel logo = new JLabel("MCXboxBroadcast \u2014 Windows Launcher");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        logo.setForeground(ACCENT_GREEN);
        topBar.add(logo, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        statusPanel.setOpaque(false);
        statusDot = new JLabel("\u25CF");
        statusDot.setFont(new Font("Segoe UI", Font.BOLD, 15));
        statusDot.setForeground(ACCENT_RED);
        statusLabel = new JLabel("Stopped");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(ACCENT_RED);
        statusPanel.add(statusDot);
        statusPanel.add(statusLabel);
        topBar.add(statusPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // ── Left sidebar with tabs ───────────────────────────────────────────
        sidebarTabs = new JTabbedPane(JTabbedPane.TOP);
        sidebarTabs.setFont(new Font("Segoe UI", Font.BOLD, 11));
        sidebarTabs.setBackground(BG_PANEL);
        sidebarTabs.setForeground(Color.BLACK);
        sidebarTabs.setPreferredSize(new Dimension(260, 0));

        sidebarTabs.addTab("Launcher", buildLauncherTab());
        sidebarTabs.addTab("Server", buildServerConfigTab());
        sidebarTabs.addTab("Friends", buildFriendConfigTab());
        sidebarTabs.addTab("Webhook", buildSlackConfigTab());
        for (int i = 0; i < sidebarTabs.getTabCount(); i++) {
            sidebarTabs.setForegroundAt(i, Color.BLACK);
        }

        add(sidebarTabs, BorderLayout.WEST);

        // ── Centre: log + command row ─────────────────────────────────────────
        JPanel centre = new JPanel(new BorderLayout(0, 4));
        centre.setBackground(BG_DARK);
        centre.setBorder(new EmptyBorder(8, 4, 8, 8));

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(new Color(0x12141A));
        logPane.setFont(new Font("Consolas", Font.PLAIN, 12));
        logDoc = logPane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x2E3440)));
        centre.add(scroll, BorderLayout.CENTER);

        JPanel cmdRow = new JPanel(new BorderLayout(6, 0));
        cmdRow.setBackground(BG_DARK);

        JLabel prompt = new JLabel(">");
        prompt.setFont(new Font("Consolas", Font.BOLD, 14));
        prompt.setForeground(ACCENT_GREEN);
        prompt.setBorder(new EmptyBorder(0, 4, 0, 4));

        cmdField = new JTextField();
        styleTextField(cmdField);
        cmdField.setFont(new Font("Consolas", Font.PLAIN, 13));
        cmdField.setToolTipText("Commands: restart | dumpsession | accounts list | accounts add <id> | exit");
        cmdField.addActionListener(e -> sendCommand());

        JButton sendBtn = makeButton("Send", ACCENT_GREEN);
        sendBtn.addActionListener(e -> sendCommand());

        cmdRow.add(prompt,   BorderLayout.WEST);
        cmdRow.add(cmdField, BorderLayout.CENTER);
        cmdRow.add(sendBtn,  BorderLayout.EAST);
        centre.add(cmdRow, BorderLayout.SOUTH);

        add(centre, BorderLayout.CENTER);

        // ── Auth banner ──────────────────────────────────────────────────────
        openBrowserBtn = makeButton(" Open Microsoft Login Page ", ACCENT_BLUE);
        openBrowserBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI(lastAuthUrl)); }
            catch (Exception ex) { appendLog("[GUI] Could not open browser: " + ex.getMessage(), TEXT_ERROR); }
        });

        JLabel authHint = new JLabel(" Authentication required \u2014 sign in with your Microsoft/Xbox account:");
        authHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        authHint.setForeground(ACCENT_BLUE);

        authBarRef = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        authBarRef.setBackground(new Color(0x0D1A2A));
        authBarRef.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ACCENT_BLUE));
        authBarRef.add(authHint);
        authBarRef.add(openBrowserBtn);
        authBarRef.setVisible(false);
        add(authBarRef, BorderLayout.SOUTH);

        // Welcome
        String resolvedPath = jarPathField.getText();
        if (new File(resolvedPath).exists()) {
            appendLog("[GUI] MCXboxBroadcast Launcher ready.", ACCENT_GREEN);
            appendLog("[GUI] Standalone JAR found: " + resolvedPath, ACCENT_GREEN);
            appendLog("[GUI] Configure your server in the Server tab, then click Start.", TEXT_MUTED);
        } else {
            appendLog("[GUI] MCXboxBroadcast Launcher ready.", ACCENT_GREEN);
            appendLog("[GUI] Standalone JAR not found at: " + resolvedPath, TEXT_WARN);
            appendLog("[GUI] Use Browse... to locate MCXboxBroadcastStandalone.jar", TEXT_MUTED);
        }
    }

    // ── Sidebar Tab: Launcher ────────────────────────────────────────────────

    private JPanel buildLauncherTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setBorder(new EmptyBorder(12, 10, 12, 10));

        p.add(sectionLabel("JAR FILE"));
        jarPathField = new JTextField(resolveJarPath());
        styleTextField(jarPathField);
        jarPathField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(jarPathField);
        p.add(Box.createVerticalStrut(4));

        JButton browseBtn = makeButton("Browse...", TEXT_MUTED);
        browseBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        browseBtn.addActionListener(e -> browseJar());
        p.add(browseBtn);

        p.add(Box.createVerticalStrut(12));
        p.add(sectionLabel("MAX HEAP (MB)"));
        int heap = parseIntSafe(prefs.getProperty("heap.mb", "256"), 256);
        heapSpinner = new JSpinner(new SpinnerNumberModel(heap, 64, 4096, 64));
        styleSpinner(heapSpinner);
        heapSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(heapSpinner);

        p.add(Box.createVerticalStrut(12));
        p.add(sectionLabel("AUTO-RESTART"));
        autoRestartCb = new JCheckBox("Restart on crash / exit");
        autoRestartCb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        autoRestartCb.setForeground(TEXT_PRIMARY);
        autoRestartCb.setBackground(BG_PANEL);
        autoRestartCb.setSelected(Boolean.parseBoolean(prefs.getProperty("auto.restart", "true")));
        autoRestartCb.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(autoRestartCb);

        p.add(Box.createVerticalStrut(6));
        p.add(sectionLabel("COOLDOWN (seconds)"));
        int cooldown = parseIntSafe(prefs.getProperty("cooldown.s", "30"), 30);
        cooldownSpinner = new JSpinner(new SpinnerNumberModel(cooldown, 5, 300, 5));
        styleSpinner(cooldownSpinner);
        cooldownSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cooldownSpinner);

        p.add(Box.createVerticalStrut(16));
        p.add(sectionLabel("CONTROLS"));

        startStopBtn = makeButton("\u25B6  Start", ACCENT_GREEN);
        startStopBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        startStopBtn.addActionListener(e -> toggleStartStop());
        p.add(startStopBtn);

        p.add(Box.createVerticalStrut(6));
        restartBtn = makeButton("\u21BA  Restart Session", ACCENT_YELLOW);
        restartBtn.setEnabled(false);
        restartBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        restartBtn.addActionListener(e -> doRestart());
        p.add(restartBtn);

        p.add(Box.createVerticalGlue());
        return p;
    }

    // ── Sidebar Tab: Server Config ───────────────────────────────────────────

    private JPanel buildServerConfigTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setBorder(new EmptyBorder(12, 10, 12, 10));

        p.add(sectionLabel("REMOTE ADDRESS"));
        p.add(descLabel("Your server's public IP or domain"));
        cfgRemoteAddress = new JTextField("auto");
        styleTextField(cfgRemoteAddress);
        cfgRemoteAddress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cfgRemoteAddress);

        p.add(Box.createVerticalStrut(10));
        p.add(sectionLabel("REMOTE PORT"));
        p.add(descLabel("Server port (\"auto\" = use default 19132)"));
        cfgRemotePort = new JTextField("auto");
        styleTextField(cfgRemotePort);
        cfgRemotePort.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cfgRemotePort);

        p.add(Box.createVerticalStrut(10));
        p.add(sectionLabel("UPDATE INTERVAL (seconds)"));
        p.add(descLabel("How often to sync session info (min 20)"));
        cfgUpdateInterval = new JSpinner(new SpinnerNumberModel(30, 20, 600, 5));
        styleSpinner(cfgUpdateInterval);
        cfgUpdateInterval.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cfgUpdateInterval);

        p.add(Box.createVerticalStrut(16));

        JButton saveBtn = makeButton("Save Server Config", ACCENT_GREEN);
        saveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        saveBtn.addActionListener(e -> saveConfigToDisk());
        p.add(saveBtn);

        p.add(Box.createVerticalStrut(6));

        JButton reloadBtn = makeButton("Reload from Disk", TEXT_MUTED);
        reloadBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        reloadBtn.addActionListener(e -> {
            loadConfigFromDisk();
            appendLog("[GUI] Config reloaded from disk.", TEXT_INFO);
        });
        p.add(reloadBtn);

        p.add(Box.createVerticalGlue());
        return p;
    }

    // ── Sidebar Tab: Friend Sync Config ──────────────────────────────────────

    private JPanel buildFriendConfigTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setBorder(new EmptyBorder(12, 10, 12, 10));

        p.add(sectionLabel("FRIEND SYNC INTERVAL (seconds)"));
        p.add(descLabel("How often to check followers (min 20)"));
        cfgFriendSyncInterval = new JSpinner(new SpinnerNumberModel(60, 20, 600, 5));
        styleSpinner(cfgFriendSyncInterval);
        cfgFriendSyncInterval.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cfgFriendSyncInterval);

        p.add(Box.createVerticalStrut(10));
        p.add(sectionLabel("FRIEND OPTIONS"));

        cfgAutoFollow = new JCheckBox("Auto-follow new followers");
        styleCheckbox(cfgAutoFollow, true);
        p.add(cfgAutoFollow);

        cfgAutoUnfollow = new JCheckBox("Auto-unfollow lost followers");
        styleCheckbox(cfgAutoUnfollow, true);
        p.add(cfgAutoUnfollow);

        cfgInitialInvite = new JCheckBox("Send invite on new friend");
        styleCheckbox(cfgInitialInvite, true);
        p.add(cfgInitialInvite);

        p.add(Box.createVerticalStrut(10));
        p.add(sectionLabel("FRIEND EXPIRY"));

        cfgShouldExpire = new JCheckBox("Expire inactive friends");
        styleCheckbox(cfgShouldExpire, true);
        p.add(cfgShouldExpire);

        p.add(Box.createVerticalStrut(6));
        p.add(sectionLabel("EXPIRE AFTER (days)"));
        cfgExpireDays = new JSpinner(new SpinnerNumberModel(15, 1, 365, 1));
        styleSpinner(cfgExpireDays);
        cfgExpireDays.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cfgExpireDays);

        p.add(Box.createVerticalStrut(6));
        p.add(sectionLabel("EXPIRE CHECK INTERVAL (seconds)"));
        cfgExpireCheck = new JSpinner(new SpinnerNumberModel(1800, 60, 86400, 60));
        styleSpinner(cfgExpireCheck);
        cfgExpireCheck.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cfgExpireCheck);

        p.add(Box.createVerticalStrut(16));

        JButton saveBtn = makeButton("Save Friend Config", ACCENT_GREEN);
        saveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        saveBtn.addActionListener(e -> saveConfigToDisk());
        p.add(saveBtn);

        p.add(Box.createVerticalGlue());
        return p;
    }

    // ── Sidebar Tab: Slack Webhook ───────────────────────────────────────────

    private JPanel buildSlackConfigTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setBorder(new EmptyBorder(12, 10, 12, 10));

        p.add(sectionLabel("SLACK WEBHOOK"));

        cfgSlackEnabled = new JCheckBox("Enable Slack notifications");
        styleCheckbox(cfgSlackEnabled, false);
        p.add(cfgSlackEnabled);

        p.add(Box.createVerticalStrut(8));
        p.add(sectionLabel("WEBHOOK URL"));
        p.add(descLabel("Paste your Slack webhook URL"));
        cfgSlackUrl = new JTextField("");
        styleTextField(cfgSlackUrl);
        cfgSlackUrl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cfgSlackUrl);

        p.add(Box.createVerticalStrut(16));

        JButton saveBtn = makeButton("Save Webhook Config", ACCENT_GREEN);
        saveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        saveBtn.addActionListener(e -> saveConfigToDisk());
        p.add(saveBtn);

        p.add(Box.createVerticalGlue());
        return p;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CONFIG.YML READ / WRITE
    // ════════════════════════════════════════════════════════════════════════

    private void loadConfigFromDisk() {
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            appendLog("[GUI] No config.yml found yet \u2014 defaults loaded. Set your server IP in the Server tab.", TEXT_MUTED);
            return;
        }

        try {
            java.util.List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            String section = "";

            for (String raw : lines) {
                String trimmed = raw.trim();
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;

                if (!raw.startsWith(" ") && !raw.startsWith("\t") && trimmed.endsWith(":") && !trimmed.contains(": ")) {
                    section = trimmed.substring(0, trimmed.length() - 1).trim();
                    continue;
                }

                String key = trimmed.contains(":") ? trimmed.substring(0, trimmed.indexOf(':')).trim() : "";
                String value = trimmed.contains(":") ? trimmed.substring(trimmed.indexOf(':') + 1).trim() : "";
                if (value.startsWith("\"") && value.endsWith("\""))
                    value = value.substring(1, value.length() - 1);

                switch (section) {
                    case "":
                        switch (key) {
                            case "remote-address":  cfgRemoteAddress.setText(value); break;
                            case "remote-port":     cfgRemotePort.setText(value); break;
                            case "update-interval": setSpinnerSafe(cfgUpdateInterval, value); break;
                        }
                        break;
                    case "friend-sync":
                        switch (key) {
                            case "update-interval": setSpinnerSafe(cfgFriendSyncInterval, value); break;
                            case "auto-follow":     cfgAutoFollow.setSelected(toBool(value)); break;
                            case "auto-unfollow":   cfgAutoUnfollow.setSelected(toBool(value)); break;
                            case "initial-invite":  cfgInitialInvite.setSelected(toBool(value)); break;
                            case "should-expire":   cfgShouldExpire.setSelected(toBool(value)); break;
                            case "expire-days":     setSpinnerSafe(cfgExpireDays, value); break;
                            case "expire-check":    setSpinnerSafe(cfgExpireCheck, value); break;
                        }
                        break;
                    case "slack-webhook":
                        switch (key) {
                            case "enabled":      cfgSlackEnabled.setSelected(toBool(value)); break;
                            case "webhook-url":  cfgSlackUrl.setText(value); break;
                        }
                        break;
                }
            }

            appendLog("[GUI] Loaded config from: " + configFile.getAbsolutePath(), TEXT_INFO);
        } catch (IOException e) {
            appendLog("[GUI] Failed to read config.yml: " + e.getMessage(), TEXT_ERROR);
        }
    }

    private void saveConfigToDisk() {
        File configFile = getConfigFile();

        StringBuilder yaml = new StringBuilder();
        yaml.append("# MCXboxBroadcast Standalone Configuration\n");
        yaml.append("# Generated by MCXboxBroadcast GUI Launcher\n\n");

        yaml.append("# The IP address to broadcast, you likely want to change this to\n");
        yaml.append("# your server's public IP\n");
        yaml.append("remote-address: ").append(cfgRemoteAddress.getText().trim()).append("\n\n");

        yaml.append("# The port to broadcast, this should be left as auto unless you're\n");
        yaml.append("# manipulating the port using network rules or reverse proxies\n");
        yaml.append("remote-port: ").append(cfgRemotePort.getText().trim()).append("\n\n");

        yaml.append("# The amount of time in seconds to update session information and\n");
        yaml.append("# sync other data\n");
        yaml.append("# Warning: This can be no lower than 20 due to xbox rate limits\n");
        yaml.append("update-interval: ").append(cfgUpdateInterval.getValue()).append("\n\n");

        yaml.append("# Friend/follower list sync settings\n");
        yaml.append("friend-sync:\n");
        yaml.append("  # The amount of time in seconds to check for follower changes\n");
        yaml.append("  update-interval: ").append(cfgFriendSyncInterval.getValue()).append("\n\n");
        yaml.append("  # Should we automatically follow people that follow us\n");
        yaml.append("  auto-follow: ").append(cfgAutoFollow.isSelected()).append("\n\n");
        yaml.append("  # Should we automatically unfollow people that no longer follow us\n");
        yaml.append("  auto-unfollow: ").append(cfgAutoUnfollow.isSelected()).append("\n\n");
        yaml.append("  # Should we automatically send an invite when a friend is added\n");
        yaml.append("  initial-invite: ").append(cfgInitialInvite.isSelected()).append("\n\n");
        yaml.append("  # Should we unfriend people that haven't joined the server in a while\n");
        yaml.append("  should-expire: ").append(cfgShouldExpire.isSelected()).append("\n\n");
        yaml.append("  # The amount of time in days before a friend is considered expired\n");
        yaml.append("  expire-days: ").append(cfgExpireDays.getValue()).append("\n\n");
        yaml.append("  # How often to check in seconds for expired friends\n");
        yaml.append("  expire-check: ").append(cfgExpireCheck.getValue()).append("\n\n");

        yaml.append("# Slack webhook settings\n");
        yaml.append("slack-webhook:\n");
        yaml.append("  # Should we send a message to a slack webhook when the session is updated\n");
        yaml.append("  enabled: ").append(cfgSlackEnabled.isSelected()).append("\n\n");
        yaml.append("  # The webhook url to send the message to\n");
        yaml.append("  webhook-url: \"").append(cfgSlackUrl.getText().trim()).append("\"\n\n");
        yaml.append("  # The message to send when the session is expired and needs to be updated\n");
        yaml.append("  session-expired-message: |\n");
        yaml.append("    <!here> Xbox Session expired, sign in again to update it.\n\n");
        yaml.append("    Use the following link to sign in: %s\n");
        yaml.append("    Enter the code: %s\n\n");
        yaml.append("  # The message to send when a friend has restrictions\n");
        yaml.append("  friend-restriction-message: |\n");
        yaml.append("    %s (%s) has restrictions in place that prevent them from being friends with our account.\n");

        try {
            configFile.getParentFile().mkdirs();
            Files.writeString(configFile.toPath(), yaml.toString(), StandardCharsets.UTF_8);
            appendLog("[GUI] Config saved to: " + configFile.getAbsolutePath(), ACCENT_GREEN);
            if (running.get()) {
                appendLog("[GUI] Note: Restart the session for config changes to take effect.", ACCENT_YELLOW);
            }
        } catch (IOException e) {
            appendLog("[GUI] Failed to save config.yml: " + e.getMessage(), TEXT_ERROR);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PROCESS LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════

    private void startProcess() {
        if (running.get()) return;

        String jar = jarPathField.getText().trim();
        File jarFile = new File(jar);
        if (!jarFile.exists()) {
            appendLog("[GUI] ERROR: JAR not found at: " + jar, TEXT_ERROR);
            appendLog("[GUI] Click Browse... in the Launcher tab to locate it", TEXT_WARN);
            return;
        }

        // Auto-save config before starting if remote-address has been changed
        if (!"auto".equals(cfgRemoteAddress.getText().trim())) {
            saveConfigToDisk();
        } else {
            File cf = getConfigFile();
            if (!cf.exists()) {
                appendLog("[GUI] Warning: No config.yml exists and remote-address is 'auto'.", TEXT_WARN);
                appendLog("[GUI] Go to the Server tab, set your server's IP, and click Save.", TEXT_WARN);
            }
        }

        int heapMb = (int) heapSpinner.getValue();
        stopping.set(false);
        running.set(true);
        setStatus("Starting...", ACCENT_YELLOW);
        SwingUtilities.invokeLater(() -> { if (authBarRef != null) authBarRef.setVisible(false); });

        File workDir = jarFile.getParentFile() != null ? jarFile.getParentFile() : new File(".");
        String javaExe = findJavaExecutable();

        ProcessBuilder pb = new ProcessBuilder(
                javaExe, "-Xms64m", "-Xmx" + heapMb + "m",
                "-jar", jarFile.getAbsolutePath()
        );
        pb.directory(workDir);
        pb.redirectErrorStream(true);

        appendLog("[GUI] Working dir: " + workDir.getAbsolutePath(), TEXT_MUTED);
        appendLog("[GUI] Command: " + javaExe + " -Xmx" + heapMb + "m -jar " + jarFile.getName(), TEXT_MUTED);

        scheduler.submit(() -> {
            try {
                process = pb.start();
                processStdin = new PrintWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);

                SwingUtilities.invokeLater(() -> {
                    startStopBtn.setText("\u25A0  Stop");
                    restartBtn.setEnabled(true);
                    jarPathField.setEnabled(false);
                    heapSpinner.setEnabled(false);
                });

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processLine(line);
                    }
                }

                int exitCode = process.waitFor();
                running.set(false);

                SwingUtilities.invokeLater(() -> {
                    appendLog("[GUI] Process exited with code " + exitCode, TEXT_MUTED);
                    startStopBtn.setText("\u25B6  Start");
                    restartBtn.setEnabled(false);
                    jarPathField.setEnabled(true);
                    heapSpinner.setEnabled(true);
                    if (authBarRef != null) authBarRef.setVisible(false);
                    setStatus("Stopped", ACCENT_RED);
                });

                if (!stopping.get() && autoRestartCb.isSelected()) {
                    scheduleAutoRestart((int) cooldownSpinner.getValue());
                }
            } catch (IOException ex) {
                running.set(false);
                appendLog("[GUI] Failed to launch: " + ex.getMessage(), TEXT_ERROR);
                appendLog("[GUI] Make sure 'java' is on your PATH (test: java -version)", TEXT_WARN);
                SwingUtilities.invokeLater(() -> setStatus("Stopped", ACCENT_RED));
            } catch (InterruptedException ex) {
                running.set(false);
                Thread.currentThread().interrupt();
            }
        });
    }

    private String findJavaExecutable() {
        String appDir = getAppDirectory();
        if (appDir != null) {
            File bundledJava = new File(appDir,
                    "runtime" + File.separator + "bin" + File.separator + "java.exe");
            if (bundledJava.exists()) return bundledJava.getAbsolutePath();

            File parentDir = new File(appDir).getParentFile();
            if (parentDir != null) {
                bundledJava = new File(parentDir,
                        "runtime" + File.separator + "bin" + File.separator + "java.exe");
                if (bundledJava.exists()) return bundledJava.getAbsolutePath();
            }
        }
        return "java";
    }

    private void stopProcess(boolean userInitiated) {
        if (!running.get()) return;
        if (userInitiated) stopping.set(true);
        if (countdownFuture != null) { countdownFuture.cancel(false); countdownFuture = null; }
        if (processStdin != null) processStdin.println("exit");
        scheduler.schedule(() -> {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }, 4, TimeUnit.SECONDS);
    }

    private void doRestart() {
        if (!running.get()) { startProcess(); return; }
        appendLog("[GUI] Sending restart command...", ACCENT_YELLOW);
        setStatus("Restarting...", ACCENT_YELLOW);
        if (processStdin != null) processStdin.println("restart");
        scheduler.schedule(() -> {
            if (!running.get() && !stopping.get()) {
                SwingUtilities.invokeLater(this::startProcess);
            } else {
                SwingUtilities.invokeLater(() -> setStatus("Running", ACCENT_GREEN));
            }
        }, 8, TimeUnit.SECONDS);
    }

    private void scheduleAutoRestart(int delaySecs) {
        appendLog("[GUI] Auto-restart watchdog \u2014 restarting in " + delaySecs + " s...", ACCENT_YELLOW);
        setStatus("Restarting in " + delaySecs + " s", ACCENT_YELLOW);
        final int[] remaining = {delaySecs};
        countdownFuture = scheduler.scheduleAtFixedRate(() -> {
            if (stopping.get()) { countdownFuture.cancel(false); return; }
            remaining[0]--;
            SwingUtilities.invokeLater(() -> setStatus("Restarting in " + remaining[0] + " s...", ACCENT_YELLOW));
            if (remaining[0] <= 0) {
                countdownFuture.cancel(false);
                SwingUtilities.invokeLater(this::startProcess);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void toggleStartStop() {
        if (running.get()) stopProcess(true);
        else               startProcess();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LOG PROCESSING
    // ════════════════════════════════════════════════════════════════════════

    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*[A-Za-z]|\\[\\d+[;m][\\d;]*m?");

    private void processLine(String raw) {
        String clean = ANSI_PATTERN.matcher(raw).replaceAll("").trim();
        if (clean.isEmpty()) return;

        Color color = TEXT_PRIMARY;
        String lower = clean.toLowerCase();

        if (lower.contains("[error]") || lower.contains("exception") || lower.contains("failed"))
            color = TEXT_ERROR;
        else if (lower.contains("[warn]") || lower.contains("warning"))
            color = TEXT_WARN;
        else if (lower.contains("[info]"))
            color = TEXT_INFO;

        if (lower.contains("creation of xbox live session was successful")
                || lower.contains("created session")
                || lower.contains("session created")
                || lower.contains("updated session")) {
            if (lower.contains("successful") || lower.contains("created")) {
                color = ACCENT_GREEN;
                SwingUtilities.invokeLater(() -> setStatus("Running", ACCENT_GREEN));
            }
        }

        if (lower.contains("microsoft.com/link")) {
            Matcher m = AUTH_CODE_PATTERN.matcher(clean);
            String code = m.find() ? "  Code: " + m.group(1) : "";
            appendLog("[GUI] Auth required" + code + " -> Click the button below", ACCENT_BLUE);
            SwingUtilities.invokeLater(() -> { if (authBarRef != null) authBarRef.setVisible(true); });
        }

        appendLog(clean, color);
    }

    private void appendLog(String text, Color color) {
        String ts = "[" + LocalTime.now().format(TIME_FMT) + "] ";
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleAttributeSet tsAttr = new SimpleAttributeSet();
                StyleConstants.setForeground(tsAttr, TEXT_MUTED);
                logDoc.insertString(logDoc.getLength(), ts, tsAttr);

                SimpleAttributeSet lineAttr = new SimpleAttributeSet();
                StyleConstants.setForeground(lineAttr, color);
                logDoc.insertString(logDoc.getLength(), text + "\n", lineAttr);

                logPane.setCaretPosition(logDoc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private void sendCommand() {
        String cmd = cmdField.getText().trim();
        if (cmd.isEmpty()) return;
        if (!running.get()) {
            appendLog("[GUI] Process is not running \u2014 start it first.", TEXT_WARN);
            return;
        }
        appendLog("> " + cmd, ACCENT_GREEN);
        if (processStdin != null) processStdin.println(cmd);
        cmdField.setText("");
    }

    // ════════════════════════════════════════════════════════════════════════
    //  STATUS / FILE CHOOSER / PREFERENCES
    // ════════════════════════════════════════════════════════════════════════

    private void setStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusDot.setForeground(color);
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        });
    }

    private void browseJar() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select MCXboxBroadcastStandalone.jar");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JAR files (*.jar)", "jar"));
        String current = jarPathField.getText().trim();
        if (!current.isEmpty()) {
            File f = new File(current);
            fc.setCurrentDirectory(f.getParentFile() != null ? f.getParentFile() : new File("."));
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            jarPathField.setText(fc.getSelectedFile().getAbsolutePath());
            loadConfigFromDisk();
        }
    }

    private void loadPrefs() {
        File prefsFile = new File(PREFS_FILE);
        String appDir = getAppDirectory();
        if (appDir != null) {
            File appPrefs = new File(appDir, PREFS_FILE);
            if (appPrefs.exists()) prefsFile = appPrefs;
        }
        try (InputStream in = new FileInputStream(prefsFile)) { prefs.load(in); }
        catch (IOException ignored) {}
    }

    private void savePrefs() {
        if (jarPathField    != null) prefs.setProperty("jar.path",     jarPathField.getText());
        if (heapSpinner     != null) prefs.setProperty("heap.mb",      heapSpinner.getValue().toString());
        if (autoRestartCb   != null) prefs.setProperty("auto.restart", Boolean.toString(autoRestartCb.isSelected()));
        if (cooldownSpinner != null) prefs.setProperty("cooldown.s",   cooldownSpinner.getValue().toString());
        try (OutputStream out = new FileOutputStream(PREFS_FILE)) {
            prefs.store(out, "MCXboxBroadcast GUI preferences");
        } catch (IOException ignored) {}
    }

    private void onClose() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Stop MCXboxBroadcast and exit the launcher?",
                "Confirm Exit", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        savePrefs();
        stopping.set(true);
        stopProcess(true);
        scheduler.shutdownNow();
        dispose();
        System.exit(0);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  STYLE HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(TEXT_MUTED);
        l.setBorder(new EmptyBorder(4, 0, 3, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel descLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        l.setForeground(new Color(0x6B7280));
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void styleTextField(JTextField f) {
        f.setBackground(BG_INPUT);
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(TEXT_PRIMARY);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B4252)),
                new EmptyBorder(4, 6, 4, 6)));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void styleSpinner(JSpinner s) {
        s.setBackground(BG_INPUT);
        JComponent editor = s.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(BG_INPUT);
            tf.setForeground(TEXT_PRIMARY);
            tf.setCaretColor(TEXT_PRIMARY);
        }
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void styleCheckbox(JCheckBox cb, boolean defaultVal) {
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cb.setForeground(TEXT_PRIMARY);
        cb.setBackground(BG_PANEL);
        cb.setSelected(defaultVal);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setBorder(new EmptyBorder(2, 0, 2, 0));
    }

    private JButton makeButton(String text, Color fg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setForeground(fg);
        b.setBackground(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 28));
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 110)),
                new EmptyBorder(5, 12, 5, 12)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);

        Color hoverColor = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60);
        Color normColor  = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 28);
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setBackground(hoverColor); }
            @Override public void mouseExited (MouseEvent e) { b.setBackground(normColor); }
        });
        return b;
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static boolean toBool(String s) {
        return "true".equalsIgnoreCase(s.trim());
    }

    private static void setSpinnerSafe(JSpinner spinner, String value) {
        try {
            int v = Integer.parseInt(value.trim());
            SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
            int min = ((Number) model.getMinimum()).intValue();
            int max = ((Number) model.getMaximum()).intValue();
            spinner.setValue(Math.max(min, Math.min(max, v)));
        } catch (Exception ignored) {}
    }
}
