package com.twilightcafe.movies.web.gecko;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import com.twilightcafe.movies.web.Bridge;
import com.twilightcafe.movies.web.Page;
import com.twilightcafe.movies.web.RequestFilter;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's own extension in Firefox's engine (assets/gecko/). Its background script asks the app about every
 * address a page loads (the RequestFilter: load it, nothing, or a stand-in, as a data: address), runs the app's
 * scripts in the page shown, and passes on what the ad-hiding script asks (the Bridge). Its arrows.js takes the
 * remote's arrows from link to link (the engine only scrolls by them) and asks for the on-screen keyboard. One
 * for the engine, for the page the app shows now (attach).
 */
final class Extension implements WebExtension.MessageDelegate, WebExtension.PortDelegate {
    private static final String TAG = "TwilightMovies";
    private static final String APP = "twilight";   // the name the extension's scripts give the app
    private static final String FOLDER = "resource://android/assets/gecko/";
    /** What gets nothing at all when it's blocked: a stand-in is for what the page shows or runs. */
    private static final Set<String> NOTHING = new HashSet<String>(Arrays.asList("xmlhttprequest", "ping", "beacon",
            "websocket", "csp_report", "speculative", "object_subrequest", "other"));
    /** The ad blocker's threads (it may still be loading its rules when the first page asks). */
    static final ExecutorService WORK = Executors.newFixedThreadPool(3);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> waiting = new ArrayList<Runnable>();
    private final List<JSONObject> unsent = new ArrayList<JSONObject>();
    private final SparseArray<Page.Result> results = new SparseArray<Page.Result>();
    private boolean ready;
    private WebExtension.Port port;
    private Object page;                 // the page shown now
    private volatile RequestFilter filter;
    private volatile Bridge bridge;
    private String bridgeName = "";
    private int lastId;

    /** Installed again at every start of the engine, so it's always the one in this version of the app. */
    Extension(GeckoRuntime runtime) {
        runtime.getWebExtensionController().installBuiltIn(FOLDER).accept(e -> {
            e.setMessageDelegate(this, APP);
            Log.i(TAG, "the app's extension is in Firefox's engine");
            setReady();
        }, x -> {
            Log.w(TAG, "the app's extension didn't start in Firefox's engine", x);
            setReady();
        });
        main.postDelayed(this::setReady, 8000);   // pages wait no longer
    }

    /** r runs (on the main thread) once the extension is ready, or now if it is. */
    void whenReady(Runnable r) {
        if (ready) r.run();
        else if (!waiting.contains(r)) waiting.add(r);
    }

    private void setReady() {
        if (ready) return;
        ready = true;
        List<Runnable> now = new ArrayList<Runnable>(waiting);
        waiting.clear();
        for (Runnable r : now) r.run();
    }

    void attach(Object p, RequestFilter f) {
        page = p;
        filter = f;
        bridge = null;
        results.clear();
        unsent.clear();
    }

    void detach(Object p) {
        if (page != p) return;
        page = null;
        filter = null;
        bridge = null;
        results.clear();
        unsent.clear();
    }

    void expose(Object p, Bridge b, String name) {
        if (page != p) return;
        bridge = b;
        bridgeName = name;
        send(message("bridge", name));
    }

    /** The script, in the page shown now (the background script runs it there); its answer to result. */
    void run(Object p, String script, Page.Result result) {
        if (page != p) return;
        int id = ++lastId;
        JSONObject m = message("run", id);
        put(m, "code", script);
        if (result != null) results.put(id, result);
        send(m);
    }

    private void send(JSONObject m) {
        if (port != null) port.postMessage(m);
        else if (unsent.size() < 20) unsent.add(m);
    }

    // From the background script (WebExtension.MessageDelegate, on the main thread): every address the page loads,
    // and the ad-hiding script's questions, answered on the ad blocker's threads, as JSON text (the engine takes no
    // JSONObject back); and OK on one of the page's fields (arrows.js): the on-screen keyboard.

    @Override
    public GeckoResult<Object> onMessage(String nativeApp, Object message, WebExtension.MessageSender sender) {
        if (!(message instanceof JSONObject)) return null;
        final JSONObject m = (JSONObject) message;
        if (m.has("keyboard")) {
            if (page instanceof GeckoPage) ((GeckoPage) page).showKeyboard();
            return GeckoResult.fromValue("{}");
        }
        final RequestFilter f = filter;
        final Bridge b = bridge;
        final GeckoResult<Object> answer = new GeckoResult<Object>();
        WORK.execute(() -> {
            JSONObject a;
            try {
                a = m.has("check") ? check(f, m) : ask(b, m);
            } catch (RuntimeException e) {
                Log.w(TAG, "the app couldn't answer its extension", e);
                a = new JSONObject();   // it loads: a page never waits for an answer that can't come
            }
            answer.complete(a.toString());
        });
        return answer;
    }

    private static JSONObject check(RequestFilter f, JSONObject m) {
        JSONObject out = new JSONObject();
        if (f == null) return out;
        String kind = m.optString("type", "other");
        RequestFilter.Answer a = f.check(m.optString("check"), kind, given(m, "frame"), given(m, "page"), null);
        if (a == null) return out;
        if (NOTHING.contains(kind)) put(out, "cancel", true);
        else put(out, "redirectUrl", "data:" + a.mime + ";base64," + Base64.encodeToString(a.data, Base64.NO_WRAP));
        return out;
    }

    private static JSONObject ask(Bridge b, JSONObject m) {
        String answer = b == null ? "" : b.ask(m.optString("ask"), m.optString("a"), m.optString("b"));
        return message("value", answer != null ? answer : "");
    }

    private static String given(JSONObject m, String key) {
        String v = m.optString(key, "");
        return v.isEmpty() ? null : v;
    }

    @Override
    public void onConnect(WebExtension.Port p) {
        port = p;
        p.setDelegate(this);
        if (!bridgeName.isEmpty()) p.postMessage(message("bridge", bridgeName));
        List<JSONObject> now = new ArrayList<JSONObject>(unsent);
        unsent.clear();
        for (JSONObject m : now) p.postMessage(m);
    }

    // From the background script, on its port (WebExtension.PortDelegate): the scripts' answers

    @Override
    public void onPortMessage(Object message, WebExtension.Port p) {
        if (!(message instanceof JSONObject)) return;
        JSONObject m = (JSONObject) message;
        int id = m.optInt("done", -1);
        Page.Result r = results.get(id);
        if (r == null) return;
        results.remove(id);
        r.value(m.isNull("value") ? null : m.optString("value", null));
    }

    @Override
    public void onDisconnect(WebExtension.Port p) {
        if (port != p) return;
        port = null;
        results.clear();
    }

    private static JSONObject message(String key, Object value) {
        JSONObject m = new JSONObject();
        put(m, key, value);
        return m;
    }

    private static void put(JSONObject m, String key, Object value) {
        try {
            m.put(key, value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(key, e);
        }
    }
}
