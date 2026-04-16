import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/**
 * MCXboxBroadcast Windows GUI Launcher
 *
 * HOW TO USE:
 *   1. Place this .java file in the same folder as MCXboxBroadcastStandalone.jar
 *   2. Open Command Prompt in that folder
 *   3. Compile:  javac MCXboxBroadcastGUI.java
 *   4. Run:      java MCXboxBroadcastGUI
 *
 * Requires Java 11+  (same JRE used to run the standalone JAR)
 */
public class MCXboxBroadcastGUI extends JFrame {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color BG_DARK        = new Color(0x1A1D23);
    private static final Color BG_PANEL       = new Color(0x22262F);
    private static final Color BG_INPUT       = new Color(0x2A2F3A);
    private static final Color ACCENT_GREEN   = new Color(0x4CAF50);
    private static final Color ACCENT_YELLOW  = new Color(0xFFC107);
    private static final Color ACCENT_RED     = new Color(0xF44336);
    private static final Color ACCENT_BLUE    = new Color(0x42A5F5);
    private static final Color ACCENT_PURPLE  = new Color(0xAB47BC);
    private static final Color TEXT_PRIMARY   = new Color(0xECEFF4);
    private static final Color TEXT_MUTED     = new Color(0x8892A4);
    private static final Color TEXT_INFO      = new Color(0x88C0D0);
    private static final Color TEXT_WARN      = new Color(0xEBCB8B);
    private static final Color TEXT_ERROR     = new Color(0xBF616A);

    // ── UI components ─────────────────────────────────────────────────────────
    private JTextPane      logPane;
    private StyledDocument logDoc;
    private JTextField     cmdField;
    private JLabel         statusLabel;
    private JLabel         statusDot;
    private JButton        startStopBtn;
    private JButton        restartBtn;
    private JButton        openBrowserBtn;
    private JTextField     jarPathField;
    private JSpinner       heapSpinner;
    private JCheckBox      autoRestartCb;
    private JSpinner       cooldownSpinner;
    private JPanel         authBarRef;

    // ── Timed session restart ─────────────────────────────────────────────────
    private JCheckBox      timedRestartCb;
    private JComboBox<String> timedRestartCombo;
    private JLabel         timedRestartNextLabel;
    private ScheduledFuture<?> timedRestartFuture;
    private long           timedRestartNextEpoch = 0;
    private ScheduledFuture<?> timedRestartTickFuture;

    // ── Process management ────────────────────────────────────────────────────
    private Process     process;
    private PrintWriter processStdin;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final AtomicBoolean running  = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private ScheduledFuture<?> countdownFuture;

    // ── Auth detection ────────────────────────────────────────────────────────
    private static final Pattern AUTH_CODE_PATTERN =
        Pattern.compile("enter the code ([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private String lastAuthUrl = "https://www.microsoft.com/link";

    // ── Preferences ───────────────────────────────────────────────────────────
    private static final String PREFS_FILE = "mcxboxbroadcast-gui.properties";
    private final Properties prefs = new Properties();

    // ── Time formatter ────────────────────────────────────────────────────────
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Timed restart interval options (label → minutes) ──────────────────────
    private static final String[] TIMED_LABELS   = {"30 min","1 hour","2 hours","3 hours","4 hours","6 hours","8 hours","12 hours","24 hours"};
    private static final int[]    TIMED_MINUTES   = {    30,       60,      120,      180,      240,      360,      480,       720,      1440};

    // ════════════════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ════════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MCXboxBroadcastGUI().setVisible(true));
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ════════════════════════════════════════════════════════════════════════════
    public MCXboxBroadcastGUI() {
        super("MCXboxBroadcast — Windows Launcher");
        loadPrefs();
        buildUI();
        setSize(980, 700);
        setMinimumSize(new Dimension(740, 500));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onClose(); }
        });
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        // ── Top bar ───────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBackground(BG_PANEL);
        topBar.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel logo = new JLabel("MCXboxBroadcast  —  Windows Launcher");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        logo.setForeground(ACCENT_GREEN);
        topBar.add(logo, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        statusPanel.setOpaque(false);
        statusDot = new JLabel("●");
        statusDot.setFont(new Font("Segoe UI", Font.BOLD, 15));
        statusDot.setForeground(ACCENT_RED);
        statusLabel = new JLabel("Stopped");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(ACCENT_RED);
        statusPanel.add(statusDot);
        statusPanel.add(statusLabel);
        topBar.add(statusPanel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // ── Left sidebar ──────────────────────────────────────────────────────
        add(buildSidebar(), BorderLayout.WEST);

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

        // Command input row
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

        // ── Auth banner (hidden until detected) ───────────────────────────────
        openBrowserBtn = makeButton("  Open Microsoft Login Page  ", ACCENT_BLUE);
        openBrowserBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI(lastAuthUrl)); }
            catch (Exception ex) { appendLog("[GUI] Could not open browser: " + ex.getMessage(), TEXT_ERROR); }
        });

        JLabel authHint = new JLabel("  Authentication required — sign in with your Microsoft/Xbox account:");
        authHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        authHint.setForeground(ACCENT_BLUE);

        authBarRef = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        authBarRef.setBackground(new Color(0x0D1A2A));
        authBarRef.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ACCENT_BLUE));
        authBarRef.add(authHint);
        authBarRef.add(openBrowserBtn);
        authBarRef.setVisible(false);
        add(authBarRef, BorderLayout.SOUTH);

        // Welcome messages
        appendLog("[GUI] MCXboxBroadcast Launcher ready.", ACCENT_GREEN);
        appendLog("[GUI] Select your JAR file, configure settings, then click Start.", TEXT_MUTED);
    }

    private JPanel buildSidebar() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setBorder(new EmptyBorder(14, 12, 14, 12));
        p.setPreferredSize(new Dimension(230, 0));

        // ── JAR file ──────────────────────────────────────────────────────────
        p.add(sectionLabel("JAR FILE"));
        jarPathField = new JTextField(prefs.getProperty("jar.path", "MCXboxBroadcastStandalone.jar"));
        styleTextField(jarPathField);
        jarPathField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        jarPathField.setToolTipText("Full path to MCXboxBroadcastStandalone.jar");
        p.add(jarPathField);
        p.add(Box.createVerticalStrut(4));

        JButton browseBtn = makeButton("Browse...", TEXT_MUTED);
        browseBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        browseBtn.addActionListener(e -> browseJar());
        p.add(browseBtn);

        // ── Heap ──────────────────────────────────────────────────────────────
        p.add(Box.createVerticalStrut(12));
        p.add(sectionLabel("MAX HEAP (MB)"));
        int heap = parseIntSafe(prefs.getProperty("heap.mb", "256"), 256);
        heapSpinner = new JSpinner(new SpinnerNumberModel(heap, 64, 4096, 64));
        styleSpinner(heapSpinner);
        heapSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(heapSpinner);

        // ── Crash auto-restart ────────────────────────────────────────────────
        p.add(Box.createVerticalStrut(12));
        p.add(sectionLabel("CRASH AUTO-RESTART"));
        autoRestartCb = new JCheckBox("Restart on crash / exit");
        autoRestartCb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        autoRestartCb.setForeground(TEXT_PRIMARY);
        autoRestartCb.setBackground(BG_PANEL);
        autoRestartCb.setSelected(Boolean.parseBoolean(prefs.getProperty("auto.restart", "true")));
        autoRestartCb.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(autoRestartCb);

        p.add(Box.createVerticalStrut(6));
        p.add(sectionLabel("CRASH COOLDOWN (seconds)"));
        int cooldown = parseIntSafe(prefs.getProperty("cooldown.s", "30"), 30);
        cooldownSpinner = new JSpinner(new SpinnerNumberModel(cooldown, 5, 300, 5));
        styleSpinner(cooldownSpinner);
        cooldownSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(cooldownSpinner);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // ── TIMED SESSION RESTART ─────────────────────────────────────────────
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        p.add(Box.createVerticalStrut(14));

        // Divider line
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0x3B4252));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        p.add(sep);
        p.add(Box.createVerticalStrut(10));

        p.add(sectionLabel("TIMED SESSION RESTART"));

        timedRestartCb = new JCheckBox("Auto-restart session every:");
        timedRestartCb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        timedRestartCb.setForeground(ACCENT_PURPLE);
        timedRestartCb.setBackground(BG_PANEL);
        timedRestartCb.setSelected(Boolean.parseBoolean(prefs.getProperty("timed.restart.enabled", "false")));
        timedRestartCb.setAlignmentX(Component.LEFT_ALIGNMENT);
        timedRestartCb.setToolTipText("Sends 'restart' to the JAR on a fixed schedule to keep the Xbox Live session fresh");
        p.add(timedRestartCb);

        p.add(Box.createVerticalStrut(5));

        // Interval combo
        timedRestartCombo = new JComboBox<>(TIMED_LABELS);
        timedRestartCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        timedRestartCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        timedRestartCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Custom renderer so text is always visible regardless of Windows LAF
        timedRestartCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                lbl.setBorder(new EmptyBorder(3, 8, 3, 8));
                if (isSelected) {
                    lbl.setBackground(new Color(0x3B4252));
                    lbl.setForeground(TEXT_PRIMARY);
                } else {
                    lbl.setBackground(BG_INPUT);
                    lbl.setForeground(TEXT_PRIMARY);
                }
                return lbl;
            }
        });
        // Style the combo button area itself
        timedRestartCombo.setBackground(BG_INPUT);
        timedRestartCombo.setForeground(TEXT_PRIMARY);
        timedRestartCombo.setBorder(BorderFactory.createLineBorder(new Color(0x3B4252)));
        for (int ci = 0; ci < timedRestartCombo.getComponentCount(); ci++) {
            Component comp = timedRestartCombo.getComponent(ci);
            comp.setBackground(BG_INPUT);
            comp.setForeground(TEXT_PRIMARY);
        }

        // Restore saved selection
        int savedTimedIdx = parseIntSafe(prefs.getProperty("timed.restart.idx", "1"), 1);
        if (savedTimedIdx >= 0 && savedTimedIdx < TIMED_LABELS.length)
            timedRestartCombo.setSelectedIndex(savedTimedIdx);

        // Rearm the timer whenever the user changes the interval while running
        timedRestartCombo.addActionListener(e -> {
            if (timedRestartCb.isSelected() && running.get()) armTimedRestart();
        });
        timedRestartCb.addActionListener(e -> {
            if (timedRestartCb.isSelected() && running.get()) armTimedRestart();
            else cancelTimedRestart();
        });

        p.add(timedRestartCombo);

        p.add(Box.createVerticalStrut(5));

        // Countdown label — shows "Next restart in X h Ym"
        timedRestartNextLabel = new JLabel("Next restart: —");
        timedRestartNextLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timedRestartNextLabel.setForeground(TEXT_MUTED);
        timedRestartNextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(timedRestartNextLabel);

        // Divider line
        p.add(Box.createVerticalStrut(10));
        JSeparator sep2 = new JSeparator();
        sep2.setForeground(new Color(0x3B4252));
        sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        p.add(sep2);
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        // ── Controls ──────────────────────────────────────────────────────────
        p.add(Box.createVerticalStrut(12));
        p.add(sectionLabel("CONTROLS"));

        startStopBtn = makeButton("▶   Start", ACCENT_GREEN);
        startStopBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        startStopBtn.addActionListener(e -> toggleStartStop());
        p.add(startStopBtn);
        p.add(Box.createVerticalStrut(6));

        restartBtn = makeButton("↺   Restart Session Now", ACCENT_YELLOW);
        restartBtn.setEnabled(false);
        restartBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        restartBtn.addActionListener(e -> doRestartNow());
        p.add(restartBtn);

        p.add(Box.createVerticalGlue());

        JLabel footer = new JLabel("MCXboxBroadcast GUI Launcher");
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        footer.setForeground(TEXT_MUTED);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(footer);

        return p;
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  TIMED RESTART LOGIC
    // ════════════════════════════════════════════════════════════════════════════

    /** Arms (or re-arms) the periodic session-restart timer based on current combo selection. */
    private void armTimedRestart() {
        cancelTimedRestart();                           // cancel any existing timer first

        int idx     = timedRestartCombo.getSelectedIndex();
        int minutes = TIMED_MINUTES[Math.max(0, idx)];
        long delayMs = (long) minutes * 60 * 1000;

        timedRestartNextEpoch = System.currentTimeMillis() + delayMs;

        appendLog("[GUI] Timed session restart armed — every " + TIMED_LABELS[idx]
                + " (first restart in " + TIMED_LABELS[idx] + ")", ACCENT_PURPLE);

        // Fire the actual restart at interval
        timedRestartFuture = scheduler.scheduleAtFixedRate(() -> {
            if (!running.get() || stopping.get()) return;
            appendLog("[GUI] ⏰ Timed session restart firing now (" + TIMED_LABELS[idx] + " interval)", ACCENT_PURPLE);
            doSessionRestartCommand();
            // reset the countdown epoch for the NEXT cycle
            timedRestartNextEpoch = System.currentTimeMillis() + delayMs;
        }, delayMs, delayMs, TimeUnit.MILLISECONDS);

        // Tick every 10 s to update the "Next restart in …" label
        timedRestartTickFuture = scheduler.scheduleAtFixedRate(
            this::updateTimedRestartLabel, 0, 10, TimeUnit.SECONDS);
    }

    /** Cancels the timed restart timer and clears the label. */
    private void cancelTimedRestart() {
        if (timedRestartFuture     != null) { timedRestartFuture.cancel(false);     timedRestartFuture = null; }
        if (timedRestartTickFuture != null) { timedRestartTickFuture.cancel(false); timedRestartTickFuture = null; }
        timedRestartNextEpoch = 0;
        SwingUtilities.invokeLater(() -> timedRestartNextLabel.setText("Next restart: —"));
    }

    /** Updates the sidebar countdown label. Called every 10 s from tick future. */
    private void updateTimedRestartLabel() {
        if (timedRestartNextEpoch == 0) return;
        long rem0 = timedRestartNextEpoch - System.currentTimeMillis();
        if (rem0 < 0) rem0 = 0;
        final long hh = rem0 / 3_600_000L;
        final long mm = (rem0 % 3_600_000L) / 60_000L;
        final String txt = hh > 0
            ? String.format("Next restart in %dh %02dm", hh, mm)
            : String.format("Next restart in %dm", mm);
        final Color col = rem0 < 120_000L ? ACCENT_YELLOW : TEXT_MUTED;
        SwingUtilities.invokeLater(() -> {
            timedRestartNextLabel.setText(txt);
            timedRestartNextLabel.setForeground(col);
        });
    }

    /**
     * Sends "restart" to the JAR stdin — triggers SessionManager.restart()
     * inside the JVM, refreshing the Xbox Live session without killing the process.
     * Used by both the manual button AND the timed scheduler.
     */
    private void doSessionRestartCommand() {
        if (!running.get()) return;
        if (processStdin != null) processStdin.println("restart");
        SwingUtilities.invokeLater(() -> setStatus("Restarting session...", ACCENT_YELLOW));
        // After 8 s revert status to Running (or re-launch if process died)
        scheduler.schedule(() -> {
            if (!running.get() && !stopping.get()) {
                SwingUtilities.invokeLater(this::startProcess);
            } else {
                SwingUtilities.invokeLater(() -> setStatus("Running", ACCENT_GREEN));
            }
        }, 8, TimeUnit.SECONDS);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  PROCESS LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════════

    private void startProcess() {
        if (running.get()) return;

        String jar    = jarPathField.getText().trim();
        File   jarFile = new File(jar);

        if (!jarFile.exists()) {
            appendLog("[GUI] ERROR: JAR not found at: " + jar, TEXT_ERROR);
            appendLog("[GUI] Click Browse... to locate MCXboxBroadcastStandalone.jar", TEXT_WARN);
            return;
        }

        int heapMb = (int) heapSpinner.getValue();
        stopping.set(false);
        running.set(true);
        setStatus("Starting...", ACCENT_YELLOW);

        SwingUtilities.invokeLater(() -> {
            if (authBarRef != null) authBarRef.setVisible(false);
        });

        File workDir = jarFile.getParentFile() != null ? jarFile.getParentFile() : new File(".");

        ProcessBuilder pb = new ProcessBuilder(
            "java", "-Xms64m", "-Xmx" + heapMb + "m", "-jar", jarFile.getAbsolutePath()
        );
        pb.directory(workDir);
        pb.redirectErrorStream(true);

        appendLog("[GUI] Working dir: " + workDir.getAbsolutePath(), TEXT_MUTED);
        appendLog("[GUI] Command: java -Xmx" + heapMb + "m -jar " + jarFile.getName(), TEXT_MUTED);

        scheduler.submit(() -> {
            try {
                process = pb.start();
                processStdin = new PrintWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);

                SwingUtilities.invokeLater(() -> {
                    startStopBtn.setText("■   Stop");
                    restartBtn.setEnabled(true);
                    jarPathField.setEnabled(false);
                    heapSpinner.setEnabled(false);
                });

                // Stream all output
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processLine(line);
                    }
                }

                int exitCode = process.waitFor();
                running.set(false);

                // Cancel timed restart timer when process stops
                cancelTimedRestart();

                SwingUtilities.invokeLater(() -> {
                    appendLog("[GUI] Process exited with code " + exitCode, TEXT_MUTED);
                    startStopBtn.setText("▶   Start");
                    restartBtn.setEnabled(false);
                    jarPathField.setEnabled(true);
                    heapSpinner.setEnabled(true);
                    if (authBarRef != null) authBarRef.setVisible(false);
                    setStatus("Stopped", ACCENT_RED);
                });

                // Crash auto-restart watchdog
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

    private void stopProcess(boolean userInitiated) {
        if (!running.get()) return;
        if (userInitiated) stopping.set(true);
        cancelTimedRestart();
        if (countdownFuture != null) { countdownFuture.cancel(false); countdownFuture = null; }
        if (processStdin != null) processStdin.println("exit");
        scheduler.schedule(() -> {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }, 4, TimeUnit.SECONDS);
    }

    /** Manual restart button — also resets the timed restart countdown. */
    private void doRestartNow() {
        if (!running.get()) { startProcess(); return; }
        appendLog("[GUI] Manual restart triggered...", ACCENT_YELLOW);
        doSessionRestartCommand();
        // Reset the timed restart clock so the next timed restart is a full interval away
        if (timedRestartCb.isSelected()) armTimedRestart();
    }

    private void scheduleAutoRestart(int delaySecs) {
        appendLog("[GUI] Auto-restart watchdog — restarting in " + delaySecs + " s...", ACCENT_YELLOW);
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

    // ════════════════════════════════════════════════════════════════════════════
    //  LOG PROCESSING
    // ════════════════════════════════════════════════════════════════════════════

    private static final Pattern ANSI_PATTERN =
        Pattern.compile("\u001B\\[[;\\d]*[A-Za-z]|\\[\\d+[;m][\\d;]*m?");

    private void processLine(String raw) {
        String clean = ANSI_PATTERN.matcher(raw).replaceAll("").trim();
        if (clean.isEmpty()) return;

        Color  color = TEXT_PRIMARY;
        String lower = clean.toLowerCase();

        if (lower.contains("[error]") || lower.contains("exception") || lower.contains("failed"))
            color = TEXT_ERROR;
        else if (lower.contains("[warn]") || lower.contains("warning"))
            color = TEXT_WARN;
        else if (lower.contains("[info]"))
            color = TEXT_INFO;

        if (lower.contains("creation of xbox live session was successful")
                || lower.contains("created session")
                || lower.contains("session created")) {
            color = ACCENT_GREEN;
            SwingUtilities.invokeLater(() -> {
                setStatus("Running", ACCENT_GREEN);
                // Arm timed restart once session is confirmed live
                if (timedRestartCb.isSelected() && timedRestartFuture == null) {
                    armTimedRestart();
                }
            });
        }

        if (lower.contains("updated session")) {
            color = ACCENT_GREEN;
        }

        if (lower.contains("microsoft.com/link")) {
            Matcher m = AUTH_CODE_PATTERN.matcher(clean);
            String code = m.find() ? "  Code: " + m.group(1) : "";
            appendLog("[GUI] Auth required" + code + "  ->  Click the button below", ACCENT_BLUE);
            SwingUtilities.invokeLater(() -> {
                if (authBarRef != null) authBarRef.setVisible(true);
            });
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
            appendLog("[GUI] Process is not running — start it first.", TEXT_WARN);
            return;
        }
        appendLog("> " + cmd, ACCENT_GREEN);
        if (processStdin != null) processStdin.println(cmd);
        cmdField.setText("");
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  STATUS
    // ════════════════════════════════════════════════════════════════════════════

    private void setStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusDot.setForeground(color);
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        });
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  FILE CHOOSER
    // ════════════════════════════════════════════════════════════════════════════

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
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  PREFERENCES
    // ════════════════════════════════════════════════════════════════════════════

    private void loadPrefs() {
        try (InputStream in = new FileInputStream(PREFS_FILE)) { prefs.load(in); }
        catch (IOException ignored) {}
    }

    private void savePrefs() {
        if (jarPathField       != null) prefs.setProperty("jar.path",              jarPathField.getText());
        if (heapSpinner        != null) prefs.setProperty("heap.mb",               heapSpinner.getValue().toString());
        if (autoRestartCb      != null) prefs.setProperty("auto.restart",          Boolean.toString(autoRestartCb.isSelected()));
        if (cooldownSpinner    != null) prefs.setProperty("cooldown.s",            cooldownSpinner.getValue().toString());
        if (timedRestartCb     != null) prefs.setProperty("timed.restart.enabled", Boolean.toString(timedRestartCb.isSelected()));
        if (timedRestartCombo  != null) prefs.setProperty("timed.restart.idx",     String.valueOf(timedRestartCombo.getSelectedIndex()));
        try (OutputStream out = new FileOutputStream(PREFS_FILE)) {
            prefs.store(out, "MCXboxBroadcast GUI preferences");
        } catch (IOException ignored) {}
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  CLOSE
    // ════════════════════════════════════════════════════════════════════════════

    private void onClose() {
        int choice = JOptionPane.showConfirmDialog(this,
            "Stop MCXboxBroadcast and exit the launcher?",
            "Confirm Exit", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        savePrefs();
        stopping.set(true);
        cancelTimedRestart();
        stopProcess(true);
        scheduler.shutdownNow();
        dispose();
        System.exit(0);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  STYLE HELPERS
    // ════════════════════════════════════════════════════════════════════════════

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(TEXT_MUTED);
        l.setBorder(new EmptyBorder(4, 0, 3, 0));
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
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }
}
