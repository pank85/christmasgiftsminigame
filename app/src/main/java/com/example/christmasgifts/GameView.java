package com.example.christmasgifts;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.res.ResourcesCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends View {

    private final Context context;
    private float basketX,basketY;
    private float oldX;
    private float oldBasketX;
    private Bitmap basket,ground,background;
    private Rect rectBackground,rectGround;
    static int dWidth,dHeight,gameViewTop;
    private Bitmap explosion=null;
    private Bitmap bombImg=null;

    private Handler handler;
    private Runnable runnableFallingPresents;
    private Runnable runnableNewPresents;

    private final Paint textPaint=new Paint();

    private Random random;
    private final List<FallingObject> fallingObjects = new ArrayList<>() ;
    private final List<FallingObject> collectedObjects = new ArrayList<>() ;
    private final List<FallingObject> missedObjects = new ArrayList<>() ;
    private final List<FallingObject> explodedBombs= new ArrayList<>();
    private final List<Bitmap> presentImages = new ArrayList<>() ;

    private final int moveObjectPeriod =10;
    private int addNewObjectPeriod =2000;
    private double presentSpeedMultiplier=1.00;
    private final int speedUpCounterStartValue=10;
    private int speedUpCounter=speedUpCounterStartValue;

    private int highScore;
    SharedPreferences sharedPreferences;

    public GameView(Context context)
    {
        super(context);
        this.context=context;
        this.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        InitScreenSize();
        InitBitmaps();
        InitRest();
        Start();

       }

    private void InitRest() {
        textPaint.setColor(Color.GRAY);
        textPaint.setTextSize(50);
        textPaint.setTypeface(ResourcesCompat.getFont(context,R.font.winter_snow_regular));
        textPaint.setStyle(Paint.Style.FILL);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        highScore = sharedPreferences.getInt(context.getString(R.string.high_score), 0);
    }

    private void Start() {
        handler= new Handler();
        random= new Random();
        runnableFallingPresents= () -> {
            handleFallingObjects();
            postInvalidate();
            if(checkGameOver())
                gameOver();
            else
                handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
        };
        handler.postDelayed(runnableFallingPresents, moveObjectPeriod);
        runnableNewPresents= () -> {
            addNewFallingObject();
            postInvalidate();
            if(!checkGameOver())
                handler.postDelayed(runnableNewPresents, addNewObjectPeriod);
        };
        handler.postDelayed(runnableNewPresents,0);
    }


    @Override
    public  boolean onTouchEvent(MotionEvent event)
    {
        float touchX=event.getX();
        float touchY=event.getY();

        if(touchY>=basketY ){
            int action=event.getAction();
            if(action==MotionEvent.ACTION_DOWN){
                oldX=event.getX();
                oldBasketX=basketX;
            }

            if(action== MotionEvent.ACTION_MOVE){
                float shift =oldX-touchX;
                float newBasketX=oldBasketX-shift;
                if(newBasketX<=0)
                    basketX=0;
                else if(newBasketX>=dWidth-basket.getWidth())
                    basketX=dWidth-basket.getWidth();
                else
                    basketX=newBasketX;
                postInvalidate();
            }

        }


        return  true;
    }


    @Override
    protected void  onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.drawBitmap(background,null,rectBackground,null);
        canvas.drawBitmap(ground,null,rectGround,null);
        if(explosion!=null)
        {
            float explosionX=basketX+(float)basket.getWidth()/2-(float)explosion.getWidth()/2;
            float explosionY=basketY+(float)basket.getHeight()/2-(float)explosion.getHeight()/2;
            canvas.drawBitmap(explosion,explosionX,explosionY,null);
        }else
        {
            canvas.drawBitmap(basket, basketX, basketY, null);

            for (FallingObject present : fallingObjects) {
                canvas.drawBitmap(present.getImage(), present.getPosition_x(), present.getPosition_y(), null);
            }
            explodedBombs.clear();
            for (FallingObject fo : missedObjects) {
                if (fo.getFallingObjectType()==FallingObjectType.PRESENT)
                    canvas.drawBitmap(fo.getImage(), fo.getPosition_x(), fo.getPosition_y(), null);
                else {
                    canvas.drawBitmap(getExplosionBitmap(), fo.getPosition_x(), fo.getPosition_y(), null);
                    explodedBombs.add(fo);
                }
            }
            explodedBombs.forEach(missedObjects::remove);
        }
        canvas.drawText(getScoreTxt(),(float) dWidth/5,gameViewTop,textPaint);
        canvas.drawText(getHighScoreTxt(),(float) 3*dWidth/5,gameViewTop,textPaint);

    }

    private boolean checkGameOver() {
        return getRemainingLives() == 0 || explosion!=null;
    }

    private void gameOver(){
        handler.removeCallbacks(runnableNewPresents);
        if (getScore()>highScore)
        {
            sharedPreferences.edit().putInt(context.getString(R.string.high_score), getScore()).apply();
        }

        Runnable r = () -> {
            Intent intent=new Intent(context,GameOver.class);
            intent.putExtra(context.getString(R.string.score),getScore());
            intent.putExtra(context.getString(R.string.high_score),highScore);
            context.startActivity(intent);
            ((Activity) context).finish();
        };
        handler.postDelayed(r,2000);

    }

    private void InitScreenSize() {
        Display display=((Activity) getContext()).getWindowManager().getDefaultDisplay();
        Point size =new Point();
        display.getSize(size);
        dWidth= size.x;
        dHeight= size.y;


        gameViewTop=dHeight/20;
    }

    private  void InitBitmaps()
    {
        background= BitmapFactory.decodeResource(context.getResources(),R.drawable.in_game_background);

        ground=BitmapFactory.decodeResource(context.getResources(),R.drawable.ground);
        rectBackground=new Rect(0,gameViewTop,dWidth,dHeight-dHeight/10);
        rectGround=new Rect(0,dHeight-dHeight/10,dWidth,dHeight);
        Bitmap tmp=BitmapFactory.decodeResource(context.getResources(),R.drawable.basket);
        basket=Bitmap.createScaledBitmap(tmp,dWidth/5,dHeight/10,false);
        basketX=(float) dWidth/2-(float) basket.getWidth()/2;
        basketY=dHeight-ground.getHeight()-basket.getHeight();

        loadPresentBitmaps();
    }

    private void handleFallingObjects() {
        fallingObjects.forEach(fallingObject -> fallingObject.setPosition_y(fallingObject.getPosition_y()+fallingObject.getSpeed()));
        checkIfCollected();
        checkIfLeftTheScreen();
    }

    private  void addNewFallingObject(){
        addNewPresent();
        addNewBomb();
    }
    private  void addNewPresent()
    {
        long countPresents= fallingObjects.stream().filter(f-> f.getFallingObjectType()==FallingObjectType.PRESENT).count();
        long limit=(long) (3+2*Math.floor(presentSpeedMultiplier));
        if (  countPresents<=limit &&
                (random.nextInt(5)==1 || fallingObjects.isEmpty()))
        {
            int presentsToAdd=random.nextInt(2)+1;
            for (int i=1 ;i<=presentsToAdd ;i++){
                FallingObject newPresent=new FallingObject();
                int imgId=random.nextInt(3);
                newPresent.InitializeAsPresent(presentSpeedMultiplier,presentImages.get(imgId));
                fallingObjects.add(newPresent);
            }

        }
    }

    private  void addNewBomb()
    {
        long countBombs = fallingObjects.stream().filter(f-> f.getFallingObjectType()==FallingObjectType.BOMB).count();
        long limit=(long) (0+Math.floor(presentSpeedMultiplier));
        if (  countBombs<=limit && random.nextInt(15)==1 )
        {
            long presentsToAdd=(long)random.nextInt(2)+1;
            if(presentsToAdd+countBombs>limit)
                presentsToAdd=limit-countBombs;

            for (long i=1 ;i<=presentsToAdd ;i++){
                FallingObject newBomb=new FallingObject();
                newBomb.InitializeAsBomb(presentSpeedMultiplier,getBombBitmap());
                fallingObjects.add(newBomb);
            }

        }
    }

    private void speedUp() {
        if(speedUpCounter<=0)
        {

            if (addNewObjectPeriod >500)
                addNewObjectPeriod -=(int) Math.floor((double) addNewObjectPeriod *0.9);

            presentSpeedMultiplier*=1.2;
            speedUpCounter=speedUpCounterStartValue;
        }
        else
            speedUpCounter --;
    }

    private void checkIfLeftTheScreen() {
        List<FallingObject> remove=new ArrayList<>();
        for (FallingObject present: fallingObjects) {
            if (present.getPosition_y()>rectGround.top){
                remove.add(present);
            }
        }
        for (FallingObject present:remove) {
            fallingObjects.remove(present);
            missedObjects.add(present);
        }
        remove.clear();
    }

    private void checkIfCollected() {
        List<FallingObject> collected=new ArrayList<>();
        for (FallingObject fo: fallingObjects) {
            int presentX=fo.getCenter().x;
            int presentY=fo.getCenter().y;
            if (presentX>=basketX
                    && presentX<=basketX+basket.getWidth()
                    && presentY>=basketY
                    && presentY<=basketY+basket.getHeight()){
                collected.add(fo);
                speedUp();
            }
        }
        for (FallingObject fo:collected) {
            fallingObjects.remove(fo);
            if(fo.getFallingObjectType()==FallingObjectType.BOMB)
            {
                explode();
            }
            else
            {
                collectedObjects.add(fo);
            }

        }
        collected.clear();
    }


    private void explode() {
        Bitmap img;
        img = BitmapFactory.decodeResource(context.getResources(),R.drawable.explosion);
        explosion=Bitmap.createScaledBitmap(img,basket.getWidth()*2,basket.getWidth()*2,false);
    }

    private Bitmap explosionBitmap=null;
    private  Bitmap getExplosionBitmap()
    {
        if (explosionBitmap==null)
        {
            Bitmap img;
            img = BitmapFactory.decodeResource(context.getResources(),R.drawable.explosion);
            explosionBitmap=Bitmap.createScaledBitmap(img,GameView.dWidth/8,GameView.dHeight/8,false);
        }

        return  explosionBitmap;
    }

    private int getScore(){
        return collectedObjects.size();
    }

    private String getScoreTxt(){
        return  "score: " + getScore() ;
    }

    private String getHighScoreTxt(){
        return  "high score: "+ highScore;
    }

    private int getRemainingLives(){
        return 3 - (int)missedObjects.stream().filter(m-> m.getFallingObjectType()==FallingObjectType.PRESENT).count();
    }


    private void loadPresentBitmaps(){

            for (int i=1;i<=3;i++){
                Bitmap img;

                switch (i) {
                    case 1:
                        img = BitmapFactory.decodeResource(context.getResources(),R.drawable.present_red_2);
                        break;
                    case 2:
                        img = BitmapFactory.decodeResource(context.getResources(),R.drawable.present_red);
                        break;
                    default:
                        img = BitmapFactory.decodeResource(context.getResources(),R.drawable.present_green);
                        break;
                }
                presentImages.add(Bitmap.createScaledBitmap(img,GameView.dWidth/8,GameView.dHeight/8,false));
            }
    }




    private Bitmap getBombBitmap() {
        if (bombImg==null)
        {
            Bitmap img;
            img = BitmapFactory.decodeResource(context.getResources(),R.drawable.bomb);
            bombImg= Bitmap.createScaledBitmap(img,GameView.dWidth/8,GameView.dHeight/8,false);
        }
        return  bombImg;
    }
}

