package com.example.administrator.most;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * 장소에 대한 데이터를 담는 클래스
 * 장소는 실내냐 실외에 대한 플래그
 * 장소 이름
 * 실외인 경우 위도 경도값
 * 실내인 경우 와이파이 스캔 결과 해쉬맵(BSSID : RSSI), 스캔 결과는 지배적인 와이파이들만 저장
 * 스캔결과 저장된 해쉬맵의 키셋(BSSID 리스트), 나중에 요소 참조시에 사용
 * 실내 장소 판별 시 기준비율, 두 와이파이 비교 시 기준차이 값
 * PlaceBuilder 를 통해 디폴트 매개변수 사용
 **/

public class Place {
    static final int FLAG_INDOOR = 0, FLAG_OUTDOOR = 1;
    String name; // 장소의 이름
    int flag;
    double latitude, longitude;
    Location savedLocation;
    int radius;
    HashMap<String, Integer> indoorInfoHash; // 와이파이 정보를 담는 해쉬맵 <키:값> = <BSSID, RSSI>
    Set<String> keySet; // 해쉬맵의 키값을 담는 셋. 해쉬맵에서 와이파이 정보를 찾기 위함이다.
    final double RATIO_STANDARD = 0.7; // 장소 판별 시 기준이 되는 비율
    final int DIFF_STANDARD = 15; // RSSI 값의 차가 +- 15까지만 허용
    Context context;
    // 객체 생성자, 장소 이름과 와이파이 정보를 담는 해쉬맵을 받는다.
    private Place(String name, int flag, double latitude, double longitude, int radius, HashMap<String, Integer> indoorInfoHash) {
        this.name = name; // 이름 저장
        this.flag = flag;

        // 실내인 경우 해쉬맵 저장 및 키셋을 얻는다.
        if(flag == FLAG_INDOOR) {
            this.indoorInfoHash = indoorInfoHash;
            if(indoorInfoHash != null)
                keySet = indoorInfoHash.keySet();
        }

        // 실외인 경우 위도,경도,반경을 저장하고
        // 위도 경도를 가진 위치를 미리 생성해둔다.
        else if(flag == FLAG_OUTDOOR) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.radius = radius;

            this.savedLocation = new Location(name);
            savedLocation.setLatitude(latitude);
            savedLocation.setLongitude(longitude);
        }
    }

    public void setContext(Context context) { this.context = context;}
    // 실내장소 판별 함수
    // 임의의 장소의 와이파이 정보를 담은 해쉬셋으로 부터
    // 등록된 장소와 같은 곳인지 비교하는 함수
    public boolean isEqualndoorPlace(HashMap<String, Integer> compareHash) {
        // 같은 장소에서의 와이파이라고 판단되는 와이파이의 개수
        int equlasCount = 0;
        String difList = "";
        // 현재 장소에 저장된 해쉬맵의 모든 키셋(BSSID)으로 부터 반복한다.
        for(Iterator i = keySet.iterator(); i.hasNext();) {
            String key = i.next().toString();
            // 비교하려는 해쉬맵에 해당 키가 존재하는지 확인한다.
            // 존재할 경우 두 rssi 값을 비교하고 동일하다면 개수를 늘린다.
            if(compareHash.containsKey(key)) {
                int rssi1 = indoorInfoHash.get(key);
                int rssi2 = compareHash.get(key);
                difList = difList + rssi1 + "/" + rssi2 + "\n";
                if(isEqualIndoorWifi(rssi1, rssi2)) equlasCount++;
            }
        }
        Log.d("Indoor Place Judge :", difList + "/" + (double)equlasCount / indoorInfoHash.size());
        // 동일하다고 판단된 개수와 최대 개수를 비교하고
        // 기준이 되는 비율보다 크거나 같다면 같은 장소로 판단한다.
        return (double)equlasCount / indoorInfoHash.size() >= RATIO_STANDARD;
    }
    // 두 개의 rssi 값으로 부터 같은 실내장소에서의 와이파이인지 확인하는 함수
    private boolean isEqualIndoorWifi(int rssi1, int rssi2) {
        int diff = Math.abs(rssi1 - rssi2);
        return diff <= DIFF_STANDARD;
    }

    // 실외장소 판별 함수
    public boolean isEqualOutdoorPlace(double compareLatitude, double compareLongitude) {
        /*
        비교하려는 장소의 위도 경도를 받아옴
        해당 장소의 Location 객체 생성
        distanceTo 함수를 통해 비교하려는 장소와 현재장소 거리 측정
        저장된 반경 이하라면 해당 장소에 있는 것.
         */

        // 비교장소 생성
        Location compareLocation = new Location("compareLocation");
        compareLocation.setLatitude(compareLatitude);
        compareLocation.setLongitude(compareLongitude);
        // 비교 후 결과값 반환
        return compareLocation.distanceTo(savedLocation) <= this.radius;
    }

    // 빌더 객체를 반환하는 빌더 함수
    public static PlaceBuilder builder() {
        return new PlaceBuilder();
    }

    // Place 객체를 생성할 빌더 클래스
    public static class PlaceBuilder {
        String name = ""; // 장소의 이름
        int flag = -1; // 장소 플래그
        double latitude = 0, longitude = 0; // 위도, 경도
        int radius = 0; // 반경
        HashMap<String, Integer> indoorInfoHash = null; // 와이파이 정보를 담는 해쉬맵 <키:값> = <RSSID, RSSI>

        // 각각의 변수를 함수를 통해 값을 저장하고 반환한다.
        public PlaceBuilder name(String newNeam) {
            this.name = newNeam;
            return this;
        }

        public PlaceBuilder flag(int newFlag) {
            this.flag = newFlag;
            return this;
        }

        public PlaceBuilder latitude(double newlatitude) {
            this.latitude = newlatitude;
            return this;
        }

        public PlaceBuilder longitude(double newlongitude) {
            this.longitude = newlongitude;
            return this;
        }

        public PlaceBuilder radius(int newRadius) {
            this.radius = newRadius;
            return this;
        }

        public PlaceBuilder indoorInfoHash(HashMap<String, Integer> indoorInfoHash) {
            this.indoorInfoHash = indoorInfoHash;
            return this;
        }

        // 함수 호출 시 Place 객체를 저장된 값들을 전달하여 생성하고 반환한다.
        public Place build() {
            return new Place(name, flag, latitude, longitude, radius, indoorInfoHash);
        }
    }
}