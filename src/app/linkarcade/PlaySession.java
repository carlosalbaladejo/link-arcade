package app.linkarcade;

import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebMessage;
import android.webkit.WebMessagePort;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class PlaySession {
    final Game game;
    final View content;
    private final WebView web;
    private final FrameLayout viewport;
    private final TouchPad touch;
    private final Button toggle;
    private final Uri target;
    private final MainActivity activity;
    private final boolean tv;
    private View error;
    private WebMessagePort drivingPort;
    private boolean ready, closed;
    private Set<String> last = Collections.emptySet();
    private Set<String> held = Collections.emptySet();

    PlaySession(MainActivity activity, Game game, boolean tv, boolean showTouch) throws Exception {
        this.activity = activity;
        this.game = game;
        this.tv = tv;
        Style style = activity.style;
        target = Uri.parse(Imports.origin(Imports.webUri(game.url)));
        web = new WebView(activity);
        web.setBackgroundColor(Style.BG);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new Browser());

        viewport = new FrameLayout(activity);
        viewport.setMotionEventSplittingEnabled(true);
        viewport.setBackgroundColor(Style.BG);
        viewport.addView(web, new FrameLayout.LayoutParams(-1, -1));
        content = viewport;

        toggle = TouchPad.overlayButton(style, "Show touch controls");
        touch = new TouchPad(style, activity::touchButton, game.driving());
        if (!tv) {
            FrameLayout controls = new FrameLayout(activity);
            controls.setMotionEventSplittingEnabled(true);
            viewport.addView(controls, new FrameLayout.LayoutParams(-1, -1));
            controls.setOnApplyWindowInsetsListener((view, insets) -> {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    android.view.DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        view.setPadding(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                                cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
                        return insets;
                    }
                }
                view.setPadding(0, 0, 0, 0);
                return insets;
            });
            LinearLayout header = style.row();
            Button back = TouchPad.overlayButton(style, "‹ Catalog");
            back.setOnClickListener(view -> activity.closeGame());
            header.addView(back, new LinearLayout.LayoutParams(-2, style.dp(48)));
            if (game.compatible()) {
                LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-2, style.dp(48));
                toggleParams.leftMargin = style.dp(6);
                header.addView(toggle, toggleParams);
                toggle.setOnClickListener(view -> showTouch(!touchVisible()));
                controls.addView(touch, new FrameLayout.LayoutParams(-1, style.dp(148), Gravity.BOTTOM));
            }
            FrameLayout.LayoutParams menu = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            menu.topMargin = style.dp(6);
            controls.addView(header, menu);
        }
        showTouch(showTouch);
    }

    void start() {
        web.requestFocus();
        load();
    }

    private boolean touchVisible() { return touch.getVisibility() == View.VISIBLE; }

    private void showTouch(boolean visible) {
        visible = visible && !tv && game.compatible();
        if (!visible) {
            activity.clearTouch();
            touch.cancelPendingInputEvents();
            resetTouchPressed(touch);
        }
        touch.setVisibility(visible ? View.VISIBLE : View.GONE);
        toggle.setText(visible ? "Hide touch controls" : "Show touch controls");
        updateDrivingControls();
    }

    private void updateDrivingControls() {
        if (drivingPort != null) {
            drivingPort.postMessage(new WebMessage(tv ? "tv" : touchVisible() ? "touch" : "physical"));
        }
    }

    private void connectDrivingControls() {
        disconnectDrivingControls();
        WebMessagePort[] channel = web.createWebMessageChannel();
        drivingPort = channel[0];
        drivingPort.setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
            @Override public void onMessage(WebMessagePort port, WebMessage message) {
                if (port != drivingPort || closed || !ready || !Game.DRIVE_URL.equals(web.getUrl())) return;
                if ("toggle-touch".equals(message.getData()) && !tv) showTouch(!touchVisible());
                if ("catalog".equals(message.getData())) activity.closeGame();
            }
        });
        web.postWebMessage(new WebMessage("paseo-costero-host", new WebMessagePort[]{channel[1]}), target);
        updateDrivingControls();
    }

    private void disconnectDrivingControls() {
        if (drivingPort == null) return;
        drivingPort.close();
        drivingPort = null;
    }

    private void resetTouchPressed(View view) {
        view.setPressed(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) resetTouchPressed(group.getChildAt(i));
        }
    }

    void load() {
        if (error != null) viewport.removeView(error);
        error = null;
        web.setVisibility(View.VISIBLE);
        ready = false;
        web.loadUrl(game.url);
    }

    private boolean allowed(String url) {
        try {
            if (game.builtin()) return url.equals(game.url);
            return target.toString().equals(Imports.origin(Imports.webUri(url)));
        } catch (IOException exception) {
            return false;
        }
    }

    void actions(Set<String> pressed) {
        held = new LinkedHashSet<>(pressed);
        if (!ready || closed || !game.compatible() || last.equals(held)
                || web.getUrl() == null || !allowed(web.getUrl())) return;
        try {
            String data = new JSONObject().put("type", "tv-game-input").put("version", 1)
                    .put("pressed", new JSONArray(held)).toString();
            web.postWebMessage(new WebMessage(data), target);
            last = held;
        } catch (JSONException | IllegalStateException exception) {
            showError("Could not send input to the game.");
        }
    }

    void suspend() {
        resetTouchPressed(touch);
        web.onPause();
    }

    void resume() { web.onResume(); }

    void destroy() {
        closed = true;
        disconnectDrivingControls();
        web.stopLoading();
        viewport.removeView(web);
        web.destroy();
    }

    private void showError(String message) {
        if (error != null || closed) return;
        ready = false;
        web.stopLoading();
        web.setVisibility(View.GONE);
        Style style = activity.style;
        LinearLayout panel = style.column();
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8));
        panel.setBackgroundColor(Style.BG);
        TextView title = style.text(message, 19, Style.TEXT);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        Button retry = style.button("Reintentar");
        retry.setOnClickListener(view -> load());
        style.append(panel, retry, 12);
        Button back = style.button("Return to catalog");
        back.setOnClickListener(view -> activity.closeGame());
        style.append(panel, back, 8);
        error = panel;
        viewport.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        retry.requestFocus();
    }

    private final class Browser extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;
            if (allowed(request.getUrl().toString())) return false;
            showError("The game tried to open a page on another site.");
            return true;
        }

        @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
            if (closed) return;
            ready = false;
            disconnectDrivingControls();
            last = Collections.emptySet();
            activity.clearInputs();
            if (!allowed(url)) showError("The page is outside the saved site.");
        }

        @Override public void onPageFinished(WebView view, String url) {
            if (closed || error != null || !allowed(url)) return;
            ready = true;
            if (game.driving()) connectDrivingControls();
            actions(held);
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError problem) {
            if (request.isForMainFrame()) showError("Could not load the game.");
        }

        @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame()) showError("The game returned HTTP " + response.getStatusCode() + ".");
        }

        @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            activity.closeGame();
            activity.toast("The browser closed. You can open the game again.");
            return true;
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            boolean builtinAsset = url.startsWith(Game.TEST_ORIGIN + "/");
            boolean blockedCleartext = "https".equals(target.getScheme())
                    && "http".equalsIgnoreCase(request.getUrl().getScheme());
            if (!builtinAsset && !game.builtin() && !blockedCleartext) return null;
            String asset = builtinAsset ? request.getUrl().getPath() : "";
            String mime = null;
            if (game.driving()) {
                switch (asset) {
                    case "/driving/index.html": mime = "text/html"; break;
                    case "/driving/style.css": mime = "text/css"; break;
                    case "/driving/game.json": mime = "application/json"; break;
                    case "/driving/cover.jpg": mime = "image/jpeg"; break;
                    case "/driving/drive.js":
                    case "/driving/world.js":
                    case "/driving/city.js":
                    case "/driving/traffic.js":
                    case "/driving/audio.js":
                    case "/driving/effects.js":
                    case "/driving/navigation.js":
                    case "/driving/vendor/three.module.min.js": mime = "text/javascript"; break;
                    default: break;
                }
            } else if (game.builtin()) {
                switch (asset) {
                    case "/controller-test/index.html": mime = "text/html"; break;
                    case "/controller-test/game.json": mime = "application/json"; break;
                    case "/controller-test/cover.png": mime = "image/png"; break;
                    default: break;
                }
            }
            if (mime != null) {
                try {
                    return new WebResourceResponse(mime, "UTF-8", activity.getAssets().open(asset.substring(1)));
                } catch (IOException ignored) { }
            }
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
        }
    }
}
