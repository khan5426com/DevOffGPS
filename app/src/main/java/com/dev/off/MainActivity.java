package com.dev.off;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private MapView mMapView;
    private EditText etSearch;
    private Button btnStartMock, btnStopMock, btnFav, btnLayer;
    private GeoPoint selectedGeoPoint;
    private ArrayList<String> favList = new ArrayList<>();
    private ArrayList<GeoPoint> favPoints = new ArrayList<>();
    private boolean isHybrid = false;

    private static final int PERMISSION_REQ_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(getApplicationContext(), getSharedPreferences("osm_pref", MODE_PRIVATE));
        setContentView(R.layout.activity_main);

        etSearch = findViewById(R.id.etSearch);
        btnStartMock = findViewById(R.id.btnStartMock);
        btnStopMock = findViewById(R.id.btnStopMock);
        btnFav = findViewById(R.id.btnFav);
        btnLayer = findViewById(R.id.btnLayer);
        mMapView = findViewById(R.id.mapView);

        setupMap();
        checkPermissions();
        checkShizukuPermission();

        btnStartMock.setOnClickListener(v -> {
            if (selectedGeoPoint != null) {
                Intent serviceIntent = new Intent(this, MockService.class);
                serviceIntent.putExtra("lat", selectedGeoPoint.getLatitude());
                serviceIntent.putExtra("lon", selectedGeoPoint.getLongitude());
                ContextCompat.startForegroundService(this, serviceIntent);
                Toast.makeText(this, "Mock Location Started!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Pehle map par location select karein!", Toast.LENGTH_SHORT).show();
            }
        });

        btnStopMock.setOnClickListener(v -> {
            stopService(new Intent(this, MockService.class));
            Toast.makeText(this, "Mock Location Stopped!", Toast.LENGTH_SHORT).show();
        });

        btnFav.setOnClickListener(v -> {
            if (selectedGeoPoint != null) {
                String name = "Loc: " + String.format("%.4f", selectedGeoPoint.getLatitude()) + ", " + String.format("%.4f", selectedGeoPoint.getLongitude());
                favList.add(name);
                favPoints.add(selectedGeoPoint);
                Toast.makeText(this, "Favourite mein add ho gaya!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Koi location select nahi hai!", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnViewFav).setOnClickListener(v -> showFavouritesDialog());

        btnLayer.setOnClickListener(v -> {
            isHybrid = !isHybrid;
            if (isHybrid) {
                mMapView.setTileSource(TileSourceFactory.USGS_SAT);
                btnLayer.setText("Map: Satellite");
            } else {
                mMapView.setTileSource(TileSourceFactory.MAPNIK);
                btnLayer.setText("Map: Standard");
            }
        });
    }

    private void setupMap() {
        mMapView.setMultiTouchControls(true);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        GeoPoint startPoint = new GeoPoint(28.6139, 77.2090);
        mMapView.getController().setZoom(15.0);
        mMapView.getController().setCenter(startPoint);

        mMapView.overlays.add(new org.osmdroid.views.overlay.MapEventsOverlay(p -> {
            selectedGeoPoint = new GeoPoint(p.getLatitude(), p.getLongitude());
            mMapView.getOverlays().removeIf(o -> o instanceof Marker && !"current".equals(((Marker)o).getId()));
            Marker marker = new Marker(mMapView);
            marker.setPosition(selectedGeoPoint);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle("Selected Location");
            mMapView.getOverlays().add(marker);
            mMapView.invalidate();
            Toast.makeText(this, "Location Selected", Toast.LENGTH_SHORT).show();
            return true;
        }));
    }

    private void showFavouritesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Favourite Locations");
        if (favList.isEmpty()) {
            builder.setMessage("Koi Favourite saved nahi hai.");
            builder.setPositiveButton("OK", null);
        } else {
            String[] items = favList.toArray(new String[0]);
            builder.setItems(items, (dialog, which) -> {
                GeoPoint pt = favPoints.get(which);
                selectedGeoPoint = pt;
                mMapView.getController().setCenter(pt);
                Toast.makeText(this, "Loaded Favourite", Toast.LENGTH_SHORT).show();
            });
        }
        builder.show();
    }

    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQ_CODE);
        }
    }

    private void checkShizukuPermission() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            Toast.makeText(this, "Shizuku version purana hai!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            try {
                Shizuku.requestPermission(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
