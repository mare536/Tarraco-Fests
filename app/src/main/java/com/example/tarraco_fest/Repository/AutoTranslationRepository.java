package com.example.tarraco_fest.Repository;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repositorio de traduccion automatica para textos de eventos.
 * Traduce a los idiomas soportados con fallback seguro al texto base.
 */
public class AutoTranslationRepository {

    private static final String[] TARGET_LANGS = new String[]{"es", "ca", "en", "ja"};
    private static final String ENDPOINT = "https://translate.googleapis.com/translate_a/single";
    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int READ_TIMEOUT_MS = 6_000;
    private static final int FAST_CONNECT_TIMEOUT_MS = 1_200;
    private static final int FAST_READ_TIMEOUT_MS = 1_800;
    private static final int MAX_CACHE_ENTRIES = 1024;

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<String, String> memoryCache = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(MAX_CACHE_ENTRIES + 1, 1.0f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );

    public interface I18nCallback {
        void onOk(Map<String, String> valuesByLang);
    }

    // Traduce el texto base a es/ca/en/ja y siempre devuelve resultado con fallback seguro.
    public void translateToSupportedLocales(String baseText, I18nCallback cb) {
        String safeBase = limpiar(baseText);
        if (cb == null) return;

        IO_EXECUTOR.execute(() -> {
            Map<String, String> out = new LinkedHashMap<>();
            for (String lang : TARGET_LANGS) {
                if ("es".equals(lang)) {
                    out.put(lang, safeBase);
                    continue;
                }
                out.put(lang, traducirConFallback(safeBase, lang, false));
            }
            mainHandler.post(() -> cb.onOk(out));
        });
    }

    // Traduce de forma bloqueante al idioma destino usando cache interna.
    // Debe llamarse fuera del hilo principal.
    public String translateBlocking(String sourceText, String targetLang) {
        String safeSource = limpiar(sourceText);
        String safeTarget = limpiar(targetLang).toLowerCase();
        if (TextUtils.isEmpty(safeSource) || TextUtils.isEmpty(safeTarget)) return safeSource;
        return traducirConFallback(safeSource, safeTarget, false);
    }

    // Traduce con timeouts agresivos para flujos sensibles a latencia (Home/API).
    // Debe llamarse fuera del hilo principal.
    public String translateBlockingFast(String sourceText, String targetLang) {
        String safeSource = limpiar(sourceText);
        String safeTarget = limpiar(targetLang).toLowerCase();
        if (TextUtils.isEmpty(safeSource) || TextUtils.isEmpty(safeTarget)) return safeSource;
        return traducirConFallback(safeSource, safeTarget, true);
    }

    // Traduce con fallback al original si hay cualquier error de red o parseo.
    private String traducirConFallback(String sourceText, String targetLang, boolean fastMode) {
        if (TextUtils.isEmpty(sourceText)) return "";
        if (TextUtils.isEmpty(targetLang)) return sourceText;

        String cacheKey = targetLang + "|" + sourceText;
        String cached = memoryCache.get(cacheKey);
        if (!TextUtils.isEmpty(cached)) {
            return cached;
        }

        try {
            String translated = requestGoogleTranslate(sourceText, targetLang, fastMode);
            String safe = TextUtils.isEmpty(translated) ? sourceText : translated.trim();
            memoryCache.put(cacheKey, safe);
            return safe;
        } catch (Exception ignored) {
            return sourceText;
        }
    }

    // Consulta endpoint publico de Google Translate sin API key.
    private String requestGoogleTranslate(String sourceText, String targetLang, boolean fastMode) throws Exception {
        String query = URLEncoder.encode(sourceText, StandardCharsets.UTF_8.name());
        String fullUrl = ENDPOINT
                + "?client=gtx"
                + "&sl=auto"
                + "&tl=" + URLEncoder.encode(targetLang, StandardCharsets.UTF_8.name())
                + "&dt=t"
                + "&q=" + query;

        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            conn = (HttpURLConnection) new URL(fullUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(fastMode ? FAST_CONNECT_TIMEOUT_MS : CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(fastMode ? FAST_READ_TIMEOUT_MS : READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TarracoFest/1.0");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return parseTranslatedText(sb.toString());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {
                    // No-op
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Extrae el texto traducido del JSON devuelto por el endpoint.
    private String parseTranslatedText(String rawJson) throws Exception {
        if (TextUtils.isEmpty(rawJson)) return "";
        JSONArray root = new JSONArray(rawJson);
        JSONArray chunks = root.optJSONArray(0);
        if (chunks == null) return "";

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < chunks.length(); i++) {
            JSONArray piece = chunks.optJSONArray(i);
            if (piece == null) continue;
            String fragment = piece.optString(0, "");
            if (!TextUtils.isEmpty(fragment)) out.append(fragment);
        }
        return out.toString().trim();
    }

    private String limpiar(String value) {
        return value == null ? "" : value.trim();
    }
}
