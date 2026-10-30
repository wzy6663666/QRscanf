package com.qrscanf.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.qrscanf.app.model.BarcodeItem;
import com.qrscanf.app.model.GenRecord;
import com.qrscanf.app.util.BarcodeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight local history, persisted as JSON in SharedPreferences.
 * No network, no database dependency.
 */
public final class HistoryStore {

    private static final String PREFS = "qrscanf_history";
    private static final String KEY_SCANS = "scans";
    private static final String KEY_GENS = "gens";
    private static final int MAX_SCANS = 200;
    private static final int MAX_GENS = 100;

    private HistoryStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void addScan(Context context, BarcodeItem item) {
        List<BarcodeItem> items = getScans(context);
        items.add(0, item);
        while (items.size() > MAX_SCANS) {
            items.remove(items.size() - 1);
        }
        saveScans(context, items);
    }

    public static void addScans(Context context, List<BarcodeItem> newItems) {
        if (newItems.isEmpty()) {
            return;
        }
        List<BarcodeItem> items = getScans(context);
        for (int i = newItems.size() - 1; i >= 0; i--) {
            items.add(0, newItems.get(i));
        }
        while (items.size() > MAX_SCANS) {
            items.remove(items.size() - 1);
        }
        saveScans(context, items);
    }

    private static void saveScans(Context context, List<BarcodeItem> items) {
        JSONArray array = new JSONArray();
        for (BarcodeItem item : items) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("value", item.value);
                obj.put("format", item.mlkitFormat);
                obj.put("formatLabel", item.formatLabel);
                obj.put("source", item.source);
                obj.put("time", item.timestamp);
                obj.put("type", item.contentType);
                array.put(obj);
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit().putString(KEY_SCANS, array.toString()).apply();
    }

    public static List<BarcodeItem> getScans(Context context) {
        List<BarcodeItem> result = new ArrayList<>();
        String json = prefs(context).getString(KEY_SCANS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                int format = obj.optInt("format", 0);
                result.add(new BarcodeItem(
                        obj.optString("value", ""),
                        format,
                        obj.optString("formatLabel", BarcodeUtils.formatLabel(format)),
                        obj.optString("source", "扫码"),
                        obj.optLong("time", 0),
                        obj.optString("type", "文本")));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    public static void clearScans(Context context) {
        prefs(context).edit().remove(KEY_SCANS).apply();
    }

    public static void addGen(Context context, GenRecord record) {
        List<GenRecord> items = getGens(context);
        items.add(0, record);
        while (items.size() > MAX_GENS) {
            items.remove(items.size() - 1);
        }
        JSONArray array = new JSONArray();
        for (GenRecord item : items) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("content", item.content);
                obj.put("formatLabel", item.formatLabel);
                obj.put("formatKey", item.formatKey);
                obj.put("time", item.timestamp);
                obj.put("size", item.size);
                obj.put("ec", item.errorCorrection);
                array.put(obj);
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit().putString(KEY_GENS, array.toString()).apply();
    }

    public static List<GenRecord> getGens(Context context) {
        List<GenRecord> result = new ArrayList<>();
        String json = prefs(context).getString(KEY_GENS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                result.add(new GenRecord(
                        obj.optString("content", ""),
                        obj.optString("formatLabel", "QR Code"),
                        obj.optString("formatKey", "QR_CODE"),
                        obj.optLong("time", 0),
                        obj.optInt("size", 512),
                        obj.optString("ec", "")));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    public static void clearGens(Context context) {
        prefs(context).edit().remove(KEY_GENS).apply();
    }
}
