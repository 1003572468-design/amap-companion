package com.autonavi.companion;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import org.json.JSONObject;

import java.util.HashMap;

final class BroadcastSummary {
    final int keyType;
    final int state;
    final String mode;
    final String roadName;
    final String turnRoad;
    final String turnDistance;
    final int laneCount;
    final boolean hasLanePayload;
    final int trafficLightCount;
    final boolean hasTrafficLightPayload;
    final boolean hasCamera;
    final int cameraType;
    final int cameraDistance;
    final int speed;
    final int limitSpeed;
    final boolean hasTmc;
    final String etaText;

    private BroadcastSummary(int keyType, int state, String mode, String roadName,
                             String turnRoad, String turnDistance, int laneCount,
                             boolean hasLanePayload, int trafficLightCount,
                             boolean hasTrafficLightPayload, boolean hasCamera,
                             int cameraType, int cameraDistance, int speed,
                             int limitSpeed, boolean hasTmc, String etaText) {
        this.keyType = keyType;
        this.state = state;
        this.mode = emptyIfNull(mode);
        this.roadName = emptyIfNull(roadName);
        this.turnRoad = emptyIfNull(turnRoad);
        this.turnDistance = emptyIfNull(turnDistance);
        this.laneCount = laneCount;
        this.hasLanePayload = hasLanePayload;
        this.trafficLightCount = trafficLightCount;
        this.hasTrafficLightPayload = hasTrafficLightPayload;
        this.hasCamera = hasCamera;
        this.cameraType = cameraType;
        this.cameraDistance = cameraDistance;
        this.speed = speed;
        this.limitSpeed = limitSpeed;
        this.hasTmc = hasTmc;
        this.etaText = emptyIfNull(etaText);
    }

    static BroadcastSummary empty() {
        return new BroadcastSummary(-1, -1, "", "", "", "", 0,
                false, 0, false, false, -1, -1, -1, -1, false, "");
    }

    static BroadcastSummary fromIntent(Intent intent) {
        if (intent == null) {
            return empty();
        }
        return from(intent.getAction(), intent.getExtras());
    }

    static BroadcastSummary from(String action, Bundle extras) {
        if (extras == null) {
            return new BroadcastSummary(-1, -1, modeForAction(action, -1, -1, null),
                    "", "", "", 0, false, 0, false,
                    false, -1, -1, -1, -1, false, "");
        }

        int keyType = intValue(extras, "KEY_TYPE", -1);
        int state = intValue(extras, "EXTRA_STATE", -1);
        int speed = intValue(extras, "CUR_SPEED", intValue(extras, "SPEED",
                intValue(extras, "CAR_SPEED", -1)));
        int limitSpeed = intValue(extras, "LIMITED_SPEED",
                intValue(extras, "CAMERA_SPEED", intValue(extras, "LIMIT_SPEED", -1)));
        String road = valueString(extras, "CUR_ROAD_NAME", "NEXT_ROAD_NAME", "ROAD_NAME",
                "roadName", "curRoadName", "CURRENT_ROAD_NAME", "ROAD_NAME_AUTO");
        String turnRoad = valueString(extras, "NEXT_ROAD_NAME", "nextRoadName", "NEXT_ROAD",
                "NEXT_ROAD_NAME_AUTO", "SEG_ROAD_NAME", "NEXT_SEG_ROAD_NAME");
        String turnDistance = valueString(extras, "SEG_REMAIN_DIS_AUTO",
                "NEXT_SEG_REMAIN_DIS_AUTO", "SEG_REMAIN_DIS", "NEXT_SEG_REMAIN_DIS");
        String eta = routeSummary(extras);

        LaneInfoParser.LaneInfo laneInfo = LaneInfoParser.parse(extras);
        int laneCount = laneInfo.hasLaneData() ? laneInfo.lanes.length : 0;
        boolean hasLanePayload = LaneInfoParser.hasLanePayload(extras);

        HashMap<Integer, TrafficLightParser.LightState> existing = new HashMap<>();
        TrafficLightParser.Result lightResult = TrafficLightParser.parse(
                extras, keyType == AmapConstants.KEY_TYPE_CRUISE, -1,
                intValue(extras, "NEW_ICON", intValue(extras, "ICON", 0)), existing);
        int explicitLightCount = intValue(extras, "routeRemainTrafficLightNum",
                intValue(extras, "TRAFFIC_LIGHT_NUM", -1));
        int trafficLightCount = Math.max(lightResult.lights.size(), Math.max(0, explicitLightCount));
        boolean hasTrafficLightPayload = lightResult.changed
                || TrafficLightParser.hasCountdownPayload(extras)
                || actionContains(action, "traffic_light")
                || explicitLightCount >= 0;

        int cameraIndex = intValue(extras, "CAMERA_INDEX", 0);
        int cameraDistance = intValue(extras, "CAMERA_DIST",
                intValue(extras, "CAMERA_DISTANCE", -1));
        int cameraType = intValue(extras, "CAMERA_TYPE", -1);
        boolean hasCamera = hasAny(extras, "CAMERA_INDEX", "CAMERA_DIST", "CAMERA_TYPE",
                "CAMERA_SPEED", "LIMITED_SPEED") && (cameraIndex != -1 || cameraDistance >= 0);

        boolean hasTmc = hasAny(extras, "EXTRA_TMC_SEGMENT", "extra_tmc_segment")
                || keyType == 13011;
        String mode = modeForAction(action, keyType, state, road);
        return new BroadcastSummary(keyType, state, mode, road, turnRoad, turnDistance,
                laneCount, hasLanePayload, trafficLightCount, hasTrafficLightPayload,
                hasCamera, cameraType, cameraDistance, speed, limitSpeed, hasTmc, eta);
    }

    static BroadcastSummary fromJson(JSONObject object) {
        if (object == null) {
            return empty();
        }
        return new BroadcastSummary(
                object.optInt("keyType", -1),
                object.optInt("state", -1),
                object.optString("mode", ""),
                object.optString("roadName", ""),
                object.optString("turnRoad", ""),
                object.optString("turnDistance", ""),
                object.optInt("laneCount", 0),
                object.optBoolean("hasLanePayload", false),
                object.optInt("trafficLightCount", 0),
                object.optBoolean("hasTrafficLightPayload", false),
                object.optBoolean("hasCamera", false),
                object.optInt("cameraType", -1),
                object.optInt("cameraDistance", -1),
                object.optInt("speed", -1),
                object.optInt("limitSpeed", -1),
                object.optBoolean("hasTmc", false),
                object.optString("etaText", ""));
    }

    JSONObject toJson() throws Exception {
        JSONObject object = new JSONObject();
        object.put("keyType", keyType);
        object.put("state", state);
        object.put("mode", mode);
        object.put("roadName", roadName);
        object.put("turnRoad", turnRoad);
        object.put("turnDistance", turnDistance);
        object.put("laneCount", laneCount);
        object.put("hasLanePayload", hasLanePayload);
        object.put("trafficLightCount", trafficLightCount);
        object.put("hasTrafficLightPayload", hasTrafficLightPayload);
        object.put("hasCamera", hasCamera);
        object.put("cameraType", cameraType);
        object.put("cameraDistance", cameraDistance);
        object.put("speed", speed);
        object.put("limitSpeed", limitSpeed);
        object.put("hasTmc", hasTmc);
        object.put("etaText", etaText);
        return object;
    }

    String oneLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(TextUtils.isEmpty(mode) ? "\u5e7f\u64ad" : mode);
        if (!TextUtils.isEmpty(roadName)) {
            sb.append(" \u00b7 ").append(roadName);
        }
        if (!TextUtils.isEmpty(turnRoad)) {
            sb.append(" \u2192 ").append(turnRoad);
        }
        if (!TextUtils.isEmpty(turnDistance)) {
            sb.append(" ").append(turnDistance);
        }
        if (speed >= 0) {
            sb.append(" \u00b7 ").append(speed).append(" km/h");
        }
        if (laneCount > 0) {
            sb.append(" \u00b7 \u8f66\u9053 ").append(laneCount);
        }
        if (trafficLightCount > 0) {
            sb.append(" \u00b7 \u7ea2\u7eff\u706f ").append(trafficLightCount);
        } else if (hasTrafficLightPayload) {
            sb.append(" \u00b7 \u7ea2\u7eff\u706f\u6570\u636e");
        }
        if (hasCamera) {
            sb.append(" \u00b7 \u7535\u5b50\u773c");
        }
        if (hasTmc) {
            sb.append(" \u00b7 \u8def\u51b5");
        }
        if (sb.length() == 0) {
            return "\u5e7f\u64ad";
        }
        return sb.toString();
    }

    private static String modeForAction(String action, int keyType, int state, String road) {
        if (keyType == AmapConstants.KEY_TYPE_NAVIGATION_STATE) {
            if (state == AmapConstants.NAV_STATE_CRUISE) return "\u5de1\u822a";
            if (state == AmapConstants.NAV_STATE_CRUISE_EXIT) return "\u5de1\u822a\u9000\u51fa";
            if (state == AmapConstants.NAV_STATE_NAVIGATING) return "\u5bfc\u822a";
            if (state == AmapConstants.NAV_STATE_NAV_EXIT) return "\u5bfc\u822a\u9000\u51fa";
            if (state == AmapConstants.APP_STATE_FOREGROUND) return "\u9ad8\u5fb7\u524d\u53f0";
            if (state == AmapConstants.APP_STATE_BACKGROUND) return "\u9ad8\u5fb7\u540e\u53f0";
            return "\u72b6\u6001";
        }
        if (keyType == AmapConstants.KEY_TYPE_ROUTE_GUIDANCE) return "\u5bfc\u822a";
        if (keyType == AmapConstants.KEY_TYPE_CRUISE) return "\u5de1\u822a";
        if (keyType == AmapConstants.KEY_TYPE_LANE_INFO) return "\u8f66\u9053";
        if (keyType == AmapConstants.KEY_TYPE_TRAFFIC_LIGHT || actionContains(action, "traffic_light")) {
            return "\u7ea2\u7eff\u706f";
        }
        if (keyType == 13011) return "\u8def\u51b5";
        if (keyType == 12011) return "\u9ad8\u901f\u51fa\u53e3";
        if (!TextUtils.isEmpty(road)) return "\u884c\u8f66\u4fe1\u53f7";
        return "\u5e7f\u64ad";
    }

    private static String routeSummary(Bundle extras) {
        String distance = valueString(extras, "ROUTE_REMAIN_DIS_AUTO", "routeRemainDistanceAuto", "distance");
        String time = valueString(extras, "ROUTE_REMAIN_TIME_AUTO", "routeRemainTimeAuto", "remainTime");
        String eta = valueString(extras, "ETA_TEXT", "etaText", "eta", "arrivalTime", "arriveTime");
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(distance)) {
            sb.append(distance);
        }
        if (!TextUtils.isEmpty(time)) {
            if (sb.length() > 0) sb.append(" \u00b7 ");
            sb.append(time);
        }
        if (!TextUtils.isEmpty(eta)) {
            if (sb.length() > 0) sb.append(" \u00b7 ");
            sb.append(eta);
        }
        return sb.toString();
    }

    private static boolean actionContains(String action, String needle) {
        return action != null && action.toLowerCase(java.util.Locale.US).contains(needle);
    }

    private static boolean hasAny(Bundle extras, String... keys) {
        if (extras == null) {
            return false;
        }
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

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
