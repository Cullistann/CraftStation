package ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import core.Lang;
import core.ServerManager;

public final class ConsolePanel extends McPanel {

    private final ServerManager server;
    private final JTextPane logArea;
    private final JTextField commandField;
    private final StyledDocument doc;
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private boolean autoScroll = true;

    private final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private Timer batchTimer;
    private final java.util.function.Consumer<String> logListener = line -> logQueue.add(line);

    private final Style infoStyle;
    private final Style warnStyle;
    private final Style errorStyle;
    private final Style panelStyle;
    private final Style defaultStyle;

    public ConsolePanel(String serverDir) {
        super(new BorderLayout(0, 10), false);
        this.server = ServerManager.getInstance(serverDir);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        McCard mainCard = new McCard(new BorderLayout(0, 8));
        mainCard.setBorder(McTheme.cardBorder());

        logArea = new JTextPane();
        logArea.setEditable(false);
        logArea.setFont(new Font("Inter", Font.PLAIN, 14));
        logArea.setBackground(McTheme.INPUT_BG);
        logArea.setForeground(McTheme.TEXT_PRIMARY);
        logArea.setCaretColor(McTheme.TEXT_TITLE);

        doc = logArea.getStyledDocument();

        // Create styles
        defaultStyle = doc.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, McTheme.TEXT_PRIMARY);

        infoStyle = doc.addStyle("info", null);
        StyleConstants.setForeground(infoStyle, McTheme.TEXT_PRIMARY);

        warnStyle = doc.addStyle("warn", null);
        StyleConstants.setForeground(warnStyle, McTheme.WARNING);

        errorStyle = doc.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, McTheme.DANGER);
        StyleConstants.setBold(errorStyle, true);

        panelStyle = doc.addStyle("panel", null);
        StyleConstants.setForeground(panelStyle, McTheme.ACCENT);
        StyleConstants.setItalic(panelStyle, true);

        JScrollPane scrollPane = new JScrollPane(logArea);
        McTheme.styleScroll(scrollPane);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        topBar.setOpaque(false);
        topBar.setBorder(McTheme.sectionBorder());
        
        JCheckBox autoScrollCheck = new JCheckBox(Lang.get("CHECK_AUTOSCROLL"), true);
        autoScrollCheck.setFont(new Font("Inter", Font.BOLD, 12));
        autoScrollCheck.setForeground(McTheme.TEXT_PRIMARY);
        autoScrollCheck.setOpaque(false);
        autoScrollCheck.addActionListener(e -> autoScroll = autoScrollCheck.isSelected());
        
        McButton clearBtn = new McButton(Lang.get("BTN_CLEAR"));
        clearBtn.setPreferredSize(new Dimension(100, 30));
        clearBtn.setFont(new Font("Inter", Font.BOLD, 12));
        clearBtn.addActionListener(e -> {
            try { doc.remove(0, doc.getLength()); } catch (Exception ignored) {}
        });
        topBar.add(autoScrollCheck);
        topBar.add(clearBtn);

        mainCard.add(topBar, BorderLayout.NORTH);
        mainCard.add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setOpaque(false);
        inputPanel.setBorder(McTheme.sectionBorder());
        
        JLabel prompt = new JLabel(" > ");
        prompt.setFont(new Font("Inter", Font.BOLD, 14));
        prompt.setForeground(McTheme.SUCCESS);

        commandField = new JTextField();
        commandField.setFont(new Font("Inter", Font.PLAIN, 14));
        commandField.setBackground(McTheme.INPUT_BG);
        commandField.setForeground(McTheme.TEXT_PRIMARY);
        commandField.setCaretColor(McTheme.SUCCESS);
        commandField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(McTheme.BORDER, 1),
            new EmptyBorder(6, 8, 6, 8)
        ));

        McButton sendBtn = new McButton(Lang.get("BTN_SEND"));
        sendBtn.setFont(new Font("Inter", Font.BOLD, 13));
        sendBtn.setPreferredSize(new Dimension(100, 34));

        ActionListener sendAction = e -> {
            String cmd = commandField.getText().trim();
            if (!cmd.isEmpty()) {
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                server.sendCommand(cmd);
                commandHistory.add(0, cmd);
                if (commandHistory.size() > 100) commandHistory.remove(commandHistory.size() - 1);
                historyIndex = -1;
                commandField.setText("");
            }
        };

        commandField.addActionListener(sendAction);
        sendBtn.addActionListener(sendAction);

        commandField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (historyIndex < commandHistory.size() - 1) {
                        historyIndex++;
                        commandField.setText(commandHistory.get(historyIndex));
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (historyIndex > 0) {
                        historyIndex--;
                        commandField.setText(commandHistory.get(historyIndex));
                    } else {
                        historyIndex = -1;
                        commandField.setText("");
                    }
                }
            }
        });

        inputPanel.add(prompt, BorderLayout.WEST);
        inputPanel.add(commandField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        mainCard.add(inputPanel, BorderLayout.SOUTH);
        add(mainCard, BorderLayout.CENTER);

        server.addLogListener(logListener);

        batchTimer = new Timer(50, e -> processLogQueue());
        batchTimer.start();

        appendLog(Lang.get("LOG_PANEL_STARTED"));
        appendLog(Lang.get("LOG_PANEL_HINT"));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // C1 fix: tab'a geri dönünce log listener'ı tekrar kaydet
        server.addLogListener(logListener);
        if (batchTimer != null && !batchTimer.isRunning()) {
            batchTimer.start();
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (batchTimer != null) {
            batchTimer.stop();
        }
        server.removeLogListener(logListener);
    }

    private void appendLog(String line) {
        if (line != null) {
            logQueue.add(line);
        }
    }

    private void processLogQueue() {
        if (logQueue.isEmpty()) return;

        try {
            int count = 0;
            String line;
            while ((line = logQueue.poll()) != null && count < 200) { // Saniyede 4000 satıra kadar işleyebilir (200 x 20)
                Style style = defaultStyle;
                if (line.contains("[Panel]")) {
                    style = panelStyle;
                } else if (line.contains("ERROR") || line.contains("SEVERE") || line.contains("FATAL")) {
                    style = errorStyle;
                } else if (line.contains("WARN")) {
                    style = warnStyle;
                } else if (line.contains("INFO")) {
                    style = infoStyle;
                }
                doc.insertString(doc.getLength(), line + "\n", style);
                count++;
            }

            int lineCount = doc.getDefaultRootElement().getElementCount();
            if (lineCount > 5000) {
                int linesToRemove = lineCount - 4000;
                javax.swing.text.Element lineElement = doc.getDefaultRootElement().getElement(linesToRemove - 1);
                if (lineElement != null) {
                    doc.remove(0, lineElement.getEndOffset());
                }
            }

            if (autoScroll) {
                logArea.setCaretPosition(doc.getLength());
            }
        } catch (javax.swing.text.BadLocationException e) {
            // Document kayması
        } catch (Exception e) {
            System.err.println("[ConsolePanel] processLogQueue hatası: " + e.getMessage());
        }
    }
}
