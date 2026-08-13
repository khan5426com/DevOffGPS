package com.dev.off;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MapView mMapView;
    private EditText etSearch;
    private GeoPoint selectedGeoPoint;
    private Marker currentMarker;
    private SharedPreferences favPrefs;
    private SharedPreferences authPrefs;
    private SharedPreferences locationPrefs;

    private Button btnStartMock;
    private Button btnDevOff;
    private Button btnDevOn;

    private static final String PREF_FAV_KEY = "favourite_locations_json";
    private static final String AUTH_PREF_NAME = "DevOff_Auth";
    private static final String KEY_SAVED_USER = "saved_username";
    private static final String KEY_SAVED_PASS = "saved_password";
    private static final String KEY_REMEMBER = "remember_checked";
    private static final String RAW_JSON_URL = "https://raw.githubusercontent.com/khan5426com/mvvnl/refs/heads/main/mvvnl.json";

    private static final String LOC_PREF_NAME = "DevOff_LastLocation";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LNG = "last_lng";

    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authPrefs = getSharedPreferences(AUTH_PREF_NAME, MODE_PRIVATE);
        locationPrefs = getSharedPreferences(LOC_PREF_NAME, MODE_PRIVATE);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        showLoginDialog();
    }

    private void showLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_login, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvDeviceId = dialogView.findViewById(R.id.tvDeviceId);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        CheckBox cbRemember = dialogView.findViewById(R.id.cbRemember);
        Button btnCopyId = dialogView.findViewById(R.id.btnCopyId);
        Button btnLogin = dialogView.findViewById(R.id.btnLogin);
        ProgressBar loginProgress = dialogView.findViewById(R.id.loginProgress);

        tvDeviceId.setText("Device ID: " + deviceId);

        boolean isRemembered = authPrefs.getBoolean(KEY_REMEMBER, false);
        if (isRemembered) {
            etUsername.setText(authPrefs.getString(KEY_SAVED_USER, ""));
            etPassword.setText(authPrefs.getString(KEY_SAVED_PASS, ""));
            cbRemember.setChecked(true);
        }

        btnCopyId.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Device ID", deviceId);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Device ID copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter both username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            loginProgress.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);

            verifyCredentialsFromGitHub(username, password, cbRemember.isChecked(), dialog, loginProgress, btnLogin);
        });

        dialog.show();
    }

    private void verifyCredentialsFromGitHub(String user, String pass, boolean remember, AlertDialog dialog, ProgressBar progress, Button btnLogin) {
        new Thread(() -> {
            boolean success = false;
            String errorMessage = "Invalid Device ID, Username, or Password!";

            try {
                URL url = new URL(RAW_JSON_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONArray jsonArray = new JSONArray(sb.toString());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject item = jsonArray.getJSONObject(i);
                        String gDeviceId = item.optString("device_id");
                        String gUser = item.optString("username");
                        String gPass = item.optString("password");
                        String gExpiry = item.optString("expirydate");

                        if (deviceId.equalsIgnoreCase(gDeviceId) && user.equals(gUser) && pass.equals(gPass)) {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
                            Date expiryDate = sdf.parse(gExpiry);
                            Date currentDate = new Date();

                            if (expiryDate != null && currentDate.after(expiryDate)) {
                                errorMessage = "Your subscription expired on " + gExpiry;
                                success = false;
                            } else {
                                success = true;
                            }
                            break;
                        }
                    }
                } else {
                    errorMessage = "Server connection failed! Code: " + conn.getResponseCode();
                }
            } catch (Exception e) {
                errorMessage = "Network error: " + e.getLocalizedMessage();
            }

            final boolean isSuccess = success;
            final String finalMsg = errorMessage;

            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                btnLogin.setEnabled(true);

                if (isSuccess) {
                    if (remember) {
                        authPrefs.edit()
                                .putString(KEY_SAVED_USER, user)
                                .putString(KEY_SAVED_PASS, pass)
                                .putBoolean(KEY_REMEMBER, true)
                                .apply();
                    } else {
                        authPrefs.edit().clear().apply();
                    }
                    Toast.makeText(MainActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    initMainApp();
                } else {
                    Toast.makeText(MainActivity.this, finalMsg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
        private void initMainApp() {
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

        setContentView(R.layout.activity_main);

        favPrefs = getSharedPreferences("DevOffGPS_Favs", MODE_PRIVATE);

        mMapView = findViewById(R.id.mapView);
        etSearch = findViewById(R.id.etSearch);
        LinearLayout layoutSearch = findViewById(R.id.layoutSearch);
        Button btnSearch = findViewById(R.id.btnSearch);
        btnStartMock = findViewById(R.id.btnStartMock);
        Button btnStopMock = findViewById(R.id.btnStopMock);
        Button btnSaveFav = findViewById(R.id.btnSaveFav);
        Button btnShowFav = findViewById(R.id.btnShowFav);
        
        btnDevOff = findViewById(R.id.btnDevOff);
        btnDevOn = findViewById(R.id.btnDevOn);
        Button btnDevSettings = findViewById(R.id.btnDevSettings);

        // Apply Dynamic Programmatic Gradients & Capsule Styles (No drawable XML needed)
        applySearchCapsuleStyle(layoutSearch);
        btnSaveFav.setBackground(createGradientDrawable("#36D1DC", "#5B86E5"));
        btnShowFav.setBackground(createGradientDrawable("#36D1DC", "#5B86E5"));
        btnDevSettings.setBackground(createGradientDrawable("#36D1DC", "#5B86E5"));
        
        btnStartMock.setBackground(createGradientDrawable("#A8E6CF", "#56AB2F")); // Light Green
        btnStopMock.setBackground(createGradientDrawable("#e74c3c", "#c0392b"));  // Red
        
        btnDevOff.setBackground(createGradientDrawable("#2ecc71", "#27ae60"));    // Green
        btnDevOn.setBackground(createGradientDrawable("#e74c3c", "#c0392b"));     // Red

        btnDevOff.setOnClickListener(v -> pushDevSetting(0));
        btnDevOn.setOnClickListener(v -> pushDevSetting(1));
        btnDevSettings.setOnClickListener(v -> openDeveloperSettingsIfEnabled());

        if (mMapView != null) {
            mMapView.setTileSource(new OnlineTileSourceBase(
                    "Google-Hybrid",
                    0, 20, 256, ".png",
                    new String[]{
                            "https://mt0.google.com/vt/lyrs=y&x={x}&y={y}&z={z}",
                            "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}",
                            "https://mt2.google.com/vt/lyrs=y&x={x}&y={y}&z={z}",
                            "https://mt3.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
                    }) {
                @Override
                public String getTileURLString(long pMapTileIndex) {
                    return getBaseUrl()
                            .replace("{x}", MapTileIndex.getX(pMapTileIndex) + "")
                            .replace("{y}", MapTileIndex.getY(pMapTileIndex) + "")
                            .replace("{z}", MapTileIndex.getZoom(pMapTileIndex) + "");
                }
            });

            mMapView.setMultiTouchControls(true);

            // Restore Last Saved Selected Location (Defaults to Delhi on first run)
            float savedLat = locationPrefs.getFloat(KEY_LAST_LAT, 28.6139f);
            float savedLng = locationPrefs.getFloat(KEY_LAST_LNG, 77.2090f);
            selectedGeoPoint = new GeoPoint(savedLat, savedLng);

            mMapView.getController().setZoom(16.0);
            mMapView.getController().setCenter(selectedGeoPoint);
            updateMarker(selectedGeoPoint, "Last Selected Location");

            MapEventsReceiver mapEventsReceiver = new MapEventsReceiver() {
                @Override
                public boolean singleTapConfirmedHelper(GeoPoint p) {
                    setSelectedLocation(p);
                    return true;
                }

                @Override
                public boolean longPressHelper(GeoPoint p) {
                    setSelectedLocation(p);
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
                Toast.makeText(this, "Please select a location on the map first!", Toast.LENGTH_SHORT).show();
                return;
            }
            startMockService();
            // Start Mock clicked -> Dark Green Gradient
            btnStartMock.setBackground(createGradientDrawable("#11998e", "#38ef7d"));
        });

        btnStopMock.setOnClickListener(v -> {
            Intent intent = new Intent(this, MockService.class);
            stopService(intent);
            // Stop Mock clicked -> Reset to Light Green Gradient
            btnStartMock.setBackground(createGradientDrawable("#A8E6CF", "#56AB2F"));
            Toast.makeText(this, "Mock Location Stopped", Toast.LENGTH_SHORT).show();
        });

        checkPermissions();
    }

    // Helper Method: Programmatic Gradient Capsules
    private GradientDrawable createGradientDrawable(String startColorHex, String endColorHex) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.parseColor(startColorHex), Color.parseColor(endColorHex) }
        );
        gd.setCornerRadius(60f);
        return gd;
    }

    // Helper Method: Pill Shaped Purple Search Bar
    private void applySearchCapsuleStyle(View searchView) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#B2A4FF"));
        gd.setCornerRadius(80f);
        gd.setStroke(4, Color.parseColor("#8E7AB5"));
        searchView.setBackground(gd);
    }

    private void setSelectedLocation(GeoPoint point) {
        selectedGeoPoint = point;
        updateMarker(point, "Selected Location");

        // Save location persistently
        locationPrefs.edit()
                .putFloat(KEY_LAST_LAT, (float) point.getLatitude())
                .putFloat(KEY_LAST_LNG, (float) point.getLongitude())
                .apply();
    }

    private void openDeveloperSettingsIfEnabled() {
        int devEnabled = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
        if (devEnabled == 1) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Click on Dev ON button", Toast.LENGTH_SHORT).show();
        }
    }

    private void pushDevSetting(int value) {
        try {
            boolean isPushed = Settings.Global.putInt(
                    getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    value
            );

            if (isPushed) {
                if (value == 0) {
                    btnDevOff.setBackground(createGradientDrawable("#2ecc71", "#27ae60")); // Green
                } else {
                    btnDevOn.setBackground(createGradientDrawable("#e74c3c", "#c0392b"));  // Red
                }
                Toast.makeText(this, "development_settings_enabled -> (" + value + ") Pushed!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Push failed! Check ADB WRITE_SECURE_SETTINGS permission.", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Permission Denied! Run ADB Command:\nadb shell pm grant com.dev.off android.permission.WRITE_SECURE_SETTINGS", Toast.LENGTH_LONG).show();
        }
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
                        setSelectedLocation(target);
                        mMapView.getController().setCenter(target);
                        mMapView.getController().setZoom(17.0);
                        Toast.makeText(this, "Found: " + addr.getAddressLine(0), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Location not found!", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Search error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
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
                setSelectedLocation(target);
                mMapView.getController().setCenter(target);
                mMapView.getController().setZoom(17.0);
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

    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMapView != null) mMapView.onPause();
    }
                    }
