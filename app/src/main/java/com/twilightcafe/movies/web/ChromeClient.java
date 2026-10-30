package com.twilightcafe.movies.web;

import android.graphics.Bitmap;
import android.os.Message;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * The page's progress, full screen, windows and messages, from Android's WebChromeClient to PageListener.
 * Protected (DRM) video may play if a player asks; the camera, the microphone and the location never.
 */
final class ChromeClient extends WebChromeClient {
    private final PageListener l;

    ChromeClient(PageListener l) {
        this.l = l;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        l.progress(newProgress);
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        l.fullscreen(view, callback);
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
        return l.popup(isUserGesture, resultMsg);
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
