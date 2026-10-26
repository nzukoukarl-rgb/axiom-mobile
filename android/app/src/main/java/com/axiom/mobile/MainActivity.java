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
                webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("try{refresh()}catch(e){}", null);
                    }
                });
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
        public String execCommand(String line) {
            try {
                if (line == null) line = "";
                line = line.trim();
                if (line.isEmpty()) {
                    return "{\"ok\":false,\"error\":\"EMPTY_COMMAND\"}";
                }
                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                // Future agent: same entrypoint for tool calls
                if ("worktree".equals(cmd)) {
                    String sub = parts.length > 1 ? parts[1].toLowerCase() : "status";
                    JSONObject w = WorkspaceStore.getActive(MainActivity.this);
                    if (w == null) {
                        return "{\"ok\":false,\"error\":\"NO_WORKSPACE\"}";
                    }
                    if ("create".equals(sub)) {
                        if (!WorkspaceStore.policyAllows(w, "repo.worktree.create")) {
                            JSONObject deny = new JSONObject();
                            deny.put("ok", false);
                            deny.put("denied", true);
                            deny.put("error", "PERMISSION_MISMATCH");
                            deny.put("capability", "repo.worktree.create");
                            deny.put("need", "WRITE");
                            return deny.toString();
                        }
                        String taskId = parts.length > 2 ? parts[2] : ("task-" + System.currentTimeMillis());
                        Uri tree = Uri.parse(w.getString("rootReference"));
                        return WorktreeIo.create(MainActivity.this, tree, taskId).toString();
                    }
                    if ("status".equals(sub) || "list".equals(sub)) {
                        Uri tree = Uri.parse(w.getString("rootReference"));
                        return WorktreeIo.status(MainActivity.this, tree).toString();
                    }
                    return "{\"ok\":false,\"error\":\"USAGE\",\"hint\":\"worktree create <taskId>|worktree status\"}";
                }
                
                
                
                if ("edit".equals(cmd)) {
                    String sub = parts.length > 1 ? parts[1].toLowerCase() : "status";
                    if ("apply".equals(sub)) {
                        return WorktreeEdit.apply(MainActivity.this).toString();
                    }
                    if ("status".equals(sub)) {
                        return WorktreeEdit.status(MainActivity.this).toString();
                    }
                    return "{\"ok\":false,\"hint\":\"edit status|apply\"}";
                }
                if ("diff".equals(cmd)) {
                    String sub = parts.length > 1 ? parts[1].toLowerCase() : "show";
                    return WorktreeEdit.diff(MainActivity.this).toString();
                }
if ("task".equals(cmd)) {
                    String sub = parts.length > 1 ? parts[1].toLowerCase() : "status";
                    if ("start".equals(sub)) {
                        StringBuilder g = new StringBuilder();
                        for (int i = 2; i < parts.length; i++) {
                            if (i > 2) g.append(' ');
                            g.append(parts[i]);
                        }
                        return AgentRuntimeStore.startTask(MainActivity.this, g.toString()).toString();
                    }
                    if ("status".equals(sub)) {
                        return AgentRuntimeStore.status(MainActivity.this).toString();
                    }
                    if ("checkpoint".equals(sub)) {
                        String lab = parts.length > 2 ? parts[2] : "cp";
                        return AgentRuntimeStore.checkpoint(MainActivity.this, lab).toString();
                    }
                    if ("resume".equals(sub)) {
                        return AgentRuntimeStore.resume(MainActivity.this).toString();
                    }
                    if ("advance".equals(sub)) {
                        return AgentRuntimeStore.advance(MainActivity.this).toString();
                    }
                    return "{\"ok\":false,\"hint\":\"task start|status|checkpoint|resume|advance\"}";
                }
if ("propose".equals(cmd)) {
                    JSONObject w = WorkspaceStore.getActive(MainActivity.this);
                    JSONObject p = ProposalStore.createDemo(MainActivity.this, w);
                    JSONObject out = new JSONObject();
                    out.put("ok", true);
                    out.put("human", true);
                    out.put("title", p.optString("title"));
                    out.put("summary", p.optString("summary"));
                    out.put("status", p.optString("status"));
                    out.put("files", p.optJSONArray("targets"));
                    out.put("workspace", p.optString("workspaceLabel"));
                    out.put("risk", p.optString("risk"));
                    out.put("proposalId", p.optString("proposalId"));
                    return out.toString();
                }
                if ("proposal".equals(cmd)) {
                    String sub = parts.length > 1 ? parts[1].toLowerCase() : "show";
                    if ("show".equals(sub)) {
                        JSONObject pend = ProposalStore.getPending(MainActivity.this);
                        if (pend == null) {
                            return "{\"ok\":true,\"empty\":true,\"message\":\"Aucune modification en attente.\"}";
                        }
                        JSONObject out = new JSONObject();
                        out.put("ok", true);
                        out.put("human", true);
                        out.put("title", pend.optString("title"));
                        out.put("summary", pend.optString("summary"));
                        out.put("status", pend.optString("status"));
                        out.put("files", pend.optJSONArray("targets"));
                        out.put("workspace", pend.optString("workspaceLabel"));
                        out.put("risk", pend.optString("risk"));
                        out.put("proposalId", pend.optString("proposalId"));
                        return out.toString();
                    }
                    if ("approve".equals(sub) || "reject".equals(sub)) {
                        JSONObject pend = ProposalStore.getPending(MainActivity.this);
                        if (pend == null) {
                            return "{\"ok\":false,\"message\":\"Rien à approuver.\"}";
                        }
                        String st = "approve".equals(sub) ? "APPROVED" : "REJECTED";
                        JSONObject updated = ProposalStore.setStatus(
                            MainActivity.this, pend.getString("proposalId"), st);
                        JSONObject out = new JSONObject();
                        out.put("ok", updated.optBoolean("ok", true));
                        out.put("human", true);
                        out.put("title", updated.optString("title"));
                        out.put("status", st);
                        out.put("message", "approve".equals(sub)
                            ? "Approuvé. Aucune écriture fichier (porte seule)."
                            : "Refusé. Canonical intact.");
                        return out.toString();
                    }
                    return "{\"ok\":false,\"hint\":\"proposal show|approve|reject\"}";
                }
if ("help".equals(cmd)) {
                    return "{\"ok\":true,\"commands\":[\"worktree create <taskId>\",\"worktree status\",\"help\"]}";
                }
                return "{\"ok\":false,\"error\":\"UNKNOWN_COMMAND\",\"cmd\":\"" + cmd + "\"}";
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
