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

public class MockService extends Service {

    private static final String CHANNEL_ID = "DevOffGPS_Channel";
    private boolean isRunning = false;
    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private LocationManager locationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            currentLat = intent.getDoubleExtra("lat", 0.0);
            currentLng = intent.getDoubleExtra("lng", 0.0);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DevOff GPS Active")
                .setContentText(String.format(Locale.US, "Mocking: %.4f, %.4f", currentLat, currentLng))
                .setSmallIcon(R.drawable.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();

        startForeground(1001, notification);

        if (!isRunning) {
            isRunning = true;
            setupTestProvider();
            startMockLoop();
        }

        return START_STICKY;
    }

    private void setupTestProvider() {
        try {
            locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
            );
        } catch (Exception ignored) {}

        try {
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
        } catch (Exception ignored) {}
    }

    private void startMockLoop() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    Location mockLoc = new Location(LocationManager.GPS_PROVIDER);
                    mockLoc.setLatitude(currentLat);
                    mockLoc.setLongitude(currentLng);
                    mockLoc.setAltitude(5.0);
                    mockLoc.setTime(System.currentTimeMillis());
                    mockLoc.setAccuracy(1.0f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        mockLoc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                    }

                    locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLoc);
                } catch (Exception e) {
                    setupTestProvider(); // Re-add provider if dropped
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
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
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
