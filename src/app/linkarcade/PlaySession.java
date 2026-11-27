package app.linkarcade;

import android.net.Uri;
import android.content.res.ColorStateList;
import android.os.SystemClock;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
    private static final long CONNECTION_RETRY_WINDOW_MS = 45_000;

    private final View loading;
    private final ProgressBar progress;
    private final Button cancel;
    private final String languageBootstrap;
    private final byte[] languagePage;
    private final Runnable reconnect = this::loadGame;
    private long retryUntil;
    private boolean retryPending;
    private String navigation;
    private View error;
    private WebMessagePort drivingPort;
    private boolean ready, closed;
    private Set<String> last;
    private Set<String> held = Collections.emptySet();

    PlaySession(MainActivity activity, Game game, boolean tv, boolean showTouch) throws Exception {
        this.activity = activity;
        this.game = game;
        this.tv = tv;
        Style style = activity.style;
        target = Uri.parse(Imports.origin(Imports.webUri(game.url)));
        String languageKey = game.languagePreference();
        languageBootstrap = languageKey == null ? null : target + "/.link-arcade-language-" + UUID.randomUUID();
        String page = languageKey == null ? "" : "<!doctype html><meta charset=utf-8><script>"
                + "try { localStorage.setItem(" + JSONObject.quote(languageKey) + ","
                + JSONObject.quote(activity.language()) + "); } catch (_) {}"
                + "location.replace(decodeURIComponent(" + JSONObject.quote(Uri.encode(game.url)) + "));</script>";
        languagePage = page.getBytes(StandardCharsets.UTF_8);
        web = new WebView(activity);
        web.setBackgroundColor(Style.BG);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (tv && game.driving()) {
            WebSettings settings = web.getSettings();
            settings.setUserAgentString(settings.getUserAgentString() + " LinkArcadeTV");
        }
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int percent) {
                if (closed || ready || retryPending || languageBootstrap != null && languageBootstrap.equals(navigation)) return;
                progress.setIndeterminate(percent < 10);
                if (percent >= 10) progress.setProgress(Math.min(percent, 95));
            }
        });
        web.setWebViewClient(new Browser());

        viewport = new FrameLayout(activity);
        viewport.setMotionEventSplittingEnabled(true);
        viewport.setBackgroundColor(Style.BG);
        viewport.addView(web, new FrameLayout.LayoutParams(-1, -1));
        content = viewport;

        if (tv) {
            toggle = null;
            touch = null;
        } else {
            toggle = TouchPad.overlayButton(style, activity.t("Show touch controls", "Mostrar controles táctiles"));
            touch = new TouchPad(style, activity::touchButton, game.driving());
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
            Button back = TouchPad.overlayButton(style, activity.t("‹ Catalog", "‹ Catálogo"));
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
            showTouch(showTouch);
        }

        LinearLayout overlay = style.column();
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(Style.BG);
        overlay.setClickable(true);
        LinearLayout panel = style.column();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(style.dp(24), style.dp(12), style.dp(24), style.dp(12));
        overlay.addView(panel, new LinearLayout.LayoutParams(tv ? style.dp(460) : -1, -2));
        TextView title = style.text(game.title, tv ? 30 : 25, Style.TEXT);
        title.setGravity(Gravity.CENTER);
        style.append(panel, title, 0);
        style.append(panel, style.text(activity.t("Loading game…", "Cargando juego…"), 16, Style.MUTED), 8);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(Style.MINT));
        progress.setIndeterminateTintList(ColorStateList.valueOf(Style.MINT));
        progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        style.append(panel, progress, 14);
        cancel = style.button(activity.t("Cancel loading", "Cancelar carga"));
        cancel.setOnClickListener(view -> activity.closeGame());
        panel.addView(cancel);
        loading = overlay;
        viewport.addView(loading, new FrameLayout.LayoutParams(-1, -1));
    }

    void start() {
        web.requestFocus();
        load();
    }

    private boolean touchVisible() { return touch != null && touch.getVisibility() == View.VISIBLE; }

    private void showTouch(boolean visible) {
        visible = visible && !tv && game.compatible();
        if (!visible) {
            activity.clearTouch();
            touch.cancelPendingInputEvents();
            resetTouchPressed(touch);
        }
        touch.setVisibility(visible ? View.VISIBLE : View.GONE);
        toggle.setText(visible ? activity.t("Hide touch controls", "Ocultar controles táctiles")
                : activity.t("Show touch controls", "Mostrar controles táctiles"));
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

    boolean acceptsInput() { return ready; }

    private void showLoading() {
        ready = false;
        loading.setVisibility(View.VISIBLE);
        cancel.requestFocus();
        progress.setIndeterminate(true);
    }

    private void load() {
        if (error != null) viewport.removeView(error);
        error = null;
        retryUntil = SystemClock.uptimeMillis() + CONNECTION_RETRY_WINDOW_MS;
        web.setVisibility(View.VISIBLE);
        showLoading();
        if (languageBootstrap != null) web.loadUrl(languageBootstrap);
        else loadGame();
    }

    private void loadGame() {
        if (closed || error != null) return;
        retryPending = false;
        web.loadUrl(game.url, Collections.singletonMap("Accept-Language", activity.language()));
    }

    private void connectionError(int code) {
        if (closed || error != null || retryPending) return;
        boolean transientError = code == WebViewClient.ERROR_TIMEOUT || code == WebViewClient.ERROR_CONNECT
                || code == WebViewClient.ERROR_IO || code == WebViewClient.ERROR_HOST_LOOKUP;
        if (transientError && !ready && SystemClock.uptimeMillis() < retryUntil) {
            retryPending = true;
            progress.setIndeterminate(true);
            web.postDelayed(reconnect, 3_000);
        } else {
            showError(activity.t("Could not load the game. Check your connection and try again.",
                    "No se pudo cargar el juego. Comprueba la conexión y vuelve a intentarlo."));
        }
    }

    private boolean allowed(String url) {
        try {
            return target.toString().equals(Imports.origin(Imports.webUri(url)));
        } catch (IOException exception) {
            return false;
        }
    }

    void actions(Set<String> pressed) {
        if (!held.equals(pressed)) held = new LinkedHashSet<>(pressed);
        if (!ready || closed || !game.compatible() || held.equals(last)) return;
        String url = web.getUrl();
        if (url == null || !allowed(url)) return;
        try {
            String data = new JSONObject().put("type", "tv-game-input").put("version", 1)
                    .put("pressed", new JSONArray(held)).toString();
            web.postWebMessage(new WebMessage(data), target);
            last = held;
        } catch (JSONException | IllegalStateException exception) {
            showError(activity.t("Could not send input to the game.", "No se pudo enviar el control al juego."));
        }
    }

    void suspend() {
        if (touch != null) resetTouchPressed(touch);
        web.onPause();
    }

    void resume() { web.onResume(); }

    void destroy() {
        closed = true;
        web.removeCallbacks(reconnect);
        disconnectDrivingControls();
        web.stopLoading();
        viewport.removeView(web);
        web.destroy();
    }

    private void showError(String message) {
        if (error != null || closed) return;
        ready = false;
        retryPending = false;
        web.removeCallbacks(reconnect);
        loading.setVisibility(View.GONE);
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
        Button retry = style.button(activity.t("Retry", "Reintentar"));
        retry.setOnClickListener(view -> load());
        style.append(panel, retry, 12);
        Button back = style.button(activity.t("Return to catalog", "Volver al catálogo"));
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
            showError(activity.t("The game tried to open another site.", "El juego intentó abrir otro sitio web."));
            return true;
        }

        @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
            if (closed || error != null) return;
            navigation = url;
            showLoading();
            disconnectDrivingControls();
            // A neutral first snapshot lets compatible games switch off their own input polling.
            last = null;
            activity.clearInputs();
            if (!allowed(url)) showError(activity.t("The page is outside the saved site.", "La página no pertenece al sitio guardado."));
        }

        @Override public void onPageFinished(WebView view, String url) {
            if (closed || error != null || retryPending || !url.equals(navigation) || !allowed(url)
                    || url.equals(languageBootstrap)) return;
            ready = true;
            loading.setVisibility(View.GONE);
            web.requestFocus();
            try {
                String message = new JSONObject().put("type", "tv-game-language").put("version", 1)
                        .put("language", activity.language()).toString();
                web.postWebMessage(new WebMessage(message), target);
            } catch (JSONException | IllegalStateException ignored) {
                // Pages without a language integration can keep their own setting.
            }
            if (game.driving() && Game.DRIVE_URL.equals(url)) connectDrivingControls();
            actions(held);
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError problem) {
            if (request.isForMainFrame()) connectionError(problem.getErrorCode());
        }

        @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame()) showError(activity.t("The game returned HTTP ",
                    "El servidor del juego devolvió HTTP ") + response.getStatusCode() + ".");
        }

        @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            activity.closeGame();
            activity.toast(activity.t("The browser closed. You can open the game again.", "El navegador se cerró. Puedes volver a abrir el juego."));
            return true;
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (request.isForMainFrame() && url.equals(languageBootstrap)) {
                return new WebResourceResponse("text/html", "UTF-8", 200, "OK",
                        Collections.singletonMap("Cache-Control", "no-store"), new ByteArrayInputStream(languagePage));
            }
            boolean blockedCleartext = "https".equals(target.getScheme())
                    && "http".equalsIgnoreCase(request.getUrl().getScheme());
            if (!blockedCleartext) return null;
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
        }
    }
}
