package com.example.administrator.most;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TrackingActivity extends AppCompatActivity implements LocationListener  {
    // 각 장소에 대한 플래그 상수 및 단순 실외, 실내 플래그
    static final int INDOOR_ROM_401 = 0, INDOOR_DASAN_LOBBY = 1;
    static final int OUTDOOR_FIELD = 2, OUTDOOR_BENCH = 3;
    static final int INDOOR_UNKWON = 4, OUTDOOR_UNKWON = 5;

    // 실내 실외 판단 시 거리 기준
    static final double STANDARD_DISTANCE = 30.0;

    // 유저의 움직임, 체류 상태플래그, 최초에는 NONE
    static final int STATE_MOVE = 6, STATE_STAY = 7, STATE_NONE = 8;
    // 실외, 실내 장소 플래그
    static final int FLAG_INDOOR = 9, FLAG_OUTDOOR = 10;

    // 유저의 상태표시 변수
    int userState = STATE_NONE;
    // 디비매니저 객체
    DBManager dbManager;

    // 위치리스너 객체와 매니저 객체
    LocationListener listener = this;
    LocationManager mLocationManager;

    // 실내/실외 플래그변수, 위치정보 수신 횟수 변수
    int DOOR_FLAG = -1;
    int getLocationCount = 0;

    // 수신된 데이터 중 가장 큰 위도와 경도
    double maxLatitude = 0, maxLongitude = 0;
    // 수신된 경도 위도의 합
    double sumLatitude = 0, sumLongitude = 0;

    // 지정된 장소 객체
    Place room_401, dasan_lobby, field,  bench;
    // 와이파이 스캔 결과를 담을 리스트 객체 선언
    List<ScanResult> scanResultList;
    // 와이파이 사용을 위한 와이파이매니저 객체 선언
    WifiManager wifiManager;
    // 현재 컨텍스트 저장
    Context context = this;

    // 시간:분으로 시간을 나타내기 위한 포맷
    SimpleDateFormat dateForamt = new SimpleDateFormat("HH:mm");
    // 체류장소를 저장할 변수
    String stayPlace = null;

    // 움직임 시작/종료시간, 체류 시작/종료시간 변수
    long moveStartTime = -1, stayStartTime = -1;
    long moveFinishTime = -1, stayFinishTime = -1;

    // 로그를 표시할 텍스트뷰
    TextView logTextView;
    // 파일 매니져 선언
    TextfileManager textfileManager;
    // 총 걸음 수
    int sumSteps = 0 ;
    // 총 움직임 시간
    int sumMovingTime = 0;
    // 위치명 : 체류시간 을 저장할 해쉬맵
    HashMap<String, Integer> placeTimeHash;

    // 결과 텍스트뷰
    TextView ResultTextView;

    // 일정 주기로 태스크 실행을 위한 타이머와 타이머태스크 객체
    Timer timer = new Timer();
    TimerTask timerTask = null;
    // 위치데이터를 수신했는지 표시할 플래그 변수
    boolean isGetLocationData = false;

    // Wifi scan 결과를 받는 용도로 사용하는 Broadcast Recevier
    BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // 와이파이 스캔 결과를 얻을 수 있을 때
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                // 저장된 상태를 리셋
                reset(FLAG_INDOOR);

                // 유저의 상태가 체류인 경우
                if (userState == STATE_STAY) {
                    // 와이파이 스캔 결과를 얻어서 현재 장소에 대한 해쉬맵 생성
                    scanResultList = wifiManager.getScanResults();
                    HashMap<String, Integer> nowPlaceInfoHash = new HashMap<>();
                    for (int i = 0; i < scanResultList.size(); i++) {
                        ScanResult result = scanResultList.get(i);
                        nowPlaceInfoHash.put(result.BSSID, result.level);
                    }
                    // 현재 장소가 실내 어느장소인지 판단하는 함수 호출, 장소플래그 저장
                    int place = findIndoorPlace(nowPlaceInfoHash);

                    // 장소 플래그에 따라 장소를 저장하고 토스트메시지 출력
                    if (place == INDOOR_ROM_401) {
                        stayPlace = "2공학관 401호";
                        Toast.makeText(context, "2공학관 401호", Toast.LENGTH_SHORT).show();
                    } else if (place == INDOOR_DASAN_LOBBY) {
                        stayPlace = "다산 로비";
                        Toast.makeText(context, "다산 로비", Toast.LENGTH_SHORT).show();
                    } else if (place == INDOOR_UNKWON) {
                        stayPlace = "실내";
                        Toast.makeText(context, "실내", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    // move, stay 브로드캐스트를 받는 리시버
    BroadcastReceiver mostReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // MOVE 브로드캐스트인 경우
            if(action.equals(MOSTService.ACTION_MOVE)) {
                Log.d("TrackingActivity", "MOVE ACTION");
                // 유저 상태가 초기상태라면 움직임 시작시간만 표시
                if(userState == STATE_NONE) moveStartTime = getNowTimeLong();

                // 만약 유저의 상태가 체류였다면
                // 지금까지 장소에서 체류하고 활동을 시작한 것
                // 체류 정보를 기록한다.
                if(userState == STATE_STAY) {
                    // 현재 시각을 얻어온다.
                    // 현재 시간은 체류가 종료된 시간이자 활동이 시작된 시간이다.
                    long nowTime = getNowTimeLong();

                    stayFinishTime = nowTime;
                    moveStartTime = nowTime;
                    // 시작시간, 종료시간, 얼마나 체류했는지 얻어온다.
                    String start = getTimeString(stayStartTime);
                    String finish = getTimeString(stayFinishTime);
                    String during = getDuring(start, finish);

                    // 체류시간이 0분 이하인 경우 저장하지 않는다.
                    if(Integer.parseInt(during) <= 0) return;

                    // 체류정보는 장소가 된다.
                    String information = stayPlace;
                    // log 메시지를 생성한다.
                    String log = start + " - " + finish + " " + during + "분 체류 " + information +"\n";

                    // 현재 장소에 대한 시간정보를 기록하고 움직임 시간, 활동량, TopPlace를 갱신한다.
                    if(placeTimeHash.containsKey(information)) {
                        placeTimeHash.put(information, placeTimeHash.get(information) + Integer.parseInt(during));
                        setTopInfo();
                    }

                    // 데이터베이스에 내용을 저장한다.
                    dbManager.insert("체류", start, finish, during, information);
                    // 로그를 출력하고 파일에 저장한다.
                    logTextView.append(log);
                    textfileManager.save(log);  // 파일 저장

                    // 체류장소는 다시 null이 된다.
                    stayPlace = null;
                }
                // 유저의 상태는 MOVE가 된다.
                userState = STATE_MOVE;
            }

            // STAY 브로드캐스트인 경우
            if(action.equals(MOSTService.ACTION_STAY)) {
                // GPS 프로바이더를 세팅하여 위치정보를 얻어온다.
                setProvider();
                Log.d("TrackingActivity", "STAY ACTION");
                // 만약 상태가 NONE이었다면 체류 시작시간만 체크한다.
                // 체류 시작시간은 브로드캐스트로부터 5분 전이다.
                if(userState == STATE_NONE) stayStartTime = getNowTimeLong() - 300000;

                // 상태가 MOVE였다면 활동을 멈추고 체류를 시작하는 것이다.
                // 활동정보를 기록한다.
                if(userState == STATE_MOVE) {
                    // 현재시간을 얻는다.
                    // 현재 시간은 체류 시작시간이자 활동 종료시간이다.
                    // 체류인 경우 브로드캐스트가 오기까지 5분이 소요되므로 5분을 빼야 맞는 시간이 된다.
                    long nowTime = getNowTimeLong() - 300000;
                    stayStartTime = nowTime;
                    moveFinishTime = nowTime;

                    // 스텝수를 얻어온다.
                    int steps = intent.getIntExtra("steps", 0);
                    if (steps > 0) {
                        // 시작시간, 종료시간, 활동시간을 얻는다.
                        String start = getTimeString(moveStartTime);
                        String finish = getTimeString(moveFinishTime);
                        String during = String.valueOf(getDuring(start, finish));

                        // 활동시간이 0분 이하인 경우 저장하지 않는다.
                        if(Integer.parseInt(during) <= 0) return;

                        // 총 걸음과 총 움직임 시간을 증가시킨다.
                        sumSteps += steps;
                        sumMovingTime += Integer.parseInt(during);

                        // 정보는 활동량이고 '걸음' 을 추가한다.
                        String information = steps + " "+"걸음";
                        // 로그 메시지를 저장한다.
                        String log = start + " - " + finish + " " + during + "분 활동 " + information +"\n";

                        // 디비에 삽입한다.
                        dbManager.insert("활동", start, finish, during, information);
                        // 로그를 텍스트뷰에 출력하고 파일에 저장한다.
                        logTextView.append(log);
                        setTopInfo();
                        textfileManager.save(log);
                    }
                }
                // 유저의 상태는 STAY가 된다.
                userState = STATE_STAY;
            }
        }
    };

    // 활동시간, 걸음수, TopPlace를 갱신하는 함수
    public void setTopInfo() {
        // topPlace는 초기에 None이다.
        String topPlace = "None";
        // TopPlace의 체류시간을 저장할 변수
        int topTime = 0;

        // 체류시간을 저장한 해쉬맵으로부터 키셋을 얻고 반복한다.
        for(String key : placeTimeHash.keySet()) {
            // 해당 장소의 체류시간을 얻고 최대시간과 비교한다.
            // 비교를 통해 topPlace가 어딘지 판단하고, 그 장소의 이름은 key다.
            int time = placeTimeHash.get(key);
            if(topTime < time) {
                topTime = time;
                topPlace = key;
            }
        }
        // 결과 텍스트뷰를 세팅한다.
        ResultTextView.setText("\nMoving time : " + sumMovingTime + "분\nSteps : " + sumSteps + "걸음\nTopPlace : "+ topPlace + "\n" );
    }

    // 시작시간과 종료시간의 차이가 몇분인지 구하는 함수다.
    public String getDuring(String start, String finish) {
        // 시작시간의 시간, 분
        int startHour, startMin;
        // 종료시간의 시간, 분
        int finishHour, finishMin;
        // 그 사이 시간, 분
        int duringHour, duringMin;

        // 시간정보는 시간:분 이므로, :를 기준으로 자른다.
        String[] starts = start.split(":");
        String[] finishs = finish.split(":");

        // 정수형으로 변환한다.
        startHour = Integer.parseInt(starts[0]);
        startMin  = Integer.parseInt(starts[1]);

        finishHour = Integer.parseInt(finishs[0]);
        finishMin  = Integer.parseInt(finishs[1]);

        // 두 시간의 차이를 구하고, 차이시간 * 60 + 차이분 을 스트링으로 반환한다.
        duringHour = finishHour - startHour;
        duringMin  = finishMin - startMin;

        return String.valueOf(duringHour * 60 + duringMin);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        // 뷰 생성
        logTextView = (TextView)findViewById(R.id.TRACKING_TEXTVIEW);
        ResultTextView = (TextView)findViewById(R.id.RESULT_TEXTVIEW);

        // 로케이션 매니저, 와이파이 매니저 객체 생성
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);

        // 와이파이를 사용가능으로 세팅한다.
        if (!wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);

        // 장소 체류시간을 저장할 해쉬맵을 생성한다.
        placeTimeHash = new HashMap<>();
        placeTimeHash.put("벤치", 0);
        placeTimeHash.put("운동장", 0);
        placeTimeHash.put("다산 로비", 0);
        placeTimeHash.put("2공학관 401호", 0);

        // MOVE, STAY 액션을 위한 필터, 리시버를 생성하고 등록한다.
        IntentFilter mostFilter = new IntentFilter();
        mostFilter.addAction(MOSTService.ACTION_MOVE);
        mostFilter.addAction(MOSTService.ACTION_STAY);
        registerReceiver(mostReceiver, mostFilter);

        // 장소정보를 생성한다.
        makePlaces();

        // 디비 매니저를 생성하고 디비를 생성한다.
        dbManager = new DBManager(this);
        dbManager.create();

        // 디비로부터 저장된 로그들을 얻어와 세팅한다.
        String savedLog = dbManager.getAllResults();
        logTextView.setText(savedLog);

        // 저장된 활동 정보를 얻고 "-" 로 자른 뒤 세팅한다.
        String[] moveResult = dbManager.getMoveResult().split("-");
        sumMovingTime = Integer.parseInt(moveResult[0]);
        sumSteps = Integer.parseInt(moveResult[1]);

        // 저장된 체류 정보를 얻고 "-"로 자른다.
        String[] stayResult = dbManager.getStayResult().split("-");

        // 저장된 결과가 없을수도 있기 때문에 길이가 2 이상인 경우 세팅한다.
        if(stayResult.length >= 2) {
            // 결과로부터 장소 및 시간을 얻고 체류시간 해쉬맵에 삽입한다.
            for (int i = 0; i < stayResult.length; i += 2) {
                String place = stayResult[i];
                int time = Integer.parseInt(stayResult[i + 1]);

                if (placeTimeHash.containsKey(place)) {
                    placeTimeHash.put(place, placeTimeHash.get(place) + time);
                }
            }
        }
        // 해쉬맵을 저장했으므로 함수를 호출하여 TopInfo를 세팅한다.
        setTopInfo();

        // 파일 매니져 클래스 생성
        textfileManager = new TextfileManager();

        // 화면이 꺼지지 않게 플래그를 세팅한다.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        // 액티비티 종료 시 리시버를 해제한다.
        unregisterMostReceiver();
        // 서비스를 종료한다.
        stopService(new Intent(this, MOSTService.class));
        // 디비를 닫는다.
        dbManager.close();
        Log.d("TrackingActivity", "onDestroy!");
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        Log.d("TrackingActivity", "onStop!");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d("TrackingActivity", "onPause");
        super.onPause();
    }

    // 호출 시 와이파이 스캔 리시버를 해제한다.
    private void unregisterScanReceiver() {
        if(scanReceiver != null) {
            try {
                unregisterReceiver(scanReceiver);
            }
            catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    // 호출 시 STAY, MOVE 리시버를 해제한다.
    private void unregisterMostReceiver() {
        if(mostReceiver != null) {
            unregisterReceiver(mostReceiver);
            mostReceiver = null;
        }
    }

    // 지정된 장소를 생성하는 함수
    public void makePlaces() {
        // 실내는 와이파이 해쉬맵을 세팅하고 장소를 생성
        // 실외는 위도,경도값을 세팅하고 장소를 생성

        // 401호 생성
        HashMap<String, Integer> indoorInfoHash_401 = new HashMap<>();
        indoorInfoHash_401.put("64:e5:99:db:05:cc",-49);
        indoorInfoHash_401.put("40:01:7a:de:11:3f",-79);
        indoorInfoHash_401.put("40:01:7a:de:11:3e",-79);
        indoorInfoHash_401.put("00:08:9f:52:b0:e4",-64);
        indoorInfoHash_401.put("40:01:7a:de:11:30",-59);
        indoorInfoHash_401.put("40:01:7a:de:11:31",-58);
        indoorInfoHash_401.put("40:01:7a:de:11:3d",-79);
        indoorInfoHash_401.put("64:e5:99:db:05:c8",-51);
        indoorInfoHash_401.put("18:80:90:c6:7b:20",-46);
        indoorInfoHash_401.put("18:80:90:c6:7b:22",-46);
        indoorInfoHash_401.put("18:80:90:c6:7b:21",-46);
        indoorInfoHash_401.put("18:80:90:c6:7b:2f",-61);
        indoorInfoHash_401.put("18:80:90:c6:7b:2e",-61);
        indoorInfoHash_401.put("20:3a:07:48:58:d5",-70);
        indoorInfoHash_401.put("20:3a:07:48:58:de",-85);
        indoorInfoHash_401.put("20:3a:07:48:58:df",-85);
        indoorInfoHash_401.put("88:36:6c:6a:95:b2",-68);
        indoorInfoHash_401.put("88:36:6c:6a:96:f8",-80);
        indoorInfoHash_401.put("64:e5:99:2c:ef:36",-62);
        room_401 = Place.builder()
                .name("2공학관 401")
                .flag(Place.FLAG_INDOOR)
                .indoorInfoHash(indoorInfoHash_401)
                .build();
        room_401.setContext(context);

        // 다산로비 생성
        HashMap<String, Integer> indoorInfoHash_lobby = new HashMap<>();
        indoorInfoHash_lobby.put("20:3a:07:49:5c:ef", -66);
        indoorInfoHash_lobby.put("20:3a:07:49:5c:ee", -66);
        indoorInfoHash_lobby.put("a4:18:75:58:77:da", -70);
        indoorInfoHash_lobby.put("20:3a:07:9e:a6:c5", -55);
        indoorInfoHash_lobby.put("20:3a:07:9e:a6:ca", -59);
        indoorInfoHash_lobby.put("20:3a:07:9e:a6:cf", -59);
        indoorInfoHash_lobby.put("20:3a:07:9e:a6:ce", -59);
        indoorInfoHash_lobby.put("34:a8:4e:6a:44:6f", -72);
        indoorInfoHash_lobby.put("34:a8:4e:6a:44:6e", -72);
        indoorInfoHash_lobby.put("34:a8:4e:6a:44:6a", -72);
        indoorInfoHash_lobby.put("a4:18:75:58:77:d5", -59);
        indoorInfoHash_lobby.put("a4:18:75:58:77:df", -71);
        indoorInfoHash_lobby.put("64:d9:89:46:4b:ef", -83);
        indoorInfoHash_lobby.put("64:d9:89:46:4b:ee", -81);
        indoorInfoHash_lobby.put("34:a8:4e:6a:44:65", -70);
        indoorInfoHash_lobby.put("88:75:56:c7:1f:1e", -78);
        dasan_lobby = Place.builder()
                .name("다산정보관 1층 로비")
                .flag(Place.FLAG_INDOOR)
                .indoorInfoHash(indoorInfoHash_lobby)
                .build();
        dasan_lobby.setContext(context);

        // 운동장 생성
        field = Place.builder()
                .name("운동장")
                .latitude(36.762581)
                .longitude(127.284527)
                .flag(Place.FLAG_OUTDOOR)
                .radius(80)
                .build();
        field.setContext(context);

        // 벤치 생성
        bench = Place.builder()
                .name("대학본부 앞 잔디광장 벤치")
                .latitude(36.764215)
                .longitude(127.282173)
                .flag(Place.FLAG_OUTDOOR)
                .radius(50)
                .build();
        bench.setContext(context);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menus_tracking, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // 액션바에 있는 스톱버튼을 누르면 메인액티비티가 실행되고 액티비티가 종료된다.
            // onDestroy에서 서비스를 멈춘다.
            case R.id.action_stop :
                Toast.makeText(this, "Tracking Stop!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(TrackingActivity.this, MainActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // GPS 프로바이더를 세팅하고 타이머를 실행한다.
    public void setProvider() {
        try {
            startTimerTask();
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    // 호출 시 실내인 경우 와이파이 스캔 리시버를 해제하고 다른 변수들을 초기화한다.
    public void reset(int flag) {
        DOOR_FLAG = -1;
        getLocationCount = 0;
        maxLatitude = 0;
        maxLongitude = 0;
        sumLatitude = 0;
        sumLongitude = 0;

        mLocationManager.removeUpdates(this);
        if(flag == FLAG_INDOOR) unregisterScanReceiver();
    }

    private void startTimerTask() {
        // TimerTask 생성한다
        // 타이머 생성 시 위치정보를 받은 플래그는 false 이다.
        // 10초뒤에 확인하게 된다.
        isGetLocationData = false;
        timerTask = new TimerTask() {
            @Override
            public void run() {
                // 10초 뒤 확인한 플래그가 false이면 GPS로부터 정보를 받을 수 없는 실외라고 판단한다.
                if(!isGetLocationData) {
                    // 위치정보 리스너를 해제한다.
                    mLocationManager.removeUpdates(listener);
                    // 와이파이 스캔 결과를 얻기 위해 필터를 생성하고 리비서를 등록한다.
                    IntentFilter scanFilter = new IntentFilter();
                    scanFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                    registerReceiver(scanReceiver, scanFilter);
                    // 와이파이 스캔을 시작한다.
                    wifiManager.startScan();
                    // 타이머를 멈춘다.
                    stopTimerTask();
                }
            }
        };

        // TimerTask를 Timer를 통해 실행시킨다
        // 5초 후에 타이머를 구동한다.
        // 호출 시 run에서 멈추기 때문에 반복은 되지 않는다.
        timer.schedule(timerTask, 5000, 10000);
    }

    private void stopTimerTask() {
        // 1. 모든 태스크를 중단한다
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    // 위치정보를 얻어올 때 호출되는 콜백함수
    public void onLocationChanged(Location location) {
        // 위치정보를 받았는데 플래그가 false이면 true로 바꾸고 타이머를 멈춘다.
        if(!isGetLocationData) {
            isGetLocationData = true;
            stopTimerTask();
        }

        // 10회의 데이터를 수집한다.
        if(getLocationCount < 10) {
            // 최대경도, 위도를 저장하고 위도 경도의 총합을 얻는다.
            maxLatitude = Math.max(maxLatitude, location.getLatitude());
            maxLongitude = Math.max(maxLatitude, location.getLongitude());
            sumLatitude += location.getLatitude();
            sumLongitude += location.getLongitude();

            // 카운트를 늘리고 함수를 종료한다.
            getLocationCount++;
            return;
        }

        // 10회의 데이터 수집이 끝난 경우
        else {
            // 최대 위도,경도를 가진 위치를 생성한다.
            Location maxLocation = new Location("max");
            maxLocation.setLatitude(maxLatitude);
            maxLocation.setLongitude(maxLongitude);

            // 총합들을 이용해 평균적인 위도, 경도 위치를 생성한다.
            Location minLocation = new Location("avg");
            minLocation.setLatitude(sumLatitude/getLocationCount);
            minLocation.setLongitude(sumLongitude/getLocationCount);

            // 두 위치의 거리를 얻는다.
            float distance = maxLocation.distanceTo(minLocation);

            // 만약 거리가 기준 거리보다 멀다면, GPS의 오차가 큰 실내다.
            if(distance >= STANDARD_DISTANCE) DOOR_FLAG = FLAG_INDOOR;

            // 그렇지 않다면 실외에서 GPS로 받는 경우이다.
            else DOOR_FLAG = FLAG_OUTDOOR;

            // 리스너를 해제한다.
            mLocationManager.removeUpdates(this);
        }

        // 위에서부터 얻은 플래그가 실외인 경우
        if(DOOR_FLAG == FLAG_OUTDOOR) {
            // 저장된 실외인지 임의의 실외인지 구하는 함수를 호출한다.
            int place = findOutdoorPlace(location.getLatitude(), location.getLongitude());
            // 실외 플래그를 전달하여 상태를 초기화한다.
            reset(FLAG_OUTDOOR);

            // 결정된 장소를 저장하고 토스트 메시지로 알린다.
            if (place == OUTDOOR_FIELD) {
                stayPlace = "운동장";
                Toast.makeText(this, "운동장", Toast.LENGTH_SHORT).show();
            } else if (place == OUTDOOR_BENCH) {
                stayPlace = "벤치";
                Toast.makeText(this, "벤치", Toast.LENGTH_SHORT).show();
            } else if (place == OUTDOOR_UNKWON) {
                stayPlace = "실외";
                Toast.makeText(this, "실외", Toast.LENGTH_SHORT).show();
            }
        }

        // 위에서부터 얻은 플래그가 실내인 경우
        else if(DOOR_FLAG == FLAG_INDOOR) {
            // 와이파이 스캔을 위해 필터를 생성하고 리시버를 등록한다.
            IntentFilter scanFilter = new IntentFilter();
            scanFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(scanReceiver, scanFilter);
            // 와이파이 스캔을 시작한다.
            wifiManager.startScan();
        }
    }

    // 해쉬맵을 전달받아 저장된 실내위치인지 판단하는 함수를 호출하고
    // 장소 플래그를 반환하는 함수다.
    public int findIndoorPlace(HashMap<String, Integer> nowPlaceInfoHash) {
        // 401호와 다산로비에 대하여 실시한다.
        if(room_401.isEqualndoorPlace(nowPlaceInfoHash)) return INDOOR_ROM_401;
        if(dasan_lobby.isEqualndoorPlace(nowPlaceInfoHash)) return INDOOR_DASAN_LOBBY;
        // 위에서 반환하지 못하면 임의의 실내를 반환한다.
        return INDOOR_UNKWON;
    }

    // 위도와 경도를 전달받아 저장된 실외위치인지 판단하는 함수를 호출하고
    // 장소 플래그를 반환하는 함수다.
    public int findOutdoorPlace(double latitude, double longitude) {
        // 운동장과 벤치에 대하여 실시한다.
        if(field.isEqualOutdoorPlace(latitude, longitude)) return OUTDOOR_FIELD;
        if(bench.isEqualOutdoorPlace(latitude, longitude)) return OUTDOOR_BENCH;
        // 위에서 반환하지 못하면 임의의 실외를 반환한다.
        return OUTDOOR_UNKWON;
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {

    }
    public void onProviderEnabled(String provider) {

    }
    public void onProviderDisabled(String provider) {
    }


    // 현재 시각을 long 타입으로 반환하는 함수다.
    public long getNowTimeLong() {
        return Calendar.getInstance().getTimeInMillis();
    }

    // long형의 시간정보를 받아 정해진 포맷으로 스트링으로 반환하는 함수다.
    public String getTimeString(long time) {
        // 시간값으로 날짜객체를 생성하고 포맷에 맞춰 반환한다.
        Date date = new Date(time);
        return dateForamt.format(date);
    }
}
