package com.example.tarraco_fest.Modelo;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

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

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid == null ? "" : uid.trim();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email.trim();
    }

    public String getNombreMostrado() {
        return nombreMostrado;
    }

    public void setNombreMostrado(String nombreMostrado) {
        this.nombreMostrado = nombreMostrado == null ? "" : nombreMostrado.trim();
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono == null ? "" : telefono.trim();
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad == null ? "" : ciudad.trim();
    }

    public String getBiografia() {
        return biografia;
    }

    public void setBiografia(String biografia) {
        this.biografia = biografia == null ? "" : biografia.trim();
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol == null || rol.trim().isEmpty()
                ? FirestoreSchema.UserRoles.USUARIO
                : rol.trim();
    }

    public boolean isBloqueado() {
        return bloqueado;
    }

    public void setBloqueado(boolean bloqueado) {
        this.bloqueado = bloqueado;
    }

    public String getMetodoAuth() {
        return metodoAuth;
    }

    public void setMetodoAuth(String metodoAuth) {
        this.metodoAuth = metodoAuth == null ? "" : metodoAuth.trim();
    }

    public List<String> getMetodosVinculados() {
        return metodosVinculados;
    }

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

    public boolean isAceptaTerminos() {
        return aceptaTerminos;
    }

    public void setAceptaTerminos(boolean aceptaTerminos) {
        this.aceptaTerminos = aceptaTerminos;
    }

    public boolean isTienePassword() {
        return tienePassword;
    }

    public void setTienePassword(boolean tienePassword) {
        this.tienePassword = tienePassword;
    }

    public Timestamp getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Timestamp creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean esAdmin() {
        return FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(rol);
    }

    public boolean tieneMetodoEmail() {
        return tienePassword || metodosVinculados.contains(FirestoreSchema.AuthMethods.EMAIL);
    }

    public boolean tieneMetodoGoogle() {
        return metodosVinculados.contains(FirestoreSchema.AuthMethods.GOOGLE);
    }
}
