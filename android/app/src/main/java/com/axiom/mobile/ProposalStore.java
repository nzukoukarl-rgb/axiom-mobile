package com.axiom.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

public final class ProposalStore {
    private static final String PREFS = "axiom_proposals";
    private static final String KEY = "items";
    private ProposalStore() {}
    public static JSONObject createDemo(Context ctx, JSONObject workspace) throws Exception {
        String wsName = workspace != null ? workspace.optString("displayName", "workspace") : "workspace";
        String wsId = workspace != null ? workspace.optString("workspaceId", "") : "";
        JSONObject p = new JSONObject();
        p.put("proposalId", "prop-" + UUID.randomUUID().toString().substring(0, 8));
        p.put("taskId", "task-local");
        p.put("runId", "run-local");
        p.put("stepId", "step-propose");
        p.put("workspaceId", wsId);
        p.put("workspaceLabel", wsName);
        p.put("runnerId", "runner-karl-phone");
        p.put("capability", "repo.edit");
        p.put("operation", "patch");
        JSONArray targets = new JSONArray(); targets.put("Combat_Arena.py");
        p.put("targets", targets);
        p.put("title", "Garde-fou solde carte cadeau");
        p.put("summary", "Refuser le paiement carte cadeau si le total panier dépasse le solde restant, avec un message d'erreur clair.");
        p.put("risk", "low");
        p.put("approvalPolicy", "required");
        p.put("status", "PROPOSED");
        p.put("canonicalIntact", true);
        String now = java.time.Instant.now().toString();
        p.put("createdAt", now);
        p.put("expiresAt", java.time.Instant.now().plusSeconds(3600).toString());
        save(ctx, p);
        return p;
    }
    public static void save(Context ctx, JSONObject p) throws Exception {
        JSONArray arr = loadAll(ctx); String id = p.getString("proposalId");
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) { JSONObject o = arr.getJSONObject(i); if (!id.equals(o.optString("proposalId"))) next.put(o); }
        next.put(p);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.toString()).apply();
    }
    public static JSONArray loadAll(Context ctx) {
        try { return new JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    public static JSONObject getPending(Context ctx) {
        JSONArray arr = loadAll(ctx);
        for (int i = arr.length() - 1; i >= 0; i--) { try { JSONObject o = arr.getJSONObject(i); if ("PROPOSED".equals(o.optString("status"))) return o; } catch (Exception ignored) {} }
        return null;
    }
    public static JSONObject setStatus(Context ctx, String proposalId, String status) throws Exception {
        JSONArray arr = loadAll(ctx); JSONObject found = null;
        for (int i = 0; i < arr.length(); i++) { JSONObject o = arr.getJSONObject(i); if (proposalId.equals(o.optString("proposalId"))) { o.put("status", status); o.put("updatedAt", java.time.Instant.now().toString()); found = o; } }
        if (found == null) { JSONObject err = new JSONObject(); err.put("ok", false); err.put("error", "NOT_FOUND"); return err; }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
        found.put("ok", true); return found;
    }
}
