package com.autonavi.companion;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class DiagnosticPackage {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_IMPORT_EVENTS = 1000;

    private DiagnosticPackage() {
    }

    static ExportResult export(Context context, ArrayList<BroadcastEvent> events) throws Exception {
        if (events == null) {
            events = new ArrayList<>();
        }
        File dir = resolveWritableLogDir(context);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        File out = new File(dir, "amap_companion_diag_" + stamp + ".acdiag.zip");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out));
        try {
            writeEntry(zip, "manifest.json", manifestJson(context, events.size()).toString(2));
            writeEntry(zip, "environment.json", environmentJson(context).toString(2));
            writeEntry(zip, "prefs.json", prefsJson(context).toString(2));
            writeEntry(zip, "events.jsonl", eventsJsonl(events));
            writeEntry(zip, "logcat.txt", LogCollector.collectLogcat(context));
        } finally {
            zip.close();
        }
        return new ExportResult(out, !isPublicLogFile(out), events.size());
    }

    static ArrayList<BroadcastEvent> importEvents(Context context, Uri uri) throws Exception {
        ArrayList<BroadcastEvent> events = new ArrayList<>();
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IllegalStateException("无法打开文件");
        }
        ZipInputStream zip = new ZipInputStream(input);
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String name = entry.getName();
                if ("events.jsonl".equals(name) || name.endsWith("/events.jsonl")) {
                    String text = readCurrentEntry(zip);
                    parseEventsJsonl(text, events);
                    break;
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return events;
    }

    private static JSONObject manifestJson(Context context, int eventCount) throws Exception {
        JSONObject object = new JSONObject();
        object.put("type", "amap_companion_diagnostic");
        object.put("formatVersion", 1);
        object.put("exportedAtMs", System.currentTimeMillis());
        object.put("packageName", context.getPackageName());
        object.put("versionName", versionName(context));
        object.put("versionCode", versionCode(context));
        object.put("eventCount", eventCount);
        return object;
    }

    private static JSONObject environmentJson(Context context) throws Exception {
        JSONObject object = new JSONObject();
        object.put("packageName", context.getPackageName());
        object.put("versionName", versionName(context));
        object.put("versionCode", versionCode(context));
        object.put("androidRelease", Build.VERSION.RELEASE);
        object.put("sdkInt", Build.VERSION.SDK_INT);
        object.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        object.put("targetPackage", AppPrefs.getTargetPackage(context));
        object.put("overlayPermission", Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(context));
        object.put("usageStatsAccess", AppPrefs.hasUsageStatsAccess(context));
        object.put("publicLogDirWritable", LogCollector.canWritePublicLogDir(context));
        object.put("mainOverlayEnabled", AppPrefs.isMainOverlayEnabled(context));
        object.put("clusterMirrorEnabled", AppPrefs.isClusterMirrorEnabled(context));
        object.put("showMainWhenTargetForeground", AppPrefs.isShowMainWhenTargetForegroundEnabled(context));
        try {
            object.put("pluginCount", PluginManager.installedPlugins(context).size());
        } catch (Throwable ignored) {
            object.put("pluginCount", -1);
        }
        return object;
    }

    private static JSONObject prefsJson(Context context) throws Exception {
        JSONObject object = new JSONObject();
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Set) {
                JSONArray array = new JSONArray();
                for (Object item : (Set<?>) value) {
                    array.put(item == null ? JSONObject.NULL : String.valueOf(item));
                }
                object.put(entry.getKey(), array);
            } else if (value == null) {
                object.put(entry.getKey(), JSONObject.NULL);
            } else {
                object.put(entry.getKey(), value);
            }
        }
        return object;
    }

    private static String eventsJsonl(ArrayList<BroadcastEvent> events) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (BroadcastEvent event : events) {
            sb.append(event.toJson().toString()).append('\n');
        }
        return sb.toString();
    }

    private static void parseEventsJsonl(String text, ArrayList<BroadcastEvent> events) throws Exception {
        if (text == null) {
            return;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() == 0) {
                continue;
            }
            BroadcastEvent event = BroadcastEvent.fromJson(trimmed);
            if (event != null) {
                events.add(event);
                if (events.size() >= MAX_IMPORT_EVENTS) {
                    return;
                }
            }
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        OutputStreamWriter writer = new OutputStreamWriter(zip, UTF_8);
        writer.write(text == null ? "" : text);
        writer.flush();
        zip.closeEntry();
    }

    private static String readCurrentEntry(ZipInputStream zip) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private static File resolveWritableLogDir(Context context) throws Exception {
        File primary = new File(Environment.getExternalStorageDirectory(), LogCollector.PUBLIC_LOG_DIR);
        if (ensureWritableDir(primary)) {
            return primary;
        }
        File fallback = context.getExternalFilesDir("logs");
        if (fallback == null) {
            fallback = new File(context.getCacheDir(), "logs");
        }
        if (ensureWritableDir(fallback)) {
            return fallback;
        }
        throw new IllegalStateException("没有可写入的诊断目录");
    }

    private static boolean ensureWritableDir(File dir) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File probe = new File(dir, ".diag_write_test");
            FileOutputStream out = new FileOutputStream(probe);
            try {
                out.write(1);
            } finally {
                out.close();
            }
            if (probe.exists()) {
                probe.delete();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPublicLogFile(File file) {
        File publicDir = new File(Environment.getExternalStorageDirectory(), LogCollector.PUBLIC_LOG_DIR);
        try {
            return file.getAbsolutePath().startsWith(publicDir.getAbsolutePath());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static long versionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    static final class ExportResult {
        final File file;
        final boolean fallback;
        final int eventCount;

        ExportResult(File file, boolean fallback, int eventCount) {
            this.file = file;
            this.fallback = fallback;
            this.eventCount = eventCount;
        }
    }
}
