package com.dev.off;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private MapView mMapView;
    private EditText etSearch;
    private GeoPoint selectedGeoPoint;
    private Marker currentMarker;
    private SharedPreferences favPrefs;

    private static final String PREF_FAV_KEY = "favourite_locations_json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));

        setContentView(R.layout.activity_main);

        favPrefs = getSharedPreferences("DevOffGPS_Favs", MODE_PRIVATE);

        mMapView = findViewById(R.id.mapView);
        etSearch = findViewById(R.id.etSearch);
        Button btnSearch = findViewById(R.id.btnSearch);
        Button btnStartMock = findViewById(R.id.btnStartMock);
        Button btnStopMock = findViewById(R.id.btnStopMock);
        Button btnSaveFav = findViewById(R.id.btnSaveFav);
        Button btnShowFav = findViewById(R.id.btnShowFav);

        if (mMapView != null) {
            mMapView.setMultiTouchControls(true);
            selectedGeoPoint = new GeoPoint(28.6139, 77.2090); // Default Delhi
            mMapView.getController().setZoom(15.0);
            mMapView.getController().setCenter(selectedGeoPoint);
            updateMarker(selectedGeoPoint, "Default Location");

            MapEventsReceiver mapEventsReceiver = new MapEventsReceiver() {
                @Override
                public boolean singleTapConfirmedHelper(GeoPoint p) {
                    selectedGeoPoint = p;
                    updateMarker(p, "Selected Point");
                    return true;
                }

                @Override
                public boolean longPressHelper(GeoPoint p) {
                    selectedGeoPoint = p;
                    updateMarker(p, "Selected Point");
                    return true;
                }
            };
            mMapView.getOverlays().add(new MapEventsOverlay(mapEventsReceiver));
        }

        btnSearch.setOnClickListener(v -> searchAddress(etSearch.getText().toString()));
        btnSaveFav.setOnClickListener(v -> saveCurrentLocationToFav());
        btnShowFav.setOnClickListener(v -> showFavouritesList());

        btnStartMock.setOnClickListener(v -> {
            if (selectedGeoPoint == null) {
                Toast.makeText(this, "Please select a point on the map first!", Toast.LENGTH_SHORT).show();
                return;
            }
            startMockService();
        });

        btnStopMock.setOnClickListener(v -> {
            Intent intent = new Intent(this, MockService.class);
            stopService(intent);
            Toast.makeText(this, "Mock Location Stopped", Toast.LENGTH_SHORT).show();
        });

        checkPermissions();
        setupShizukuPermission();
    }

    private void updateMarker(GeoPoint point, String title) {
        if (mMapView == null) return;
        if (currentMarker != null) {
            mMapView.getOverlays().remove(currentMarker);
        }
        currentMarker = new Marker(mMapView);
        currentMarker.setPosition(point);
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentMarker.setTitle(title);
        mMapView.getOverlays().add(currentMarker);
        mMapView.invalidate();
    }

    private void searchAddress(String query) {
        if (query.trim().isEmpty()) {
            Toast.makeText(this, "Enter address or city name", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocationName(query, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    GeoPoint target = new GeoPoint(addr.getLatitude(), addr.getLongitude());
                    runOnUiThread(() -> {
                        selectedGeoPoint = target;
                        mMapView.getController().setCenter(target);
                        mMapView.getController().setZoom(16.0);
                        updateMarker(target, query);
                        Toast.makeText(this, "Found: " + addr.getAddressLine(0), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Location not found!", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Search failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveCurrentLocationToFav() {
        if (selectedGeoPoint == null) {
            Toast.makeText(this, "Select location first!", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Location Name");

        final EditText input = new EditText(this);
        input.setHint("Home, Office, etc.");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "Fav (" + selectedGeoPoint.getLatitude() + ")";

            try {
                String rawJson = favPrefs.getString(PREF_FAV_KEY, "[]");
                JSONArray jsonArray = new JSONArray(rawJson);

                JSONObject obj = new JSONObject();
                obj.put("name", name);
                obj.put("lat", selectedGeoPoint.getLatitude());
                obj.put("lng", selectedGeoPoint.getLongitude());

                jsonArray.put(obj);
                favPrefs.edit().putString(PREF_FAV_KEY, jsonArray.toString()).apply();
                Toast.makeText(this, "Saved to Favourites!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to save favourite", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showFavouritesList() {
        try {
            String rawJson = favPrefs.getString(PREF_FAV_KEY, "[]");
            JSONArray jsonArray = new JSONArray(rawJson);

            if (jsonArray.length() == 0) {
                Toast.makeText(this, "No saved favourites found!", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> names = new ArrayList<>();
            List<GeoPoint> points = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                names.add(obj.getString("name"));
                points.add(new GeoPoint(obj.getDouble("lat"), obj.getDouble("lng")));
            }

            String[] items = names.toArray(new String[0]);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Favourite Locations");
            builder.setItems(items, (dialog, which) -> {
                GeoPoint target = points.get(which);
                selectedGeoPoint = target;
                mMapView.getController().setCenter(target);
                mMapView.getController().setZoom(16.0);
                updateMarker(target, names.get(which));
                Toast.makeText(this, "Loaded: " + names.get(which), Toast.LENGTH_SHORT).show();
            });
            builder.show();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading favourites", Toast.LENGTH_SHORT).show();
        }
    }

    private void startMockService() {
        Intent intent = new Intent(this, MockService.class);
        intent.putExtra("lat", selectedGeoPoint.getLatitude());
        intent.putExtra("lng", selectedGeoPoint.getLongitude());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "Mock Location Started!", Toast.LENGTH_SHORT).show();
    }

    private void setupShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(100);
                } else {
                    grantAppOps();
                }
            }
        } catch (Exception ignored) {}
    }

    private void grantAppOps() {
        new Thread(() -> {
            try {
                // Grant mock location appops and set secure mock location app setting
                Shizuku.newProcess(new String[]{"sh", "-c", "appops set " + getPackageName() + " MOCK_LOCATION allow"}, null, null).waitFor();
                Shizuku.newProcess(new String[]{"sh", "-c", "settings put secure mock_location_app " + getPackageName()}, null, null).waitFor();
            } catch (Exception ignored) {}
        }).start();
    }

    private void checkPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> requestList = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                requestList.add(p);
            }
        }

        if (!requestList.isEmpty()) {
            ActivityCompat.requestPermissions(this, requestList.toArray(new String[0]), 101);
        }
    }
}

