package app.streamloom.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile JSONObject activeSession;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(11, 13, 18));
        window.setNavigationBarColor(Color.rgb(11, 13, 18));
        WindowCompat.setDecorFitsSystemWindows(window, false);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(11, 13, 18));
        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        webView.addJavascriptInterface(new PortalBridge(), "Android");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        setContentView(webView);
        ViewCompat.requestApplyInsets(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void deliver(boolean ok, JSONObject value) {
        if (webView == null) return;
        final String json = value.toString().replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
        runOnUiThread(() -> webView.evaluateJavascript("window.__nativeResult && window.__nativeResult(" + ok + ", '" + json + "')", null));
    }

    private JSONObject error(String message) {
        JSONObject out = new JSONObject();
        try { out.put("error", message == null ? "Could not connect to the portal." : message); } catch (Exception ignored) {}
        return out;
    }

    private final class PortalBridge {
        @JavascriptInterface public void connectPortal(String portal, String mac) {
            executor.execute(() -> {
                try {
                    JSONObject session = StalkerPortalClient.connect(portal, mac);
                    activeSession = session;
                    getPreferences(MODE_PRIVATE).edit().putString("portal", session.optString("portal"))
                            .putString("mac", session.optString("mac")).apply();
                    deliver(true, session);
                } catch (Exception ex) { deliver(false, error(ex.getMessage())); }
            });
        }

        @JavascriptInterface public void loadChannels(String sessionJson, int page, String category) {
            executor.execute(() -> {
                try { deliver(true, StalkerPortalClient.loadChannels(new JSONObject(sessionJson), page, category)); }
                catch (Exception ex) { deliver(false, error(ex.getMessage())); }
            });
        }

        @JavascriptInterface public void playChannel(String sessionJson, String command, String name) {
            executor.execute(() -> {
                try {
                    String url = StalkerPortalClient.resolveChannel(new JSONObject(sessionJson), command);
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra("stream_url", url);
                    intent.putExtra("channel_name", name);
                    startActivity(intent);
                    JSONObject result = new JSONObject(); result.put("started", true); deliver(true, result);
                } catch (Exception ex) { deliver(false, error(ex.getMessage())); }
            });
        }

        @JavascriptInterface public String getSavedSource() {
            SharedPreferences preferences = getPreferences(MODE_PRIVATE);
            JSONObject result = new JSONObject();
            try { result.put("portal", preferences.getString("portal", "")); result.put("mac", preferences.getString("mac", "")); }
            catch (Exception ignored) {}
            return result.toString();
        }

        @JavascriptInterface public String getActiveSession() { return activeSession == null ? "{}" : activeSession.toString(); }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        if (webView != null) { webView.removeJavascriptInterface("Android"); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
