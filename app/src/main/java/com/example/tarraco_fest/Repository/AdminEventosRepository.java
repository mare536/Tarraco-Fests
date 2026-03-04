package com.example.tarraco_fest.Repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.AdminEvento;
import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageException;

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repositorio de gestion de eventos para administracion.
 * Encapsula CRUD en Firestore y subida de imagenes en Storage.
 */
public class AdminEventosRepository {
    private static final String TAG = "AdminEventosRepository";
    private static final int DOWNLOAD_URL_RETRIES = 3;
    private static final long DOWNLOAD_URL_RETRY_DELAY_MS = 350L;
    private static final int MAX_UPLOAD_BYTES = 25 * 1024 * 1024;
    private static final int MAX_IMAGE_SIDE_PX = 1280;
    private static final int MAX_IMAGE_BYTES_FOR_FIRESTORE = 340 * 1024;
    private static final ExecutorService UPLOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AutoTranslationRepository autoTranslationRepository = new AutoTranslationRepository();

    public interface ListCallback {
        void onOk(List<AdminEvento> eventos);
        void onError(Exception e);
    }

    public interface ActionCallback {
        void onOk();
        void onError(Exception e);
    }

    public interface ImageUploadCallback {
        void onOk(String imageUrl);
        void onError(Exception e);
    }

    public interface EventI18nCallback {
        void onOk(Map<String, String> tituloI18n, Map<String, String> descripcionI18n);
    }

    // Carga eventos desde la fuente correspondiente.
    public void cargarEventos(ListCallback cb) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .orderBy(FirestoreSchema.EventoFields.INICIO)
                .limit(500)
                .get()
                .addOnSuccessListener(qs -> {
                    List<AdminEvento> out = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : qs) {
                        AdminEvento e = new AdminEvento();
                        e.id = doc.getId();
                        e.titulo = leer(doc.getString(FirestoreSchema.EventoFields.TITULO));
                        e.descripcion = leer(doc.getString(FirestoreSchema.EventoFields.DESCRIPCION));
                        e.categoriaId = leer(doc.getString(FirestoreSchema.EventoFields.CATEGORIA_ID));
                        if (TextUtils.isEmpty(e.categoriaId)) {
                            e.categoriaId = "cultura";
                        }
                        e.lugarNombre = leer(doc.getString(FirestoreSchema.EventoFields.LUGAR_NOMBRE));
                        e.imagenUrl = leer(doc.getString(FirestoreSchema.EventoFields.IMAGEN_URL));
                        e.imagenBase64 = leer(doc.getString(FirestoreSchema.EventoFields.IMAGEN_BASE64));
                        e.tituloI18n = leerMapaString(doc.get(FirestoreSchema.EventoFields.TITULO_I18N));
                        e.descripcionI18n = leerMapaString(doc.get(FirestoreSchema.EventoFields.DESCRIPCION_I18N));
                        e.inicio = doc.getTimestamp(FirestoreSchema.EventoFields.INICIO);
                        Boolean act = doc.getBoolean(FirestoreSchema.EventoFields.ACTIVO);
                        e.activo = act == null || act;
                        out.add(e);
                    }
                    cb.onOk(out);
                })
                .addOnFailureListener(cb::onError);
    }

    // Crea evento con los datos disponibles.
    public void crearEvento(AdminEvento evento, ActionCallback cb) {
        Map<String, Object> data = toFirestore(evento);
        data.put(FirestoreSchema.EventoFields.CREADO_EN, Timestamp.now());
        data.put(FirestoreSchema.EventoFields.UPDATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .add(data)
                .addOnSuccessListener(ref -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    // Actualiza evento con la logica de negocio actual.
    public void actualizarEvento(AdminEvento evento, ActionCallback cb) {
        if (evento.id == null || evento.id.trim().isEmpty()) {
            cb.onError(new IllegalArgumentException("Id de evento no valido"));
            return;
        }

        Map<String, Object> data = toFirestore(evento);
        data.put(FirestoreSchema.EventoFields.UPDATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .document(evento.id)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    // Actualiza activo con la logica de negocio actual.
    public void actualizarActivo(String eventId, boolean activo, ActionCallback cb) {
        Map<String, Object> up = new HashMap<>();
        up.put(FirestoreSchema.EventoFields.ACTIVO, activo);
        up.put(FirestoreSchema.EventoFields.UPDATED_AT, Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .document(eventId)
                .set(up, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    // Elimina evento de forma permanente en Firestore.
    public void eliminarEvento(String eventId, ActionCallback cb) {
        if (eventId == null || eventId.trim().isEmpty()) {
            cb.onError(new IllegalArgumentException("Id de evento no valido"));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.EVENTOS)
                .document(eventId)
                .delete()
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    // Gestiona to firestore en este bloque.
    private Map<String, Object> toFirestore(AdminEvento e) {
        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.EventoFields.TITULO, leer(e.titulo));
        data.put(FirestoreSchema.EventoFields.DESCRIPCION, leer(e.descripcion));
        data.put(FirestoreSchema.EventoFields.CATEGORIA_ID, leer(e.categoriaId));
        data.put(FirestoreSchema.EventoFields.LUGAR_NOMBRE, leer(e.lugarNombre));
        data.put(FirestoreSchema.EventoFields.IMAGEN_URL, leer(e.imagenUrl));
        data.put(FirestoreSchema.EventoFields.IMAGEN_BASE64, leer(e.imagenBase64));
        data.put(FirestoreSchema.EventoFields.TITULO_I18N, limpiarMapaI18n(e.tituloI18n, e.titulo));
        data.put(FirestoreSchema.EventoFields.DESCRIPCION_I18N, limpiarMapaI18n(e.descripcionI18n, e.descripcion));
        data.put(FirestoreSchema.EventoFields.INICIO, e.inicio != null ? e.inicio : Timestamp.now());
        data.put(FirestoreSchema.EventoFields.ACTIVO, e.activo);
        return data;
    }

    // Genera traducciones automaticas de titulo y descripcion para es/ca/en/ja.
    public void generarI18nEvento(String tituloBase, String descripcionBase, EventI18nCallback cb) {
        if (cb == null) return;
        autoTranslationRepository.translateToSupportedLocales(leer(tituloBase), tituloMap ->
                autoTranslationRepository.translateToSupportedLocales(leer(descripcionBase), descMap ->
                        cb.onOk(
                                tituloMap == null ? Collections.emptyMap() : tituloMap,
                                descMap == null ? Collections.emptyMap() : descMap
                        )));
    }

    // Mantiene nombre por compatibilidad: ahora codifica imagen para guardarla en Firestore.
    public void subirImagenEvento(Context context, Uri imageUri, ImageUploadCallback cb) {
        if (imageUri == null) {
            cb.onError(new IllegalArgumentException("Uri de imagen no valido"));
            return;
        }
        if (context == null) {
            cb.onError(new IllegalArgumentException("Contexto no valido para leer imagen"));
            return;
        }

        UPLOAD_EXECUTOR.execute(() -> {
            try {
                byte[] imageBytes = leerBytesDesdeUri(context, imageUri, MAX_UPLOAD_BYTES);
                String base64 = codificarImagenParaFirestore(imageBytes);
                mainHandler.post(() -> cb.onOk(base64));
            } catch (Exception e) {
                mainHandler.post(() -> cb.onError(e));
            }
        });
    }

    // Codifica la imagen en JPG optimizado para no superar el limite de documento de Firestore.
    private String codificarImagenParaFirestore(byte[] imageBytes) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        if (bitmap == null) throw new IOException("No se pudo decodificar la imagen");

        Bitmap scaled = escalarBitmap(bitmap, MAX_IMAGE_SIDE_PX);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int quality = 85;
        boolean ok = false;
        while (quality >= 35) {
            out.reset();
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
            if (out.size() <= MAX_IMAGE_BYTES_FOR_FIRESTORE) {
                ok = true;
                break;
            }
            quality -= 10;
        }

        if (!ok) {
            throw new IOException("La imagen es demasiado grande para Firestore. Elige una imagen mas ligera.");
        }

        byte[] finalBytes = out.toByteArray();
        String encoded = Base64.encodeToString(finalBytes, Base64.NO_WRAP);
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new IOException("No se pudo codificar la imagen");
        }
        return encoded;
    }

    // Escala manteniendo proporcion para reducir peso y consumo.
    private Bitmap escalarBitmap(Bitmap source, int maxSide) {
        int w = source.getWidth();
        int h = source.getHeight();
        int max = Math.max(w, h);
        if (max <= maxSide) return source;

        float scale = (float) maxSide / (float) max;
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(source, nw, nh, true);
    }

    // Intenta subir en distintos buckets cuando Firebase devuelve 404 por configuracion de bucket.
    private void intentarSubidaEnBucket(byte[] imageBytes,
                                        String filename,
                                        String downloadToken,
                                        StorageMetadata metadata,
                                        List<String> buckets,
                                        int index,
                                        ImageUploadCallback cb,
                                        Exception ultimoError) {
        if (index >= buckets.size()) {
            cb.onError(ultimoError != null
                    ? ultimoError
                    : new IllegalStateException("No hay bucket valido para subir imagen"));
            return;
        }

        String bucket = buckets.get(index);
        StorageReference ref = FirebaseStorage.getInstance("gs://" + bucket).getReference().child(filename);
        String publicUrlDirecta = construirUrlPublica(bucket, filename);

        Log.d(TAG, "Intentando subida en bucket=" + bucket + ", path=" + filename);

        ref.putBytes(imageBytes, metadata)
                .addOnSuccessListener(taskSnapshot -> {
                    StorageReference uploadedRef = taskSnapshot.getStorage();
                    Log.d(TAG, "Upload OK. ref=" + uploadedRef + ", bucket=" + resolverBucketSeguro(uploadedRef));
                    // Camino principal: URL directa por bucket+path, evita fallo intermitente -13010.
                    if (!TextUtils.isEmpty(publicUrlDirecta)) {
                        Log.d(TAG, "Usando URL directa de imagen: " + publicUrlDirecta);
                        cb.onOk(publicUrlDirecta);
                        return;
                    }
                    String tokenUrl = construirUrlPublicaConToken(uploadedRef, downloadToken);
                    if (!TextUtils.isEmpty(tokenUrl)) {
                        Log.d(TAG, "Usando URL con token de metadata.");
                        cb.onOk(tokenUrl);
                        return;
                    }
                    resolverDownloadUrl(uploadedRef, DOWNLOAD_URL_RETRIES, cb);
                })
                .addOnFailureListener(e -> {
                    StorageException se = extraerStorageException(e);
                    int code = se != null ? se.getErrorCode() : Integer.MIN_VALUE;
                    int http = se != null ? se.getHttpResultCode() : Integer.MIN_VALUE;
                    Log.e(TAG, "Fallo subida bucket=" + bucket + " code=" + code + " http=" + http, e);

                    boolean errorDeBucket = se != null && (
                            se.getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND
                                    || se.getErrorCode() == StorageException.ERROR_BUCKET_NOT_FOUND
                                    || se.getErrorCode() == StorageException.ERROR_PROJECT_NOT_FOUND
                                    || se.getHttpResultCode() == 404
                    );

                    if (errorDeBucket && index + 1 < buckets.size()) {
                        Log.w(TAG, "Reintentando subida con bucket alternativo...");
                        intentarSubidaEnBucket(
                                imageBytes,
                                filename,
                                downloadToken,
                                metadata,
                                buckets,
                                index + 1,
                                cb,
                                e
                        );
                        return;
                    }
                    cb.onError(e);
                });
    }

    // Lee bytes desde URI en segundo plano y limita tamano para evitar OOM o rechazo por reglas.
    private byte[] leerBytesDesdeUri(Context context, Uri imageUri, int maxBytes) throws IOException {
        InputStream in = null;
        ByteArrayOutputStream out = null;
        try {
            in = context.getContentResolver().openInputStream(imageUri);
            if (in == null) {
                throw new IOException("No se pudo abrir el flujo de la imagen");
            }
            out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("La imagen supera el limite de 25MB");
                }
                out.write(buffer, 0, read);
            }
            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                throw new IOException("La imagen seleccionada esta vacia");
            }
            return bytes;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) { }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignore) { }
            }
        }
    }

    // Intenta obtener la URL de descarga con reintentos en errores transitorios (-13010).
    private void resolverDownloadUrl(StorageReference uploadedRef, int retriesLeft, ImageUploadCallback cb) {
        uploadedRef.getDownloadUrl()
                .addOnSuccessListener(uri -> cb.onOk(uri.toString()))
                .addOnFailureListener(e -> {
                    StorageException se = extraerStorageException(e);
                    boolean objectNotFound = se != null && se.getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND;

                    if (objectNotFound && retriesLeft > 0) {
                        Log.w(TAG, "getDownloadUrl devolvio -13010, reintentando. retriesLeft=" + retriesLeft);
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> resolverDownloadUrl(uploadedRef, retriesLeft - 1, cb),
                                DOWNLOAD_URL_RETRY_DELAY_MS
                        );
                        return;
                    }

                    if (objectNotFound) {
                        String fallbackUrl = construirUrlPublica(uploadedRef);
                        if (!TextUtils.isEmpty(fallbackUrl)) {
                            Log.w(TAG, "Usando fallback URL tras -13010: " + fallbackUrl);
                            cb.onOk(fallbackUrl);
                            return;
                        }
                    }
                    Log.e(TAG, "No se pudo resolver URL de descarga: " + e.getMessage(), e);
                    cb.onError(e);
                });
    }

    // Construye URL publica directa para objetos legibles por reglas de Storage.
    private String construirUrlPublica(StorageReference ref) {
        if (ref == null) return null;
        String path = ref.getPath();
        String bucket = resolverBucketSeguro(ref);
        if (bucket == null || bucket.trim().isEmpty()) return null;
        if (path == null || path.trim().isEmpty()) return null;
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        return construirUrlPublica(bucket, cleanPath);
    }

    // Construye URL publica con token de descarga persistente en metadata.
    private String construirUrlPublicaConToken(StorageReference ref, String token) {
        if (token == null || token.trim().isEmpty()) return null;
        String base = construirUrlPublica(ref);
        if (base == null) return null;
        return base + "&token=" + Uri.encode(token);
    }

    // Construye URL publica con bucket y path conocidos.
    private String construirUrlPublica(String bucket, String path) {
        if (bucket == null || bucket.trim().isEmpty()) return null;
        if (path == null || path.trim().isEmpty()) return null;
        String encodedPath = Uri.encode(path.trim());
        return "https://firebasestorage.googleapis.com/v0/b/" + bucket.trim() + "/o/" + encodedPath + "?alt=media";
    }

    // Obtiene el bucket desde la configuracion del proyecto Firebase.
    private String obtenerBucketDesdeConfig() {
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            if (app == null || app.getOptions() == null) return null;
            String bucket = app.getOptions().getStorageBucket();
            return (bucket == null || bucket.trim().isEmpty()) ? null : bucket.trim();
        } catch (Exception ignore) {
            return null;
        }
    }

    // Construye lista de buckets candidatos para cubrir diferencias entre appspot y firebasestorage.app.
    private List<String> construirBucketsCandidatos() {
        Set<String> out = new LinkedHashSet<>();

        String bucketConfig = limpiarBucket(obtenerBucketDesdeConfig());
        String bucketResolucion = limpiarBucket(resolverBucketSeguro(null));
        String projectId = obtenerProjectIdSeguro();

        agregarBucketYAlias(out, bucketConfig);
        agregarBucketYAlias(out, bucketResolucion);
        if (!TextUtils.isEmpty(projectId)) {
            agregarBucketYAlias(out, projectId + ".appspot.com");
            agregarBucketYAlias(out, projectId + ".firebasestorage.app");
        }

        return new ArrayList<>(out);
    }

    // Agrega bucket y su alias equivalente (appspot/firebasestorage.app) para fallback automatico.
    private void agregarBucketYAlias(Set<String> set, String rawBucket) {
        String bucket = limpiarBucket(rawBucket);
        if (TextUtils.isEmpty(bucket)) return;
        set.add(bucket);

        if (bucket.endsWith(".firebasestorage.app")) {
            String alias = bucket.replace(".firebasestorage.app", ".appspot.com");
            set.add(alias);
        } else if (bucket.endsWith(".appspot.com")) {
            String alias = bucket.replace(".appspot.com", ".firebasestorage.app");
            set.add(alias);
        }
    }

    // Obtiene projectId desde FirebaseApp para construir buckets por convencion.
    private String obtenerProjectIdSeguro() {
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            if (app == null || app.getOptions() == null) return null;
            String projectId = app.getOptions().getProjectId();
            return projectId == null || projectId.trim().isEmpty() ? null : projectId.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ignore) {
            return null;
        }
    }

    // Intenta resolver el bucket por varias vias para evitar nulos en algunos dispositivos.
    private String resolverBucketSeguro(StorageReference ref) {
        if (ref != null) {
            String fromRef = limpiarBucket(ref.getBucket());
            if (!TextUtils.isEmpty(fromRef)) return fromRef;

            String fromRefUrl = extraerBucketDesdeGsUrl(ref.toString());
            if (!TextUtils.isEmpty(fromRefUrl)) return fromRefUrl;
        }

        try {
            StorageReference rootRef = FirebaseStorage.getInstance().getReference();
            String fromRoot = limpiarBucket(rootRef.getBucket());
            if (!TextUtils.isEmpty(fromRoot)) return fromRoot;

            String fromRootUrl = extraerBucketDesdeGsUrl(rootRef.toString());
            if (!TextUtils.isEmpty(fromRootUrl)) return fromRootUrl;
        } catch (Exception ignore) {
            // Sin accion: continuamos con config.
        }

        return limpiarBucket(obtenerBucketDesdeConfig());
    }

    // Limpia y normaliza bucket para construir URL estable.
    private String limpiarBucket(String rawBucket) {
        if (rawBucket == null) return null;
        String bucket = rawBucket.trim().toLowerCase(Locale.ROOT);
        if (bucket.startsWith("gs://")) {
            bucket = bucket.substring(5);
        }
        while (bucket.startsWith("/")) {
            bucket = bucket.substring(1);
        }
        int slash = bucket.indexOf('/');
        if (slash >= 0) {
            bucket = bucket.substring(0, slash);
        }
        return bucket.isEmpty() ? null : bucket;
    }

    // Extrae bucket desde referencia gs://bucket/path.
    private String extraerBucketDesdeGsUrl(String gsUrl) {
        if (gsUrl == null) return null;
        String value = gsUrl.trim();
        if (!value.startsWith("gs://")) return null;
        String noScheme = value.substring(5);
        int slash = noScheme.indexOf('/');
        String bucket = slash >= 0 ? noScheme.substring(0, slash) : noScheme;
        return limpiarBucket(bucket);
    }

    // Extrae StorageException aunque llegue envuelta por otras capas.
    private StorageException extraerStorageException(Throwable throwable) {
        Throwable actual = throwable;
        while (actual != null) {
            if (actual instanceof StorageException) return (StorageException) actual;
            actual = actual.getCause();
        }
        return null;
    }

    // Gestiona leer en este bloque.
    private String leer(String value) {
        return value == null ? "" : value.trim();
    }

    // Lee mapas de Firestore y conserva solo pares String/String no vacios.
    private Map<String, String> leerMapaString(Object raw) {
        Map<String, String> out = new HashMap<>();
        if (!(raw instanceof Map<?, ?>)) return out;

        Map<?, ?> input = (Map<?, ?>) raw;
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) continue;
            String k = leer((String) entry.getKey());
            String v = leer((String) entry.getValue());
            if (k.isEmpty() || v.isEmpty()) continue;
            out.put(k, v);
        }
        return out;
    }

    // Limpia mapa i18n y asegura fallback en es cuando falta.
    private Map<String, String> limpiarMapaI18n(Map<String, String> input, String baseEs) {
        Map<String, String> out = new HashMap<>();
        if (input != null) {
            for (Map.Entry<String, String> e : input.entrySet()) {
                String k = leer(e.getKey());
                String v = leer(e.getValue());
                if (k.isEmpty() || v.isEmpty()) continue;
                out.put(k, v);
            }
        }

        String safeEs = leer(baseEs);
        if (safeEs.isEmpty()) safeEs = out.get("es");
        if (safeEs == null) safeEs = "";

        if (!safeEs.isEmpty()) out.put("es", safeEs);
        if (!out.containsKey("ca") || leer(out.get("ca")).isEmpty()) out.put("ca", safeEs);
        if (!out.containsKey("en") || leer(out.get("en")).isEmpty()) out.put("en", safeEs);
        if (!out.containsKey("ja") || leer(out.get("ja")).isEmpty()) out.put("ja", safeEs);
        return out;
    }
}
