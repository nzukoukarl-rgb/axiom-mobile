package com.axiom.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/**
 * AGENT-RUNTIME-001 — durable agent state (not ActionProposal).
 * Message ≠ Task ≠ Run ≠ Action.
 * No repo mutation here.
 */
public final class AgentRuntimeStore {
    private static final String PREFS = "axiom_agent_runtime";
    private static final String KEY_SESSION = "session";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_RUNS = "runs";

    private AgentRuntimeStore() {}

    public static JSONObject ensureSession(Context ctx) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_SESSION, null);
        if (raw != null) {
            return new JSONObject(raw);
        }
        JSONObject s = new JSONObject();
        s.put("sessionId", "sess-" + UUID.randomUUID().toString().substring(0, 8));
        s.put("createdAt", java.time.Instant.now().toString());
        s.put("label", "Session locale");
        sp.edit().putString(KEY_SESSION, s.toString()).apply();
        return s;
    }

    public static JSONObject startTask(Context ctx, String goal) throws Exception {
        JSONObject session = ensureSession(ctx);
        if (goal == null || goal.trim().isEmpty()) {
            goal = "Avancer le travail en cours";
        }
        JSONObject task = new JSONObject();
        task.put("taskId", "task-" + UUID.randomUUID().toString().substring(0, 8));
        task.put("sessionId", session.optString("sessionId"));
        task.put("goal", goal.trim());
        task.put("status", "RUNNING");
        task.put("createdAt", java.time.Instant.now().toString());

        JSONObject run = new JSONObject();
        run.put("runId", "run-" + UUID.randomUUID().toString().substring(0, 8));
        run.put("taskId", task.getString("taskId"));
        run.put("status", "RUNNING");
        run.put("stepIndex", 1);
        run.put("stepTotal", 5);
        run.put("stepLabel", "Inspecter le contexte");
        run.put("checkpoint", "cp-1");
        run.put("updatedAt", java.time.Instant.now().toString());

        putTask(ctx, task);
        putRun(ctx, run);

        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("human", true);
        out.put("title", "Tâche démarrée");
        out.put("summary", goal.trim());
        out.put("status", "RUNNING");
        out.put("message", "Étape 1/5 · " + run.optString("stepLabel"));
        out.put("taskId", task.getString("taskId"));
        out.put("runId", run.getString("runId"));
        return out;
    }

    public static JSONObject status(Context ctx) throws Exception {
        JSONObject session = ensureSession(ctx);
        JSONObject run = latestRun(ctx);
        JSONObject task = run != null ? getTask(ctx, run.optString("taskId")) : null;
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("human", true);
        if (task == null) {
            out.put("empty", true);
            out.put("message", "Aucune tâche en cours. Tape: task start <objectif>");
            out.put("sessionLabel", session.optString("label"));
            return out;
        }
        out.put("title", task.optString("goal"));
        out.put("status", task.optString("status"));
        int idx = run.optInt("stepIndex", 1);
        int tot = run.optInt("stepTotal", 5);
        out.put("message", "Étape " + idx + "/" + tot + " · " + run.optString("stepLabel"));
        out.put("summary", "Session active · reprise possible après fermeture de l'app");
        out.put("taskId", task.optString("taskId"));
        out.put("runId", run.optString("runId"));
        return out;
    }

    public static JSONObject checkpoint(Context ctx, String label) throws Exception {
        JSONObject run = latestRun(ctx);
        if (run == null) {
            JSONObject e = new JSONObject();
            e.put("ok", false);
            e.put("message", "Aucun run à sauvegarder");
            return e;
        }
        if (label == null || label.isEmpty()) label = "checkpoint";
        run.put("checkpoint", label);
        run.put("updatedAt", java.time.Instant.now().toString());
        putRun(ctx, run);
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("human", true);
        out.put("title", "Point de reprise enregistré");
        out.put("message", "Tu peux tuer l'app : task resume retrouvera cet état.");
        out.put("status", run.optString("status"));
        return out;
    }

    public static JSONObject resume(Context ctx) throws Exception {
        JSONObject st = status(ctx);
        if (st.optBoolean("empty")) {
            st.put("ok", true);
            st.put("message", "Rien à reprendre.");
            return st;
        }
        st.put("title", "Reprise");
        st.put("message", "Repris · " + st.optString("message"));
        st.put("summary", st.optString("title"));
        // keep goal as summary already in status
        JSONObject run = latestRun(ctx);
        JSONObject task = getTask(ctx, run.optString("taskId"));
        if (task != null) {
            st.put("summary", task.optString("goal"));
            st.put("title", "Reprise · " + task.optString("goal"));
        }
        return st;
    }

    public static JSONObject advance(Context ctx) throws Exception {
        JSONObject run = latestRun(ctx);
        if (run == null) {
            JSONObject e = new JSONObject();
            e.put("ok", false);
            e.put("message", "Aucun run");
            return e;
        }
        int idx = run.optInt("stepIndex", 1) + 1;
        int tot = run.optInt("stepTotal", 5);
        String[] labels = {
            "Inspecter le contexte",
            "Diagnostiquer",
            "Formuler une proposition",
            "Attendre approbation",
            "Vérifier le résultat"
        };
        if (idx > tot) idx = tot;
        run.put("stepIndex", idx);
        run.put("stepLabel", labels[Math.min(idx, labels.length) - 1]);
        run.put("updatedAt", java.time.Instant.now().toString());
        if (idx >= 4) {
            run.put("status", "WAITING_APPROVAL");
        }
        putRun(ctx, run);
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("human", true);
        out.put("title", "Avancement");
        out.put("message", "Étape " + idx + "/" + tot + " · " + run.optString("stepLabel"));
        out.put("status", run.optString("status"));
        return out;
    }

    private static void putTask(Context ctx, JSONObject task) throws Exception {
        JSONArray arr = loadArr(ctx, KEY_TASKS);
        JSONArray next = new JSONArray();
        String id = task.getString("taskId");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (!id.equals(o.optString("taskId"))) next.put(o);
        }
        next.put(task);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TASKS, next.toString()).apply();
    }

    private static void putRun(Context ctx, JSONObject run) throws Exception {
        JSONArray arr = loadArr(ctx, KEY_RUNS);
        JSONArray next = new JSONArray();
        String id = run.getString("runId");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (!id.equals(o.optString("runId"))) next.put(o);
        }
        next.put(run);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RUNS, next.toString()).apply();
    }

    private static JSONArray loadArr(Context ctx, String key) {
        try {
            return new JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static JSONObject latestRun(Context ctx) {
        JSONArray arr = loadArr(ctx, KEY_RUNS);
        if (arr.length() == 0) return null;
        try {
            return arr.getJSONObject(arr.length() - 1);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject getTask(Context ctx, String taskId) {
        JSONArray arr = loadArr(ctx, KEY_TASKS);
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                if (taskId.equals(o.optString("taskId"))) return o;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
