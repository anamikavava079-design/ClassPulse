package classpulse.client;

import classpulse.common.Message.CloseRoom;
import classpulse.common.Message.KickPlayer;
import classpulse.common.Message.RoomCreated;
import classpulse.common.Message.StartGame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** The teacher's waiting room: shows the code to project, and who has joined. */
final class LobbyPanel extends JPanel {
    private final JLabel titleLabel = Widgets.pageTitle(" ");
    private final JLabel detailLabel = Widgets.pageSubtitle(" ");
    final JLabel codeLabel = Theme.label(" ", Font.BOLD, 76, Color.WHITE);
    private final JLabel countLabel = Theme.label("0 students joined", Font.BOLD, 17, Theme.INK);
    private final DefaultListModel<String> players = new DefaultListModel<>();
    final JList<String> playerList = new JList<>(players);
    final JButton startButton = Theme.withIcon(Theme.primary("Start quiz"), Icons.Kind.PLAY);
    final JButton removeButton = Theme.withIcon(Theme.danger("Remove selected"), Icons.Kind.CROSS);
    final QrView qrView = new QrView(150);
    private final JLabel phoneUrl = Theme.label(" ", Font.BOLD, 13, Color.WHITE);
    private final JPanel phoneRow = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 16, 0));
    /** Copies the room code, so the teacher can paste it into a chat or an email. */
    final JButton copyCodeButton = Theme.withIcon(Theme.soft("Copy code"), Icons.Kind.COPY);
    final JButton copyLinkButton = new JButton("Copy link");
    private String phoneLink;

    LobbyPanel(AppFrame app) {
        setLayout(new BorderLayout(0, 20));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(28, 32, 26, 32));
        add(Widgets.header(titleLabel, detailLabel), BorderLayout.NORTH);

        JPanel center = Theme.transparent(new GridLayout(1, 2, 20, 0));
        JPanel codeCard = new Theme.RoundPanel(Theme.PRIMARY, 24, true) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                Theme.smooth(g);
                g.setPaint(new GradientPaint(0, 0, new Color(0x4338CA), getWidth(), getHeight(), new Color(0x7C3AED)));
                g.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 4, 24, 24);
                g.setColor(new Color(255, 255, 255, 18));
                g.fillOval(getWidth() - 160, -80, 260, 260);
                g.dispose();
            }
        };
        codeCard.setLayout(new BorderLayout());
        codeCard.setBorder(BorderFactory.createEmptyBorder(26, 28, 26, 28));
        JPanel codeTop = Theme.transparent(new BorderLayout());
        codeTop.add(Theme.label("ROOM CODE", Font.BOLD, 14, new Color(255, 255, 255, 200)), BorderLayout.WEST);
        copyCodeButton.setToolTipText("Copy the room code to paste it anywhere");
        codeTop.add(copyCodeButton, BorderLayout.EAST);
        codeCard.add(codeTop, BorderLayout.NORTH);
        codeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        // Letter spacing makes the code easy to read from the back of the room.
        codeLabel.setFont(codeLabel.getFont().deriveFont(java.util.Map.of(
                java.awt.font.TextAttribute.TRACKING, 0.12f)));
        codeCard.add(codeLabel, BorderLayout.CENTER);
        JLabel how = Theme.label("Students: open ClassPulse, choose Join a quiz, and type this code.", Font.PLAIN, 14,
                new Color(255, 255, 255, 220));
        how.setHorizontalAlignment(SwingConstants.CENTER);
        // Phone play (added in the phone upgrade): hidden until the server sends a link.
        JPanel phoneText = Theme.transparent(new GridLayout(4, 1, 0, 2));
        phoneText.add(Theme.label("No app? Scan with a phone", Font.BOLD, 16, Color.WHITE));
        phoneText.add(Theme.label("Same Wi-Fi as this computer, then open:", Font.PLAIN, 13, new Color(255, 255, 255, 210)));
        phoneText.add(phoneUrl);
        // A white, underlined text button that fits on the purple card.
        copyLinkButton.setFont(Theme.font(Font.BOLD, 13));
        copyLinkButton.setForeground(new Color(0xE0E7FF));
        copyLinkButton.setIcon(Icons.of(Icons.Kind.COPY, 13, new Color(0xE0E7FF)));
        copyLinkButton.setIconTextGap(6);
        copyLinkButton.setContentAreaFilled(false);
        copyLinkButton.setBorderPainted(false);
        copyLinkButton.setFocusPainted(false);
        copyLinkButton.setOpaque(false);
        copyLinkButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        copyLinkButton.setHorizontalAlignment(SwingConstants.LEFT);
        copyLinkButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        copyLinkButton.setToolTipText("Copy the phone link to share it in a chat");
        phoneText.add(copyLinkButton);
        phoneRow.add(qrView);
        phoneRow.add(phoneText);
        phoneRow.setVisible(false);
        JPanel south = Theme.transparent(new BorderLayout(0, 12));
        south.add(phoneRow, BorderLayout.CENTER);
        south.add(how, BorderLayout.SOUTH);
        codeCard.add(south, BorderLayout.SOUTH);
        center.add(codeCard);

        JPanel playerCard = Theme.card();
        playerCard.setLayout(new BorderLayout(0, 12));
        countLabel.setIcon(Icons.of(Icons.Kind.USERS, 20, Theme.PRIMARY));
        countLabel.setIconTextGap(10);
        playerCard.add(countLabel, BorderLayout.NORTH);
        playerList.setFont(Theme.font(Font.PLAIN, 15));
        playerList.setFixedCellHeight(42);
        playerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, false);
                setIcon(Theme.avatar(String.valueOf(value), 28));
                setIconTextGap(12);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                setBackground(selected ? Theme.PRIMARY_SOFT : Color.WHITE);
                setForeground(Theme.INK);
                return this;
            }
        });
        playerCard.add(Theme.scroll(playerList), BorderLayout.CENTER);
        JPanel remove = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        remove.add(removeButton);
        playerCard.add(remove, BorderLayout.SOUTH);
        center.add(playerCard);
        add(center, BorderLayout.CENTER);

        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton close = Theme.secondary("Close room");
        buttons.add(close);
        buttons.add(Theme.height(startButton, 48));
        add(buttons, BorderLayout.SOUTH);

        copyCodeButton.addActionListener(e -> Theme.copyWithFeedback(copyCodeButton, code(), app, "Room code " + code()));
        copyLinkButton.addActionListener(e -> {
            if (phoneLink != null) {
                Theme.copyWithFeedback(copyLinkButton, phoneLink, app, "Phone link");
            }
        });
        startButton.addActionListener(e -> app.send(new StartGame()));
        close.addActionListener(e -> app.send(new CloseRoom()));
        removeButton.addActionListener(e -> {
            String name = playerList.getSelectedValue();
            if (name != null) {
                app.send(new KickPlayer(name));
            }
        });
        playerList.addListSelectionListener(e -> removeButton.setEnabled(playerList.getSelectedValue() != null));
    }

    @Override
    protected void paintComponent(java.awt.Graphics graphics) {
        Theme.paintBackdrop(this, graphics);
    }

    void open(RoomCreated room) {
        titleLabel.setText(room.title());
        detailLabel.setText(room.questionCount() + " questions" + (room.shuffled() ? "   |   shuffled order" : "")
                + "   |   Waiting for students to join");
        codeLabel.setText(room.code());
        setPlayers(List.of());
        phoneRow.setVisible(false); // Shown again if the server sends a phone link for this room.
    }

    /** Shows the QR code that phones scan to join (added in the phone upgrade). */
    void showPhoneLink(String url) {
        phoneLink = url;
        qrView.setText(url);
        phoneUrl.setText(url.replaceFirst("^http://", ""));
        phoneRow.setVisible(true);
        revalidate();
        repaint();
    }

    void setPlayers(List<String> names) {
        players.clear();
        players.addAll(names);
        countLabel.setText(names.size() + (names.size() == 1 ? " student joined" : " students joined"));
        startButton.setEnabled(!names.isEmpty());
        removeButton.setEnabled(playerList.getSelectedValue() != null);
    }

    String code() {
        return codeLabel.getText();
    }
}
