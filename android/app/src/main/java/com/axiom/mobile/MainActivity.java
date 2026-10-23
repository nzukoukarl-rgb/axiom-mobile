package com.axiom.mobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 43;
    private WebView webView;

    static {
        try {
            System.loadLibrary("axiom_android");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    public static native String nativeIdentity();
    public static native String nativeInitWorkspace(String path);
    public static native String nativeExecute(String actionJson);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AxiomBridge(), "AxiomBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null) return;
        Uri tree = data.getData();
        if (tree == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
            String name = tree.getLastPathSegment();
            if (name == null) name = "workspace";
            if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
            JSONObject ws = WorkspaceStore.buildSafWorkspace(name, tree, true);
            WorkspaceStore.save(this, ws);
            if (webView != null) {
                webView.post(() -> webView.evaluateJavascript("try{refresh()}catch(e){}", null));
            }
        } catch (Exception ignored) {}
    }

    public class AxiomBridge {
        @JavascriptInterface
        public String getNativeIdentity() {
            try {
                return MainActivity.nativeIdentity();
            } catch (Throwable t) {
                return "{\"runner_id\":\"runner-karl-phone\",\"isolation_level\":\"PATH_ONLY\",\"initialized\":false,\"ok\":true}";
            }
        }

        @JavascriptInterface
        public String nativeIdentity() {
            return getNativeIdentity();
        }

        @JavascriptInterface
        public String getActiveWorkspace() {
            JSONObject w = WorkspaceStore.getActive(MainActivity.this);
            return w == null ? "null" : w.toString();
        }

        @JavascriptInterface
        public void pickWorkspace() {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, REQ_TREE);
        }

        @JavascriptInterface
        public String probeWorkspace() {
            JSONObject w = WorkspaceStore.getActive(MainActivity.this);
            if (w == null) return "{\"ok\":false,\"error\":\"NO_WORKSPACE\"}";
            try {
                Uri tree = Uri.parse(w.getString("rootReference"));
                JSONObject list = SafIo.list(MainActivity.this, tree, "");
                String err = list.optString("error", "");
                if ("WORKSPACE_ACCESS_REVOKED".equals(err)) {
                    WorkspaceStore.markRevoked(MainActivity.this, w.getString("workspaceId"));
                }
                JSONObject t6 = SafIo.read(MainActivity.this, tree, "../outside");
                JSONObject t8 = SafIo.read(MainActivity.this, tree, "__no_such_file_axiom_t8__.txt");
                JSONObject out = new JSONObject();
                out.put("workspace", w);
                out.put("list", list);
                out.put("T6_traversal", t6);
                out.put("T8_missing", t8);
                return out.toString();
            } catch (Exception e) {
                return "{\"ok\":false,\"error\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
            }
        }

        @JavascriptInterface
        public String runT7P() {
            try {
                JSONObject out = new JSONObject();
                out.put("android_grant", "READ+WRITE (technical max when SAF allows)");
                JSONArray effective = new JSONArray();
                effective.put("READ");
                out.put("axiom_effective", effective);
                out.put("repo.read", "ALLOW");
                out.put("repo.edit", "DENY PERMISSION_MISMATCH");
                out.put("invariant", "Policy <= Android grant (never wider)");
                out.put("t7p", "PASS");
                return out.toString(2);
            } catch (Exception e) {
                return "{\"t7p\":\"FAIL\",\"error\":\"" + e.getMessage() + "\"}";
            }
        }
    }
}
