package lbsproject.turnbyturnnavi;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.util.Log;

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

import static lbsproject.turnbyturnnavi.R.id.map;

public class MapsActivity extends FragmentActivity
        implements OnMapReadyCallback {

    private static final int REQUEST_CODE_AUTOCOMPLETE = 1;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        // autocomplete activity 실행할 Button
        Button openButton = (Button) findViewById(R.id.open_button);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission
                (this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Device의 위치 표시 (마커)
        mMap.setMyLocationEnabled(true);
        // Device의 현재 위치를 반환해주는 버튼을 삭제
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
    }

    private void openAutocompleteActivity() {
        try {
            // Intent를 이용하여 Google play Services가 가능 한지 판단
            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                    .build(this);
            startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE);
        } catch (GooglePlayServicesRepairableException e) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, e.getConnectionStatusCode(),
                    0 /* requestCode */).show();
        } catch (GooglePlayServicesNotAvailableException e) {
            String message = "Google Play Services is not available: " +
                    GoogleApiAvailability.getInstance().getErrorString(e.errorCode);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * autocomplete activity 끝나고 나서 결과를 불러내는 함수
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // autocomplete widget으로 부터의 결과를 체크
        if (requestCode == REQUEST_CODE_AUTOCOMPLETE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);

            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
            } else if (resultCode == RESULT_CANCELED) {
            }
        }
    }

    private void startLocationService() {
        // 위치 관리자 객체 참조
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // 위치 정보를 받을 리스너 생성
        GPSListener gpsListener = new GPSListener();
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
        } catch(SecurityException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 리스너 클래스 정의
     */
    private class GPSListener implements LocationListener {
        /**
         * 위치 정보가 확인될 때 자동 호출되는 메소드
         */
        public void onLocationChanged(Location location) {
            Double latitude = location.getLatitude();
            Double longitude = location.getLongitude();

            String msg = "Latitude : "+ latitude + "\nLongitude:"+ longitude;
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
     * @param latitude 현재 위치에 대한 Lat 값
     * @param longitude 현재 위치에 대한 Long 값
     */
    private void showCurrentLocation(Double latitude, Double longitude) {
        // 현재 위치를 이용해 LatLon 객체 생성
        LatLng curPoint = new LatLng(latitude, longitude);

        // Device의 View를 현재 위치로 재설정
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(curPoint, 15));
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

    }

    // GPS Setting 상태 확인
    private void enableGPSSetting() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
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
                            startLocationService();
                        }
                    })
                    // 닫기를 Click하면 초기 화면인 Sdney에서 변경 없음
                    .setNegativeButton("닫기", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).show();
        } else { // GPS가 켜져 있다면 바로 현재 위치로
            startLocationService();
        }
    }
}