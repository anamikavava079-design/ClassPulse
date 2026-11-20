package classpulse.client;

import classpulse.common.Views.Account;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The dark navigation bar on the left of the teacher and student screens. */
final class Sidebar extends JPanel {
    record Item(String id, String label, Icons.Kind icon) {
    }

    private final List<NavButton> buttons = new ArrayList<>();
    private final JLabel avatar = new JLabel();
    private final JLabel name = Theme.label(" ", Font.BOLD, 14, Color.WHITE);
    private final JLabel detail = Theme.label(" ", Font.PLAIN, 12, Theme.SIDEBAR_TEXT);
    final JButton signOut;

    Sidebar(String roleName, List<Item> items, Consumer<String> onSelect, Runnable onSignOut) {
        setLayout(new BorderLayout());
        setBackground(Theme.SIDEBAR);
        setPreferredSize(new Dimension(236, 100));
        setBorder(BorderFactory.createEmptyBorder(22, 14, 18, 14));

        JPanel top = Theme.transparent(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel brand = Theme.label("ClassPulse", Font.BOLD, 20, Color.WHITE);
        brand.setIcon(Icons.of(Icons.Kind.LOGO, 30, Color.WHITE));
        brand.setIconTextGap(10);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 6, 22, 0));
        brand.setAlignmentX(LEFT_ALIGNMENT);
        top.add(brand);

        JPanel profile = new Theme.RoundPanel(Theme.SIDEBAR_HOVER, 16, false);
        profile.setLayout(new BorderLayout(10, 0));
        profile.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        profile.add(avatar, BorderLayout.WEST);
        JPanel who = Theme.transparent(new java.awt.GridLayout(2, 1));
        who.add(name);
        detail.setText(roleName);
        who.add(detail);
        profile.add(who, BorderLayout.CENTER);
        profile.setAlignmentX(LEFT_ALIGNMENT);
        profile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        top.add(profile);
        top.add(Box.createVerticalStrut(22));

        JLabel menu = Theme.label("MENU", Font.BOLD, 11, new Color(0x64748B));
        menu.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 0));
        menu.setAlignmentX(LEFT_ALIGNMENT);
        top.add(menu);
        for (Item item : items) {
            NavButton button = new NavButton(item);
            button.addActionListener(e -> {
                select(item.id());
                onSelect.accept(item.id());
            });
            buttons.add(button);
            top.add(button);
            top.add(Box.createVerticalStrut(4));
        }
        add(top, BorderLayout.NORTH);

        signOut = new NavButton(new Item("signout", "Sign out", Icons.Kind.LOGOUT));
        signOut.addActionListener(e -> onSignOut.run());
        add(signOut, BorderLayout.SOUTH);
        if (!items.isEmpty()) {
            select(items.get(0).id());
        }
    }

    /** A deep navy-to-indigo gradient with a faint violet glow at the top. */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x0F172A), 0, h, new Color(0x1E1B4B)));
        g.fillRect(0, 0, w, h);
        g.setPaint(new java.awt.RadialGradientPaint(w * 0.2f, 0, Math.max(200, w * 1.1f), new float[]{0f, 1f},
                new Color[]{new Color(124, 58, 237, 55), new Color(124, 58, 237, 0)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
    }

    void setAccount(Account account) {
        String shown = account.fullName();
        name.setText(shown.length() > 18 ? shown.substring(0, 17) + "..." : shown);
        avatar.setIcon(Theme.avatar(account.fullName(), 40));
    }

    void select(String id) {
        for (NavButton button : buttons) {
            button.selected = button.item.id().equals(id);
            button.refresh();
        }
    }

    /** One menu entry. The selected one gets an indigo highlight. */
    private static final class NavButton extends JButton {
        final Item item;
        private final javax.swing.Icon dimIcon;
        private final javax.swing.Icon brightIcon;
        boolean selected;
        boolean hover;

        NavButton(Item item) {
            super(item.label());
            this.item = item;
            dimIcon = Icons.of(item.icon(), 19, Theme.SIDEBAR_TEXT);
            brightIcon = Icons.of(item.icon(), 19, Color.WHITE);
            setFont(Theme.font(Font.BOLD, 14));
            setHorizontalAlignment(LEFT);
            setIconTextGap(12);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(11, 14, 11, 14));
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    refresh();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    refresh();
                }
            });
            refresh();
        }

        void refresh() {
            boolean bright = selected || hover;
            setForeground(bright ? Color.WHITE : Theme.SIDEBAR_TEXT);
            setIcon(bright ? brightIcon : dimIcon);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            if (selected) {
                // A glowing indigo-to-violet pill for the page that is open.
                g.setColor(new Color(124, 58, 237, 45));
                g.fillRoundRect(0, 2, getWidth(), getHeight() - 1, 14, 14);
                g.setPaint(new java.awt.GradientPaint(0, 0, Theme.PRIMARY, getWidth(), 0, Theme.ACCENT));
                g.fillRoundRect(0, 0, getWidth(), getHeight() - 2, 12, 12);
                g.setColor(new Color(255, 255, 255, 30));
                g.fillRoundRect(1, 1, getWidth() - 2, (getHeight() - 2) / 2, 12, 12);
            } else if (hover) {
                g.setColor(new Color(255, 255, 255, 18));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
