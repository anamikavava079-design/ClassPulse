package classpulse.client;

import classpulse.common.Message.ChangePassword;
import classpulse.common.Message.UpdateProfile;
import classpulse.common.Validation;
import classpulse.common.Views.Account;
import classpulse.common.Views.Role;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** The account page, shared by teachers and students: details, name change and password change. */
final class ProfilePanel extends JPanel {
    private final AppFrame app;
    private final JLabel avatar = new JLabel();
    private final JLabel name = Theme.label(" ", Font.BOLD, 22, Theme.INK);
    private final JLabel role = Theme.pill(" ", Theme.PRIMARY_SOFT, Theme.PRIMARY_DARK);
    private final JLabel username = Theme.label(" ", Font.PLAIN, 14, Theme.MUTED);
    private final JLabel email = Theme.label(" ", Font.PLAIN, 14, Theme.INK);
    final JTextField nameField = Theme.field(20);
    final JButton saveName = Theme.primary("Save name");
    final JPasswordField currentPassword = Theme.passwordField(16, "Current password");
    final JPasswordField newPassword = Theme.passwordField(16, "New password");
    final JPasswordField confirmPassword = Theme.passwordField(16, "Repeat new password");
    private final Widgets.StrengthMeter strength = new Widgets.StrengthMeter();
    final JButton changePassword = Theme.withIcon(Theme.primary("Change password"), Icons.Kind.LOCK);

    ProfilePanel(AppFrame app) {
        this.app = app;
        setLayout(new BorderLayout(0, 20));
        setOpaque(false);
        add(Widgets.header(Widgets.pageTitle("Profile"), Widgets.pageSubtitle("Your account details and security.")),
                BorderLayout.NORTH);

        JPanel body = Theme.transparent(new GridLayout(1, 2, 20, 0));
        body.add(detailsCard());
        body.add(passwordCard());
        JPanel top = Theme.transparent(new BorderLayout());
        top.add(body, BorderLayout.NORTH);
        add(top, BorderLayout.CENTER);

        saveName.addActionListener(e -> saveName());
        nameField.addActionListener(e -> saveName());
        changePassword.addActionListener(e -> changePassword());
        Theme.confirms(confirmPassword, newPassword); // Green tick when both passwords match.
        newPassword.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                strength.update(new String(newPassword.getPassword()));
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }
        });
    }

    private JComponent detailsCard() {
        JPanel card = Theme.card();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = column();
        JPanel who = Theme.transparent(new BorderLayout(16, 0));
        who.add(avatar, BorderLayout.WEST);
        JPanel lines = Theme.transparent(new GridLayout(3, 1, 0, 2));
        lines.add(name);
        lines.add(username);
        JPanel rolePill = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        rolePill.add(role);
        lines.add(rolePill);
        who.add(lines, BorderLayout.CENTER);
        card.add(who, c);

        c.insets = new Insets(22, 0, 0, 0);
        card.add(Theme.label("Email", Font.BOLD, 13, Theme.MUTED), c);
        JPanel emailRow = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        email.setIcon(Icons.of(Icons.Kind.MAIL, 16, Theme.MUTED));
        email.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 6));
        emailRow.add(email);
        JLabel verified = Theme.pill("Verified", Theme.CORRECT_SOFT, new Color(0x15803D));
        verified.setIcon(Icons.of(Icons.Kind.CHECK, 12, new Color(0x15803D)));
        emailRow.add(verified);
        c.insets = new Insets(6, 0, 0, 0);
        card.add(emailRow, c);

        c.insets = new Insets(22, 0, 6, 0);
        card.add(Theme.label("Full name", Font.BOLD, 13, Theme.MUTED), c);
        c.insets = new Insets(0, 0, 0, 0);
        card.add(nameField, c);
        c.insets = new Insets(12, 0, 0, 0);
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        card.add(saveName, c);
        c.weighty = 1;
        card.add(Theme.transparent(null), c);
        return card;
    }

    private JComponent passwordCard() {
        JPanel card = Theme.card();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = column();
        card.add(Theme.label("Change password", Font.BOLD, 17, Theme.INK), c);
        c.insets = new Insets(4, 0, 14, 0);
        card.add(Theme.label("We will email you to confirm the change.", Font.PLAIN, 13, Theme.MUTED), c);
        for (JComponent field : new JComponent[]{currentPassword, newPassword, confirmPassword}) {
            c.insets = new Insets(0, 0, 10, 0);
            card.add(field, c);
        }
        card.add(strength, c);
        c.insets = new Insets(8, 0, 0, 0);
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        card.add(changePassword, c);
        c.weighty = 1;
        card.add(Theme.transparent(null), c);
        return card;
    }

    private static GridBagConstraints column() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    void setAccount(Account account) {
        avatar.setIcon(Theme.avatar(account.fullName(), 64));
        name.setText(account.fullName());
        username.setText("@" + account.username());
        role.setText(account.role() == Role.TEACHER ? "Teacher" : "Student");
        email.setText(account.email());
        nameField.setText(account.fullName());
        currentPassword.setText("");
        newPassword.setText("");
        confirmPassword.setText("");
    }

    private void saveName() {
        try {
            app.busy(saveName);
            app.send(new UpdateProfile(Validation.fullName(nameField.getText())));
        } catch (IllegalArgumentException e) {
            app.clearBusy();
            app.toast(e.getMessage(), ToastLayer.Kind.ERROR);
        }
    }

    private void changePassword() {
        String next = new String(newPassword.getPassword());
        try {
            Validation.password(next);
        } catch (IllegalArgumentException e) {
            app.toast(e.getMessage(), ToastLayer.Kind.ERROR);
            return;
        }
        if (!next.equals(new String(confirmPassword.getPassword()))) {
            app.toast("The new passwords do not match.", ToastLayer.Kind.ERROR);
            return;
        }
        app.busy(changePassword);
        app.send(new ChangePassword(new String(currentPassword.getPassword()), next));
        currentPassword.setText("");
        newPassword.setText("");
        confirmPassword.setText("");
    }
}
