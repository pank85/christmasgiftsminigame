package com.example.christmasgifts;
import android.graphics.Bitmap;
import android.graphics.Point;

import java.util.Random;


public class FallingObject {

    private float position_x;
    private float position_y;
    private float speed;
    private float baseSpeed;
    private int remainingReactionTicks;
    private double speedMultiplier;
    private Bitmap image;
    private  FallingObjectType fallingObjectType;

    public void InitializeAsPresent(double speedMultiplier, Bitmap img, float spawnX, float spawnY){
        fallingObjectType=FallingObjectType.PRESENT;
        this.speedMultiplier = speedMultiplier;
        Random random = new Random();
        this.image= img;
        position_x = spawnX;
        position_y = spawnY;
        int initialSpeed=Math.max(1, GameView.dWidth/250);
        int maxExtraSpeed=initialSpeed/2+1;
        speed=(float) (speedMultiplier * (initialSpeed + random.nextInt(maxExtraSpeed)));
    }

    public void InitializeAsBomb(double speedMultiplier, Bitmap img, float spawnX, float spawnY){
        fallingObjectType=FallingObjectType.BOMB;
        this.speedMultiplier = speedMultiplier;
        this.image= img;
        Random random = new Random();
        position_x = spawnX;
        position_y = spawnY;
        int initialSpeed=Math.max(1, GameView.dWidth/250);
        int maxExtraSpeed=initialSpeed/2+1;
        speed=(float) (speedMultiplier * (initialSpeed + 4 * random.nextInt(maxExtraSpeed)));
        baseSpeed = speed;
        remainingReactionTicks = 40;
    }

    public void InitializeAsSnowflake(double speedMultiplier, Bitmap img, float spawnX, float spawnY) {
        InitializeAsPresent(speedMultiplier, img, spawnX, spawnY);
        fallingObjectType = FallingObjectType.SNOWFLAKE;
    }

    public int getScoreValue() {
        switch (fallingObjectType) {
            case PRESENT:
                return 1;
            default:
                return 0;
        }
    }

    public boolean costsLifeIfMissed() {
        switch (fallingObjectType) {
            case PRESENT:
                return true;
            default:
                return false;
        }
    }

    public FallingObjectType getFallingObjectType() {
            return this.fallingObjectType;
    }

    public Point getCenter(){
        int x= (int) (getPosition_x()+ getWidth()/2);
        int y= (int) (getPosition_y()+ getHeight()/2);
        return new Point(x,y);
    }
    public int getWidth(){
        return image.getWidth();
    }

    public int getHeight(){
        return  image.getHeight();
    }

    public  float getSpeed(){
        return speed;
    }



    public float getPosition_x(){
        return  position_x;
    }

    public float getPosition_y(){
        return  position_y;
    }

    public void setPosition_y (float newY){
        position_y=newY;
    }

    public void relayout(Bitmap image, float x, float y) {
        this.image = image;
        position_x = x;
        position_y = y;
    }

    public void preserveReactionInterval(float remainingDistance) {
        if (remainingReactionTicks > 0) {
            speed = Math.min(baseSpeed, Math.max(0f, remainingDistance) / remainingReactionTicks);
        }
    }

    public void move() {
        position_y += speed;
        if (remainingReactionTicks > 0 && --remainingReactionTicks == 0) {
            speed = baseSpeed;
        }
    }

    public Bitmap getImage(){
        return  image;
    }
}

