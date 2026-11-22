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
import java.security.MessageDigest;

/**
 * M2.3 SAF — OtherWS/AxiomWorktrees/ (visible, no leading dot).
 * CREATE → WRITE → CLOSE → EXISTS → READ-BACK → HASH.
 * Canonical never written. No silent app-private fallback.
 */
public final class WorktreeEdit {
    public static final String WT = "AxiomWorktrees";
    private static final String TASK = "task-001";
    private static final String PROOF_NAME = "proof.txt";
    private static final String PROOF_CONTENT = "AXIOM_WRITE_PROOF_001\n";
    private static final String MARKER = "# AXIOM-M2.3: garde-fou solde carte cadeau (worktree only)";

    private WorktreeEdit() {}

    public static JSONObject create(Context ctx, String taskId) throws Exception {
        if (taskId == null || taskId.trim().isEmpty()) taskId = TASK;
        taskId = taskId.trim();
        if (WorkspaceStore.getActive(ctx) == null) {
            return fail("NO_WORKSPACE", "Choisis un dossier dans Settings", "gate");
        }
        DocumentFile root = rootOf(ctx);
        if (root == null) return fail("WORKSPACE_ACCESS_REVOKED", "Dossier inaccessible ou revoque", "createDirectory");

        DocumentFile wtRoot = ensureDir(root, WT);
        if (wtRoot == null) return fail("CREATE_DIR_FAILED", "Impossible de creer " + WT, "createDirectory");
        DocumentFile taskDir = ensureDir(wtRoot, taskId);
        if (taskDir == null) return fail("CREATE_DIR_FAILED", "Impossible de creer " + taskId, "createDirectory");

        JSONObject o = ok();
        o.put("title", "Worktree pret");
        o.put("message", "Visible: " + WT + "/" + taskId
                + "\nlistFiles: " + names(taskDir)
                + "\nOuvre OtherWS → AxiomWorktrees dans Fichiers Android.");
        o.put("status", "DIR_EXISTS");
        o.put("relativePath", WT + "/" + taskId);
        o.put("listFiles", names(taskDir));
        o.put("canonicalIntact", true);
        return o;
    }

    public static JSONObject proof(Context ctx) throws Exception {
        if (findApproved(ctx) == null) {
            return fail("NOT_APPROVED", "proposal approve d abord", "gate");
        }
        DocumentFile root = rootOf(ctx);
        if (root == null) return fail("WORKSPACE_ACCESS_REVOKED", "Dossier inaccessible", "createDirectory");

        DocumentFile wtRoot = ensureDir(root, WT);
        if (wtRoot == null) return fail("CREATE_DIR_FAILED", "Echec " + WT, "createDirectory");
        DocumentFile taskDir = ensureDir(wtRoot, TASK);
        if (taskDir == null) return fail("CREATE_DIR_FAILED", "Echec " + TASK, "createDirectory");

        DocumentFile proof = childFile(taskDir, PROOF_NAME);
        if (proof == null) proof = taskDir.createFile("text/plain", PROOF_NAME);
        if (proof == null) {
            return fail("CREATE_FILE_FAILED", "createFile null. list=" + names(taskDir), "createFile");
        }
        try {
            writeAll(ctx, proof, PROOF_CONTENT);
        } catch (Exception e) {
            return fail("WRITE_FAILED", "write: " + e.getMessage(), "write");
        }

        DocumentFile listed = childFile(taskDir, PROOF_NAME);
        if (listed == null) {
            return fail("EXISTS_FAILED", "proof.txt absent apres write. list=" + names(taskDir), "exists");
        }
        String readBack = readDoc(ctx, listed);
        if (readBack == null) {
            return fail("READ_BACK_FAILED", "openInputStream null", "read-back");
        }
        if (!readBack.contains("AXIOM_WRITE_PROOF_001")) {
            return fail("READ_BACK_MISMATCH", "contenu relu inattendu: " + readBack.trim(), "read-back");
        }

        String hash = sha256(readBack);
        JSONObject o = ok();
        o.put("title", "Preuve d ecriture OK");
        StringBuilder msg = new StringBuilder();
        msg.append("createDirectory: PASS\ncreateFile: PASS\nwrite: PASS\nexists: PASS\nread-back: PASS\n");
        msg.append("hash: ").append(hash.substring(0, 12)).append("...\n");
        msg.append("listFiles: ").append(names(taskDir)).append('\n');
        msg.append("relativePath: ").append(WT).append('/').append(TASK).append('/').append(PROOF_NAME).append('\n');
        msg.append("Ouvre Fichiers → OtherWS → AxiomWorktrees → task-001 → proof.txt");
        o.put("message", msg.toString());
        o.put("status", "PROOF_OK");
        o.put("sha256", hash);
        o.put("relativePath", WT + "/" + TASK + "/" + PROOF_NAME);
        o.put("listFiles", names(taskDir));
        o.put("canonicalIntact", true);
        return o;
    }

    public static JSONObject apply(Context ctx) throws Exception {
        JSONObject gate = gate(ctx);
        if (!gate.optBoolean("ok", false)) return gate;

        DocumentFile root = rootOf(ctx);
        if (root == null) return fail("WORKSPACE_ACCESS_REVOKED", "Dossier inaccessible", "createDirectory");

        DocumentFile taskDir = childDir(childDir(root, WT), TASK);
        if (taskDir == null) {
            return fail("PROOF_REQUIRED", "Fais: edit create puis edit proof. Pas de " + WT + "/" + TASK, "exists");
        }
        DocumentFile proofFile = childFile(taskDir, PROOF_NAME);
        if (proofFile == null) {
            return fail("PROOF_REQUIRED", "proof.txt manquant. edit proof d abord. list=" + names(taskDir), "exists");
        }
        String proofBody = readDoc(ctx, proofFile);
        if (proofBody == null || !proofBody.contains("AXIOM_WRITE_PROOF_001")) {
            return fail("PROOF_INVALID", "proof.txt illisible ou invalide", "read-back");
        }

        String targetName = resolveTargetName(ctx);
        String original = readChild(ctx, root, targetName);
        if (original == null) original = "# " + targetName + "\n# canonical unavailable\n";
        String patched = original.contains("AXIOM-M2.3") ? original : (MARKER + "\n" + original);

        DocumentFile out = childFile(taskDir, targetName);
        if (out == null) out = taskDir.createFile("text/plain", targetName);
        if (out == null) {
            return fail("CREATE_FILE_FAILED", "createFile " + targetName + " null. list=" + names(taskDir), "createFile");
        }
        try {
            writeAll(ctx, out, patched);
        } catch (Exception e) {
            return fail("WRITE_FAILED", e.getMessage(), "write");
        }

        DocumentFile listed = childFile(taskDir, out.getName() != null ? out.getName() : targetName);
        String verify = listed != null ? readDoc(ctx, listed) : null;
        if (verify == null || !verify.contains("AXIOM-M2.3")) {
            return fail("WRITE_NOT_DURABLE", "Mutation non relue. list=" + names(taskDir), "read-back");
        }

        JSONObject o = ok();
        o.put("title", "Mutation worktree prouvee");
        o.put("message", "Fichier: " + WT + "/" + TASK + "/" + (listed.getName() != null ? listed.getName() : targetName)
                + "\nhash=" + sha256(verify).substring(0, 12) + "..."
                + "\nlistFiles: " + names(taskDir)
                + "\nCanonical non modifie. Visible dans Fichiers Android.");
        o.put("status", "MUTATION_PROVEN");
        o.put("sha256", sha256(verify));
        o.put("relativePath", WT + "/" + TASK + "/" + (listed.getName() != null ? listed.getName() : targetName));
        o.put("listFiles", names(taskDir));
        o.put("canonicalIntact", true);
        return o;
    }

    public static JSONObject diff(Context ctx) throws Exception {
        DocumentFile root = rootOf(ctx);
        if (root == null) return fail("WORKSPACE_ACCESS_REVOKED", "Dossier inaccessible", "exists");

        DocumentFile taskDir = childDir(childDir(root, WT), TASK);
        JSONObject out = ok();
        if (taskDir == null) {
            out.put("title", "Aucun diff");
            out.put("message", "Pas de " + WT + "/" + TASK + ". edit create / edit proof");
            out.put("status", "EMPTY");
            return out;
        }

        String listed = names(taskDir);
        String targetName = resolveTargetName(ctx);
        DocumentFile wtFile = childFile(taskDir, targetName);
        DocumentFile proof = childFile(taskDir, PROOF_NAME);

        StringBuilder human = new StringBuilder();
        human.append("Zone: ").append(WT).append("/").append(TASK).append('\n');
        human.append("listFiles: ").append(listed).append('\n');
        human.append("Canonical: intact\n");

        boolean any = false;
        if (proof != null) {
            any = true;
            String body = readDoc(ctx, proof);
            human.append("proof.txt: ").append(body != null ? body.trim() : "?").append('\n');
            if (body != null) human.append("proof hash=").append(sha256(body).substring(0, 12)).append("...\n");
        }
        if (wtFile != null) {
            any = true;
            String modified = readDoc(ctx, wtFile);
            String original = readChild(ctx, root, targetName);
            if (original == null) original = "";
            if (modified == null) modified = "";
            human.append("worktree file: ").append(wtFile.getName()).append('\n');
            if (modified.contains("AXIOM-M2.3")) human.append("+ marqueur AXIOM-M2.3\n");
            human.append(modified.equals(original) ? "Identique a original.\n" : "Changements detectes vs canonical.\n");
            human.append("hash=").append(sha256(modified).substring(0, 12)).append("...\n");
        }

        if (!any) {
            out.put("title", "Aucun diff");
            out.put("message", "Dossier worktree vide. list=" + listed);
            out.put("status", "EMPTY");
            return out;
        }
        out.put("title", "Diff worktree (SAF)");
        out.put("message", human.toString().trim());
        out.put("status", "HAS_DIFF");
        out.put("listFiles", listed);
        out.put("canonicalIntact", true);
        return out;
    }

    public static JSONObject status(Context ctx) throws Exception {
        JSONObject approved = findApproved(ctx);
        DocumentFile root = rootOf(ctx);
        DocumentFile taskDir = root != null ? childDir(childDir(root, WT), TASK) : null;

        JSONObject out = ok();
        out.put("title", "Etat edit / worktree");
        StringBuilder msg = new StringBuilder();
        msg.append("Proposition: ").append(approved != null ? "APPROVED" : "absente").append('\n');
        msg.append("Worktree: ").append(taskDir != null ? (WT + "/" + TASK) : "absent").append('\n');
        if (taskDir != null) {
            msg.append("listFiles: ").append(names(taskDir)).append('\n');
            DocumentFile pf = childFile(taskDir, PROOF_NAME);
            msg.append("proof.txt: ").append(pf != null ? "present" : "absent").append('\n');
        }
        msg.append("Commandes: edit create | edit proof | edit apply | diff show");
        out.put("message", msg.toString());
        out.put("status", taskDir != null ? "DIR_EXISTS" : (approved != null ? "READY" : "BLOCKED"));
        out.put("canonicalIntact", true);
        return out;
    }

    private static JSONObject gate(Context ctx) throws Exception {
        JSONObject active = WorkspaceStore.getActive(ctx);
        if (active == null) return fail("NO_WORKSPACE", "Choisis un dossier dans Settings", "gate");
        if (!WorkspaceStore.policyAllows(active, "repo.edit")
                && !WorkspaceStore.policyAllows(active, "repo.worktree.create")) {
            return fail("POLICY_DENY", "Ecriture non autorisee par la politique", "gate");
        }
        if (findApproved(ctx) == null) {
            return fail("NOT_APPROVED", "Approuve d abord (proposal approve)", "gate");
        }
        return ok();
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

    private static DocumentFile rootOf(Context ctx) {
        try {
            JSONObject ws = WorkspaceStore.getActive(ctx);
            if (ws == null) return null;
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
        DocumentFile created = parent.createDirectory(name);
        if (created == null) return null;
        return childDir(parent, name);
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

    private static DocumentFile childFile(DocumentFile parent, String name) {
        if (parent == null || name == null) return null;
        DocumentFile[] kids = parent.listFiles();
        if (kids != null) {
            for (DocumentFile k : kids) {
                if (k != null && k.isFile() && k.getName() != null) {
                    if (k.getName().equals(name) || k.getName().equalsIgnoreCase(name)) return k;
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
        try (OutputStream out = ctx.getContentResolver().openOutputStream(f.getUri(), "w")) {
            if (out == null) throw new Exception("openOutputStream null");
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static JSONObject ok() throws Exception {
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("human", true);
        return o;
    }

    private static JSONObject fail(String code, String message, String step) throws Exception {
        JSONObject e = new JSONObject();
        e.put("ok", false);
        e.put("human", true);
        e.put("error", code);
        e.put("step", step);
        e.put("title", "Edit bloque");
        e.put("message", "[" + step + "] " + message);
        e.put("status", code);
        return e;
    }
}
