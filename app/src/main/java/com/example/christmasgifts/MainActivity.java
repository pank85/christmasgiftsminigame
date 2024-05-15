package com.example.christmasgifts;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;

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
        addListeners();
    }

    private void addListeners() {
        Button startButton = findViewById(R.id.btn_start);
        startButton.setOnClickListener(view -> startGame());
//        ImageButton exitButton = findViewById(R.id.btn_exit);
//        exitButton.setOnClickListener(view -> finishAndRemoveTask());
    }


    public void startGame() {
        GameView gameView=new GameView(this);
        setContentView(gameView);
    }


}