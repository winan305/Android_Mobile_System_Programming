package com.example.administrator.most;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import static android.hardware.SensorManager.SENSOR_DELAY_FASTEST;

public class MOSTService extends Service implements SensorEventListener {
    private static final String LOGTAG = "ADC_Step_Monitor";
    static final String ACTION_MOVE = "ACTION_MOVE";
    static final String ACTION_STAY = "ACTION_STAY";

    AlarmManager am;        // 알람 메니저 선언
    PendingIntent pendingIntent;    // 시스템에게 보낼 팬딩인텐트 선언
    SensorEventListener listener = this;
    private PowerManager.WakeLock wakeLock;
    private CountDownTimer timer;

    private long period = 10000;    // 알람 주기 - 첫 시작 10초
    private static final long activeTime = 3000;    // 첫 알람 시작
    private static final long periodForMoving = 5000;   // 움직일 때 5초
    private static final long periodIncrement = 5000;   // 안움직임면 5초 증가
    private static final long periodMax = 30000;        // 안움직일 때 최대 30초까지만 증가

    long timestack_move = 0;    // 움직일 때 타임 스택
    long timestack_stay = 0;    // 멈춰 있을 때 타임 스택
    boolean timestack_move_idx = true;  // 움직임에 대한 브로드케스트를 보냈는지 판단
    boolean timestack_stay_idx = true;  // 체류에 대한 브로드케스트를 보냈는지 판단

    // 0 : 값이 아무것도 없을 때(처음 들어오는 값을 위한거), 1 : 움직였을 때, 2 : 정지해 있을 때
    // 3개의 값을 비교해서 움직임과 체류를 판단한다.
    int moving_idx_mostprev = 0;
    int moving_idx_prev = 0;
    int moving_idx_currt = 0;

    int steps;  // 걸음 수
    int STEP_PER_FIVESEC = 7;   // 5초에 7걸음

    private SensorManager mSensorManager;
    private Sensor mLinear;
    // 움직임 여부를 나타내는 bool 변수: true이면 움직임, false이면 안 움직임
    private boolean isMoving;
    // onStart() 호출 이후 onStop() 호출될 때까지 센서 데이터 업데이트 횟수를 저장하는 변수
    private int sensingCount;
    // 센서 데이터 업데이트 중 움직임으로 판단된 횟수를 저장하는 변수
    private int movementCount;
    // 움직임 여부를 판단하기 위한 3축 가속도 데이터의 RMS 값의 기준 문턱값
    private static final double RMS_THRESHOLD = 1.0;

    // Alarm 시간이 되었을 때 안드로이드 시스템이 전송해주는 broadcast를 받을 receiver 정의
    // 움직임 여부에 따라 기준에 맞게 다음 alarm이 발생하도록 설정한다.
    private BroadcastReceiver AlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("kr.ac.koreatech.msp.adcalarm")) {
                Log.d(LOGTAG, "Alarm fired!!!!");
                //-----------------
                // Alarm receiver에서는 장시간에 걸친 연산을 수행하지 않도록 한다
                // Alarm을 발생할 때 안드로이드 시스템에서 wakelock을 잡기 때문에 CPU를 사용할 수 있지만
                // 그 시간은 제한적이기 때문에 애플리케이션에서 필요하면 wakelock을 잡아서 연산을 수행해야 함
                //-----------------

                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AdaptiveDutyCyclingStepMonitor_Wakelock");
                // ACQUIRE a wakelock here to collect and process accelerometer data
                wakeLock.acquire();
                startSensing();
                timer = new CountDownTimer(activeTime, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                    @Override
                    public void onFinish() {
                        Log.d(LOGTAG, "1-second accel data collected!!");
                        // stop the accel data update
                        stopSensing();
                        // 움직임 여부
                        boolean moving = isMoving();
                        // 움직임과 체류에 대한 판단
                        // 움직였을 때
                        if(moving){
                            steps += STEP_PER_FIVESEC;  // 움직였으므로 걸음 수 증가
                            moving_idx_currt = 1;
                            movingdivision(moving); // 움직였을 때 판단
                        }
                        // 정지했을 때
                        else{
                            moving_idx_currt = 2;
                            stopdivision(moving);   // 정지했을 때 판단
                        }

                        // 움직임 여부에 따라 다음 alarm 주기 설정
                        setNextAlarm(moving);

                        // When you finish your job, RELEASE the wakelock
                        wakeLock.release();
                        wakeLock = null;
                    }
                };
                timer.start();
            }
        }
    };

    // 움직였을 때 현재 값과 이전과 이전이전값을 비교해 사용자가 움직이는 상태인지 정지해있는 상태인지 판단
    // 움직이는 시간과 정지 시간을 계산
    private void movingdivision(boolean moving){
        // 첫 번째 혹은 두 번째로 들어와 앞의 값이 없을 때
        if(moving_idx_prev == 0){
            // 첫 번째로 들어와 아무 값이 없을 때
            if(moving_idx_mostprev == 0){
                timestack_move += period;   // 움직이는 시간 축적
                moving_idx_mostprev = moving_idx_currt;
            }
            // 두 번째로 들어왔을 때
            else if(moving_idx_mostprev != 0){
                moving_idx_prev = moving_idx_currt; // 들어온 값을 이전으로 이동
                // 이전에도 움직였고 지금도 움직이고 있을 때
                if( moving_idx_mostprev == moving_idx_prev){
                    timestack_move += period;   // 움직이는 시간 축적
                }
                // 이전에는 정지해 있었지만 지금은 움직이고 있을 때
                else if( moving_idx_mostprev != moving_idx_prev){
                    // 계속 움직일지 계속 정지할지 모르기 때문에 둘다 시간을 축적
                    timestack_move += period;
                    timestack_stay += period;
                }
            }
        }
        // 앞에 값들이 있을 때
        else if(moving_idx_prev != 0){
            // 전에도 움직이고 지금도 움직이면 움직이고 있는거라 판단 하고 정지시간을 초기화, 움직이는 시간 축적
            if(moving_idx_prev == moving_idx_currt){
                timestack_move += period;
                timestack_stay = 0;

                // 앞으로 이동
                moving_idx_mostprev = moving_idx_prev;
                moving_idx_prev = moving_idx_currt;
            }
            // 전에는 정지했지만 지금은 움직이고 있으면
            else if( moving_idx_prev != moving_idx_currt){
                // 움직임 - 정지 - 움직임이면 잠깐 정지한 것으로 판단해서 정지시간을 초기화, 움직이는 시간 축적
                if(moving_idx_mostprev == moving_idx_currt){
                    timestack_move += period;
                    timestack_stay = 0;

                    // 앞으로 이동
                    moving_idx_mostprev = moving_idx_prev;
                    moving_idx_prev = moving_idx_currt;
                }
                // 정지 - 정지 - 움직임이면 뒤에 것에 따라 판단 되기 때문에 둘다 시간을 축적적
                else if( moving_idx_mostprev != moving_idx_currt){

                    timestack_move += period;
                    timestack_stay += period;

                    // 앞으로 이동
                    moving_idx_mostprev = moving_idx_prev;
                    moving_idx_prev = moving_idx_currt;

                }
            }
        }

        if( timestack_move_idx){
            // 움직이는 시간이 1분 이상이면 브로드케스트 날림
            if( timestack_move >= 60000 ){
                sendDataToActivity(moving);
                timestack_move_idx = false;
                timestack_stay_idx = true;
            }
        }
    }

    // 정지했을 때 현재 값과 이전과 이전이전값을 비교해 사용자가 움직이는 상태인지 정지해있는 상태인지 판단
    // 움직이는 시간과 정지 시간을 계산
    private void stopdivision(boolean moving){
        // 첫 번째 혹은 두 번째로 들어와 앞의 값이 없을 때
        if(moving_idx_prev == 0){
            // 첫 번째로 들어와 아무 값이 없을 때
            if(moving_idx_mostprev == 0){
                moving_idx_mostprev = moving_idx_currt;
                timestack_stay += period;
            }
            // 두 번째로 들어왔을 때
            else if(moving_idx_mostprev != 0){
                moving_idx_prev = moving_idx_currt; // 들어온 값을 이전으로 이동
                // 이전에도 정지해있고 지금도 정지해 있을 때
                if( moving_idx_mostprev == moving_idx_prev){
                    timestack_stay += period;
                }
                // 이전에는 움직였지만 지금은 정지해 있을 때
                else if( moving_idx_mostprev != moving_idx_prev){
                    timestack_move += period;
                    timestack_stay += period;
                }
            }
        }
        else if(moving_idx_prev != 0){
            // 전에도 정지했고 지금도 정지해있으면 정지하고 있는거라 판단 하고 정지시간을 축적, 움직이는 시간 초기화
            if(moving_idx_prev == moving_idx_currt){
                timestack_move += 0;
                timestack_stay += period;

                // 앞으로 이동
                moving_idx_mostprev = moving_idx_prev;
                moving_idx_prev = moving_idx_currt;
            }
            // 전에는 움직였지만 지금은 정지해 있으면
            else if( moving_idx_prev != moving_idx_currt){
                // 정지 - 움직임 - 정지면 잠깐 움직인것으로 판단해서 정지시간을 축적, 움직이는 시간 초기화
                if(moving_idx_mostprev == moving_idx_currt){
                    timestack_move += 0;
                    timestack_stay += period;

                    // 앞으로 이동
                    moving_idx_mostprev = moving_idx_prev;
                    moving_idx_prev = moving_idx_currt;
                }
                // 움직임 - 움직임 - 정지면 뒤에 것에 따라 판단 되기 때문에 둘다 시간을 축적적
                else if( moving_idx_mostprev != moving_idx_currt){

                    timestack_move += period;
                    timestack_stay += period;

                    steps += STEP_PER_FIVESEC;  // 다시 움직임이 되면 움직임으로 판별하니까 걸음 수 축적

                    // 앞으로 이동
                    moving_idx_mostprev = moving_idx_prev;
                    moving_idx_prev = moving_idx_currt;

                }
            }
        }

        if( timestack_stay_idx ){
            // 멈춰있는 시간이 5분 이상이면 브로드케스트 날림
            if( timestack_stay >= 60000*5 ){
                sendDataToActivity(moving);
                timestack_stay_idx = false;
                timestack_move_idx = true;
            }
        }

    }

    // 다음 알람 설정
    private void setNextAlarm(boolean moving) {

        // 움직임이면 5초 period로 등록
        // 움직임이 아니면 5초 증가, max 30초로 제한

        if(moving){
            period = periodForMoving;
        }
        else{
            period = period + periodIncrement;
            if(period >= periodMax) {
                period = periodMax;
            }
        }

        Log.d(LOGTAG, "Next alarm: " + period);

        // 다음 alarm 등록
        Intent in = new Intent("kr.ac.koreatech.msp.adcalarm");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);
        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period - activeTime, pendingIntent);
    }

    // 움직임과 체류에 대한 해당 값을 브로드케스트로 전송하는 함수
    private void sendDataToActivity(boolean moving) {
        // 화면에 정보 표시를 위해 activity의 broadcast receiver가 받을 수 있도록 broadcast 전송
        Intent intent;
        if(moving) intent = new Intent(ACTION_MOVE);
        else intent = new Intent(ACTION_STAY);

        //intent.putExtra("moving", moving);
        if(!moving && steps > 0){
            intent.putExtra("steps", steps);
            steps = 0;
        }
        // broadcast 전송
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        Log.d(LOGTAG, "onCreate");

        // Alarm 발생 시 전송되는 broadcast를 수신할 receiver 등록
        IntentFilter intentFilter = new IntentFilter("kr.ac.koreatech.msp.adcalarm");
        registerReceiver(AlarmReceiver, intentFilter);

        // AlarmManager 객체 얻기
        am = (AlarmManager)getSystemService(ALARM_SERVICE);
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mLinear = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intent: startService() 호출 시 넘기는 intent 객체
        // flags: service start 요청에 대한 부가 정보. 0, START_FLAG_REDELIVERY, START_FLAG_RETRY
        // startId: start 요청을 나타내는 unique integer id

        Log.d(LOGTAG, "onStartCommand");
        //Toast.makeText(this, "Activity Monitor 시작", Toast.LENGTH_SHORT).show();

        // Alarm이 발생할 시간이 되었을 때, 안드로이드 시스템에 전송을 요청할 broadcast를 지정
        Intent in = new Intent("kr.ac.koreatech.msp.adcalarm");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);

        // Alarm이 발생할 시간 및 alarm 발생시 이용할 pending intent 설정
        // 설정한 시간 (period=10000 (10초)) 후 alarm 발생
        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period, pendingIntent);

        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {

        try {
            // Alarm 발생 시 전송되는 broadcast 수신 receiver를 해제
            unregisterReceiver(AlarmReceiver);
        } catch(IllegalArgumentException ex) {
            ex.printStackTrace();
        }
        // AlarmManager에 등록한 alarm 취소
        am.cancel(pendingIntent);

        // release all the resources you use
        if(timer != null)
            timer.cancel();
        if(wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    public void startSensing() {
        // SensorEventListener 등록
        if (mLinear != null) {
            Log.d(LOGTAG, "Register Accel Listener!");
            mSensorManager.registerListener(listener, mLinear, SENSOR_DELAY_FASTEST);
        }
        // 변수 초기화
        isMoving = false;
        sensingCount = 0;
        movementCount = 0;
    }

    public void stopSensing() {
        // SensorEventListener 등록 해제
        if (mSensorManager != null) {
            Log.d(LOGTAG, "Unregister Accel Listener!");
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // 센서 데이터가 업데이트 되면 호출
    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d(LOGTAG, "StepMonitor: onSensorChanged called");
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            sensingCount++;
            //***** sensor data collection *****//
            // event.values 배열의 사본을 만들어서 values 배열에 저장
            float[] values = event.values.clone();

            detectMovement(values);
        }
    }

    // 일정 시간 동안 움직임 판단 횟수가 센서 업데이트 횟수의 50%를 넘으면 움직임으로 판단
    public boolean isMoving() {
        if(sensingCount == 0) {
            isMoving = false;
            return isMoving;
        }
        double ratio = (double)movementCount / (double)sensingCount;
        if(ratio >= 0.5) {
            isMoving = true;
        } else {
            isMoving = false;
        }
        return isMoving;
    }

    private void detectMovement(float[] values) {
        // 현재 업데이트 된 accelerometer x, y, z 축 값의 Root Mean Square 값 계산
        double rms = Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
        Log.d(LOGTAG, "rms: " + rms);

        // 계산한 rms 값을 threshold 값과 비교하여 움직임이면 count 변수 증가
        if(rms > RMS_THRESHOLD) {
            movementCount++;
        }
    }
}