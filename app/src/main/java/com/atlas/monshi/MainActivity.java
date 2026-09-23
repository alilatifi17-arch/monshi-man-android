package com.atlas.monshi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 2001;
    private static final int REQ_RECORD_AUDIO = 2002;
    private static final int REQ_CREATE_BACKUP = 3001;
    private static final int REQ_OPEN_BACKUP = 3002;

    private WebView webView;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private String recordingId;
    private String pendingRecordingId;
    private String pendingBackupJson;
    private String pendingBackupName;
    private boolean pageReady = false;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(15, 23, 42));
        NotificationUtil.ensureChannels(this);

        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.addJavascriptInterface(new NativeBridge(), "AndroidNative");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                syncEventsToWeb();
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
        AlarmScheduler.scheduleSummary(this, ReminderStore.getSummaryTime(this));
    }

    @Override protected void onResume() {
        super.onResume();
        if (pageReady) syncEventsToWeb();
    }

    @Override protected void onDestroy() {
        stopPlayer();
        stopRecorderQuietly(false);
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private void syncEventsToWeb() {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript("window.syncNativeEvents && window.syncNativeEvents();", null);
        });
    }

    private File voiceFile(String id) {
        File dir = new File(getFilesDir(), "voice");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".m4a");
    }

    private void startRecordingNow(String id) {
        try {
            stopRecorderQuietly(false);
            recordingId = id;
            File out = voiceFile(id);
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(out.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            recorder = null;
            callbackRecording(id, false);
            Toast.makeText(this, "ضبط صدا شروع نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecorderQuietly(boolean callback) {
        String id = recordingId;
        boolean ok = false;
        if (recorder != null) {
            try { recorder.stop(); ok = true; } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
        recordingId = null;
        if (callback && id != null) callbackRecording(id, ok && voiceFile(id).exists());
    }

    private void callbackRecording(String id, boolean ok) {
        if (webView == null) return;
        String safe = JSONObject.quote(id == null ? "" : id);
        runOnUiThread(() -> webView.evaluateJavascript("window.onNativeRecordingStopped && window.onNativeRecordingStopped(" + safe + "," + ok + ");", null));
    }

    private void playRecordingNow(String id) {
        try {
            stopPlayer();
            File f = voiceFile(id);
            if (!f.exists()) {
                Toast.makeText(this, "فایل صوتی پیدا نشد", Toast.LENGTH_SHORT).show();
                return;
            }
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(mp -> stopPlayer());
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopPlayer();
            Toast.makeText(this, "پخش صدا انجام نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void requestNotificationPermissionNative() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void requestExactAlarmPermissionNative() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        }
    }

    private void testNotificationNative() {
        requestNotificationPermissionNative();
        NotificationUtil.ensureChannels(this);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 909090, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, NotificationUtil.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("منشی من ⏰")
                .setContentText("هشدار آزمایشی با موفقیت ارسال شد.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(909091, n);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingRecordingId != null) {
                String id = pendingRecordingId;
                pendingRecordingId = null;
                startRecordingNow(id);
            } else {
                String id = pendingRecordingId;
                pendingRecordingId = null;
                callbackRecording(id, false);
                Toast.makeText(this, "اجازه میکروفن داده نشد", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_CREATE_BACKUP && pendingBackupJson != null) {
                try (OutputStream raw = getContentResolver().openOutputStream(uri, "w");
                     ZipOutputStream zip = raw == null ? null : new ZipOutputStream(raw)) {
                    if (zip == null) throw new IllegalStateException("No output stream");
                    zip.putNextEntry(new ZipEntry("data.json"));
                    zip.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                    File voiceDir = new File(getFilesDir(), "voice");
                    File[] voices = voiceDir.listFiles();
                    if (voices != null) {
                        byte[] buf = new byte[8192];
                        for (File f : voices) {
                            if (!f.isFile()) continue;
                            zip.putNextEntry(new ZipEntry("voice/" + f.getName()));
                            try (InputStream in = new java.io.FileInputStream(f)) {
                                int n;
                                while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
                            }
                            zip.closeEntry();
                        }
                    }
                }
                pendingBackupJson = null;
                Toast.makeText(this, "پشتیبان کامل ذخیره شد", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_OPEN_BACKUP) {
                String dataJson = null;
                File voiceDir = new File(getFilesDir(), "voice");
                if (!voiceDir.exists()) voiceDir.mkdirs();
                try (InputStream raw = getContentResolver().openInputStream(uri);
                     ZipInputStream zip = raw == null ? null : new ZipInputStream(raw)) {
                    if (zip == null) throw new IllegalStateException("No input stream");
                    byte[] buf = new byte[8192];
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName();
                        if ("data.json".equals(name)) {
                            java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
                            int n;
                            while ((n = zip.read(buf)) > 0) bout.write(buf, 0, n);
                            dataJson = bout.toString(StandardCharsets.UTF_8.name());
                        } else if (name.startsWith("voice/") && !entry.isDirectory()) {
                            String base = new File(name).getName();
                            if (!base.matches("[a-zA-Z0-9_-]+\\.m4a")) { zip.closeEntry(); continue; }
                            File outFile = new File(voiceDir, base);
                            try (OutputStream out = new java.io.FileOutputStream(outFile)) {
                                int n;
                                while ((n = zip.read(buf)) > 0) out.write(buf, 0, n);
                            }
                        }
                        zip.closeEntry();
                    }
                }
                if (dataJson == null) throw new IllegalArgumentException("Backup data.json missing");
                String quoted = JSONObject.quote(dataJson);
                webView.evaluateJavascript("window.receiveNativeBackup && window.receiveNativeBackup(" + quoted + ");", null);
                Toast.makeText(this, "پشتیبان کامل بازیابی شد", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "عملیات فایل انجام نشد", Toast.LENGTH_SHORT).show();
        }
    }

    public class NativeBridge {
        @JavascriptInterface public void scheduleReminder(String id, String title, String note, long atMillis, String repeat, int hours, String weekdaysCsv) {
            Reminder r = new Reminder();
            r.id = id;
            r.title = title == null ? "" : title;
            r.note = note == null ? "" : note;
            r.nextAt = atMillis;
            r.repeat = repeat == null ? "once" : repeat;
            r.hours = Math.max(1, hours);
            if (weekdaysCsv != null && !weekdaysCsv.trim().isEmpty()) {
                for (String x : weekdaysCsv.split(",")) {
                    try { r.weekdays.add(Integer.parseInt(x.trim())); } catch (Exception ignored) {}
                }
            }
            r.active = true;
            ReminderStore.save(MainActivity.this, r);
            AlarmScheduler.schedule(MainActivity.this, r);
        }

        @JavascriptInterface public void cancelReminder(String id) {
            AlarmScheduler.cancel(MainActivity.this, id);
            Reminder r = ReminderStore.get(MainActivity.this, id);
            if (r != null) { r.active = false; ReminderStore.save(MainActivity.this, r); }
        }

        @JavascriptInterface public void cancelAllReminders() {
            for (Reminder r : ReminderStore.all(MainActivity.this)) {
                AlarmScheduler.cancel(MainActivity.this, r.id);
                ReminderStore.delete(MainActivity.this, r.id);
            }
        }

        @JavascriptInterface public void requestNotificationPermission() {
            runOnUiThread(MainActivity.this::requestNotificationPermissionNative);
        }

        @JavascriptInterface public void requestExactAlarmPermission() {
            runOnUiThread(MainActivity.this::requestExactAlarmPermissionNative);
        }

        @JavascriptInterface public boolean canScheduleExactAlarms() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            return am.canScheduleExactAlarms();
        }

        @JavascriptInterface public void startRecording(String id) {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingRecordingId = id;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
                } else startRecordingNow(id);
            });
        }

        @JavascriptInterface public void stopRecording() {
            runOnUiThread(() -> stopRecorderQuietly(true));
        }

        @JavascriptInterface public void cancelRecording() {
            runOnUiThread(() -> {
                String id = recordingId;
                stopRecorderQuietly(false);
                if (id != null) {
                    File f = voiceFile(id);
                    if (f.exists()) f.delete();
                }
            });
        }

        @JavascriptInterface public void playRecording(String id) {
            runOnUiThread(() -> playRecordingNow(id));
        }

        @JavascriptInterface public void deleteRecording(String id) {
            if (id == null) return;
            File f = voiceFile(id);
            if (f.exists()) f.delete();
        }

        @JavascriptInterface public String drainEvents() {
            return ReminderStore.drainEvents(MainActivity.this);
        }

        @JavascriptInterface public void updateStats(int total, int done, int late, String date) {
            ReminderStore.setStats(MainActivity.this, total, done, late, date);
        }

        @JavascriptInterface public void scheduleSummary(String hhmm) {
            AlarmScheduler.scheduleSummary(MainActivity.this, hhmm);
        }

        @JavascriptInterface public void testNotification() {
            runOnUiThread(MainActivity.this::testNotificationNative);
        }

        @JavascriptInterface public void saveBackup(String json, String filename) {
            pendingBackupJson = json;
            pendingBackupName = filename == null || filename.isEmpty() ? "monshi-man-backup.json" : filename;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/zip");
                i.putExtra(Intent.EXTRA_TITLE, pendingBackupName);
                startActivityForResult(i, REQ_CREATE_BACKUP);
            });
        }

        @JavascriptInterface public void openBackupPicker() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/zip");
                startActivityForResult(i, REQ_OPEN_BACKUP);
            });
        }
    }
}
