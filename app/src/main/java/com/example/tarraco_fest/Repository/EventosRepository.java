package com.example.tarraco_fest.Repository;

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.tarraco_fest.BuildConfig;
import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repositorio principal de eventos para Home y listados.
 * Combina fuente remota, cache local y normalizacion de datos.
 */
public class EventosRepository {

    private static final String TARRAGONA_API_CSV_URL = "https://opendatafiles.tarragona.cat/00302.csv";
    private static final int FIRESTORE_LIMIT = 80;
    private static final int API_LIMIT = 400;
    private static final long API_CACHE_MS = Math.max(60_000L, BuildConfig.EVENTS_API_CACHE_MS);

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final Object API_CACHE_LOCK = new Object();
    private static List<Evento> apiCache = new ArrayList<>();
    private static long apiCacheAt = 0L;

    public interface Callback {
        void onOk(List<Evento> eventos);
        void onError(Exception e);
    }

    // Carga eventos desde la fuente correspondiente.
    public void cargarEventos(Callback cb) {
        Query query = FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .orderBy(FirestoreSchema.EventoFields.INICIO)
                .limit(FIRESTORE_LIMIT);

        query.get(Source.SERVER)
                .addOnSuccessListener(qs -> {
                    List<Evento> firestoreEventos = mapearEventosFirestore(qs);
                    cb.onOk(firestoreEventos);
                    cargarEventosApiAsincrono(firestoreEventos, cb, null);
                })
                .addOnFailureListener(serverError -> {
                    query.get()
                            .addOnSuccessListener(qs -> {
                                List<Evento> firestoreEventos = mapearEventosFirestore(qs);
                                cb.onOk(firestoreEventos);
                                cargarEventosApiAsincrono(firestoreEventos, cb, null);
                            })
                            .addOnFailureListener(firestoreError -> {
                                cargarEventosApiAsincrono(new ArrayList<>(), cb, firestoreError);
                            });
                });
    }

    // Gestiona invalidar cache api en este bloque.
    public static void invalidarCacheApi() {
        synchronized (API_CACHE_LOCK) {
            apiCache.clear();
            apiCacheAt = 0L;
        }
    }

    // Mapea eventos firestore al modelo usado por la app.
    private List<Evento> mapearEventosFirestore(Iterable<QueryDocumentSnapshot> docs) {
        List<Evento> list = new ArrayList<>();

        for (QueryDocumentSnapshot d : docs) {
            Boolean activo = d.getBoolean(FirestoreSchema.EventoFields.ACTIVO);
            if (activo != null && !activo) continue;

            Evento e = new Evento();
            e.setId(d.getId());
            String tituloBase = d.getString(FirestoreSchema.EventoFields.TITULO);
            String descripcionBase = d.getString(FirestoreSchema.EventoFields.DESCRIPCION);
            Map<String, String> tituloI18n = leerMapaString(d.get(FirestoreSchema.EventoFields.TITULO_I18N));
            Map<String, String> descripcionI18n = leerMapaString(d.get(FirestoreSchema.EventoFields.DESCRIPCION_I18N));
            e.setTitulo(resolverTextoI18n(tituloBase, tituloI18n));
            e.setDescripcion(resolverTextoI18n(descripcionBase, descripcionI18n));
            e.setCategoriaId(d.getString(FirestoreSchema.EventoFields.CATEGORIA_ID));
            e.setLugarNombre(d.getString(FirestoreSchema.EventoFields.LUGAR_NOMBRE));
            e.setCiudad(d.getString("ciudad"));
            e.setDireccion(d.getString("direccion"));
            e.setImagenUrl(d.getString("imagenUrl"));
            e.setImagenBase64(d.getString(FirestoreSchema.EventoFields.IMAGEN_BASE64));
            e.setLatitud(leerDouble(d, "latitud", "latitudEvento", "lat", "latitude"));
            e.setLongitud(leerDouble(d, "longitud", "longitudEvento", "lng", "lon", "longitude"));

            Boolean estadoActivo = d.getBoolean(FirestoreSchema.EventoFields.ACTIVO);
            e.setActivo(estadoActivo == null || estadoActivo);

            Object palabrasObj = d.get("palabrasClave");
            if (palabrasObj instanceof List<?>) {
                //noinspection unchecked
                e.setPalabrasClave((List<String>) palabrasObj);
            }

            com.google.firebase.Timestamp timestamp = d.getTimestamp(FirestoreSchema.EventoFields.INICIO);
            if (timestamp != null) {
                long inicioMillis = timestamp.toDate().getTime();
                e.setInicioMillis(inicioMillis);
                e.setFecha(formatearFecha(inicioMillis));
            } else {
                e.setInicioMillis(0L);
                e.setFecha("Fecha por confirmar");
            }

            Double precioBase = d.getDouble("precio");
            e.setPrecio(precioBase != null ? precioBase : 0.0);
            e.setImagenResId(obtenerImagenPredefinida(e.getCategoriaId()));

            list.add(e);
        }

        return list;
    }

    // Carga eventos api asincrono desde la fuente correspondiente.
    private void cargarEventosApiAsincrono(List<Evento> base, Callback cb, Exception fallbackError) {
        IO_EXECUTOR.execute(() -> {
            try {
                List<Evento> apiEventos = cargarEventosApiConCache();
                List<Evento> merged = fusionarEventos(base, apiEventos);

                if (base.isEmpty() || merged.size() != base.size()) {
                    publicarOk(cb, merged);
                }
            } catch (Exception apiError) {
                if (base.isEmpty()) {
                    publicarError(cb, fallbackError != null ? fallbackError : apiError);
                }
            }
        });
    }

    // Carga eventos api con cache desde la fuente correspondiente.
    private List<Evento> cargarEventosApiConCache() throws IOException {
        synchronized (API_CACHE_LOCK) {
            long now = System.currentTimeMillis();
            if (!apiCache.isEmpty() && (now - apiCacheAt) < API_CACHE_MS) {
                return new ArrayList<>(apiCache);
            }
        }

        List<Evento> descargados = descargarEventosDesdeCsv();

        synchronized (API_CACHE_LOCK) {
            apiCache = new ArrayList<>(descargados);
            apiCacheAt = System.currentTimeMillis();
            return new ArrayList<>(apiCache);
        }
    }

    // Gestiona descargar eventos desde csv en este bloque.
    private List<Evento> descargarEventosDesdeCsv() throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(TARRAGONA_API_CSV_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "text/csv");
            conn.setRequestProperty("User-Agent", "TarracoFest/1.0");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Error API Tarragona: HTTP " + code);
            }

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));

            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                return new ArrayList<>();
            }

            List<String> headers = parseCsvLine(headerLine);
            Map<String, Integer> indices = construirIndiceCabeceras(headers);

            List<Evento> out = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                List<String> row = parseCsvLine(line);
                Evento e = mapearFilaApi(row, indices);
                if (e != null) {
                    out.add(e);
                }
                if (out.size() >= API_LIMIT) break;
            }

            out.sort(Comparator.comparingLong(this::ordenarMillis));
            return out;

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Mapea fila api al modelo usado por la app.
    private Evento mapearFilaApi(List<String> row, Map<String, Integer> idx) {
        String titulo = valorCampo(row, idx, "TITOL");
        String uid = valorCampo(row, idx, "UID");
        String adreca = valorCampo(row, idx, "ADRECA");
        String url = valorCampo(row, idx, "URL");
        String inicioRaw = valorCampo(row, idx, "INICI");
        String fiRaw = valorCampo(row, idx, "FI");
        String categoriesRaw = valorCampo(row, idx, "CATEGORIES");
        String latitudRaw = valorCampo(row, idx, "LATITUD");
        String longitudRaw = valorCampo(row, idx, "LONGITUD");

        if (titulo.isEmpty() || inicioRaw.isEmpty()) return null;

        long inicioMillis = parsearFechaApiMillis(inicioRaw);
        if (inicioMillis <= 0L) return null;

        long fiMillis = parsearFechaApiMillis(fiRaw);
        long now = System.currentTimeMillis();

        if (fiMillis > 0L && fiMillis < now) return null;
        if (fiMillis <= 0L && inicioMillis < now) return null;

        Evento e = new Evento();
        e.setId(uid.isEmpty()
                ? ("api_" + Math.abs((titulo + "|" + inicioRaw).hashCode()))
                : ("api_" + uid));
        e.setActivo(true);
        e.setTitulo(titulo);
        e.setLugarNombre(adreca);
        e.setDireccion(adreca);
        e.setCiudad("Tarragona");
        e.setCategoriaId(inferirCategoriaId(categoriesRaw));
        e.setInicioMillis(inicioMillis);
        e.setFecha(formatearFecha(inicioMillis));
        e.setPrecio(0.0);
        e.setImagenResId(obtenerImagenPredefinida(e.getCategoriaId()));
        e.setImagenUrl("");
        e.setLatitud(parsearDouble(rawOrEmpty(latitudRaw)));
        e.setLongitud(parsearDouble(rawOrEmpty(longitudRaw)));

        String categoriesClean = categoriesRaw
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .trim();
        String urlNormalizada = normalizarUrlPublica(url);
        String descripcion = construirDescripcionApi(titulo, adreca, categoriesClean, inicioMillis);
        if (!urlNormalizada.isEmpty()) {
            descripcion = descripcion + "\n" + urlNormalizada;
        }
        e.setDescripcion(descripcion);

        return e;
    }

    // Gestiona construir descripcion api en este bloque.
    private String construirDescripcionApi(String titulo, String adreca, String categorias, long inicioMillis) {
        String t = titulo == null ? "" : titulo.trim();
        String lugar = adreca == null ? "" : adreca.trim();
        String tema = categorias == null ? "" : categorias.trim();

        StringBuilder sb = new StringBuilder();
        if (!t.isEmpty()) {
            sb.append(t).append(".");
        } else {
            sb.append("Evento de la agenda cultural de Tarragona.");
        }

        if (inicioMillis > 0L) {
            sb.append(" Fecha: ").append(formatearFecha(inicioMillis)).append(".");
        }
        if (!lugar.isEmpty()) {
            sb.append(" Lugar: ").append(lugar).append(".");
        }
        if (!tema.isEmpty()) {
            sb.append(" Categoria: ").append(tema).append(".");
        }
        sb.append(" Consulta la web oficial para mas detalles.");
        return sb.toString();
    }

    // Normaliza url publica para evitar inconsistencias de comparacion.
    private String normalizarUrlPublica(String rawUrl) {
        if (rawUrl == null) return "";
        String url = rawUrl.trim();
        if (url.isEmpty()) return "";

        url = url.replaceAll("[),.;]+$", "");
        if (url.isEmpty()) return "";

        if (url.startsWith("www.")) {
            url = "https://" + url;
        } else if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            url = "https://" + url;
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.trim().isEmpty()) return "";
            return uri.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    // Gestiona fusionar eventos en este bloque.
    private List<Evento> fusionarEventos(List<Evento> firestoreEventos, List<Evento> apiEventos) {
        LinkedHashMap<String, Evento> map = new LinkedHashMap<>();

        for (Evento e : firestoreEventos) {
            map.put(claveEvento(e), e);
        }
        for (Evento e : apiEventos) {
            map.putIfAbsent(claveEvento(e), e);
        }

        List<Evento> out = new ArrayList<>(map.values());
        out.sort(Comparator.comparingLong(this::ordenarMillis));
        return out;
    }

    // Gestiona clave evento en este bloque.
    private String claveEvento(Evento e) {
        if (e == null) return "";
        String titulo = safeLower(e.getTitulo());
        long inicio = e.getInicioMillis();
        if (inicio > 0L) return titulo + "|" + inicio;
        return titulo + "|" + safeLower(e.getFecha());
    }

    // Gestiona ordenar millis en este bloque.
    private long ordenarMillis(Evento e) {
        if (e == null || e.getInicioMillis() <= 0L) return Long.MAX_VALUE;
        return e.getInicioMillis();
    }

    // Gestiona formatear fecha en este bloque.
    private String formatearFecha(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    // Gestiona parsear fecha api millis en este bloque.
    private long parsearFechaApiMillis(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0L;

        String value = raw.trim();
        String[] patrones = new String[]{
                "yyyy-MM-dd HH:mm:ss.S",
                "yyyy-MM-dd HH:mm:ss"
        };

        for (String p : patrones) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.US);
                sdf.setLenient(false);
                Date d = sdf.parse(value);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0L;
    }

    // Gestiona inferir categoria id en este bloque.
    private String inferirCategoriaId(String categoriesRaw) {
        String n = normalizar(categoriesRaw);
        if (n.contains("music") || n.contains("concert")) return "musica";
        if (n.contains("gastronom")) return "gastronomia";
        if (n.contains("sport") || n.contains("esport") || n.contains("deport")
                || n.contains("futbol") || n.contains("basket") || n.contains("basquet")) {
            return "esport";
        }
        if (n.contains("famil") || n.contains("infantil") || n.contains("kids")
                || n.contains("nens") || n.contains("nenes")) {
            return "familiar";
        }
        return "cultura";
    }

    // Gestiona obtener imagen predefinida en este bloque.
    private int obtenerImagenPredefinida(String categoriaId) {
        String n = normalizar(categoriaId);
        if (n.contains("music")) return R.drawable.card_musica;
        if (n.contains("gastronom")) return R.drawable.card_gastronomia;
        if (n.contains("cultur")) return R.drawable.card_cultura;
        if (n.contains("sport") || n.contains("esport") || n.contains("deport")) {
            return R.drawable.card_esport;
        }
        if (n.contains("famil")) return R.drawable.card_familiar;
        return R.drawable.card_festival;
    }

    // Normaliza el flujo para evitar inconsistencias de comparacion.
    private String normalizar(String raw) {
        String base = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // Gestiona construir indice cabeceras en este bloque.
    private Map<String, Integer> construirIndiceCabeceras(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = headers.get(i);
            if (key == null) continue;
            String normalizada = key
                    .replace("\uFEFF", "")
                    .replace("\"", "")
                    .trim()
                    .toUpperCase(Locale.ROOT);
            if (!normalizada.isEmpty()) {
                map.put(normalizada, i);
            }
        }
        return map;
    }

    // Gestiona valor campo en este bloque.
    private String valorCampo(List<String> row, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null) return "";
        if (i < 0 || i >= row.size()) return "";
        String raw = row.get(i);
        return raw == null ? "" : raw.trim();
    }

    // Convierte csv line al formato interno necesario.
    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }

        out.add(sb.toString());
        return out;
    }

    // Gestiona safe lower en este bloque.
    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    // Gestiona raw or empty en este bloque.
    private String rawOrEmpty(String value) {
        return value == null ? "" : value;
    }

    // Gestiona parsear double en este bloque.
    private Double parsearDouble(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().replace("\"", "").replace(",", ".");
        if (clean.isEmpty()) return null;
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Gestiona leer double en este bloque.
    private Double leerDouble(QueryDocumentSnapshot d, String... keys) {
        if (d == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            Object value = d.get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String) {
                Double parsed = parsearDouble((String) value);
                if (parsed != null) return parsed;
            }
        }
        return null;
    }

    // Lee mapa i18n desde Firestore y conserva solo claves/valores string no vacios.
    private Map<String, String> leerMapaString(Object raw) {
        Map<String, String> out = new HashMap<>();
        if (!(raw instanceof Map<?, ?>)) return out;

        Map<?, ?> map = (Map<?, ?>) raw;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) continue;
            String k = safeLower((String) entry.getKey());
            String v = ((String) entry.getValue()).trim();
            if (k.isEmpty() || v.isEmpty()) continue;
            out.put(k, v);
        }
        return out;
    }

    // Resuelve texto segun idioma actual de la app con fallback: idioma actual -> es -> base.
    private String resolverTextoI18n(String base, Map<String, String> map) {
        String fallback = base == null ? "" : base.trim();
        if (map == null || map.isEmpty()) return fallback;

        String lang = idiomaAppActual();
        if (!lang.isEmpty()) {
            String exact = map.get(lang);
            if (exact != null && !exact.trim().isEmpty()) return exact.trim();
        }

        String es = map.get("es");
        if (es != null && !es.trim().isEmpty()) return es.trim();

        return fallback;
    }

    // Lee idioma actual configurado en AppCompat (si no existe, usa Locale del sistema).
    private String idiomaAppActual() {
        try {
            LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
            Locale locale = locales.isEmpty() ? Locale.getDefault() : locales.get(0);
            if (locale == null) return "";
            String lang = locale.getLanguage();
            return lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ignore) {
            String lang = Locale.getDefault().getLanguage();
            return lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
        }
    }

    // Gestiona publicar ok en este bloque.
    private void publicarOk(Callback cb, List<Evento> data) {
        MAIN_HANDLER.post(() -> cb.onOk(data));
    }

    // Gestiona publicar error en este bloque.
    private void publicarError(Callback cb, Exception e) {
        MAIN_HANDLER.post(() -> cb.onError(e));
    }
}
