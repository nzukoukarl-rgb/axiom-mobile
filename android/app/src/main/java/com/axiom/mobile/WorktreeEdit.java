package com.axiom.mobile;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * M2.3 — Edit ONLY under .axiom-worktrees/. Canonical never written here.
 * Requires ActionProposal status APPROVED.
 */
public final class WorktreeEdit {
    private static final String WT = ".axiom-worktrees";

    private WorktreeEdit() {}

    public static JSONObject apply(Context ctx) throws Exception {
        JSONObject gate = gate(ctx);
        if (!gate.optBoolean("ok", false)) return gate;

        JSONObject ws = WorkspaceStore.getActive(ctx);
        Uri tree = Uri.parse(ws.getString("treeUri"));
        DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
        if (root == null || !root.exists()) {
            return err("WORKSPACE_ACCESS", "Dossier inaccessible");
        }

        String taskId = "task-001";
        DocumentFile wtRoot = ensureDir(root, WT);
        DocumentFile taskDir = ensureDir(wtRoot, taskId);

        String targetName = "Combat_Arena.py";
        JSONObject pend = ProposalStore.getPending(ctx);
        // After approve, pending is empty — use last APPROVED from store
        JSONObject approved = findApproved(ctx);
        if (approved != null && approved.optJSONArray("targets") != null
                && approved.optJSONArray("targets").length() > 0) {
            targetName = approved.getJSONArray("targets").optString(0, targetName);
        }

        String original = readNamed(ctx, root, targetName);
        if (original == null) {
            original = "# " + targetName + "\n# (fichier absent du workspace — stub worktree)\n\ndef placeholder():\n    pass\n";
        }

        String marker = "# AXIOM-M2.3: garde-fou solde carte cadeau (worktree only)\n";
        String patched;
        if (original.contains("AXIOM-M2.3")) {
            patched = original;
        } else {
            patched = marker + original;
        }

        DocumentFile out = taskDir.findFile(targetName);
        if (out == null) {
            out = taskDir.createFile("text/plain", targetName);
        }
        if (out == null) {
            return err("WRITE_FAILED", "Impossible de créer le fichier worktree");
        }
        writeAll(ctx, out, patched);

        JSONObject evidence = new JSONObject();
        evidence.put("kind", "worktree_edit");
        evidence.put("target", targetName);
        evidence.put("worktree", WT + "/" + taskId + "/" + targetName);
        evidence.put("canonicalIntact", true);
        evidence.put("bytes", patched.getBytes(StandardCharsets.UTF_8).length);

        JSONObject outJ = new JSONObject();
        outJ.put("ok", true);
        outJ.put("human", true);
        outJ.put("title", "Modification appliquée (worktree)");
        outJ.put("summary", targetName + " modifié uniquement sous " + WT + "/" + taskId);
        outJ.put("message", "Canonical intact. Tape: diff show");
        outJ.put("status", "EDITED_IN_WORKTREE");
        outJ.put("evidence", evidence);
        return outJ;
    }

    public static JSONObject diff(Context ctx) throws Exception {
        JSONObject ws = WorkspaceStore.getActive(ctx);
        if (ws == null) return err("NO_WORKSPACE", "Choisis un dossier");

        Uri tree = Uri.parse(ws.getString("treeUri"));
        DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
        if (root == null) return err("WORKSPACE_ACCESS", "Dossier inaccessible");

        String taskId = "task-001";
        String targetName = "Combat_Arena.py";
        JSONObject approved = findApproved(ctx);
        if (approved != null && approved.optJSONArray("targets") != null
                && approved.optJSONArray("targets").length() > 0) {
            targetName = approved.getJSONArray("targets").optString(0, targetName);
        }

        DocumentFile wtRoot = root.findFile(WT);
        DocumentFile taskDir = wtRoot != null ? wtRoot.findFile(taskId) : null;
        DocumentFile wtFile = taskDir != null ? taskDir.findFile(targetName) : null;

        String original = readNamed(ctx, root, targetName);
        if (original == null) original = "";
        String modified = wtFile != null ? readDoc(ctx, wtFile) : null;

        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("human", true);
        if (modified == null) {
            out.put("title", "Aucun diff");
            out.put("message", "Pas de fichier worktree. Approve puis: edit apply");
            out.put("status", "EMPTY");
            return out;
        }

        boolean changed = !modified.equals(original);
        StringBuilder human = new StringBuilder();
        human.append("Fichier: ").append(targetName).append("\n");
        human.append("Zone: worktree seulement\n");
        human.append("Canonical: intact\n");
        if (modified.contains("AXIOM-M2.3")) {
            human.append("+ ligne garde-fou AXIOM-M2.3 ajoutée en tête\n");
        }
        human.append(changed ? "Changements détectés." : "Identique à l'original.");

        out.put("title", "Diff worktree");
        out.put("summary", targetName);
        out.put("message", human.toString());
        out.put("status", changed ? "HAS_DIFF" : "NO_CHANGE");
        out.put("canonicalIntact", true);
        return out;
    }

    public static JSONObject status(Context ctx) throws Exception {
        JSONObject approved = findApproved(ctx);
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("human", true);
        if (approved == null) {
            out.put("message", "Aucune proposition APPROVED. propose → proposal approve → edit apply");
            out.put("status", "BLOCKED");
            return out;
        }
        out.put("title", "Prêt à éditer");
        out.put("summary", approved.optString("title"));
        out.put("message", "Proposition approuvée. edit apply écrira seulement dans le worktree.");
        out.put("status", "READY");
        return out;
    }

    private static JSONObject gate(Context ctx) throws Exception {
        JSONObject ws = WorkspaceStore.getActive(ctx);
        if (ws == null) return err("NO_WORKSPACE", "Choisis un dossier dans Réglages");

        if (!WorkspaceStore.policyAllows(ws, "repo.edit", "WRITE")) {
            // fallback: worktree create policy
            if (!WorkspaceStore.policyAllows(ws, "repo.worktree.create", "WRITE")) {
                return err("POLICY_DENY", "Écriture non autorisée par la politique");
            }
        }

        JSONObject approved = findApproved(ctx);
        if (approved == null) {
            return err("NOT_APPROVED", "Approuve d'abord une proposition (proposal approve)");
        }
        JSONObject ok = new JSONObject();
        ok.put("ok", true);
        return ok;
    }

    private static JSONObject findApproved(Context ctx) {
        try {
            JSONArray arr = ProposalStore.loadAll(ctx);
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                if ("APPROVED".equals(o.optString("status"))) return o;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static DocumentFile ensureDir(DocumentFile parent, String name) {
        DocumentFile d = parent.findFile(name);
        if (d != null && d.isDirectory()) return d;
        return parent.createDirectory(name);
    }

    private static String readNamed(Context ctx, DocumentFile root, String name) {
        DocumentFile f = root.findFile(name);
        if (f == null || !f.isFile()) return null;
        return readDoc(ctx, f);
    }

    private static String readDoc(Context ctx, DocumentFile f) {
        try (InputStream in = ctx.getContentResolver().openInputStream(f.getUri())) {
            if (in == null) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeAll(Context ctx, DocumentFile f, String content) throws Exception {
        try (OutputStream out = ctx.getContentResolver().openOutputStream(f.getUri(), "wt")) {
            if (out == null) throw new Exception("no stream");
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static JSONObject err(String code, String message) throws Exception {
        JSONObject e = new JSONObject();
        e.put("ok", false);
        e.put("human", true);
        e.put("error", code);
        e.put("message", message);
        e.put("title", "Edit bloqué");
        return e;
    }
}
