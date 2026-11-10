# Neil & Teri's Time Killer Games

*Ideal for those long flights where internet is miles away.* ✈️

*Created by Neil Sampson.*

Neil & Teri's Time Killer Games is nine two-player games for Android phones and tablets, played directly between your two devices.
**It works with no internet at all**, so you can play on a plane, because the devices talk to
each other over Bluetooth and/or Wi-Fi. After you pair, the host picks any game from the lobby,
and you can switch games without reconnecting.

| Game | What it is |
|---|---|
| 🎨 **Draw Something** | One draws a word, the other watches it appear live and guesses. Fast guesses score big. |
| 💣 **Bomb Find** | Minesweeper for two. Take turns clearing a shared minefield, dodge bombs, grab power-ups. |
| 🚢 **Sea Battle** | Battleships. Hide your fleet, hunt theirs. Hit a ship and fire again. |
| 🔴 **Drop Four** | The classic four-in-a-row (Connect Four style). Full-screen board, slide-and-release to drop, glowing winning line, three colour themes, optional power discs (bomb and double drop). |
| 🃏 **Memory Match** | Flip cards, find pairs, build streaks. Four card themes, three board sizes. |
| ⚡ **Quick Draw** | A reflex duel: tap first when the screen turns green. Optional fake-out decoys. |
| 🎲 **Lucky Dice** | Push your luck: keep rolling, bank it before you bust. One or two dice. |
| ⚫ **Flip Fight** | Reversi. Trap lines of their discs to flip them. 8×8 or 6×6. |
| 🎴 **Higher or Lower** | Guess whether the next card is higher or lower and climb the prize ladder, like the TV game show. Change card once, optional odds. |

## Play solo, or learn by watching

- **🤖 Solo vs computer.** Press it on the home screen, choose Easy, Normal or Hard, then pick any game as
  usual. The computer is a stand-in for a second phone, so every game, rule and option works exactly as it
  does with a real partner. It only uses what you could see (it can't peek at hidden cards, ships or mines).
  In Draw Something it's practice only: you draw every round and the computer pretends to guess, because it
  can't read a drawing.
- **📖 Learn to play.** Pick a game and watch it play itself with captions explaining each step (and a few
  tips), instead of reading the rules. Pause, step through one caption at a time, or speed it up. Nothing
  needs connecting and nobody else needs to be there.

## Do you know your Holiday? (quiz)

A head-to-head quiz about where you're going. Both phones see the same question and four answers; the
**first correct answer** earns 100 points plus up to 100 for speed, a slower right answer still earns 40, and a
wrong answer costs 25. After each question the right answer and a "did you know" fact appear. The correct answer
is never sent to the other phone until it is revealed, and the answers are shuffled every time.

The first holiday is **Cape Verde · Sal**. The questions are written from general knowledge, so double-check
anything time-sensitive (entry rules, prices) before you travel.

### Adding another holiday
Drop a JSON file in `app/src/main/assets/holidays/`; the app finds it on start-up (it then shows up as a choice in the lobby).

```json
{
  "id": "tenerife",
  "title": "Tenerife",
  "emoji": "🌋",
  "blurb": "One line shown in the lobby.",
  "questions": [
    { "q": "Which volcano towers over Tenerife?", "a": ["Teide", "Etna", "Vesuvius", "Fuji"], "c": 0,
      "fact": "Shown after the answer.", "cat": "Nature" }
  ]
}
```
Each question needs 3 or 4 different answers; `c` is the position (from 0) of the right one. The app shuffles them.
Questions that don't make sense are skipped, and `HolidayTests` checks every bundled pack (at least 30 questions, four unique
answers, the right answer not always in the same slot or always the longest).

### Removing the feature
Set `Features.HOLIDAY` to `false` in `core/Holiday.kt` and the quiz disappears from every menu. To remove it for good, delete
`core/Holiday.kt`, `ui/HolidayScreens.kt`, `assets/holidays/` and the `HOLIDAY` lines the compiler then points at.

## Scores, leaderboards and the Dominator

Every finished game is recorded on both phones for both players (by name). **Scores** on the home screen shows a
leaderboard for each game (ranked by wins, then points) and the overall **Dominator** table: a win is worth 100, a draw 40,
a loss 10, plus up to 25 for the points you scored in the game. Games against an Easy computer, and Draw Something practice,
don't count; scores live only on the phone (and can be reset).

## Sound

Every sound effect is generated in code (`core/Synth.kt`), so there are no audio files. There is a mute button on the home screen.

## Tetris (falling blocks, head to head)

Tetris played side by side: you each play your own board, and each phone shows the other player's board live.
The whole screen is the control, there are no buttons:
* **Slide** your finger sideways: the piece follows. **Tap** the left half to turn left, the right half to turn right.
* **Drag down** and hold: soft drop. **Flick down** (long, fast, straight): hard drop. **Flick up**: hold.
* Mistake protection: a small wobble does nothing, a gesture stays sideways or vertical once recognised, and a hard drop needs a clear flick.
  Three touch feels (Relaxed / Normal / Quick) in ⚙ Settings.
* Settings (⚙ on the home screen and in-game): four block styles (Glass, Neon, Candy, Classic), vibration, optional quiet music (off by default).

Clear rows to send **gift pieces** to your rival. A charge meter builds with every clear (single 1, double 3, triple 5, four 10, plus combo and back-to-back
bonuses); every 2 charge becomes one gift, so even singles send a gift every other line.

How gifts work (modelled on how other versus Tetris games handle attacks, but with pieces you control instead of rising junk rows):
* Gifts wait in a **meter** for 3 seconds. Clearing 2+ rows in that time **cancels** them (oldest first), as in Tetris 99 and similar games.
* The rest join your **NEXT queue** as gold striped pieces. You place them yourself, at normal speed, with full control, like any other piece.
* They arrive **at most every other piece** and not straight after you clear a row, so you always get breathing room.
* Long no-clock matches speed up on their own after 5 minutes ("overtime"), so a match always ends.

Seven game modes:

| Mode | How it works |
|---|---|
| ⚔️ Battle | Last stack standing wins. |
| ⏱ Score attack | Best score when the 2, 3 or 5 minute clock runs out wins. Gifts optional. |
| 🏁 Sprint | First to clear 20, 40 or 60 lines wins. Gifts optional. |
| 💣 Bomb Blitz | Every 6th piece is a bomb. Its blast blows a hole, the blocks above fall in, and the blown-up blocks are sent to your rival as gifts. |
| 🌊 Rising Tide | A junk row with one gap rises from the bottom of both boards every 12 seconds (the same gap on both). |
| 🔥 Turbo | The speed goes up every 25 seconds whatever you do. |
| ⚡ Power Surge | Every 5th piece carries a star block. Clear a row with a star to launch a power: dizzy (rival's left and right swapped), turbo curse (they fall 3x faster), fog (they can't see ahead), double gift, or clean-up (your bottom rows vanish). |

Also: back-to-back four-line clears (x1.5 and an extra gift), perfect clears (empty board: big bonus and four gifts), combos, line-clear flashes,
bomb explosions, ghost piece, hold, next-three preview, wall kicks, soft and hard drop, swipe controls on your board (slide, swipe down, flick to drop),
Gentle / Fast / Frantic start speeds. Both players get exactly the same piece sequence (a shared 7-piece bag), so it is fair, and each phone
simulates its own board so the controls never lag; only the board picture, gift pieces and curses cross the link. It works against the
computer too (Easy / Normal / Hard, and it knows how to aim a bomb) and has a Learn demo.

## Bomb Find (minesweeper for two)

A shared grid (6×6, 8×8, 10×10 or 12×12). You **take turns** tapping a square; a number tells you how
many bombs touch it. Your first square is always safe.

- **Safe square:** +10 each. An empty square opens its neighbours too, so a lucky open scores a lot.
- **Bomb:** −25 (never below 0) and your turn ends. The game carries on.
- **Hidden power-ups** fire when you reveal them: 🎁 Bonus (+30), 🛡️ Shield (blocks your next bomb, +10),
  ✨ Double (your next reveal scores double), ⏩ Extra turn, 🧲 Steal (take 20 points from your opponent),
  📡 Radar (flags a hidden bomb for both of you).
- The game ends when every safe square is open. Highest score wins.

The host's phone holds the real minefield and referees every move; the other phone is only ever sent
what has been revealed, so there is nothing to peek at.

## Sea Battle (battleships)

Each player has a hidden fleet (4 ships on 8×8, 5 on 10×10), placed for you with a **Shuffle** button.
Take turns firing at the other player's grid:

- 🎯 **Shot:** one square. **A hit means you fire again.**
- 📡 **Radar (once):** counts the ship squares in a 3×3 area. Uses your turn.
- 💥 **Cluster bomb (once):** hits a plus-shaped area of 5 squares. Uses your turn.

Sink every enemy ship to win. Fleets never leave your phone: each phone answers the other's shots.

## How Draw Something works

1. **Everyone types their name** and one player taps **Host a game**. The other taps **Join a
   game**, picks the host from the list, and the host taps **Accept**. That's the whole setup.
2. Both players read and tap **I agree to the rules** (see below). Then the host picks how many
   rounds (4–10), how long each drawing gets (60/90/120 s) and the **word difficulty**:
   *Everything* (easy to hard, with the odd extra-hard one), *No easy words*, *Hard*, or
   *Extra hard* (scenes, actions and ideas such as "traffic jam" or "gravity").
3. You **take turns** drawing. The drawer sees five words (always a spread of difficulty) and can pick one
   or tap **🎲 Surprise me** and let the game choose.
4. The drawer draws; the guesser sees every stroke **as it's drawn**, with the category and a
   `_ _ _ _` letter mask as a hint.
5. The guesser types a guess and sends it. The drawer sees it and taps **✓ Correct** or
   **✗ Not quite**. (The game highlights an exact or near match to help, but the drawer decides.)
6. Points are awarded, roles swap, repeat. Highest total wins; **Play again** is one tap.

Drawing tools: 12 colours, 4 brush sizes, eraser, undo and clear. No fill bucket, no layers.

## Rules

Both players agree to these before the game starts (they're always one tap away under **📜 Rules**):

1. Drawing only. No letters, words or numbers, no labels or captions.
2. Don't spell it out: no initials, no first letter, no letters hidden in the drawing.
3. No spoken or acted-out hints.
4. Guessers: no peeking at the drawer's screen.
5. Be fair: accept right guesses, decline wrong ones, only report a genuine rule break.

**Reporting a rule break:** if the drawer breaks a rule, the guesser taps **⚑ Rule break** and picks
what happened (text, label/caption, spelling it out, or hints). The drawer sees the report and
chooses **They're right** or **I disagree**.

- If the drawer agrees: they lose **20 points**, their drawing is **cleared** (so it has to be
  redrawn without the offending part) and any guess that was waiting is cancelled **without**
  costing the guesser anything.
- If the drawer disagrees: nobody loses points and play carries on.
- Each player can report at most **3 times per round**, one at a time, so reports can't be spammed.
- The clock doesn't stop for a report, and the round can't time out while one is unanswered.

## Scoring (designed to be fair to both players)

| | |
|---|---|
| Round value | Up to **100** points for an instant guess, falling smoothly to **30%** when the clock runs out |
| Who earns it | **Both players**, from the same clock, so a clear drawing and a sharp eye both pay, and neither gains by sabotaging the other |
| Difficulty | Easy ×1.0 · Medium ×1.25 · Hard ×1.5 · Extra hard ×2.0, applied to both players |
| Wrong guess | Guesser loses **5** for each guess the drawer rejects |
| Skipping a word | Drawer loses **15** per skip (you then pick a new word) |
| Rule break | Drawer loses **20** each time they agree to a report (more than a skip, because it's cheating) |
| Time runs out | Nobody scores (skips still cost the drawer); scores never go below 0 |
| Verdict delay | Time is stamped when the guess *arrives*, so a drawer who is slow to tap Correct never costs anyone points |

Roles alternate every round, so both players draw and guess equally often. Because both
players are paid from the same clock, the thing that separates them is their own accuracy:
fewer wrong guesses, skips and rule breaks. The numbers live in
[`Scoring.kt`](app/src/main/java/com/neil1454/drawsomething/core/Scoring.kt) if you want to tune them.

## How the devices connect

The app uses Google's **Nearby Connections** API (`play-services-nearby`). It chooses the best
available radio by itself (Bluetooth, BLE, Wi-Fi Direct / hotspot / same-network Wi-Fi) and
needs **no internet**, so it works in airplane mode as long as you switch Bluetooth (and/or
Wi-Fi) back on. The first time you host or join, Android asks for the Nearby/Bluetooth
permissions (on Android 11 and older that means Location, which must also be switched on).

Requirements: Android 8.0+ and Google Play services on both devices (standard on almost
every phone/tablet; Amazon Fire tablets don't have it).

## Security

- **Encrypted, authenticated link.** Nearby Connections encrypts everything between the two
  devices, and the connection is only made after a manual accept from **both** players.
- **Pairing code.** When two devices connect, both screens show the same 4-digit code derived from
  the encrypted handshake. Each player checks it matches before tapping **Codes match**. Someone
  who isn't your partner, or something pretending to be them, can't produce a matching code.
- **Door locked behind you.** The moment your partner is connected, the app stops advertising and
  searching, rejects every other connection attempt, and only reads data from the one paired device.
  While hosting, the host sees any extra requests (name + code) and can pick the right one or decline.
  People without the app can't see or join anything at all.
- **The internet is used for one thing only: updates.** The games never touch the network. The app
  does hold the INTERNET permission, but the only code that uses it is the **Check for updates**
  button on the home screen. Nothing runs in the background and nothing is sent when you play. When
  you tap it, the app asks GitHub for this project's latest release and, if you agree, downloads it.
  Every connection is HTTPS and only to GitHub's own hosts (also across redirects); the file must come
  from this repository's releases; its size and SHA-256 are checked; and before Android is asked to
  install it the app confirms it is the same app, newer, and signed by the same key as your copy.
  Android then still asks you to tap Install. The app keeps no account, location or contact data
  (only your name, saved on your phone), and cloud backup is switched off.
- **Defensive decoding.** Every incoming message is size-limited and range-checked (text length,
  coordinates, scores, number of strokes), so a damaged or hostile packet can't crash the app or use
  up memory. Unit tests cover this.
- **Be aware.** The pairing check is only as good as the players: always compare the code. While you
  are *hosting* (before your partner joins) your name is visible to other copies of this app nearby,
  so host just before pairing. Scores are worked out by the drawer's phone and trusted by the
  guesser's: fine for a friendly game, not cheat-proof.
- **Signing key.** Android only installs an update that is signed with the same key as the installed
  copy. By default builds use a convenience key committed to this repo (so anyone can build the
  project). That key is public, so for a real release a *private* key can be stored as GitHub secrets
  (`RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`).
  The build uses it automatically, and only for builds of `main`: branches and pull requests (including
  from forks) never see it. Switching keys means each phone must uninstall once, because Android treats it
  as a different publisher.
- **Release builds.** Released APKs are proper release builds (not debuggable), checked in CI to be signed
  with the intended key. The build robot has read-only access except in the one job that publishes a release.

## Updating

Tap **🔄 Check for updates** on the home screen. The app looks at the latest GitHub release and, if it
is newer, offers to download and install it. The first time, Android asks you to allow this app to
install updates (Settings → “Allow from this source”). It works without any login or key as long as
the GitHub repository is **public**.

**Tidying up:** the downloaded file is kept only in the app's private cache folder (one file, never in your Downloads,
gallery or files) and is named with its version, e.g. `TimeKillerGames-v2.4.3.apk`.
* Android's "allow installs from this app" switch is checked **before** anything is downloaded, so you are never made to download twice because of it.
* **Version cross-check:** every time the app starts, and every time it comes back on screen, it reads the version number
  out of each downloaded APK (not just the name) and compares it with the version that is installed. A file is deleted if it is
  the version you already have, an older version, a different app, damaged, or unfinished. Only a file that is being installed
  right now is kept. A newer file that was never installed is also removed (the app downloads a fresh one if you ask again).
* The home screen shows how much space update files take, and **Details** lists each file with the reason it is (not) needed,
  what the last tidy-up removed, and a **Delete all** button.
* These files are in the app's *private* storage (`/data/data/com.neil1454.drawsomething/cache`), which Android does not let file
  managers open. That is by design. Check it from Settings → Apps → this app → Storage: the Cache size should be tiny, and
  "Clear cache" empties it. Samsung's My Files "APK files" view only lists APKs in shared folders, so ours never appear there.

## Building

Open the project in Android Studio and run it, or:

```
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Every push also builds it in
GitHub Actions (**Actions → Android build → artifact `draw-something-debug-apk`**); download
and install it on both devices (allow "install unknown apps" for your browser/Files app).

## Code layout

```
app/src/main/java/com/neil1454/drawsomething/
  core/   pure Kotlin game logic: engine, scoring, words, messages, drawing board (JVM-testable)
  net/    NearbyTransport: the Bluetooth/Wi-Fi link
  ui/     Jetpack Compose screens and the drawing canvas
  GameViewModel.kt, MainActivity.kt
app/src/test/   unit tests, including full two-player games played through the real wire format
```

The drawer's phone is the referee for its round (word, clock, verdict, score); the guesser's
phone just displays what it's told. The secret word is never sent to the guesser until the
round ends. Drawings are sent as normalised (0–1) strokes, so a phone and a tablet show the
identical picture.

## Status

Version 2.4.3. Two players (or solo vs the computer), nine games, in-app updates, learn mode. The game logic is covered by unit tests; the Bluetooth/Wi-Fi link
and the touch UI need testing on two real devices.

## Credits

Designed and created by **Neil Sampson**. © Neil Sampson.
