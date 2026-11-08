# Draw Something

*Created by Neil Sampson.*

A two-player drawing and guessing game for Android phones and tablets. One of you draws a
word, the other watches it appear live and tries to guess it. **It works with no internet at
all**, so you can play on a plane, because the two devices talk to each other directly over
Bluetooth and/or Wi-Fi.

## How a game works

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

Version 1.0. Two players. The game logic is covered by unit tests; the Bluetooth/Wi-Fi link
and the touch UI need testing on two real devices.

## Credits

Designed and created by **Neil Sampson**. © Neil Sampson.
