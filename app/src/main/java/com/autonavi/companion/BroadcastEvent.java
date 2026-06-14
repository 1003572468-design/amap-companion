package com.autonavi.companion;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;

final class BroadcastEvent {
    final long timestampMs;
    final String action;
    final String packageName;
    final String componentName;
    final JSONObject extras;
    final BroadcastSummary summary;

    private BroadcastEvent(long timestampMs, String action, String packageName,
                           String componentName, JSONObject extras,
                           BroadcastSummary summary) {
        this.timestampMs = timestampMs;
        this.action = emptyIfNull(action);
        this.packageName = emptyIfNull(packageName);
        this.componentName = emptyIfNull(componentName);
        this.extras = extras == null ? new JSONObject() : extras;
        this.summary = summary == null ? BroadcastSummary.empty() : summary;
    }

    static BroadcastEvent fromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        ComponentName component = intent.getComponent();
        return new BroadcastEvent(
                System.currentTimeMillis(),
                intent.getAction(),
                intent.getPackage(),
                component == null ? "" : component.flattenToShortString(),
                extrasToJson(intent.getExtras()),
                BroadcastSummary.fromIntent(intent));
    }

    static BroadcastEvent fromJson(String json) throws Exception {
        return fromJson(new JSONObject(json));
    }

    static BroadcastEvent fromJson(JSONObject object) throws Exception {
        if (object == null) {
            return null;
        }
        JSONObject summaryJson = object.optJSONObject("summary");
        return new BroadcastEvent(
                object.optLong("timestampMs", System.currentTimeMillis()),
                object.optString("action", ""),
                object.optString("packageName", ""),
                object.optString("componentName", ""),
                object.optJSONObject("extras"),
                BroadcastSummary.fromJson(summaryJson));
    }

    JSONObject toJson() throws Exception {
        JSONObject object = new JSONObject();
        object.put("timestampMs", timestampMs);
        object.put("action", action);
        object.put("packageName", packageName);
        object.put("componentName", componentName);
        object.put("extras", extras);
        object.put("summary", summary.toJson());
        return object;
    }

    Intent toReplayIntent() {
        if (TextUtils.isEmpty(action)) {
            return null;
        }
        Intent intent = new Intent(action);
        restoreExtras(intent, extras);
        intent.putExtra(AppPrefs.EXTRA_DIAGNOSTIC_REPLAY, true);
        return intent;
    }

    String displayTitle() {
        return summary.oneLine();
    }

    String displayDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("action=").append(action);
        if (summary.keyType >= 0) {
            sb.append("  KEY_TYPE=").append(summary.keyType);
        }
        if (!TextUtils.isEmpty(packageName)) {
            sb.append("  package=").append(packageName);
        }
        String extraText = describeExtras(420);
        if (!TextUtils.isEmpty(extraText)) {
            sb.append('\n').append(extraText);
        }
        return sb.toString();
    }

    String describeExtras(int maxChars) {
        ArrayList<String> keys = new ArrayList<>();
        java.util.Iterator<String> iterator = extras.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (AppPrefs.EXTRA_DIAGNOSTIC_REPLAY.equals(key)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(key).append('=').append(extraValueLabel(extras.optJSONObject(key)));
            if (maxChars > 0 && sb.length() > maxChars) {
                sb.setLength(Math.max(0, maxChars - 1));
                sb.append('\u2026');
                break;
            }
        }
        return sb.toString();
    }

    private static String extraValueLabel(JSONObject encoded) {
        if (encoded == null) {
            return "null";
        }
        String type = encoded.optString("type", "string");
        Object value = encoded.opt("value");
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            return type + "[" + array.length() + "] " + compact(array.toString(), 160);
        }
        return compact(String.valueOf(value), 180) + "(" + type + ")";
    }

    private static String compact(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\n', ' ').replace('\r', ' ');
        if (clean.length() <= maxChars) {
            return clean;
        }
        return clean.substring(0, Math.max(0, maxChars - 1)) + '\u2026';
    }

    private static JSONObject extrasToJson(Bundle extras) {
        JSONObject object = new JSONObject();
        if (extras == null) {
            return object;
        }
        ArrayList<String> keys = new ArrayList<>(extras.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            try {
                if (AppPrefs.EXTRA_DIAGNOSTIC_REPLAY.equals(key)) {
                    continue;
                }
                object.put(key, encodeExtraValue(safeExtra(extras, key)));
            } catch (Throwable ignored) {
            }
        }
        return object;
    }

    private static Object safeExtra(Bundle extras, String key) {
        try {
            return extras.get(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JSONObject encodeExtraValue(Object value) throws Exception {
        JSONObject object = new JSONObject();
        if (value == null) {
            object.put("type", "null");
            object.put("value", JSONObject.NULL);
            return object;
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            object.put("type", "int");
            object.put("value", ((Number) value).intValue());
            return object;
        }
        if (value instanceof Long) {
            object.put("type", "long");
            object.put("value", ((Long) value).longValue());
            return object;
        }
        if (value instanceof Float) {
            object.put("type", "float");
            object.put("value", ((Float) value).doubleValue());
            return object;
        }
        if (value instanceof Double) {
            object.put("type", "double");
            object.put("value", ((Double) value).doubleValue());
            return object;
        }
        if (value instanceof Boolean) {
            object.put("type", "boolean");
            object.put("value", ((Boolean) value).booleanValue());
            return object;
        }
        if (value instanceof CharSequence || value instanceof Character) {
            object.put("type", "string");
            object.put("value", String.valueOf(value));
            return object;
        }
        if (value instanceof Bundle) {
            object.put("type", "bundle");
            object.put("value", extrasToJson((Bundle) value));
            return object;
        }
        if (value instanceof ArrayList) {
            return encodeArrayList((ArrayList<?>) value);
        }
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            return encodeArray(value);
        }
        object.put("type", "string_repr");
        object.put("className", cls.getName());
        object.put("value", String.valueOf(value));
        return object;
    }

    private static JSONObject encodeArrayList(ArrayList<?> values) throws Exception {
        JSONObject object = new JSONObject();
        boolean allInt = true;
        boolean allLong = true;
        boolean allString = true;
        for (Object value : values) {
            if (!(value instanceof Integer || value instanceof Short || value instanceof Byte)) {
                allInt = false;
            }
            if (!(value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte)) {
                allLong = false;
            }
            if (!(value instanceof CharSequence)) {
                allString = false;
            }
        }
        String type = allInt ? "int_array_list" : allLong ? "long_array_list"
                : allString ? "string_array_list" : "array_list_repr";
        JSONArray array = new JSONArray();
        for (Object value : values) {
            if (value == null) {
                array.put(JSONObject.NULL);
            } else if ("array_list_repr".equals(type)) {
                array.put(String.valueOf(value));
            } else {
                array.put(value);
            }
        }
        object.put("type", type);
        object.put("value", array);
        return object;
    }

    private static JSONObject encodeArray(Object value) throws Exception {
        JSONObject object = new JSONObject();
        int length = Array.getLength(value);
        Class<?> component = value.getClass().getComponentType();
        String type;
        if (component == Integer.TYPE || component == Short.TYPE || component == Byte.TYPE
                || Integer.class.equals(component) || Short.class.equals(component) || Byte.class.equals(component)) {
            type = "int_array";
        } else if (component == Long.TYPE || Long.class.equals(component)) {
            type = "long_array";
        } else if (component == Float.TYPE || Float.class.equals(component)) {
            type = "float_array";
        } else if (component == Double.TYPE || Double.class.equals(component)) {
            type = "double_array";
        } else if (component == Boolean.TYPE || Boolean.class.equals(component)) {
            type = "boolean_array";
        } else if (String.class.equals(component) || CharSequence.class.isAssignableFrom(component)) {
            type = "string_array";
        } else {
            type = "array_repr";
            object.put("componentName", component == null ? "" : component.getName());
        }
        JSONArray array = new JSONArray();
        for (int i = 0; i < length; i++) {
            Object item = Array.get(value, i);
            if (item == null) {
                array.put(JSONObject.NULL);
            } else if ("array_repr".equals(type)) {
                array.put(String.valueOf(item));
            } else {
                array.put(item);
            }
        }
        object.put("type", type);
        object.put("value", array);
        return object;
    }

    private static void restoreExtras(Intent intent, JSONObject extras) {
        if (intent == null || extras == null) {
            return;
        }
        java.util.Iterator<String> iterator = extras.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            JSONObject encoded = extras.optJSONObject(key);
            if (encoded == null) {
                continue;
            }
            restoreExtra(intent, key, encoded);
        }
    }

    private static void restoreExtra(Intent intent, String key, JSONObject encoded) {
        try {
            String type = encoded.optString("type", "string");
            if ("null".equals(type) || encoded.isNull("value")) {
                return;
            }
            if ("int".equals(type)) {
                intent.putExtra(key, encoded.optInt("value"));
            } else if ("long".equals(type)) {
                intent.putExtra(key, encoded.optLong("value"));
            } else if ("float".equals(type)) {
                intent.putExtra(key, (float) encoded.optDouble("value"));
            } else if ("double".equals(type)) {
                intent.putExtra(key, encoded.optDouble("value"));
            } else if ("boolean".equals(type)) {
                intent.putExtra(key, encoded.optBoolean("value"));
            } else if ("bundle".equals(type)) {
                Bundle bundle = new Bundle();
                restoreBundle(bundle, encoded.optJSONObject("value"));
                intent.putExtra(key, bundle);
            } else if ("int_array".equals(type) || "int_array_list".equals(type)) {
                intent.putExtra(key, toIntArray(encoded.optJSONArray("value")));
            } else if ("long_array".equals(type) || "long_array_list".equals(type)) {
                intent.putExtra(key, toLongArray(encoded.optJSONArray("value")));
            } else if ("float_array".equals(type)) {
                intent.putExtra(key, toFloatArray(encoded.optJSONArray("value")));
            } else if ("double_array".equals(type)) {
                intent.putExtra(key, toDoubleArray(encoded.optJSONArray("value")));
            } else if ("boolean_array".equals(type)) {
                intent.putExtra(key, toBooleanArray(encoded.optJSONArray("value")));
            } else if ("string_array".equals(type) || "string_array_list".equals(type)) {
                intent.putExtra(key, toStringArray(encoded.optJSONArray("value")));
            } else {
                intent.putExtra(key, encoded.optString("value", ""));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void restoreBundle(Bundle bundle, JSONObject extras) {
        if (bundle == null || extras == null) {
            return;
        }
        java.util.Iterator<String> iterator = extras.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            JSONObject encoded = extras.optJSONObject(key);
            if (encoded == null) {
                continue;
            }
            try {
                String type = encoded.optString("type", "string");
                if ("int".equals(type)) {
                    bundle.putInt(key, encoded.optInt("value"));
                } else if ("long".equals(type)) {
                    bundle.putLong(key, encoded.optLong("value"));
                } else if ("float".equals(type)) {
                    bundle.putFloat(key, (float) encoded.optDouble("value"));
                } else if ("double".equals(type)) {
                    bundle.putDouble(key, encoded.optDouble("value"));
                } else if ("boolean".equals(type)) {
                    bundle.putBoolean(key, encoded.optBoolean("value"));
                } else if ("int_array".equals(type) || "int_array_list".equals(type)) {
                    bundle.putIntArray(key, toIntArray(encoded.optJSONArray("value")));
                } else if ("long_array".equals(type) || "long_array_list".equals(type)) {
                    bundle.putLongArray(key, toLongArray(encoded.optJSONArray("value")));
                } else if ("float_array".equals(type)) {
                    bundle.putFloatArray(key, toFloatArray(encoded.optJSONArray("value")));
                } else if ("double_array".equals(type)) {
                    bundle.putDoubleArray(key, toDoubleArray(encoded.optJSONArray("value")));
                } else if ("boolean_array".equals(type)) {
                    bundle.putBooleanArray(key, toBooleanArray(encoded.optJSONArray("value")));
                } else if ("string_array".equals(type) || "string_array_list".equals(type)) {
                    bundle.putStringArray(key, toStringArray(encoded.optJSONArray("value")));
                } else if (!"null".equals(type)) {
                    bundle.putString(key, encoded.optString("value", ""));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static int[] toIntArray(JSONArray array) {
        if (array == null) return new int[0];
        int[] out = new int[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = array.optInt(i);
        return out;
    }

    private static long[] toLongArray(JSONArray array) {
        if (array == null) return new long[0];
        long[] out = new long[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = array.optLong(i);
        return out;
    }

    private static float[] toFloatArray(JSONArray array) {
        if (array == null) return new float[0];
        float[] out = new float[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = (float) array.optDouble(i);
        return out;
    }

    private static double[] toDoubleArray(JSONArray array) {
        if (array == null) return new double[0];
        double[] out = new double[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = array.optDouble(i);
        return out;
    }

    private static boolean[] toBooleanArray(JSONArray array) {
        if (array == null) return new boolean[0];
        boolean[] out = new boolean[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = array.optBoolean(i);
        return out;
    }

    private static String[] toStringArray(JSONArray array) {
        if (array == null) return new String[0];
        String[] out = new String[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = array.optString(i, "");
        return out;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
