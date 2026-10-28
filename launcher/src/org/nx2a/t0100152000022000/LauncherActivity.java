package org.nx2a.t0100152000022000;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

// Launcher, redesigned after the Mario Kart 8 Deluxe main menu the user sent:
// light-blue checkerboard on the left, pale arrow rows with a yellow selected row,
// a white diagonal panel on the right with a big 8, DELUXE and a black/white
// checkered strip. Programmatic UI only, no XML, so the plain javac+d8 build keeps
// working. This activity only writes env.txt / settings.txt in the app's external
// files folder (shared with the game, same package) and then starts the game
// activity. It does not port an NSP and does not translate shaders: Import unpacks
// a data pack zip a porter run already made. Vulkan driver: System by default; an
// imported driver (a zip with libvulkan_*.so) is unpacked to drivers/<name>/, the
// selection is written to env.txt as DM_VK_DRIVER / DM_VK_DRIVER_DIR, and the host
// loads that driver's ICD directly, falling back to the system driver if it fails
// (see gfx_vk_core.c). The log's device/driver line shows which driver actually ran.
public class LauncherActivity extends Activity {
    private File root;
    private boolean docked; // false = Performance (handheld), true = Resolution (docked)
    private int present;    // 0 = Fit (bars), 1 = Fill (crop), 2 = Stretch
    private boolean res720p = true;
    private boolean dataOk;
    private String gameVer = "";
    private String zipName = "";        // data pack zip (not a driver zip)
    private String driverZipName = "";  // driver zip, if any in the files folder
    private ArrayList<String> driverNames = new ArrayList<String>(); // imported drivers
    private int driverIndex = 0; // 0 = System, 1.. = driverNames.get(driverIndex-1)
    private String envDriverName = "";

    private TextView statusView;
    private TextView progressView;
    private ProgressBar progressBar;
    private LinearLayout playRow;
    private TextView modeVal, presentVal, resVal, driverVal, importVal, importDriverVal;
    private volatile boolean importing;
    private Typeface displayFont;
    private static final int REQ_DATA_PICK = 1001;
    private static final int REQ_DRIVER_PICK = 1002;

    private void applyFullscreen() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    // User-supplied high-res art (assets/launcher_bg.png, fallback launcher_bg.jpg):
    // centre-cropped to the screen, with a baked-in gradient overlay: dark on the
    // left where the menu sits, fading to clear on the right where the art shows,
    // plus a dark fade at the bottom for the info card. No diagonal stripes.
    private BitmapDrawable darkenedBackground() {
        try {
            Bitmap src = null;
            try {
                InputStream in = getAssets().open("launcher_bg.png");
                src = BitmapFactory.decodeStream(in);
                in.close();
            } catch (Exception ignored) {}
            if (src == null) {
                InputStream in = getAssets().open("launcher_bg.jpg");
                src = BitmapFactory.decodeStream(in);
                in.close();
            }
            if (src == null) return null;
            int W = getResources().getDisplayMetrics().widthPixels;
            int H = getResources().getDisplayMetrics().heightPixels;
            if (W <= 0 || H <= 0) { W = src.getWidth(); H = src.getHeight(); }
            Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            float scale = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());
            float sw = src.getWidth() * scale, sh = src.getHeight() * scale;
            cv.drawBitmap(src, null, new RectF((W - sw) / 2, (H - sh) / 2, (W + sw) / 2, (H + sh) / 2), null);
            Paint grad = new Paint();
            grad.setShader(new LinearGradient(0, 0, W * 0.65f, 0, 0xD9000000, 0x00000000, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, grad);
            Paint bottom = new Paint();
            bottom.setShader(new LinearGradient(0, H * 0.65f, 0, H, 0x00000000, 0x80000000, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, bottom);
            return new BitmapDrawable(getResources(), out);
        } catch (Exception e) {
            return null;
        }
    }

    private Typeface loadDisplayFont() {
        try {
            return Typeface.createFromAsset(getAssets(), "Bungee.ttf");
        } catch (Exception e) {
            return Typeface.DEFAULT_BOLD;
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable bg(int color, int radius, int strokeColor, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (stroke > 0) d.setStroke(dp(stroke), strokeColor);
        return d;
    }

    // Tiled checkerboard drawable, like the main menu's left side.
    private BitmapDrawable checkerDrawable(int c1, int c2, int squareDp) {
        int s = dp(squareDp);
        Bitmap bm = Bitmap.createBitmap(s * 2, s * 2, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint p = new Paint();
        p.setColor(c1); cv.drawRect(0, 0, s * 2, s * 2, p);
        p.setColor(c2);
        cv.drawRect(0, 0, s, s, p);
        cv.drawRect(s, s, s * 2, s * 2, p);
        BitmapDrawable d = new BitmapDrawable(getResources(), bm);
        d.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        return d;
    }

    // White panel with light-blue diagonal stripes, like the main menu's right side.
    private BitmapDrawable diagonalDrawable() {
        int w = dp(200);
        Bitmap bm = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint p = new Paint();
        p.setColor(Color.WHITE); cv.drawRect(0, 0, w, w, p);
        p.setColor(0xFFBFE6FA); p.setStrokeWidth(dp(14));
        for (int i = -w; i < w * 2; i += dp(48)) {
            cv.drawLine(i, w, i + w, 0, p);
        }
        BitmapDrawable d = new BitmapDrawable(getResources(), bm);
        d.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        return d;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // One main-menu style row: a left chevron bar, label on the left, value + ">" on the right.
    // Play is the yellow selected row; the others are pale blue-white.
    private LinearLayout menuRow(String labelText, TextView valueView, boolean isPlay, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(isPlay
                ? bg(0xFFFFC800, 18, 0xFFB88A00, 2)
                : bg(0xE6FFFFFF, 14, 0xFF9AD0F0, 1));
        row.setElevation(dp(isPlay ? 8 : 4));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(isPlay ? 72 : 56));
        rp.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(rp);
        row.setClickable(true);
        row.setOnClickListener(onClick);

        TextView chev = label(isPlay ? "\u25B6" : "\u276F", isPlay ? 22 : 20, isPlay ? Color.BLACK : 0xFF0B5FA5, true);
        chev.setPadding(dp(14), 0, dp(8), 0);
        row.addView(chev);

        TextView lab = label(labelText, isPlay ? 22 : 17, Color.BLACK, isPlay);
        if (isPlay && displayFont != null) lab.setTypeface(displayFont);
        lab.setShadowLayer(dp(1), dp(1), dp(1), 0x40000000);
        lab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(lab);

        if (valueView != null) {
            valueView.setTextColor(0xFF0B5FA5);
            valueView.setTypeface(Typeface.DEFAULT_BOLD);
            valueView.setTextSize(15);
            valueView.setPadding(dp(8), 0, dp(8), 0);
            row.addView(valueView);
        }
        TextView arrow = label(">", 20, isPlay ? Color.BLACK : 0xFF0B5FA5, true);
        arrow.setPadding(0, 0, dp(14), 0);
        row.addView(arrow);
        return row;
    }

    private LinearLayout checkerStrip(int heightDp, int squares) {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        for (int i = 0; i < squares; i++) {
            View sq = new View(this);
            sq.setBackgroundColor(i % 2 == 0 ? Color.BLACK : Color.WHITE);
            sq.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            strip.addView(sq);
        }
        return strip;
    }

    private String driverName() {
        return driverIndex == 0 ? "System" : driverNames.get(driverIndex - 1);
    }

    private void refreshButtons() {
        playRow.setAlpha(dataOk && !importing ? 1.0f : 0.5f);
        modeVal.setText(docked ? "Resolution (docked)" : "Performance (handheld)");
        presentVal.setText(present == 2 ? "Stretch" : present == 1 ? "Fill (crop)" : "Fit (bars)");
        resVal.setText(res720p ? "720p" : "Native");
        driverVal.setText(driverIndex == 0 ? "System driver" : driverName());
        importVal.setText("Choose a file...");
        importDriverVal.setText("Choose a driver zip...");
        if (dataOk) {
            statusView.setText("Data pack installed" + (gameVer.length() > 0 ? " - " + gameVer : "") + ". Tap Play.");
            statusView.setTextColor(0xFF7CFC9A);
        } else if (zipName.length() > 0) {
            statusView.setText("Data pack missing. Tap Import to unpack " + zipName + ".");
            statusView.setTextColor(0xFFFF8A8A);
        } else {
            statusView.setText("Data pack missing. Put its zip in this app's files folder, then tap Import.");
            statusView.setTextColor(0xFFFF8A8A);
        }
    }

    private void dataCheck() {
        dataOk = new File(root, "RomFS").isDirectory()
                && new File(root, "shaders.dmsv").isFile()
                && new File(root, "mods").isDirectory()
                && new File(root, "nx2a-data.json").isFile();
        gameVer = "";
        File js = new File(root, "nx2a-data.json");
        if (js.isFile()) {
            try {
                byte[] buf = new byte[8192];
                InputStream in = new FileInputStream(js);
                int n = in.read(buf);
                in.close();
                if (n > 0) {
                    String s = new String(buf, 0, n, "UTF-8");
                    int i = s.indexOf("\"display\"");
                    if (i >= 0) {
                        int q1 = s.indexOf('"', s.indexOf(':', i) + 1);
                        int q2 = q1 >= 0 ? s.indexOf('"', q1 + 1) : -1;
                        if (q1 >= 0 && q2 > q1) gameVer = s.substring(q1 + 1, q2);
                    }
                }
            } catch (Exception e) { /* keep the fallback version */ }
        }
        zipName = "";
        driverZipName = "";
        File[] files = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                String low = f.getName().toLowerCase();
                if (!low.endsWith(".zip")) continue;
                if (low.contains("driver") || low.contains("turnip") || low.contains("vulkan")) {
                    if (driverZipName.length() == 0) driverZipName = f.getName();
                } else if (zipName.length() == 0) {
                    zipName = f.getName();
                }
            }
        }
        scanDrivers();
    }

    private void scanDrivers() {
        driverNames.clear();
        File dir = new File(root, "drivers");
        File[] subs = dir.isDirectory() ? dir.listFiles() : null;
        if (subs != null) {
            for (File sub : subs) {
                if (!sub.isDirectory()) continue;
                File[] inner = sub.listFiles();
                if (inner == null) continue;
                for (File f : inner) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".so")) { driverNames.add(sub.getName()); break; }
                }
            }
        }
        if (driverIndex > driverNames.size()) driverIndex = 0;
    }

    private String readLang() {
        String lang = "en-US";
        File env = new File(root, "env.txt");
        if (env.isFile()) {
            try {
                InputStream in = new FileInputStream(env);
                byte[] buf = new byte[8192];
                int n = in.read(buf);
                in.close();
                if (n > 0) {
                    String s = new String(buf, 0, n, "UTF-8");
                    for (String line : s.split("\n")) {
                        if (line.startsWith("DM_LANG=")) lang = line.substring(8).trim();
                    }
                }
            } catch (Exception e) { /* default */ }
        }
        return lang;
    }

    private void writeEnv() {
        String lang = readLang();
        StringBuilder old = new StringBuilder();
        File env = new File(root, "env.txt");
        if (env.isFile()) {
            try {
                InputStream in = new FileInputStream(env);
                byte[] buf = new byte[8192];
                int n;
                StringBuilder all = new StringBuilder();
                while ((n = in.read(buf)) > 0) all.append(new String(buf, 0, n, "UTF-8"));
                in.close();
                for (String line : all.toString().split("\n")) {
                    if (line.startsWith("DM_VK_PRESENT_STRETCH=") || line.startsWith("DM_VK_PRESENT_FILL=")
                            || line.startsWith("DM_VK_PRESENT_720P=")
                            || line.startsWith("DM_VK_DRIVER=") || line.startsWith("DM_VK_DRIVER_DIR=")) continue;
                    if (line.length() > 0) old.append(line).append("\n");
                }
            } catch (Exception e) { /* rewrite from scratch */ }
        }
        try {
            OutputStream out = new FileOutputStream(env);
            StringBuilder s = new StringBuilder(old.toString());
            s.append("DM_LANG=").append(lang).append("\n");
            s.append("DM_VK_PRESENT_STRETCH=").append(present == 2 ? 1 : 0).append("\n");
            s.append("DM_VK_PRESENT_FILL=").append(present == 1 ? 1 : 0).append("\n");
            s.append("DM_VK_PRESENT_720P=").append(res720p ? 1 : 0).append("\n");
            s.append("DM_VK_DRIVER=").append(driverName()).append("\n");
            if (driverIndex > 0) {
                s.append("DM_VK_DRIVER_DIR=").append(new File(new File(root, "drivers"), driverName()).getAbsolutePath()).append("\n");
            }
            out.write(s.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception e) { Toast.makeText(this, "Cannot write env.txt", Toast.LENGTH_SHORT).show(); }
    }

    private void loadSettings() {
        docked = false;
        File st = new File(root, "settings.txt");
        if (st.isFile()) {
            try {
                InputStream in = new FileInputStream(st);
                byte[] buf = new byte[4096];
                int n = in.read(buf);
                in.close();
                if (n > 0) {
                    for (String line : new String(buf, 0, n, "UTF-8").split("\n")) {
                        if (line.startsWith("docked=")) docked = line.substring(7).trim().equals("1");
                    }
                }
            } catch (Exception e) { /* default handheld */ }
        }
        present = 2;
        res720p = true;
        driverIndex = 0;
        File env = new File(root, "env.txt");
        if (env.isFile()) {
            try {
                InputStream in = new FileInputStream(env);
                byte[] buf = new byte[8192];
                int n = in.read(buf);
                in.close();
                if (n > 0) {
                    String s = new String(buf, 0, n, "UTF-8");
                    boolean seen = false;
                    for (String line : s.split("\n")) {
                        if (line.startsWith("DM_VK_PRESENT_STRETCH=1")) { present = 2; seen = true; }
                        else if (line.startsWith("DM_VK_PRESENT_FILL=1")) { present = 1; seen = true; }
                        else if (line.startsWith("DM_VK_PRESENT_720P=")) res720p = line.substring(19).trim().equals("1");
                        else if (line.startsWith("DM_VK_DRIVER=")) {
                            String d = line.substring(13).trim();
                            if (!d.equals("System") && !d.equals("system") && d.length() > 0) envDriverName = d;
                        }
                    }
                    if (seen && present != 2 && present != 1) present = 0;
                    if (s.contains("DM_VK_PRESENT_STRETCH=0") && s.contains("DM_VK_PRESENT_FILL=0")) present = 0;
                }
            } catch (Exception e) { /* defaults */ }
        }
    }

    private void writeSettings() {
        String overlay = "1", rumble = "1", mx = null, my = null, fps = null, controls = null;
        File st = new File(root, "settings.txt");
        if (st.isFile()) {
            try {
                InputStream in = new FileInputStream(st);
                byte[] buf = new byte[4096];
                int n = in.read(buf);
                in.close();
                if (n > 0) {
                    for (String line : new String(buf, 0, n, "UTF-8").split("\n")) {
                        if (line.startsWith("overlay=")) overlay = line.substring(8).trim();
                        else if (line.startsWith("rumble=")) rumble = line.substring(7).trim();
                        else if (line.startsWith("fps=")) fps = line.substring(4).trim();
                        else if (line.startsWith("controls=")) controls = line.substring(9).trim();
                        else if (line.startsWith("menu_x=")) mx = line.substring(7).trim();
                        else if (line.startsWith("menu_y=")) my = line.substring(7).trim();
                    }
                }
            } catch (Exception e) { /* defaults */ }
        }
        try {
            OutputStream out = new FileOutputStream(st);
            String s = "overlay=" + overlay + "\nrumble=" + rumble + "\ndocked=" + (docked ? 1 : 0) + "\n"
                    + (fps != null ? "fps=" + fps + "\n" : "")
                    + (controls != null ? "controls=" + controls + "\n" : "")
                    + (mx != null ? "menu_x=" + mx + "\n" : "")
                    + (my != null ? "menu_y=" + my + "\n" : "");
            out.write(s.getBytes("UTF-8"));
            out.close();
        } catch (Exception e) { Toast.makeText(this, "Cannot write settings.txt", Toast.LENGTH_SHORT).show(); }
    }

    private String importZipTo(File zip, File destDir) {
        ZipFile zf;
        try { zf = new ZipFile(zip); } catch (Exception e) { return "Cannot open " + zip.getName() + "."; }
        try {
            long total = 0;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory() && e.getSize() > 0) total += e.getSize();
            }
            long done = 0;
            byte[] buf = new byte[65536];
            en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (name.startsWith("/") || name.contains("../") || name.equals("..")) return "Refused unsafe path in zip: " + name;
                File dst = new File(destDir, name);
                if (e.isDirectory()) { dst.mkdirs(); continue; }
                File parent = dst.getParentFile();
                if (parent != null) parent.mkdirs();
                InputStream in = new BufferedInputStream(zf.getInputStream(e));
                OutputStream out = new BufferedOutputStream(new FileOutputStream(dst));
                int n;
                while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); done += n; }
                in.close();
                out.close();
                if (total > 0) {
                    final int pct = (int) (done * 100 / total);
                    runOnUiThread(new Runnable() { public void run() { progressBar.setProgress(pct); progressView.setText("Importing: " + pct + "%"); } });
                }
            }
        } catch (Exception e) {
            try { zf.close(); } catch (Exception ignored) {}
            return "Import failed: " + e.getMessage();
        }
        try { zf.close(); } catch (Exception ignored) {}
        return null;
    }

    private void launchPicker(int req) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed"});
        try {
            startActivityForResult(intent, req);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception e) { /* fallback below */ }
        finally { if (c != null) c.close(); }
        String p = uri.getLastPathSegment();
        return p != null ? p : "import.zip";
    }

    // Stream-unpack a zip picked with the system file picker. No copy of the zip
    // is made first: a data pack is ~13 GB, copying it would double the storage.
    // Progress is entry-by-entry (indeterminate bar), not percent, because a
    // ZipInputStream does not know the total until it has read it all.
    private void importFromUri(final Uri uri, final boolean isDriver) {
        importing = true;
        refreshButtons();
        final String name = displayName(uri);
        progressView.setText("Importing " + name + " ...");
        progressBar.setIndeterminate(true);
        new Thread(new Runnable() {
            public void run() {
                String base = name.toLowerCase().endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
                File dest = isDriver ? new File(new File(root, "drivers"), base) : root;
                if (isDriver) dest.mkdirs();
                String err = null;
                int entries = 0;
                try {
                    InputStream raw = getContentResolver().openInputStream(uri);
                    if (raw == null) throw new IOException("Cannot open " + name);
                    ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw));
                    byte[] buf = new byte[65536];
                    ZipEntry e;
                    while ((e = zin.getNextEntry()) != null) {
                        String ename = e.getName();
                        if (ename.startsWith("/") || ename.contains("../") || ename.equals("..")) { err = "Refused unsafe path in zip: " + ename; break; }
                        File dst = new File(dest, ename);
                        if (e.isDirectory()) { dst.mkdirs(); continue; }
                        File parent = dst.getParentFile();
                        if (parent != null) parent.mkdirs();
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(dst));
                        int n;
                        while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                        out.close();
                        entries++;
                        if (entries % 50 == 0) {
                            final String en = ename;
                            runOnUiThread(new Runnable() { public void run() { progressView.setText("Importing: " + en); } });
                        }
                    }
                    zin.close();
                } catch (Exception ex) {
                    err = "Import failed: " + ex.getMessage();
                }
                final String msg;
                if (err != null) msg = err;
                else if (isDriver) {
                    scanDrivers();
                    msg = driverNames.contains(base) ? "Driver imported: " + base + ". Tap the driver row to select it, then Play."
                            : "Driver zip unpacked, but no .so was found inside it.";
                } else {
                    dataCheck();
                    msg = dataOk ? "Import complete. Data pack installed" + (gameVer.length() > 0 ? " - " + gameVer : "") + ". Tap Play."
                            : "Import finished but the data pack is incomplete (RomFS / shaders / mods missing).";
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        importing = false;
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(0);
                        progressView.setText(msg);
                        dataCheck();
                        refreshButtons();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_DATA_PICK) importFromUri(data.getData(), false);
        else if (requestCode == REQ_DRIVER_PICK) importFromUri(data.getData(), true);
    }

    private void doImport() {
        launchPicker(REQ_DATA_PICK);
    }

    private void doImportDriver() {
        launchPicker(REQ_DRIVER_PICK);
    }

    // Legacy folder-zip import, kept for reference only; the rows now use the picker above.
    private void doImportOld() {
        if (zipName.length() == 0) {
            Toast.makeText(this, "No zip in the files folder. Put the data pack zip there first.", Toast.LENGTH_LONG).show();
            return;
        }
        importing = true;
        refreshButtons();
        progressView.setText("Importing " + zipName + " ...");
        progressBar.setProgress(0);
        new Thread(new Runnable() {
            public void run() {
                String err = importZipTo(new File(root, zipName), root);
                final String msg;
                if (err != null) msg = err;
                else {
                    dataCheck();
                    msg = dataOk ? "Import complete. Data pack installed" + (gameVer.length() > 0 ? " - " + gameVer : "") + ". Tap Play."
                            : "Import finished but the data pack is incomplete (RomFS / shaders / mods missing).";
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        importing = false;
                        progressView.setText(msg);
                        progressBar.setProgress(0);
                        dataCheck();
                        refreshButtons();
                    }
                });
            }
        }).start();
    }

    private void doImportDriverOld() {
        if (driverZipName.length() == 0) {
            Toast.makeText(this, "No driver zip in the files folder. Put a Turnip driver zip there (name it *driver* or *turnip*).", Toast.LENGTH_LONG).show();
            return;
        }
        importing = true;
        refreshButtons();
        progressView.setText("Importing driver " + driverZipName + " ...");
        progressBar.setProgress(0);
        new Thread(new Runnable() {
            public void run() {
                String base = driverZipName.substring(0, driverZipName.length() - 4);
                File dest = new File(new File(root, "drivers"), base);
                dest.mkdirs();
                String err = importZipTo(new File(root, driverZipName), dest);
                final String msg;
                if (err != null) msg = err;
                else {
                    scanDrivers();
                    msg = driverNames.contains(base) ? "Driver imported: " + base + ". Tap the driver row to select it, then Play."
                            : "Driver zip unpacked, but no .so was found inside it.";
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        importing = false;
                        progressView.setText(msg);
                        progressBar.setProgress(0);
                        dataCheck();
                        refreshButtons();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No title bar, no status bar: the black bar at the top in the last screenshot
        // was the activity's title bar cutting into the UI. Fullscreen immersive, and
        // re-applied in onResume() because returning from the game restores the bars.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyFullscreen();
        File ext = getExternalFilesDir(null);
        root = ext != null ? ext : getFilesDir();
        loadSettings();
        dataCheck();
        // Resolve the driver saved in env.txt against the drivers actually on disk.
        if (envDriverName.length() > 0) {
            int idx = driverNames.indexOf(envDriverName);
            driverIndex = idx >= 0 ? idx + 1 : 0;
        } else {
            driverIndex = 0;
        }

        LinearLayout rootCol = new LinearLayout(this);
        rootCol.setOrientation(LinearLayout.HORIZONTAL);
        BitmapDrawable bgArt = darkenedBackground();
        if (bgArt != null) rootCol.setBackground(bgArt);
        else rootCol.setBackground(checkerDrawable(0xFF8FD0F8, 0xFFBDE7FB, 48));

        // ---- Left: the main-menu rows ----
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 11));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(24), dp(12), dp(16), dp(20));
        scroll.addView(left, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        displayFont = loadDisplayFont();

        LinearLayout titlePanel = new LinearLayout(this);
        titlePanel.setOrientation(LinearLayout.VERTICAL);
        titlePanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView title = label("MARIO KART", 34, Color.WHITE, true);
        if (displayFont != null) title.setTypeface(displayFont);
        title.setShadowLayer(dp(4), dp(2), dp(2), Color.BLACK);
        title.setGravity(Gravity.CENTER);
        TextView sub = label("8  DELUXE", 26, 0xFFFFD700, true);
        if (displayFont != null) sub.setTypeface(displayFont);
        sub.setShadowLayer(dp(4), dp(2), dp(2), Color.BLACK);
        sub.setGravity(Gravity.CENTER);
        titlePanel.addView(title);
        titlePanel.addView(sub);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(0, 0, 0, dp(16));
        titlePanel.setLayoutParams(tp);
        left.addView(titlePanel);

        playRow = menuRow("PLAY", null, true, new View.OnClickListener() {
            public void onClick(View v) {
                if (!dataOk || importing) { Toast.makeText(LauncherActivity.this, "Data pack missing. Import it first.", Toast.LENGTH_SHORT).show(); return; }
                writeEnv();
                writeSettings();
                startActivity(new Intent(LauncherActivity.this, android.app.NativeActivity.class));
            }
        });
        left.addView(playRow);

        modeVal = label("", 15, 0xFF0B5FA5, true);
        left.addView(menuRow("Mode", modeVal, false, new View.OnClickListener() {
            public void onClick(View v) { docked = !docked; writeSettings(); refreshButtons(); }
        }));

        presentVal = label("", 15, 0xFF0B5FA5, true);
        left.addView(menuRow("Present", presentVal, false, new View.OnClickListener() {
            public void onClick(View v) { present = (present + 1) % 3; writeEnv(); refreshButtons(); }
        }));

        resVal = label("", 15, 0xFF0B5FA5, true);
        left.addView(menuRow("Present resolution", resVal, false, new View.OnClickListener() {
            public void onClick(View v) { res720p = !res720p; writeEnv(); refreshButtons(); }
        }));

        driverVal = label("", 15, 0xFF0B5FA5, true);
        left.addView(menuRow("Vulkan driver", driverVal, false, new View.OnClickListener() {
            public void onClick(View v) {
                if (driverNames.isEmpty()) {
                    Toast.makeText(LauncherActivity.this, "No imported driver yet. Use Import driver below.", Toast.LENGTH_SHORT).show();
                    return;
                }
                driverIndex = (driverIndex + 1) % (driverNames.size() + 1);
                writeEnv();
                refreshButtons();
            }
        }));

        importVal = label("", 15, 0xFF0B5FA5, true);
        left.addView(menuRow("Import data pack", importVal, false, new View.OnClickListener() {
            public void onClick(View v) { if (!importing) doImport(); }
        }));

        importDriverVal = label("", 15, 0xFF0B5FA5, true);
        left.addView(menuRow("Import Vulkan driver", importDriverVal, false, new View.OnClickListener() {
            public void onClick(View v) { if (!importing) doImportDriver(); }
        }));

        LinearLayout leftStrip = checkerStrip(16, 24);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16));
        lsp.setMargins(0, dp(8), 0, 0);
        leftStrip.setLayoutParams(lsp);
        leftStrip.setElevation(dp(4));
        left.addView(leftStrip);

        rootCol.addView(scroll);

        // ---- Right: no diagonal panel anymore. The art shows through; a floating
        // big 8 with shadow, gold stars, a finish-line strip, and a dark glass
        // info card at the bottom with shadow. ----
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(24), dp(16), dp(24), dp(32));
        right.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 9));

        TextView stars = label("\u2605  \u2605  \u2605", 20, 0xFFFFD700, true);
        stars.setShadowLayer(dp(3), dp(1), dp(1), Color.BLACK);
        stars.setGravity(Gravity.CENTER);
        right.addView(stars);
        TextView big8 = label("8", 110, 0xFFE60012, true);
        if (displayFont != null) big8.setTypeface(displayFont);
        big8.setShadowLayer(dp(6), dp(3), dp(3), Color.BLACK);
        big8.setGravity(Gravity.CENTER);
        right.addView(big8);
        TextView deluxe = label("DELUXE", 30, 0xFFFFD700, true);
        if (displayFont != null) deluxe.setTypeface(displayFont);
        deluxe.setShadowLayer(dp(4), dp(2), dp(2), Color.BLACK);
        deluxe.setGravity(Gravity.CENTER);
        right.addView(deluxe);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16));
        sp.setMargins(0, dp(16), 0, dp(8));
        LinearLayout strip = checkerStrip(16, 24);
        strip.setLayoutParams(sp);
        strip.setElevation(dp(4));
        right.addView(strip);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        right.addView(spacer);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(0xCC000000, 16, 0xFFFFD700, 1));
        card.setElevation(dp(8));
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        statusView = label("", 15, Color.WHITE, false);
        statusView.setPadding(0, 0, 0, dp(12));
        card.addView(statusView);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        card.addView(progressBar);
        progressView = label("", 14, 0xFFDDDDDD, false);
        progressView.setPadding(0, dp(6), 0, dp(8));
        card.addView(progressView);
        TextView foot = label("System driver is the one already on the phone (Turnip, on this device). An imported driver is loaded by the game only if its .so loads; the log's device/driver line shows which driver actually ran. Tap Import to choose a zip with the system file picker.", 13, 0xFFBBBBBB, false);
        card.addView(foot);
        right.addView(card);

        rootCol.addView(right);
        refreshButtons();
        setContentView(rootCol);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyFullscreen();
        if (!importing) { dataCheck(); refreshButtons(); }
    }
}
