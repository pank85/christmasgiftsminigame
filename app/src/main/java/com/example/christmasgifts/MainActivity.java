package com.example.christmasgifts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private GameView gameView;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        hideSystemBars();
        if (getSupportActionBar()!=null)
            getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int highScore = prefs.getInt(getString(R.string.high_score), 0);
        TextView menuHighScore = findViewById(R.id.menu_high_score);
        menuHighScore.setText(getString(R.string.menu_high_score_fmt, highScore));
        bindLevelGrid(prefs);
    }

    private void bindLevelGrid(SharedPreferences prefs) {
        GridLayout grid = findViewById(R.id.level_grid);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int level = 1; level <= LevelTable.LEVEL_COUNT; level++) {
            View tile = inflater.inflate(R.layout.level_tile, grid, false);
            TextView number = tile.findViewById(R.id.level_number);
            View starsRow = tile.findViewById(R.id.level_stars);
            ImageView lock = tile.findViewById(R.id.level_lock);
            ImageView[] stars = new ImageView[] {
                    tile.findViewById(R.id.star_1),
                    tile.findViewById(R.id.star_2),
                    tile.findViewById(R.id.star_3)
            };
            number.setText(String.valueOf(level));
            boolean unlocked = LevelProgress.isUnlocked(prefs, level);
            int earned = LevelProgress.stars(prefs, level);
            if (unlocked && earned > 0) {
                starsRow.setVisibility(View.VISIBLE);
                for (int i = 0; i < stars.length; i++) {
                    stars[i].setVisibility(i < earned ? View.VISIBLE : View.INVISIBLE);
                }
            } else {
                starsRow.setVisibility(View.INVISIBLE);
            }
            if (unlocked) {
                tile.setAlpha(1f);
                lock.setVisibility(View.GONE);
                tile.setEnabled(true);
                tile.setClickable(true);
                final int selected = level;
                tile.setOnClickListener(view -> startGame(selected));
                tile.setContentDescription(earned > 0
                        ? getString(R.string.level_stars_desc, level, earned)
                        : getString(R.string.level_open_desc, level));
            } else {
                tile.setAlpha(0.45f);
                lock.setVisibility(View.VISIBLE);
                tile.setEnabled(false);
                tile.setClickable(false);
                tile.setOnClickListener(null);
                tile.setContentDescription(getString(R.string.level_locked_desc, level));
            }
            grid.addView(tile);
        }
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (gameView != null) gameView.setActivityResumed(true);
    }

    @Override protected void onPause() {
        resumed = false;
        if (gameView != null) gameView.setActivityResumed(false);
        super.onPause();
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    public void startGame(int level) {
        gameView = new GameView(this, level);
        setContentView(gameView);
        gameView.setActivityResumed(resumed);
    }
}
