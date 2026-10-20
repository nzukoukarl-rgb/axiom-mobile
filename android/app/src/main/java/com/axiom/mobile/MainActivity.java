package com.axiom.mobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

/** M1.5 UI — Karl Mode vs Lab Mode. No mutation controls (Lead). */
public class MainActivity extends Activity {
    private static final int REQ_SAF_TREE = 1001;

    static {
        try {
            System.loadLibrary("axiom_android");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    public static native String nativeIdentity();
    public static native String nativeInitWorkspace(String path);
    public static native String nativeExecute(String actionJson);

    private TextView body;
    private boolean labMode = false;
    private String lastLabDump = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0F14);
        root.setPadding(36, 48, 36, 36);

        TextView title = new TextView(this);
        title.setText("Axiom");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22f);
        root.addView(title);

        body = new TextView(this);
        body.setTextColor(0xFFD1D5DB);
        body.setTextSize(14f);
        body.setPadding(0, 24, 0, 24);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, sp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button pick = btn("Choisir dossier");
        pick.setOnClickListener(v -> openSaf());
        row.addView(pick);

        Button openWs = btn("Lire workspace");
        openWs.setOnClickListener(v -> runWorkspaceProbe());
        row.addView(openWs);

        Button mode = btn("Karl / Lab");
        mode.setOnClickListener(v -> {
            labMode = !labMode;
            render();
        });
        row.addView(mode);

        root.addView(row);
        setContentView(root);
        render();
    }

    private Button btn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(12f);
        return b;
    }

    private void openSaf() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, REQ_SAF_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAF_TREE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        final int takeFlags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
            String name = uri.getLastPathSegment();
            if (name != null && name.contains(":")) name = name.substring(name.indexOf(':') + 1);
            if (name == null || name.isEmpty()) name = "Gloria";
            JSONObject ws = WorkspaceStore.buildSafWorkspace(name, uri, true);
            WorkspaceStore.save(this, ws);
            Toast.makeText(this, "Workspace: " + name, Toast.LENGTH_SHORT).show();
            lastLabDump = ws.toString(2);
            render();
        } catch (Exception e) {
            Toast.makeText(this, "SAF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runWorkspaceProbe() {
        JSONObject ws = WorkspaceStore.getActive(this);
        if (ws == null) {
            body.setText("Aucun workspace.\nAppuie sur « Choisir dossier ».");
            return;
        }
        try {
            if (!"AUTHORIZED".equals(ws.optString("status"))) {
                body.setText("WORKSPACE_ACCESS_REVOKED");
                return;
            }
            Uri tree = Uri.parse(ws.getString("rootReference"));
            JSONObject list = SafIo.list(this, tree, "");
            lastLabDump = "workspace=" + ws.toString() + "\nlist=" + list.toString(2);
            if (list.optBoolean("ok") && list.has("entries")) {
                org.json.JSONArray entries = list.getJSONArray("entries");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject e = entries.getJSONObject(i);
                    if (!e.optBoolean("isDir")) {
                        JSONObject read = SafIo.read(this, tree, e.getString("name"));
                        lastLabDump += "\nread=" + read.toString(2);
                        break;
                    }
                }
            }
            render();
        } catch (Exception e) {
            body.setText("ERROR: " + e.getMessage());
        }
    }

    private void render() {
        JSONObject ws = WorkspaceStore.getActive(this);
        String identity = "—";
        try {
            identity = nativeIdentity();
        } catch (Throwable t) {
            identity = "native: " + t.getMessage();
        }

        if (labMode) {
            body.setText("=== LAB MODE ===\n\nidentity:\n" + identity
                    + "\n\nactive workspace:\n" + (ws != null ? ws.toString() : "null")
                    + "\n\n" + lastLabDump);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Runner\n");
        if (identity.contains("runner-karl-phone")) {
            sb.append("● runner-karl-phone\n  isolation: PATH_ONLY\n\n");
        } else {
            sb.append("○ hors ligne / native\n\n");
        }
        if (ws == null) {
            sb.append("Workspace\nAucun workspace.\n\n");
            sb.append("Appuie sur « Choisir dossier » (SAF).\n");
        } else {
            sb.append("Workspace\n📁 ").append(ws.optString("displayName", "?")).append("\n");
            sb.append("   ").append(ws.optString("provider")).append("\n");
            sb.append("   ").append(ws.optString("status")).append(" · ");
            sb.append(ws.optJSONArray("permissions")).append("\n\n");
            sb.append("Appuie sur « Lire workspace ».\n");
        }
        sb.append("\n—\nPas d'édition ici (M2 verrouillé).\n");
        body.setText(sb.toString());
    }
}
