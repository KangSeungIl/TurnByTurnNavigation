package lbsproject.turnbyturnnavi;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Created by kangsi on 2017. 2. 10..
 */

public class PlacesAutoComplete {
    private static final String LOG_TAG = "Google Places Autocomplete";
    private static final String PLACES_API_BASE = "https://maps.googleapis.com/maps/api/place";
    private static final String TYPE_AUTOCOMPLETE = "/autocomplete";
    private static final String OUT_JSON = "/json";
    private static final String API_KEY = "AIzaSyA9HtscpiGDrU3l-5eePCpeSi2Gz2I2VYg";

    @SuppressLint("LongLogTag")
    public static ArrayList autocomplete(String input) {
        ArrayList resultList = null;
        HttpURLConnection conn = null; // HttpURLConnection 객체 생성
        StringBuilder jsonResults = new StringBuilder();
        try {
            // https://maps.googleapis.com/maps/api/place/autocomplete/json?input=input&types=establishment&key=API_KEY
            StringBuilder sb = new StringBuilder(PLACES_API_BASE + TYPE_AUTOCOMPLETE + OUT_JSON); // url 초기 주소
            sb.append("?input=" + URLEncoder.encode(input, "utf8")); // input
            sb.append("&type=establishment"); // 저동완성 서비스에서 사업체 결과만 반환
            sb.append("&language=kr"); // 한국어로 결과 요청
            sb.append("&key=" + API_KEY); // API키
            Log.v("URL", sb.toString());

            URL url = new URL(sb.toString()); // URL 값 대입
            conn = (HttpURLConnection) url.openConnection(); // URL 연결, GET 방식이용
            Log.v("URL", "Connect");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream())); // 버퍼로 스트림 읽어들임

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read); // 요청받은 결과를 jsonResults에 저장
            }
            Log.v("Result", jsonResults.toString());

        } catch (MalformedURLException e) {
            Log.e(LOG_TAG, "Error processing Places API URL", e);
            return resultList;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error connecting to Places API", e);
            return resultList;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            // Create a JSON object hierarchy from the results
            JSONObject jsonObj = new JSONObject(jsonResults.toString()); // jsonObj로 결과값 json저장
            JSONArray predsJsonArray = jsonObj.getJSONArray("predictions");

            // Extract the Place descriptions from the results
            resultList = new ArrayList(predsJsonArray.length());
            for (int i = 0; i < predsJsonArray.length(); i++) {
                System.out.println(predsJsonArray.getJSONObject(i).getString("description")); // 시스템 출력
                System.out.println("============================================================");
                resultList.add(predsJsonArray.getJSONObject(i).getString("description"));
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Cannot process JSON results", e);
        }
        return resultList; // ArrayList 형태로 결과값 반환
    }
}
