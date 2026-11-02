package com.twilightcafe.movies.sites;

import android.content.res.AssetManager;

import com.twilightcafe.movies.util.Streams;
import com.twilightcafe.movies.util.Urls;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The websites in the app: assets/sites.json, in the order the Websites panel shows them (the first opens on a
 * new box). tests/js/sites.test.js checks the file on every build; if it can't be read anyway, KurdFilm alone.
 */
public final class SiteCatalog {
    static final String FILE = "sites.json";
    static final Site KURDFILM = new Site("kurdfilm", "KurdFilm", null, "https://app.kurdfilm.krd/tv",
            new String[]{"kurdfilm.krd"}, false);

    private SiteCatalog() {
    }

    public static List<Site> read(AssetManager assets) {
        InputStream in = null;
        try {
            in = assets.open(FILE);
            List<Site> sites = parse(new String(Streams.read(in, 1 << 20), "UTF-8"));
            if (!sites.isEmpty()) return sites;
        } catch (IOException | JSONException | RuntimeException e) {
            // the built-in website below
        } finally {
            Streams.close(in);
        }
        return Collections.singletonList(KURDFILM);
    }

    /**
     * The websites in json; one without an id or a web address (or the Twilight screens' twilight:) is left out,
     * and so is an id seen before.
     */
    static List<Site> parse(String json) throws JSONException {
        JSONArray list = new JSONObject(json).getJSONArray("sites");
        List<Site> out = new ArrayList<Site>();
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String id = o.optString("id", "");
            String address = o.optString("address", "");
            boolean known = Urls.OWN.equals(address) || Urls.normalize(address) != null;
            if (id.isEmpty() || !known || !ids.add(id)) continue;
            JSONArray d = o.optJSONArray("domains");
            String[] domains = new String[d != null ? d.length() : 0];
            for (int j = 0; j < domains.length; j++) domains[j] = d.getString(j);
            out.add(new Site(id, o.optString("name", id), o.optString("name_ckb", null), address, domains,
                    o.optBoolean("pointer", false)));
        }
        return out;
    }
}
