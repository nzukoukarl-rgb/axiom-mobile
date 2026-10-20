package com.axiom.mobile;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.security.MessageDigest;

/** ANDROID_SAF I/O via DocumentFile. Relative paths only. */
public final class SafIo {
    private SafIo() {}

    public static JSONObject list(Context ctx, Uri tree, String relative) throws Exception {
        DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
        if (root == null || !root.exists()) {
            return err("WORKSPACE_ACCESS_REVOKED");
        }
        DocumentFile dir = resolve(root, relative);
        if (dir == null || !dir.isDirectory()) {
            return err("NOT_FOUND");
        }
        JSONArray entries = new JSONArray();
        DocumentFile[] kids = dir.listFiles();
        if (kids != null) {
            for (DocumentFile f : kids) {
                JSONObject e = new JSONObject();
                e.put("name", f.getName());
                e.put("isDir", f.isDirectory());
                e.put("length", f.length());
                entries.put(e);
            }
        }
        JSONObject ok = new JSONObject();
        ok.put("ok", true);
        ok.put("denied", false);
        ok.put("entries", entries);
        ok.put("count", entries.length());
        return ok;
    }

    public static JSONObject read(Context ctx, Uri tree, String relative) throws Exception {
        if (relative == null || relative.isEmpty()) return err("EMPTY_PATH");
        if (relative.contains("..") || relative.startsWith("/")) {
            return err("PATH_TRAVERSAL_DENIED");
        }
        DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
        if (root == null || !root.exists()) {
            return err("WORKSPACE_ACCESS_REVOKED");
        }
        DocumentFile file = resolve(root, relative);
        if (file == null || !file.isFile()) {
            return err("NOT_FOUND");
        }
        InputStream in = ctx.getContentResolver().openInputStream(file.getUri());
        if (in == null) return err("NOT_FOUND");
        byte[] buf = readAll(in);
        in.close();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] dig = md.digest(buf);
        StringBuilder hex = new StringBuilder();
        for (byte b : dig) hex.append(String.format("%02x", b));
        String preview = new String(buf, 0, Math.min(buf.length, 400), "UTF-8");
        JSONObject payload = new JSONObject();
        payload.put("path", relative);
        payload.put("byteLength", buf.length);
        payload.put("contentHash", hex.toString());
        payload.put("contentPreview", preview);
        payload.put("contentIsData", true);
        JSONObject ok = new JSONObject();
        ok.put("ok", true);
        ok.put("denied", false);
        ok.put("payload", payload);
        return ok;
    }

    private static DocumentFile resolve(DocumentFile root, String relative) {
        if (relative == null || relative.isEmpty() || ".".equals(relative)) return root;
        String[] parts = relative.replace('\\', '/').split("/");
        DocumentFile cur = root;
        for (String p : parts) {
            if (p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p)) return null;
            DocumentFile next = null;
            DocumentFile[] kids = cur.listFiles();
            if (kids == null) return null;
            for (DocumentFile k : kids) {
                if (p.equals(k.getName())) {
                    next = k;
                    break;
                }
            }
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) >= 0) bos.write(b, 0, n);
        return bos.toByteArray();
    }

    private static JSONObject err(String code) throws Exception {
        JSONObject o = new JSONObject();
        o.put("ok", false);
        o.put("denied", code.contains("DENIED") || code.contains("REVOKED") || code.contains("MISMATCH"));
        o.put("error", code);
        return o;
    }
}
