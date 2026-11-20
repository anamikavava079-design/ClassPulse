# ClassPulse 2.1: Live Classroom Quiz System

Anamika Edathatta (2024UG000265), Object-Oriented Programming, Vidyashilp University.
A teacher hosts a timed quiz. Students join with a room code and answer from their own windows. Scores update after every question, every game is saved in MySQL, and results and reports are sent by email.

Built with Java (Swing, TCP sockets, virtual threads, JDBC, JNDI DNS, SSL sockets), MySQL and a hand-written SMTP client.

## What it can do

- **Accounts for teachers and students**: register, verify the email with a 6-digit code, sign in with a username or email, forgot/reset password, change password, edit your name.
- **Real email checks**: format check, typo check ("Did you mean @gmail.com?"), DNS mail-server (MX) lookup, then a one-time code. Reset codes are sent **only** to emails that belong to an account.
- **Teachers**: overview dashboard, quiz library (create, edit, duplicate, delete, import CSV), shuffle question order, remove a student from the lobby, live hosting, reports with a "worth reteaching" insight, CSV export, and an emailed report with a CSV attachment.
- **Students**: join with a code, streak bonuses, personal result email, history with an accuracy chart, nine achievement badges, and a profile page. Guests can still join without an account.
- **One smooth window**: animated screen changes, in-window notifications instead of pop-ups, and the Student | Teacher choice as a single sliding switch.

## New in the upgrade (November 2026)

- **Play from a phone with a QR code**: the lobby shows a QR code. Students on the same Wi-Fi scan it and play in the phone's browser, with no app to install, in the same game as app players. The server runs a small built-in web server (port 8080) for this. The QR code generator is written by hand (Reed-Solomon error correction, 8 masks).
- **Certificates**: a signed-in student gets a certificate (a PNG drawn with Java2D) attached to their result email when they win. With other players, the winner gets it (if several share the top score, all of them do). Playing alone, it takes full marks or one mark less (for example 6/6 or 5/6).
- **Classes and homework with automatic emails**: teachers create classes (students join with a 6-character code) and set a quiz as homework with a deadline. Students are emailed when it is set, reminded before the deadline if they have not done it, and get their result by email. After the deadline the teacher gets a summary with a CSV attachment. A background clock checks every 30 seconds, so these emails need nobody to press anything.
- 4 new tables (12 in total). They are created on the next server start; existing data is not touched.

Phone play tips: when Windows asks whether Java may use the network, allow **Private networks**. If the QR code shows the wrong address, start the server with `run-server.bat --phone-host 192.168.x.x` (your Wi-Fi address from `ipconfig`). `--phone-port off` turns phone play off.

## Requirements

- JDK 21 or newer
- MySQL 8 running on localhost, port 3306
- The MySQL driver is already included in `lib`

## Run it

1. Double-click `run-server.bat` and type your MySQL password when asked. The `classpulse_v2` database and its 12 tables are created automatically.
2. Double-click `run-client.bat`. Choose **Teacher**, then **Create an account**. Type the 6-digit code from the email (in demo mode it is printed in the server window).
3. Open **My quizzes**, select a quiz and choose **Host live**. A room code appears.
4. Double-click `run-client.bat` again for each student: sign in as a student (or **Join a quiz as a guest**) and enter the code.
5. Choose **Start quiz** in the teacher window.

After changing code, run `build.bat`.

## Email

Settings are in `config/mail.properties`.

- `mail.mode=console`: **demo mode**. Emails are printed in the server window and saved as `.html` and `.eml` files in `mail-outbox`. No internet needed.
This is the default in this repository, so the app works out of the box.
- `mail.mode=smtp`: real email. For Gmail, turn on 2-Step Verification, create an **App Password** at https://myaccount.google.com/apppasswords, put your address in `mail.smtp.username`, and type the app password when the server asks. It is never saved in a file.

## Tests

`run-tests.bat` asks for your MySQL password, then runs 40 tests against a separate `classpulse_v2_test` database. They use a fake mailer, so no real email is sent.

## CSV quiz format

Columns: `question, A, B, C, D, correct, seconds`. See `sample-data/oop-concepts-quiz.csv`.

## Layout

| Folder | Contents |
| --- | --- |
| `src/classpulse/common` | Messages, data records, wire protocol, validation, CSV |
| `src/classpulse/server` | Sockets, game rooms, scoring, accounts, repositories, email templates |
| `src/classpulse/mail` | SMTP client, MIME builder, demo mailer, DNS mail-server check |
| `src/classpulse/client` | Swing screens, custom widgets and the client socket |
| `test` | Test runner (40 tests) and GUI walkthrough |
| `lib` | MySQL Connector/J driver |
| `config` | Database and email settings (no passwords stored) |
| `sample-data` | Sample quiz CSV |
| `docs` | Latest test results (`test-results.txt`: 40 passed, 0 failed) |
