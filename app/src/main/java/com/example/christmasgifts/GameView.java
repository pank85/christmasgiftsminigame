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
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends View {
    private final Context context;
    static int dWidth, dHeight, gameViewTop;
    private float basketX, basketY, oldX, oldBasketX;
    private Bitmap basket, ground, background, bombImg, snowflakeImg, explosionBitmap, explosion, santa;
    private float santaX, santaY;
    private int santaDir = 1;
    private float hudFirstBaseline, hudSecondBaseline, hudBottom;
    private Rect rectBackground, rectGround;
    private Handler handler;
    private Runnable runnableFallingPresents, runnableNewPresents, gameOverRunnable;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] speckX = new float[12], speckY = new float[12], speckSpeed = new float[12];
    private final float[] burstX = new float[8], burstY = new float[8];
    private int burstCount;
    private long burstStartMs, explosionStartMs;
    private final Random random = new Random();
    private final List<FallingObject> fallingObjects = new ArrayList<>();
    private final Deque<PendingThrow> pendingThrows = new ArrayDeque<>();

    private static class PendingThrow {
        final FallingObjectType type;
        final Bitmap image;
        final double speedMultiplier;
        PendingThrow(FallingObjectType type, Bitmap image, double speedMultiplier) {
            this.type = type;
            this.image = image;
            this.speedMultiplier = speedMultiplier;
        }
    }
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
        InitScreenSize();
        InitRest();
        InitBitmaps();
        Start();
    }

    private void InitRest() {
        textPaint.setColor(ContextCompat.getColor(context, R.color.festive_cream));
        textPaint.setTextSize(Math.max(1, Math.min(50, gameViewTop / 4f)));
        textPaint.setTypeface(ResourcesCompat.getFont(context, R.font.winter_snow_regular));
        textPaint.setStyle(Paint.Style.FILL);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        highScore = sharedPreferences.getInt(context.getString(R.string.high_score), 0);
        fitHud();
        speckPaint.setColor(Color.WHITE);
        for (int i = 0; i < speckX.length; i++) {
            speckX[i] = random.nextFloat() * dWidth;
            speckY[i] = hudBottom + 3 + random.nextFloat() * Math.max(0, dHeight - hudBottom - 3);
            speckSpeed[i] = 0.5f + random.nextFloat();
        }
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
        fitHud();
        updateSantaLayout();
        drawCenterCrop(canvas, background, rectBackground);
        drawCenterCrop(canvas, ground, rectGround);
        canvas.drawBitmap(santa, santaX, santaY, null);
        for (int i = 0; i < speckX.length; i++) {
            canvas.drawCircle(speckX[i], Math.max(hudBottom + 3, speckY[i]), 3, speckPaint);
            speckY[i] += speckSpeed[i];
            if (speckY[i] > dHeight) speckY[i] = hudBottom + 3;
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
            drawCenterCrop(canvas, basket, new Rect((int) basketX, (int) basketY,
                    (int) basketX + basket.getWidth(), (int) basketY + basket.getHeight()));
            canvas.save();
            canvas.clipRect(0, hudBottom, dWidth, dHeight);
            for (FallingObject item : fallingObjects)
                canvas.drawBitmap(item.getImage(), item.getPosition_x(), item.getPosition_y(), null);
            canvas.restore();
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
        String[] labels = {"score: " + score, "high score: " + highScore,
                "lives: " + getRemainingLives(), combo > 1 ? "combo x" + combo : ""};
        float padding = Math.max(4f, dWidth / 20f);
        float gap = padding;
        float columnWidth = Math.max(0, (dWidth - 2 * padding - gap) / 2f);
        float rightX = padding + columnWidth + gap;
        textPaint.setColor(ContextCompat.getColor(context, R.color.festive_cream));
        canvas.drawText(TextUtils.ellipsize(labels[0], textPaint, columnWidth, TextUtils.TruncateAt.END).toString(), padding, hudFirstBaseline, textPaint);
        canvas.drawText(TextUtils.ellipsize(labels[1], textPaint, columnWidth, TextUtils.TruncateAt.END).toString(), rightX, hudFirstBaseline, textPaint);
        canvas.drawText(TextUtils.ellipsize(labels[2], textPaint, columnWidth, TextUtils.TruncateAt.END).toString(), padding, hudSecondBaseline, textPaint);
        if (combo > 1) {
            textPaint.setColor(ContextCompat.getColor(context, R.color.festive_gold));
            canvas.drawText(TextUtils.ellipsize(labels[3], textPaint, columnWidth, TextUtils.TruncateAt.END).toString(), rightX, hudSecondBaseline, textPaint);
        }
    }

    private void fitHud() {
        String[] labels = {"score: " + score, "high score: " + highScore,
                "lives: " + getRemainingLives(), combo > 1 ? "combo x" + combo : ""};
        float padding = Math.max(4f, dWidth / 20f);
        float columnWidth = Math.max(0, (dWidth - 3 * padding) / 2f);
        float size = Math.max(8f, Math.min(50f, gameViewTop / 4f));
        while (true) {
            textPaint.setTextSize(size);
            boolean fits = true;
            for (String label : labels) if (textPaint.measureText(label) > columnWidth) fits = false;
            if (fits || size <= 8f) break;
            size = Math.max(8f, size - 1f);
        }
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        hudFirstBaseline = -fm.ascent;
        hudSecondBaseline = hudFirstBaseline + fm.descent - fm.ascent;
        hudBottom = Math.max(1f, Math.min(gameViewTop, hudSecondBaseline + fm.descent + 4f));
    }

    private void updateSantaLayout() {
        int side = Math.max(1, Math.min(Math.min(dWidth / 5, dHeight / 8),
                (int) (basketY - hudBottom - 1)));
        if (santa == null || santa.getWidth() != side)
            santa = bitmapFromDrawable(R.drawable.santa, side, side);
        santaY = hudBottom;
        santaX = Math.max(0, Math.min(santaX, dWidth - santa.getWidth()));
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
        Activity activity = (Activity) getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
            dWidth = bounds.width();
            dHeight = bounds.height();
        } else {
            Display display = activity.getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getRealSize(size);
            dWidth = size.x;
            dHeight = size.y;
        }
        gameViewTop = dHeight / 5;
    }

    private Bitmap bitmapFromDrawable(int resId, int width, int height) {
        Drawable drawable = AppCompatResources.getDrawable(context, resId);
        if (drawable == null) throw new IllegalStateException("Missing drawable: " + resId);
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private void drawCenterCrop(Canvas canvas, Bitmap bitmap, Rect destination) {
        if (destination.width() <= 0 || destination.height() <= 0) return;
        float scale = Math.max((float) destination.width() / bitmap.getWidth(),
                (float) destination.height() / bitmap.getHeight());
        float sourceWidth = destination.width() / scale;
        float sourceHeight = destination.height() / scale;
        float left = (bitmap.getWidth() - sourceWidth) / 2f;
        float top = (bitmap.getHeight() - sourceHeight) / 2f;
        Rect source = new Rect(Math.max(0, (int) left), Math.max(0, (int) top),
                Math.min(bitmap.getWidth(), (int) Math.ceil(left + sourceWidth)),
                Math.min(bitmap.getHeight(), (int) Math.ceil(top + sourceHeight)));
        canvas.drawBitmap(bitmap, source, destination, null);
    }

    private Bitmap intrinsicBitmap(int resId) {
        Drawable drawable = AppCompatResources.getDrawable(context, resId);
        if (drawable == null) throw new IllegalStateException("Missing drawable: " + resId);
        return bitmapFromDrawable(resId, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    private void InitBitmaps() {
        rectBackground = new Rect(0, gameViewTop, dWidth, dHeight - dHeight / 10);
        rectGround = new Rect(0, dHeight - dHeight / 10, dWidth, dHeight);
        background = intrinsicBitmap(R.drawable.bg_play);
        ground = intrinsicBitmap(R.drawable.ground_snow);
        int basketSide = Math.max(1, Math.min(dWidth / 5, dHeight / 10));
        Bitmap sleighSource = intrinsicBitmap(R.drawable.sleigh);
        basket = Bitmap.createBitmap(basketSide, basketSide, Bitmap.Config.ARGB_8888);
        drawCenterCrop(new Canvas(basket), sleighSource, new Rect(0, 0, basketSide, basketSide));
        santaX = 0;
        basketX = (dWidth - basket.getWidth()) / 2f;
        basketY = rectGround.top - basket.getHeight();
        updateSantaLayout();
        int objectSide = Math.max(1, Math.min(dWidth, dHeight) / 8);
        presentImages.add(bitmapFromDrawable(R.drawable.gift_red, objectSide, objectSide));
        presentImages.add(bitmapFromDrawable(R.drawable.gift_gold, objectSide, objectSide));
        presentImages.add(bitmapFromDrawable(R.drawable.gift_green, objectSide, objectSide));
        snowflakeImg = bitmapFromDrawable(R.drawable.snowflake, objectSide, objectSide);
        bombImg = bitmapFromDrawable(R.drawable.bomb_ornament, objectSide, objectSide);
        explosionBitmap = bitmapFromDrawable(R.drawable.explosion_burst, basket.getWidth() * 2, basket.getWidth() * 2);
    }

    private void handleFallingObjects() {
        float maxX = Math.max(0, dWidth - santa.getWidth());
        santaX += santaDir * Math.max(1, dWidth / 150);
        if (santaX >= maxX) { santaX = maxX; santaDir = -1; }
        else if (santaX <= 0) { santaX = 0; santaDir = 1; }
        releasePendingThrow();
        for (FallingObject item : fallingObjects) item.setPosition_y(item.getPosition_y() + item.getSpeed());
        checkIfCollected();
        checkIfLeftTheScreen();
    }

    private void addNewFallingObject() { addNewPresent(); addNewBomb(); }

    private float spawnX(Bitmap image) {
        return Math.max(0, Math.min(dWidth - image.getWidth(),
                santaX + santa.getWidth() * 17f / 24f - image.getWidth() / 2f));
    }

    private float spawnY() { return santaY + santa.getHeight() * 19f / 24f; }

    private void releasePendingThrow() {
        PendingThrow pending = pendingThrows.peekFirst();
        if (pending == null) return;
        float x = spawnX(pending.image), y = spawnY();
        for (FallingObject live : fallingObjects) {
            if (x < live.getPosition_x() + live.getWidth()
                    && x + pending.image.getWidth() > live.getPosition_x()) return;
        }
        FallingObject item = new FallingObject();
        if (pending.type == FallingObjectType.BOMB) item.InitializeAsBomb(pending.speedMultiplier, pending.image, x, y);
        else if (pending.type == FallingObjectType.SNOWFLAKE) item.InitializeAsSnowflake(pending.speedMultiplier, pending.image, x, y);
        else item.InitializeAsPresent(pending.speedMultiplier, pending.image, x, y);
        pendingThrows.removeFirst();
        fallingObjects.add(item);
    }

    private void addNewPresent() {
        long countPresents = fallingObjects.stream().filter(f -> f.getFallingObjectType() == FallingObjectType.PRESENT).count()
                + pendingThrows.stream().filter(f -> f.type == FallingObjectType.PRESENT).count();
        long limit = (long) (3 + 2 * Math.floor(presentSpeedMultiplier));
        if (countPresents <= limit && (random.nextInt(5) == 1 || (fallingObjects.isEmpty() && pendingThrows.isEmpty()))) {
            int presentsToAdd = random.nextInt(2) + 1;
            for (int i = 0; i < presentsToAdd; i++) {
                boolean hasSnowflake = fallingObjects.stream().anyMatch(f -> f.getFallingObjectType() == FallingObjectType.SNOWFLAKE)
                        || pendingThrows.stream().anyMatch(f -> f.type == FallingObjectType.SNOWFLAKE);
                if (!hasSnowflake && random.nextFloat() < 0.12f)
                    pendingThrows.addLast(new PendingThrow(FallingObjectType.SNOWFLAKE, snowflakeImg, presentSpeedMultiplier));
                else {
                    Bitmap image = presentImages.get(random.nextInt(3));
                    pendingThrows.addLast(new PendingThrow(FallingObjectType.PRESENT, image, presentSpeedMultiplier));
                }
            }
        }
    }

    private void addNewBomb() {
        long countBombs = fallingObjects.stream().filter(f -> f.getFallingObjectType() == FallingObjectType.BOMB).count()
                + pendingThrows.stream().filter(f -> f.type == FallingObjectType.BOMB).count();
        long limit = (long) Math.floor(presentSpeedMultiplier);
        if (countBombs <= limit && random.nextInt(15) == 1) {
            long bombsToAdd = random.nextInt(2) + 1;
            if (bombsToAdd + countBombs > limit) bombsToAdd = limit - countBombs;
            for (long i = 0; i < bombsToAdd; i++) {
                pendingThrows.addLast(new PendingThrow(FallingObjectType.BOMB, bombImg, presentSpeedMultiplier));
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
