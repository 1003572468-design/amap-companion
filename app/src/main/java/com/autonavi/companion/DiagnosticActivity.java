package com.autonavi.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class DiagnosticActivity extends Activity {
    private static final int REQUEST_IMPORT_DIAGNOSTIC = 7201;
    private static final int MAX_VISIBLE_EVENTS = 80;

    private TextView statusText;
    private TextView recentTitle;
    private TextView importedTitle;
    private LinearLayout checksContainer;
    private LinearLayout recentContainer;
    private LinearLayout importedSection;
    private LinearLayout importedContainer;
    private final ArrayList<BroadcastEvent> importedEvents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View content = buildContent();
        FontManager.applyToViewTree(this, content);
        setContentView(content);
        refreshAll();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_DIAGNOSTIC
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {
            importDiagnosticPackage(data.getData());
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF3F6FA);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout hero = card(0xFF111827);
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("诊断中心");
        title.setTextSize(27f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        hero.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("检查运行环境，记录最近高德广播，导出或导入诊断包并回放问题现场。");
        subtitle.setTextSize(14f);
        subtitle.setTextColor(0xFFD1D5DB);
        subtitle.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.setMargins(0, dp(8), 0, 0);
        hero.addView(subtitle, subtitleLp);

        statusText = new TextView(this);
        statusText.setTextSize(13f);
        statusText.setTextColor(0xFFA7F3D0);
        statusText.setTextIsSelectable(true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(10), 0, 0);
        hero.addView(statusText, statusLp);

        LinearLayout actions = card(Color.WHITE);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.setMargins(0, dp(14), 0, 0);
        root.addView(actions, actionsLp);
        addButtonPair(actions,
                button("刷新", v -> refreshAll(), 0xFF2563EB),
                button("启动服务", v -> startCompanionService(), 0xFF0F766E));
        addButtonPair(actions,
                button("导出诊断包", v -> confirmExport(), 0xFF4F46E5),
                button("导入诊断包", v -> openImportPicker(), 0xFF7C3AED));
        addButtonPair(actions,
                button("清空历史", v -> confirmClearHistory(), 0xFFB45309),
                button("返回", v -> finish(), 0xFF475569));

        TextView exportHint = new TextView(this);
        exportHint.setText("导出的诊断包会包含最近广播原始字段、偏好设置和日志。分享前请确认其中可能包含道路、目的地或位置相关信息。");
        exportHint.setTextSize(12f);
        exportHint.setTextColor(0xFF64748B);
        exportHint.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(8), 0, 0);
        actions.addView(exportHint, hintLp);

        LinearLayout checks = card(Color.WHITE);
        LinearLayout.LayoutParams checksLp = new LinearLayout.LayoutParams(-1, -2);
        checksLp.setMargins(0, dp(14), 0, 0);
        root.addView(checks, checksLp);
        addSectionTitle(checks, "兼容性检查");
        checksContainer = new LinearLayout(this);
        checksContainer.setOrientation(LinearLayout.VERTICAL);
        checks.addView(checksContainer, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout recent = card(Color.WHITE);
        LinearLayout.LayoutParams recentLp = new LinearLayout.LayoutParams(-1, -2);
        recentLp.setMargins(0, dp(14), 0, 0);
        root.addView(recent, recentLp);
        recentTitle = addSectionTitle(recent, "最近广播");
        recentContainer = new LinearLayout(this);
        recentContainer.setOrientation(LinearLayout.VERTICAL);
        recent.addView(recentContainer, new LinearLayout.LayoutParams(-1, -2));

        importedSection = card(Color.WHITE);
        LinearLayout.LayoutParams importedLp = new LinearLayout.LayoutParams(-1, -2);
        importedLp.setMargins(0, dp(14), 0, 0);
        root.addView(importedSection, importedLp);
        importedTitle = addSectionTitle(importedSection, "已导入诊断包");
        importedContainer = new LinearLayout(this);
        importedContainer.setOrientation(LinearLayout.VERTICAL);
        importedSection.addView(importedContainer, new LinearLayout.LayoutParams(-1, -2));
        importedSection.setVisibility(View.GONE);

        return scroll;
    }

    private void refreshAll() {
        renderChecks();
        renderRecentEvents();
        renderImportedEvents();
        statusText.setText("缓存广播 " + BroadcastEventRecorder.size() + " 条，最多保留 200 条；历史不会重复导出或自动写盘。");
    }

    private void renderChecks() {
        checksContainer.removeAllViews();
        ArrayList<DiagnosticChecks.Item> items = DiagnosticChecks.collect(this);
        for (DiagnosticChecks.Item item : items) {
            checksContainer.addView(checkRow(item), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private View checkRow(DiagnosticChecks.Item item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setClickable(true);
        row.setOnClickListener(v -> showCheckHelp(item));

        TextView badge = new TextView(this);
        badge.setText(item.ok ? "OK" : "!");
        badge.setTextSize(12f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(item.ok ? 0xFF065F46 : 0xFF92400E);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(item.ok ? 0xFFD1FAE5 : 0xFFFEF3C7);
        badgeBg.setCornerRadius(dp(999));
        badge.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(42), dp(28));
        badgeLp.setMargins(0, 0, dp(12), 0);
        row.addView(badge, badgeLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = new TextView(this);
        name.setText(item.name);
        name.setTextSize(15f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(0xFF111827);
        textCol.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView detail = new TextView(this);
        detail.setText(item.detail);
        detail.setTextSize(12f);
        detail.setTextColor(0xFF64748B);
        detail.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.setMargins(0, dp(3), 0, 0);
        textCol.addView(detail, detailLp);

        TextView arrow = new TextView(this);
        arrow.setText(">");
        arrow.setTextSize(18f);
        arrow.setTypeface(Typeface.DEFAULT_BOLD);
        arrow.setTextColor(0xFF94A3B8);
        arrow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(28), dp(34));
        arrowLp.setMargins(dp(8), 0, 0, 0);
        row.addView(arrow, arrowLp);
        return row;
    }

    private void showCheckHelp(DiagnosticChecks.Item item) {
        StringBuilder message = new StringBuilder();
        message.append(item.detail);
        if (!TextUtils.isEmpty(item.solution)) {
            message.append("\n\n解决方案：\n").append(item.solution);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setMessage(message.toString())
                .setNegativeButton("关闭", null);
        if (!TextUtils.isEmpty(item.actionLabel)) {
            builder.setPositiveButton(item.actionLabel, (dialog, which) -> performCheckAction(item));
        }
        builder.show();
    }

    private void performCheckAction(DiagnosticChecks.Item item) {
        if (DiagnosticChecks.ID_OVERLAY.equals(item.id)) {
            openOverlaySettings();
            return;
        }
        if (DiagnosticChecks.ID_FOREGROUND.equals(item.id)) {
            openUsageAccessSettings();
            return;
        }
        if (DiagnosticChecks.ID_SERVICE.equals(item.id)) {
            startCompanionService();
            return;
        }
        if (DiagnosticChecks.ID_LOG_DIR.equals(item.id)) {
            openStorageSettings();
            return;
        }
        if (DiagnosticChecks.ID_TARGET.equals(item.id)) {
            if (!openTargetApp()) {
                openMainSettings();
            }
            return;
        }
        if (DiagnosticChecks.ID_DISPLAY.equals(item.id)
                || DiagnosticChecks.ID_PLUGIN.equals(item.id)) {
            openMainSettings();
            return;
        }
        if (DiagnosticChecks.ID_BROADCAST.equals(item.id)
                || DiagnosticChecks.ID_TRAFFIC_LIGHT.equals(item.id)
                || DiagnosticChecks.ID_LANE.equals(item.id)) {
            startCompanionService();
            openTargetApp();
        }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "当前系统不需要单独授权悬浮窗", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivitySafely(intent, "无法打开悬浮窗授权页");
    }

    private void openUsageAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivitySafely(intent, "无法打开用量访问设置");
    }

    private void openStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent appIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            appIntent.setData(Uri.parse("package:" + getPackageName()));
            if (startActivitySafely(appIntent, null)) {
                return;
            }
            Intent allFilesIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            if (startActivitySafely(allFilesIntent, null)) {
                return;
            }
        }
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        details.setData(Uri.parse("package:" + getPackageName()));
        startActivitySafely(details, "无法打开应用设置");
    }

    private void openMainSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true);
        startActivity(intent);
    }

    private boolean openTargetApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(AppPrefs.getTargetPackage(this));
        if (launch == null) {
            Toast.makeText(this, "无法打开目标应用，请回首页检查包名", Toast.LENGTH_LONG).show();
            return false;
        }
        startActivitySafely(launch, "无法打开目标应用");
        return true;
    }

    private boolean startActivitySafely(Intent intent, String errorMessage) {
        try {
            startActivity(intent);
            return true;
        } catch (Throwable t) {
            if (!TextUtils.isEmpty(errorMessage)) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    }

    private void renderRecentEvents() {
        ArrayList<BroadcastEvent> events = BroadcastEventRecorder.recentEvents();
        recentTitle.setText("最近广播（" + events.size() + "/200）");
        renderEventList(recentContainer, events, false);
    }

    private void renderImportedEvents() {
        importedSection.setVisibility(importedEvents.isEmpty() ? View.GONE : View.VISIBLE);
        importedTitle.setText("已导入诊断包（" + importedEvents.size() + "）");
        ArrayList<BroadcastEvent> newestFirst = new ArrayList<>(importedEvents);
        Collections.reverse(newestFirst);
        renderEventList(importedContainer, newestFirst, true);
    }

    private void renderEventList(LinearLayout container, ArrayList<BroadcastEvent> events, boolean imported) {
        container.removeAllViews();
        if (events.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(imported ? "还没有导入诊断包。" : "还没有捕获到广播。启动服务后进入导航或巡航，页面会开始记录。");
            empty.setTextSize(13f);
            empty.setTextColor(0xFF64748B);
            empty.setPadding(0, dp(10), 0, dp(6));
            container.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        int count = Math.min(events.size(), MAX_VISIBLE_EVENTS);
        for (int i = 0; i < count; i++) {
            BroadcastEvent event = events.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(8), 0, 0);
            container.addView(eventRow(event), lp);
        }
        if (events.size() > count) {
            TextView more = new TextView(this);
            more.setText("仅显示最近 " + count + " 条，导出会包含当前缓存的全部事件。");
            more.setTextSize(12f);
            more.setTextColor(0xFF64748B);
            more.setPadding(0, dp(10), 0, 0);
            container.addView(more, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private View eventRow(BroadcastEvent event) {
        BroadcastProtocol.Info protocol = BroadcastProtocol.describe(event);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF8FAFC);
        bg.setStroke(dp(1), 0xFFE2E8F0);
        bg.setCornerRadius(dp(8));
        row.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(this);
        title.setText(formatTime(event.timestampMs) + "  " + event.displayTitle());
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF111827);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView action = new TextView(this);
        String actionText = TextUtils.isEmpty(event.action) ? "action=unknown" : event.action;
        if (event.summary.keyType >= 0) {
            actionText += "  KEY_TYPE=" + event.summary.keyType;
        }
        action.setText(actionText);
        action.setTextSize(11f);
        action.setTextColor(0xFF64748B);
        action.setSingleLine(true);
        action.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
        actionLp.setMargins(0, dp(3), 0, 0);
        titleCol.addView(action, actionLp);

        TextView protocolText = new TextView(this);
        protocolText.setText("协议：" + protocol.shortLine());
        protocolText.setTextSize(11f);
        protocolText.setTextColor(0xFF0F766E);
        protocolText.setSingleLine(true);
        protocolText.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams protocolLp = new LinearLayout.LayoutParams(-1, -2);
        protocolLp.setMargins(0, dp(3), 0, 0);
        titleCol.addView(protocolText, protocolLp);

        Button replay = smallButton("回放");
        replay.setOnClickListener(v -> replayEvent(event));
        LinearLayout.LayoutParams replayLp = new LinearLayout.LayoutParams(dp(72), dp(38));
        replayLp.setMargins(dp(10), 0, 0, 0);
        header.addView(replay, replayLp);

        String extras = event.describeExtras(520);
        StringBuilder detailText = new StringBuilder();
        if (!TextUtils.isEmpty(extras)) {
            detailText.append(extras);
        }
        if (detailText.length() > 0) {
            TextView detail = new TextView(this);
            detail.setText(detailText.toString());
            detail.setTextSize(11f);
            detail.setTextColor(0xFF475569);
            detail.setTextIsSelectable(true);
            detail.setLineSpacing(dp(2), 1.0f);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.setMargins(0, dp(8), 0, 0);
            row.addView(detail, detailLp);
        }
        return row;
    }

    private void confirmExport() {
        new AlertDialog.Builder(this)
                .setTitle("导出诊断包")
                .setMessage("诊断包会包含最近广播原始字段、偏好设置和日志，可能带有道路、目的地或位置相关信息。确认导出？")
                .setPositiveButton("导出", (dialog, which) -> exportDiagnosticPackage())
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportDiagnosticPackage() {
        statusText.setText("正在导出诊断包...");
        new Thread(() -> {
            try {
                DiagnosticPackage.ExportResult result = DiagnosticPackage.export(
                        DiagnosticActivity.this,
                        BroadcastEventRecorder.eventsOldestFirst());
                runOnUiThread(() -> {
                    String path = result.file.getAbsolutePath();
                    statusText.setText("已导出 " + result.eventCount + " 条事件：\n" + path
                            + (result.fallback ? "\n公共目录不可写，已保存到应用私有目录。" : ""));
                    Toast.makeText(this, "诊断包已导出", Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    statusText.setText("导出失败：" + t.getMessage());
                    Toast.makeText(this, "导出失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "diagnostic-export").start();
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_DIAGNOSTIC);
        } catch (Throwable t) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            startActivityForResult(fallback, REQUEST_IMPORT_DIAGNOSTIC);
        }
    }

    private void importDiagnosticPackage(Uri uri) {
        statusText.setText("正在导入诊断包...");
        new Thread(() -> {
            try {
                ArrayList<BroadcastEvent> events = DiagnosticPackage.importEvents(this, uri);
                runOnUiThread(() -> {
                    importedEvents.clear();
                    importedEvents.addAll(events);
                    renderImportedEvents();
                    statusText.setText("已导入 " + events.size() + " 条事件，可选择单条回放。");
                    Toast.makeText(this, "导入完成", Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    statusText.setText("导入失败：" + t.getMessage());
                    Toast.makeText(this, "导入失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "diagnostic-import").start();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("清空历史")
                .setMessage("只会清空本次运行期间缓存的最近广播，不会删除已经导出的诊断包。")
                .setPositiveButton("清空", (dialog, which) -> {
                    BroadcastEventRecorder.clear();
                    refreshAll();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void replayEvent(BroadcastEvent event) {
        try {
            Intent serviceIntent = new Intent(this, OverlayService.class);
            serviceIntent.setAction(AppPrefs.ACTION_DIAGNOSTIC_REPLAY);
            serviceIntent.putExtra(AppPrefs.EXTRA_DIAGNOSTIC_EVENT_JSON, event.toJson().toString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "已发送回放广播", Toast.LENGTH_SHORT).show();
            statusText.setText("已回放：" + event.displayTitle()
                    + "\n协议：" + BroadcastProtocol.describe(event).shortLine());
        } catch (Throwable t) {
            Toast.makeText(this, "回放失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startCompanionService() {
        MainActivity.startOverlayService(this);
        Toast.makeText(this, "已请求启动伴侣服务", Toast.LENGTH_SHORT).show();
        statusText.postDelayed(this::refreshAll, 500L);
    }

    private TextView addSectionTitle(LinearLayout parent, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF111827);
        parent.addView(title, new LinearLayout.LayoutParams(-1, -2));
        return title;
    }

    private LinearLayout card(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        return card;
    }

    private Button button(String text, View.OnClickListener listener, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(46));
        button.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        button.setBackground(bg);
        button.setOnClickListener(listener);
        return button;
    }

    private Button smallButton(String text) {
        Button button = button(text, null, 0xFF334155);
        button.setTextSize(12f);
        button.setMinHeight(dp(36));
        return button;
    }

    private void addButtonPair(LinearLayout parent, Button left, Button right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, parent.getChildCount() == 0 ? 0 : dp(8), 0, 0);
        parent.addView(row, rowLp);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1f);
        leftLp.setMargins(0, 0, right == null ? 0 : dp(6), 0);
        row.addView(left, leftLp);
        if (right != null) {
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -2, 1f);
            rightLp.setMargins(dp(6), 0, 0, 0);
            row.addView(right, rightLp);
        }
    }

    private String formatTime(long timestampMs) {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(timestampMs));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
