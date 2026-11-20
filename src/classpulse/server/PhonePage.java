package classpulse.server;

/**
 * The page a phone opens after scanning the QR code (added in the phone upgrade). It is one
 * self-contained file, kept in a Java text block so the server has no extra files to find.
 * All text from the server is shown with textContent, never innerHTML, so it cannot inject HTML.
 */
final class PhonePage {
    private PhonePage() {
    }

    static final String HTML = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <meta name="theme-color" content="#4338CA">
            <title>ClassPulse</title>
            <style>
              :root { --indigo:#4F46E5; --indigo-dark:#4338CA; --violet:#7C3AED; --ink:#111827; --muted:#6B7280;
                      --bg:#F4F6FB; --green:#16A34A; --red:#DC2626; }
              * { box-sizing:border-box; }
              html, body { margin:0; height:100%; }
              body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; background:var(--bg);
                     color:var(--ink); -webkit-tap-highlight-color:transparent; }
              header { background:linear-gradient(135deg,var(--indigo-dark),var(--violet)); color:#fff;
                       padding:14px 18px; display:flex; align-items:center; justify-content:space-between; }
              header b { font-size:19px; letter-spacing:.3px; }
              header span { font-size:13px; opacity:.85; max-width:55%; overflow:hidden; text-overflow:ellipsis;
                            white-space:nowrap; }
              main { padding:18px; max-width:560px; margin:0 auto; }
              .card { background:#fff; border-radius:18px; padding:22px; box-shadow:0 4px 18px rgba(17,24,39,.08); }
              .center { text-align:center; }
              h1 { font-size:26px; margin:6px 0 8px; }
              h2 { font-size:21px; line-height:1.35; margin:4px 0 14px; }
              p { color:var(--muted); margin:6px 0; }
              label { display:block; font-weight:700; font-size:13px; color:var(--muted); margin:14px 0 6px; }
              input { width:100%; font-size:20px; padding:13px 14px; border:1.5px solid #D1D5DB; border-radius:12px;
                      outline:none; }
              input:focus { border-color:var(--indigo); }
              #code { text-transform:uppercase; letter-spacing:6px; text-align:center; font-weight:800; font-size:26px; }
              button.main { width:100%; margin-top:18px; padding:15px; font-size:18px; font-weight:700; color:#fff;
                            background:var(--indigo); border:0; border-radius:14px; }
              button.main:active { background:var(--indigo-dark); }
              .error { color:var(--red); font-weight:600; min-height:20px; margin-top:10px; }
              .pill { display:inline-block; background:#EEF2FF; color:var(--indigo-dark); font-weight:700; font-size:13px;
                      padding:5px 12px; border-radius:99px; }
              .bar { height:10px; background:#E5E7EB; border-radius:99px; overflow:hidden; margin:12px 0 16px; }
              .bar div { height:100%; background:var(--indigo); width:100%; transition:width .2s linear; }
              .options { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
              .options button { min-height:96px; border:0; border-radius:16px; color:#fff; font-size:17px; font-weight:700;
                                padding:10px; word-break:break-word; }
              .options button:disabled { opacity:.45; }
              .options button.picked { opacity:1; outline:4px solid #111827; }
              .o0 { background:#2563EB; } .o1 { background:#EA580C; } .o2 { background:#0D9488; } .o3 { background:#DB2777; }
              .big { font-size:56px; margin:4px 0; }
              .good { color:var(--green); } .bad { color:var(--red); }
              .stats { display:flex; gap:10px; margin:16px 0 4px; }
              .stats div { flex:1; background:#EEF2FF; border-radius:12px; padding:10px 6px; }
              .stats b { display:block; font-size:22px; color:var(--indigo-dark); }
              .stats small { color:var(--muted); }
              ol { text-align:left; padding-left:24px; margin:14px 0 0; }
              ol li { padding:6px 0; border-bottom:1px solid #F1F2F6; }
              ol li.me { font-weight:800; color:var(--indigo-dark); }
              .hidden { display:none; }
              .dots::after { content:''; animation:dots 1.4s infinite; }
              @keyframes dots { 0%{content:''} 33%{content:'.'} 66%{content:'..'} 100%{content:'...'} }
              /* ----- Design upgrade: depth, motion and celebration ----- */
              body { background:
                       radial-gradient(circle at 100% 0%, rgba(124,58,237,.16), transparent 45%),
                       radial-gradient(circle at 0% 100%, rgba(79,70,229,.12), transparent 40%), var(--bg);
                     min-height:100vh; }
              header { box-shadow:0 6px 20px rgba(67,56,202,.35); position:sticky; top:0; z-index:5; }
              .card { animation:rise .35s ease-out; border:1px solid rgba(99,102,241,.08); }
              section:not(.hidden) { animation:rise .3s ease-out; }
              @keyframes rise { from { opacity:0; transform:translateY(14px); } to { opacity:1; transform:none; } }
              button.main { background:linear-gradient(135deg,var(--indigo),var(--violet));
                            box-shadow:0 8px 20px rgba(79,70,229,.35); transition:transform .1s, box-shadow .1s; }
              button.main:active { transform:scale(.97); box-shadow:0 4px 10px rgba(79,70,229,.3); }
              .options button { box-shadow:0 6px 0 rgba(0,0,0,.18); transition:transform .08s, box-shadow .08s; }
              .options button:active { transform:translateY(4px); box-shadow:0 2px 0 rgba(0,0,0,.18); }
              .options button.picked { transform:scale(1.03); }
              .big { animation:pop .5s cubic-bezier(.2,1.6,.4,1); }
              @keyframes pop { from { transform:scale(.3); opacity:0; } to { transform:scale(1); opacity:1; } }
              .bar div { background:linear-gradient(90deg,var(--indigo),var(--violet)); }
              .stats div { background:linear-gradient(135deg,#EEF2FF,#F5F3FF); }
              ol li.me { background:#EEF2FF; border-radius:8px; padding-left:6px; }
              .confetti { position:fixed; top:-12px; width:9px; height:5px; z-index:20; pointer-events:none;
                          animation:fall linear forwards; }
              @keyframes fall { to { transform:translateY(105vh) rotate(720deg); } }
            </style>
            </head>
            <body>
            <header><b>&#9889; ClassPulse</b><span id="who"></span></header>
            <main>
              <section id="join" class="card">
                <h1>Join a live quiz</h1>
                <p>Type the room code on the board and your name.</p>
                <label for="code">Room code</label>
                <input id="code" maxlength="6" autocomplete="off" autocapitalize="characters" inputmode="text">
                <label for="name">Your name</label>
                <input id="name" maxlength="20" autocomplete="nickname">
                <button class="main" id="joinBtn">Join</button>
                <div class="error" id="joinError"></div>
              </section>

              <section id="wait" class="card center hidden">
                <div class="big">&#9203;</div>
                <h1 id="waitTitle">You're in!</h1>
                <p id="waitText">Waiting for the teacher to start<span class="dots"></span></p>
                <p><span class="pill" id="playerCount"></span></p>
              </section>

              <section id="question" class="hidden">
                <span class="pill" id="qProgress"></span>
                <div class="bar"><div id="timer"></div></div>
                <h2 id="qText"></h2>
                <div class="options" id="options"></div>
                <p class="center" id="qNote"></p>
              </section>

              <section id="reveal" class="card center hidden">
                <div class="big" id="rIcon"></div>
                <h1 id="rTitle"></h1>
                <p id="rText"></p>
                <div class="stats">
                  <div><b id="rTotal"></b><small>Score</small></div>
                  <div><b id="rRank"></b><small>Rank</small></div>
                  <div><b id="rStreak"></b><small>Streak</small></div>
                </div>
                <p>Waiting for the next question<span class="dots"></span></p>
              </section>

              <section id="over" class="card center hidden">
                <div class="big">&#127942;</div>
                <h1 id="oTitle"></h1>
                <p id="oText"></p>
                <ol id="oRanking"></ol>
                <button class="main" id="againBtn">Join another quiz</button>
              </section>

              <section id="closed" class="card center hidden">
                <div class="big">&#128075;</div>
                <h1>The room has closed</h1>
                <p id="closedText"></p>
                <button class="main" id="closedBtn">Join another quiz</button>
              </section>
            </main>
            <script>
            "use strict";
            const $ = id => document.getElementById(id);
            const screens = ["join", "wait", "question", "reveal", "over", "closed"];
            let token = null, last = 0, myName = "", qIndex = -1, deadline = 0, timerHandle = null, polling = false;

            function show(id) { screens.forEach(s => $(s).classList.toggle("hidden", s !== id)); }
            function post(path, data) {
              return fetch(path, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
                                   body: new URLSearchParams(data) }).then(r => r.json());
            }

            const params = new URLSearchParams(location.search);
            if (params.get("code")) { $("code").value = params.get("code").toUpperCase(); }
            try { $("name").value = localStorage.getItem("classpulseName") || ""; } catch (e) {}
            $("joinBtn").onclick = join;
            $("name").onkeydown = e => { if (e.key === "Enter") join(); };
            $("againBtn").onclick = $("closedBtn").onclick = () => { token = null; show("join"); };

            function join() {
              const code = $("code").value.trim().toUpperCase(), name = $("name").value.trim();
              $("joinError").textContent = "";
              if (code.length !== 6) { $("joinError").textContent = "The room code has 6 characters."; return; }
              if (!name) { $("joinError").textContent = "Please type your name."; return; }
              $("joinBtn").disabled = true;
              post("/api/join", { code, name }).then(r => {
                $("joinBtn").disabled = false;
                if (!r.ok) { $("joinError").textContent = r.error; return; }
                try { localStorage.setItem("classpulseName", name); } catch (e) {}
                token = r.token; last = 0; myName = r.name;
                $("who").textContent = r.name + " \\u00b7 " + r.title;
                $("waitTitle").textContent = "You're in!";
                $("waitText").firstChild.textContent = "Waiting for the teacher to start";
                show("wait");
                if (!polling) poll();
              }).catch(() => { $("joinBtn").disabled = false; $("joinError").textContent = "Could not reach the ClassPulse server."; });
            }

            function poll() {
              if (!token) { polling = false; return; }
              polling = true;
              const mine = token;
              fetch("/api/poll?token=" + encodeURIComponent(mine) + "&after=" + last)
                .then(r => r.json())
                .then(r => {
                  if (mine !== token) return;
                  if (!r.ok) { token = null; $("closedText").textContent = "Your place in the room was lost."; show("closed"); return; }
                  r.events.forEach(handle);
                  last = r.last;
                })
                .catch(() => new Promise(done => setTimeout(done, 1500)))
                .finally(() => { polling = false; if (token) poll(); });
            }

            function handle(e) {
              switch (e.type) {
                case "players": $("playerCount").textContent = e.count + (e.count === 1 ? " player" : " players") + " in the room"; break;
                case "question": startQuestion(e); break;
                case "answer":
                  if (e.accepted) { $("qNote").textContent = "Answer locked in! Waiting for the others..."; }
                  else { $("qNote").textContent = e.text; }
                  break;
                case "ended": revealAnswer(e); break;
                case "over": gameOver(e); break;
                case "closed": stopTimer(); token = null; $("closedText").textContent = e.reason; show("closed"); break;
              }
            }

            function startQuestion(e) {
              qIndex = e.index;
              $("qProgress").textContent = "Question " + (e.index + 1) + " of " + e.total;
              $("qText").textContent = e.text;
              $("qNote").textContent = "";
              const box = $("options");
              box.textContent = "";
              e.options.forEach((text, i) => {
                const b = document.createElement("button");
                b.className = "o" + i;
                b.textContent = String.fromCharCode(65 + i) + ". " + text;
                b.onclick = () => answer(i, b);
                box.appendChild(b);
              });
              deadline = Date.now() + e.seconds * 1000;
              const total = e.seconds * 1000;
              stopTimer();
              timerHandle = setInterval(() => {
                const left = Math.max(0, deadline - Date.now());
                $("timer").style.width = (100 * left / total) + "%";
                $("timer").style.background = left < total / 4 ? "#DC2626" : "#4F46E5";
                if (left === 0) stopTimer();
              }, 100);
              show("question");
            }

            function stopTimer() { if (timerHandle) { clearInterval(timerHandle); timerHandle = null; } }

            /** A short burst of falling confetti, for a correct answer or the final result. */
            function confetti() {
              const colours = ["#4F46E5", "#7C3AED", "#F59E0B", "#10B981", "#EC4899", "#0EA5E9"];
              for (let i = 0; i < 70; i++) {
                const piece = document.createElement("div");
                piece.className = "confetti";
                piece.style.left = (Math.random() * 100) + "vw";
                piece.style.background = colours[i % colours.length];
                piece.style.animationDuration = (1.6 + Math.random() * 1.6) + "s";
                piece.style.animationDelay = (Math.random() * 0.4) + "s";
                document.body.appendChild(piece);
                setTimeout(() => piece.remove(), 3800);
              }
            }

            function answer(choice, button) {
              document.querySelectorAll("#options button").forEach(b => b.disabled = true);
              button.classList.add("picked");
              $("qNote").textContent = "Sending...";
              post("/api/answer", { token, index: qIndex, choice }).catch(() => { $("qNote").textContent = "Could not send the answer."; });
            }

            function revealAnswer(e) {
              stopTimer();
              const you = e.you || {};
              if (!you.answered) { $("rIcon").textContent = "\\u23F0"; $("rTitle").textContent = "Time's up!"; $("rTitle").className = "bad"; }
              else if (you.correct) { $("rIcon").textContent = "\\u2705"; $("rTitle").textContent = "Correct! +" + you.points; $("rTitle").className = "good"; confetti(); }
              else { $("rIcon").textContent = "\\u274C"; $("rTitle").textContent = "Not this time"; $("rTitle").className = "bad"; }
              $("rText").textContent = you.bonus > 0 ? "Streak bonus: +" + you.bonus + " points!" : "";
              $("rTotal").textContent = you.total ?? 0;
              $("rRank").textContent = "#" + (you.rank ?? "-");
              $("rStreak").textContent = you.streak ?? 0;
              show("reveal");
            }

            function gameOver(e) {
              stopTimer();
              const you = e.you || {};
              $("oTitle").textContent = "You finished #" + you.rank;
              $("oText").textContent = you.total + " points  \\u00b7  " + e.correct + " of " + e.questions + " correct";
              const list = $("oRanking");
              list.textContent = "";
              e.ranking.slice(0, 10).forEach(r => {
                const li = document.createElement("li");
                li.textContent = r.name + " \\u2014 " + r.score;
                if (r.name === myName) li.className = "me";
                list.appendChild(li);
              });
              token = null;
              show("over");
              if (you.rank === 1) confetti();
            }

            window.addEventListener("pagehide", () => {
              if (token && navigator.sendBeacon) {
                navigator.sendBeacon("/api/leave", new URLSearchParams({ token }));
              }
            });
            </script>
            </body>
            </html>
            """;
}
