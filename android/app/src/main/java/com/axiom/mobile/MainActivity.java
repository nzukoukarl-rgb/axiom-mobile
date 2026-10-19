package com.axiom.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    static {
        try {
            System.loadLibrary("axiom_android");
        } catch (UnsatisfiedLinkError e) {
            // .so missing
        }
    }

    public static native String nativeIdentity();
    public static native String nativeInitWorkspace(String path);
    public static native String nativeExecute(String actionJson);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setTextSize(13f);
        tv.setPadding(32, 48, 32, 48);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(tv);
        setContentView(scroll);

        StringBuilder out = new StringBuilder();
        try {
            // 1) Workspace = private app files (no SAF needed for M0.5)
            File ws = getFilesDir();
            if (!ws.exists()) ws.mkdirs();

            // Seed a file so repo.read has something real
            File readme = new File(ws, "README-AXIOM.txt");
            if (!readme.exists()) {
                FileOutputStream fos = new FileOutputStream(readme);
                fos.write("Axiom Karl phone workspace\nM0.5 repo.read demo\n".getBytes("UTF-8"));
                fos.close();
            }

            out.append("=== 1. IDENTITY ===\n");
            out.append(nativeIdentity()).append("\n\n");

            out.append("=== 2. INIT WORKSPACE ===\n");
            out.append(nativeInitWorkspace(ws.getAbsolutePath())).append("\n\n");

            out.append("=== 3. IDENTITY (after init) ===\n");
            out.append(nativeIdentity()).append("\n\n");

            out.append("=== 4. repo.list ===\n");
            out.append(nativeExecute(
                "{\"action_id\":\"a-list-1\",\"capability\":\"repo.list\","
                + "\"workspace_id\":\"karl-phone\",\"path\":null,\"query\":null,"
                + "\"content\":null,\"approval\":\"none\","
                + "\"expected_runner_id\":\"runner-karl-phone\",\"expires_at\":null}"
            )).append("\n\n");

            out.append("=== 5. repo.read README-AXIOM.txt ===\n");
            out.append(nativeExecute(
                "{\"action_id\":\"a-read-1\",\"capability\":\"repo.read\","
                + "\"workspace_id\":\"karl-phone\",\"path\":\"README-AXIOM.txt\","
                + "\"query\":null,\"content\":null,\"approval\":\"none\","
                + "\"expected_runner_id\":\"runner-karl-phone\",\"expires_at\":null}"
            )).append("\n");

        } catch (Throwable t) {
            out.append("ERROR:\n").append(t.getMessage());
        }
        tv.setText(out.toString());
    }
}
