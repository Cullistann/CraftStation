package ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import core.Lang;
import core.ServerManager;

public final class PlayersPanel extends McPanel {

    private final ServerManager server;
    private final DefaultListModel<String> playerListModel;
    private final JList<String> playerList;
    private final JLabel countLabel;
    private final JTextField searchField;
    private String searchFilter = "";
    private final Timer updateTimer;

    public PlayersPanel(String serverDir) {
        super(new BorderLayout(10, 10), false);
        this.server = ServerManager.getInstance(serverDir);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        McCard headerCard = new McCard(new BorderLayout(10, 0));
        headerCard.setBorder(McTheme.cardBorder());
        
        JLabel title = new JLabel(Lang.get("PLAYERS_TITLE"));
        title.setFont(McTheme.FONT_TITLE);
        title.setForeground(McTheme.TEXT_TITLE);
        countLabel = new JLabel("0 " + Lang.get("PLAYERS_COUNT"));
        countLabel.setFont(new Font("Inter", Font.BOLD, 14));
        countLabel.setForeground(McTheme.TEXT_SECONDARY);
        headerCard.add(title, BorderLayout.WEST);
        headerCard.add(countLabel, BorderLayout.EAST);
        add(headerCard, BorderLayout.NORTH);

        McCard centerCard = new McCard(new BorderLayout(0, 6));
        centerCard.setBorder(McTheme.cardBorder());

        // Search bar
        searchField = new JTextField();
        searchField.setColumns(18);
        searchField.setFont(new Font("Inter", Font.PLAIN, 14));
        searchField.setBackground(McTheme.INPUT_BG);
        searchField.setForeground(McTheme.TEXT_PRIMARY);
        searchField.setCaretColor(McTheme.TEXT_PRIMARY);
        searchField.setPreferredSize(new Dimension(0, 34));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(McTheme.BORDER, 1),
            new EmptyBorder(6, 10, 6, 10)
        ));
        searchField.putClientProperty("JTextField.placeholderText", Lang.get("SEARCH_PLAYER"));
        // Fallback placeholder via FocusListener
        searchField.setText(Lang.get("SEARCH_PLAYER"));
        searchField.setForeground(McTheme.TEXT_SECONDARY);
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (searchField.getText().equals(Lang.get("SEARCH_PLAYER"))) {
                    searchField.setText("");
                    searchField.setForeground(McTheme.TEXT_PRIMARY);
                }
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    searchField.setText(Lang.get("SEARCH_PLAYER"));
                    searchField.setForeground(McTheme.TEXT_SECONDARY);
                }
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
            private void applyFilter() {
                String text = searchField.getText();
                if (text.equals(Lang.get("SEARCH_PLAYER"))) {
                    searchFilter = "";
                } else {
                    searchFilter = text.toLowerCase(java.util.Locale.ROOT);
                }
                updatePlayerList();
            }
        });
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setOpaque(false);
        searchPanel.setBorder(new EmptyBorder(0, 0, 2, 0));
        searchPanel.add(searchField, BorderLayout.CENTER);
        centerCard.add(searchPanel, BorderLayout.NORTH);

        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        playerList.setFont(new Font("Inter", Font.PLAIN, 15));
        playerList.setBackground(McTheme.INPUT_BG);
        playerList.setForeground(McTheme.TEXT_PRIMARY);
        playerList.setSelectionBackground(new Color(74, 96, 118));
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playerList.setCellRenderer(new PlayerCellRenderer());

        JScrollPane scrollPane = new JScrollPane(playerList);
        McTheme.styleScroll(scrollPane);
        centerCard.add(scrollPane, BorderLayout.CENTER);
        add(centerCard, BorderLayout.CENTER);

        // Action buttons using McButton
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        actionPanel.setOpaque(false);
        actionPanel.setBorder(McTheme.sectionBorder());

        McButton kickBtn = new McButton(Lang.get("BTN_KICK"));
        McButton banBtn = new McButton(Lang.get("BTN_BAN"));
        McButton opBtn = new McButton(Lang.get("BTN_OP"));
        McButton deopBtn = new McButton(Lang.get("BTN_DEOP"));
        McButton whitelistBtn = new McButton(Lang.get("BTN_WHITELIST"));
        McButton refreshBtn = new McButton(Lang.get("BTN_REFRESH"));

        Dimension smallBtn = new Dimension(120, 32);
        Dimension medBtn = new Dimension(150, 32);
        kickBtn.setPreferredSize(smallBtn);
        banBtn.setPreferredSize(smallBtn);
        opBtn.setPreferredSize(smallBtn);
        deopBtn.setPreferredSize(smallBtn);
        whitelistBtn.setPreferredSize(medBtn);
        refreshBtn.setPreferredSize(smallBtn);

        kickBtn.addActionListener(e -> executeOnSelected("kick", Lang.get("PROMPT_KICK"), true));
        banBtn.addActionListener(e -> executeOnSelected("ban", Lang.get("PROMPT_BAN"), true));
        opBtn.addActionListener(e -> executeOnSelected("op", null, false));
        deopBtn.addActionListener(e -> executeOnSelected("deop", null, false));
        whitelistBtn.addActionListener(e -> executeOnSelected("whitelist add", null, false));
        refreshBtn.addActionListener(e -> server.sendCommand("list"));

        actionPanel.add(kickBtn);
        actionPanel.add(banBtn);
        actionPanel.add(opBtn);
        actionPanel.add(deopBtn);
        actionPanel.add(whitelistBtn);
        actionPanel.add(Box.createHorizontalStrut(10));
        actionPanel.add(refreshBtn);

        add(actionPanel, BorderLayout.SOUTH);

        // Update timer
        updateTimer = new Timer(3000, e -> updatePlayerList());
        updateTimer.start();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (updateTimer != null && !updateTimer.isRunning()) updateTimer.start();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (updateTimer != null) updateTimer.stop();
    }

    private void updatePlayerList() {
        var players = server.getOnlinePlayers();
        if (players == null) players = java.util.Collections.emptyList();
        // Apply search filter
        java.util.List<String> filtered = new java.util.ArrayList<>();
        for (String p : players) {
            if (searchFilter.isEmpty() || p.toLowerCase(java.util.Locale.ROOT).contains(searchFilter)) {
                filtered.add(p);
            }
        }
        boolean changed = false;
        if (filtered.size() != playerListModel.getSize()) {
            changed = true;
        } else {
            for (int i = 0; i < filtered.size(); i++) {
                if (!filtered.get(i).equals(playerListModel.getElementAt(i))) {
                    changed = true;
                    break;
                }
            }
        }
        if (changed) {
            String selected = playerList.getSelectedValue();
            playerListModel.clear();
            for (String p : filtered) {
                playerListModel.addElement(p);
            }
            if (selected != null) {
                playerList.setSelectedValue(selected, true);
            }
        }
        countLabel.setText(players.size() + " " + Lang.get("PLAYERS_COUNT"));
    }

    private void executeOnSelected(String command, String reasonPrompt, boolean hasReason) {
        String selected = playerList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, Lang.get("WARN_SELECT_PLAYER"), Lang.get("WARN_TITLE"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String reason = "";
        if (hasReason && reasonPrompt != null) {
            reason = JOptionPane.showInputDialog(this, reasonPrompt);
            if (reason == null) return;
        }

        String fullCmd = command + " " + selected;
        if (!reason.isEmpty()) fullCmd += " " + reason;

        server.sendCommand(fullCmd);
    }

    static class PlayerCellRenderer extends DefaultListCellRenderer {
        private final Icon steveIcon = PixelIcons.createSteveIcon(2);
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setIcon(steveIcon);
            setIconTextGap(10);
            setFont(new Font("Inter", Font.PLAIN, 15));
            setBorder(new EmptyBorder(6, 8, 6, 8));
            if (!isSelected) {
                setBackground(index % 2 == 0 ? new Color(29, 33, 38) : new Color(24, 28, 32));
                setForeground(McTheme.TEXT_PRIMARY);
            }
            return this;
        }
    }
}
