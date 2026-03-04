package com.example.tarraco_fest.Modelo;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

/**
 * Modelo simple de evento para el modulo admin.
 * Representa el documento editable de Firestore.
 */
public class AdminEvento {
    public String id = "";
    public String titulo = "";
    public String descripcion = "";
    public String categoriaId = "cultura";
    public String lugarNombre = "";
    public String imagenUrl = "";
    public String imagenBase64 = "";
    public Map<String, String> tituloI18n = new HashMap<>();
    public Map<String, String> descripcionI18n = new HashMap<>();
    public Timestamp inicio;
    public boolean activo = true;
}
