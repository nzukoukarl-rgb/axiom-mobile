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
 * M2.3 — Edit ONLY under .axiom-worktrees/. Canonical never written.
 * Success only if file is listable + readable after write.
 */
public final class WorktreeEdit {
    private static final String WT = ".axiom-worktrees";
    private static final String TASK = "task-001";
    private static final String MARKER = "# AXIOM-M2.3: garde-fou solde carte cadeau (worktree only)";

    private WorktreeEdit() {}

    public static JSONObject apply(Context ctx) throws Exception {
        JSONObject gate = gate(ctx);
        if (!gate.optBoolean("ok", false)) return gate;

        JSONObject ws = WorkspaceStore.getActive(ctx);
        DocumentFile root = rootOf(ctx, ws);
        if (root == null) return err("WORKSPACE_ACCESS", "Dossier inaccessible");

        DocumentFile wtRoot = ensureDir(root, WT);
        if (wtRoot == null) return err("CREATE_DIR_FAILED", "Impossible de créer " + WT);
        DocumentFile taskDir = ensureDir(wtRoot, TASK);
        if (taskDir == null) return err("CREATE_DIR_FAILED", "Impossible de créer " + TASK);

        String targetName = resolveTargetName(ctx);
        String original = readChild(ctx, root, targetName);
        if (original == null) {
            original = "# " + targetName + "\n# stub worktree\n\ndef placeholder():\n    pass\n";
        }

        String patched = original.contains("AXIOM-M2.3") ? original : (MARKER + "\n" + original);

        // Prefer stable simple name if SAF chokes on existing names
        String outName = targetName;
        DocumentFile out = childFile(taskDir, outName);
        if (out == null) {
            out = taskDir.createFile("text/plain", outName);
        }
        if (out == null) {
            outName = "axiom_edit.py";
            out = childFile(taskDir, outName);
            if (out == null) out = taskDir.createFile("text/plain", outName);
        }
        if (out == null) {
            return err("WRITE_FAILED", "createFile a renvoyé null. Contenu dir: " + names(taskDir));
        }

        writeAll(ctx, out, patched);

        // Mandatory durable check
        DocumentFile listed = childFile(taskDir, out.getName() != null ? out.getName() : outName);
        if (listed == null) {
            listed = childFile(taskDir, outName);
        }
        String verify = listed != null ? readDoc(ctx, listed) : null;
        if (verify == null || !verify.contains("AXIOM-M2.3")) {
            return err("WRITE_NOT_DURABLE",
                    "Écriture non visible après coup. Fichiers dans worktree: " + names(taskDir));
        }

        JSONObject outJ = new JSONObject();
        outJ.put("ok", true);
        outJ.put("human", true);
        outJ.put("title", "Modification appliquée (worktree)");
        outJ.put("summary", outName + " sous " + WT + "/" + TASK);
        outJ.put("message", "Canonical intact. Fichiers: " + names(taskDir) + " — tape: diff show");
        outJ.put("status", "EDITED_IN_WORKTREE");
        outJ.put("canonicalIntact", true);
        return outJ;
    }

    public static JSONObject diff(Context ctx) throws Exception {
        JSONObject ws = WorkspaceStore.getActive(ctx);
        if (ws == null) return err("NO_WORKSPACE", "Choisis un dossier");
        DocumentFile root = rootOf(ctx, ws);
        if (root == null) return err("WORKSPACE_ACCESS", "Dossier inaccessible");

        DocumentFile taskDir = childDir(childDir(root, WT), TASK);
        String targetName = resolveTargetName(ctx);

        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("human", true);

        if (taskDir == null) {
            out.put("title", "Aucun diff");
            out.put("message", "Pas de dossier worktree. worktree create " + TASK);
            out.put("status", "EMPTY");
            return out;
        }

        String listed = names(taskDir);
        DocumentFile wtFile = childFile(taskDir, targetName);
        if (wtFile == null) wtFile = childFile(taskDir, "axiom_edit.py");
        // any py/txt in dir
        if (wtFile == null) {
            DocumentFile[] kids = taskDir.listFiles();
            if (kids != null) {
                for (DocumentFile k : kids) {
                    if (k != null && k.isFile()) {
                        wtFile = k;
                        break;
                    }
                }
            }
        }

        if (wtFile == null) {
            out.put("title", "Aucun diff");
            out.put("message", "Dossier worktree vide. Contenu: " + listed);
            out.put("status", "EMPTY");
            return out;
        }

        String original = readChild(ctx, root, targetName);
        if (original == null) original = "";
        String modified = readDoc(ctx, wtFile);
        if (modified == null) modified = "";

        boolean changed = !modified.equals(original);
        StringBuilder human = new StringBuilder();
        human.append("Fichier worktree: ").append(wtFile.getName()).append("\n");
        human.append("Zone: ").append(WT).append("/").append(TASK).append("\n");
        human.append("Canonical: intact\n");
        if (modified.contains("AXIOM-M2.3")) {
            human.append("+ marqueur AXIOM-M2.3 présent\n");
        }
        human.append(changed ? "Changements détectés." : "Identique à l'original.");
        human.append("\nContenu dir: ").append(listed);

        out.put("title", "Diff worktree");
        out.put("summary", wtFile.getName());
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
        out.put("message", "Proposition approuvée. edit apply → worktree seulement.");
        out.put("status", "READY");
        return out;
    }

    private static String resolveTargetName(Context ctx) {
        String targetName = "Combat_Arena.py";
        try {
            JSONObject approved = findApproved(ctx);
            if (approved != null && approved.optJSONArray("targets") != null
                    && approved.optJSONArray("targets").length() > 0) {
                targetName = approved.getJSONArray("targets").optString(0, targetName);
            }
        } catch (Exception ignored) {}
        return targetName;
    }

    private static JSONObject gate(Context ctx) throws Exception {
        JSONObject ws = WorkspaceStore.getActive(ctx);
        if (ws == null) return err("NO_WORKSPACE", "Choisis un dossier dans Réglages");
        if (!WorkspaceStore.policyAllows(ws, "repo.edit")
                && !WorkspaceStore.policyAllows(ws, "repo.worktree.create")) {
            return err("POLICY_DENY", "Écriture non autorisée par la politique");
        }
        if (findApproved(ctx) == null) {
            return err("NOT_APPROVED", "Approuve d'abord (proposal approve)");
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

    private static DocumentFile rootOf(Context ctx, JSONObject ws) {
        try {
            Uri tree = Uri.parse(ws.getString("rootReference"));
            DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
            if (root == null || !root.exists()) return null;
            return root;
        } catch (Exception e) {
            return null;
        }
    }

    private static DocumentFile ensureDir(DocumentFile parent, String name) {
        DocumentFile d = childDir(parent, name);
        if (d != null) return d;
        return parent.createDirectory(name);
    }

    private static DocumentFile childDir(DocumentFile parent, String name) {
        if (parent == null) return null;
        DocumentFile[] kids = parent.listFiles();
        if (kids == null) return null;
        for (DocumentFile k : kids) {
            if (k != null && k.isDirectory() && name.equals(k.getName())) return k;
        }
        // findFile fallback
        DocumentFile f = parent.findFile(name);
        if (f != null && f.isDirectory()) return f;
        return null;
    }

    private static DocumentFile childFile(DocumentFile parent, String name) {
        if (parent == null || name == null) return null;
        DocumentFile[] kids = parent.listFiles();
        if (kids != null) {
            for (DocumentFile k : kids) {
                if (k != null && k.isFile() && k.getName() != null) {
                    if (k.getName().equals(name) || k.getName().equalsIgnoreCase(name)) return k;
                    if (k.getName().startsWith(name)) return k;
                }
            }
        }
        DocumentFile f = parent.findFile(name);
        if (f != null && f.isFile()) return f;
        return null;
    }

    private static String names(DocumentFile dir) {
        if (dir == null) return "(null)";
        DocumentFile[] kids = dir.listFiles();
        if (kids == null || kids.length == 0) return "(vide)";
        StringBuilder sb = new StringBuilder();
        for (DocumentFile k : kids) {
            if (k == null || k.getName() == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(k.getName());
        }
        return sb.length() == 0 ? "(vide)" : sb.toString();
    }

    private static String readChild(Context ctx, DocumentFile parent, String name) {
        DocumentFile f = childFile(parent, name);
        return f != null ? readDoc(ctx, f) : null;
    }

    private static String readDoc(Context ctx, DocumentFile f) {
        try (InputStream in = ctx.getContentResolver().openInputStream(f.getUri())) {
            if (in == null) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeAll(Context ctx, DocumentFile f, String content) throws Exception {
        // "w" only — "wt" is unreliable on many SAF providers
        try (OutputStream out = ctx.getContentResolver().openOutputStream(f.getUri(), "w")) {
            if (out == null) throw new Exception("no stream");
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
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
