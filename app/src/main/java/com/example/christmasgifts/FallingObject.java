package com.example.christmasgifts;
import android.graphics.Bitmap;
import android.graphics.Point;

import java.util.Random;


public class FallingObject {

    private float position_x;
    private float position_y;
    private float speed;
    private Bitmap image;
    private  FallingObjectType fallingObjectType;

    public void InitializeAsPresent(double speedMultiplier,Bitmap img){
        fallingObjectType=FallingObjectType.PRESENT;
        Random random = new Random();
        this.image= img;
        position_x= random.nextInt(GameView.dWidth- getWidth());
        position_y=2;
        int initialSpeed=GameView.dWidth/250;
        int maxExtraSpeed=initialSpeed/2+1;
        speed=(float) Math.ceil( speedMultiplier * (double) random.nextInt(maxExtraSpeed)+initialSpeed);
    }

    public void InitializeAsBomb(double speedMultiplier,Bitmap img){
        fallingObjectType=FallingObjectType.BOMB;
        this.image= img;
        Random random = new Random();
        position_x= random.nextInt(GameView.dWidth- getWidth());
        position_y=2;
        int initialSpeed=GameView.dWidth/250;
        speedMultiplier+=3;
        int maxExtraSpeed=initialSpeed/2+1;
        speed=(float) Math.ceil( speedMultiplier * (double) random.nextInt(maxExtraSpeed)+initialSpeed);
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

    public Bitmap getImage(){
        return  image;
    }
}

