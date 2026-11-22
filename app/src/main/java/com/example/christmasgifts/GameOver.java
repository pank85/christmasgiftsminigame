package com.example.christmasgifts;

import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


public class GameOver extends AppCompatActivity {
    TextView tvScore;
    TextView tvHighScore;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (getSupportActionBar()!=null)
            getSupportActionBar().hide();
        setContentView(R.layout.game_over);
        Intent intent=getIntent();
        int score=intent.getIntExtra(getString(R.string.score),0);
        int highScore=intent.getIntExtra(getString(R.string.high_score),0);
        int comboPeak = intent.getIntExtra(getString(R.string.combo_peak), 0);
        boolean runWon = intent.getBooleanExtra(getString(R.string.run_won), false);
        int levelReached = intent.getIntExtra(getString(R.string.level_reached), 1);
        tvScore=findViewById(R.id.score);
        tvHighScore=findViewById(R.id.highScore);
        TextView tvComboPeak = findViewById(R.id.combo_peak);
        TextView resultTitle = findViewById(R.id.result_title);
        TextView tvLevelReached = findViewById(R.id.level_reached);
        resultTitle.setText(runWon ? R.string.win_title : R.string.lose_title);
        tvLevelReached.setText(getString(R.string.level_reached_fmt, levelReached));
        tvScore.setText(getString(R.string.score_fmt, score));
        tvHighScore.setText(getString(R.string.high_score_fmt, highScore));
        tvComboPeak.setText(getString(R.string.combo_peak_fmt, comboPeak));

        addListeners();
    }

    private void addListeners() {
        ImageButton reStartButton = findViewById(R.id.btn_restart);
        reStartButton.setOnClickListener(view -> reStartGame());
        ImageButton exitButton = findViewById(R.id.btn_exit);
        exitButton.setOnClickListener(view -> finishAndRemoveTask());
    }


    public void reStartGame() {
        Intent intent=new Intent(GameOver.this,MainActivity.class);
        startActivity(intent);
        finish();
    }

}
