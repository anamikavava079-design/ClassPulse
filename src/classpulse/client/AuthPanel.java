package classpulse.client;

import classpulse.common.Message.CodePurpose;
import classpulse.common.Message.CodeSent;
import classpulse.common.Message.ForgotPassword;
import classpulse.common.Message.JoinRoom;
import classpulse.common.Message.Login;
import classpulse.common.Message.Register;
import classpulse.common.Message.Request;
import classpulse.common.Message.ResendCode;
import classpulse.common.Message.ResetPassword;
import classpulse.common.Message.VerifyEmail;
import classpulse.common.Protocol;
import classpulse.common.Validation;
import classpulse.common.Views.Role;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.geom.Path2D;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * The start screen, in one window. A brand panel on the left; on the right one card with
 * a sliding Student | Teacher switch in a single row, and below it only the form that is
 * needed right now: sign in, create account, enter the emailed code, forgot password,
 * set a new password, or join as a guest. The forms slide into each other.
 */
final class AuthPanel extends JPanel {
    static final String SIGN_IN = "signIn";
    static final String REGISTER = "register";
    static final String VERIFY = "verify";
    static final String FORGOT = "forgot";
    static final String RESET = "reset";
    static final String GUEST = "guest";

    private final AppFrame app;
    private final SmoothDeck forms = new SmoothDeck();
    final SegmentedControl roleSwitch = new SegmentedControl(new String[]{"Student", "Teacher"},
            new Icons.Kind[]{Icons.Kind.STUDENT, Icons.Kind.TEACHER});
    private final JLabel title = Theme.label(" ", Font.BOLD, 26, Theme.INK);
    private final JTextArea subtitle = Theme.wrapped(" ", Font.PLAIN, 14, Theme.MUTED);
    private final JPanel statusBox = new Theme.RoundPanel(Theme.WRONG_SOFT, 12, false);
    private final JTextArea status = Theme.wrapped(" ", Font.BOLD, 13, Theme.WRONG);

    // Sign in
    final JTextField loginField = Theme.field(18, "Username or email");
    final JPasswordField passwordField = Theme.passwordField(18, "Your password");
    final JButton signInButton = Theme.primary("Sign in");
    private final JPanel guestBox = Theme.transparent(new BorderLayout(0, 12));
    // Register
    final JTextField fullNameField = Theme.field(10, "e.g. Anamika E");
    final JTextField usernameField = Theme.field(10, "e.g. anamika_e");
    final JTextField emailField = Theme.field(18, "name@example.com");
    final JPasswordField newPasswordField = Theme.passwordField(10, "Password");
    final JPasswordField confirmField = Theme.passwordField(10, "Repeat it");
    private final Widgets.StrengthMeter strength = new Widgets.StrengthMeter();
    final JButton createButton = Theme.primary("Create student account");
    // Verify
    private final JTextArea verifyMessage = Theme.wrapped(" ", Font.PLAIN, 14, Theme.INK);
    final JTextField codeField = codeField();
    final JButton verifyButton = Theme.primary("Verify email");
    private final JButton resendVerify = Theme.link("Resend code");
    // Forgot and reset
    final JTextField forgotEmailField = Theme.field(18, "The email you registered with");
    final JButton sendResetButton = Theme.withIcon(Theme.primary("Email me a reset code"), Icons.Kind.SEND);
    private final JTextArea resetMessage = Theme.wrapped(" ", Font.PLAIN, 14, Theme.INK);
    final JTextField resetCodeField = codeField();
    final JPasswordField resetPasswordField = Theme.passwordField(10, "New password");
    final JPasswordField resetConfirmField = Theme.passwordField(10, "Repeat it");
    private final Widgets.StrengthMeter resetStrength = new Widgets.StrengthMeter();
    final JButton resetButton = Theme.primary("Reset password");
    private final JButton resendReset = Theme.link("Resend code");
    // Guest
    final JTextField guestCodeField = Theme.field(8, "ABC123");
    final JTextField guestNameField = Theme.field(14, "Your name in the room");
    final JButton guestJoinButton = Theme.withIcon(Theme.primary("Join quiz"), Icons.Kind.PLAY);
    // Connection
    final JTextField serverField = Theme.field(11);
    final JTextField portField = Theme.field(5);

    private String pendingEmail = "";
    private int cooldown;
    private final Timer cooldownTimer = new Timer(1000, e -> tickCooldown());

    AuthPanel(AppFrame app) {
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        add(new HeroPanel(), BorderLayout.WEST);

        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(Theme.BACKGROUND);
        JPanel column = Theme.transparent(new BorderLayout(0, 14));
        column.add(buildCard(), BorderLayout.CENTER);
        column.add(connectionRow(), BorderLayout.SOUTH);
        right.add(column);
        add(right, BorderLayout.CENTER);

        roleSwitch.onChange(i -> roleChanged());
        roleChanged();
        showView(SIGN_IN);
        wireActions();
    }

    // ----- Layout -----

    private JComponent buildCard() {
        JPanel card = new Theme.RoundPanel(Theme.CARD, 24, true);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(30, 34, 30, 34));
        card.setPreferredSize(null);

        JPanel heading = Theme.transparent(new BorderLayout(0, 6));
        heading.add(title, BorderLayout.NORTH);
        subtitle.setSize(new Dimension(400, Short.MAX_VALUE));
        heading.add(subtitle, BorderLayout.CENTER);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        add(card, heading);
        roleSwitch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        roleSwitch.setMinimumSize(new Dimension(200, 46));
        card.add(Box.createVerticalStrut(18));
        add(card, roleSwitch);
        card.add(Box.createVerticalStrut(16));

        statusBox.setLayout(new BorderLayout());
        statusBox.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        statusBox.add(status);
        statusBox.setVisible(false);
        add(card, statusBox);
        card.add(Box.createVerticalStrut(4));

        forms.setSizeToCurrent(true);
        forms.addCard(signInForm(), SIGN_IN);
        forms.addCard(registerForm(), REGISTER);
        forms.addCard(verifyForm(), VERIFY);
        forms.addCard(forgotForm(), FORGOT);
        forms.addCard(resetForm(), RESET);
        forms.addCard(guestForm(), GUEST);
        add(card, forms);

        JPanel sized = Theme.transparent(new BorderLayout());
        sized.add(card);
        sized.setPreferredSize(null);
        return new FixedWidth(sized, 468);
    }

    /** Lets a BoxLayout child stretch to the full width. */
    private static void add(JPanel box, JComponent child) {
        child.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(child);
    }

    private JComponent signInForm() {
        JPanel form = form();
        JPanel passwordLabel = Theme.transparent(new BorderLayout());
        passwordLabel.add(fieldLabel("Password"), BorderLayout.WEST);
        JButton forgot = Theme.link("Forgot password?");
        forgot.addActionListener(e -> {
            String typed = loginField.getText().strip();
            if (Validation.looksLikeEmail(typed)) {
                forgotEmailField.setText(typed);
            }
            showView(FORGOT);
        });
        passwordLabel.add(forgot, BorderLayout.EAST);
        row(form, labeled(fieldLabel("Username or email"), loginField), 8);
        row(form, labeled(passwordLabel, passwordField), 14);
        row(form, Theme.height(signInButton, 46), 22);
        row(form, linkRow("New to ClassPulse?", "Create an account", () -> showView(REGISTER)), 14);

        JPanel divider = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                Theme.smooth(g);
                int y = getHeight() / 2;
                g.setColor(Theme.BORDER);
                g.drawLine(0, y, getWidth() / 2 - 22, y);
                g.drawLine(getWidth() / 2 + 22, y, getWidth(), y);
                g.setColor(Theme.SUBTLE);
                g.setFont(Theme.font(Font.PLAIN, 12));
                g.drawString("or", getWidth() / 2 - 6, y + 4);
                g.dispose();
            }
        };
        divider.setOpaque(false);
        divider.setPreferredSize(new Dimension(10, 18));
        JButton guest = Theme.withIcon(Theme.secondary("Join a quiz as a guest"), Icons.Kind.PLAY);
        guest.addActionListener(e -> showView(GUEST));
        guestBox.add(divider, BorderLayout.NORTH);
        guestBox.add(Theme.height(guest, 44), BorderLayout.CENTER);
        row(form, guestBox, 12);
        return form;
    }

    private JComponent registerForm() {
        JPanel form = form();
        row(form, pair(labeled(fieldLabel("Full name"), fullNameField), labeled(fieldLabel("Username"), usernameField)), 8);
        JPanel email = labeled(fieldLabel("Email address"), emailField);
        JLabel hint = Theme.label("We'll email you a code to prove the address is yours.", Font.PLAIN, 12, Theme.MUTED);
        hint.setIcon(Icons.of(Icons.Kind.MAIL, 14, Theme.MUTED));
        email.add(hint, BorderLayout.SOUTH);
        row(form, email, 12);
        row(form, pair(labeled(fieldLabel("Password"), newPasswordField),
                labeled(fieldLabel("Confirm password"), confirmField)), 12);
        row(form, strength, 8);
        row(form, Theme.height(createButton, 46), 16);
        row(form, linkRow("Already have an account?", "Sign in", () -> showView(SIGN_IN)), 12);
        newPasswordField.getDocument().addDocumentListener(onChange(() ->
                strength.update(new String(newPasswordField.getPassword()))));
        Theme.confirms(confirmField, newPasswordField); // Green tick when both passwords match.
        return form;
    }

    private JComponent verifyForm() {
        JPanel form = form();
        row(form, iconMessage(Icons.Kind.MAIL, verifyMessage), 4);
        row(form, labeled(fieldLabel("6-digit code"), codeField), 16);
        row(form, Theme.height(verifyButton, 46), 18);
        JPanel links = Theme.transparent(new BorderLayout());
        links.add(resendVerify, BorderLayout.WEST);
        JButton back = Theme.link("Back to sign in");
        back.addActionListener(e -> showView(SIGN_IN));
        links.add(back, BorderLayout.EAST);
        row(form, links, 14);
        return form;
    }

    private JComponent forgotForm() {
        JPanel form = form();
        JTextArea info = Theme.wrapped("Enter the email address of your account. A reset code is emailed only "
                + "if a ClassPulse account is registered with that address.", Font.PLAIN, 14, Theme.INK);
        row(form, iconMessage(Icons.Kind.LOCK, info), 4);
        row(form, labeled(fieldLabel("Email address"), forgotEmailField), 16);
        row(form, Theme.height(sendResetButton, 46), 18);
        row(form, linkRow("Remembered it?", "Back to sign in", () -> showView(SIGN_IN)), 14);
        return form;
    }

    private JComponent resetForm() {
        JPanel form = form();
        row(form, iconMessage(Icons.Kind.MAIL, resetMessage), 4);
        row(form, labeled(fieldLabel("6-digit code"), resetCodeField), 14);
        row(form, pair(labeled(fieldLabel("New password"), resetPasswordField),
                labeled(fieldLabel("Confirm password"), resetConfirmField)), 12);
        row(form, resetStrength, 8);
        row(form, Theme.height(resetButton, 46), 14);
        JPanel links = Theme.transparent(new BorderLayout());
        links.add(resendReset, BorderLayout.WEST);
        JButton back = Theme.link("Back to sign in");
        back.addActionListener(e -> showView(SIGN_IN));
        links.add(back, BorderLayout.EAST);
        row(form, links, 14);
        resetPasswordField.getDocument().addDocumentListener(onChange(() ->
                resetStrength.update(new String(resetPasswordField.getPassword()))));
        Theme.confirms(resetConfirmField, resetPasswordField);
        return form;
    }

    private JComponent guestForm() {
        JPanel form = form();
        JTextArea info = Theme.wrapped("Guests can play straight away, but their results are not saved. "
                + "Create a free student account to keep your history and earn badges.", Font.PLAIN, 14, Theme.INK);
        row(form, iconMessage(Icons.Kind.PLAY, info), 4);
        guestCodeField.setFont(Theme.font(Font.BOLD, 24));
        guestCodeField.setHorizontalAlignment(SwingConstants.CENTER);
        ((AbstractDocument) guestCodeField.getDocument()).setDocumentFilter(new CodeFilter(6, true));
        row(form, labeled(fieldLabel("Room code"), guestCodeField), 16);
        row(form, labeled(fieldLabel("Your name"), guestNameField), 12);
        row(form, Theme.height(guestJoinButton, 46), 18);
        row(form, linkRow("Have an account?", "Sign in", () -> showView(SIGN_IN)), 14);
        return form;
    }

    private JComponent connectionRow() {
        JPanel row = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 8, 0));
        serverField.setText("localhost");
        portField.setText(String.valueOf(Protocol.DEFAULT_PORT));
        serverField.setFont(Theme.font(Font.PLAIN, 13));
        portField.setFont(Theme.font(Font.PLAIN, 13));
        JLabel label = Theme.label("Server", Font.BOLD, 12, Theme.MUTED);
        label.setIcon(Icons.of(Icons.Kind.TEACHER, 14, Theme.MUTED));
        row.add(label);
        row.add(serverField);
        row.add(Theme.label(":", Font.BOLD, 13, Theme.MUTED));
        row.add(portField);
        return row;
    }

    // ----- Small layout helpers -----

    private static JPanel form() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        return form;
    }

    private static void row(JPanel form, Component component, int top) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = form.getComponentCount();
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(top, 0, 0, 0);
        form.add(component, c);
    }

    private static JLabel fieldLabel(String text) {
        return Theme.label(text, Font.BOLD, 13, new Color(0x374151));
    }

    private static JPanel labeled(JComponent label, JComponent field) {
        JPanel panel = Theme.transparent(new BorderLayout(0, 6));
        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel pair(JComponent left, JComponent right) {
        JPanel panel = Theme.transparent(new GridLayout(1, 2, 12, 0));
        panel.add(left);
        panel.add(right);
        return panel;
    }

    private static JPanel linkRow(String text, String linkText, Runnable action) {
        JPanel panel = Theme.transparent(new FlowLayout(FlowLayout.CENTER, 6, 0));
        panel.add(Theme.label(text, Font.PLAIN, 13, Theme.MUTED));
        JButton link = Theme.link(linkText);
        link.addActionListener(e -> action.run());
        panel.add(link);
        return panel;
    }

    private static JPanel iconMessage(Icons.Kind icon, JTextArea text) {
        JPanel panel = new Theme.RoundPanel(Theme.PRIMARY_SOFT, 14, false);
        panel.setLayout(new BorderLayout(12, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JLabel iconLabel = new JLabel(Icons.of(icon, 22, Theme.PRIMARY));
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        panel.add(iconLabel, BorderLayout.WEST);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private static JTextField codeField() {
        JTextField field = Theme.field(8, "000000");
        field.setFont(new Font("Consolas", Font.BOLD, 28));
        field.setHorizontalAlignment(SwingConstants.CENTER);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new CodeFilter(6, false));
        return field;
    }

    private static javax.swing.event.DocumentListener onChange(Runnable action) {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                action.run();
            }
        };
    }

    // ----- Behaviour -----

    private void wireActions() {
        signInButton.addActionListener(e -> signIn());
        passwordField.addActionListener(e -> signIn());
        loginField.addActionListener(e -> passwordField.requestFocusInWindow());
        createButton.addActionListener(e -> register());
        confirmField.addActionListener(e -> register());
        verifyButton.addActionListener(e -> verify());
        codeField.addActionListener(e -> verify());
        resendVerify.addActionListener(e -> resend(CodePurpose.VERIFY_EMAIL));
        sendResetButton.addActionListener(e -> forgot());
        forgotEmailField.addActionListener(e -> forgot());
        resetButton.addActionListener(e -> reset());
        resetConfirmField.addActionListener(e -> reset());
        resendReset.addActionListener(e -> resend(CodePurpose.RESET_PASSWORD));
        guestJoinButton.addActionListener(e -> joinAsGuest());
        guestNameField.addActionListener(e -> joinAsGuest());
    }

    Role selectedRole() {
        return roleSwitch.selected() == 0 ? Role.STUDENT : Role.TEACHER;
    }

    private void roleChanged() {
        boolean student = selectedRole() == Role.STUDENT;
        createButton.setText(student ? "Create student account" : "Create teacher account");
        guestBox.setVisible(student);
        updateHeading();
        forms.revalidate();
        forms.repaint();
    }

    String currentView() {
        return forms.current();
    }

    void showView(String view) {
        String old = forms.current();
        int direction = order(view) >= order(old) ? 1 : -1;
        forms.show(view, direction);
        roleSwitch.setVisible(view.equals(SIGN_IN) || view.equals(REGISTER));
        clearStatus();
        updateHeading();
        revalidate();
        repaint();
        Component focus = switch (view) {
            case SIGN_IN -> loginField.getText().isBlank() ? loginField : passwordField;
            case REGISTER -> fullNameField;
            case VERIFY -> codeField;
            case FORGOT -> forgotEmailField;
            case RESET -> resetCodeField;
            default -> guestCodeField;
        };
        javax.swing.SwingUtilities.invokeLater(focus::requestFocusInWindow);
    }

    private static int order(String view) {
        return switch (view) {
            case SIGN_IN -> 0;
            case GUEST -> 1;
            case REGISTER -> 2;
            case FORGOT -> 3;
            case VERIFY, RESET -> 4;
            default -> 0;
        };
    }

    private void updateHeading() {
        boolean student = selectedRole() == Role.STUDENT;
        String view = forms.current() == null ? SIGN_IN : forms.current();
        switch (view) {
            case REGISTER -> {
                title.setText("Create your account");
                subtitle.setText(student ? "Save your results, earn badges and get every result by email."
                        : "Build quizzes, host live sessions and get class reports by email.");
            }
            case VERIFY -> {
                title.setText("Check your email");
                subtitle.setText("One last step: confirm that this email address is really yours.");
            }
            case FORGOT -> {
                title.setText("Forgot your password?");
                subtitle.setText("No problem. We will email you a code to set a new one.");
            }
            case RESET -> {
                title.setText("Set a new password");
                subtitle.setText("Enter the code from your email, then choose a new password.");
            }
            case GUEST -> {
                title.setText("Join as a guest");
                subtitle.setText("Enter the room code shown on your teacher's screen.");
            }
            default -> {
                title.setText("Welcome back");
                subtitle.setText(student ? "Sign in to join live quizzes and track your progress."
                        : "Sign in to host quizzes and see how your class is doing.");
            }
        }
    }

    private void signIn() {
        String login = loginField.getText().strip();
        String password = new String(passwordField.getPassword());
        if (login.isEmpty() || password.isEmpty()) {
            showError("Enter your username (or email) and password.");
            return;
        }
        send(new Login(login, password), signInButton);
    }

    private void register() {
        try {
            String fullName = Validation.fullName(fullNameField.getText());
            String username = Validation.username(usernameField.getText());
            String email = Validation.email(emailField.getText());
            String suggestion = Validation.suggestEmail(email);
            if (suggestion != null) {
                showError("Did you mean " + suggestion + "? Please check the email address.");
                return;
            }
            String password = Validation.password(new String(newPasswordField.getPassword()));
            if (!password.equals(new String(confirmField.getPassword()))) {
                showError("The two passwords do not match.");
                return;
            }
            send(new Register(fullName, username, email, password, selectedRole()), createButton);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void verify() {
        try {
            send(new VerifyEmail(pendingEmail, Validation.emailCode(codeField.getText())), verifyButton);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void forgot() {
        try {
            String email = Validation.email(forgotEmailField.getText());
            send(new ForgotPassword(email), sendResetButton);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void reset() {
        try {
            String code = Validation.emailCode(resetCodeField.getText());
            String password = Validation.password(new String(resetPasswordField.getPassword()));
            if (!password.equals(new String(resetConfirmField.getPassword()))) {
                showError("The two passwords do not match.");
                return;
            }
            send(new ResetPassword(pendingEmail, code, password), resetButton);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void resend(CodePurpose purpose) {
        if (cooldown > 0 || pendingEmail.isEmpty()) {
            return;
        }
        send(new ResendCode(pendingEmail, purpose), null);
    }

    private void joinAsGuest() {
        try {
            String code = Validation.roomCode(guestCodeField.getText());
            String name = Validation.displayName(guestNameField.getText());
            send(new JoinRoom(code, name), guestJoinButton);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void send(Request request, JButton busyButton) {
        int port;
        try {
            port = Integer.parseInt(portField.getText().strip());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showError("The port must be a number from 1 to 65535.");
            return;
        }
        clearStatus();
        if (busyButton != null) {
            app.busy(busyButton);
        }
        app.connectAndSend(serverField.getText().strip(), port, request);
    }

    /** The server emailed a code: move to the matching code screen. */
    void codeSent(CodeSent sent) {
        pendingEmail = sent.email();
        boolean verify = sent.purpose() == CodePurpose.VERIFY_EMAIL;
        (verify ? verifyMessage : resetMessage).setText(sent.text());
        (verify ? codeField : resetCodeField).setText("");
        String view = verify ? VERIFY : RESET;
        if (!view.equals(forms.current())) {
            showView(view);
        } else {
            showInfo(sent.text());
        }
        startCooldown();
    }

    void resetDone(String text) {
        loginField.setText(pendingEmail);
        passwordField.setText("");
        resetPasswordField.setText("");
        resetConfirmField.setText("");
        showView(SIGN_IN);
        showInfo(text);
    }

    /** Back to a clean sign-in form, for example after signing out. */
    void reset(String message) {
        passwordField.setText("");
        newPasswordField.setText("");
        confirmField.setText("");
        codeField.setText("");
        if (!SIGN_IN.equals(forms.current())) {
            showView(SIGN_IN);
        }
        if (message == null || message.isBlank()) {
            clearStatus();
        } else {
            showError(message);
        }
    }

    private void startCooldown() {
        cooldown = 60;
        tickCooldown();
        cooldownTimer.restart();
    }

    private void tickCooldown() {
        for (JButton link : new JButton[]{resendVerify, resendReset}) {
            link.setEnabled(cooldown <= 0);
            link.setText(cooldown > 0 ? "Resend code in " + cooldown + "s" : "Resend code");
        }
        if (cooldown <= 0) {
            cooldownTimer.stop();
        }
        cooldown--;
    }

    void showError(String text) {
        setStatus(text, Theme.WRONG_SOFT, Theme.WRONG);
    }

    void showInfo(String text) {
        setStatus(text, Theme.CORRECT_SOFT, new Color(0x15803D));
    }

    private void setStatus(String text, Color background, Color foreground) {
        boolean empty = text == null || text.isBlank();
        statusBox.setBackground(background);
        status.setForeground(foreground);
        status.setText(empty ? " " : text);
        // A wrapping text area needs a width before it can work out its height.
        status.setSize(new Dimension(370, Short.MAX_VALUE));
        Dimension needed = statusBox.getPreferredSize();
        statusBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, needed.height));
        statusBox.setVisible(!empty);
        revalidate();
        repaint();
    }

    private void clearStatus() {
        setStatus(" ", Theme.WRONG_SOFT, Theme.WRONG);
    }

    String statusText() {
        return statusBox.isVisible() ? status.getText() : "";
    }

    // ----- Helper classes -----

    /** Allows only digits (email codes) or room-code letters, up to a maximum length. */
    private static final class CodeFilter extends DocumentFilter {
        private final int max;
        private final boolean letters;

        CodeFilter(int max, boolean letters) {
            this.max = max;
            this.letters = letters;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String clean = text == null ? "" : text.toUpperCase(Locale.ROOT)
                    .replaceAll(letters ? "[^A-Z0-9]" : "[^0-9]", "");
            int room = max - (fb.getDocument().getLength() - length);
            fb.replace(offset, length, clean.substring(0, Math.max(0, Math.min(room, clean.length()))), attrs);
        }
    }

    /** Keeps the card at one width, whatever the window size. */
    private static final class FixedWidth extends JPanel {
        private final int width;

        FixedWidth(JComponent child, int width) {
            super(new BorderLayout());
            this.width = width;
            setOpaque(false);
            add(child);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, super.getPreferredSize().height);
        }
    }

    /** The coloured brand panel on the left. */
    private static final class HeroPanel extends JPanel {
        HeroPanel() {
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(450, 600));
            setBorder(BorderFactory.createEmptyBorder(40, 44, 34, 44));
            setOpaque(false);

            JLabel brand = Theme.label("ClassPulse", Font.BOLD, 24, Color.WHITE);
            brand.setIcon(Icons.of(Icons.Kind.LOGO, 36, Color.WHITE));
            brand.setIconTextGap(12);
            add(brand, BorderLayout.NORTH);

            JPanel middle = Theme.transparent(null);
            middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
            JTextArea headline = Theme.wrapped("Feel the pulse of your classroom.", Font.BOLD, 34, Color.WHITE);
            JTextArea text = Theme.wrapped("Live quizzes, instant results, and reports that show what to teach next.",
                    Font.PLAIN, 16, new Color(255, 255, 255, 215));
            for (JComponent c : new JComponent[]{headline, text}) {
                c.setAlignmentX(LEFT_ALIGNMENT);
            }
            middle.add(headline);
            middle.add(Box.createVerticalStrut(12));
            middle.add(text);
            middle.add(Box.createVerticalStrut(28));
            String[][] features = {
                    {"Live timed quizzes", "Faster correct answers and streaks earn more points"},
                    {"Verified accounts", "Every email is checked with a one-time code"},
                    {"Reports and history", "Spot the question to reteach, track progress"},
                    {"Results by email", "Students and teachers get a copy automatically"}};
            for (String[] feature : features) {
                middle.add(feature(feature[0], feature[1]));
                middle.add(Box.createVerticalStrut(14));
            }
            JPanel center = Theme.transparent(new GridBagLayout());
            center.add(middle);
            add(center, BorderLayout.CENTER);

            add(Theme.label("Java  |  Swing  |  TCP sockets  |  MySQL  |  SMTP", Font.PLAIN, 12,
                    new Color(255, 255, 255, 160)), BorderLayout.SOUTH);
        }

        private static JComponent feature(String title, String detail) {
            JPanel row = Theme.transparent(new BorderLayout(14, 0));
            row.setAlignmentX(LEFT_ALIGNMENT);
            JLabel tick = new JLabel(Icons.of(Icons.Kind.CHECK, 16, Color.WHITE)) {
                @Override
                protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    Theme.smooth(g);
                    g.setColor(new Color(255, 255, 255, 45));
                    g.fillOval(0, 0, getWidth(), getHeight());
                    g.dispose();
                    super.paintComponent(graphics);
                }
            };
            tick.setPreferredSize(new Dimension(34, 34));
            JPanel wrapper = Theme.transparent(new BorderLayout());
            wrapper.add(tick, BorderLayout.NORTH);
            row.add(wrapper, BorderLayout.WEST);
            JPanel words = Theme.transparent(new GridLayout(2, 1));
            words.add(Theme.label(title, Font.BOLD, 15, Color.WHITE));
            words.add(Theme.label(detail, Font.PLAIN, 13, new Color(255, 255, 255, 190)));
            row.add(words, BorderLayout.CENTER);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            return row;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            int w = getWidth();
            int h = getHeight();
            g.setPaint(new GradientPaint(0, 0, new Color(0x4338CA), w, h, new Color(0x7C3AED)));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 255, 255, 16));
            g.fillOval(w - 220, -120, 360, 360);
            g.fillOval(-140, h - 220, 320, 320);
            g.setColor(new Color(255, 255, 255, 10));
            g.fillOval(w / 2, h / 2 - 40, 220, 220);
            // A faint heartbeat line, echoing the logo.
            g.setColor(new Color(255, 255, 255, 30));
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D pulse = new Path2D.Double();
            int y = h - 120;
            pulse.moveTo(0, y);
            pulse.lineTo(w * 0.35, y);
            pulse.lineTo(w * 0.42, y - 46);
            pulse.lineTo(w * 0.5, y + 40);
            pulse.lineTo(w * 0.56, y - 18);
            pulse.lineTo(w * 0.62, y);
            pulse.lineTo(w, y);
            g.draw(pulse);
            g.dispose();
        }
    }
}
