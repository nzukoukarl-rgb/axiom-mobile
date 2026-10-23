package com.axiom.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

public final class WorkspaceStore {
    private static final String PREFS = "axiom_workspaces";
    private static final String KEY = "registry";

    private WorkspaceStore() {}

    public static JSONObject buildSafWorkspace(String displayName, Uri treeUri, boolean write) throws Exception {
        JSONObject w = new JSONObject();
        w.put("workspaceId", "ws-" + UUID.randomUUID().toString().substring(0, 8));
        w.put("displayName", displayName != null ? displayName : "workspace");
        w.put("provider", "ANDROID_SAF");
        w.put("rootReference", treeUri.toString());
        JSONArray perms = new JSONArray();
        perms.put("READ");
        if (write) perms.put("WRITE");
        w.put("permissions", perms);
        w.put("status", "AUTHORIZED");
        String now = java.time.Instant.now().toString();
        w.put("createdAt", now);
        w.put("updatedAt", now);
        return w;
    }

    public static JSONObject withPolicyReadOnly(JSONObject w) throws Exception {
        JSONObject copy = new JSONObject(w.toString());
        JSONArray perms = new JSONArray();
        perms.put("READ");
        copy.put("permissions", perms);
        copy.put("policyNote", "T7-P effective READ-only");
        return copy;
    }

    public static void save(Context ctx, JSONObject workspace) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr = loadAll(ctx);
        String id = workspace.getString("workspaceId");
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (!id.equals(o.optString("workspaceId"))) next.put(o);
        }
        next.put(workspace);
        sp.edit().putString(KEY, next.toString()).apply();
    }

    public static JSONArray loadAll(Context ctx) {
        try {
            String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static JSONObject getActive(Context ctx) {
        JSONArray arr = loadAll(ctx);
        for (int i = arr.length() - 1; i >= 0; i--) {
            try {
                JSONObject w = arr.getJSONObject(i);
                if ("AUTHORIZED".equals(w.optString("status"))) return w;
            } catch (Exception ignored) {}
        }
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
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static boolean policyAllows(JSONObject workspace, String capability) {
        if (workspace == null) return false;
        if (!"AUTHORIZED".equals(workspace.optString("status"))) return false;
        JSONArray perms = workspace.optJSONArray("permissions");
        if (perms == null) return false;
        boolean read = false, write = false;
        for (int i = 0; i < perms.length(); i++) {
            String p = perms.optString(i);
            if ("READ".equals(p)) read = true;
            if ("WRITE".equals(p)) write = true;
        }
        if ("repo.read".equals(capability) || "repo.list".equals(capability)) return read;
        if ("repo.edit".equals(capability) || "repo.worktree.create".equals(capability)) return write;
        return false;
    }
}
