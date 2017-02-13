package lbsproject.turnbyturnnavi;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

import static lbsproject.turnbyturnnavi.R.id.map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int REQUEST_CODE_AUTOCOMPLETE = 1;
    private GoogleMap mMap;
    private LocationManager manager;
    private GPSListener gpsListener;
    private double myLocationLat;
    private double myLocationLng;


    private static final String LOG_TAG = "Google Direction API";
    private static final String DIRECTION_API_BASE = "https://maps.googleapis.com/maps/api/directions";
    private static final String OUT_JSON = "/json";
    private static final String API_KEY = "AIzaSyA9HtscpiGDrU3l-5eePCpeSi2Gz2I2VYg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        // 위치 관리자 객체 참조
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // 위치 정보를 받을 리스너 생성
        gpsListener = new GPSListener();

        // autocomplete activity 실행할 Button
        Button openButton = (Button) findViewById(R.id.open_button);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopMyLocationService(); // 현재 위치로 반환하는 위치 정보 수집 종료
                openAutocompleteActivity(); // autocomplete를 실행할 Activity 실행
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // GPS Setting 상태 확인
        enableGPSSetting();

        // Permission 설정 확인 (Runtime)
        if (ActivityCompat.checkSelfPermission
                (this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Device의 위치 표시 (마커)
        mMap.setMyLocationEnabled(true);
    }

    /**
     * AutoComplete를 실행해줄 Activity에 대한 함수
     */
    private void openAutocompleteActivity() {
        try {
            // Intent를 이용하여 Google play Services가 가능 한지 판단
            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                    .build(this);
            // onActivityResult함수로 값을 넘겨줌
            startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE);
        } catch (GooglePlayServicesRepairableException e) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, e.getConnectionStatusCode(),
                    0 /* requestCode */).show();
        } catch (GooglePlayServicesNotAvailableException e) {
            String message = "Google Play Services is not available: " +
                    GoogleApiAvailability.getInstance().getErrorString(e.errorCode);
            Log.e("Error: ", message);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * autocomplete activity 끝나고 나서 결과를 불러내는 함수
     *
     * @param requestCode AutoCompleteActivity에서 넘겨주는 requestCode
     * @param resultCode  AutoCompleteActivity에서 넘겨주는 resultCode
     * @param data        AutoCompleteActivity에서 넘겨주는 Intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // autocomplete widget으로 부터의 결과를 체크
        if (requestCode == REQUEST_CODE_AUTOCOMPLETE) {
            if (resultCode == RESULT_OK) {
                // 선택한 장소에 대한 데이터 가져옴
                Place place = PlaceAutocomplete.getPlace(this, data);
                Log.i("Place Selected: ", "" + place.getName());

                // 선택한 장소로 Camera 이동
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15));
                // MapType은 Normal로 설정
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                // 선택한 장소에 마커 표시
                mMap.addMarker(new MarkerOptions().position(place.getLatLng()).title((String) place.getName()));
//                direction(myLocationLat, myLocationLng, place.getId());
                new JsonLoadingTask().execute();

            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                // 결과 에러가 났을 경우 상태 데이터를 가져옴
                Status status = PlaceAutocomplete.getStatus(this, data);
                Log.e("Error: Status = ", "" + status.toString());
            } else if (resultCode == RESULT_CANCELED) {
            }
        }
    }

    /**
     * 현재 위치를 반환해주기 위한 함수
     */
    private void startMyLocationService() {
        long minTime = 10000; // 10초 단위로 Reset
        float minDistance = 0; // 거리는 0단위로 Reset

        try {
            // GPS를 이용한 위치 요청
            manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTime,
                    minDistance,
                    gpsListener);

            // 네트워크를 이용한 위치 요청
            manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTime,
                    minDistance,
                    gpsListener);
        } catch (SecurityException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 현재 위치를 반환을 중지해주기 위한 함수
     */
    private void stopMyLocationService() {
        try {
            // 위치 정보 수집 종료
            manager.removeUpdates(gpsListener);
        } catch (SecurityException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 현재위치에 대한 LocationListener를 GPSListener 클래스로 정의
     */
    private class GPSListener implements LocationListener {
        /**
         * 위치 정보가 확인될 때 자동 호출되는 메소드
         */
        public void onLocationChanged(Location location) {
            Double latitude = location.getLatitude();
            Double longitude = location.getLongitude();

            String msg = "Latitude : " + latitude + "\nLongitude:" + longitude;
            Log.i("GPSListener", msg);

            showCurrentLocation(latitude, longitude);
        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    }

    /**
     * 현재 위치의 지도를 보여주기 위해 정의한 메소드
     *
     * @param latitude  현재 위치에 대한 Lat 값
     * @param longitude 현재 위치에 대한 Long 값
     */
    private void showCurrentLocation(Double latitude, Double longitude) {
        // 현재 위치를 이용해 LatLon 객체 생성
        myLocationLat = latitude;
        myLocationLng = longitude;
        LatLng currentLocation = new LatLng(latitude, longitude);


        // Device의 View를 현재 위치로 재설정
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

    }

    // GPS Setting 상태 확인
    private void enableGPSSetting() {
        boolean gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 초기 GPS가 꺼져있다면 GPS 설정으로 넘어 갈 수 있도록 함
        if (!gpsEnabled) {
            // GPS 켜기를 통해 GPS를 키면 현재 위치로 이동
            new AlertDialog.Builder(this)
                    .setTitle("GPS 설정")
                    .setMessage("GPS가 꺼져 있습니다.\nGPS를 켜시겠습니까?")
                    .setPositiveButton("GPS 켜기", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                            startMyLocationService();
                        }
                    })
                    // 닫기를 Click하면 초기 화면인 Sdney에서 변경 없음
                    .setNegativeButton("닫기", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).show();
        } else {
            // GPS가 켜져 있다면 바로 현재 위치로
            startMyLocationService();
        }
    }

 /*   public static ArrayList direction(double originLat, double originLng, String destination) {
        double startLat = originLat;
        double startLng = originLng;
        String end = destination;
        ArrayList resultList = null;
        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            StringBuilder sb = new StringBuilder(DIRECTION_API_BASE + OUT_JSON);

            sb.append("?origin=" + startLat + startLng);
            sb.append("&destination=place_id:" + end);
            sb.append("&language=kr");
            sb.append("&mode=transit");
            sb.append("&key=" + API_KEY);

            URL url = new URL(sb.toString());
            conn = (HttpURLConnection) url.openConnection();
            Log.v("URL", String.valueOf(url));

            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }
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
            JSONObject jsonObj = new JSONObject(jsonResults.toString());
            JSONArray routesJsonArray = jsonObj.getJSONArray("routes");
            // Extract the Place descriptions from the results
            resultList = new ArrayList(routesJsonArray.length());
            for (int i = 0; i < routesJsonArray.length(); i++) {
                System.out.println(routesJsonArray.getJSONObject(i).getString("routes"));
                System.out.println("============================================================");
                resultList.add(routesJsonArray.getJSONObject(i).getString("routes"));
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Cannot process JSON results", e);
        }
        return resultList;
    }*/

    public String getJsonText() {
        Log.v("getJsonText","실행");

        StringBuffer sb = new StringBuffer();

        try {
            String line = getStringFromUrl("https://maps.googleapis.com/maps/api/directions/json?origin=Chicago,IL&destination=Los+Angeles,CA&waypoints=Joplin,MO|Oklahoma+City,OK&key=AIzaSyA9HtscpiGDrU3l-5eePCpeSi2Gz2I2VYg");
            Log.v("URL", line);
            // 원격에서 읽어온 데이터로 JSON 객체 생성
            JSONObject object = new JSONObject(line);

            // "steps" 배열로 구성 되어있으므로 JSON 배열생성
            JSONArray routesArray = object.getJSONArray("routes");

            for (int i = 0; i < routesArray.length(); i++) {
                JSONArray legsArray = ((JSONObject) routesArray.get(i)).getJSONArray("legs");

                for(int j = 0; j < legsArray.length(); j++) {
                    JSONArray stepsArray = ((JSONObject) legsArray.get(j)).getJSONArray("steps");

                    for(int k = 0; k < stepsArray.length(); k++)
                    {
                        // bodylist 배열안에 내부 JSON 이므로 JSON 내부 객체 생성
                        JSONObject insideObject = stepsArray.getJSONObject(i);

                        System.out.println(insideObject.getJSONObject("distance").getString("text"));
                        System.out.println(insideObject.getJSONObject("distance").getString("value"));
                        System.out.println(insideObject.getJSONObject("duration").getString("text"));
                        System.out.println(insideObject.getJSONObject("duration").getString("value"));
                        System.out.println(insideObject.getJSONObject("end_location").getString("lat"));
                        System.out.println(insideObject.getJSONObject("end_location").getString("lng"));
                        System.out.println(insideObject.getString("html_instructions"));
                        System.out.println(insideObject.getJSONObject("polyline").getString("points"));
                        System.out.println(insideObject.getJSONObject("start_location").getString("lat"));
                        System.out.println(insideObject.getJSONObject("start_location").getString("lng"));
                        System.out.println(insideObject.getString("travel_mode"));
                        System.out.println("============================================================");

                        // StringBuffer 메소드 ( append : StringBuffer 인스턴스에 뒤에 덧붙인다. )
                        // JSONObject 메소드 ( get.String(), getInt(), getBoolean() .. 등 : 객체로부터 데이터의 타입에 따라 원하는 데이터를 읽는다. )

                        sb.append("distance text :").append(insideObject.getJSONObject("distance").getString("text")).append("\n");
                        sb.append("distance value :").append(insideObject.getJSONObject("distance").getString("value")).append("\n");
                        sb.append("duration text :").append(insideObject.getJSONObject("duration").getString("text")).append("\n");
                        sb.append("duration value :").append(insideObject.getJSONObject("duration").getString("value")).append("\n");
                        sb.append("end_location lat :").append(insideObject.getJSONObject("end_location").getString("lat")).append("\n");
                        sb.append("end_location lng :").append(insideObject.getJSONObject("end_location").getString("lng")).append("\n");
                        sb.append("html_instructions : ").append(insideObject.getString("html_instructions")).append("\n");
                        sb.append("polyline points :").append(insideObject.getJSONObject("polyline").getString("points")).append("\n");
                        sb.append("start_location lat :").append(insideObject.getJSONObject("start_location").getString("lat")).append("\n");
                        sb.append("start_location lng :").append(insideObject.getJSONObject("start_location").getString("lng")).append("\n");
                        sb.append("travel_mode : ").append(insideObject.getString("travel_mode")).append("\n");
                        sb.append("\n");
                    }
                }
            } // for
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    } // getJsonText


    // getStringFromUrl : 주어진 URL 페이지를 문자열로 얻는다.
    public String getStringFromUrl(String url) throws UnsupportedEncodingException {
        Log.v("getStringFromUrl","실행");

        BufferedReader br = null;
        HttpURLConnection urlConnection = null;

        // 읽은 데이터를 저장한 StringBuffer 를 생성한다.
        StringBuffer sb = new StringBuffer();

        try {
            URL jsonurl = new URL(url);
            urlConnection = (HttpURLConnection) jsonurl.openConnection();
            InputStream contentStream = urlConnection.getInputStream();

            br = new BufferedReader(new InputStreamReader(contentStream, "UTF-8"));

            // 라인 단위로 읽은 데이터를 임시 저장한 문자열 변수 line
            String line = null;

            // 라인 단위로 데이터를 읽어서 StringBuffer 에 저장한다.
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //자원해제
            try {
                br.close();
                urlConnection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    } // getStringFromUrl

    private class JsonLoadingTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strs) {
            Log.v("Background","실행");
            return getJsonText();
        } // doInBackground : 백그라운드 작업을 진행한다.
        @Override
        protected void onPostExecute(String result) {
            Log.v("onPostExecute","실행");
        } // onPostExecute : 백그라운드 작업이 끝난 후 UI 작업을 진행한다.
    } // JsonLoadingTask
}