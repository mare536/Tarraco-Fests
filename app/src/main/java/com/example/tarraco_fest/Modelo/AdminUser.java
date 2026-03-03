package com.example.tarraco_fest.Modelo;

import com.example.tarraco_fest.Data.FirestoreSchema;

/**
 * Modelo de usuario para vistas de administracion.
 * Contiene los datos minimos para listar y gestionar permisos.
 */
public class AdminUser {
    public String uid = "";
    public String nombre = "";
    public String email = "";
    public String rol = FirestoreSchema.UserRoles.USUARIO;
    public boolean bloqueado = false;
    public boolean tienePassword = false;
}
