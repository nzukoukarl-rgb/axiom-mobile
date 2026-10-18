package com.axiom.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    static {
        try {
            System.loadLibrary("axiom_android");
        } catch (UnsatisfiedLinkError e) {
            // .so absent en test host
        }
    }

    public static native String nativeIdentity();
    public static native String nativeInitWorkspace(String path);
    public static native String nativeExecute(String actionJson);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        String text = "Axiom Mobile";
        try {
            text = nativeIdentity();
        } catch (Throwable t) {
            text = "Axiom (native pending)\n" + t.getMessage();
        }
        tv.setText(text);
        tv.setTextSize(18f);
        tv.setPadding(48, 48, 48, 48);
        setContentView(tv);
    }
}
