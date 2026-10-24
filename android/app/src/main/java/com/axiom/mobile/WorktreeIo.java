package com.axiom.mobile;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * M2.0 — worktree dirs under workspace root via SAF.
 * Canonical files are never modified here.
 */
public final class WorktreeIo {
    public static final String WT_ROOT = ".axiom-worktrees";

    private WorktreeIo() {}

    private static DocumentFile requireRoot(Context ctx, Uri tree) throws Exception {
        DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
        if (root == null || !root.exists()) {
            throw new SecurityException("WORKSPACE_ACCESS_REVOKED");
        }
        return root;
    }

    private static DocumentFile ensureDir(DocumentFile parent, String name) throws Exception {
        DocumentFile existing = parent.findFile(name);
        if (existing != null && existing.isDirectory()) {
            return existing;
        }
        if (existing != null && !existing.isDirectory()) {
            throw new Exception("NOT_A_DIRECTORY:" + name);
        }
        DocumentFile created = parent.createDirectory(name);
        if (created == null) {
            throw new Exception("CREATE_DIR_FAILED:" + name);
        }
        return created;
    }

    /** Create .axiom-worktrees/<taskId>/ — does not touch other files. */
    public static JSONObject create(Context ctx, Uri tree, String taskId) {
        JSONObject o = new JSONObject();
        try {
            if (taskId == null) taskId = "";
            taskId = taskId.trim();
            if (taskId.isEmpty() || taskId.contains("/") || taskId.contains("..")
                    || taskId.contains("\\") || taskId.startsWith(".")) {
                o.put("ok", false);
                o.put("error", "INVALID_TASK_ID");
                return o;
            }
            DocumentFile root = requireRoot(ctx, tree);
            DocumentFile wtRoot = ensureDir(root, WT_ROOT);
            DocumentFile taskDir = ensureDir(wtRoot, taskId);

            o.put("ok", true);
            o.put("denied", false);
            o.put("capability", "repo.worktree.create");
            o.put("taskId", taskId);
            o.put("relativePath", WT_ROOT + "/" + taskId);
            o.put("canonicalIntact", true);
            o.put("note", "Worktree dir created; no file edits applied");
            JSONArray paths = new JSONArray();
            paths.put(WT_ROOT + "/" + taskId + "/");
            o.put("paths", paths);
        } catch (SecurityException se) {
            try {
                o.put("ok", false);
                o.put("denied", true);
                o.put("error", se.getMessage());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                o.put("ok", false);
                o.put("error", e.getMessage() != null ? e.getMessage() : "IO_ERROR");
            } catch (Exception ignored) {}
        }
        return o;
    }

    public static JSONObject status(Context ctx, Uri tree) {
        JSONObject o = new JSONObject();
        try {
            DocumentFile root = requireRoot(ctx, tree);
            DocumentFile wtRoot = root.findFile(WT_ROOT);
            JSONArray list = new JSONArray();
            if (wtRoot != null && wtRoot.isDirectory()) {
                DocumentFile[] kids = wtRoot.listFiles();
                if (kids != null) {
                    for (DocumentFile k : kids) {
                        if (k.isDirectory()) {
                            JSONObject e = new JSONObject();
                            e.put("taskId", k.getName());
                            e.put("relativePath", WT_ROOT + "/" + k.getName());
                            list.put(e);
                        }
                    }
                }
            }
            o.put("ok", true);
            o.put("canonicalIntact", true);
            o.put("worktreeRoot", WT_ROOT);
            o.put("worktrees", list);
            o.put("count", list.length());
        } catch (SecurityException se) {
            try {
                o.put("ok", false);
                o.put("denied", true);
                o.put("error", se.getMessage());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                o.put("ok", false);
                o.put("error", e.getMessage() != null ? e.getMessage() : "IO_ERROR");
            } catch (Exception ignored) {}
        }
        return o;
    }
}
