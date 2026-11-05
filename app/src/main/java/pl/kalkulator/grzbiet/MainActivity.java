package pl.kalkulator.grzbiet;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 1;
    private static final int REQ_IMPORT = 2;

    private WebView web;
    private String pendingExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private final Object fileLock = new Object();

    private File historyFile() {
        return new File(getFilesDir(), "history.json");
    }

    private class Bridge {
        @JavascriptInterface
        public boolean isSystemDark() {
            int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        }

        @JavascriptInterface
        public void setBarsDark(boolean dark) {
            runOnUiThread(() -> {
                int bg = dark ? 0xFF11161C : 0xFFEEF1F4;
                getWindow().setStatusBarColor(bg);
                getWindow().setNavigationBarColor(bg);
                View d = getWindow().getDecorView();
                int flags = d.getSystemUiVisibility();
                int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                d.setSystemUiVisibility(dark ? (flags & ~light) : (flags | light));
                web.setBackgroundColor(bg);
            });
        }

        @JavascriptInterface
        public String loadData() {
            synchronized (fileLock) {
                File f = historyFile();
                if (!f.exists()) return "";
                try (FileInputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[(int) f.length()];
                    int off = 0;
                    while (off < buf.length) {
                        int n = in.read(buf, off, buf.length - off);
                        if (n < 0) break;
                        off += n;
                    }
                    return new String(buf, 0, off, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return "";
                }
            }
        }

        @JavascriptInterface
        public boolean saveData(String json) {
            synchronized (fileLock) {
                File tmp = new File(getFilesDir(), "history.json.tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                } catch (Exception e) {
                    return false;
                }
                return tmp.renameTo(historyFile());
            }
        }

        @JavascriptInterface
        public void exportData(String json, String fileName) {
            runOnUiThread(() -> {
                pendingExport = json;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, fileName);
                startActivityForResult(i, REQ_EXPORT);
            });
        }

        @JavascriptInterface
        public void importData() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingExport = null;
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT && pendingExport != null) {
            boolean ok = false;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                    ok = true;
                }
            } catch (Exception ignored) {
            }
            pendingExport = null;
            web.evaluateJavascript("window.onExportDone(" + ok + ")", null);
        } else if (requestCode == REQ_IMPORT) {
            StringBuilder sb = new StringBuilder();
            try (InputStream in = getContentResolver().openInputStream(uri);
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            } catch (Exception e) {
                sb.setLength(0);
            }
            web.evaluateJavascript("window.onImport(" + JSONObject.quote(sb.toString()) + ")", null);
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.androidBack && window.androidBack()", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }
}
