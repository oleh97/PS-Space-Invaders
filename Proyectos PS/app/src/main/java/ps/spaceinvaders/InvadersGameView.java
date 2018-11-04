package ps.spaceinvaders;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.os.Handler;
import android.view.View;

import java.util.ArrayList;

public class InvadersGameView extends SurfaceView implements Runnable {

    private boolean mode;

    Context context;

    private Thread mainTh;
    UpdateEnemiesThread enemiesThread;
    BulletManagerThread bulletThread;

    private SurfaceHolder holder;

    private volatile boolean isPlaying;

    private boolean isPaused;

    private Canvas canvas;
    private Paint paint;

    private int screenX;
    private int screenY;

    private long fps;
    private long timeFrame;
    private long timer;
    private long spawnTimer;
    private  int increments = 1;

    //Elementos del juego
    private ArrayList<Enemy> enemiesList = new ArrayList();
    private ArrayList<Enemy> spawnedEnemies = new ArrayList();
    private SpaceShip spaceShip;
    private Defence[] blocks = new Defence[400];

    //Controlar las balas
    private ArrayList<Bullet> bullets = new ArrayList();
    private ArrayList<Bullet> removedBullets = new ArrayList();
    //Las naves no pueden diparar mas N balas por vez
    private boolean fullCapacity;
    private int enemyBulletsCount;
    private int maxEnemyBullets = 10;

    int totalEnemies = 0;
    int killedEnemies = 0;
    private int numDefences;
    private boolean firstSpawn;
    private int spawnCount;
    private Enemy lastSpawned;

    //Puntuacion
    int score = 0;
    boolean lost = false;

    Handler handler = new Handler();
    boolean isReloading = false;

    private boolean animation = true;
    private long timeAnim = 1000;

    private long lastTime = System.currentTimeMillis();

    private boolean changeColor=false;

    //Botones de movimiento y disparo
    private Buttons izq,der,dis,arr,abj;
    private String name;

    public InvadersGameView (Context context, int x, int y, boolean isViolent,String name){
        super(context);

        this.context = context;
        this.name=name;

        this.mode = isViolent;

        holder = getHolder();
        paint = new Paint();

        screenX= x;
        screenY= y;

        isPaused = true;
        //saveInfo(this);

        iniLvl();
    }

    //THREADS QUE VAMOS A USAR

    //-----THREAD QUE ESPERA N SEGUNDOS----//

    //--------THREAD QUE INICIALIZA LA PARTIDA--------//
    class LoadingThread extends Thread {
        @Override
        public void run() {
            try {
                lastSpawned = new Enemy(context, 0, 0, screenX, screenY);
                firstSpawn = false;
                spawnCount = 0;
                increments = 1;
                isReloading = false;
                spaceShip = new SpaceShip(context, screenX, screenY);
                spaceShip.resetShootsCount();
                izq=new Buttons(context,screenX,screenY,R.drawable.izq);
                der=new Buttons(context,screenX,screenY,R.drawable.der);
                dis=new Buttons(context,screenX,screenY,R.drawable.scope);
                arr=new Buttons(context,screenX,screenY,R.drawable.arr);
                abj=new Buttons(context,screenX,screenY,R.drawable.abj);
                changeColor = false;
                killedEnemies = 0;
                bullets.clear();
                enemiesList.clear();
                spawnedEnemies.clear();
                score = 0;

                fullCapacity = false;
                enemyBulletsCount = 0;

                // Construye las defensas
                numDefences= 0;
                for(int shelterNumber = 0; shelterNumber < 4; shelterNumber++){
                    for(int column = 0; column < 10; column ++ ) {
                        for (int row = 0; row < 5; row++) {
                            if (!(row>1 && (column>1&&column<8))) {
                                blocks[numDefences] = new Defence(row, column, shelterNumber, screenX, screenY);
                                numDefences++;
                            }

                        }
                    }
                }

                // Construye la formación enemiga
                //numEnemies = 0;
                for(int column = 0; column < 4; column ++ ){
                    for(int row = 0; row < 3; row ++ ){
                        Enemy e = new Enemy(context, row+1, column, screenX, screenY);
                        enemiesList.add(e);
                    }
                }

                totalEnemies = enemiesList.size();
                lost=false;
            }
            catch (Exception e) {
                System.out.println("Error while loading the game");
                e.printStackTrace();
            }
        }
    }


    //---------THREAD QUE SE ENCARGA DEL MOVIMIENTO DE LOS ALIENS---//
    class UpdateEnemiesThread extends Thread {
        @Override
        public void run() {
            try {
                boolean bumped = false;
                // Actualiza todos los enemies activos
                for (int i = 0; i < enemiesList.size(); i++) {
                    if(RectF.intersects(spaceShip.getRect(), enemiesList.get(i).getRect())){
                        lost = true;
                    }
                    enemiesList.get(i).angryEnemie(killedEnemies);
                    //if (enemiesList.get(i).getVisibility()) {
                        // Mueve enemy
                        enemiesList.get(i).update(fps);
                        checkAlienBlockCollision(enemiesList.get(i));
                        // ¿Quiere hacer un disparo?
                        if (!fullCapacity && enemiesList.get(i).randomShot(spaceShip.getX(),
                                spaceShip.getLength(), killedEnemies)) {
                            Bullet b = new Bullet(context, screenY, screenX);
                            b.setEnemyBullet(true);
                            b.setFriend(true);
                            bullets.add(b);
                            enemyBulletsCount++;
                            if (bullets.get(bullets.size() - 1).shoot(enemiesList.get(i).getX()
                                            + enemiesList.get(i).getLength() / 2,
                                    enemiesList.get(i).getY(), bullets.get(bullets.size() - 1).DOWN)) {
                                if (enemyBulletsCount == maxEnemyBullets) {
                                    fullCapacity = true;
                                }
                            }
                        }
                        if (enemiesList.get(i).getX() > screenX - enemiesList.get(i).getLength() || enemiesList.get(i).getX() < 0){
                            bumped = true;
                        }
                    //}
                }
                if(bumped){
                    // Mueve a todos los invaders hacia abajo y cambia la dirección
                    for(int i = 0; i < enemiesList.size(); i++){
                        enemiesList.get(i).enemyCicle();
                        // Han llegado abajo
                        if((enemiesList.get(i).getY() > screenY - screenY / 10)&& enemiesList.get(i).isVisible){
                            lost = true;
                        }
                    }
                }

            }
            catch (Exception e) {
                System.out.println("Error moviendo aliens");
                e.printStackTrace();
            }
        }
    }

    //--------- THREAD ENCARGADO DE GESTIONAR LAS BALAS ------//
    class BulletManagerThread extends Thread{
        @Override
        public void run() {
            try {
                for (Bullet b : bullets) {

                    b.update(fps);

                    //Comprueba limites pantalla
                    if (b.getImpactPointY() < 0 || b.getImpactPointY() > screenY) {
                        b.changeDirection();
                        b.updateBounceCounts();
                        //Una bala solo puede rebotar 2 veces
                        if (b.getBounceCounts() == 2) {
                            removedBullets.add(b);
                        }
                        b.setFriend(false);
                    }

                    //Si la bala choca con los enemigos
                    checkEnemyCollision(b);

                    //Si la bala choca con los bloques
                    checkBlockCollision(b);

                    //Si la bala choca con el jugador
                    if (RectF.intersects(b.getRect(), spaceShip.getRect())) {
                        lost = true;
                    }
                }
            }
            catch (Exception e){
                System.out.println("Error gestionando las balas");
                e.printStackTrace();
            }
        }
    }

    private void iniLvl(){
        LoadingThread load = new LoadingThread();
        load.run();
        enemiesThread = new UpdateEnemiesThread();
        bulletThread = new BulletManagerThread();
        spawnTimer = -1;
    }

    //Si la bala choca con los enemigos
    public void checkEnemyCollision(Bullet b) {
        for(int i = 0; i < enemiesList.size(); i++) {
            //if (enemiesList.get(i).getVisibility()) {
                if (!b.getFriend() && RectF.intersects(b.getRect(), enemiesList.get(i).getRect())) {
                    if(enemiesList.get(i).isSpawned) {
                        spawnedEnemies.remove(enemiesList.get(i));
                    }
                    enemiesList.remove(enemiesList.get(i));
                    removedBullets.add(b);
                    score = score + 100;
                    killedEnemies++;
                    checkVictory();
                }
            //}
        }
    }

    public void checkAlienBlockCollision(Enemy e) {
        for(int i = 0; i < numDefences; i++) {
            if(blocks[i].getActive()) {
                if(RectF.intersects(blocks[i].getRect(), e.getRect())) {
                    blocks[i].destoyDefence();
                }
            }
        }
    }

    public void checkBlockCollision(Bullet b){
        for(int i = 0; i < numDefences; i++){
            if(blocks[i].getActive()){
                if(RectF.intersects(b.getRect(), blocks[i].getRect())){
                    //b.setInactive();
                    blocks[i].destoyDefence();
                    removedBullets.add(b);
                    if(b.getEnemyBullet()) {
                        changeColor =!changeColor;
                    }
                }
            }
        }
    }

    public void checkVictory() {
        if(score == totalEnemies * 100){
            lost = true;
        }
    }

    public void playerShoot() {
        Bullet b = new Bullet(context, screenY, screenX);
        bullets.add(b);
        b.shoot(spaceShip.getX() + spaceShip.getLength() / 2, spaceShip.getY() - spaceShip.getHeight(), b.UP);
    }

    @Override
    public void run() {
        while(isPlaying) {
            long iniFrameTime = System.currentTimeMillis();

            if (!isPaused) {
                update();

                if((iniFrameTime - lastTime) > timeAnim){
                    lastTime = System.currentTimeMillis();

                    animation = !animation;
                }
            }
            draw();
            timeFrame = System.currentTimeMillis() - iniFrameTime;
            if (timeFrame >= 1) {
                fps = 1000 / timeFrame;
            }
        }
    }

    private void update(){
        if(!spawnedEnemies.isEmpty()){
            lastSpawned = spawnedEnemies.get(spawnedEnemies.size()-1);
        }

        if(System.currentTimeMillis() >= spawnTimer+1000*increments) {
            if(spawnedEnemies.isEmpty()) {
                //System.out.println("spawn");
                if(enemiesList.get(0).getRow() > 0) {
                    Enemy e = new Enemy(context, 0, 0, screenX, screenY);
                    e.setX(enemiesList.get(0).getX());
                    e.setY(enemiesList.get(0).getY()-enemiesList.get(0).getHeight()-enemiesList.get(0).getPadding()/2);
                    e.setEnemySpeed(enemiesList.get(0).getEnemySpeed());
                    e.setEnemyMoving(enemiesList.get(0).getEnemyMoving());
                    e.setSpawned(true);
                    totalEnemies++;
                    //firstSpawn = true;
                    spawnCount++;
                    //lastSpawned = e;
                    spawnedEnemies.add(e);
                    enemiesList.add(e);
                }
            }
            else if (spawnCount < 4) {
                //System.out.println("poner izquieda");
                if (enemiesList.get(0).getRow() > 0) {
                    Enemy e = new Enemy(context, 0, 0, screenX, screenY);
                    e.setX(spawnedEnemies.get(spawnedEnemies.size()-1).getX() + lastSpawned.getLength() + spawnedEnemies.get(spawnedEnemies.size()-1).getPadding());
                    e.setY(spawnedEnemies.get(spawnedEnemies.size()-1).getY());
                    e.setEnemySpeed(enemiesList.get(0).getEnemySpeed());
                    e.setEnemyMoving(enemiesList.get(0).getEnemyMoving());
                    e.setSpawned(true);
                    totalEnemies++;
                    spawnCount++;
                    //lastSpawned = e;
                    spawnedEnemies.add(e);
                    enemiesList.add(e);
                }
            }
            else {
                //System.out.println("poner encima");
                if(enemiesList.get(0).getRow() > 0 && lastSpawned.getRow() > 0) {
                    Enemy e = new Enemy(context, 0, 0, screenX, screenY);
                    e.setX(spawnedEnemies.get(spawnedEnemies.size()-1).getX()-((spawnedEnemies.get(spawnedEnemies.size()-1).getLength()*(spawnCount-1))+(spawnedEnemies.get(spawnedEnemies.size()-1).getPadding()*(spawnCount-1))));
                    e.setY(spawnedEnemies.get(spawnedEnemies.size()-1).getY() - spawnedEnemies.get(spawnedEnemies.size()-1).getHeight() - spawnedEnemies.get(spawnedEnemies.size()-1).getPadding() / 2);
                    e.setEnemySpeed(enemiesList.get(0).getEnemySpeed());
                    e.setEnemyMoving(enemiesList.get(0).getEnemyMoving());
                    e.setSpawned(true);
                    totalEnemies++;
                    spawnCount = 1;
                    //lastSpawned = e;
                    spawnedEnemies.add(e);
                    enemiesList.add(e);
                }
            }

            increments++;
        }
        // Mueve la nave espacial
        spaceShip.update(fps);
        //Llamada el thread que se encarga de los aliens
        enemiesThread.run();

        //Thread encargado de gestionar las balas y todas sus comprobaciones
        bulletThread.run();

        //Limpia las balas que han tocado algo
        bullets.removeAll(removedBullets);
        for(Bullet b : removedBullets) {
            if(b.getEnemyBullet()) {
                enemyBulletsCount--;
            }
        }
        removedBullets.clear();

        if (enemyBulletsCount < maxEnemyBullets) {
            fullCapacity = false;
        }

        if(System.currentTimeMillis() >= timer+2000){
            isReloading = false;
            spaceShip.resetShootsCount();
        }

        if(lost){
            isPaused = true;
            saveInfoR(this,score,name);
            display(this);
            iniLvl();
        }
    }

    private void draw(){
        if (holder.getSurface().isValid()) {
            canvas = holder.lockCanvas();

            canvas.drawColor(Color.argb(255, 0, 0, 0));


            paint.setColor(Color.argb(255, 255, 255, 255));

            //Pintar la puntuación
            paint.setColor(Color.argb(255, 249, 129, 0));
            paint.setTextSize(50);
            canvas.drawText("Score: " + score, 30,50, paint);

            // Dibuja la nave espacial

            canvas.drawBitmap(izq.getBitmap(), screenX/20*1, screenY - 200, paint);
            canvas.drawBitmap(der.getBitmap(), screenX/20*5, screenY - 200, paint);
            canvas.drawBitmap(dis.getBitmap(), screenX/20*9, screenY - 200, paint);
            canvas.drawBitmap(arr.getBitmap(), screenX/20*13, screenY - 200, paint);
            canvas.drawBitmap(abj.getBitmap(), screenX/20*17, screenY - 200, paint);
            canvas.drawBitmap(spaceShip.getBitmap(), spaceShip.getX(), spaceShip.getY(), paint);

            // Dibuja las defensas no destruidas
            for(int i = 0; i < numDefences; i++){
                if(blocks[i].getActive()) {
                    canvas.drawRect(blocks[i].getRect(), paint);
                }
            }

            // Dibuja a los invaders
            for(int i = 0; i < enemiesList.size(); i++) {
                //if (enemiesList.get(i).getVisibility()) {
                    if (!changeColor) {
                        if (animation) {
                            canvas.drawBitmap(enemiesList.get(i).getBitmap(), enemiesList.get(i).getX(), enemiesList.get(i).getY(), paint);
                        } else {
                            canvas.drawBitmap(enemiesList.get(i).getBitmap2(), enemiesList.get(i).getX(), enemiesList.get(i).getY(), paint);
                        }
                    } else {
                        if (animation) {
                            canvas.drawBitmap(enemiesList.get(i).getBitmap3(), enemiesList.get(i).getX(), enemiesList.get(i).getY(), paint);
                        } else {
                            canvas.drawBitmap(enemiesList.get(i).getBitmap4(), enemiesList.get(i).getX(), enemiesList.get(i).getY(), paint);
                        }

                    }
                //}
              }

            // Dibuja las balas de los invaders

            // Actualiza todas las balas de los invaders si están activas
            for(Bullet b : bullets) {
                if(!b.getEnemyBullet()){
                    canvas.drawBitmap(b.getBulletSpaceship(), b.getX()-b.getLength()/2, b.getY(), paint);
                }
                else {
                    canvas.drawBitmap(b.getBulletEnemy(), b.getX()-b.getLength()/2, b.getY(), paint);
                }
            }

            holder.unlockCanvasAndPost(canvas);
        }
    }

    public void pause(){
        isPlaying = false;
    }

    public void resume(){
        isPlaying = true;
        mainTh = new Thread(this);
        mainTh.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {

        if(!lost) {
            switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                // El jugador ha pulsado la pantalla
                case MotionEvent.ACTION_DOWN:
                    isPaused = false;
                    if ((motionEvent.getX() > (screenX/20*1))&&(motionEvent.getX() < (screenX/20*1+izq.getLength())) && (motionEvent.getY() > (screenY - (screenY / 6)))) {
                        spaceShip.setMovementState(spaceShip.LEFT); }
                    else if ((motionEvent.getX() > (screenX/20*5))&&(motionEvent.getX() < (screenX/20*5+izq.getLength())) && (motionEvent.getY() > (screenY - (screenY / 6)))) {
                        spaceShip.setMovementState(spaceShip.RIGHT);
                    }
                    if ((motionEvent.getX() > (screenX/20*9))&&(motionEvent.getX() < (screenX/20*9+izq.getLength())) && (motionEvent.getY() > (screenY - (screenY / 6)))) {
                        if(!isReloading) {
                            playerShoot();
                            spaceShip.addShootsCount();
                            timer = System.currentTimeMillis();
                            if(spaceShip.getShootsCount() >= 2) {
                                isReloading = true;
                            }
                        }
                    }
                    else if ((motionEvent.getX() > (screenX/20*13))&&(motionEvent.getX() < (screenX/20*13+izq.getLength())) && (motionEvent.getY() > (screenY - (screenY / 6)))) {
                        spaceShip.setMovementState(spaceShip.UP);
                    }
                    else if ((motionEvent.getX() > (screenX/20*17))&&(motionEvent.getX() < (screenX/20*17+izq.getLength())) && (motionEvent.getY() > (screenY - (screenY / 6)))) {
                        spaceShip.setMovementState(spaceShip.DOWN);
                    }
                    break;

                // Deja de pulsar la pantalla
                case MotionEvent.ACTION_UP:
                    spaceShip.setMovementState(spaceShip.STOPPED);
                    break;
            }
            if(spawnTimer == -1) {
                spawnTimer = System.currentTimeMillis();
            }
            return true;
        }
        return true;
    }

    public void saveInfo(View view){
        SharedPreferences sharedPreferences = context.getSharedPreferences("Ranking2", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("Rank 1","aaa-90");
        editor.putString("Rank 2","bbb-50");
        editor.putString("Rank 3","ccc-10");
        editor.apply();
    }

    public void saveInfoR(View view,int score,String name){
        SharedPreferences sharedPreferences = context.getSharedPreferences("Ranking2", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPreferences.edit();


        if (score>=Integer.parseInt(sharedPreferences.getString("Rank 1","0").split("-")[1])){
            editor.putString("Rank 2",sharedPreferences.getString("Rank 1","0"));
            editor.putString("Rank 3",sharedPreferences.getString("Rank 2","0"));
            editor.putString("Rank 1",name+"-"+Integer.toString(score));
            editor.apply();
            //System.out.println('a');
        }else if(score>=Integer.parseInt(sharedPreferences.getString("Rank 2","0").split("-")[1])){
            editor.putString("Rank 3",sharedPreferences.getString("Rank 2","0"));
            editor.putString("Rank 2",name+"-"+Integer.toString(score));
            editor.apply();
            //System.out.println('b');
        }else if (score>=Integer.parseInt(sharedPreferences.getString("Rank 3","0").split("-")[1])){
            editor.putString("Rank 3",name+"-"+Integer.toString(score));
            editor.apply();
            // System.out.println('c');
        }

    }

    public void display(View view){
        SharedPreferences sharedPreferences = context.getSharedPreferences("Ranking2", Context.MODE_PRIVATE);

        System.out.println(sharedPreferences.getString("Rank 1","0"));
        System.out.println(sharedPreferences.getString("Rank 2","0"));
        System.out.println(sharedPreferences.getString("Rank 3","0"));
    }





}
