package com.craig.mapapp;

import android.content.Intent;
import android.content.res.AssetManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.collect.Multimap;
import com.google.maps.android.ui.IconGenerator;

import org.apache.commons.codec.EncoderException;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Queue;

public class MapsActivity extends FragmentActivity implements
        LocationListener,
        RotationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "LocationActivity";
    private static final long INTERVAL = 1000 * 60 * 1; //1 minute
    private static final long FASTEST_INTERVAL = 1000 * 60 * 1; // 1 minute
    Button btnFusedLocation;
    TextView tvLocation;
    LocationRequest mLocationRequest;
    GoogleApiClient mGoogleApiClient;
    Location mCurrentLocation;
    String mLastUpdateTime;
    GoogleMap googleMap;
    AssetManager assetManager;
    Navmesh navmesh;
    NavmeshViewer navmeshViewer;
    MapAppSM stateMachine;
    CompassListener compassListener;

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate ...............................");
        //show error dialog if GooglePlayServices not available
        if (!isGooglePlayServicesAvailable()) {
            finish();
        }
        createLocationRequest();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        setContentView(R.layout.activity_maps);
        SupportMapFragment fm = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        googleMap = fm.getMap();
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        assetManager = this.getAssets();

        try {
            navmesh = Navmesh.fromFile(assetManager.open("campusCentreMap.txt"));
            Log.d(TAG, "got campus centre map!");
        } catch (IOException e) {
            Log.d(TAG, "couldn't open campus centre map :(");
            navmesh = new Navmesh();
        }

        compassListener = new CompassListener(this);
        compassListener.setRotationListener(this);

        navmeshViewer = new NavmeshViewer(googleMap, navmesh);
        navmeshViewer.draw();

        stateMachine = new MapAppSM(this, navmesh, navmeshViewer);

        String testSearch = "food";
        Queue<Pair<Double, Navmesh.Cell>> results = navmesh.getCellsMatchingString(testSearch);
        for (Pair<Double, Navmesh.Cell> result : results) {
            System.err.println("found result: " + result.first + " - " + result.second.getName());
        }

        //cc!
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-37.911825, 145.132955), 19));

    }


    private boolean consumingTwoFingerTap = false;
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 2 && event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            consumingTwoFingerTap = true;
            Toast.makeText(getApplicationContext(), "tapped x 2! " + event.getAction(), Toast.LENGTH_SHORT).show();
            stateMachine.onTwoFingerTap();
            return true;
        }
        if (event.getPointerCount() == 1 && event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (consumingTwoFingerTap) {
                consumingTwoFingerTap = false;
                return false;
            }
            Toast.makeText(getApplicationContext(), "tapped! " + event.getAction(), Toast.LENGTH_SHORT).show();
            if (stateMachine.onTap(event, findViewById(R.id.rlayout))) {
                return true;
            }
        }
        return super.dispatchTouchEvent(event);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case AbstractVoiceSM.REQ_CODE_SPEECH_INPUT:
                stateMachine.handleSpeech(resultCode, data);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.compassListener.stopListening();
        stopLocationUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.compassListener.startListening();
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    private boolean isGooglePlayServicesAvailable() {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS == status) {
            return true;
        } else {
            GooglePlayServicesUtil.getErrorDialog(status, this, 0).show();
            return false;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        PendingResult<Status> pendingResult
            = LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed: " + connectionResult.toString());
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        LatLng currentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        //googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 13));
        stateMachine.onLocationChanged(location);
        //addMarker();
    }

    @Override
    public void onRotationChanged(float rotation) {
        stateMachine.onRotationChanged(rotation);
    }

    private void addMarker() {
        MarkerOptions options = new MarkerOptions();

        // following four lines requires 'Google Maps Android API Utility Library'
        // https://developers.google.com/maps/documentation/android/utility/
        // I have used this to display the time as title for location markers
        // you can safely comment the following four lines but for this info
        IconGenerator iconFactory = new IconGenerator(this);
        iconFactory.setStyle(IconGenerator.STYLE_PURPLE);
        options.icon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(mLastUpdateTime)));
        options.anchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());

        LatLng currentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        options.position(currentLatLng);
        Marker mapMarker = googleMap.addMarker(options);
        long atTime = mCurrentLocation.getTime();
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date(atTime));
        mapMarker.setTitle(mLastUpdateTime);
        Log.d(TAG, "Marker added.............................");
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng,
                13));
        Log.d(TAG, "Zoom done.............................");

        //LatLng NEWARK = new LatLng(40.714086, -74.228697);
        GroundOverlayOptions newarkMap = new GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromResource(R.drawable.heman))
                .position(currentLatLng, 4300f, 3250f);
        googleMap.addGroundOverlay(newarkMap);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng,
                13));
    }


    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
            Log.d(TAG, "Location update stopped .......................");
        }
    }

}