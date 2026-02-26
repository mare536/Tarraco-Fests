package com.example.tarraco_fest.Repository;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.UsuarioPerfil;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Capa de acceso a datos para perfil de usuario.
 * Centraliza Firebase Auth + Firestore para evitar logica de datos en la UI.
 */
public class UsuarioRepository {

    public interface PerfilCallback {
        void onOk(UsuarioPerfil perfil);
        void onError(Exception e);
    }

    public interface ActionCallback {
        void onOk();
        void onError(Exception e);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public UsuarioRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    public UsuarioRepository(FirebaseAuth auth, FirebaseFirestore db) {
        this.auth = auth;
        this.db = db;
    }

    public void cargarPerfilActual(PerfilCallback cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onError(new IllegalStateException("No hay sesion"));
            return;
        }

        db.collection(FirestoreSchema.Collections.USUARIOS)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    // Primera sesion: crea un documento base para mantener esquema consistente.
                    if (!doc.exists() || doc.getData() == null) {
                        Map<String, Object> inicial = crearDatosIniciales(user);
                        db.collection(FirestoreSchema.Collections.USUARIOS)
                                .document(user.getUid())
                                .set(inicial, SetOptions.merge())
                                .addOnSuccessListener(v -> cb.onOk(mapearPerfil(user, inicial)))
                                .addOnFailureListener(cb::onError);
                        return;
                    }

                    UsuarioPerfil perfil = mapearPerfil(user, doc.getData());
                    cb.onOk(perfil);
                })
                .addOnFailureListener(cb::onError);
    }

    public void guardarInformacionGeneral(UsuarioPerfil perfil, ActionCallback cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onError(new IllegalStateException("No hay sesion"));
            return;
        }

        String nombre = limpiar(perfil.getNombreMostrado());
        String telefono = limpiar(perfil.getTelefono());
        String ciudad = limpiar(perfil.getCiudad());
        String biografia = limpiar(perfil.getBiografia());

        UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                .setDisplayName(nombre)
                .build();

        user.updateProfile(req)
                .addOnSuccessListener(v -> {
                    // Firestore almacena datos extendidos del perfil (Auth solo displayName/email).
                    Map<String, Object> up = new HashMap<>();
                    up.put(FirestoreSchema.UsuarioFields.NOMBRE_MOSTRADO, nombre);
                    up.put(FirestoreSchema.UsuarioFields.EMAIL, user.getEmail() != null ? user.getEmail() : "");
                    up.put(FirestoreSchema.UsuarioFields.TELEFONO, telefono);
                    up.put(FirestoreSchema.UsuarioFields.CIUDAD, ciudad);
                    up.put(FirestoreSchema.UsuarioFields.BIOGRAFIA, biografia);
                    up.put(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS, resolverMetodosVinculados(user));
                    up.put(FirestoreSchema.UsuarioFields.TIENE_PASSWORD, tieneProveedorPassword(user));
                    up.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

                    db.collection(FirestoreSchema.Collections.USUARIOS)
                            .document(user.getUid())
                            .set(up, SetOptions.merge())
                            .addOnSuccessListener(x -> cb.onOk())
                            .addOnFailureListener(cb::onError);
                })
                .addOnFailureListener(cb::onError);
    }

    public void vincularPassword(String password, ActionCallback cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onError(new IllegalStateException("No hay sesion"));
            return;
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            cb.onError(new IllegalStateException("No se pudo obtener el email de la cuenta"));
            return;
        }
        if (tieneProveedorPassword(user)) {
            cb.onError(new IllegalStateException("Esta cuenta ya tiene contrasena"));
            return;
        }

        // Vincula credencial email/password sobre una cuenta ya autenticada (normalmente Google).
        user.linkWithCredential(EmailAuthProvider.getCredential(user.getEmail(), password))
                .addOnSuccessListener(r -> {
                    Map<String, Object> up = new HashMap<>();
                    up.put(FirestoreSchema.UsuarioFields.TIENE_PASSWORD, true);
                    up.put(FirestoreSchema.UsuarioFields.PASSWORD_VINCULADA_EN, Timestamp.now());
                    up.put(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS, resolverMetodosVinculados(user));
                    up.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

                    db.collection(FirestoreSchema.Collections.USUARIOS)
                            .document(user.getUid())
                            .set(up, SetOptions.merge())
                            .addOnSuccessListener(v -> cb.onOk())
                            .addOnFailureListener(cb::onError);
                })
                .addOnFailureListener(cb::onError);
    }

    public void sincronizarMetodosAutenticacion(ActionCallback cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onError(new IllegalStateException("No hay sesion"));
            return;
        }

        Map<String, Object> up = new HashMap<>();
        up.put(FirestoreSchema.UsuarioFields.EMAIL, user.getEmail() != null ? user.getEmail() : "");
        up.put(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS, resolverMetodosVinculados(user));
        up.put(FirestoreSchema.UsuarioFields.TIENE_PASSWORD, tieneProveedorPassword(user));
        up.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

        db.collection(FirestoreSchema.Collections.USUARIOS)
                .document(user.getUid())
                .set(up, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(cb::onError);
    }

    private UsuarioPerfil mapearPerfil(FirebaseUser user, Map<String, Object> data) {
        UsuarioPerfil p = new UsuarioPerfil();

        p.setUid(user.getUid());
        p.setEmail(leerTexto(data.get(FirestoreSchema.UsuarioFields.EMAIL)));
        if (p.getEmail().isEmpty() && user.getEmail() != null) p.setEmail(user.getEmail());

        p.setNombreMostrado(leerTexto(data.get(FirestoreSchema.UsuarioFields.NOMBRE_MOSTRADO)));
        if (p.getNombreMostrado().isEmpty() && user.getDisplayName() != null) {
            p.setNombreMostrado(user.getDisplayName());
        }
        if (p.getNombreMostrado().isEmpty()) p.setNombreMostrado("Usuario");

        p.setTelefono(leerTexto(data.get(FirestoreSchema.UsuarioFields.TELEFONO)));
        p.setCiudad(leerTexto(data.get(FirestoreSchema.UsuarioFields.CIUDAD)));
        p.setBiografia(leerTexto(data.get(FirestoreSchema.UsuarioFields.BIOGRAFIA)));
        p.setRol(leerTexto(data.get(FirestoreSchema.UsuarioFields.ROL)));

        Object bloqueado = data.get(FirestoreSchema.UsuarioFields.BLOQUEADO);
        p.setBloqueado(bloqueado instanceof Boolean && (Boolean) bloqueado);

        p.setMetodoAuth(leerTexto(data.get(FirestoreSchema.UsuarioFields.METODO_AUTH)));
        p.setMetodosVinculados(leerMetodosVinculados(data.get(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS), user));

        Object aceptaTerminos = data.get(FirestoreSchema.UsuarioFields.ACEPTA_TERMINOS);
        p.setAceptaTerminos(aceptaTerminos instanceof Boolean && (Boolean) aceptaTerminos);

        Object tienePasswordDoc = data.get(FirestoreSchema.UsuarioFields.TIENE_PASSWORD);
        boolean tienePassword = tienePasswordDoc instanceof Boolean
                ? (Boolean) tienePasswordDoc
                : tieneProveedorPassword(user);
        p.setTienePassword(tienePassword);

        Object creadoEn = data.get(FirestoreSchema.UsuarioFields.CREADO_EN);
        if (creadoEn instanceof Timestamp) p.setCreadoEn((Timestamp) creadoEn);

        Object updatedAt = data.get(FirestoreSchema.UsuarioFields.UPDATED_AT);
        if (updatedAt instanceof Timestamp) p.setUpdatedAt((Timestamp) updatedAt);

        return p;
    }

    private Map<String, Object> crearDatosIniciales(FirebaseUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.UsuarioFields.NOMBRE_MOSTRADO,
                user.getDisplayName() != null ? user.getDisplayName() : "Usuario");
        data.put(FirestoreSchema.UsuarioFields.EMAIL, user.getEmail() != null ? user.getEmail() : "");
        data.put(FirestoreSchema.UsuarioFields.ROL, FirestoreSchema.UserRoles.USUARIO);
        data.put(FirestoreSchema.UsuarioFields.BLOQUEADO, false);
        data.put(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS, resolverMetodosVinculados(user));
        data.put(FirestoreSchema.UsuarioFields.TIENE_PASSWORD, tieneProveedorPassword(user));
        data.put(FirestoreSchema.UsuarioFields.CREADO_EN, Timestamp.now());
        data.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());
        return data;
    }

    private List<String> resolverMetodosVinculados(FirebaseUser user) {
        List<String> out = new ArrayList<>();
        if (user == null) return out;

        for (UserInfo info : user.getProviderData()) {
            if (info == null) continue;
            if ("google.com".equals(info.getProviderId())
                    && !out.contains(FirestoreSchema.AuthMethods.GOOGLE)) {
                out.add(FirestoreSchema.AuthMethods.GOOGLE);
            }
            if ("password".equals(info.getProviderId())
                    && !out.contains(FirestoreSchema.AuthMethods.EMAIL)) {
                out.add(FirestoreSchema.AuthMethods.EMAIL);
            }
        }
        return out;
    }

    private List<String> leerMetodosVinculados(Object raw, FirebaseUser user) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof String)) continue;
                String valor = ((String) item).trim();
                if (valor.isEmpty()) continue;
                if (!out.contains(valor)) out.add(valor);
            }
        }

        // Asegura que Firestore y providers reales de Auth no queden desalineados.
        for (String metodo : resolverMetodosVinculados(user)) {
            if (!out.contains(metodo)) out.add(metodo);
        }
        return out;
    }

    private boolean tieneProveedorPassword(FirebaseUser user) {
        if (user == null) return false;
        for (UserInfo info : user.getProviderData()) {
            if (info != null && "password".equals(info.getProviderId())) return true;
        }
        return false;
    }

    private String leerTexto(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }

    private String limpiar(String value) {
        return value == null ? "" : value.trim();
    }
}
