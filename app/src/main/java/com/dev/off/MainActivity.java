package com.dev.off;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class MainActivity extends AppCompatActivity {

    private MapView mMapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid configuration
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));

        setContentView(R.layout.activity_main);

        mMapView = findViewById(R.id.mapView);

        if (mMapView != null) {
            mMapView.setMultiTouchControls(true);

            // Default location setup
            GeoPoint startPoint = new GeoPoint(28.6139, 77.2090);
            mMapView.getController().setZoom(15.0);
            mMapView.getController().setCenter(startPoint);

            // Corrected OSMDroid MapEventsReceiver interface
            MapEventsReceiver mapEventsReceiver = new MapEventsReceiver() {
                @Override
                public boolean singleTapConfirmedHelper(GeoPoint p) {
                    return false;
                }

                @Override
                public boolean longPressHelper(GeoPoint p) {
                    Marker startMarker = new Marker(mMapView);
                    startMarker.setPosition(p);
                    startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    startMarker.setTitle("Selected Location");

                    mMapView.getOverlays().clear();
                    mMapView.getOverlays().add(new MapEventsOverlay(this));
                    mMapView.getOverlays().add(startMarker);
                    mMapView.invalidate();
                    return true;
                }
            };

            MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(mapEventsReceiver);
            mMapView.getOverlays().add(mapEventsOverlay);
        }

        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 1001);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMapView != null) {
            mMapView.onPause();
        }
    }
}
