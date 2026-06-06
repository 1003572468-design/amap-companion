package com.autonavi.companion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class LogCollector {
    static final String PUBLIC_LOG_DIR = "amap_companion/log";

    private LogCollector() {
    }

    static String collectLogcat(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("AMap Companion log report\n");
        sb.append("time=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date())).append('\n');
        sb.append("package=").append(context.getPackageName()).append('\n');
        sb.append("version=").append(currentVersionName(context)).append(" (").append(currentVersionCode(context)).append(")\n");
        sb.append("targetPackage=").append(AppPrefs.getTargetPackage(context)).append('\n');
        sb.append("android=").append(Build.VERSION.RELEASE).append(" sdk=").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("readLogsPermission=").append(hasPermission(context, Manifest.permission.READ_LOGS)).append('\n');
        sb.append("publicLogDirWritable=").append(canWritePublicLogDir(context)).append('\n');
        sb.append("preferredLogDir=/sdcard/").append(PUBLIC_LOG_DIR).append('\n');
        sb.append("note=Android may restrict third-party apps to their own logs only.\n\n");
        int lines = appendLogcatCommand(sb, "filtered", new String[]{
                "logcat", "-d", "-v", "time", "-t", "1000",
                "AmapCompanion:D", "AndroidRuntime:E", "System.err:W", "*:S"
        });
        if (lines == 0) {
            lines = appendLogcatCommand(sb, "recent", new String[]{
                    "logcat", "-d", "-v", "time", "-t", "300"
            });
        }
        if (lines == 0) {
            sb.append("\n(no logcat output; system may restrict log access or logs may be empty)\n");
        }
        return sb.toString();
    }

    static SaveResult saveLog(Context context, String text) throws Exception {
        File dir = resolveWritableLogDir(context);
        String name = "amap_companion_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date()) + ".txt";
        File out = new File(dir, name);
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
        return new SaveResult(out, !isPublicLogFile(out));
    }

    static boolean canWritePublicLogDir(Context context) {
        return ensureWritableDir(new File(Environment.getExternalStorageDirectory(), PUBLIC_LOG_DIR));
    }

    static boolean hasPermission(Context context, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static String storagePermissionSummary(Context context) {
        if (canWritePublicLogDir(context)) {
            return "/sdcard/" + PUBLIC_LOG_DIR + " 可写，保存日志会优先使用该目录";
        }
        return "/sdcard/" + PUBLIC_LOG_DIR + " 不可写，保存日志会自动回退到应用私有目录";
    }

    private static String currentVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static long currentVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int appendLogcatCommand(StringBuilder sb, String label, String[] command) {
        sb.append("---- logcat ").append(label).append(" ----\n");
        java.lang.Process process = null;
        int lines = 0;
        try {
            process = Runtime.getRuntime().exec(command);
            lines = appendStream(sb, process.getInputStream());
            int exit = process.waitFor();
            StringBuilder err = new StringBuilder();
            appendStream(err, process.getErrorStream());
            if (err.length() > 0) {
                sb.append("\n---- logcat stderr ----\n").append(err);
            }
            sb.append("\n---- logcat ").append(label).append(" exit=").append(exit).append(" lines=").append(lines).append(" ----\n");
        } catch (Throwable t) {
            sb.append("\nlogcat failed: ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append('\n');
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return lines;
    }

    private static int appendStream(StringBuilder sb, InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        int lines = 0;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
            lines++;
        }
        return lines;
    }

    private static File resolveWritableLogDir(Context context) throws Exception {
        File primary = new File(Environment.getExternalStorageDirectory(), PUBLIC_LOG_DIR);
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
        throw new IllegalStateException("no writable log dir");
    }

    private static boolean ensureWritableDir(File dir) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File probe = new File(dir, ".write_test");
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
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isPublicLogFile(File file) {
        File publicDir = new File(Environment.getExternalStorageDirectory(), PUBLIC_LOG_DIR);
        try {
            return file.getAbsolutePath().startsWith(publicDir.getAbsolutePath());
        } catch (Throwable t) {
            return false;
        }
    }

    static final class SaveResult {
        final File file;
        final boolean fallback;

        SaveResult(File file, boolean fallback) {
            this.file = file;
            this.fallback = fallback;
        }
    }
}
