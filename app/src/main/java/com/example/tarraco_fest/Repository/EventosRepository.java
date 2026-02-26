package com.example.tarraco_fest.Repository;

import android.os.Handler;
import android.os.Looper;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
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

public class EventosRepository {

    private static final String TARRAGONA_API_CSV_URL = "https://opendatafiles.tarragona.cat/00302.csv";
    private static final int FIRESTORE_LIMIT = 80;
    private static final int API_LIMIT = 400;
    private static final long API_CACHE_MS = 5L * 60L * 1000L;

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final Object API_CACHE_LOCK = new Object();
    private static List<Evento> apiCache = new ArrayList<>();
    private static long apiCacheAt = 0L;

    public interface Callback {
        void onOk(List<Evento> eventos);
        void onError(Exception e);
    }

    public void cargarEventos(Callback cb) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .orderBy(FirestoreSchema.EventoFields.INICIO)
                .limit(FIRESTORE_LIMIT)
                .get()
                .addOnSuccessListener(qs -> {
                    List<Evento> firestoreEventos = mapearEventosFirestore(qs);
                    cb.onOk(firestoreEventos);
                    cargarEventosApiAsincrono(firestoreEventos, cb, null);
                })
                .addOnFailureListener(firestoreError -> {
                    cargarEventosApiAsincrono(new ArrayList<>(), cb, firestoreError);
                });
    }

    private List<Evento> mapearEventosFirestore(Iterable<QueryDocumentSnapshot> docs) {
        List<Evento> list = new ArrayList<>();

        for (QueryDocumentSnapshot d : docs) {
            Boolean activo = d.getBoolean(FirestoreSchema.EventoFields.ACTIVO);
            if (activo != null && !activo) continue;

            Evento e = new Evento();
            e.setId(d.getId());
            e.setTitulo(d.getString(FirestoreSchema.EventoFields.TITULO));
            e.setDescripcion(d.getString("descripcion"));
            e.setCategoriaId(d.getString("categoriaId"));
            e.setLugarNombre(d.getString(FirestoreSchema.EventoFields.LUGAR_NOMBRE));
            e.setCiudad(d.getString("ciudad"));
            e.setDireccion(d.getString("direccion"));
            e.setImagenUrl(d.getString("imagenUrl"));

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

    private Evento mapearFilaApi(List<String> row, Map<String, Integer> idx) {
        String titulo = valorCampo(row, idx, "TITOL");
        String uid = valorCampo(row, idx, "UID");
        String adreca = valorCampo(row, idx, "ADRECA");
        String url = valorCampo(row, idx, "URL");
        String inicioRaw = valorCampo(row, idx, "INICI");
        String fiRaw = valorCampo(row, idx, "FI");
        String categoriesRaw = valorCampo(row, idx, "CATEGORIES");

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

        String categoriesClean = categoriesRaw
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .trim();
        String descripcion = categoriesClean.isEmpty() ? "Evento importado de agenda publica" : ("Categorias: " + categoriesClean);
        if (!url.isEmpty()) {
            descripcion = descripcion + "\n" + url;
        }
        e.setDescripcion(descripcion);

        return e;
    }

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

    private String claveEvento(Evento e) {
        if (e == null) return "";
        String titulo = safeLower(e.getTitulo());
        long inicio = e.getInicioMillis();
        if (inicio > 0L) return titulo + "|" + inicio;
        return titulo + "|" + safeLower(e.getFecha());
    }

    private long ordenarMillis(Evento e) {
        if (e == null || e.getInicioMillis() <= 0L) return Long.MAX_VALUE;
        return e.getInicioMillis();
    }

    private String formatearFecha(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

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

    private String normalizar(String raw) {
        String base = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

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

    private String valorCampo(List<String> row, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null) return "";
        if (i < 0 || i >= row.size()) return "";
        String raw = row.get(i);
        return raw == null ? "" : raw.trim();
    }

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

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void publicarOk(Callback cb, List<Evento> data) {
        MAIN_HANDLER.post(() -> cb.onOk(data));
    }

    private void publicarError(Callback cb, Exception e) {
        MAIN_HANDLER.post(() -> cb.onError(e));
    }
}
