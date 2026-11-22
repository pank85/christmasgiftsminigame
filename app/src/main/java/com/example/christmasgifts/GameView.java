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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;

public class GameView extends View {
    private final Context context;
    static int dWidth, dHeight, gameViewTop;
    private float basketX, basketY, oldX, oldBasketX;
    private Bitmap basket, ground, background, bombImg, snowflakeImg, explosionBitmap, explosion, santa;
    private float santaX, santaY;
    private Bitmap elf;
    private float elfY;
    private int throwingElf = -1, throwTicks;
    private static final int THROW_DURATION_TICKS = 45;
    private boolean activityResumed, running, draggingBasket;
    private long gameOverRemaining = 2000, gameOverDeadline;
    private long spawnRemaining, spawnDeadline;
    private float hudFirstBaseline, hudSecondBaseline, hudBottom;
    private Rect rectBackground, rectGround;
    private Handler handler;
    private Runnable runnableFallingPresents, runnableNewPresents, gameOverRunnable;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] speckX = new float[12], speckY = new float[12], speckSpeed = new float[12];
    private final float[] burstX = new float[8], burstY = new float[8];
    private int burstCount;
    private long burstStartMs, explosionStartMs, pausedAtMs;
    private final Random random = new Random();
    private final List<FallingObject> fallingObjects = new ArrayList<>();
    // Mirrors the bomb's 40 movement ticks, including the last protected collision check.
    private final Map<FallingObject, Integer> bombReactionTicks = new IdentityHashMap<>();
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
    private int level = 1, levelCaught;
    private boolean runWon;
    private long levelBannerUntil;
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
            if (!running) return;
            handleFallingObjects();
            postInvalidate();
            if (checkGameOver()) gameOver();
            else if (gameOverRunnable != null) return;
            else handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
        };
        runnableNewPresents = () -> {
            if (!running) return;
            addNewFallingObject();
            postInvalidate();
            if (!checkGameOver()) {
                spawnRemaining = LevelTable.spawnPeriodMs(level);
                scheduleSpawn();
            }
        };
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            draggingBasket = event.getY() >= basketY;
            if (draggingBasket) { oldX = touchX; oldBasketX = basketX; }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE && draggingBasket && event.getY() >= basketY) {
            basketX = Math.max(0, Math.min(dWidth - basket.getWidth(), oldBasketX - (oldX - touchX)));
            postInvalidate();
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            draggingBasket = false;
        }
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        fitHud();
        updateSantaLayout();
        canvas.save();
        canvas.clipRect(0, hudBottom, dWidth, dHeight);
        drawCenterCrop(canvas, background, rectBackground);
        drawCenterCrop(canvas, ground, rectGround);
        drawElfPile(canvas);
        for (int i = 0; i < speckX.length; i++) {
            canvas.drawCircle(speckX[i], Math.max(hudBottom + 3, speckY[i]), 3, speckPaint);
            speckY[i] += speckSpeed[i];
            if (speckY[i] > dHeight) speckY[i] = hudBottom + 3;
        }
        long now = running || pausedAtMs == 0 ? SystemClock.uptimeMillis() : pausedAtMs;
        canvas.drawBitmap(santa, santaX, santaY, null);
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
            if (running) postInvalidateOnAnimation();
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
        if (levelBannerUntil > now) {
            textPaint.setColor(ContextCompat.getColor(context, R.color.festive_gold));
            String banner = "Level " + level;
            canvas.drawText(banner, (dWidth - textPaint.measureText(banner)) / 2f,
                    hudBottom + elf.getHeight() + textPaint.getTextSize() * 2, textPaint);
        }
        canvas.restore();
        String[] labels = {"Level " + level, "Caught: " + levelCaught + "/" + LevelTable.quota(level),
                "Lives: " + getRemainingLives(), "Score: " + score};
        float padding = Math.max(4f, dWidth / 20f);
        float gap = padding;
        float columnWidth = Math.max(0, (dWidth - 2 * padding - gap) / 2f);
        float rightX = padding + columnWidth + gap;
        hudPaint.setStyle(Paint.Style.FILL);
        hudPaint.setColor(ContextCompat.getColor(context, R.color.festive_night));
        hudPaint.setAlpha(180);
        canvas.drawRoundRect(padding / 2f, 2, dWidth - padding / 2f, hudBottom - 2, 12, 12, hudPaint);
        hudPaint.setColor(ContextCompat.getColor(context, R.color.festive_gold));
        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(2f);
        canvas.drawRoundRect(padding / 2f, 2, dWidth - padding / 2f, hudBottom - 2, 12, 12, hudPaint);
        textPaint.setColor(ContextCompat.getColor(context, R.color.festive_cream));
        canvas.drawText(TextUtils.ellipsize(labels[0], textPaint, columnWidth, TextUtils.TruncateAt.END).toString(), padding, hudFirstBaseline, textPaint);
        canvas.drawText(TextUtils.ellipsize(labels[1], textPaint, columnWidth, TextUtils.TruncateAt.END).toString(), rightX, hudFirstBaseline, textPaint);
        canvas.drawText(TextUtils.ellipsize(labels[2], textPaint, columnWidth, TextUtils.TruncateAt.END).toString(), padding, hudSecondBaseline, textPaint);
        canvas.drawText(TextUtils.ellipsize(labels[3], textPaint, columnWidth, TextUtils.TruncateAt.END).toString(), rightX, hudSecondBaseline, textPaint);
    }

    private void fitHud() {
        String[] labels = {"Level 10", "Caught: 120/120", "Lives: 5", "Score: 999999"};
        float padding = Math.max(4f, dWidth / 20f);
        float columnWidth = Math.max(0, (dWidth - 3 * padding) / 2f);
        float size = Math.max(1f, Math.min(50f, dHeight / 24f));
        while (true) {
            textPaint.setTextSize(size);
            boolean fits = true;
            for (String label : labels) if (textPaint.measureText(label) > columnWidth) fits = false;
            if (fits || size <= 1f) break;
            size = Math.max(1f, size - 1f);
        }
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float margin = Math.max(3f, size * 0.15f);
        hudFirstBaseline = margin - fm.ascent;
        hudSecondBaseline = hudFirstBaseline + fm.descent - fm.ascent;
        hudBottom = Math.min(dHeight, hudSecondBaseline + fm.descent + margin);
        gameViewTop = Math.max(1, (int) Math.ceil(hudBottom));
    }

    private void updateSantaLayout() {
        int side = Math.max(1, Math.min(Math.min(dWidth / 5, dHeight / 8),
                (int) (basketY - hudBottom - 1)));
        if (santa == null || santa.getWidth() != side)
            santa = bitmapFromDrawable(R.drawable.santa, side, side);
        santaY = Math.max(hudBottom, basketY - santa.getHeight() * 0.8f);
        santaX = Math.max(0, Math.min(dWidth - santa.getWidth(),
                basketX + (basket.getWidth() - santa.getWidth()) / 2f));
    }

    private boolean checkGameOver() { return runWon || getRemainingLives() == 0 || explosion != null; }

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
            intent.putExtra(context.getString(R.string.run_won), runWon);
            intent.putExtra(context.getString(R.string.level_reached), level);
            context.startActivity(intent);
            ((Activity) context).finish();
        };
        gameOverRemaining = 2000;
        scheduleGameOver();
    }

    public void setActivityResumed(boolean resumed) {
        activityResumed = resumed;
        updateRunning();
    }

    private void scheduleGameOver() {
        if (!running || gameOverRunnable == null) return;
        gameOverDeadline = SystemClock.uptimeMillis() + gameOverRemaining;
        handler.postDelayed(gameOverRunnable, gameOverRemaining);
    }

    private void scheduleSpawn() {
        spawnDeadline = SystemClock.uptimeMillis() + spawnRemaining;
        handler.postDelayed(runnableNewPresents, spawnRemaining);
    }

    private void updateRunning() {
        if (handler == null) return;
        boolean shouldRun = activityResumed && isAttachedToWindow() && hasWindowFocus()
                && getWindowVisibility() == VISIBLE && isShown() && getWidth() > 0 && getHeight() > 0;
        if (shouldRun == running) return;
        running = shouldRun;
        if (running) {
            if (pausedAtMs != 0) {
                long inactive = SystemClock.uptimeMillis() - pausedAtMs;
                if (combo > 0) lastPresentCatchMs += inactive;
                if (burstCount > 0) burstStartMs += inactive;
                if (explosion != null) explosionStartMs += inactive;
                if (levelBannerUntil > 0) levelBannerUntil += inactive;
                pausedAtMs = 0;
            }
            if (gameOverRunnable != null) scheduleGameOver();
            else {
                handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
                scheduleSpawn();
            }
        } else {
            pausedAtMs = SystemClock.uptimeMillis();
            handler.removeCallbacks(runnableFallingPresents);
            if (gameOverRunnable == null)
                spawnRemaining = Math.max(0, spawnDeadline - pausedAtMs);
            handler.removeCallbacks(runnableNewPresents);
            if (gameOverRunnable != null) {
                gameOverRemaining = Math.max(0, gameOverDeadline - SystemClock.uptimeMillis());
                handler.removeCallbacks(gameOverRunnable);
            }
        }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateRunning(); }
    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateRunning();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); updateRunning();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); updateRunning();
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility); updateRunning();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0 || handler == null) { updateRunning(); return; }
        dWidth = w; dHeight = h;
        fitHud();
        float oldBasketY = basketY;
        float oldBasketFraction = oldw > 0 ? basketX / oldw : 0.5f;
        float oldSantaFraction = oldw > 0 ? santaX / oldw : 0;
        List<Bitmap> oldPresentImages = new ArrayList<>(presentImages);
        presentImages.clear();
        InitBitmaps();
        basketX = Math.max(0, Math.min(dWidth - basket.getWidth(), oldBasketFraction * dWidth));
        santaX = oldSantaFraction * w;
        fitHud(); updateSantaLayout();
        // Retain object identity, random speed and selected variant across relayout.
        for (FallingObject item : fallingObjects) {
            Bitmap image = item.getFallingObjectType() == FallingObjectType.BOMB ? bombImg
                    : item.getFallingObjectType() == FallingObjectType.SNOWFLAKE ? snowflakeImg
                    : presentImages.get(Math.max(0, oldPresentImages.indexOf(item.getImage())));
            float x = Math.max(0, Math.min(w - image.getWidth(), oldw > 0 ? item.getPosition_x() * w / oldw : 0));
            float y = oldh > 0 ? item.getPosition_y() * h / oldh : hudBottom;
            if (bombReactionTicks.containsKey(item)) {
                // Keep the old available corridor, scaled for height, where geometry permits.
                float oldDistance = Math.max(0, oldBasketY - item.getPosition_y() - item.getHeight());
                float desiredDistance = oldh > 0 ? oldDistance * h / oldh : 0;
                y = Math.max(hudBottom, Math.min(y, basketY - image.getHeight() - desiredDistance));
            }
            item.relayout(image, x, y);
            if (item.getFallingObjectType() == FallingObjectType.BOMB) {
                item.preserveReactionInterval(basketY - (y + item.getHeight()));
            }
        }
        Deque<PendingThrow> resizedPending = new ArrayDeque<>();
        for (PendingThrow pending : pendingThrows) {
            Bitmap image = pending.type == FallingObjectType.BOMB ? bombImg
                    : pending.type == FallingObjectType.SNOWFLAKE ? snowflakeImg
                    : presentImages.get(Math.max(0, oldPresentImages.indexOf(pending.image)));
            resizedPending.addLast(new PendingThrow(pending.type, image, pending.speedMultiplier));
        }
        pendingThrows.clear(); pendingThrows.addAll(resizedPending);
        if (explosion != null) explosion = explosionBitmap;
        updateRunning(); invalidate();
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
        gameViewTop = 1;
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
        int elfSide = Math.max(1, Math.min(dWidth / 5, dHeight / 9));
        elf = bitmapFromDrawable(R.drawable.evil_elf, elfSide, elfSide);
        elfY = hudBottom;
        explosionBitmap = bitmapFromDrawable(R.drawable.explosion_burst, basket.getWidth() * 2, basket.getWidth() * 2);
    }

    private void handleFallingObjects() {
        releasePendingThrow();
        for (FallingObject item : fallingObjects) item.move();
        checkIfCollected();
        checkIfLeftTheScreen();
        Iterator<Map.Entry<FallingObject, Integer>> reaction = bombReactionTicks.entrySet().iterator();
        while (reaction.hasNext()) {
            Map.Entry<FallingObject, Integer> entry = reaction.next();
            if (!fallingObjects.contains(entry.getKey()) || entry.getValue() <= 1) reaction.remove();
            else entry.setValue(entry.getValue() - 1);
        }
    }

    private float elfX(int index) {
        return index == 0 ? dWidth * 0.18f : dWidth * 0.68f;
    }

    private void drawElfPile(Canvas canvas) {
        int side = Math.max(1, dWidth / 17);
        for (int i = 0; i < 50; i++) {
            int row = i / 25, col = i % 25;
            int x = Math.round(col * (dWidth - side) / 24f);
            int y = Math.round(elfY + elf.getHeight() * 0.35f + row * side * 0.45f);
            canvas.drawBitmap(presentImages.get(i % presentImages.size()), null,
                    new Rect(x, y, x + side, y + side), null);
        }
        for (int i = 0; i < 2; i++) {
            float progress = throwingElf == i ? Math.min(1f, throwTicks / (float) THROW_DURATION_TICKS) : 0f;
            float lean = (float) Math.sin(progress * Math.PI) * elf.getWidth() * 0.18f;
            canvas.drawBitmap(elf, elfX(i) + lean, elfY + lean * 0.35f, null);
        }
        PendingThrow pending = pendingThrows.peekFirst();
        if (pending != null && throwingElf >= 0) {
            float progress = Math.min(1f, throwTicks / (float) THROW_DURATION_TICKS);
            float handX = throwX(throwingElf, pending.image);
            float handY = elfY + elf.getHeight() * ElfThrowAnchor.HAND_Y;
            float x = handX + (1f - progress) * elf.getWidth() * 0.35f;
            float y = handY + (1f - progress) * elf.getHeight() * 0.35f
                    - (float) Math.sin(progress * Math.PI) * elf.getHeight() * 0.4f;
            canvas.drawBitmap(pending.image, x, y, null);
        }
    }

    private void addNewFallingObject() {
        if (fallingObjects.size() + pendingThrows.size() >= 8) return;
        FallingObjectType type;
        Bitmap image;
        if (random.nextInt(100) < LevelTable.bombChancePercent(level)) {
            type = FallingObjectType.BOMB;
            image = bombImg;
        } else if (random.nextInt(100) < LevelTable.snowflakeChancePercent(level)) {
            type = FallingObjectType.SNOWFLAKE;
            image = snowflakeImg;
        } else {
            type = FallingObjectType.PRESENT;
            image = presentImages.get(random.nextInt(presentImages.size()));
        }
        pendingThrows.addLast(new PendingThrow(type, image, LevelTable.speedMultiplier(level)));
    }

    private float throwX(int index, Bitmap image) {
        return Math.max(0, Math.min(dWidth - image.getWidth(),
                elfX(index) + elf.getWidth() * ElfThrowAnchor.HAND_X - image.getWidth() / 2f));
    }

    private void releasePendingThrow() {
        PendingThrow pending = pendingThrows.peekFirst();
        if (pending == null) { throwingElf = -1; throwTicks = 0; return; }
        if (throwingElf < 0) { throwingElf = random.nextInt(2); throwTicks = 0; }
        if (throwTicks < THROW_DURATION_TICKS) { throwTicks++; return; }
        {
            float x = throwX(throwingElf, pending.image);
            float y = elfY + elf.getHeight() * ElfThrowAnchor.HAND_Y;
            boolean blocked = false;
            for (FallingObject live : fallingObjects) {
                if (x < live.getPosition_x() + live.getWidth()
                        && x + pending.image.getWidth() > live.getPosition_x()
                        && live.getPosition_y() < y + pending.image.getHeight()) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) return;
            FallingObject item = new FallingObject();
            if (pending.type == FallingObjectType.BOMB) {
                item.InitializeAsBomb(pending.speedMultiplier, pending.image, x, y);
                item.preserveReactionInterval(Math.max(0, basketY - (y + item.getHeight())));
                bombReactionTicks.put(item, 40);
            } else if (pending.type == FallingObjectType.SNOWFLAKE) {
                item.InitializeAsSnowflake(pending.speedMultiplier, pending.image, x, y);
            } else {
                item.InitializeAsPresent(pending.speedMultiplier, pending.image, x, y);
            }
            pendingThrows.removeFirst();
            throwingElf = -1;
            throwTicks = 0;
            fallingObjects.add(item);
            return;
        }
    }

    private void checkIfLeftTheScreen() {
        Iterator<FallingObject> iterator = fallingObjects.iterator();
        while (iterator.hasNext()) {
            FallingObject item = iterator.next();
            if (!bombReactionTicks.containsKey(item) && item.getPosition_y() > rectGround.top) {
                if (item.costsLifeIfMissed()) { missedPresents++; combo = 0; }
                iterator.remove();
            }
        }
    }

    private void checkIfCollected() {
        Iterator<FallingObject> iterator = fallingObjects.iterator();
        while (iterator.hasNext()) {
            FallingObject item = iterator.next();
            // Impossible resize geometry can overlap the basket even at hudBottom.
            // Defer both collision and off-screen removal until the interval expires.
            if (bombReactionTicks.containsKey(item)) continue;
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
                    levelCaught++;
                    if (levelCaught >= LevelTable.quota(level)) {
                        if (level == LevelTable.LEVEL_COUNT) {
                            runWon = true;
                            gameOver();
                        } else {
                            level++;
                            levelCaught = 0;
                            levelBannerUntil = SystemClock.uptimeMillis() + 1500;
                        }
                    }
                } else if (item.getFallingObjectType() == FallingObjectType.SNOWFLAKE && getRemainingLives() < 5) {
                    bonusLives++;
                }
                burstCount = 8;
                burstStartMs = SystemClock.uptimeMillis();
                for (int i = 0; i < burstCount; i++) { burstX[i] = center.x; burstY[i] = center.y; }
                if (runWon) break;
            }
        }
    }

    private int getScore() { return score; }
    private int getRemainingLives() { return Math.max(0, Math.min(5, 3 + bonusLives - missedPresents)); }
}
