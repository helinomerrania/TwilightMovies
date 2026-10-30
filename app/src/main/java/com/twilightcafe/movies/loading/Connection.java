package com.twilightcafe.movies.loading;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;

/** Whether the box is online (the error screen says so), and a call on the main thread when the internet is back. */
final class Connection {
    private final ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;

    Connection(Context c, final Handler main, final Runnable back) {
        cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        try {
            ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    main.post(back);
                }
            };
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb);
            callback = cb;
        } catch (RuntimeException e) {
            callback = null;   // some boxes refuse; the countdown still tries again
        }
    }

    @SuppressWarnings("deprecation")
    boolean online() {
        try {
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= 23) {
                Network n = cm.getActiveNetwork();
                NetworkCapabilities c = n != null ? cm.getNetworkCapabilities(n) : null;
                return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (RuntimeException e) {
            return true;
        }
    }

    void stop() {
        if (callback == null) return;
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
            // already gone
        }
        callback = null;
    }
}
