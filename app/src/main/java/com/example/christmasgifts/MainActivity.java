package com.example.christmasgifts;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (getSupportActionBar()!=null)
            getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        int highScore = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(getString(R.string.high_score), 0);
        TextView menuHighScore = findViewById(R.id.menu_high_score);
        menuHighScore.setText(getString(R.string.menu_high_score_fmt, highScore));
        addListeners();
    }

    private void addListeners() {
        Button startButton = findViewById(R.id.btn_start);
        startButton.setOnClickListener(view -> startGame());
    }


    public void startGame() {
        GameView gameView=new GameView(this);
        setContentView(gameView);
    }


}