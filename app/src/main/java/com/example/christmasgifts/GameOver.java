package com.example.christmasgifts;

import android.content.Intent;
import android.graphics.Color;
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
        if(score==0 && highScore==0)
            reStartGame();
        tvScore=findViewById(R.id.score);
        tvHighScore=findViewById(R.id.highScore);
        tvScore.setTextColor(Color.RED);
        tvHighScore.setTextColor(Color.RED);
        tvScore.setText(String.format( "Score: %s",score));
        tvHighScore.setText(String.format("High Score: %s",highScore));

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
