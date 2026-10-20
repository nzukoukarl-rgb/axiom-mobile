package com.axiom.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

/** M1.5 registry. rootReference = SAF tree URI, not a path identity. */
public final class WorkspaceStore {
    private static final String PREF = "axiom_workspace_registry";
    private static final String KEY = "workspaces_json";

    private WorkspaceStore() {}

    public static void save(Context ctx, JSONObject workspace) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        JSONArray arr = loadAll(ctx);
        String id = workspace.getString("workspaceId");
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject w = arr.getJSONObject(i);
            if (!id.equals(w.optString("workspaceId"))) next.put(w);
        }
        next.put(workspace);
        sp.edit().putString(KEY, next.toString()).apply();
    }

    public static JSONArray loadAll(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static JSONObject getActive(Context ctx) {
        JSONArray arr = loadAll(ctx);
        try {
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject w = arr.getJSONObject(i);
                if ("AUTHORIZED".equals(w.optString("status"))) return w;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void markRevoked(Context ctx, String workspaceId) {
        try {
            JSONArray arr = loadAll(ctx);
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject w = arr.getJSONObject(i);
                if (workspaceId.equals(w.optString("workspaceId"))) {
                    w.put("status", "REVOKED");
                    w.put("updatedAt", java.time.Instant.now().toString());
                }
                next.put(w);
            }
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static JSONObject buildSafWorkspace(String displayName, Uri treeUri, boolean write) throws Exception {
        String now = java.time.Instant.now().toString();
        JSONObject w = new JSONObject();
        w.put("workspaceId", "ws-" + Integer.toHexString(treeUri.toString().hashCode()));
        w.put("displayName", displayName != null ? displayName : "Workspace");
        w.put("provider", "ANDROID_SAF");
        w.put("rootReference", treeUri.toString());
        JSONArray perms = new JSONArray();
        perms.put("READ");
        if (write) perms.put("WRITE");
        w.put("permissions", perms);
        w.put("status", "AUTHORIZED");
        w.put("createdAt", now);
        w.put("updatedAt", now);
        return w;
    }
}
