package com.twilightcafe.movies.web.webview;

import android.graphics.Bitmap;
import android.os.Message;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.twilightcafe.movies.web.PageListener;

/**
 * The page's progress, full screen, windows and messages, from Android's WebChromeClient to PageListener.
 * Protected (DRM) video may play if a player asks; the camera, the microphone and the location never.
 */
final class ChromeClient extends WebChromeClient {
    private final PageListener l;
    private final Popups popups;

    ChromeClient(PageListener l, Popups popups) {
        this.l = l;
        this.popups = popups;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        l.progress(newProgress);
    }

    @Override
    public void onShowCustomView(View view, final CustomViewCallback callback) {
        l.fullscreen(view, callback::onCustomViewHidden);
    }

    @Override
    public void onHideCustomView() {
        l.fullscreenEnd();
    }

    @Override
    public Bitmap getDefaultVideoPoster() {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);   // not the grey "play" picture
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        return popups.open(isUserGesture, resultMsg);
    }

    @Override
    public void onPermissionRequest(PermissionRequest request) {
        String[] wanted = request.getResources();
        if (wanted != null) {
            for (String r : wanted) {
                if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                    return;
                }
            }
        }
        request.deny();
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        callback.invoke(origin, false, false);
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage m) {
        if (m != null && m.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                && m.message() != null && m.message().contains("SyntaxError")) {
            l.scriptError(m.sourceId() != null ? m.sourceId() : "");
        }
        return false;
    }
}
