package com.wither.nsmbw;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;

/** ZERO Patch Center v1. Developer-only, local-only and deliberately not a native .so loader. */
final class ZeroPatchCenter {
    static final int REQUEST_AUTH = 2891;
    static final int REQUEST_EXPORT_CPP = 2892;
    private static final String PREFS = "zero_patch_center_v1";
    private static final String TEMPLATE = "ZERO_PATCH_V1\nstartup_cover_frames=8\nslow_log_every=8\n";
    private static final String CPP_TEMPLATE =
            "// ZERO Native Mario — C++ experimental draft\\n"
          + "// This file is NOT executable by the Patch Center.\\n"
          + "// Export it and review/compile using the private native toolchain.\\n"
          + "// A native hook must be deliberately declared before it can be activated.\\n\\n"
          + "// Example: measure or optimize a well-defined GX operation.\\n"
          + "// Preserve GX order, EFB correctness, guest time and memory semantics.\\n";
    private static boolean authenticated = false;

    static boolean allowed(Context context) {
        return context.getPackageName().equals("com.wither.nsmbw.zeropatchdev");
    }
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private static File folder(Context c) {
        return new File(c.getFilesDir(), "zero-patches");
    }
    private static File active(Context c) { return new File(folder(c), "active.patch"); }
    private static File previous(Context c) { return new File(folder(c), "previous.patch"); }
    private static File draft(Context c) { return new File(folder(c), "draft.cpp"); }

    /** Called before SDLActivity.onCreate; invalid/tampered patch files are never passed to native. */
    static String nativePatchFile(Context c) {
        if (!allowed(c) || !prefs(c).getBoolean("enabled", false)) return "";
        try {
            File f = active(c);
            if (!f.isFile() || f.length() > 1024) return "";
            byte[] b = readSmall(f, 1024);
            validate(new String(b, StandardCharsets.UTF_8));
            if (!sha(b).equals(prefs(c).getString("sha256", ""))) return "";
            return f.getAbsolutePath();
        } catch (Exception ignored) {
            return "";
        }
    }

    static void requestAccess(Activity a) {
        if (!allowed(a)) {
            alert(a, "Unavailable", "The Patch Center is only in the separate developer build.");
            return;
        }
        KeyguardManager guard = (KeyguardManager) a.getSystemService(Context.KEYGUARD_SERVICE);
        if (guard == null || !guard.isDeviceSecure()) {
            alert(a, "Device lock required", "Enable a secure screen lock before using native patch controls.");
            return;
        }
        // Always ask the OS to authenticate for each new visit, not a hardcoded PIN in the APK.
        Intent challenge = guard.createConfirmDeviceCredentialIntent("ZERO Patch Center", "Authenticate to edit native runtime settings");
        if (challenge == null) {
            alert(a, "Authorization unavailable", "Android could not create a secure unlock challenge.");
            return;
        }
        authenticated = false;
        a.startActivityForResult(challenge, REQUEST_AUTH);
    }
    static void onActivityResult(Activity a, int requestCode, int resultCode, Intent data) {
        if (!allowed(a)) return;
        if (requestCode == REQUEST_AUTH) {
            if (resultCode == Activity.RESULT_OK) {
                authenticated = true;
                show(a);
            } else {
                authenticated = false;
            }
            return;
        }
        if (requestCode == REQUEST_EXPORT_CPP && resultCode == Activity.RESULT_OK && data != null) {
            if (!authenticated || data.getData() == null) return;
            try (OutputStream stream = a.getContentResolver().openOutputStream(data.getData(), "w")) {
                if (stream == null) throw new IOException("Export destination unavailable");
                stream.write(readSmall(draft(a), 120 * 1024));
                stream.flush();
                alert(a, "Draft exported", "Copy the C++ source into your private Termux build for review. It has NOT been activated.");
            } catch (Exception e) { alert(a, "Export failed", e.getMessage()); }
        }
    }

    static void show(Activity a) {
        if (!allowed(a) || !authenticated) return;
        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 18, 28, 16);
        TextView details = new TextView(a);
        details.setTextColor(Color.LTGRAY);
        details.setText("ZERO Patch Engine v1 • Bounded native hooks\n"
                + "Recipe changes activate on the NEXT Play launch.\n"
                + "No downloaded executable code is loaded.");
        layout.addView(details);
        EditText source = new EditText(a);
        source.setTypeface(Typeface.MONOSPACE);
        source.setTextColor(Color.WHITE);
        source.setBackgroundColor(Color.rgb(24, 24, 24));
        source.setTextSize(14);
        source.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        source.setMinLines(5);
        source.setGravity(Gravity.TOP | Gravity.START);
        source.setText(safeRead(active(a), TEMPLATE, 1024));
        layout.addView(source, new LinearLayout.LayoutParams(-1, -2));
        TextView help = new TextView(a);
        help.setText("startup_cover_frames: 0–60  •  slow_log_every: 0–120 (0 disables ZERO sampled diagnostics; built-in logs are unaffected).\n"
            + "Only approved keys. Neither value changes game simulation, geometry or EFB ordering.");
        help.setTextColor(Color.LTGRAY);
        layout.addView(help);
        Button apply = new Button(a);
        apply.setText("SAVE & ACTIVATE ON NEXT LAUNCH");
        layout.addView(apply);
        apply.setOnClickListener(v -> {
            try {
                String patch = source.getText().toString();
                validate(patch);
                writeActive(a, patch.getBytes(StandardCharsets.UTF_8));
                alert(a, "Patch staged", "Validated and saved in private app storage. Restart the game through the launcher to apply. The current game process is unchanged.");
            } catch (Exception e) { alert(a, "Patch rejected", e.getMessage()); }
        });
        Button rollback = new Button(a);
        rollback.setText("ROLLBACK TO PREVIOUS");
        layout.addView(rollback);
        rollback.setOnClickListener(v -> {
            try {
                if (!previous(a).isFile()) throw new IOException("No previous patch exists.");
                byte[] backup = readSmall(previous(a), 1024);
                validate(new String(backup, StandardCharsets.UTF_8));
                writeActive(a, backup);
                source.setText(new String(backup, StandardCharsets.UTF_8));
                alert(a, "Rollback staged", "The previous recipe will load on the next game launch.");
            } catch (Exception e) { alert(a, "Rollback unavailable", e.getMessage()); }
        });
        Button safe = new Button(a);
        safe.setText(prefs(a).getBoolean("enabled", false) ? "SAFE MODE: DISABLE PATCHES" : "ENABLE LAST SAVED PATCH");
        layout.addView(safe);
        safe.setOnClickListener(v -> {
            boolean next = !prefs(a).getBoolean("enabled", false);
            if (next && nativePatchFileCheck(a).isEmpty()) {
                alert(a, "Cannot enable", "No validated patch file or checksum. Save the recipe first.");
                return;
            }
            prefs(a).edit().putBoolean("enabled", next).apply();
            safe.setText(next ? "SAFE MODE: DISABLE PATCHES" : "ENABLE LAST SAVED PATCH");
            alert(a, "Patch mode", next ? "Enabled for next game launch." : "Safe mode enabled; defaults will apply next game launch.");
        });
        Button editor = new Button(a);
        editor.setText("C++ SOURCE LAB (EXPORT FOR COMPILATION)");
        layout.addView(editor);
        editor.setOnClickListener(v -> showCppDraft(a));
        ScrollView scroller = new ScrollView(a);
        scroller.addView(layout);
        final AlertDialog dialog = new AlertDialog.Builder(a).setTitle("ZERO / Patch Center")
            .setView(scroller).setPositiveButton("Close", null).create();
        dialog.setOnDismissListener(d -> authenticated = false);
        dialog.show();
    }

    private static String nativePatchFileCheck(Context c) {
        // Read independently of the current enabled flag before reenabling.
        try {
            File f = active(c);
            byte[] b = readSmall(f, 1024);
            validate(new String(b, StandardCharsets.UTF_8));
            return sha(b).equals(prefs(c).getString("sha256", "")) ? f.getAbsolutePath() : "";
        } catch (Exception e) { return ""; }
    }
    private static void showCppDraft(Activity a) {
        if (!authenticated) return;
        EditText cpp = new EditText(a);
        cpp.setTypeface(Typeface.MONOSPACE);
        cpp.setMinLines(14);
        cpp.setTextSize(13);
        cpp.setTextColor(Color.WHITE);
        cpp.setBackgroundColor(Color.rgb(24, 24, 24));
        cpp.setText(safeRead(draft(a), CPP_TEMPLATE.replace("\\n", "\n"), 120*1024));
        cpp.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        ScrollView wrap = new ScrollView(a);
        wrap.addView(cpp);
        new AlertDialog.Builder(a).setTitle("C++ Source Draft — NOT executable")
            .setMessage("Edit and export a native source draft. Building and signing compiled modules remains a separate step; nothing here hot-swaps native code.")
            .setView(wrap)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Save draft", (d, w) -> saveDraft(a, cpp.getText().toString(), false))
            .setPositiveButton("Save & Export", (d, w) -> saveDraft(a, cpp.getText().toString(), true))
            .show();
    }
    private static void saveDraft(Activity a, String source, boolean export) {
        if (!allowed(a) || !authenticated) return;
        try {
            byte[] data = source.getBytes(StandardCharsets.UTF_8);
            if (data.length == 0 || data.length > 120*1024) throw new IOException("Draft must be 1–120 KiB.");
            writeAtomically(draft(a), data);
            if (export) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.setType("text/x-c++src");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.putExtra(Intent.EXTRA_TITLE, "ZERO_native_patch_draft.cpp");
                a.startActivityForResult(i, REQUEST_EXPORT_CPP);
            } else alert(a, "Saved", "Your C++ draft was saved privately; no native code changed.");
        } catch (Exception e) { alert(a, "Save failed", e.getMessage()); }
    }

    private static void writeActive(Context c, byte[] bytes) throws Exception {
        File f = active(c);
        if (f.exists()) {
            byte[] old = readSmall(f, 1024);
            writeAtomically(previous(c), old);
        }
        writeAtomically(f, bytes);
        prefs(c).edit().putString("sha256", sha(bytes)).putBoolean("enabled", true).commit();
    }
    private static void writeAtomically(File f, byte[] bytes) throws IOException {
        File dir = f.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create patch directory");
        File tmp = new File(dir, f.getName() + ".new");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.getFD().sync();
        }
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static byte[] readSmall(File f, int max) throws IOException {
        if (!f.isFile() || f.length() > max) throw new IOException("Missing or oversized file: " + f.getName());
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) >= 0) {
                buffer.write(tmp, 0, n);
                if (buffer.size() > max) throw new IOException("File too large");
            }
            return buffer.toByteArray();
        }
    }
    private static String safeRead(File f, String fallback, int max) {
        try { return new String(readSmall(f,max), StandardCharsets.UTF_8); }
        catch (Exception ignored) { return fallback; }
    }
    private static String sha(byte[] bytes) throws Exception {
        byte[] sum = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : sum) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }
    private static int parseLine(String line, String key, int max) throws IOException {
        if (!line.startsWith(key + "=")) throw new IOException("Expected " + key + "=...");
        String value = line.substring(key.length() + 1);
        if (!value.matches("[0-9]{1,4}")) throw new IOException(key + " must be a number");
        int n = Integer.parseInt(value);
        if (n > max) throw new IOException(key + " exceeds " + max);
        return n;
    }
    private static void validate(String text) throws IOException {
        if (text.length() > 1024 || text.indexOf('\r') >= 0 || text.indexOf('\0') >= 0)
            throw new IOException("Invalid character or oversized patch");
        String[] lines = text.split("\n", -1);
        if (lines.length != 4 || !lines[3].isEmpty() || !lines[0].equals("ZERO_PATCH_V1"))
            throw new IOException("Expected ZERO_PATCH_V1 followed by exactly two supported settings and a newline");
        parseLine(lines[1], "startup_cover_frames", 60);
        parseLine(lines[2], "slow_log_every", 120);
    }
    private static void alert(Context c, String title, String detail) {
        new AlertDialog.Builder(c).setTitle(title).setMessage(detail).setPositiveButton("OK", null).show();
    }
}
