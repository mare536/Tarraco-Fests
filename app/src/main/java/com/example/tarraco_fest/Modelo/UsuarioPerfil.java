package com.example.tarraco_fest.Modelo;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de dominio para perfil de usuario.
 * Incluye campos editables de cuenta y metadatos internos (rol, metodos, bloqueo).
 */
public class UsuarioPerfil {

    private String uid = "";
    private String email = "";
    private String nombreMostrado = "";
    private String telefono = "";
    private String ciudad = "";
    private String biografia = "";
    private String rol = FirestoreSchema.UserRoles.USUARIO;
    private boolean bloqueado = false;
    private String metodoAuth = "";
    private final List<String> metodosVinculados = new ArrayList<>();
    private boolean aceptaTerminos = false;
    private boolean tienePassword = false;
    private Timestamp creadoEn;
    private Timestamp updatedAt;

    // Devuelve uid.
    public String getUid() {
        return uid;
    }

    // Actualiza uid con el valor recibido.
    public void setUid(String uid) {
        this.uid = uid == null ? "" : uid.trim();
    }

    // Devuelve email.
    public String getEmail() {
        return email;
    }

    // Actualiza email con el valor recibido.
    public void setEmail(String email) {
        this.email = email == null ? "" : email.trim();
    }

    // Devuelve nombre mostrado.
    public String getNombreMostrado() {
        return nombreMostrado;
    }

    // Actualiza nombre mostrado con el valor recibido.
    public void setNombreMostrado(String nombreMostrado) {
        this.nombreMostrado = nombreMostrado == null ? "" : nombreMostrado.trim();
    }

    // Devuelve telefono.
    public String getTelefono() {
        return telefono;
    }

    // Actualiza telefono con el valor recibido.
    public void setTelefono(String telefono) {
        this.telefono = telefono == null ? "" : telefono.trim();
    }

    // Devuelve ciudad.
    public String getCiudad() {
        return ciudad;
    }

    // Actualiza ciudad con el valor recibido.
    public void setCiudad(String ciudad) {
        this.ciudad = ciudad == null ? "" : ciudad.trim();
    }

    // Devuelve biografia.
    public String getBiografia() {
        return biografia;
    }

    // Actualiza biografia con el valor recibido.
    public void setBiografia(String biografia) {
        this.biografia = biografia == null ? "" : biografia.trim();
    }

    // Devuelve rol.
    public String getRol() {
        return rol;
    }

    // Actualiza rol con el valor recibido.
    public void setRol(String rol) {
        this.rol = rol == null || rol.trim().isEmpty()
                ? FirestoreSchema.UserRoles.USUARIO
                : rol.trim();
    }

    // Indica si bloqueado.
    public boolean isBloqueado() {
        return bloqueado;
    }

    // Actualiza bloqueado con el valor recibido.
    public void setBloqueado(boolean bloqueado) {
        this.bloqueado = bloqueado;
    }

    // Devuelve metodo auth.
    public String getMetodoAuth() {
        return metodoAuth;
    }

    // Actualiza metodo auth con el valor recibido.
    public void setMetodoAuth(String metodoAuth) {
        this.metodoAuth = metodoAuth == null ? "" : metodoAuth.trim();
    }

    // Devuelve metodos vinculados.
    public List<String> getMetodosVinculados() {
        return metodosVinculados;
    }

    // Actualiza metodos vinculados con el valor recibido.
    public void setMetodosVinculados(List<String> items) {
        metodosVinculados.clear();
        if (items == null) return;
        for (String item : items) {
            if (item == null) continue;
            String limpio = item.trim();
            if (limpio.isEmpty()) continue;
            if (!metodosVinculados.contains(limpio)) metodosVinculados.add(limpio);
        }
    }

    // Indica si acepta terminos.
    public boolean isAceptaTerminos() {
        return aceptaTerminos;
    }

    // Actualiza acepta terminos con el valor recibido.
    public void setAceptaTerminos(boolean aceptaTerminos) {
        this.aceptaTerminos = aceptaTerminos;
    }

    // Indica si tiene password.
    public boolean isTienePassword() {
        return tienePassword;
    }

    // Actualiza tiene password con el valor recibido.
    public void setTienePassword(boolean tienePassword) {
        this.tienePassword = tienePassword;
    }

    // Devuelve creado en.
    public Timestamp getCreadoEn() {
        return creadoEn;
    }

    // Actualiza creado en con el valor recibido.
    public void setCreadoEn(Timestamp creadoEn) {
        this.creadoEn = creadoEn;
    }

    // Devuelve updated at.
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    // Actualiza updated at con el valor recibido.
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Indica si admin.
    public boolean esAdmin() {
        // El rol es uso interno de permisos; no se muestra en UI de usuario final.
        return FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(rol);
    }

    // Indica si metodo email esta disponible.
    public boolean tieneMetodoEmail() {
        return tienePassword || metodosVinculados.contains(FirestoreSchema.AuthMethods.EMAIL);
    }

    // Indica si metodo google esta disponible.
    public boolean tieneMetodoGoogle() {
        return metodosVinculados.contains(FirestoreSchema.AuthMethods.GOOGLE);
    }
}
