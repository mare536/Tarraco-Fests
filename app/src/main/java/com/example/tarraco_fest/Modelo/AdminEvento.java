package com.example.tarraco_fest.Modelo;

import com.google.firebase.Timestamp;

/**
 * Modelo simple de evento para el modulo admin.
 * Representa el documento editable de Firestore.
 */
public class AdminEvento {
    public String id = "";
    public String titulo = "";
    public String categoriaId = "cultura";
    public String lugarNombre = "";
    public String imagenUrl = "";
    public String imagenBase64 = "";
    public Timestamp inicio;
    public boolean activo = true;
}
