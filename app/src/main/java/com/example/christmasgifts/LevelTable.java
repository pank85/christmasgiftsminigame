package com.example.christmasgifts;

public final class LevelTable {
    public static final int LEVEL_COUNT = 10;

    private static final long[] SPAWN_PERIODS_MS = {
        2000, 1850, 1700, 1550, 1400, 1250, 1100, 950, 800, 700
    };
    private static final double[] SPEED_MULTIPLIERS = {
        1.0, 1.1, 1.2, 1.4, 1.5, 1.7, 1.8, 2.0, 2.1, 2.2
    };
    private static final int[] BOMB_CHANCES_PERCENT = {
        4, 6, 9, 12, 15, 18, 20, 23, 25, 28
    };
    public static final int BOMB_RAIN_MIN_LEVEL = 4;
    private static final long[] BOMB_RAIN_INTERVALS_MS = {
        0, 0, 0, 20000, 18000, 16000, 14000, 12000, 11000, 10000
    };
    private static final long[] BOMB_RAIN_DURATIONS_MS = {
        0, 0, 0, 3500, 3500, 4000, 4000, 4500, 4500, 5000
    };
    private static final long[] BOMB_RAIN_SPAWN_PERIODS_MS = {
        0, 0, 0, 650, 600, 550, 500, 450, 420, 400
    };

    private LevelTable() {}

    static int index(int level) {
        if (level < 1 || level > LEVEL_COUNT) {
            throw new IllegalArgumentException("Level must be between 1 and " + LEVEL_COUNT);
        }
        return level - 1;
    }

    public static int quota(int level) {
        return 30 + index(level) * 10;
    }

    public static long spawnPeriodMs(int level) {
        return SPAWN_PERIODS_MS[index(level)];
    }

    public static double speedMultiplier(int level) {
        return SPEED_MULTIPLIERS[index(level)];
    }

    public static int bombChancePercent(int level) {
        return BOMB_CHANCES_PERCENT[index(level)];
    }

    public static int snowflakeChancePercent(int level) {
        index(level);
        return 12;
    }

    public static boolean hasBombRain(int level) {
        index(level);
        return level >= BOMB_RAIN_MIN_LEVEL;
    }

    public static long bombRainIntervalMs(int level) {
        if (!hasBombRain(level)) {
            return 0;
        }
        return BOMB_RAIN_INTERVALS_MS[index(level)];
    }

    public static long bombRainDurationMs(int level) {
        if (!hasBombRain(level)) {
            return 0;
        }
        return BOMB_RAIN_DURATIONS_MS[index(level)];
    }

    public static long bombRainSpawnPeriodMs(int level) {
        if (!hasBombRain(level)) {
            return 0;
        }
        return BOMB_RAIN_SPAWN_PERIODS_MS[index(level)];
    }
}
