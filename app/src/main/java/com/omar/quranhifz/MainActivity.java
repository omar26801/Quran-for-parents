package com.omar.quranhifz;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    // The page is served from this made-up https host so it gets a normal origin:
    // /audio/<reciter>/<file> comes from downloaded files, everything else from assets/www.
    private static final String HOST = "app.local";
    private static final String HOME = "https://" + HOST + "/index.html";

    private WebView webView;
    private final ExecutorService downloads = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep the screen awake while someone is reading and listening.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#FAF7F0"));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // The page sets its own large sizes; ignore the system font scale so layouts don't break.
        s.setTextZoom(100);
        webView.setWebViewClient(new LocalClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidApp");
        setContentView(webView);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME);
        }
    }

    private File audioDir(String reciter) {
        return new File(getFilesDir(), "audio/" + reciter.replaceAll("[^a-z0-9_]", ""));
    }

    private class LocalClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!HOST.equals(uri.getHost())) return null;
            String path = uri.getPath() == null ? "/" : uri.getPath();
            if (path.contains("..")) return notFound();
            try {
                if (path.startsWith("/audio/")) {
                    String[] parts = path.substring("/audio/".length()).split("/");
                    if (parts.length != 2) return notFound();
                    File f = new File(audioDir(parts[0]), parts[1]);
                    if (!f.isFile()) return notFound();
                    return response("audio/mpeg", new FileInputStream(f));
                }
                if (path.equals("/")) path = "/index.html";
                return response(mimeFor(path), getAssets().open("www" + path));
            } catch (IOException e) {
                return notFound();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !HOST.equals(request.getUrl().getHost());
        }
    }

    private static WebResourceResponse response(String mime, InputStream in) {
        WebResourceResponse r = new WebResourceResponse(mime, mime.startsWith("text/") || mime.endsWith("javascript") ? "utf-8" : null, in);
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        r.setResponseHeaders(headers);
        return r;
    }

    private static WebResourceResponse notFound() {
        WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", null);
        r.setStatusCodeAndReasonPhrase(404, "Not Found");
        return r;
    }

    private static String mimeFor(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }

    /** Called from the page as window.AndroidApp. */
    private class Bridge {
        /** How many of the given files (comma separated) are already downloaded for this reciter. */
        @JavascriptInterface
        public int countDownloaded(String reciter, String files) {
            File dir = audioDir(reciter);
            int n = 0;
            for (String name : files.split(",")) {
                if (new File(dir, name).isFile()) n++;
            }
            return n;
        }

        /**
         * Downloads the given files from the first base url that has them, one at a time, and reports
         * progress to window.onDownloadProgress(key, done, total, failed).
         */
        @JavascriptInterface
        public void download(final String key, final String reciter, final String bases, final String files) {
            cancelled.set(false);
            downloads.execute(() -> {
                File dir = audioDir(reciter);
                dir.mkdirs();
                String[] names = files.split(",");
                String[] urls = bases.split(",");
                int done = 0;
                boolean failed = false;
                for (String name : names) {
                    if (cancelled.get()) break;
                    File dest = new File(dir, name);
                    if (!dest.isFile() && !fetch(urls, name, dest)) {
                        failed = true;
                        break;
                    }
                    done++;
                    report(key, done, names.length, false);
                }
                report(key, done, names.length, failed);
            });
        }

        @JavascriptInterface
        public void cancelDownloads() {
            cancelled.set(true);
        }
    }

    private boolean fetch(String[] bases, String name, File dest) {
        for (String base : bases) {
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpURLConnection c = null;
                File tmp = new File(dest.getPath() + ".part");
                try {
                    c = (HttpURLConnection) new URL(base + name).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    if (c.getResponseCode() == 404) break;
                    if (c.getResponseCode() != 200) continue;
                    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[32768];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    if (tmp.renameTo(dest)) return true;
                } catch (IOException e) {
                    tmp.delete();
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }
        return false;
    }

    private void report(String key, int done, int total, boolean failed) {
        final String js = "window.onDownloadProgress && window.onDownloadProgress('" + key.replaceAll("[^A-Za-z0-9_:-]", "")
                + "'," + done + "," + total + "," + failed + ")";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        webView.evaluateJavascript("window.appPaused && window.appPaused()", null);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // The page returns true when it handled back (e.g. went from a surah to the grid).
        webView.evaluateJavascript("window.handleBack ? window.handleBack() : false", value -> {
            if (!"true".equals(value)) {
                MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onDestroy() {
        cancelled.set(true);
        downloads.shutdownNow();
        webView.destroy();
        super.onDestroy();
    }
}
