# 🎄 Christmas Gifts Minigame

An arcade-style Android game built in Java where you slide Santa across the snowy village ground to catch falling Christmas presents thrown by an evil elf, dodge ornament bombs, repair broken gifts with snow crystals, and fill the village Christmas tree!

---

## ✨ Features

- **10 Progressive Levels (`LevelTable`)**
  - Home screen 5×2 level selector (`MainActivity`) with lock states, best-star badges, and persistent high score.
  - Only **Level 1** is unlocked at first; passing a level (`Missed ≤ 3`) unlocks the next level and saves your best star rating (`SharedPreferences`).
- **Slide-to-Move Controls**
  - Smooth horizontal drag controls (`ACTION_MOVE` delta) keep Santa anchored to the snowy ground lane with comfortable finger clearance below.
- **Dynamic Visual Progression**
  - **Sleigh Cargo:** The 3D sleigh visually empties as the elf throws presents (`sleigh_100` → `sleigh_0`), staying loaded until the final gift is thrown.
  - **Village Tree Root:** Presents gradually pile up around the root of the central Christmas tree (`tree_0` → `tree_100`) as you catch them.
  - **Broken Presents on Snow:** Missed presents stay smashed on the snow at the exact X position where they landed.
- **Snowflake Crystals & Combo Scoring**
  - Catching consecutive gifts within 2.5s builds a combo multiplier.
  - Catching a **Snowflake Crystal** repairs one broken present on the snow, reduces your missed count by 1, and adds it to your caught total.
- **BombRain Mode (Levels 4–10)**
  - Periodic **BOMB RAIN!** bursts trigger on harder levels, switching the elf to rapid bomb-only throws with an on-screen warning banner.
- **Animated Level-Complete Card**
  - Displays staggered popping stars, count-up stats (gifts collected, best combo, score), and a 3D **Next Level** button.

---

## ⭐ Star & Level Progression

| Misses in Level | Result | Stars Earned |
| :---: | :---: | :---: |
| `0` | Pass (unlocks next level) | ⭐⭐⭐ (3) |
| `1` | Pass (unlocks next level) | ⭐⭐ (2) |
| `2` – `3` | Pass (unlocks next level) | ⭐ (1) |
| `> 3` or Bomb hit | Game Over | — |

### Level Tuning (`LevelTable`)

| Level | Gift Quota | Spawn Period | Speed | Bomb Chance | BombRain (Interval / Duration / Cadence) |
| :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | 30 | 2000 ms | 1.0× | 4% | — |
| 2 | 40 | 1850 ms | 1.1× | 6% | — |
| 3 | 50 | 1700 ms | 1.2× | 9% | — |
| 4 | 60 | 1550 ms | 1.4× | 12% | 20.0s / 3.5s / 650 ms |
| 5 | 70 | 1400 ms | 1.5× | 15% | 18.0s / 3.5s / 600 ms |
| 6 | 80 | 1250 ms | 1.7× | 18% | 16.0s / 4.0s / 550 ms |
| 7 | 90 | 1100 ms | 1.8× | 20% | 14.0s / 4.0s / 500 ms |
| 8 | 100 | 950 ms | 2.0× | 23% | 12.0s / 4.5s / 450 ms |
| 9 | 110 | 800 ms | 2.1× | 25% | 11.0s / 4.5s / 420 ms |
| 10 | 120 | 700 ms | 2.2× | 28% | 10.0s / 5.0s / 400 ms |

---

## 🗂️ Project Structure

```text
app/src/main/
├── java/com/example/christmasgifts/
│   ├── MainActivity.java       # Home screen, 10-level GridLayout, high score
│   ├── GameView.java           # Custom Canvas game loop, touch, HUD, BombRain, win card
│   ├── GameOver.java           # End-of-run summary, restart & exit
│   ├── LevelTable.java         # 10-level quotas, speeds, bomb chances, BombRain tables
│   ├── LevelProgress.java      # SharedPreferences unlock & best-stars persistence
│   ├── FallingObject.java      # Present, bomb, and snowflake physics/motion
│   ├── FallingObjectType.java  # PRESENT, BOMB, SNOWFLAKE enum
│   └── ElfThrowAnchor.java     # Elf hand anchor coordinates
└── res/
    ├── drawable-nodpi/         # 3D WebP sprites, backgrounds, sleigh/tree checkpoints, app icon
    ├── drawable/               # Vector drawables (buttons, stars, lock, level tiles)
    └── layout/                 # activity_main.xml, level_tile.xml, game_over.xml
```

---

## 🛠️ Tech Stack & Requirements

- **Language:** Java (Java 21 toolchain)
- **Android SDK:** `minSdk 24` · `compileSdk 35` · `targetSdk 35`
- **Application ID:** `com.pank.christmasgifts`
- **UI & Rendering:** Android `View` + `Canvas`, `AppCompat`, Material Components, `PreferenceManager`

---

## 🚀 Building

1. Configure `ANDROID_HOME` / `ANDROID_SDK_ROOT` (or `sdk.dir` in `local.properties`).
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`
3. Build a signed release App Bundle (requires `keystore.properties` outside the git repository):
   ```bash
   ./gradlew bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`
