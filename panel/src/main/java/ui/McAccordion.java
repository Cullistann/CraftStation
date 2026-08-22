package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class McAccordion extends JPanel {

    private final JPanel contentPanel;
    private final JLabel iconLabel;
    private boolean isExpanded = false;

    public McAccordion(String title, JComponent content) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(McTheme.cardBorder());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(42, 46, 51));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 15));
        titleLabel.setForeground(McTheme.TEXT_TITLE);
        
        iconLabel = new JLabel("▼");
        iconLabel.setFont(new Font("Inter", Font.BOLD, 12));
        iconLabel.setForeground(McTheme.TEXT_SECONDARY);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(iconLabel, BorderLayout.EAST);

        // Content
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        contentPanel.add(content, BorderLayout.CENTER);
        contentPanel.setVisible(false);

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                header.setBackground(new Color(50, 55, 60));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                header.setBackground(new Color(42, 46, 51));
            }
        });
    }

    public void toggle() {
        isExpanded = !isExpanded;
        contentPanel.setVisible(isExpanded);
        iconLabel.setText(isExpanded ? "▲" : "▼");
        
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
    }

    public void setExpanded(boolean expanded) {
        if (this.isExpanded != expanded) {
            toggle();
        }
    }
}
