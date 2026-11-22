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
    private float elfX, elfY, elfTargetX;
    private int elfState; // 0 hidden, 1 entering, 2 dwell, 3 clearance, 4 exiting
    private int elfExitDir, elfDwellTicks;
    private boolean activityResumed, running;
    private long gameOverRemaining = 2000, gameOverDeadline;
    private long spawnRemaining, spawnDeadline;
    private int santaDir = 1;
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
            if (!running) return;
            handleFallingObjects();
            postInvalidate();
            if (checkGameOver()) gameOver();
            else handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
        };
        runnableNewPresents = () -> {
            if (!running) return;
            addNewFallingObject();
            postInvalidate();
            if (!checkGameOver()) {
                spawnRemaining = addNewObjectPeriod;
                scheduleSpawn();
            }
        };
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
        if (elfState != 0 && elf != null) canvas.drawBitmap(elf, elfX, elfY, null);
        canvas.drawBitmap(santa, santaX, santaY, null);
        for (int i = 0; i < speckX.length; i++) {
            canvas.drawCircle(speckX[i], Math.max(hudBottom + 3, speckY[i]), 3, speckPaint);
            speckY[i] += speckSpeed[i];
            if (speckY[i] > dHeight) speckY[i] = hudBottom + 3;
        }
        long now = running || pausedAtMs == 0 ? SystemClock.uptimeMillis() : pausedAtMs;
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
        String[] labels = {"score: " + score, "high score: " + highScore,
                "lives: " + getRemainingLives(), combo > 1 ? "combo x" + combo : ""};
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
        float size = Math.max(1f, Math.min(50f, gameViewTop / 4f));
        while (true) {
            textPaint.setTextSize(size);
            boolean fits = true;
            for (String label : labels) if (textPaint.measureText(label) > columnWidth) fits = false;
            if (fits || size <= 1f) break;
            size = Math.max(1f, size - 1f);
        }
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        hudFirstBaseline = -fm.ascent;
        hudSecondBaseline = hudFirstBaseline + fm.descent - fm.ascent;
        // The reserved region depends only on viewport dimensions, never on counters.
        hudBottom = Math.max(1f, gameViewTop);
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
        dWidth = w; dHeight = h; gameViewTop = Math.max(1, h / 5);
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
        // A completed drop must finish exiting; unfinished deliveries restart with their queue entry.
        if (elfState == 4 && elf != null) {
            int side = Math.max(1, Math.min(dWidth / 6, dHeight / 10));
            elfX = Math.max(-side, Math.min(dWidth, oldw > 0 ? elfX * w / oldw : 0));
            elfY = Math.max(hudBottom, Math.min(Math.max(hudBottom, basketY - side),
                    oldh > 0 ? elfY * h / oldh : hudBottom));
            elf = bitmapFromDrawable(R.drawable.evil_elf, side, side);
        } else {
            elfState = 0; elf = null; elfDwellTicks = 0;
        }
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
        updateElfDelivery();
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

    private void addNewFallingObject() { addNewPresent(); addNewBomb(); }

    private float spawnX(Bitmap image) {
        return Math.max(0, Math.min(dWidth - image.getWidth(),
                santaX + santa.getWidth() * 17f / 24f - image.getWidth() / 2f));
    }

    private float spawnY() { return santaY + santa.getHeight() * 19f / 24f; }

    private void releasePendingThrow() {
        PendingThrow pending = pendingThrows.peekFirst();
        if (pending == null || pending.type == FallingObjectType.BOMB) return;
        float x = spawnX(pending.image), y = spawnY();
        for (FallingObject live : fallingObjects) {
            if (x < live.getPosition_x() + live.getWidth()
                    && x + pending.image.getWidth() > live.getPosition_x()) return;
        }
        FallingObject item = new FallingObject();
        if (pending.type == FallingObjectType.SNOWFLAKE) item.InitializeAsSnowflake(pending.speedMultiplier, pending.image, x, y);
        else item.InitializeAsPresent(pending.speedMultiplier, pending.image, x, y);
        pendingThrows.removeFirst();
        fallingObjects.add(item);
    }

    private void updateElfDelivery() {
        PendingThrow pending = pendingThrows.peekFirst();
        if (elfState == 0) {
            if (pending == null || pending.type != FallingObjectType.BOMB) return;
            int side = Math.max(1, Math.min(dWidth / 6, dHeight / 10));
            float corridor = Math.max(pending.image.getHeight(), dHeight / 8f);
            float maxY = Math.min(basketY - side,
                    basketY - corridor - pending.image.getHeight() - side * ElfThrowAnchor.HAND_Y);
            float minX = Math.max(0, pending.image.getWidth() / 2f - side * ElfThrowAnchor.HAND_X);
            float maxX = Math.min(dWidth - side,
                    dWidth - pending.image.getWidth() / 2f - side * ElfThrowAnchor.HAND_X);
            // No safe delivery geometry: discard this bomb rather than block the FIFO forever.
            if (maxY < hudBottom || maxX < minX) { pendingThrows.removeFirst(); return; }
            if (elf == null || elf.getWidth() != side)
                elf = bitmapFromDrawable(R.drawable.evil_elf, side, side);
            elfY = Math.max(hudBottom, Math.min(santaY + santa.getHeight(), maxY));
            elfTargetX = minX + random.nextFloat() * (maxX - minX);
            elfX = random.nextBoolean() ? -side : dWidth;
            elfState = 1;
            return;
        }
        float step = Math.max(1, dWidth / 150f);
        if (elfState == 1) {
            float delta = elfTargetX - elfX;
            if (Math.abs(delta) <= step) { elfX = elfTargetX; elfDwellTicks = 0; elfState = 2; }
            else elfX += Math.signum(delta) * step;
            return;
        }
        if (elfState == 2) {
            if (++elfDwellTicks >= 28) elfState = 3;
            return;
        }
        if (elfState == 3) {
            if (pending == null || pending.type != FallingObjectType.BOMB) { elfState = 0; return; }
            float x = elfX + elf.getWidth() * ElfThrowAnchor.HAND_X - pending.image.getWidth() / 2f;
            for (FallingObject live : fallingObjects) {
                if (x < live.getPosition_x() + live.getWidth()
                        && x + pending.image.getWidth() > live.getPosition_x()) return;
            }
            float y = elfY + elf.getHeight() * ElfThrowAnchor.HAND_Y;
            FallingObject item = new FallingObject();
            item.InitializeAsBomb(pending.speedMultiplier, pending.image, x, y);
            // Movement happens immediately below, so reserve 40 complete 10 ms ticks.
            float available = basketY - (y + item.getHeight());
            if (available <= 0) { pendingThrows.removeFirst(); elfState = 0; return; }
            item.preserveReactionInterval(available);
            pendingThrows.removeFirst(); fallingObjects.add(item);
            bombReactionTicks.put(item, 40);
            elfExitDir = elfX + elf.getWidth() / 2f < dWidth / 2f ? -1 : 1;
            elfState = 4;
            return;
        }
        if (elfState == 4) {
            elfX += elfExitDir * step;
            if (elfX + elf.getWidth() <= 0 || elfX >= dWidth) elfState = 0;
        }
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
