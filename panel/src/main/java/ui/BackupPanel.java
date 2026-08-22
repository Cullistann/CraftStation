package ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import core.Lang;
import core.BackupManager;
import core.ServerManager;
import core.IServerManager;
import core.IBackupStrategy;

public final class BackupPanel extends McPanel {

    private final BackupManager backupManager;
    private final ServerManager server;
    private final DefaultListModel<IBackupStrategy.BackupEntry> backupListModel;
    private final JLabel statusLabel;
    private volatile boolean backupInProgress = false;

    public BackupPanel(String serverDir) {
        super(new BorderLayout(10, 10), false);
        this.backupManager = BackupManager.getInstance(serverDir);
        this.server = ServerManager.getInstance(serverDir);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        McCard headerCard = new McCard(new BorderLayout(10, 0));
        headerCard.setBorder(McTheme.cardBorder());

        JLabel title = new JLabel(Lang.get("BACKUP_TITLE"));
        title.setFont(McTheme.FONT_TITLE);
        title.setForeground(McTheme.TEXT_TITLE);
        headerCard.add(title, BorderLayout.WEST);

        McButton createBtn = new McButton(Lang.get("BTN_CREATE_BACKUP"));
        createBtn.setPreferredSize(new Dimension(200, 36));
        createBtn.setFont(new Font("Inter", Font.BOLD, 13));
        createBtn.addActionListener(e -> createBackup());
        headerCard.add(createBtn, BorderLayout.EAST);

        add(headerCard, BorderLayout.NORTH);

        McCard centerCard = new McCard(new BorderLayout());
        centerCard.setBorder(McTheme.cardBorder());
        backupListModel = new DefaultListModel<>();
        JList<IBackupStrategy.BackupEntry> backupList = new JList<>(backupListModel) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getModel().getSize() == 0) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setFont(new Font("Inter", Font.BOLD, 18));
                    g2.setColor(McTheme.TEXT_SECONDARY);
                    String text = "📭 " + Lang.get("NO_BACKUPS_YET");
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(text)) / 2;
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(text, x, y);
                    g2.dispose();
                }
            }
        };
        backupList.setFont(new Font("Inter", Font.PLAIN, 14));
        backupList.setBackground(McTheme.INPUT_BG);
        backupList.setForeground(McTheme.TEXT_PRIMARY);
        backupList.setSelectionBackground(new Color(74, 96, 118));
        backupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        backupList.setCellRenderer(new BackupCellRenderer());

        JScrollPane scrollPane = new JScrollPane(backupList);
        McTheme.styleScroll(scrollPane);
        centerCard.add(scrollPane, BorderLayout.CENTER);
        add(centerCard, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(McTheme.sectionBorder());

        statusLabel = new JLabel("  ");
        statusLabel.setFont(new Font("Inter", Font.ITALIC, 12));
        statusLabel.setForeground(McTheme.TEXT_SECONDARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        btnPanel.setOpaque(false);

        McButton deleteBtn = new McButton(Lang.get("BTN_DELETE"));
        deleteBtn.setPreferredSize(new Dimension(100, 32));
        deleteBtn.setFont(new Font("Inter", Font.BOLD, 12));
        deleteBtn.addActionListener(e -> {
            IBackupStrategy.BackupEntry selected = backupList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, Lang.get("WARN_SELECT_BACKUP"), Lang.get("WARN_TITLE"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            int c = JOptionPane.showConfirmDialog(this, Lang.get("WARN_DELETE_BACKUP") + "\n" + selected.fileName(),
                    Lang.get("DELETE_CONFIRM_TITLE"), JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                try {
                    backupManager.deleteBackup(selected);
                    refreshListAsync(false);
                    statusLabel.setText("  " + Lang.get("MSG_BACKUP_DELETED"));
                    statusLabel.setForeground(McTheme.WARNING);
                } catch (IOException ex) {
                    statusLabel.setText("  " + Lang.get("MSG_BACKUP_ERROR") + " " + ex.getMessage());
                    statusLabel.setForeground(McTheme.DANGER);
                }
            }
        });
        McButton restoreBtn = new McButton(Lang.get("BTN_RESTORE_BACKUP"));
        restoreBtn.setPreferredSize(new Dimension(120, 32));
        restoreBtn.setFont(new Font("Inter", Font.BOLD, 12));

        McButton refreshBtn = new McButton(Lang.get("BTN_REFRESH"));
        refreshBtn.setPreferredSize(new Dimension(110, 32));
        refreshBtn.setFont(new Font("Inter", Font.BOLD, 12));
        refreshBtn.addActionListener(e -> refreshListAsync(true));

        restoreBtn.addActionListener(e -> {
            IBackupStrategy.BackupEntry selected = backupList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, Lang.get("WARN_SELECT_BACKUP"), Lang.get("WARN_TITLE"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server.getStatus() != IServerManager.Status.STOPPED) {
                JOptionPane.showMessageDialog(this, Lang.get("WARN_RESTORE_RUNNING"), Lang.get("WARN_TITLE"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            int c = JOptionPane.showConfirmDialog(this, Lang.get("WARN_RESTORE_CONFIRM") + "\n" + selected.fileName(),
                    Lang.get("CONFIRM_TITLE"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (c == JOptionPane.YES_OPTION) {
                statusLabel.setText("  " + Lang.get("MSG_RESTORING"));
                statusLabel.setForeground(McTheme.WARNING);
                restoreBtn.setEnabled(false);
                deleteBtn.setEnabled(false);
                refreshBtn.setEnabled(false);

                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        backupManager.restoreBackup(selected);
                        return null;
                    }

                    @Override
                    protected void done() {
                        restoreBtn.setEnabled(true);
                        deleteBtn.setEnabled(true);
                        refreshBtn.setEnabled(true);
                        try {
                            get();
                            statusLabel.setText("  " + Lang.get("MSG_RESTORE_SUCCESS"));
                            statusLabel.setForeground(McTheme.SUCCESS);
                        } catch (Exception ex) {
                            statusLabel.setText("  " + Lang.get("MSG_RESTORE_ERROR") + " " + ex.getMessage());
                            statusLabel.setForeground(McTheme.DANGER);
                        }
                    }
                };
                worker.execute();
            }
        });

        btnPanel.add(deleteBtn);
        btnPanel.add(restoreBtn);
        btnPanel.add(refreshBtn);
        bottomPanel.add(btnPanel, BorderLayout.WEST);

        bottomPanel.add(statusLabel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        refreshListAsync(false);
    }

    private void createBackup() {
        if (backupInProgress) return;

        if (server.getStatus() == IServerManager.Status.RUNNING) {
            int c = JOptionPane.showConfirmDialog(this,
                    Lang.get("WARN_BACKUP_RUNNING"),
                    Lang.get("WARN_TITLE"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (c != JOptionPane.YES_OPTION)
                return;
        }

        backupInProgress = true;
        statusLabel.setText("  " + Lang.get("MSG_BACKUP_CREATING"));
        statusLabel.setForeground(McTheme.WARNING);

        SwingWorker<IBackupStrategy.BackupEntry, Void> worker = new SwingWorker<>() {
            @Override
            protected IBackupStrategy.BackupEntry doInBackground() throws Exception {
                return backupManager.createBackup();
            }

            @Override
            protected void done() {
                backupInProgress = false;
                try {
                    IBackupStrategy.BackupEntry entry = get();
                    statusLabel.setText("  " + Lang.get("MSG_BACKUP_SUCCESS") + " " + entry.fileName());
                    statusLabel.setForeground(McTheme.SUCCESS);
                    refreshListAsync(false);
                } catch (Exception e) {
                    statusLabel.setText("  " + Lang.get("MSG_BACKUP_ERROR") + " " + e.getMessage());
                    statusLabel.setForeground(McTheme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void refreshListAsync(boolean isManual) {
        if (isManual) {
            statusLabel.setText("  " + Lang.get("MSG_REFRESHING"));
            statusLabel.setForeground(McTheme.TEXT_SECONDARY);
        }

        SwingWorker<List<IBackupStrategy.BackupEntry>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<IBackupStrategy.BackupEntry> doInBackground() throws Exception {
                return backupManager.listBackups();
            }

            @Override
            protected void done() {
                try {
                    List<IBackupStrategy.BackupEntry> backups = get();
                    backupListModel.clear();
                    for (IBackupStrategy.BackupEntry backup : backups) {
                        backupListModel.addElement(backup);
                    }
                    if (isManual) {
                        statusLabel.setText("  ");
                    }
                } catch (Exception e) {
                    statusLabel.setText("  " + Lang.get("MSG_ERROR") + ": " + e.getMessage());
                    statusLabel.setForeground(McTheme.DANGER);
                }
            }
        };
        worker.execute();
    }

    static class BackupCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof IBackupStrategy.BackupEntry) {
                IBackupStrategy.BackupEntry entry = (IBackupStrategy.BackupEntry) value;
                String displayText = String.format("📦  %s (%s, %s)",
                        entry.fileName(),
                        entry.getFormattedSize(),
                        entry.getFormattedDate());
                setText("  " + displayText);
                setForeground(McTheme.TEXT_PRIMARY);
            } else {
                setText("  " + value.toString());
                setForeground(McTheme.TEXT_PRIMARY);
            }

            setFont(new Font("Inter", Font.PLAIN, 14));
            setBorder(new EmptyBorder(6, 8, 6, 8));
            if (!isSelected) {
                setBackground(index % 2 == 0 ? new Color(29, 33, 38) : new Color(24, 28, 33));
            }
            return this;
        }
    }
}
