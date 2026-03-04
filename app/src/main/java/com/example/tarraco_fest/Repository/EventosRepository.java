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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repositorio principal de eventos para Home y listados.
 * Combina fuente remota, cache local y normalizacion de datos.
 */
public class EventosRepository {

    private static final String TARRAGONA_API_CSV_URL = "https://opendatafiles.tarragona.cat/00302.csv";
    private static final int FIRESTORE_LIMIT = 80;
    private static final int API_LIMIT = 400;
    private static final long API_CACHE_MS = Math.max(60_000L, BuildConfig.EVENTS_API_CACHE_MS);
    private static final int RUNTIME_TRANSLATION_MAX_ITEMS = 0;
    private static final long RUNTIME_TRANSLATION_BUDGET_MS = 0L;
    private static final int PROGRESSIVE_TRANSLATION_MAX_EVENTS = 120;
    private static final int PROGRESSIVE_TRANSLATION_PUBLISH_EVERY = 6;

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ExecutorService TRANSLATION_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final Object API_CACHE_LOCK = new Object();
    private static List<Evento> apiCache = new ArrayList<>();
    private static long apiCacheAt = 0L;
    private static String apiCacheLang = "";

    private final AutoTranslationRepository autoTranslationRepository = new AutoTranslationRepository();
    private final AtomicLong cargaSecuencia = new AtomicLong(0L);
    private final AtomicLong traduccionSecuencia = new AtomicLong(0L);

    public interface Callback {
        void onOk(List<Evento> eventos);
        void onError(Exception e);

        default void onLoadingStateChanged(boolean isLoading) {
            // Optional
        }

        default void onTranslationStateChanged(boolean isTranslating) {
            // Optional
        }
    }

    // Carga eventos desde la fuente correspondiente.
    public void cargarEventos(Callback cb) {
        final long cargaId = cargaSecuencia.incrementAndGet();
        traduccionSecuencia.incrementAndGet(); // Invalida traducciones anteriores.
        publicarTranslating(cb, false);
        publicarLoading(cb, true);

        Query query = FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .orderBy(FirestoreSchema.EventoFields.INICIO)
                .limit(FIRESTORE_LIMIT);

        query.get(Source.SERVER)
                .addOnSuccessListener(qs -> procesarResultadoFirestore(qs, cb, null, cargaId))
                .addOnFailureListener(serverError -> {
                    query.get()
                            .addOnSuccessListener(qs -> procesarResultadoFirestore(qs, cb, null, cargaId))
                            .addOnFailureListener(firestoreError -> {
                                cargarEventosApiAsincrono(new ArrayList<>(), cb, firestoreError, cargaId);
                            });
                });
    }

    // Procesa mapeo Firestore en IO para evitar bloqueos de UI y luego continua con API.
    private void procesarResultadoFirestore(Iterable<QueryDocumentSnapshot> docs, Callback cb, Exception fallbackError, long cargaId) {
        IO_EXECUTOR.execute(() -> {
            if (cargaId != cargaSecuencia.get()) return;
            List<Evento> firestoreEventos = mapearEventosFirestore(docs);
            if (cargaId != cargaSecuencia.get()) return;
            publicarOk(cb, firestoreEventos);
            publicarLoading(cb, false);
            cargarEventosApiAsincrono(firestoreEventos, cb, fallbackError, cargaId);
        });
    }

    // Gestiona invalidar cache api en este bloque.
    public static void invalidarCacheApi() {
        synchronized (API_CACHE_LOCK) {
            apiCache.clear();
            apiCacheAt = 0L;
            apiCacheLang = "";
        }
    }

    // Mapea eventos firestore al modelo usado por la app.
    private List<Evento> mapearEventosFirestore(Iterable<QueryDocumentSnapshot> docs) {
        List<Evento> list = new ArrayList<>();
        String lang = normalizarIdioma(idiomaAppActual());
        final long runtimeTranslateDeadline = System.currentTimeMillis() + RUNTIME_TRANSLATION_BUDGET_MS;
        int runtimeTranslateCount = 0;

        for (QueryDocumentSnapshot d : docs) {
            Boolean activo = d.getBoolean(FirestoreSchema.EventoFields.ACTIVO);
            if (activo != null && !activo) continue;

            Evento e = new Evento();
            e.setId(d.getId());
            String tituloBase = d.getString(FirestoreSchema.EventoFields.TITULO);
            String descripcionBase = d.getString(FirestoreSchema.EventoFields.DESCRIPCION);
            Map<String, String> tituloI18n = leerMapaString(d.get(FirestoreSchema.EventoFields.TITULO_I18N));
            Map<String, String> descripcionI18n = leerMapaString(d.get(FirestoreSchema.EventoFields.DESCRIPCION_I18N));
            String tituloResuelto = resolverTextoI18n(tituloBase, tituloI18n);
            String descripcionResuelta = resolverTextoI18n(descripcionBase, descripcionI18n);

            if (debeIntentarTraduccionDinamica(lang, tituloBase, tituloResuelto, tituloI18n)
                    && runtimeTranslateCount < RUNTIME_TRANSLATION_MAX_ITEMS
                    && System.currentTimeMillis() < runtimeTranslateDeadline) {
                tituloResuelto = traducirTextoRuntime(tituloResuelto, lang);
                runtimeTranslateCount++;
            }

            if (debeIntentarTraduccionDinamica(lang, descripcionBase, descripcionResuelta, descripcionI18n)
                    && runtimeTranslateCount < RUNTIME_TRANSLATION_MAX_ITEMS
                    && System.currentTimeMillis() < runtimeTranslateDeadline) {
                descripcionResuelta = traducirTextoRuntime(descripcionResuelta, lang);
                runtimeTranslateCount++;
            }

            e.setTitulo(tituloResuelto);
            e.setDescripcion(descripcionResuelta);
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
    private void cargarEventosApiAsincrono(List<Evento> base, Callback cb, Exception fallbackError, long cargaId) {
        IO_EXECUTOR.execute(() -> {
            if (cargaId != cargaSecuencia.get()) return;
            try {
                List<Evento> apiEventos = cargarEventosApiConCache();
                if (cargaId != cargaSecuencia.get()) return;
                List<Evento> merged = fusionarEventos(base, apiEventos);

                if (base.isEmpty() || merged.size() != base.size()) {
                    publicarOk(cb, merged);
                }

                List<Evento> referenciaUi = (base.isEmpty() || merged.size() != base.size()) ? merged : base;
                iniciarTraduccionProgresiva(referenciaUi, cb, cargaId);
            } catch (Exception apiError) {
                if (base.isEmpty()) {
                    publicarLoading(cb, false);
                    publicarError(cb, fallbackError != null ? fallbackError : apiError);
                } else {
                    iniciarTraduccionProgresiva(base, cb, cargaId);
                }
            }
        });
    }

    // Carga eventos api con cache desde la fuente correspondiente.
    private List<Evento> cargarEventosApiConCache() throws IOException {
        String currentLang = normalizarIdioma(idiomaAppActual());
        List<Evento> staleCache;

        synchronized (API_CACHE_LOCK) {
            long now = System.currentTimeMillis();
            if (!apiCache.isEmpty() && (now - apiCacheAt) < API_CACHE_MS
                    && currentLang.equals(apiCacheLang)) {
                return new ArrayList<>(apiCache);
            }
            staleCache = new ArrayList<>(apiCache);
        }

        List<Evento> descargados;
        try {
            descargados = descargarEventosDesdeCsv(currentLang);
        } catch (IOException ioException) {
            if (!staleCache.isEmpty()) {
                return staleCache;
            }
            throw ioException;
        }

        // Si la API responde vacia por un problema transitorio de parsing/datos, mantenemos cache previa.
        if (descargados.isEmpty() && !staleCache.isEmpty()) {
            return staleCache;
        }

        synchronized (API_CACHE_LOCK) {
            apiCache = new ArrayList<>(descargados);
            apiCacheAt = System.currentTimeMillis();
            apiCacheLang = currentLang;
            return new ArrayList<>(apiCache);
        }
    }

    // Gestiona descargar eventos desde csv en este bloque.
    private List<Evento> descargarEventosDesdeCsv(String uiLang) throws IOException {
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
                Evento e = mapearFilaApi(row, indices, uiLang);
                if (e != null) {
                    out.add(e);
                }
                if (out.size() >= API_LIMIT) break;
            }

            aplicarTraduccionRuntimeApi(out, uiLang);
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
    private Evento mapearFilaApi(List<String> row, Map<String, Integer> idx, String uiLang) {
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
        String tituloLocalizado = titulo;
        e.setTitulo(tituloLocalizado);
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
        String descripcion = construirDescripcionApi(tituloLocalizado, adreca, categoriesClean, inicioMillis, uiLang);
        if (!urlNormalizada.isEmpty()) {
            descripcion = descripcion + "\n" + urlNormalizada;
        }
        e.setDescripcion(descripcion);

        return e;
    }

    // Gestiona construir descripcion api en este bloque.
    private String construirDescripcionApi(String titulo, String adreca, String categorias, long inicioMillis, String uiLang) {
        String t = titulo == null ? "" : titulo.trim();
        String lugar = adreca == null ? "" : adreca.trim();
        String tema = categorias == null ? "" : categorias.trim();
        String lang = normalizarIdioma(uiLang);

        String fraseBase;
        String labelFecha;
        String labelLugar;
        String labelCategoria;
        String fraseWeb;

        switch (lang) {
            case "en":
                fraseBase = "Event from Tarragona cultural agenda.";
                labelFecha = "Date";
                labelLugar = "Place";
                labelCategoria = "Category";
                fraseWeb = "Check the official website for more details.";
                break;
            case "ja":
                fraseBase = "Event from Tarragona cultural agenda.";
                labelFecha = "Date";
                labelLugar = "Place";
                labelCategoria = "Category";
                fraseWeb = "Check the official website for more details.";
                break;
            case "ca":
                fraseBase = "Esdeveniment de l agenda cultural de Tarragona.";
                labelFecha = "Data";
                labelLugar = "Lloc";
                labelCategoria = "Categoria";
                fraseWeb = "Consulta el web oficial per a mes detalls.";
                break;
            default:
                fraseBase = "Evento de la agenda cultural de Tarragona.";
                labelFecha = "Fecha";
                labelLugar = "Lugar";
                labelCategoria = "Categoria";
                fraseWeb = "Consulta la web oficial para mas detalles.";
                break;
        }

        StringBuilder sb = new StringBuilder();
        if (!t.isEmpty()) {
            sb.append(t).append(".");
        } else {
            sb.append(fraseBase);
        }

        if (inicioMillis > 0L) {
            sb.append(" ").append(labelFecha).append(": ").append(formatearFecha(inicioMillis)).append(".");
        }
        if (!lugar.isEmpty()) {
            sb.append(" ").append(labelLugar).append(": ").append(lugar).append(".");
        }
        if (!tema.isEmpty()) {
            sb.append(" ").append(labelCategoria).append(": ").append(tema).append(".");
        }
        sb.append(" ").append(fraseWeb);
        return sb.toString();
    }

    // Traduce una parte acotada de eventos API para no bloquear la carga principal.
    private void aplicarTraduccionRuntimeApi(List<Evento> eventos, String uiLang) {
        if (eventos == null || eventos.isEmpty()) return;

        String lang = normalizarIdioma(uiLang);
        if ("es".equals(lang)) return;

        final long deadline = System.currentTimeMillis() + RUNTIME_TRANSLATION_BUDGET_MS;
        int translated = 0;

        for (Evento evento : eventos) {
            if (evento == null) continue;
            if (translated >= RUNTIME_TRANSLATION_MAX_ITEMS) break;
            if (System.currentTimeMillis() >= deadline) break;

            String titulo = evento.getTitulo();
            if (titulo == null || titulo.trim().isEmpty()) continue;

            String tituloTraducido = traducirTextoRuntime(titulo, lang);
            if (!safeLower(tituloTraducido).equals(safeLower(titulo))) {
                evento.setTitulo(tituloTraducido);
                translated++;
            }
        }
    }

    // Decide si debe aplicarse traduccion dinamica cuando faltan textos i18n para el idioma actual.
    private boolean debeIntentarTraduccionDinamica(String lang, String base, String resolved, Map<String, String> i18nMap) {
        if ("es".equals(lang)) return false;

        String resolvedSafe = resolved == null ? "" : resolved.trim();
        if (resolvedSafe.isEmpty()) return false;

        String baseSafe = base == null ? "" : base.trim();
        String valueLang = "";
        if (i18nMap != null && lang != null) {
            String mapValue = i18nMap.get(lang);
            valueLang = mapValue == null ? "" : mapValue.trim();
        }

        // Si ya tenemos traduccion real para el idioma, no forzamos traduccion en runtime.
        if (!valueLang.isEmpty() && !safeLower(valueLang).equals(safeLower(baseSafe))) {
            return false;
        }

        // Si el texto mostrado es igual al base, intentamos traducir en caliente.
        return safeLower(resolvedSafe).equals(safeLower(baseSafe));
    }

    // Traduce texto con timeouts cortos para evitar que Home quede bloqueado.
    private String traducirTextoRuntime(String sourceText, String lang) {
        String source = sourceText == null ? "" : sourceText.trim();
        if (source.isEmpty()) return source;
        if (lang == null || lang.trim().isEmpty() || "es".equals(lang)) return source;
        return autoTranslationRepository.translateBlockingFast(source, lang);
    }

    // Traduce progresivamente los eventos ya renderizados y publica actualizaciones por tandas.
    private void iniciarTraduccionProgresiva(List<Evento> eventosBase, Callback cb, long cargaId) {
        if (eventosBase == null || eventosBase.isEmpty()) {
            publicarTranslating(cb, false);
            return;
        }

        String idiomaDestino = normalizarIdioma(idiomaAppActual());
        if ("es".equals(idiomaDestino)) {
            publicarTranslating(cb, false);
            return;
        }

        final long traduccionId = traduccionSecuencia.incrementAndGet();
        final List<Evento> trabajo = clonarEventos(eventosBase);
        publicarTranslating(cb, true);

        TRANSLATION_EXECUTOR.execute(() -> {
            int cambiosPublicables = 0;
            int traducidos = 0;
            try {
                for (Evento evento : trabajo) {
                    if (cargaId != cargaSecuencia.get() || traduccionId != traduccionSecuencia.get()) return;
                    if (evento == null) continue;
                    if (traducidos >= PROGRESSIVE_TRANSLATION_MAX_EVENTS) break;

                    boolean cambio = traducirEventoProgresivo(evento, idiomaDestino);
                    traducidos++;

                    if (!cambio) continue;
                    cambiosPublicables++;

                    if (cambiosPublicables % PROGRESSIVE_TRANSLATION_PUBLISH_EVERY == 0) {
                        publicarOk(cb, clonarEventos(trabajo));
                    }
                }

                if (cambiosPublicables > 0
                        && cargaId == cargaSecuencia.get()
                        && traduccionId == traduccionSecuencia.get()) {
                    publicarOk(cb, clonarEventos(trabajo));
                }
            } finally {
                if (cargaId == cargaSecuencia.get() && traduccionId == traduccionSecuencia.get()) {
                    publicarTranslating(cb, false);
                }
            }
        });
    }

    // Traduce titulo y descripcion de un evento con fallback seguro si la respuesta viene corrupta.
    private boolean traducirEventoProgresivo(Evento evento, String idiomaDestino) {
        if (evento == null) return false;
        boolean changed = false;

        String titulo = evento.getTitulo();
        String tituloTraducido = traducirTextoSeguro(titulo, idiomaDestino);
        if (!safeLower(tituloTraducido).equals(safeLower(titulo))) {
            evento.setTitulo(tituloTraducido);
            changed = true;
        }

        String descripcion = evento.getDescripcion();
        if (debeTraducirDescripcion(descripcion)) {
            String descTraducida = traducirTextoSeguro(descripcion, idiomaDestino);
            if (!safeLower(descTraducida).equals(safeLower(descripcion))) {
                evento.setDescripcion(descTraducida);
                changed = true;
            }
        }

        return changed;
    }

    // Evita traducciones que suelen romper enlaces o texto tecnico.
    private boolean debeTraducirDescripcion(String text) {
        if (text == null) return false;
        String v = text.trim();
        if (v.isEmpty()) return false;
        String lower = v.toLowerCase(Locale.ROOT);
        return !lower.contains("http://") && !lower.contains("https://") && !lower.contains("www.");
    }

    // Traduce un texto y descarta respuestas potencialmente corruptas (mojibake).
    private String traducirTextoSeguro(String sourceText, String idiomaDestino) {
        String source = sourceText == null ? "" : sourceText.trim();
        if (source.isEmpty()) return source;
        if (idiomaDestino == null || idiomaDestino.trim().isEmpty() || "es".equals(idiomaDestino)) {
            return source;
        }

        String translated = autoTranslationRepository.translateBlocking(source, idiomaDestino);
        if (translated == null) return source;
        String safe = translated.trim();
        if (safe.isEmpty()) return source;
        if (pareceTextoCorrupto(safe)) return source;
        return safe;
    }

    // Detecta patrones comunes de texto roto por codificacion.
    private boolean pareceTextoCorrupto(String text) {
        if (text == null || text.isEmpty()) return false;
        if (text.indexOf('\uFFFD') >= 0) return true;
        if (text.contains("Ã") || text.contains("Â")) return true;
        if (text.contains("â€") || text.contains("â€™") || text.contains("â€œ")) return true;
        return false;
    }

    // Copia defensiva de eventos para publicar cambios progresivos sin mutar la referencia original.
    private List<Evento> clonarEventos(List<Evento> source) {
        List<Evento> out = new ArrayList<>();
        if (source == null) return out;

        for (Evento e : source) {
            if (e == null) continue;
            Evento c = new Evento();
            c.setId(e.getId());
            c.setActivo(e.isActivo());
            c.setCategoriaId(e.getCategoriaId());
            c.setCiudad(e.getCiudad());
            c.setDescripcion(e.getDescripcion());
            c.setDireccion(e.getDireccion());
            c.setLugarNombre(e.getLugarNombre());
            c.setPalabrasClave(e.getPalabrasClave());
            c.setTitulo(e.getTitulo());
            c.setImagenUrl(e.getImagenUrl());
            c.setImagenBase64(e.getImagenBase64());
            c.setFecha(e.getFecha());
            c.setInicioMillis(e.getInicioMillis());
            c.setPrecio(e.getPrecio());
            c.setImagenResId(e.getImagenResId());
            c.setLatitud(e.getLatitud());
            c.setLongitud(e.getLongitud());
            c.setDistanciaKm(e.getDistanciaKm());
            c.setFavorito(e.isFavorito());
            out.add(c);
        }
        return out;
    }

    // Normaliza codigo de idioma a formato corto (es/ca/en/ja).
    private String normalizarIdioma(String raw) {
        if (raw == null) return "es";
        String lang = raw.trim().toLowerCase(Locale.ROOT);
        if (lang.isEmpty()) return "es";
        int dash = lang.indexOf('-');
        if (dash > 0) lang = lang.substring(0, dash);
        int underscore = lang.indexOf('_');
        if (underscore > 0) lang = lang.substring(0, underscore);
        return lang;
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

    // Publica estado de carga inicial de datos al hilo principal.
    private void publicarLoading(Callback cb, boolean isLoading) {
        MAIN_HANDLER.post(() -> cb.onLoadingStateChanged(isLoading));
    }

    // Publica estado de traduccion progresiva al hilo principal.
    private void publicarTranslating(Callback cb, boolean isTranslating) {
        MAIN_HANDLER.post(() -> cb.onTranslationStateChanged(isTranslating));
    }

    // Gestiona publicar error en este bloque.
    private void publicarError(Callback cb, Exception e) {
        MAIN_HANDLER.post(() -> cb.onError(e));
    }
}
