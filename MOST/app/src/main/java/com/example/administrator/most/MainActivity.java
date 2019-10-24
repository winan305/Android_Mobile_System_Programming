package com.example.administrator.most;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

/**
 * 2018-1 Mobile System Programming Team Project
 * 2013136110 전두영
 * 2013136106 이창석
 * MOST (MOve, Stay Tracker)
**/

public class MainActivity extends AppCompatActivity {
    final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    boolean isPermitted = false;

    DBManager dbManager;    // 디비매니져
    TextView steptextview;  // 총 걸음 수
    TextView movingtimeview;    // 총 움직임 시간
    TextView classtimeview; // 401호
    TextView dasantimeview; // 다산
    TextView benchtimeview; // 벤치
    TextView groundtimeview; // 운동장

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 퍼미션 얻기
        requestRuntimePermission();

        steptextview = (TextView)findViewById(R.id.textView_step);
        movingtimeview = (TextView)findViewById(R.id.textView_movingtime);
        classtimeview = (TextView)findViewById(R.id.textView_401);
        dasantimeview = (TextView)findViewById(R.id.textView_dasan);
        benchtimeview = (TextView)findViewById(R.id.textView_bench);
        groundtimeview = (TextView)findViewById(R.id.textView_ground);

        dbManager = new DBManager(this);    // 디비 매니져 생성
        dbManager.create();     // 디비 생성
        // 걸음 수와 움직인 시간을 가져옴 : 총 움직인 시간 - 총 걸음 수
        String moveResult = dbManager.getMoveResult();
        // 장소별로 장소 이름과 해당 장소의 체류 시간을 가져옴 : 장소이름 - 체류시간 - 장소이름 - 체류시간
        String stayResult = dbManager.getStayResult();

        // -로 구별해서 배열로 저장
        String[] moveResult_split = moveResult.split("-");
        // textview에 해당 내용 갱신
        movingtimeview.setText("총 움직인 시간 : "+moveResult_split[0] + " 분");
        steptextview.setText("총 걸음 수 : "+moveResult_split[1] + " 걸음");

        // -로 구별해서 배열로 저장
        String[] stayResult_split = stayResult.split("-");
        // 각 장소별로 구별해 해당 장소가 있으면 총 체류시간을 textview에 갱신
        for(int i=0 ; i< stayResult_split.length ; i++){
            if(stayResult_split[i].equals("2공학관 401호")){
                classtimeview.setText("총 체류 시간 : "+stayResult_split[i+1]+ " 분");
            }
            else if(stayResult_split[i].equals("다산 로비")){
                dasantimeview.setText("총 체류 시간 : "+stayResult_split[i+1]+ " 분");
            }
            else if(stayResult_split[i].equals("벤치")){
                benchtimeview.setText("총 체류 시간 : "+stayResult_split[i+1]+ " 분");
            }
            else if(stayResult_split[i].equals("운동장")){
                groundtimeview.setText("총 체류 시간 : "+stayResult_split[i+1]+ " 분");
            }
        }

    }

    // 위치와 쓰기에 대한 권한 얻기
    private void requestRuntimePermission() {
        //*******************************************************************
        // Runtime permission check
        //*******************************************************************
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        } else {
            // ACCESS_FINE_LOCATION 권한이 있는 것
            isPermitted = true;
        }
    }

    // 권한을 받은 후 처리
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // ACCESS_FINE_LOCATION 권한을 얻음
                    isPermitted = true;

                } else {
                    // 권한을 얻지 못 하였으므로 location 요청 작업을 수행할 수 없다
                    // 적절히 대처한다
                    isPermitted = false;
                }
                return;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.actionbar_menus, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_tarcking :
                // 트랙킹 액티비티 실행
                startActivity(new Intent(MainActivity.this, TrackingActivity.class));
                finish();   // 현재 액티비티 종료

                // 서비스 시작
                startService(new Intent(MainActivity.this, MOSTService.class));

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}