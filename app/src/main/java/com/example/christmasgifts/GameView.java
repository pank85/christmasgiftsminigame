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
import android.graphics.RectF;
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
    private Bitmap ground, background, bombImg, snowflakeImg, explosionBitmap, explosion, santa;
    private float santaX, santaY;
    private boolean santaPlaced;
    private Bitmap elf;
    private float elfX, elfY, elfTargetX;
    private boolean elfPlaced, dropChosen;
    private int throwTicks, elfBobTicks;
    private static final int THROW_DURATION_TICKS = 45;
    private enum ElfPhase { HOME, RUN, THROW }
    private ElfPhase elfPhase = ElfPhase.HOME;
    private boolean activityResumed, running;
    private boolean levelComplete;
    private long gameOverRemaining = 2000, gameOverDeadline;
    private long spawnRemaining, spawnDeadline;
    private float hudFirstBaseline, hudSecondBaseline, hudBottom;
    private Rect rectBackground, rectGround;
    private final RectF nextLevelButton = new RectF();
    private Handler handler;
    private Runnable runnableFallingPresents, runnableNewPresents, gameOverRunnable;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private final List<Bitmap> brokenPresentImages = new ArrayList<>();
    private final List<BrokenPresent> brokenPresents = new ArrayList<>();

    private static class BrokenPresent {
        final int variant;
        final float xFraction;
        float x;
        float y;
        BrokenPresent(int variant, float xFraction, float x, float y) {
            this.variant = variant;
            this.xFraction = xFraction;
            this.x = x;
            this.y = y;
        }
    }
    private final int moveObjectPeriod = 10;
    private int level = 1, levelCaught, stock = -1;
    private boolean runWon;
    private int score, combo, comboPeak, missedPresents, highScore;
    private long lastPresentCatchMs;
    private SharedPreferences sharedPreferences;
    private Bitmap sleighFrame, treeFrame;
    private int sleighStep = -1, treeStep = -1, frameWidth, frameHeight;

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
        if (stock < 0) stock = LevelTable.quota(level);
    }

    private void Start() {
        handler = new Handler();
        runnableFallingPresents = () -> {
            if (!running || levelComplete) return;
            handleFallingObjects();
            postInvalidate();
            if (levelComplete) {
                pauseGameplayHandlers();
                return;
            }
            if (checkGameOver()) gameOver();
            else if (gameOverRunnable != null) return;
            else handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
        };
        runnableNewPresents = () -> {
            if (!running || levelComplete) return;
            addNewFallingObject();
            postInvalidate();
            if (levelComplete) {
                pauseGameplayHandlers();
                return;
            }
            if (!checkGameOver()) {
                spawnRemaining = LevelTable.spawnPeriodMs(level);
                scheduleSpawn();
            }
        };
    }

    private void pauseGameplayHandlers() {
        if (handler == null) return;
        handler.removeCallbacks(runnableFallingPresents);
        handler.removeCallbacks(runnableNewPresents);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        float touchY = event.getY();
        if (levelComplete) {
            if (event.getAction() == MotionEvent.ACTION_UP && nextLevelButton.contains(touchX, touchY)) {
                advanceFromLevelCard();
            }
            return true;
        }
        if (santa == null || gameOverRunnable != null) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            santaX = touchX - santa.getWidth() / 2f;
            clampSanta();
            postInvalidate();
        }
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        fitHud();
        updateSantaLayout();
        ensureCheckpointFrames();
        layoutElfHomeY();
        if (!elfPlaced) {
            elfX = homeX();
            elfTargetX = elfX;
            elfPlaced = true;
        }
        canvas.save();
        canvas.clipRect(0, hudBottom, dWidth, dHeight);
        drawCenterCrop(canvas, background, rectBackground);
        drawCenterCrop(canvas, ground, rectGround);
        Rect treeRect = treeRect();
        if (treeFrame != null) drawCenterCrop(canvas, treeFrame, treeRect);
        for (int i = 0; i < speckX.length; i++) {
            canvas.drawCircle(speckX[i], Math.max(hudBottom + 3, speckY[i]), 3, speckPaint);
            speckY[i] += speckSpeed[i];
            if (speckY[i] > dHeight) speckY[i] = hudBottom + 3;
        }
        Rect sleighRect = sleighRect();
        if (sleighFrame != null) drawCenterCrop(canvas, sleighFrame, sleighRect);
        drawElf(canvas);
        for (BrokenPresent broken : brokenPresents) {
            if (broken.variant >= 0 && broken.variant < brokenPresentImages.size()) {
                canvas.drawBitmap(brokenPresentImages.get(broken.variant), broken.x, broken.y, null);
            }
        }
        long now = running || pausedAtMs == 0 ? SystemClock.uptimeMillis() : pausedAtMs;
        if (explosion != null) {
            float progress = Math.min(1f, (now - explosionStartMs) / 2000f);
            float scale = 1f + 0.6f * progress;
            effectPaint.setAlpha((int) (255 - 215 * progress));
            canvas.save();
            canvas.translate(santaX + santa.getWidth() / 2f, santaY + santa.getHeight() / 2f);
            canvas.scale(scale, scale);
            canvas.drawBitmap(explosion, -explosion.getWidth() / 2f, -explosion.getHeight() / 2f, effectPaint);
            canvas.restore();
            effectPaint.setAlpha(255);
            if (running && !levelComplete) postInvalidateOnAnimation();
        } else {
            canvas.drawBitmap(santa, santaX, santaY, null);
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
        canvas.restore();
        String[] labels = {"Level " + level, "Caught: " + levelCaught + "/" + LevelTable.quota(level),
                "Missed: " + missedPresents + "/3", "Score: " + score};
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
        if (levelComplete) drawLevelCard(canvas);
    }

    private void drawLevelCard(Canvas canvas) {
        float pad = Math.max(12f, dWidth / 18f);
        float cardLeft = pad;
        float cardRight = dWidth - pad;
        float cardTop = hudBottom + pad;
        float cardBottom = Math.min(dHeight - pad, cardTop + dHeight * 0.55f);
        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(ContextCompat.getColor(context, R.color.festive_night));
        cardPaint.setAlpha(230);
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 16, 16, cardPaint);
        cardPaint.setStyle(Paint.Style.STROKE);
        cardPaint.setStrokeWidth(3f);
        cardPaint.setColor(ContextCompat.getColor(context, R.color.festive_gold));
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 16, 16, cardPaint);
        textPaint.setColor(ContextCompat.getColor(context, R.color.festive_cream));
        float textSize = Math.max(14f, Math.min(textPaint.getTextSize(), (cardBottom - cardTop) / 8f));
        textPaint.setTextSize(textSize);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float line = textSize + Math.max(4f, -fm.ascent * 0.15f);
        float y = cardTop + pad - fm.ascent;
        int quota = LevelTable.quota(level);
        String[] rows = {
                "Score: " + score,
                "Caught: " + levelCaught + "/" + quota,
                "Missed: " + missedPresents + "/3",
                "Combo: " + comboPeak
        };
        float inner = cardRight - cardLeft - 2 * pad;
        for (String row : rows) {
            canvas.drawText(TextUtils.ellipsize(row, textPaint, inner, TextUtils.TruncateAt.END).toString(),
                    cardLeft + pad, y, textPaint);
            y += line;
        }
        float buttonTop = Math.min(cardBottom - pad - line * 1.4f, y + line * 0.3f);
        float buttonBottom = Math.min(cardBottom - pad, buttonTop + line * 1.5f);
        nextLevelButton.set(cardLeft + pad, buttonTop, cardRight - pad, buttonBottom);
        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(ContextCompat.getColor(context, R.color.festive_gold));
        canvas.drawRoundRect(nextLevelButton, 12, 12, cardPaint);
        textPaint.setColor(ContextCompat.getColor(context, R.color.festive_night));
        String label = "Next Level";
        float labelW = textPaint.measureText(label);
        canvas.drawText(label, nextLevelButton.centerX() - labelW / 2f,
                nextLevelButton.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
        textPaint.setColor(ContextCompat.getColor(context, R.color.festive_cream));
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

    private int santaSide() {
        return Math.max(1, Math.min(dWidth / 5, dHeight / 8));
    }

    private void updateSantaLayout() {
        int side = santaSide();
        if (santa == null || santa.getWidth() != side)
            santa = bitmapFromDrawable(R.drawable.santa, side, side);
        if (!santaPlaced) {
            santaX = (dWidth - santa.getWidth()) / 2f;
            santaY = santaGroundY();
            santaPlaced = true;
        }
        clampSanta();
    }

    private int groundHeight() {
        return Math.max(1, dHeight / 6);
    }

    private int groundTop() {
        return dHeight - groundHeight();
    }

    /** Top of the Santa bitmap on the fixed bottom ground line. */
    private float santaGroundY() {
        if (santa == null) return 0f;
        float feetY = groundTop() + groundHeight() * 0.48f;
        return Math.max(hudBottom, feetY - santa.getHeight());
    }

    private void clampSanta() {
        if (santa == null) return;
        santaX = Math.max(0, Math.min(dWidth - santa.getWidth(), santaX));
        santaY = santaGroundY();
    }

    private boolean checkGameOver() { return runWon || explosion != null; }

    private void gameOver() {
        if (gameOverRunnable != null) return;
        handler.removeCallbacks(runnableNewPresents);
        handler.removeCallbacks(runnableFallingPresents);
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
        if (levelComplete || handler == null) return;
        spawnDeadline = SystemClock.uptimeMillis() + spawnRemaining;
        handler.postDelayed(runnableNewPresents, spawnRemaining);
    }

    private void resumeGameplay() {
        if (!running || levelComplete || gameOverRunnable != null || handler == null) return;
        handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
        scheduleSpawn();
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
            else if (!levelComplete) {
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
        float oldSantaBottom = santa == null ? 0 : santaY + santa.getHeight();
        float oldSantaXFraction = oldw > 0 ? santaX / oldw : 0.5f;
        float oldElfFraction = oldw > 0 ? elfX / oldw : 0.5f;
        List<Bitmap> oldPresentImages = new ArrayList<>(presentImages);
        presentImages.clear();
        brokenPresentImages.clear();
        sleighFrame = null;
        treeFrame = null;
        sleighStep = -1;
        treeStep = -1;
        InitBitmaps();
        santaX = oldSantaXFraction * w;
        santaPlaced = true;
        fitHud();
        updateSantaLayout();
        layoutElfHomeY();
        elfX = Math.max(0, Math.min(dWidth - elf.getWidth(), oldElfFraction * w));
        elfPlaced = true;
        // Retain object identity, random speed and selected variant across relayout.
        for (FallingObject item : fallingObjects) {
            Bitmap image = item.getFallingObjectType() == FallingObjectType.BOMB ? bombImg
                    : item.getFallingObjectType() == FallingObjectType.SNOWFLAKE ? snowflakeImg
                    : presentImages.get(Math.max(0, oldPresentImages.indexOf(item.getImage())));
            float x = Math.max(0, Math.min(w - image.getWidth(), oldw > 0 ? item.getPosition_x() * w / oldw : 0));
            float y = oldh > 0 ? item.getPosition_y() * h / oldh : hudBottom;
            if (bombReactionTicks.containsKey(item)) {
                float oldDistance = Math.max(0, oldSantaBottom - item.getPosition_y() - item.getHeight());
                float desiredDistance = oldh > 0 ? oldDistance * h / oldh : 0;
                y = Math.max(hudBottom, Math.min(y, santaY - image.getHeight() - desiredDistance));
            }
            item.relayout(image, x, y);
            if (item.getFallingObjectType() == FallingObjectType.BOMB) {
                item.preserveReactionInterval(santaY - (y + item.getHeight()));
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
        for (BrokenPresent broken : brokenPresents) {
            if (!brokenPresentImages.isEmpty()) {
                Bitmap img = brokenPresentImages.get(Math.max(0, Math.min(brokenPresentImages.size() - 1, broken.variant)));
                broken.x = Math.max(0, Math.min(w - img.getWidth(), broken.xFraction * w));
                broken.y = brokenPresentGroundY(img);
            }
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
        rectBackground = new Rect(0, gameViewTop, dWidth, groundTop());
        rectGround = new Rect(0, groundTop(), dWidth, dHeight);
        background = intrinsicBitmap(R.drawable.bg_play);
        ground = intrinsicBitmap(R.drawable.ground_snow);
        updateSantaLayout();
        int objectSide = Math.max(1, Math.min(dWidth, dHeight) / 8);
        presentImages.add(bitmapFromDrawable(R.drawable.gift_red, objectSide, objectSide));
        presentImages.add(bitmapFromDrawable(R.drawable.gift_gold, objectSide, objectSide));
        presentImages.add(bitmapFromDrawable(R.drawable.gift_green, objectSide, objectSide));
        brokenPresentImages.clear();
        brokenPresentImages.add(bitmapFromDrawable(R.drawable.broken_gift_red, objectSide, objectSide));
        brokenPresentImages.add(bitmapFromDrawable(R.drawable.broken_gift_gold, objectSide, objectSide));
        brokenPresentImages.add(bitmapFromDrawable(R.drawable.broken_gift_green, objectSide, objectSide));
        snowflakeImg = bitmapFromDrawable(R.drawable.snowflake, objectSide, objectSide);
        bombImg = bitmapFromDrawable(R.drawable.bomb_ornament, objectSide, objectSide);
        int elfSide = Math.max(1, Math.min(dWidth / 5, dHeight / 9));
        elf = bitmapFromDrawable(R.drawable.evil_elf, elfSide, elfSide);
        layoutElfHomeY();
        explosionBitmap = bitmapFromDrawable(R.drawable.explosion_burst, santa.getWidth() * 2, santa.getWidth() * 2);
        frameWidth = 0;
        frameHeight = 0;
        if (stock < 0) stock = LevelTable.quota(level);
    }

    private Rect sleighRect() {
        int w = Math.max(1, dWidth / 3);
        int h = Math.max(1, Math.min(dHeight / 6, dHeight / 5));
        int x = (dWidth - w) / 2;
        int y = (int) hudBottom;
        return new Rect(x, y, x + w, Math.min(dHeight, y + h));
    }

    private Rect treeRect() {
        Rect dest = rectBackground != null ? rectBackground
                : new Rect(0, gameViewTop, dWidth, groundTop());
        int bmpW = background != null ? background.getWidth() : 768;
        int bmpH = background != null ? background.getHeight() : 1376;
        float scale = Math.max((float) dest.width() / bmpW, (float) dest.height() / bmpH);
        float sourceWidth = dest.width() / scale;
        float sourceHeight = dest.height() / scale;
        float left = (bmpW - sourceWidth) / 2f;
        float top = (bmpH - sourceHeight) / 2f;
        float srcX = bmpW * (384f / 768f);
        float srcY = bmpH * (1000f / 1376f);
        float screenX = dest.left + (srcX - left) * scale;
        float screenY = dest.top + (srcY - top) * scale;
        int playTop = (int) Math.ceil(hudBottom);
        int playBottom = groundTop();
        int side = Math.max(1, (Math.max(1, dWidth / 5) + Math.max(1, dWidth / 4)) / 2);
        side = Math.min(side, Math.max(1, playBottom - playTop));
        side = Math.min(side, Math.max(1, dWidth));
        int x = Math.round(screenX - side / 2f);
        int y = Math.round(screenY - side / 2f);
        x = Math.max(0, Math.min(x, Math.max(0, dWidth - side)));
        y = Math.max(playTop, Math.min(y, Math.max(playTop, playBottom - side)));
        return new Rect(x, y, x + side, y + side);
    }

    private void ensureCheckpointFrames() {
        int sw = sleighRect().width();
        int sh = sleighRect().height();
        if (sleighFrame == null || frameWidth != dWidth || frameHeight != dHeight
                || sleighStep != sleighCheckpoint()) {
            sleighStep = sleighCheckpoint();
            sleighFrame = bitmapFromDrawable(sleighRes(sleighStep), sw, sh);
        }
        if (treeFrame == null || frameWidth != dWidth || frameHeight != dHeight
                || treeStep != treeCheckpoint()) {
            treeStep = treeCheckpoint();
            Rect tree = treeRect();
            treeFrame = bitmapFromDrawable(treeRes(treeStep), tree.width(), tree.height());
        }
        frameWidth = dWidth;
        frameHeight = dHeight;
    }

    /** Greatest checkpoint in {0,20,40,60,80,100} that is <= floor(part/whole*100). */
    static int floorCheckpoint(int part, int whole) {
        if (part <= 0 || whole <= 0) return 0;
        if (part >= whole) return 100;
        int floorPct = (int) Math.floor(100.0 * part / whole);
        int chosen = 0;
        for (int step = 0; step <= 100; step += 20) {
            if (step <= floorPct) chosen = step;
        }
        return chosen;
    }

    private int inFlightPresents() {
        int count = 0;
        for (FallingObject item : fallingObjects) {
            if (item.getFallingObjectType() == FallingObjectType.PRESENT) count++;
        }
        for (PendingThrow pending : pendingThrows) {
            if (pending.type == FallingObjectType.PRESENT) count++;
        }
        return count;
    }

    private int pendingPresents() {
        int count = 0;
        for (PendingThrow pending : pendingThrows) {
            if (pending.type == FallingObjectType.PRESENT) count++;
        }
        return count;
    }

    private int sleighCheckpoint() {
        return floorCheckpoint(stock, LevelTable.quota(level));
    }

    private int treeCheckpoint() {
        return floorCheckpoint(levelCaught, LevelTable.quota(level));
    }

    private static int sleighRes(int step) {
        switch (step) {
            case 100: return R.drawable.sleigh_100;
            case 80: return R.drawable.sleigh_80;
            case 60: return R.drawable.sleigh_60;
            case 40: return R.drawable.sleigh_40;
            case 20: return R.drawable.sleigh_20;
            default: return R.drawable.sleigh_0;
        }
    }

    private static int treeRes(int step) {
        switch (step) {
            case 100: return R.drawable.tree_100;
            case 80: return R.drawable.tree_80;
            case 60: return R.drawable.tree_60;
            case 40: return R.drawable.tree_40;
            case 20: return R.drawable.tree_20;
            default: return R.drawable.tree_0;
        }
    }

    private void layoutElfHomeY() {
        Rect sleigh = sleighRect();
        elfY = sleigh.top + sleigh.height() * 0.42f;
        if (elf != null) elfY = Math.min(elfY, dHeight - elf.getHeight());
    }

    private float homeX() {
        Rect sleigh = sleighRect();
        float x = sleigh.left + sleigh.width() * 0.38f;
        if (elf == null) return x;
        return Math.max(0, Math.min(dWidth - elf.getWidth(), x));
    }

    private void handleFallingObjects() {
        updateElf();
        for (FallingObject item : fallingObjects) item.move();
        checkIfCollected();
        checkIfLeftTheScreen();
        Iterator<Map.Entry<FallingObject, Integer>> reaction = bombReactionTicks.entrySet().iterator();
        while (reaction.hasNext()) {
            Map.Entry<FallingObject, Integer> entry = reaction.next();
            if (!fallingObjects.contains(entry.getKey()) || entry.getValue() <= 1) reaction.remove();
            else entry.setValue(entry.getValue() - 1);
        }
        if (!levelComplete && explosion == null && stock <= 0
                && pendingThrows.isEmpty() && fallingObjects.isEmpty()) {
            finishLevel();
        }
    }

    private float elfRunSpeed() {
        return Math.max(3f, dWidth / 80f);
    }

    private boolean moveElfToward(float target) {
        float delta = target - elfX;
        float step = elfRunSpeed();
        if (Math.abs(delta) <= step) {
            elfX = target;
            return true;
        }
        elfX += Math.copySign(step, delta);
        return false;
    }

    private float dropElfX(PendingThrow pending) {
        float imageW = pending.image.getWidth();
        float dropX = random.nextFloat() * Math.max(1f, dWidth - imageW);
        float desired = dropX - elf.getWidth() * ElfThrowAnchor.HAND_X + imageW / 2f;
        return Math.max(0, Math.min(dWidth - elf.getWidth(), desired));
    }

    private void updateElf() {
        elfBobTicks++;
        PendingThrow pending = pendingThrows.peekFirst();
        if (pending == null) {
            dropChosen = false;
            throwTicks = 0;
            elfTargetX = homeX();
            if (moveElfToward(elfTargetX)) elfPhase = ElfPhase.HOME;
            else elfPhase = ElfPhase.RUN;
            return;
        }
        if (elfPhase != ElfPhase.THROW) {
            if (!dropChosen) {
                elfTargetX = dropElfX(pending);
                dropChosen = true;
            }
            elfPhase = ElfPhase.RUN;
            if (moveElfToward(elfTargetX)) {
                elfPhase = ElfPhase.THROW;
                throwTicks = 0;
            }
            return;
        }
        if (throwTicks < THROW_DURATION_TICKS) {
            throwTicks++;
            return;
        }
        if (!releasePendingThrow(pending)) return;
        pendingThrows.removeFirst();
        throwTicks = 0;
        dropChosen = false;
        elfPhase = ElfPhase.RUN;
        if (pendingThrows.isEmpty()) elfTargetX = homeX();
        else elfTargetX = dropElfX(pendingThrows.peekFirst());
        dropChosen = !pendingThrows.isEmpty();
    }

    private void drawElf(Canvas canvas) {
        float progress = elfPhase == ElfPhase.THROW ? Math.min(1f, throwTicks / (float) THROW_DURATION_TICKS) : 0f;
        float lean = (float) Math.sin(progress * Math.PI) * elf.getWidth() * 0.18f;
        float bob = (float) Math.sin(elfBobTicks / 6.0) * elf.getHeight() * 0.04f;
        canvas.drawBitmap(elf, elfX + lean, elfY + bob + lean * 0.35f, null);
        PendingThrow pending = pendingThrows.peekFirst();
        if (pending != null && elfPhase == ElfPhase.THROW) {
            float handX = throwX(pending.image);
            float handY = elfY + elf.getHeight() * ElfThrowAnchor.HAND_Y;
            float x = handX + (1f - progress) * elf.getWidth() * 0.35f;
            float y = handY + bob + (1f - progress) * elf.getHeight() * 0.35f
                    - (float) Math.sin(progress * Math.PI) * elf.getHeight() * 0.4f;
            canvas.drawBitmap(pending.image, x, y, null);
        }
    }

    private void addNewFallingObject() {
        if (levelComplete || stock <= pendingPresents()) return;
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

    private float throwX(Bitmap image) {
        return Math.max(0, Math.min(dWidth - image.getWidth(),
                elfX + elf.getWidth() * ElfThrowAnchor.HAND_X - image.getWidth() / 2f));
    }

    private boolean releasePendingThrow(PendingThrow pending) {
        if (pending.type == FallingObjectType.PRESENT && stock <= 0) return false;
        float x = throwX(pending.image);
        float y = elfY + elf.getHeight() * ElfThrowAnchor.HAND_Y;
        for (FallingObject live : fallingObjects) {
            if (x < live.getPosition_x() + live.getWidth()
                    && x + pending.image.getWidth() > live.getPosition_x()
                    && live.getPosition_y() < y + pending.image.getHeight()) {
                return false;
            }
        }
        FallingObject item = new FallingObject();
        if (pending.type == FallingObjectType.BOMB) {
            item.InitializeAsBomb(pending.speedMultiplier, pending.image, x, y);
            item.preserveReactionInterval(Math.max(0, santaY - (y + item.getHeight())));
            bombReactionTicks.put(item, 40);
        } else if (pending.type == FallingObjectType.SNOWFLAKE) {
            item.InitializeAsSnowflake(pending.speedMultiplier, pending.image, x, y);
        } else {
            item.InitializeAsPresent(pending.speedMultiplier, pending.image, x, y);
            stock = Math.max(0, stock - 1);
        }
        fallingObjects.add(item);
        return true;
    }

    private float brokenPresentGroundY(Bitmap img) {
        float feetY = groundTop() + groundHeight() * 0.50f;
        return Math.max(hudBottom, feetY - img.getHeight());
    }

    private void checkIfLeftTheScreen() {
        float passY = santaY + santa.getHeight();
        Iterator<FallingObject> iterator = fallingObjects.iterator();
        while (iterator.hasNext()) {
            FallingObject item = iterator.next();
            if (!bombReactionTicks.containsKey(item) && item.getPosition_y() > passY) {
                if (item.costsLifeIfMissed()) {
                    missedPresents++;
                    combo = 0;
                    int variant = Math.max(0, presentImages.indexOf(item.getImage()));
                    if (!brokenPresentImages.isEmpty()) {
                        Bitmap brokenImg = brokenPresentImages.get(Math.min(variant, brokenPresentImages.size() - 1));
                        float bx = Math.max(0, Math.min(dWidth - brokenImg.getWidth(), item.getPosition_x()));
                        float by = brokenPresentGroundY(brokenImg);
                        float xFraction = dWidth > 0 ? bx / dWidth : 0f;
                        brokenPresents.add(new BrokenPresent(variant, xFraction, bx, by));
                    }
                }
                iterator.remove();
            }
        }
    }

    private boolean hitsSanta(FallingObject item) {
        float left = item.getPosition_x();
        float top = item.getPosition_y();
        return left < santaX + santa.getWidth()
                && left + item.getWidth() > santaX
                && top < santaY + santa.getHeight()
                && top + item.getHeight() > santaY;
    }

    private void checkIfCollected() {
        Iterator<FallingObject> iterator = fallingObjects.iterator();
        while (iterator.hasNext()) {
            FallingObject item = iterator.next();
            // Impossible resize geometry can overlap Santa even at hudBottom.
            // Defer both collision and off-screen removal until the interval expires.
            if (bombReactionTicks.containsKey(item)) continue;
            if (!hitsSanta(item)) continue;
            Point center = item.getCenter();
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
            } else if (item.getFallingObjectType() == FallingObjectType.SNOWFLAKE) {
                if (!brokenPresents.isEmpty()) {
                    brokenPresents.remove(brokenPresents.size() - 1);
                    missedPresents = Math.max(0, missedPresents - 1);
                    levelCaught++;
                    score++;
                }
            }
            burstCount = 8;
            burstStartMs = SystemClock.uptimeMillis();
            for (int i = 0; i < burstCount; i++) { burstX[i] = center.x; burstY[i] = center.y; }
            if (levelComplete) break;
        }
    }

    private void finishLevel() {
        if (missedPresents > 3) {
            runWon = false;
            gameOver();
            return;
        }
        if (level >= LevelTable.LEVEL_COUNT) {
            runWon = true;
            gameOver();
            return;
        }
        enterLevelComplete();
    }

    private void enterLevelComplete() {
        if (levelComplete) return;
        levelComplete = true;
        pauseGameplayHandlers();
        postInvalidate();
    }

    private void advanceFromLevelCard() {
        if (!levelComplete || gameOverRunnable != null) return;
        levelComplete = false;
        if (level >= LevelTable.LEVEL_COUNT) {
            runWon = true;
            gameOver();
            return;
        }
        level++;
        levelCaught = 0;
        missedPresents = 0;
        brokenPresents.clear();
        combo = 0;
        comboPeak = 0;
        stock = LevelTable.quota(level);
        dropChosen = false;
        elfPhase = ElfPhase.HOME;
        elfTargetX = homeX();
        treeStep = -1;
        sleighStep = -1;
        spawnRemaining = LevelTable.spawnPeriodMs(level);
        resumeGameplay();
        postInvalidate();
    }

    private int getScore() { return score; }
}
