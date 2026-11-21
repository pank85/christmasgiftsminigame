package com.example.christmasgifts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends View {
    private final Context context;
    static int dWidth, dHeight, gameViewTop;
    private float basketX, basketY, oldX, oldBasketX;
    private Bitmap basket, ground, background, bombImg, snowflakeImg, explosionBitmap, explosion;
    private Rect rectBackground, rectGround;
    private Handler handler;
    private Runnable runnableFallingPresents, runnableNewPresents, gameOverRunnable;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] speckX = new float[12], speckY = new float[12], speckSpeed = new float[12];
    private final float[] burstX = new float[8], burstY = new float[8];
    private int burstCount;
    private long burstStartMs, explosionStartMs;
    private final Random random = new Random();
    private final List<FallingObject> fallingObjects = new ArrayList<>();
    private final List<Bitmap> presentImages = new ArrayList<>();
    private final int moveObjectPeriod = 10;
    private int addNewObjectPeriod = 2000;
    private double presentSpeedMultiplier = 1.00;
    private final int speedUpCounterStartValue = 10;
    private int speedUpCounter = speedUpCounterStartValue;
    private int score, combo, comboPeak, bonusLives, missedPresents, highScore;
    private long lastPresentCatchMs;
    private SharedPreferences sharedPreferences;

    public GameView(Context context) {
        super(context);
        this.context = context;
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        InitScreenSize();
        InitBitmaps();
        InitRest();
        Start();
    }

    private void InitRest() {
        textPaint.setColor(ContextCompat.getColor(context, R.color.festive_cream));
        textPaint.setTextSize(50);
        textPaint.setTypeface(ResourcesCompat.getFont(context, R.font.winter_snow_regular));
        textPaint.setStyle(Paint.Style.FILL);
        speckPaint.setColor(Color.WHITE);
        for (int i = 0; i < speckX.length; i++) {
            speckX[i] = random.nextFloat() * dWidth;
            speckY[i] = gameViewTop + random.nextFloat() * (dHeight - gameViewTop);
            speckSpeed[i] = 0.5f + random.nextFloat();
        }
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        highScore = sharedPreferences.getInt(context.getString(R.string.high_score), 0);
    }

    private void Start() {
        handler = new Handler();
        runnableFallingPresents = () -> {
            handleFallingObjects();
            postInvalidate();
            if (checkGameOver()) gameOver();
            else handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
        };
        runnableNewPresents = () -> {
            addNewFallingObject();
            postInvalidate();
            if (!checkGameOver()) handler.postDelayed(runnableNewPresents, addNewObjectPeriod);
        };
        handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
        handler.postDelayed(runnableNewPresents, 0);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        if (event.getY() >= basketY) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                oldX = touchX;
                oldBasketX = basketX;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                basketX = Math.max(0, Math.min(dWidth - basket.getWidth(), oldBasketX - (oldX - touchX)));
                postInvalidate();
            }
        }
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(background, null, rectBackground, null);
        canvas.drawBitmap(ground, null, rectGround, null);
        for (int i = 0; i < speckX.length; i++) {
            canvas.drawCircle(speckX[i], speckY[i], 3, speckPaint);
            speckY[i] += speckSpeed[i];
            if (speckY[i] > dHeight) speckY[i] = gameViewTop - 4;
        }
        long now = SystemClock.uptimeMillis();
        if (explosion != null) {
            float progress = Math.min(1f, (now - explosionStartMs) / 2000f);
            float scale = 1f + 0.6f * progress;
            effectPaint.setAlpha((int) (255 - 215 * progress));
            canvas.save();
            canvas.translate(basketX + basket.getWidth() / 2f, basketY + basket.getHeight() / 2f);
            canvas.scale(scale, scale);
            canvas.drawBitmap(explosion, -explosion.getWidth() / 2f, -explosion.getHeight() / 2f, effectPaint);
            canvas.restore();
            effectPaint.setAlpha(255);
            postInvalidateOnAnimation();
        } else {
            canvas.drawBitmap(basket, basketX, basketY, null);
            for (FallingObject item : fallingObjects)
                canvas.drawBitmap(item.getImage(), item.getPosition_x(), item.getPosition_y(), null);
        }
        long burstAge = now - burstStartMs;
        if (burstCount > 0 && burstAge < 400) {
            float progress = burstAge / 400f;
            speckPaint.setAlpha((int) (255 * (1 - progress)));
            for (int i = 0; i < burstCount; i++) {
                double angle = 2 * Math.PI * i / burstCount;
                canvas.drawCircle(burstX[i] + (float) Math.cos(angle) * progress * 40,
                        burstY[i] + (float) Math.sin(angle) * progress * 40, 5, speckPaint);
            }
            speckPaint.setAlpha(255);
        }
        textPaint.setColor(ContextCompat.getColor(context, R.color.festive_cream));
        canvas.drawText("score: " + score, dWidth / 20f, gameViewTop, textPaint);
        canvas.drawText("high score: " + highScore, dWidth / 2f, gameViewTop, textPaint);
        canvas.drawText("lives: " + getRemainingLives(), dWidth / 20f, gameViewTop + 50, textPaint);
        if (combo > 1) {
            textPaint.setColor(ContextCompat.getColor(context, R.color.festive_gold));
            canvas.drawText("combo x" + combo, dWidth / 2f, gameViewTop + 50, textPaint);
        }
    }

    private boolean checkGameOver() { return getRemainingLives() == 0 || explosion != null; }

    private void gameOver() {
        if (gameOverRunnable != null) return;
        handler.removeCallbacks(runnableNewPresents);
        if (getScore() > highScore) {
            highScore = getScore();
            sharedPreferences.edit().putInt(context.getString(R.string.high_score), highScore).apply();
        }
        gameOverRunnable = () -> {
            Intent intent = new Intent(context, GameOver.class);
            intent.putExtra(context.getString(R.string.score), getScore());
            intent.putExtra(context.getString(R.string.high_score), highScore);
            intent.putExtra(context.getString(R.string.combo_peak), comboPeak);
            context.startActivity(intent);
            ((Activity) context).finish();
        };
        handler.postDelayed(gameOverRunnable, 2000);
    }

    @Override protected void onDetachedFromWindow() {
        if (handler != null) {
            handler.removeCallbacks(runnableFallingPresents);
            handler.removeCallbacks(runnableNewPresents);
            if (gameOverRunnable != null) handler.removeCallbacks(gameOverRunnable);
        }
        super.onDetachedFromWindow();
    }

    private void InitScreenSize() {
        Display display = ((Activity) getContext()).getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        dWidth = size.x;
        dHeight = size.y;
        gameViewTop = dHeight / 20;
    }

    private Bitmap bitmapFromDrawable(int resId, int width, int height) {
        Drawable drawable = AppCompatResources.getDrawable(context, resId);
        if (drawable == null) throw new IllegalStateException("Missing drawable: " + resId);
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private void InitBitmaps() {
        rectBackground = new Rect(0, gameViewTop, dWidth, dHeight - dHeight / 10);
        rectGround = new Rect(0, dHeight - dHeight / 10, dWidth, dHeight);
        background = bitmapFromDrawable(R.drawable.bg_play, dWidth, rectBackground.height());
        ground = bitmapFromDrawable(R.drawable.ground_snow, dWidth, rectGround.height());
        basket = bitmapFromDrawable(R.drawable.sleigh, dWidth / 5, dHeight / 10);
        basketX = (dWidth - basket.getWidth()) / 2f;
        basketY = rectGround.top - basket.getHeight();
        presentImages.add(bitmapFromDrawable(R.drawable.gift_red, dWidth / 8, dHeight / 8));
        presentImages.add(bitmapFromDrawable(R.drawable.gift_gold, dWidth / 8, dHeight / 8));
        presentImages.add(bitmapFromDrawable(R.drawable.gift_green, dWidth / 8, dHeight / 8));
        snowflakeImg = bitmapFromDrawable(R.drawable.snowflake, dWidth / 8, dHeight / 8);
        bombImg = bitmapFromDrawable(R.drawable.bomb_ornament, dWidth / 8, dHeight / 8);
        explosionBitmap = bitmapFromDrawable(R.drawable.explosion_burst, basket.getWidth() * 2, basket.getWidth() * 2);
    }

    private void handleFallingObjects() {
        for (FallingObject item : fallingObjects) item.setPosition_y(item.getPosition_y() + item.getSpeed());
        checkIfCollected();
        checkIfLeftTheScreen();
    }

    private void addNewFallingObject() { addNewPresent(); addNewBomb(); }

    private void addNewPresent() {
        long countPresents = fallingObjects.stream().filter(f -> f.getFallingObjectType() == FallingObjectType.PRESENT).count();
        long limit = (long) (3 + 2 * Math.floor(presentSpeedMultiplier));
        if (countPresents <= limit && (random.nextInt(5) == 1 || fallingObjects.isEmpty())) {
            int presentsToAdd = random.nextInt(2) + 1;
            for (int i = 0; i < presentsToAdd; i++) {
                FallingObject item = new FallingObject();
                boolean hasSnowflake = fallingObjects.stream().anyMatch(f -> f.getFallingObjectType() == FallingObjectType.SNOWFLAKE);
                if (!hasSnowflake && random.nextFloat() < 0.12f) item.InitializeAsSnowflake(presentSpeedMultiplier, snowflakeImg);
                else item.InitializeAsPresent(presentSpeedMultiplier, presentImages.get(random.nextInt(3)));
                fallingObjects.add(item);
            }
        }
    }

    private void addNewBomb() {
        long countBombs = fallingObjects.stream().filter(f -> f.getFallingObjectType() == FallingObjectType.BOMB).count();
        long limit = (long) Math.floor(presentSpeedMultiplier);
        if (countBombs <= limit && random.nextInt(15) == 1) {
            long bombsToAdd = random.nextInt(2) + 1;
            if (bombsToAdd + countBombs > limit) bombsToAdd = limit - countBombs;
            for (long i = 0; i < bombsToAdd; i++) {
                FallingObject bomb = new FallingObject();
                bomb.InitializeAsBomb(presentSpeedMultiplier, bombImg);
                fallingObjects.add(bomb);
            }
        }
    }

    private void speedUp() {
        if (speedUpCounter <= 0) {
            if (addNewObjectPeriod > 500)
                addNewObjectPeriod -= (int) Math.floor((double) addNewObjectPeriod * 0.9);
            presentSpeedMultiplier *= 1.2;
            speedUpCounter = speedUpCounterStartValue;
        } else speedUpCounter--;
    }

    private void checkIfLeftTheScreen() {
        Iterator<FallingObject> iterator = fallingObjects.iterator();
        while (iterator.hasNext()) {
            FallingObject item = iterator.next();
            if (item.getPosition_y() > rectGround.top) {
                if (item.costsLifeIfMissed()) { missedPresents++; combo = 0; }
                iterator.remove();
            }
        }
    }

    private void checkIfCollected() {
        Iterator<FallingObject> iterator = fallingObjects.iterator();
        while (iterator.hasNext()) {
            FallingObject item = iterator.next();
            Point center = item.getCenter();
            if (center.x >= basketX && center.x <= basketX + basket.getWidth()
                    && center.y >= basketY && center.y <= basketY + basket.getHeight()) {
                iterator.remove();
                if (item.getFallingObjectType() == FallingObjectType.BOMB) {
                    combo = 0;
                    explosion = explosionBitmap;
                    explosionStartMs = SystemClock.uptimeMillis();
                    break;
                }
                if (item.getFallingObjectType() == FallingObjectType.PRESENT) {
                    long now = SystemClock.uptimeMillis();
                    combo = combo > 0 && now - lastPresentCatchMs <= 2500 ? combo + 1 : 1;
                    lastPresentCatchMs = now;
                    comboPeak = Math.max(comboPeak, combo);
                    score += item.getScoreValue() * combo;
                    speedUp();
                } else if (item.getFallingObjectType() == FallingObjectType.SNOWFLAKE && getRemainingLives() < 5) {
                    bonusLives++;
                }
                burstCount = 8;
                burstStartMs = SystemClock.uptimeMillis();
                for (int i = 0; i < burstCount; i++) { burstX[i] = center.x; burstY[i] = center.y; }
            }
        }
    }

    private int getScore() { return score; }
    private int getRemainingLives() { return Math.max(0, Math.min(5, 3 + bonusLives - missedPresents)); }
}
