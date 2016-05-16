package com.martluik.andromondo;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {

    private final static String TAG = "MainActivity";

    SharedPreferences sPref;

    // Notifications
    private NotificationManager mNotificationManager;
    private BroadcastReceiver mBroadcastReceiver;


    // Google maps
    private GoogleMap mGoogleMap;
    private Menu mOptionsMenu;
    private LocationManager locationManager;
    private String provider;
    private int markerCount = 0;
    private Polyline mPolyline;
    private PolylineOptions mPolylineOptions;


    // This run starting position
    private Location locationStart;
    // Waypoint starting position
    private Location locationWaypointStart;
    // Current run starting position
    private Location locationCurrentRunStart;
    // Current location
    private Location locationCurrent;
    // Previous location
    private Location locationPrevious;

    // Waypoints and speed
    private double speed = 0;
    private TextView tVWaypointsCounter;
    private TextView tVSpeed;

    // Current run variables
    private double distanceCurrentRunReal = 0;
    private double distanceCurrentRunLine = 0;
    private TextView tVdistanceCurrentRunLine;
    private TextView tVdistanceCurrentRunReal;

    // Waypoint variables
    private double distanceRealFromWaypoint = 0;
    private double distanceLineFromWaypoint = 0;
    private TextView tVdistanceFromWaypointLine;
    private TextView tVdistanceFromWaypointReal;

    // Total distance variables
    private double distanceRealTotal;
    private double distanceLineTotal = 0;
    private TextView tVdistanceTotalLine;
    private TextView tVdistanceTotalReal;

    // Notification fields
    private TextView tVnotifWaypoint;
    private TextView tVnotifTotal;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Notificatons
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notifWithControls();

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                switch (action) {
                    case ("notification-broadcast-reset"):
                        buttonResetClicked(null);
                        break;
                    case ("notification-broadcast-waypoint"):
                        buttonAddWayPointClicked(null);
                        break;
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("notification-broadcast");
        intentFilter.addAction("notification-broadcast-waypoint");
        intentFilter.addAction("notification-broadcast-reset");
        registerReceiver(mBroadcastReceiver, intentFilter);


        // Google maps
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Criteria criteria = new Criteria();

        // get the location provider (GPS/CEL-towers, WIFI)
        provider = locationManager.getBestProvider(criteria, false);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");

        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");

        }

        locationCurrent = locationManager.getLastKnownLocation(provider);

        if (locationCurrent != null) {

            locationStart = locationCurrent;
            locationCurrentRunStart = locationCurrent;
            locationPrevious = locationCurrent;
        }

        // Current run TextViews
        tVdistanceCurrentRunReal = (TextView) findViewById(R.id.textview_creset_distance);
        tVdistanceCurrentRunLine = (TextView) findViewById(R.id.textview_creset_line);
        // Waypoint TextViews
        tVdistanceFromWaypointReal = (TextView) findViewById(R.id.textview_wp_distance);
        tVdistanceFromWaypointLine = (TextView) findViewById(R.id.textview_wp_line);
        // Total distances TextViews
        tVdistanceTotalReal = (TextView) findViewById(R.id.textview_total_distance);
        tVdistanceTotalLine = (TextView) findViewById(R.id.textview_total_line);

        tVWaypointsCounter = (TextView) findViewById(R.id.textview_wpcount);
        tVSpeed = (TextView) findViewById(R.id.textview_speed);

        // Load total distance
        sPref = getSharedPreferences("sp", 0);
        distanceRealTotal = sPref.getInt("total", 0);

        if (distanceRealTotal != 0) {
            tVdistanceTotalReal.setText(String.valueOf(Math.round(distanceRealTotal)) + "m");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mOptionsMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.menu_mylocation:
                item.setChecked(!item.isChecked());
                updateMyLocation();
                return true;
            case R.id.menu_trackposition:
                item.setChecked(!item.isChecked());
                updateTrackPosition();
                return true;
            case R.id.menu_keepmapcentered:
                item.setChecked(!item.isChecked());
                return true;
            case R.id.menu_map_type_hybrid:
            case R.id.menu_map_type_none:
            case R.id.menu_map_type_normal:
            case R.id.menu_map_type_satellite:
            case R.id.menu_map_type_terrain:
                item.setChecked(true);
                updateMapType();
                return true;

            case R.id.menu_map_zoom_10:
            case R.id.menu_map_zoom_15:
            case R.id.menu_map_zoom_20:
            case R.id.menu_map_zoom_in:
            case R.id.menu_map_zoom_out:
            case R.id.menu_map_zoom_fittrack:
                updateMapZoomLevel(item.getItemId());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }


    }

    private void updateMapZoomLevel(int itemId) {
        if (!checkReady()) {
            return;
        }

        switch (itemId) {
            case R.id.menu_map_zoom_10:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(10));
                break;
            case R.id.menu_map_zoom_15:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                break;
            case R.id.menu_map_zoom_20:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(20));
                break;
            case R.id.menu_map_zoom_in:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomIn());
                break;
            case R.id.menu_map_zoom_out:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomOut());
                break;
            case R.id.menu_map_zoom_fittrack:
                updateMapZoomFitTrack();
                break;
        }
    }

    private void updateMapZoomFitTrack() {
        if (mPolyline == null) {
            return;
        }

        List<LatLng> points = mPolyline.getPoints();

        if (points.size() <= 1) {
            return;
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        LatLngBounds bounds = builder.build();
        int padding = 0; // offset from edges of the map in pixels
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));

    }

    private void updateTrackPosition() {
        if (!checkReady()) {
            return;
        }

        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {
            mPolylineOptions = new PolylineOptions().width(5).color(Color.BLUE);
            mPolyline = mGoogleMap.addPolyline(mPolylineOptions);
        }
    }

    private void updateMapType() {
        if (!checkReady()) {
            return;
        }

        if (mOptionsMenu.findItem(R.id.menu_map_type_normal).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_hybrid).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_none).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NONE);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_satellite).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_terrain).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        }
    }

    public void startRun(View view) {

        if (!mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {
            mGoogleMap.clear();

            distanceCurrentRunReal = 0;
            tVdistanceCurrentRunReal.setText(String.valueOf("0m"));

            distanceCurrentRunLine = 0;
            tVdistanceCurrentRunLine.setText(String.valueOf("0m"));

            distanceRealFromWaypoint = 0;
            tVdistanceFromWaypointReal.setText(String.valueOf("0m"));

            distanceLineFromWaypoint = 0;
            tVdistanceFromWaypointLine.setText(String.valueOf("0m"));

            locationWaypointStart = null;
            markerCount = 0;

            tVWaypointsCounter.setText(String.valueOf("0"));

            notifWithControls();

            mOptionsMenu.findItem(R.id.menu_trackposition).setChecked(true);
            mOptionsMenu.findItem(R.id.menu_keepmapcentered).setChecked(true);
            mOptionsMenu.findItem(R.id.menu_mylocation).setChecked(true);

            updateMyLocation();
            updateTrackPosition();

            if (locationCurrent != null) {
                locationStart = locationCurrent;
                locationCurrentRunStart = locationCurrent;
                locationPrevious = locationCurrent;
            }

            Log.d(TAG, "Run started");

        }
    }

    public void endRun(View view) {

        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {
            mOptionsMenu.findItem(R.id.menu_trackposition).setChecked(false);

            tVSpeed.setText("0m");

            Log.d(TAG, "Run ended");
        }
    }

    private boolean checkReady() {
        if (mGoogleMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void updateMyLocation() {
        if (mOptionsMenu.findItem(R.id.menu_mylocation).isChecked()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mGoogleMap.setMyLocationEnabled(true);
            return;
        }

        mGoogleMap.setMyLocationEnabled(false);
    }

    public void buttonAddWayPointClicked(View view) {
        if (locationCurrent == null || mOptionsMenu.findItem(R.id.menu_trackposition).isChecked() == false) {
            return;
        }

        markerCount++;

        mGoogleMap.addMarker(new MarkerOptions().position(new LatLng(locationCurrent.getLatitude(), locationCurrent.getLongitude())).title(Integer.toString(markerCount)));
        tVWaypointsCounter.setText(Integer.toString(markerCount));

        // Save waypoint location
        locationWaypointStart = locationCurrent;

        // Set distance from waypoint to 0
        distanceRealFromWaypoint = 0;
        distanceLineFromWaypoint = 0;

        tVdistanceFromWaypointReal.setText(String.valueOf("0m"));
        tVdistanceFromWaypointLine.setText(String.valueOf("0m"));

        notifWithControls();

        Log.d(TAG, "New waypoint added");
    }

    public void buttonResetClicked(View view) {
        // Set total distance tracers (real and linear) to 0
        distanceRealTotal = 0;
        distanceLineTotal = 0;

        // Reset total starting position
        locationStart = locationCurrent;

        // Reset displays
        tVdistanceTotalReal.setText(String.valueOf("0m"));
        tVdistanceTotalLine.setText(String.valueOf("0m"));

        notifWithControls();

        Log.d(TAG, "Total distances reset");
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;

        // set zoom level to 15 - street
        mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(17));

        // if there was initial location received, move map to it
        if (locationPrevious != null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(locationPrevious.getLatitude(), locationPrevious.getLongitude())));
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng newPoint = new LatLng(location.getLatitude(), location.getLongitude());

        if (mGoogleMap == null || mOptionsMenu == null) return;

        locationCurrent = location;

        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {

            if (mOptionsMenu.findItem(R.id.menu_keepmapcentered).isChecked() || locationPrevious == null) {
                mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(newPoint));
            }

            if (mPolyline != null) {
                List<LatLng> points = mPolyline.getPoints();
                points.add(newPoint);
                mPolyline.setPoints(points);
            }

            if (locationCurrentRunStart == null) {
                locationCurrentRunStart = location;
            }

            if (locationPrevious == null) {
                locationPrevious = location;
            }

            // Current run distance - real
            distanceCurrentRunReal += locationPrevious.distanceTo(location);
            tVdistanceCurrentRunReal.setText(String.valueOf(Math.round(distanceCurrentRunReal)) + "m");

            // Current run distance - linear
            distanceCurrentRunLine = locationCurrentRunStart.distanceTo(location);
            tVdistanceCurrentRunLine.setText(String.valueOf(Math.round(distanceCurrentRunLine)) + "m");

            // Atleast one waypoint is set
            if (locationWaypointStart != null) {
                // Distance from last waypoint - real
                distanceRealFromWaypoint += locationPrevious.distanceTo(location);
                tVdistanceFromWaypointReal.setText(String.valueOf(Math.round(distanceRealFromWaypoint)) + "m");

                // Distance from last waypoint - linear
                distanceLineFromWaypoint = locationWaypointStart.distanceTo(location);
                tVdistanceFromWaypointLine.setText(String.valueOf(Math.round(distanceLineFromWaypoint)) + "m");
            }

            // Total distance - real
            distanceRealTotal += locationPrevious.distanceTo(location);
            tVdistanceTotalReal.setText(String.valueOf(Math.round(distanceRealTotal)) + "m");

            // Total distance - linear
            distanceLineTotal = locationStart.distanceTo(location); // temp
            tVdistanceTotalLine.setText(String.valueOf(Math.round(distanceLineTotal)) + "m");

            // speed
            speed = 1000 / (locationPrevious.distanceTo(location) / 0.5);
            double min = speed / 60;
            double sec = speed % 60;
            tVSpeed.setText(String.format("%s:%s", Math.round(min), Math.round(sec)) + " min:km");

            // Update previous location
            locationPrevious = location;

        } else if (locationCurrentRunStart != null) {
            // Current run starting position is reset
            locationCurrentRunStart = null;

            // Current run distances are reset
            distanceCurrentRunReal = 0;
            distanceCurrentRunLine = 0;

            tVdistanceCurrentRunReal.setText("0m");
            tVdistanceCurrentRunLine.setText("0m");
        }
        notifWithControls();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");
        }

        if (locationManager != null) {
            locationManager.requestLocationUpdates(provider, 500, 1, this);
        }

        notifWithControls();
    }

    private void notifWithControls() {

        // get the view layout
        RemoteViews remoteView = new RemoteViews(
                getPackageName(), R.layout.notification);

        remoteView.setTextViewText(R.id.textViewNotifTotal, "Total\n" + Math.round(distanceRealTotal) + "m");
        remoteView.setTextViewText(R.id.textViewNotifWaypoint, "WP\n" + Math.round(distanceRealFromWaypoint) + "m");

        // define intents
        PendingIntent pIntentAddWaypoint = PendingIntent.getBroadcast(
                this,
                0,
                new Intent("notification-broadcast-waypoint"),
                0
        );

        PendingIntent pIntentResetTripmeter = PendingIntent.getBroadcast(
                this,
                0,
                new Intent("notification-broadcast-reset"),
                0
        );

        // bring back already running activity
        // in manifest set android:launchMode="singleTop"
        PendingIntent pIntentOpenActivity = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // attach events
        remoteView.setOnClickPendingIntent(R.id.buttonAddWayPoint, pIntentAddWaypoint);
        remoteView.setOnClickPendingIntent(R.id.buttonResetTripmeter, pIntentResetTripmeter);
        remoteView.setOnClickPendingIntent(R.id.buttonOpenActivity, pIntentOpenActivity);

        // build notification
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setContent(remoteView)
                        .setSmallIcon(R.drawable.ic_my_location_white_48dp)
                        .setOngoing(true);
        // notify
        mNotificationManager.notify(1, mBuilder.build());
    }
}
