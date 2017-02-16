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
import android.widget.RelativeLayout;
import android.widget.TextView;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.StringTokenizer;

import static lbsproject.turnbyturnnavi.R.id.map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int REQUEST_CODE_AUTOCOMPLETE = 1;
    private GoogleMap mMap;
    private LocationManager manager;
    private GPSListener gpsListener;
    private TextView placeImfo; // 선택된 장소의 이름, 주소를 보려줄 TextView
    private RelativeLayout imformationLayout; // 선택된 장소의 이름, 주소, 도착지설정버튼, 상세정보버튼을 보여줄 Layout
    private Button endLocationSet;  // 도착지 설정 버튼
    private Button moreImformation; // 상세 정보 버튼
    private PolylineOptions polylineOptions; // polyline을 그릴 point들을 add할 polylineoption
    private String placesImformation; // 상세 정보에 필요한 장소 정보들을 저장할 String
    private View fragment;         // 상세정보 Fragment를 조정하기 위한 View 객체
    private double myLocationLat;  // 출발지 Lat
    private double myLocationLng;  // 출발지 Lng
    private double endLocationLat; // 도착지 Lat
    private double endLocationLng; // 도착지 Lng
    private static final String API_KEY = "8ebeb3d5-dfda-3849-a311-ce9c0292d0ca";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        // 상세정보 Fragment를 조정하기 위한 fragment 변수(View 객체)
        fragment = findViewById(R.id.imformationFragment);
        // 초기 설정은 상세정보 Fragment를 보이지 않게 하기
        fragment.setVisibility(View.GONE);

        // 위치 관리자 객체 참조
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // 위치 정보를 받을 리스너 생성
        gpsListener = new GPSListener();
        // 폴리라인을 그릴 point 객체를 add해주기 위한 polylineoptions 객체
        polylineOptions = new PolylineOptions();

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
                final Place place = PlaceAutocomplete.getPlace(this, data);
                Log.i("Place Selected: ", "" + place.getName());

                // 선택한 장소로 Camera 이동
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15));
                // MapType은 Normal로 설정
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                // 선택한 장소에 마커 표시
                mMap.addMarker(new MarkerOptions().position(place.getLatLng()).title((String) place.getName()));

                // 선택된 장소의 이름, 주소, 도착지설정버튼, 상세정보버튼을 보여줄 Layout
                imformationLayout = (RelativeLayout) findViewById(R.id.imformationLayout);
                imformationLayout.setVisibility(View.VISIBLE);

                // 선택된 장소의 이름, 주소를 저장하고, 보여중 TextView
                placeImfo = (TextView) findViewById(R.id.place_imfo);
                placeImfo.setText(place.getName() + "\n");
                placeImfo.append(place.getAddress() + "\n");

                // 도착지 설정 버튼
                endLocationSet = (Button) findViewById(R.id.endLocationSet);
                endLocationSet.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //
                        imformationLayout.setVisibility(View.GONE);

                        // 선택한 장소의 lat, lng 값을 저장
                        endLocationLat = place.getLatLng().latitude;
                        endLocationLng = place.getLatLng().longitude;

                        // T-map url을 이용하여 JSON 파일을 불러오기 위한 함수 사용
                        new JsonLoadingTask().execute();
                    }
                });

                // 상세정보 버튼
                moreImformation = (Button) findViewById(R.id.moreImformation);
                moreImformation.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // 상세정보 보이기
                        fragment.setVisibility(View.VISIBLE);
                    }
                });

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
        long minTime = 5000; // 5초 단위로 Reset
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

    // T-map Url 에서 JSON 파일을 가져오는 함수
    public String getJsonText() {

        StringBuffer sb = new StringBuffer();

        try {
            // T-map 연동 url 주소
            String url = "https://apis.skplanetx.com/tmap/routes/pedestrian?version=1"
                    + "&startX=" + myLocationLng
                    + "&startY=" + myLocationLat
                    + "&endX=" + endLocationLng
                    + "&endY=" + endLocationLat
                    + "&reqCoordType=WGS84GEO" // 출발지, 도착지 좌표계 유형 : 경위도
                    + "&resCoordType=WGS84GEO" // 응답 받는 좌표계 유형 : 경위도
                    + "&startName=%EC%B6%9C%EB%B0%9C%EC%A7%80" // 출발지 UTF-8 변환
                    + "&endName=%EB%8F%84%EC%B0%A9%EC%A7%80" // 도착지 UTF-8 변환
                    + "&appKey=" + API_KEY;
            // url 주소로 JSON 파일 가져옴
            String line = getStringFromUrl(url);
            // 원격에서 읽어온 데이터로 JSON 객체 생성
            JSONObject object = new JSONObject(line);

            // "features" 배열로 구성 되어있으므로 JSON 배열생성
            JSONArray featuresArray = object.getJSONArray("features");

            for (int i = 0; i < featuresArray.length(); i++) {
                // "features" JSON Array에서 "properties" JSON Object를 가져오기 위한 JSON Object 생성
                JSONObject typeObject = featuresArray.getJSONObject(i);

                if(typeObject.getJSONObject("geometry").getString("type").equals("Point")) {
                    StringTokenizer tokenizer = new StringTokenizer(typeObject.getJSONObject("geometry").getString("coordinates"), "\\[|,|\\]");
                    String[] latData = new String[( tokenizer.countTokens() / 2 )]; // Lat 데이터를 저장해줄 String 배열
                    String[] lngData = new String[( tokenizer.countTokens() / 2 )]; // Lng 데이터를 저장해줄 String 배열
                    int tokenCount = 1;   // Lat와 Lng 데이터를 나눠서 저장해 주기 위한 사용한 token을 Count
                    int latDataCount = 0; // latData String 배열의 index
                    int lngDataCount = 0; // lngData String 배열의 index
                    // coordinates 의 홀수Token - lng, 짝수Token - lat 데이터
                    while(tokenizer.hasMoreTokens()) {
                        if(tokenCount%2 != 0) { // 홀수Token - lng 데이터 추출
                            String coordinates = tokenizer.nextToken();
                            System.out.println("coordinates lngData :" + coordinates);
                            sb.append("coordinates lngData :").append(coordinates).append("\n");
                            lngData[lngDataCount] = coordinates;
                            lngDataCount++;
                        } else { // 짝수Token - lat 데이터 추출
                            String coordinates = tokenizer.nextToken();
                            System.out.println("coordinates latData :" + coordinates);
                            sb.append("coordinates latData :").append(coordinates).append("\n");
                            latData[latDataCount] = coordinates;
                            latDataCount++;
                        }
                        tokenCount++;
                    }
                    // latlng 데이터를 polylineOptions에 add하여 한번에 polyline으로 그릴 준비
                    for(int j = 0; j < latData.length; j++) {
                        polylineOptions.add(new LatLng(Double.parseDouble(latData[j]), Double.parseDouble(lngData[j])));
                    }

                    System.out.println("index :" + typeObject.getJSONObject("properties").getString("index"));
                    System.out.println("pointIndex :" + typeObject.getJSONObject("properties").getString("pointIndex"));
                    System.out.println("name :" + typeObject.getJSONObject("properties").getString("name"));
                    System.out.println("description :" + typeObject.getJSONObject("properties").getString("description"));
                    System.out.println("direction :" + typeObject.getJSONObject("properties").getString("direction"));
                    System.out.println("intersectionName :" + typeObject.getJSONObject("properties").getString("intersectionName"));
                    System.out.println("turnType :" + typeObject.getJSONObject("properties").getString("turnType"));
                    System.out.println("pointType :" + typeObject.getJSONObject("properties").getString("pointType"));
                    if(typeObject.getJSONObject("properties").getString("pointType").equals("SP"))
                    {
                        System.out.println("totalDistance :" + typeObject.getJSONObject("properties").getString("totalDistance"));
                        System.out.println("totalTime :" + typeObject.getJSONObject("properties").getString("totalTime"));
                    }
                    System.out.println("============================================================");

                    sb.append("index :").append(typeObject.getJSONObject("properties").getString("index")).append("\n");
                    sb.append("pointIndex :").append(typeObject.getJSONObject("properties").getString("pointIndex")).append("\n");
                    sb.append("name :").append(typeObject.getJSONObject("properties").getString("name")).append("\n");
                    sb.append("description :").append(typeObject.getJSONObject("properties").getString("description")).append("\n");
                    sb.append("direction :").append(typeObject.getJSONObject("properties").getString("direction")).append("\n");
                    sb.append("intersectionName :").append(typeObject.getJSONObject("properties").getString("intersectionName")).append("\n");
                    sb.append("turnType : ").append(typeObject.getJSONObject("properties").getString("turnType")).append("\n");
                    sb.append("pointType :").append(typeObject.getJSONObject("properties").getString("pointType")).append("\n");
                    if(typeObject.getJSONObject("properties").getString("pointType").equals("SP"))
                    {
                        sb.append("totalDistance :").append(typeObject.getJSONObject("properties").getString("totalDistance")).append("\n");
                        sb.append("totalTime :").append(typeObject.getJSONObject("properties").getString("totalTime")).append("\n");
                    }
                    sb.append("\n");

                } else {
                    StringTokenizer tokenizer = new StringTokenizer(typeObject.getJSONObject("geometry").getString("coordinates"),"\\[|,|\\]");
                    String[] latData = new String[( tokenizer.countTokens() / 2 )]; // Lat 데이터를 저장해줄 String 배열
                    String[] lngData = new String[( tokenizer.countTokens() / 2 )]; // Lng 데이터를 저장해줄 String 배열
                    int tokenCount = 1;   // Lat와 Lng 데이터를 나눠서 저장해 주기 위한 사용한 token을 Count
                    int latDataCount = 0; // latData String 배열의 index
                    int lngDataCount = 0; // lngData String 배열의 index
                    while(tokenizer.hasMoreTokens()) {
                        if(tokenCount%2 != 0) { // 홀수Token - lng 데이터 추출
                            String coordinates = tokenizer.nextToken();
                            System.out.println("coordinates lngData :" + coordinates);
                            sb.append("coordinates lngData :").append(coordinates).append("\n");
                            lngData[lngDataCount] = coordinates;
                            lngDataCount++;
                        } else { // 짝수Token - lat 데이터 추출
                            String coordinates = tokenizer.nextToken();
                            System.out.println("coordinates latData :" + coordinates);
                            sb.append("coordinates latData :").append(coordinates).append("\n");
                            latData[latDataCount] = coordinates;
                            latDataCount++;
                        }
                        tokenCount++;
                    }
                    // latlng 데이터를 polylineOptions에 add하여 한번에 polyline으로 그릴 준비
                    for(int j = 0; j < latData.length; j++) {
                        polylineOptions.add(new LatLng(Double.parseDouble(latData[j]), Double.parseDouble(lngData[j])));
                    }

                    System.out.println("index :" + typeObject.getJSONObject("properties").getString("index"));
                    System.out.println("lineIndex :" + typeObject.getJSONObject("properties").getString("lineIndex"));
                    System.out.println("name :" + typeObject.getJSONObject("properties").getString("name"));
                    System.out.println("description :" + typeObject.getJSONObject("properties").getString("description"));
                    System.out.println("time :" + typeObject.getJSONObject("properties").getString("time"));
                    System.out.println("distance :" + typeObject.getJSONObject("properties").getString("distance"));
                    System.out.println("roadType :" + typeObject.getJSONObject("properties").getString("roadType"));
                    System.out.println("============================================================");

                    sb.append("index :").append(typeObject.getJSONObject("properties").getString("index")).append("\n");
                    sb.append("lineIndex :").append(typeObject.getJSONObject("properties").getString("lineIndex")).append("\n");
                    sb.append("name :").append(typeObject.getJSONObject("properties").getString("name")).append("\n");
                    sb.append("description :").append(typeObject.getJSONObject("properties").getString("description")).append("\n");
                    sb.append("time :").append(typeObject.getJSONObject("properties").getString("time")).append("\n");
                    sb.append("distance :").append(typeObject.getJSONObject("properties").getString("distance")).append("\n");
                    sb.append("roadType : ").append(typeObject.getJSONObject("properties").getString("roadType")).append("\n");
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        placesImformation = sb.toString();
        return sb.toString();
    }

    // 주어진 URL 페이지를 문자열로 얻는 함수
    public String getStringFromUrl(String url) throws UnsupportedEncodingException {

        BufferedReader br = null;
        HttpURLConnection urlConnection = null;

        // 읽은 데이터를 저장한 StringBuffer 를 생성한다.
        StringBuffer sb = new StringBuffer();

        try {
            URL jsonUrl = new URL(url);
            urlConnection = (HttpURLConnection) jsonUrl.openConnection();
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
    }

    // JSON 파일을 백그라운드에서 실행하기 위한 쓰레드를 만드는 클래스
    private class JsonLoadingTask extends AsyncTask<String, Void, String> {
        // doInBackground : 백그라운드 작업을 진행한다.
        @Override
        protected String doInBackground(String... strs) {
            return getJsonText();
        }
        // onPostExecute : 백그라운드 작업이 끝난 후 UI 작업을 진행한다.
        @Override
        protected void onPostExecute(String result) {
            // 경로 polyline으로 그려주기
            Polyline polyline = mMap.addPolyline(polylineOptions);
        }
    }

    // 뒤로 가기 버튼 Custom
    @Override
    public void onBackPressed() {
        if(fragment.getVisibility() == View.VISIBLE) {
            fragment.setVisibility(View.GONE);
        } else {
            super.onBackPressed();
        }
    }
}