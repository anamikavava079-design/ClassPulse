package classpulse.server;

import classpulse.common.Message.AuthResult;
import classpulse.common.Message.ChangePassword;
import classpulse.common.Message.CodePurpose;
import classpulse.common.Message.CodeSent;
import classpulse.common.Message.ErrorText;
import classpulse.common.Message.ForgotPassword;
import classpulse.common.Message.Login;
import classpulse.common.Message.Notice;
import classpulse.common.Message.PasswordResetDone;
import classpulse.common.Message.ProfileUpdated;
import classpulse.common.Message.Register;
import classpulse.common.Message.ResendCode;
import classpulse.common.Message.ResetPassword;
import classpulse.common.Message.Response;
import classpulse.common.Message.VerifyEmail;
import classpulse.common.Validation;
import classpulse.common.Views.Role;
import classpulse.mail.DomainChecker;
import classpulse.server.UserRepository.CodeRecord;
import classpulse.server.UserRepository.UserRecord;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Account rules: registration, email verification, sign-in, and password reset.
 *
 * How we make sure an email address is real before trusting it:
 *  1. Format check (Validation.email).
 *  2. DNS check: the domain must have a mail server (DomainChecker). Typos such as
 *     "gmial.com" are refused before anything is sent.
 *  3. A random 6-digit code is emailed. The account stays locked until that code is typed in,
 *     which proves the person can read that inbox.
 * Password-reset codes are only ever sent to an email address that belongs to an account.
 */
final class AuthService {
    static final int CODE_MINUTES = 10;
    static final int MAX_CODE_ATTEMPTS = 5;
    static final int RESEND_SECONDS = 60;
    /** Compared against when an account does not exist, so both cases take the same time. */
    private static final String DUMMY_HASH = PasswordHasher.hash("no-such-account-1");

    /** What to send back, and the user who is now signed in (or null). */
    record Outcome(Response response, UserRecord signedIn) {
        static Outcome reply(Response response) {
            return new Outcome(response, null);
        }
    }

    private final UserRepository users;
    private final QuizRepository quizzes;
    private final MailService mail;
    private final DomainChecker domains;
    private final SecureRandom random = new SecureRandom();

    AuthService(UserRepository users, QuizRepository quizzes, MailService mail, DomainChecker domains) {
        this.users = users;
        this.quizzes = quizzes;
        this.mail = mail;
        this.domains = domains;
    }

    Outcome register(Register m) {
        String fullName = Validation.fullName(m.fullName());
        String username = Validation.username(m.username());
        String email = Validation.email(m.email());
        String password = Validation.password(m.password());
        if (m.role() == null) {
            throw new IllegalArgumentException("Choose whether you are a student or a teacher.");
        }
        if (users.usernameTaken(username)) {
            return Outcome.reply(new AuthResult(false, "That username is already taken.", null));
        }
        if (users.emailTaken(email)) {
            return Outcome.reply(new AuthResult(false,
                    "An account with this email already exists. Sign in, or use Forgot password.", null));
        }
        String suggestion = Validation.suggestEmail(email);
        if (suggestion != null) {
            return Outcome.reply(new AuthResult(false, "Did you mean " + suggestion + "? Please check the email address.", null));
        }
        if (domains.check(email) == DomainChecker.Status.NO_MAIL_SERVER) {
            String domain = email.substring(email.indexOf('@') + 1);
            return Outcome.reply(new AuthResult(false, "\"" + domain + "\" cannot receive email. "
                    + "Please check the address for typos.", null));
        }
        Optional<Long> id = users.create(fullName, username, email, m.role(), PasswordHasher.hash(password));
        if (id.isEmpty()) { // Someone took the name or email a moment ago.
            return Outcome.reply(new AuthResult(false, "That username or email was just taken.", null));
        }
        if (m.role() == Role.TEACHER) {
            quizzes.saveQuiz(id.get(), SampleQuiz.create());
        }
        UserRecord user = users.findById(id.get()).orElseThrow();
        return Outcome.reply(sendCode(user, CodePurpose.VERIFY_EMAIL));
    }

    Outcome verify(VerifyEmail m) {
        String email = Validation.email(m.email());
        String code = Validation.emailCode(m.code());
        UserRecord user = users.findByEmail(email).orElse(null);
        if (user == null) {
            return Outcome.reply(new ErrorText("No account uses that email address."));
        }
        if (user.verified()) {
            return Outcome.reply(new ErrorText("This email is already verified. Please sign in."));
        }
        String problem = checkCode(user, CodePurpose.VERIFY_EMAIL, code);
        if (problem != null) {
            return Outcome.reply(new ErrorText(problem));
        }
        users.markVerified(user.id());
        users.recordLogin(user.id());
        mail.sendLater(user.email(), "WELCOME", EmailTemplates.welcome(user.fullName(), user.role()));
        return new Outcome(new AuthResult(true, "Email verified. Welcome to ClassPulse!", user.account()), user);
    }

    Outcome resend(ResendCode m) {
        String email = Validation.email(m.email());
        UserRecord user = users.findByEmail(email).orElse(null);
        if (user == null) {
            return Outcome.reply(new ErrorText("No ClassPulse account is registered with this email address."));
        }
        CodePurpose purpose = m.purpose() == null ? CodePurpose.VERIFY_EMAIL : m.purpose();
        if (purpose == CodePurpose.VERIFY_EMAIL && user.verified()) {
            return Outcome.reply(new ErrorText("This email is already verified. Please sign in."));
        }
        long wait = secondsUntilResend(user, purpose);
        if (wait > 0) {
            return Outcome.reply(new ErrorText("Please wait " + wait + " seconds before asking for another code."));
        }
        return Outcome.reply(sendCode(user, purpose));
    }

    /** login may be a username or an email address. */
    Outcome login(Login m) {
        String login = m.login() == null ? "" : m.login().strip();
        String password = m.password() == null ? "" : m.password();
        Optional<UserRecord> found = Validation.looksLikeEmail(login)
                ? users.findByEmail(login.toLowerCase(Locale.ROOT))
                : users.findByUsername(login);
        boolean ok = PasswordHasher.verify(password, found.map(UserRecord::passwordHash).orElse(DUMMY_HASH))
                && found.isPresent();
        if (!ok) {
            return Outcome.reply(new AuthResult(false, "Wrong username, email or password.", null));
        }
        UserRecord user = found.get();
        if (!user.verified()) {
            if (secondsUntilResend(user, CodePurpose.VERIFY_EMAIL) > 0) {
                return Outcome.reply(new CodeSent(user.email(), CodePurpose.VERIFY_EMAIL,
                        "Please verify your email first. Enter the code we sent to " + user.email() + "."));
            }
            return Outcome.reply(sendCode(user, CodePurpose.VERIFY_EMAIL));
        }
        users.recordLogin(user.id());
        return new Outcome(new AuthResult(true, "Signed in.", user.account()), user);
    }

    /** Sends a reset code, but only if the address belongs to an account. */
    Outcome forgot(ForgotPassword m) {
        String email = Validation.email(m.email());
        UserRecord user = users.findByEmail(email).orElse(null);
        if (user == null) {
            return Outcome.reply(new ErrorText("No ClassPulse account is registered with this email address, "
                    + "so no email was sent."));
        }
        long wait = secondsUntilResend(user, CodePurpose.RESET_PASSWORD);
        if (wait > 0) {
            return Outcome.reply(new CodeSent(user.email(), CodePurpose.RESET_PASSWORD,
                    "A code was sent less than a minute ago. Check your inbox, or wait " + wait + " seconds."));
        }
        return Outcome.reply(sendCode(user, CodePurpose.RESET_PASSWORD));
    }

    Outcome reset(ResetPassword m) {
        String email = Validation.email(m.email());
        String code = Validation.emailCode(m.code());
        String password = Validation.password(m.newPassword());
        UserRecord user = users.findByEmail(email).orElse(null);
        if (user == null) {
            return Outcome.reply(new ErrorText("No account uses that email address."));
        }
        String problem = checkCode(user, CodePurpose.RESET_PASSWORD, code);
        if (problem != null) {
            return Outcome.reply(new ErrorText(problem));
        }
        users.updatePassword(user.id(), PasswordHasher.hash(password));
        users.markVerified(user.id()); // Reading the code also proves the email is theirs.
        mail.sendLater(user.email(), "PASSWORD_CHANGED", EmailTemplates.passwordChanged(user.fullName()));
        return Outcome.reply(new PasswordResetDone("Your password has been reset. Sign in with your new password."));
    }

    Response changePassword(long userId, ChangePassword m) {
        UserRecord user = users.findById(userId).orElseThrow(() -> new IllegalStateException("Please sign in again."));
        String current = m.currentPassword() == null ? "" : m.currentPassword();
        if (!PasswordHasher.verify(current, user.passwordHash())) {
            return new ErrorText("Your current password is not correct.");
        }
        String next = Validation.password(m.newPassword());
        if (next.equals(current)) {
            return new ErrorText("The new password must be different from the current one.");
        }
        users.updatePassword(userId, PasswordHasher.hash(next));
        mail.sendLater(user.email(), "PASSWORD_CHANGED", EmailTemplates.passwordChanged(user.fullName()));
        return new Notice("Password changed. We sent a confirmation to " + user.email() + ".");
    }

    Response updateProfile(long userId, String fullName) {
        users.updateFullName(userId, Validation.fullName(fullName));
        return new ProfileUpdated(users.findById(userId).orElseThrow().account());
    }

    // ----- Codes -----

    private Response sendCode(UserRecord user, CodePurpose purpose) {
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        users.saveCode(user.id(), purpose, PasswordHasher.hash(code), LocalDateTime.now().plusMinutes(CODE_MINUTES));
        boolean verify = purpose == CodePurpose.VERIFY_EMAIL;
        EmailTemplates.Content content = verify
                ? EmailTemplates.verificationCode(user.fullName(), code, CODE_MINUTES)
                : EmailTemplates.resetCode(user.fullName(), code, CODE_MINUTES);
        String error = mail.send(user.email(), verify ? "VERIFY_CODE" : "RESET_CODE", content);
        if (error != null) {
            return new CodeSent(user.email(), purpose, "We could not send the email just now (" + error
                    + "). Wait a minute, then press Resend code.");
        }
        return new CodeSent(user.email(), purpose, "We sent a 6-digit code to " + user.email()
                + ". It expires in " + CODE_MINUTES + " minutes.");
    }

    /** Returns null if the code is right, otherwise what is wrong. A used code is deleted. */
    private String checkCode(UserRecord user, CodePurpose purpose, String code) {
        CodeRecord record = users.findCode(user.id(), purpose).orElse(null);
        if (record == null) {
            return "There is no active code for this email. Press Resend code.";
        }
        if (LocalDateTime.now().isAfter(record.expiresAt())) {
            users.deleteCode(user.id(), purpose);
            return "This code has expired. Press Resend code to get a new one.";
        }
        if (record.attempts() >= MAX_CODE_ATTEMPTS) {
            return "Too many wrong attempts. Press Resend code to get a new code.";
        }
        if (!PasswordHasher.verify(code, record.codeHash())) {
            users.countFailedAttempt(user.id(), purpose);
            int left = MAX_CODE_ATTEMPTS - record.attempts() - 1;
            return left > 0 ? "That code is not right. " + left + (left == 1 ? " attempt" : " attempts") + " left."
                    : "That code is not right. Press Resend code to get a new code.";
        }
        users.deleteCode(user.id(), purpose); // One-time: a code cannot be used twice.
        return null;
    }

    private long secondsUntilResend(UserRecord user, CodePurpose purpose) {
        return users.findCode(user.id(), purpose)
                .map(c -> RESEND_SECONDS - Duration.between(c.createdAt(), LocalDateTime.now()).toSeconds())
                .filter(seconds -> seconds > 0)
                .orElse(0L);
    }
}
