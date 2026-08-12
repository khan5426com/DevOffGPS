package com.dev.off;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import rikka.shizuku.Shizuku;

public class MockService extends Service {

    private boolean isRunning = false;
    private double lat = 0.0, lon = 0.0;
    private Thread mockThread;
    private static final String CHANNEL_ID = "MockGPSChannel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            lat = intent.getDoubleExtra("lat", 0.0);
            lon = intent.getDoubleExtra("lon", 0.0);
        }

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DevOff GPS Active")
                .setContentText("Mocking Location: " + lat + ", " + lon)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();

        startForeground(1, notification);

        if (!isRunning) {
            isRunning = true;
            startMockingLoop();
        }

        return START_STICKY;
    }

    private void startMockingLoop() {
        mockThread = new Thread(() -> {
            while (isRunning) {
                try {
                    injectMockLocation(lat, lon);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        mockThread.start();
    }

    private void injectMockLocation(double latitude, double longitude) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Process p1 = Shizuku.newProcess(new String[]{"sh", "-c", "cmd location set-test-provider-enabled gps true"}, null, null);
                p1.waitFor();

                Process p2 = Shizuku.newProcess(new String[]{"sh", "-c", "cmd location set-test-provider-location gps " + latitude + " " + longitude}, null, null);
                p2.waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Mock GPS Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (mockThread != null) {
            mockThread.interrupt();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
