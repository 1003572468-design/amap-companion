package com.autonavi.companion;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;

final class DiagnosticChecks {
    static final String ID_OVERLAY = "overlay";
    static final String ID_TARGET = "target";
    static final String ID_FOREGROUND = "foreground";
    static final String ID_SERVICE = "service";
    static final String ID_DISPLAY = "display";
    static final String ID_LOG_DIR = "log_dir";
    static final String ID_BROADCAST = "broadcast";
    static final String ID_TRAFFIC_LIGHT = "traffic_light";
    static final String ID_LANE = "lane";
    static final String ID_PLUGIN = "plugin";

    private DiagnosticChecks() {
    }

    static ArrayList<Item> collect(Context context) {
        ArrayList<Item> items = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        String targetPackage = AppPrefs.getTargetPackage(context);

        boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(context);
        items.add(new Item(ID_OVERLAY, "悬浮窗权限", overlayGranted,
                overlayGranted ? "已授权，可以显示主屏/副屏浮窗。" : "未授权，浮窗无法显示。",
                "授予“允许显示在其他应用上层”权限。没有该权限时服务可以接收广播，但界面不会浮出来。",
                overlayGranted ? "" : "打开授权页"));

        boolean targetInstalled = isPackageInstalled(pm, targetPackage);
        Intent launch = pm.getLaunchIntentForPackage(targetPackage);
        items.add(new Item(ID_TARGET, "目标应用", targetInstalled && launch != null,
                targetInstalled
                        ? (launch != null ? targetPackage + " 可打开。" : targetPackage + " 已安装，但没有桌面入口。")
                        : targetPackage + " 未安装或当前系统隐藏了包信息。",
                "确认首页选择的目标包名是否为当前车机上的高德/高德镜像包。若已安装但没有桌面入口，可以仍然保留它作为广播目标。",
                launch != null ? "打开目标应用" : "返回首页"));

        ArrayList<BroadcastEvent> recent = BroadcastEventRecorder.recentEvents();
        boolean hasForegroundBroadcast = hasForegroundBroadcast(recent);
        boolean usageAccess = AppPrefs.hasUsageStatsAccess(context);
        String foregroundDetail;
        boolean foregroundOk = hasForegroundBroadcast || usageAccess;
        if (hasForegroundBroadcast) {
            foregroundDetail = "已收到高德前后台状态广播，当前会优先使用广播识别。";
        } else if (usageAccess) {
            foregroundDetail = "还没收到前后台状态广播；用量访问权限已开启，可作为兜底识别。";
        } else {
            foregroundDetail = "还没收到前后台状态广播；可打开一次高德触发广播，或开启用量访问作为兜底。";
        }
        items.add(new Item(ID_FOREGROUND, "前后台广播识别", foregroundOk,
                foregroundDetail,
                "新版逻辑优先读取高德广播里的 APP_STATE_FOREGROUND / APP_STATE_BACKGROUND。用量访问只用于没收到该广播时的兜底。",
                hasForegroundBroadcast ? "" : "打开用量访问"));

        boolean serviceRunning = isOverlayServiceRunning(context);
        items.add(new Item(ID_SERVICE, "伴侣服务", serviceRunning,
                serviceRunning ? "OverlayService 正在运行。" : "服务未运行，可在诊断中心启动或回放时临时启动。",
                "启动伴侣服务后才会持续监听高德广播、维护悬浮窗和诊断历史。",
                serviceRunning ? "" : "启动服务"));

        boolean hasVisualEntry = AppPrefs.isMainOverlayEnabled(context)
                || AppPrefs.isClusterMirrorEnabled(context)
                || AppPrefs.isShowMainWhenTargetForegroundEnabled(context);
        items.add(new Item(ID_DISPLAY, "显示入口", hasVisualEntry,
                hasVisualEntry ? "至少启用了一个显示入口。"
                        : "主屏、副屏和高德广播自动显示均未启用，普通广播不会可视化。",
                "返回首页启用主屏悬浮窗、副屏悬浮窗，或“高德广播自动显示”。",
                hasVisualEntry ? "" : "返回首页"));

        boolean logWritable = LogCollector.canWritePublicLogDir(context);
        items.add(new Item(ID_LOG_DIR, "日志目录", logWritable,
                logWritable ? "/sdcard/" + LogCollector.PUBLIC_LOG_DIR + " 可写。"
                        : "公共日志目录不可写，会回退到应用私有目录。",
                "导出诊断包优先写入公共日志目录；如果车机系统限制外部存储，可授予文件访问权限，或接受回退到应用私有目录。",
                logWritable ? "" : "打开存储设置"));

        BroadcastEvent latest = recent.isEmpty() ? null : recent.get(0);
        items.add(new Item(ID_BROADCAST, "广播接收", latest != null,
                latest == null ? "尚未记录到高德运行时广播。"
                        : "最近 " + ageLabel(System.currentTimeMillis() - latest.timestampMs)
                        + " 收到：" + latest.summary.oneLine(),
                "启动伴侣服务后打开目标高德，进入导航或巡航。诊断历史只记录高德运行时广播，不记录设置变更。",
                latest == null ? "启动并打开高德" : ""));

        boolean hasTrafficLight = false;
        boolean hasLane = false;
        for (BroadcastEvent event : recent) {
            hasTrafficLight |= event.summary.hasTrafficLightPayload;
            hasLane |= event.summary.hasLanePayload;
        }
        items.add(new Item(ID_TRAFFIC_LIGHT, "红绿灯协议", hasTrafficLight,
                hasTrafficLight ? "最近广播中包含红绿灯数据。"
                        : "最近没有红绿灯数据；需要高德版本/自定义地图支持并处于巡航或导航。",
                "进入巡航或导航并经过支持红绿灯倒计时的路口。若使用自定义地图，需要确认包装层已经转发红绿灯字段。",
                hasTrafficLight ? "" : "启动并打开高德"));
        items.add(new Item(ID_LANE, "车道协议", hasLane,
                hasLane ? "最近广播中包含车道数据。"
                        : "最近没有车道数据；该数据通常只在导航路口附近出现。",
                "开始导航后接近复杂路口，等待高德下发车道线广播。也可以导入别人提供的诊断包回放确认 UI 是否正常。",
                hasLane ? "" : "启动并打开高德"));

        int pluginCount = 0;
        try {
            pluginCount = PluginManager.installedPlugins(context).size();
        } catch (Throwable ignored) {
        }
        items.add(new Item(ID_PLUGIN, "插件", true,
                pluginCount > 0 ? "已安装 " + pluginCount + " 个插件。" : "未安装插件，不影响基础功能。",
                "插件只影响字体、图标或 UI 扩展；基础广播解析、诊断导出和回放不依赖插件。",
                "返回首页"));

        return items;
    }

    private static boolean hasForegroundBroadcast(ArrayList<BroadcastEvent> events) {
        for (BroadcastEvent event : events) {
            int keyType = event.summary.keyType;
            int state = event.summary.state;
            if (keyType == AmapConstants.KEY_TYPE_NAVIGATION_STATE
                    && (state == AmapConstants.APP_STATE_FOREGROUND
                    || state == AmapConstants.APP_STATE_BACKGROUND)) {
                return true;
            }
        }
        return false;
    }

    static String ageLabel(long ageMs) {
        if (ageMs < 0) {
            return "刚刚";
        }
        long seconds = ageMs / 1000L;
        if (seconds < 60) {
            return seconds + " 秒前";
        }
        long minutes = seconds / 60L;
        if (minutes < 60) {
            return minutes + " 分钟前";
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return hours + " 小时前";
        }
        return (hours / 24L) + " 天前";
    }

    private static boolean isPackageInstalled(PackageManager pm, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        try {
            PackageInfo ignored = pm.getPackageInfo(packageName, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isOverlayServiceRunning(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return false;
            }
            for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(80)) {
                if (info.service != null
                        && context.getPackageName().equals(info.service.getPackageName())
                        && OverlayService.class.getName().equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static final class Item {
        final String id;
        final String name;
        final boolean ok;
        final String detail;
        final String solution;
        final String actionLabel;

        Item(String id, String name, boolean ok, String detail,
             String solution, String actionLabel) {
            this.id = id;
            this.name = name;
            this.ok = ok;
            this.detail = detail;
            this.solution = solution;
            this.actionLabel = actionLabel == null ? "" : actionLabel;
        }
    }
}
