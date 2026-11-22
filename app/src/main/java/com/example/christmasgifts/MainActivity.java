package com.example.christmasgifts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
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
        int highScore = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(getString(R.string.high_score), 0);
        TextView menuHighScore = findViewById(R.id.menu_high_score);
        menuHighScore.setText(getString(R.string.menu_high_score_fmt, highScore));
        addListeners();
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

    private void addListeners() {
        ImageButton startButton = findViewById(R.id.btn_start);
        startButton.setOnClickListener(view -> startGame());
    }


    public void startGame() {
        gameView = new GameView(this);
        setContentView(gameView);
        gameView.setActivityResumed(resumed);
    }


}