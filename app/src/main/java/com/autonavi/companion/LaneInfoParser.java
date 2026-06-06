package com.autonavi.companion;

import android.os.Bundle;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Parses AMap lane guidance broadcasts into a UI-independent result.
 *
 * This class is intentionally public and small so forks can extend lane protocol
 * support without touching OverlayService rendering code.
 */
public final class LaneInfoParser {

    public static final String[] DRIVE_WAY_JSON_KEYS = {
            "EXTRA_DRIVE_WAY", "drive_way_info_json", "driveWayInfo"
    };

    public static final String[] LANE_ICON_KEYS = {
            "drive_way_lane_Back_icon", "trafficLaneType", "trafficLaneIcon",
            "laneBackInfo", "laneSelectInfo", "frontLane", "backLane",
            "FRONT_LANE", "BACK_LANE"
    };

    public static final String[] LANE_ADVISED_KEYS = {
            "trafficLaneAdvised", "recommend", "laneRecommend", "LANE_RECOMMEND"
    };

    private LaneInfoParser() {
    }

    public static LaneInfo parse(Bundle extras) {
        if (extras == null) {
            return LaneInfo.notHandled();
        }

        String driveWayJson = valueString(extras, DRIVE_WAY_JSON_KEYS);
        if (!TextUtils.isEmpty(driveWayJson)) {
            LaneInfo jsonLaneInfo = parseDriveWayJson(driveWayJson);
            if (jsonLaneInfo.isHandled()) {
                return jsonLaneInfo;
            }
        }

        int keyType = intValue(extras, "KEY_TYPE", -1);
        if (keyType != AmapConstants.KEY_TYPE_LANE_INFO && !hasAny(extras, LANE_ICON_KEYS)) {
            return LaneInfo.notHandled();
        }

        int[] lanes = intArrayValue(extras, LANE_ICON_KEYS);
        boolean[] advised = booleanArrayValue(extras, LANE_ADVISED_KEYS);
        if (lanes == null || lanes.length == 0) {
            return LaneInfo.notHandled();
        }
        return LaneInfo.data(lanes, advised);
    }

    public static LaneInfo parseDriveWayJson(String json) {
        if (TextUtils.isEmpty(json)) {
            return LaneInfo.notHandled();
        }
        try {
            JSONObject object = new JSONObject(json);
            boolean enabled = object.optBoolean("drive_way_enabled");
            if (!enabled) {
                return LaneInfo.clear();
            }
            JSONArray info = object.optJSONArray("drive_way_info");
            if (info == null || info.length() == 0) {
                return LaneInfo.clear();
            }
            int count = Math.min(info.length(), 8);
            int[] lanes = new int[count];
            boolean[] advised = new boolean[count];
            boolean hasAdvised = false;
            for (int i = 0; i < count; i++) {
                JSONObject item = info.optJSONObject(i);
                if (item == null) {
                    lanes[i] = 1;
                    advised[i] = true;
                    continue;
                }
                lanes[i] = item.optInt("drive_way_lane_Back_icon",
                        item.optInt("trafficLaneIcon", item.optInt("trafficLaneType", 1)));
                advised[i] = item.optBoolean("trafficLaneAdvised");
                hasAdvised |= item.has("trafficLaneAdvised");
            }
            return LaneInfo.data(lanes, hasAdvised ? advised : null);
        } catch (Throwable ignored) {
            return LaneInfo.notHandled();
        }
    }

    public static boolean hasLanePayload(Bundle extras) {
        if (extras == null) {
            return false;
        }
        return !TextUtils.isEmpty(valueString(extras, DRIVE_WAY_JSON_KEYS))
                || intValue(extras, "KEY_TYPE", -1) == AmapConstants.KEY_TYPE_LANE_INFO
                || hasAny(extras, LANE_ICON_KEYS);
    }

    private static boolean hasAny(Bundle extras, String... keys) {
        for (String key : keys) {
            if (extras.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static Object safeExtra(Bundle extras, String key) {
        try {
            return extras.get(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int intValue(Bundle extras, String key, int fallback) {
        Object value = safeExtra(extras, key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int[] intArrayValue(Bundle extras, String... keys) {
        for (String key : keys) {
            int[] parsed = parseIntArray(safeExtra(extras, key));
            if (parsed != null && parsed.length > 0) {
                return parsed;
            }
        }
        return null;
    }

    private static boolean[] booleanArrayValue(Bundle extras, String... keys) {
        for (String key : keys) {
            boolean[] parsed = parseBooleanArray(safeExtra(extras, key));
            if (parsed != null && parsed.length > 0) {
                return parsed;
            }
        }
        return null;
    }

    private static int[] parseIntArray(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof int[]) {
            return (int[]) value;
        }
        if (value instanceof Integer) {
            return new int[]{(Integer) value};
        }
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            int length = Array.getLength(value);
            int[] out = new int[length];
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                out[i] = item instanceof Number ? ((Number) item).intValue() : parseInt(String.valueOf(item), 1);
            }
            return out;
        }
        String s = String.valueOf(value).replace('[', ' ').replace(']', ' ').trim();
        if (TextUtils.isEmpty(s)) {
            return null;
        }
        String[] parts = s.split("[,;| ]+");
        int[] out = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) {
                out[count++] = parseInt(part, 1);
            }
        }
        if (count == 0) {
            return null;
        }
        return Arrays.copyOf(out, count);
    }

    private static boolean[] parseBooleanArray(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof boolean[]) {
            return (boolean[]) value;
        }
        if (value instanceof Boolean) {
            return new boolean[]{(Boolean) value};
        }
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            int length = Array.getLength(value);
            boolean[] out = new boolean[length];
            for (int i = 0; i < length; i++) {
                out[i] = parseBoolean(Array.get(value, i));
            }
            return out;
        }
        String s = String.valueOf(value).replace('[', ' ').replace(']', ' ').trim();
        if (TextUtils.isEmpty(s)) {
            return null;
        }
        String[] parts = s.split("[,;| ]+");
        boolean[] out = new boolean[parts.length];
        int count = 0;
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) {
                out[count++] = parseBoolean(part);
            }
        }
        if (count == 0) {
            return null;
        }
        return Arrays.copyOf(out, count);
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String s = String.valueOf(value);
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "\u662f".equals(s);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String valueString(Bundle extras, String... keys) {
        for (String key : keys) {
            Object value = safeExtra(extras, key);
            if (value == null) {
                continue;
            }
            String s = String.valueOf(value);
            if (!TextUtils.isEmpty(s) && !"0".equals(s) && !"null".equals(s)) {
                return s;
            }
        }
        return null;
    }

    public static final class LaneInfo {
        private static final LaneInfo NOT_HANDLED = new LaneInfo(false, false, null, null);
        private static final LaneInfo CLEAR = new LaneInfo(true, true, null, null);

        public final boolean handled;
        public final boolean clear;
        public final int[] lanes;
        public final boolean[] advised;

        private LaneInfo(boolean handled, boolean clear, int[] lanes, boolean[] advised) {
            this.handled = handled;
            this.clear = clear;
            this.lanes = lanes == null ? null : Arrays.copyOf(lanes, lanes.length);
            this.advised = advised == null ? null : Arrays.copyOf(advised, advised.length);
        }

        public static LaneInfo notHandled() {
            return NOT_HANDLED;
        }

        public static LaneInfo clear() {
            return CLEAR;
        }

        public static LaneInfo data(int[] lanes, boolean[] advised) {
            return new LaneInfo(true, false, lanes, advised);
        }

        public boolean isHandled() {
            return handled;
        }

        public boolean shouldClear() {
            return clear;
        }

        public boolean hasLaneData() {
            return handled && !clear && lanes != null && lanes.length > 0;
        }
    }
}
