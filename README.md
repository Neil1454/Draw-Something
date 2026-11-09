# Neil & Teri's Time Killer Games

*Ideal for those long flights where internet is miles away.* ✈️

*Created by Neil Sampson.*

Neil & Teri's Time Killer Games is three two-player games for Android phones and tablets, played directly between your two devices.
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
- **Signing key.** Builds are signed with a fixed key committed to the repo so updates install over
  the old version. It is a convenience key for sideloading, not a secret.

## Updating

Tap **🔄 Check for updates** on the home screen. The app looks at the latest GitHub release and, if it
is newer, offers to download and install it. The first time, Android asks you to allow this app to
install updates (Settings → “Allow from this source”). It works without any login or key as long as
the GitHub repository is **public**.

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

Version 1.5. Two players, eight games, in-app updates. The game logic is covered by unit tests; the Bluetooth/Wi-Fi link
and the touch UI need testing on two real devices.

## Credits

Designed and created by **Neil Sampson**. © Neil Sampson.
