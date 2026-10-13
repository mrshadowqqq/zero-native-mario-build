package com.wither.nsmbw;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Start screen: play, or import the player's own game the way Dolphin extracts it (the folder, or
 * a zip of it). Until a game is imported the Dolphin steps are shown here; Help repeats them.
 */
public final class LauncherActivity extends Activity {
    private static final int REQUEST_IMPORT = 1;
    /** Set by GameActivity.restartForSession: open the game again once its old process is gone. */
    static final String EXTRA_RESTART_GAME = "restartGame";
    /** Set by the game's "Start Menu": show this screen even with Skip Start Menu on. */
    static final String EXTRA_SHOW_START_MENU = "showStartMenu";

    private AppSettings settings;

    private TextView status;
    private Button play;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ZeroDiagnostics.event(this, "LAUNCHER_READY", "launcher created");
        settings = new AppSettings(this);
        // Skip Start Menu: straight into the game, unless the game sent the player here or is
        // restarting itself into a LAN session.
        final Intent launch = getIntent();
        final boolean asked = launch != null && (launch.getBooleanExtra(EXTRA_SHOW_START_MENU, false)
                || launch.getBooleanExtra(EXTRA_RESTART_GAME, false));
        if (savedInstanceState == null && !asked && settings.skipStartMenu() && AppFiles.gameReady(this)) {
            startActivity(new Intent(this, GameActivity.class));
            overridePendingTransition(0, 0);
            finish();
            return;
        }
        // ZERO_SIMPLE_V29A: three primary actions; no ZIP import during startup.
        final int pad = dp(28);
        final LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, dp(56), pad, dp(32));
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setBackgroundColor(Color.BLACK);

        final TextView title = text("ZERO", 42);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(title);

        final TextView subtitle = text("NATIVE MARIO  /  REVISION 2", 13);
        subtitle.setTextColor(Color.rgb(165, 165, 165));
        subtitle.setPadding(0, dp(8), 0, dp(24));
        column.addView(subtitle);

        status = text("Checking game data...", 15);
        status.setTextColor(Color.rgb(190, 190, 190));
        status.setPadding(0, dp(15), 0, dp(28));
        column.addView(status);

        play = button("PLAY");
        play.setAllCaps(false);
        play.setTypeface(Typeface.DEFAULT_BOLD);
        play.setTextColor(Color.BLACK);
        play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        play.setOnClickListener(v -> {
            ZeroDiagnostics.event(this, "PLAY_REQUEST", "starting native game activity");
            startActivity(new Intent(this, GameActivity.class));
        });
        column.addView(play);

        final Button settingsButton = button("SETTINGS");
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(48,48,48)));
        settingsButton.setOnClickListener(v -> showZeroSettings());
        column.addView(settingsButton);

        final Button debugButton = button("DEBUG & LOGS");
        debugButton.setTextColor(Color.WHITE);
        debugButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(48,48,48)));
        debugButton.setOnClickListener(v -> startActivity(new Intent(this, ZeroDiagnosticsActivity.class)));
        column.addView(debugButton);

        final TextView note = text("Native ARM64  ·  Developer Build", 12);
        note.setTextColor(Color.rgb(126,126,126));
        note.setPadding(0, dp(30), 0, 0);
        column.addView(note);

        final ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
        restartGameIfAsked(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        restartGameIfAsked(intent);
    }

    /** Network play: the game asked to be started again into a LAN session. */
    private void restartGameIfAsked(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_RESTART_GAME, false)) {
            return;
        }
        intent.removeExtra(EXTRA_RESTART_GAME);
        status.setText("Starting the LAN game...");
        main.postDelayed(() -> startActivity(new Intent(this, GameActivity.class)), 700);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        final boolean present = AppFiles.hasGameData(this);
        final String problem = present ? AppFiles.gameProblem(AppFiles.gameDir(this)) : null;
        if (!present) {
            status.setText("Game data not installed. Open Settings > Game Data.");
        } else if (problem != null) {
            status.setText("Game data needs attention. " + problem);
        } else {
            status.setText("Game files verified. Ready to attempt native launch.");
        }
        play.setEnabled(present && problem == null);
    }

    private void showZeroSettings() {
        final String[] options = {
            "Graphics & Controls", "Game Data", "Multiplayer (Advanced)",
            "Open directly into game: " + (settings.skipStartMenu() ? "On" : "Off"),
            "About & Source Credits",
            "ZERO Patch Center (Developer)"
        };
        new AlertDialog.Builder(this).setTitle("ZERO  /  Settings")
            .setItems(options, (dialog, selection) -> {
                switch (selection) {
                    case 0: SettingsMenu.show(this); break;
                    case 1: showZeroGameData(); break;
                    case 2: DevicesDialog.show(this); break;
                    case 3:
                        settings.setSkipStartMenu(!settings.skipStartMenu());
                        showZeroSettings();
                        break;
                    case 4:
                        showZeroCredits();
                        break;
                    case 5:
                        ZeroPatchCenter.requestAccess(this);
                        break;
                    default: break;
                }
            })
            .setNegativeButton("Close", null).show();
    }

    // ZERO_CREDITS_V288: project identity is separate from upstream authorship and licenses.
    private void showZeroCredits() {
        final String credits =
            "ZERO NATIVE MARIO\nProject ZERO Android ARM64 experiments and optimizations.\n\n"
            + "Upstream native game port: WitherBlitz\n"
            + "Static recompiler: WiiCompiled by patchzyy\n"
            + "Renderer: Aurora by Luke Street / encounter (MIT)\n"
            + "Wii system data and behavior reference: Dolphin Emulator Project\n"
            + "Other components: SDL, Dawn and vendored dependencies.\n\n"
            + "The game, characters, artwork and audio are Nintendo intellectual property. "
            + "ZERO is an independent project, not an official Nintendo product.\n\n"
            + "The native port is GPLv3; each dependency retains its own license. "
            + "The complete source URLs and third-party notices are documented in "
            + "docs/ZERO_SOURCE_CREDITS.md and upstream THIRD-PARTY-NOTICES.md.\n\n"
            + "Original port: github.com/WitherBlitz/NSMB-Wii-Multiplayer-Recompilation\n"
            + "WiiCompiled: github.com/patchzyy/Wiicompiled\n"
            + "Aurora: github.com/encounter/aurora\n"
            + "Dolphin: github.com/dolphin-emu/dolphin";
        new AlertDialog.Builder(this)
            .setTitle("ZERO / Credits & Source")
            .setMessage(credits)
            .setNeutralButton("Original port source", (dialog, which) -> {
                final android.content.Intent web = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/WitherBlitz/NSMB-Wii-Multiplayer-Recompilation"));
                try { startActivity(web); }
                catch (android.content.ActivityNotFoundException ex) { /* URLs also displayed above. */ }
            })
            .setPositiveButton("Close", null)
            .show();
    }

    private void showZeroGameData() {
        final String state = AppFiles.gameReady(this)
            ? "Extracted Revision 2 files are installed. The game reads these files directly, not a ZIP."
            : "To set up game data, select the extracted folder containing files/ and sys/.";
        new AlertDialog.Builder(this).setTitle("Game Data")
            .setMessage(state + "\n\nImporting a replacement is optional. Existing saves are stored separately.")
            .setPositiveButton("Import extracted folder", (dialog, which) ->
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_IMPORT))
            .setNegativeButton("Cancel", null).show();
    }

    private String versionText() {
        try {
            final PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName + " · Build " + info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // ZERO_PATCH_CENTER_V289_LAUNCHER: handle developer unlock and draft export first.
        if (requestCode == ZeroPatchCenter.REQUEST_AUTH ||
            requestCode == ZeroPatchCenter.REQUEST_EXPORT_CPP ||
            requestCode == ZeroPatchCenter.REQUEST_IMPORT_MODULE) { // ZERO_NATIVE_MODULE_V291_LAUNCHER
            ZeroPatchCenter.onActivityResult(this, requestCode, resultCode, data);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT) {
            runImport(progress -> copyGame(uri, progress));
        }
    }

    // --- import --------------------------------------------------------------------------------

    private interface Progress { void update(int done, int total, String text); }

    private interface ImportJob { String run(Progress progress) throws IOException; }

    private static final class Entry {
        final String id, name;
        final boolean directory;
        Entry(String id, String name, boolean directory) { this.id = id; this.name = name; this.directory = directory; }
    }

    private void runImport(ImportJob job) {
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        final TextView label = text("Looking at the game files…", 14);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(16), dp(24), dp(8));
        box.addView(bar);
        box.addView(label);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Importing game data").setView(box).setCancelable(false).show();

        new Thread(() -> {
            String result;
            try {
                result = job.run((done, total, text) -> main.post(() -> {
                    bar.setIndeterminate(total <= 0);
                    if (total > 0) {
                        bar.setMax(total);
                        bar.setProgress(done);
                    }
                    label.setText(text);
                }));
            } catch (Exception e) {
                AppFiles.deleteRecursively(stagingDir());
                result = "Import failed: " + e.getMessage() + "\n\nNothing was changed.";
            }
            final String message = result;
            main.post(() -> {
                dialog.dismiss();
                refresh();
                new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
            });
        }, "game-import").start();
    }

    private File stagingDir() { return new File(AppFiles.dataDir(this), "game.importing"); }

    /** The checked game at `root` (inside the staging folder) becomes the game; saves stay. */
    private String install(File root, int fileCount) throws IOException {
        final File staging = stagingDir();
        final String problem = AppFiles.gameProblem(root);
        if (problem != null) {
            AppFiles.deleteRecursively(staging);
            return problem + "\n\nNothing was changed.";
        }
        final File game = AppFiles.gameDir(this);
        AppFiles.deleteRecursively(game);
        if (!root.renameTo(game)) throw new IOException("cannot move the imported data into place");
        AppFiles.deleteRecursively(staging);
        return "Imported " + fileCount + " files. Tap Play to start.";
    }

    /** A folder from the document picker: copied into the staging folder, checked, then swapped in. */
    private String copyGame(Uri tree, Progress progress) throws IOException {
        final ContentResolver resolver = getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        if (!looksLikeGame(resolver, tree, rootId)) {
            String nested = null;
            for (Entry e : list(resolver, tree, rootId)) {
                if (e.directory && e.name.equalsIgnoreCase("DATA") && looksLikeGame(resolver, tree, e.id)) nested = e.id;
            }
            if (nested == null) {
                return "That folder is not an extracted game: it needs files/ and sys/ (or a DATA folder holding "
                        + "them), as Dolphin's Extract Entire Disc makes.";
            }
            rootId = nested;
        }
        // Which game and revision, before copying anything.
        final String problem = treeProblem(resolver, tree, rootId);
        if (problem != null) {
            return problem + "\n\nNothing was changed.";
        }
        final List<String[]> files = new ArrayList<>();  // {documentId, relative path}
        collect(resolver, tree, rootId, "", files);

        final File staging = stagingDir();
        AppFiles.deleteRecursively(staging);
        for (int i = 0; i < files.size(); ++i) {
            final File target = new File(staging, files.get(i)[1]);
            final File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
            final Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, files.get(i)[0]);
            try (InputStream in = resolver.openInputStream(document); OutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IOException("cannot read " + files.get(i)[1]);
                AppFiles.copy(in, out);
            }
            progress.update(i + 1, files.size(), (i + 1) + " of " + files.size() + " files");
        }
        return install(staging, files.size());
    }

    /** sys/boot.bin of the picked folder names the game and its revision. */
    private String treeProblem(ContentResolver resolver, Uri tree, String rootId) throws IOException {
        String sysId = null;
        for (Entry e : list(resolver, tree, rootId)) {
            if (e.directory && e.name.equals("sys")) sysId = e.id;
        }
        String bootId = null;
        if (sysId != null) {
            for (Entry e : list(resolver, tree, sysId)) {
                if (!e.directory && e.name.equals("boot.bin")) bootId = e.id;
            }
        }
        if (bootId == null) {
            return "That folder has no sys/boot.bin, so the game can't be identified.";
        }
        final byte[] header = new byte[8];
        try (InputStream in = resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, bootId))) {
            if (in == null || AppFiles.readFully(in, header) < header.length) {
                return "That folder's sys/boot.bin is too short, so the game can't be identified.";
            }
        }
        return AppFiles.headerProblem(header);
    }

    private boolean looksLikeGame(ContentResolver resolver, Uri tree, String id) {
        boolean sys = false, filesDir = false;
        for (Entry e : list(resolver, tree, id)) {
            if (e.directory && e.name.equals("sys")) sys = true;
            if (e.directory && e.name.equals("files")) filesDir = true;
        }
        return sys && filesDir;
    }

    private void collect(ContentResolver resolver, Uri tree, String id, String prefix, List<String[]> out) {
        for (Entry e : list(resolver, tree, id)) {
            final String path = prefix.isEmpty() ? e.name : prefix + "/" + e.name;
            if (e.directory) collect(resolver, tree, e.id, path, out);
            else out.add(new String[] {e.id, path});
        }
    }

    private List<Entry> list(ContentResolver resolver, Uri tree, String id) {
        final List<Entry> entries = new ArrayList<>();
        final Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        final String[] columns = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        };
        try (Cursor c = resolver.query(children, columns, null, null, null)) {
            while (c != null && c.moveToNext()) {
                entries.add(new Entry(c.getString(0), c.getString(1),
                        DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))));
            }
        }
        return entries;
    }

    // --- views ---------------------------------------------------------------------------------

    private TextView text(String value, int sp) {
        final TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        return view;
    }

    private TextView link(String label, View.OnClickListener onClick) {
        final TextView view = text(label, 16);
        view.setTextColor(Color.rgb(255, 138, 128));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setOnClickListener(onClick);
        view.setBackgroundResource(android.R.drawable.list_selector_background);
        return view;
    }

    private Button button(String label) {
        final Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
