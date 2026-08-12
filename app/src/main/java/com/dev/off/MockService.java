package com.dev.off;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MockService extends Service {

    private static final String CHANNEL_ID = "DevOffGPS_Channel";
    private boolean isRunning = false;
    private double currentLat = 0.0;
    private double currentLng = 0.0;

    @Override
    public void onCreate() {
        super.onCreate();
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
                .setContentText(String.format(Locale.US, "Mocking via Shizuku: %.4f, %.4f", currentLat, currentLng))
                .setSmallIcon(R.drawable.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();

        startForeground(1001, notification);

        if (!isRunning) {
            isRunning = true;
            startShizukuMockLoop();
        }

        return START_STICKY;
    }

    private void startShizukuMockLoop() {
        new Thread(() -> {
            // 1. Shizuku Shell Commands se Test Provider init karein
            execShizukuCmd("cmd location add-test-provider gps");
            execShizukuCmd("cmd location set-test-provider-enabled gps true");

            // 2. Continuous location inject loop
            while (isRunning) {
                if (Shizuku.pingBinder()) {
                    String locCmd = String.format(Locale.US,
                            "cmd location set-test-provider-location gps --location %f,%f",
                            currentLat, currentLng);
                    execShizukuCmd(locCmd);
                }

                try {
                    Thread.sleep(1000); // Har 1 second mein location update hogi
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void execShizukuCmd(String command) {
        try {
            if (Shizuku.pingBinder()) {
                Process process = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
                process.waitFor();
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
        new Thread(() -> execShizukuCmd("cmd location remove-test-provider gps")).start();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
