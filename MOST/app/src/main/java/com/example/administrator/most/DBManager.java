package com.example.administrator.most;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;

// 데이터베이스를 사용하기 위한 매니저 클래스
public class DBManager{
    // SQLite 데이터베이스 객체와 컨텍스트를 저장한다.
    SQLiteDatabase db;
    Context context;
    // DBHelper 생성자로 관리할 DB 이름과 버전 정보를 받음
    public DBManager(Context context) {
        // 컨텍스트를 저장하고 디비를 생성하거나 오픈한다.
        // 앱 내에서만 사용 가능하다.
        this.context = context;
        this.db = context.openOrCreateDatabase("MOSTDB", Activity.MODE_PRIVATE, null);
    }

    // 테이블 생성 함수
    public void create() {
        // 새로운 테이블 생성
        // 테이블은 id, 카테고리(활동/체류), 시작시간, 종료시간, 기간, 정보 로 구성되어 있다.
        // 쿼리를 생성한다.
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS MOST (");
        sql.append("_id INTEGER PRIMARY KEY AUTOINCREMENT,");
        sql.append("category TEXT NOT NULL,");
        sql.append("start TEXT NOT NULL,");
        sql.append("finish TEXT NOT NULL,");
        sql.append("during TEXT NOT NULL,");
        sql.append("information TEXT NOT NULL);");

        // 쿼리를 실행한다.
        db.execSQL(sql.toString());
    }

    // 정보 삽입 함수
    public void insert(String category, String start, String finish, String during, String information) {
        // 쿼리를 생성한다.
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO MOST(category, start, finish, during, information) VALUES(");
        sql.append("'" + category + "',");
        sql.append("'" + start + "',");
        sql.append("'" + finish + "',");
        sql.append("'" + during + "',");
        sql.append("'" + information + "');");
        // 쿼리를 실행한다.
        db.execSQL(sql.toString());
    }

    // 테이블 삭제 함수
    // 테스트용으로 작성되었다.
    public void drop() {
        db.execSQL("DROP TABLE MOST");
    }

    // 저장된 모든 데이터를 보기 좋게 반환하는 함수
    public String getAllResults() {

        StringBuilder result = new StringBuilder();

        // 모든 행을 얻어온다.
        Cursor cursor = db.rawQuery("SELECT * FROM MOST", null);
        // start + " - " + finish + " " + during + "분 활동 " + information +"\n";
        // 위의 포맷에 맞춰서 생성하고 반환한다.
        while (cursor.moveToNext()) {
            result.append(cursor.getString(2) + " - ");
            result.append(cursor.getString(3) + " - ");
            if(cursor.getString(1).equals("활동")) result.append(cursor.getString(4) + "분 활동 ");
            else result.append(cursor.getString(4) + "분 체류 ");
            result.append(cursor.getString(5) + " \n");
        }

        // 스트링빌더를 스트링으로 변환하여 반환
        return result.toString();
    }

    // 카테고리가 활동인 행만 얻는 함수
    public String getMoveResult() {
        // 기간과 정보를 얻어온다.
        // 활동량을 보여주기 위한 정보는 두 정보면 충분하다.
        Cursor cursor = db.rawQuery("SELECT during, information FROM MOST WHERE category = '활동'", null);

        // 결과시간, 결과 걸음수 저장 변수
        int resultTime = 0, resultSteps = 0;

        while (cursor.moveToNext()) {
            // 결과 시간과 스텝수를 얻어와 저장한다.
            resultTime += Integer.parseInt(cursor.getString(0));
            String info = cursor.getString(1);
            resultSteps += Integer.parseInt(info.substring(0, info.length()-3));
        }
        // 결과를 반환한다.
        return resultTime +"-" + resultSteps;
    }

    // 카테고리가 체류인 행만 얻는 함수
    public String getStayResult() {
        StringBuilder result = new StringBuilder();
        // 체류를 위치별로 그룹화 하고
        // 위치별로 체류 기간을 합한다.
        Cursor cursor = db.rawQuery("SELECT sum(cast(during as INTEGER)), information FROM MOST WHERE category = '체류' GROUP BY information", null);
        while (cursor.moveToNext()) {
            // 결과를 저장한다.
            result.append(cursor.getString(1) + "-");
            result.append(cursor.getString(0) + "-");
        }
        // 스트링 빌더를 스트링으로 변환하여 반환
        return result.toString();
    }

    // 디비를 닫는 함수
    public void close() {
        db.close();
    }
}
