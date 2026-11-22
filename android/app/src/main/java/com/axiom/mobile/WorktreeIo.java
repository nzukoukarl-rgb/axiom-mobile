package com.axiom.mobile;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

/** M2.3 — AxiomWorktrees visible (no leading dot). Canonical never modified. */
public final class WorktreeIo {
    public static final String WT_ROOT = "AxiomWorktrees";

    private WorktreeIo() {}

    private static DocumentFile requireRoot(Context ctx, Uri tree) throws Exception {
        DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
        if (root == null || !root.exists()) {
            throw new SecurityException("WORKSPACE_ACCESS_REVOKED");
        }
        return root;
    }

    private static DocumentFile childDir(DocumentFile parent, String name) {
        if (parent == null) return null;
        DocumentFile[] kids = parent.listFiles();
        if (kids != null) {
            for (DocumentFile k : kids) {
                if (k != null && k.isDirectory() && name.equals(k.getName())) return k;
            }
        }
        DocumentFile f = parent.findFile(name);
        if (f != null && f.isDirectory()) return f;
        return null;
    }

    private static DocumentFile ensureDir(DocumentFile parent, String name) throws Exception {
        DocumentFile existing = childDir(parent, name);
        if (existing != null) return existing;
        DocumentFile created = parent.createDirectory(name);
        if (created == null) throw new Exception("CREATE_DIR_FAILED:" + name);
        DocumentFile listed = childDir(parent, name);
        if (listed == null) throw new Exception("CREATE_DIR_NOT_VISIBLE:" + name);
        return listed;
    }

    public static JSONObject create(Context ctx, Uri tree, String taskId) {
        JSONObject o = new JSONObject();
        try {
            if (taskId == null) taskId = "";
            taskId = taskId.trim();
            if (taskId.isEmpty() || taskId.contains("/") || taskId.contains("..")
                    || taskId.contains("\\") || taskId.startsWith(".")) {
                o.put("ok", false);
                o.put("human", true);
                o.put("error", "INVALID_TASK_ID");
                o.put("message", "taskId invalide");
                return o;
            }
            DocumentFile root = requireRoot(ctx, tree);
            DocumentFile wtRoot = ensureDir(root, WT_ROOT);
            DocumentFile taskDir = ensureDir(wtRoot, taskId);
            o.put("ok", true);
            o.put("human", true);
            o.put("denied", false);
            o.put("capability", "repo.worktree.create");
            o.put("taskId", taskId);
            o.put("relativePath", WT_ROOT + "/" + taskId);
            o.put("canonicalIntact", true);
            o.put("title", "Worktree pret");
            o.put("message", "Dossier visible: " + WT_ROOT + "/" + taskId
                    + " — ouvre OtherWS dans Fichiers Android");
            o.put("status", "DIR_EXISTS");
            JSONArray paths = new JSONArray();
            paths.put(WT_ROOT + "/" + taskId + "/");
            o.put("paths", paths);
        } catch (SecurityException se) {
            try {
                o.put("ok", false); o.put("human", true); o.put("denied", true);
                o.put("error", se.getMessage());
                o.put("message", "Acces workspace revoque ou refuse");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                o.put("ok", false); o.put("human", true);
                o.put("error", e.getMessage() != null ? e.getMessage() : "IO_ERROR");
                o.put("message", "Echec create: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return o;
    }

    public static JSONObject status(Context ctx, Uri tree) {
        JSONObject o = new JSONObject();
        try {
            DocumentFile root = requireRoot(ctx, tree);
            DocumentFile wtRoot = childDir(root, WT_ROOT);
            JSONArray list = new JSONArray();
            if (wtRoot != null && wtRoot.isDirectory()) {
                DocumentFile[] kids = wtRoot.listFiles();
                if (kids != null) {
                    for (DocumentFile k : kids) {
                        if (k != null && k.isDirectory()) {
                            JSONObject e = new JSONObject();
                            e.put("taskId", k.getName());
                            e.put("relativePath", WT_ROOT + "/" + k.getName());
                            list.put(e);
                        }
                    }
                }
            }
            o.put("ok", true);
            o.put("human", true);
            o.put("canonicalIntact", true);
            o.put("worktreeRoot", WT_ROOT);
            o.put("worktrees", list);
            o.put("count", list.length());
            o.put("message", "Root: " + WT_ROOT + " · count=" + list.length());
        } catch (SecurityException se) {
            try {
                o.put("ok", false); o.put("human", true); o.put("denied", true);
                o.put("error", se.getMessage());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                o.put("ok", false); o.put("human", true);
                o.put("error", e.getMessage() != null ? e.getMessage() : "IO_ERROR");
            } catch (Exception ignored) {}
        }
        return o;
    }
}
