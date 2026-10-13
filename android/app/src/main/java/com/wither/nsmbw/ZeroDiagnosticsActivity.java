package com.wither.nsmbw;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Locally inspect or export our own Java/native logs, without ADB. */
public final class ZeroDiagnosticsActivity extends Activity {
    private static final int CREATE_REPORT = 928;
    private TextView reportText;
    private String snapshot;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ZeroDiagnostics.event(this, "DEBUG_SCREEN", "opened");
        final LinearLayout layout = new LinearLayout(this);
        layout.setPadding(22, 20, 22, 20);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(14, 16, 20));
        final TextView heading = new TextView(this);
        heading.setText("ZERO · Diagnostics");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(22);
        layout.addView(heading);
        final TextView instruction = new TextView(this);
        instruction.setText("Local startup checkpoints, resource status, process exits and available native logs. Share a report only when you choose.");
        instruction.setTextColor(Color.LTGRAY);
        layout.addView(instruction);
        final Button refresh = new Button(this);
        refresh.setText("Refresh diagnostics");
        refresh.setOnClickListener(v -> refresh());
        layout.addView(refresh);
        final Button export = new Button(this);
        export.setText("Save diagnostic report (.txt)");
        export.setOnClickListener(v -> {
            snapshot = ZeroDiagnostics.report(this);
            final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "ZERO-Native-Mario-v28-diagnostics.txt");
            startActivityForResult(intent, CREATE_REPORT);
        });
        layout.addView(export);
        final ScrollView scroll = new ScrollView(this);
        reportText = new TextView(this);
        reportText.setTextColor(Color.rgb(224, 226, 230));
        reportText.setTextSize(12);
        reportText.setTextIsSelectable(true);
        scroll.addView(reportText);
        layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(layout);
        refresh();
    }

    private void refresh() {
        snapshot = ZeroDiagnostics.report(this);
        reportText.setText(snapshot);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_REPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new java.io.IOException("Document output unavailable");
            out.write((snapshot == null ? ZeroDiagnostics.report(this) : snapshot).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "ZERO report saved", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            ZeroDiagnostics.failure(this, "EXPORT_FAILED", e);
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
