package com.dev.off;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import java.util.Locale;
import java.util.Random;

import rikka.shizuku.Shizuku;

public class MockService extends Service {

    private static final String CHANNEL_ID = "DevOffGPS_Channel";
    private boolean isRunning = false;
    private double baseLat = 0.0;
    private double baseLng = 0.0;
    private double activeLat = 0.0;
    private double activeLng = 0.0;

    private LocationManager locationManager;
    private final String[] PROVIDERS = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused"};
    private final Random random = new Random();

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            baseLat = intent.getDoubleExtra("lat", 0.0);
            baseLng = intent.getDoubleExtra("lng", 0.0);
            activeLat = baseLat;
            activeLng = baseLng;
        }

        updateNotification();

        if (!isRunning) {
            isRunning = true;
            initShizukuAndStart();
        }

        return START_STICKY;
    }

    private void updateNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DevOff GPS Active")
                .setContentText(String.format(Locale.US, "Mocking: %.5f, %.5f (Acc: 102m)", activeLat, activeLng))
                .setSmallIcon(R.drawable.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();

        startForeground(1001, notification);
    }

    private void initShizukuAndStart() {
        new Thread(() -> {
            runShellCmd("appops set " + getPackageName() + " MOCK_LOCATION allow");
            runShellCmd("settings put secure mock_location_app " + getPackageName());

            for (String provider : PROVIDERS) {
                runShellCmd("cmd location add-test-provider " + provider);
                runShellCmd("cmd location set-test-provider-enabled " + provider + " true");
                setupTestProviderApi(provider);
            }

            startMockLoop();
        }).start();
    }

    private void setupTestProviderApi(String provider) {
        try {
            locationManager.addTestProvider(
                    provider,
                    false, false, false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            );
        } catch (Exception ignored) {}

        try {
            locationManager.setTestProviderEnabled(provider, true);
        } catch (Exception ignored) {}
    }

    private void startMockLoop() {
        long lastJitterTime = System.currentTimeMillis();

        while (isRunning) {
            long currentTime = System.currentTimeMillis();

            // Har 1 minute (60,000 ms) mein 2 se 50 meter ke radius mein random movement
            if (currentTime - lastJitterTime >= 60000) {
                applyRandomMovement();
                lastJitterTime = currentTime;
                updateNotification();
            }

            for (String provider : PROVIDERS) {
                try {
                    Location mockLoc = new Location(provider);
                    mockLoc.setLatitude(activeLat);
                    mockLoc.setLongitude(activeLng);
                    mockLoc.setAltitude(5.0);
                    mockLoc.setTime(System.currentTimeMillis());
                    
                    // Fixed Accuracy = 102
                    mockLoc.setAccuracy(102.0f);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        mockLoc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                    }

                    locationManager.setTestProviderLocation(provider, mockLoc);
                } catch (Exception e) {
                    setupTestProviderApi(provider);
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // 2 meter se 50 meter radius jitter algorithm
    private void applyRandomMovement() {
        double radiusMeters = 2.0 + (48.0 * random.nextDouble()); // Random distance between 2m and 50m
        double randomAngle = 2.0 * Math.PI * random.nextDouble();  // Random direction (0 to 360 degrees)

        double deltaLat = (radiusMeters * Math.cos(randomAngle)) / 111111.0;
        double deltaLng = (radiusMeters * Math.sin(randomAngle)) / (111111.0 * Math.cos(Math.toRadians(baseLat)));

        activeLat = baseLat + deltaLat;
        activeLng = baseLng + deltaLng;
    }

    private void runShellCmd(String cmd) {
        try {
            if (Shizuku.pingBinder()) {
                Process p = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
                p.waitFor();
            }
        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DevOff GPS Service",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        new Thread(() -> {
            for (String provider : PROVIDERS) {
                runShellCmd("cmd location remove-test-provider " + provider);
                try {
                    locationManager.removeTestProvider(provider);
                } catch (Exception ignored) {}
            }
        }).start();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
