package com.axiom.mobile;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.security.MessageDigest;

public final class SafIo {
    private SafIo() {}

    private static DocumentFile resolve(DocumentFile root, String relative) throws SecurityException {
        if (relative == null) relative = "";
        String p = relative.replace("\\", "/").trim();
        while (p.startsWith("./")) p = p.substring(2);
        if (p.startsWith("/") || p.startsWith("\~") || p.equals("..") || p.startsWith("../")
                || p.contains("/../") || p.contains("/..")) {
            throw new SecurityException("PATH_TRAVERSAL_DENIED");
        }
        if (p.isEmpty() || p.equals(".")) return root;
        DocumentFile cur = root;
        for (String part : p.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) throw new SecurityException("PATH_TRAVERSAL_DENIED");
            DocumentFile next = cur.findFile(part);
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }

    public static JSONObject list(Context ctx, Uri tree, String relative) {
        JSONObject o = new JSONObject();
        try {
            DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
            if (root == null || !root.exists()) {
                o.put("ok", false);
                o.put("denied", true);
                o.put("error", "WORKSPACE_ACCESS_REVOKED");
                return o;
            }
            DocumentFile dir = resolve(root, relative);
            if (dir == null || !dir.exists()) {
                o.put("ok", false);
                o.put("error", "NOT_FOUND");
                return o;
            }
            JSONArray entries = new JSONArray();
            DocumentFile[] files = dir.listFiles();
            if (files != null) {
                for (DocumentFile f : files) {
                    JSONObject e = new JSONObject();
                    e.put("name", f.getName());
                    e.put("isDir", f.isDirectory());
                    e.put("length", f.length());
                    entries.put(e);
                }
            }
            o.put("ok", true);
            o.put("denied", false);
            o.put("entries", entries);
            o.put("count", entries.length());
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

    public static JSONObject read(Context ctx, Uri tree, String relative) {
        JSONObject o = new JSONObject();
        try {
            DocumentFile root = DocumentFile.fromTreeUri(ctx, tree);
            if (root == null || !root.exists()) {
                o.put("ok", false);
                o.put("denied", true);
                o.put("error", "WORKSPACE_ACCESS_REVOKED");
                return o;
            }
            DocumentFile doc = resolve(root, relative);
            if (doc == null || !doc.exists() || doc.isDirectory()) {
                o.put("ok", false);
                o.put("denied", false);
                o.put("error", "NOT_FOUND");
                return o;
            }
            byte[] data;
            try (InputStream in = ctx.getContentResolver().openInputStream(doc.getUri())) {
                if (in == null) {
                    o.put("ok", false);
                    o.put("error", "IO_ERROR");
                    return o;
                }
                data = readAll(in, 512 * 1024);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : dig) hex.append(String.format("%02x", b));
            String preview = new String(data, 0, Math.min(data.length, 200), java.nio.charset.StandardCharsets.UTF_8);

            JSONObject payload = new JSONObject();
            payload.put("path", relative);
            payload.put("byteLength", data.length);
            payload.put("contentHash", hex.toString());
            payload.put("contentPreview", preview);
            payload.put("contentIsData", true);

            o.put("ok", true);
            o.put("denied", false);
            o.put("payload", payload);
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

    private static byte[] readAll(InputStream in, int max) throws Exception {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n, total = 0;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > max) break;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
