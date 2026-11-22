package com.example.christmasgifts;

import android.content.SharedPreferences;

public final class LevelProgress {
    public static final int MAX_STARS = 3;
    public static final String UNLOCKED_KEY = "level_unlocked";

    private LevelProgress() {}

    public static String starKey(int level) {
        LevelTable.index(level);
        return "level_stars_" + level;
    }

    public static int starsForMisses(int missedPresents) {
        if (missedPresents <= 0) {
            return 3;
        }
        if (missedPresents == 1) {
            return 2;
        }
        if (missedPresents == 2 || missedPresents == 3) {
            return 1;
        }
        return 0;
    }

    public static int highestUnlocked(SharedPreferences prefs) {
        int stored;
        try {
            stored = prefs.getInt(UNLOCKED_KEY, 1);
        } catch (ClassCastException ignored) {
            return 1;
        }
        if (stored <= 0) {
            return 1;
        }
        if (stored > LevelTable.LEVEL_COUNT) {
            return LevelTable.LEVEL_COUNT;
        }
        return stored;
    }

    public static boolean isUnlocked(SharedPreferences prefs, int level) {
        LevelTable.index(level);
        if (level == 1) {
            return true;
        }
        return highestUnlocked(prefs) >= level;
    }

    public static int stars(SharedPreferences prefs, int level) {
        int stored;
        try {
            stored = prefs.getInt(starKey(level), 0);
        } catch (ClassCastException ignored) {
            return 0;
        }
        if (stored < 0) {
            return 0;
        }
        if (stored > MAX_STARS) {
            return MAX_STARS;
        }
        return stored;
    }

    public static int recordPass(SharedPreferences prefs, int level, int missedPresents) {
        int earned = starsForMisses(missedPresents);
        if (earned == 0) {
            return stars(prefs, level);
        }
        int best = Math.max(stars(prefs, level), earned);
        int unlocked = Math.max(highestUnlocked(prefs), Math.min(LevelTable.LEVEL_COUNT, level + 1));
        prefs.edit()
                .putInt(starKey(level), best)
                .putInt(UNLOCKED_KEY, unlocked)
                .apply();
        return best;
    }
}
