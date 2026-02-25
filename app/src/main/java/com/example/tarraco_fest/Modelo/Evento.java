package com.example.tarraco_fest.Modelo;

import java.io.Serializable;
import java.util.List;

public class Evento implements Serializable {
    // Atributos mapeados exactamente a Firebase
    private String id;
    private boolean activo;
    private String categoriaId;
    private String ciudad;
    private String descripcion;
    private String direccion;
    private String lugarNombre;
    private List<String> palabrasClave;
    private String titulo;
    private String imagenUrl;

    // Atributos procesados para la UI
    private String fecha;
    private double precio;
    private int imagenResId;

    public Evento() {
        // Obligatorio para la deserialización de Firebase
    }

    // --- Getters ---
    public String getId() { return id; }
    public boolean isActivo() { return activo; }
    public String getCategoriaId() { return categoriaId; }
    public String getCiudad() { return ciudad; }
    public String getDescripcion() { return descripcion; }
    public String getDireccion() { return direccion; }
    public String getLugarNombre() { return lugarNombre; }
    public List<String> getPalabrasClave() { return palabrasClave; }
    public String getTitulo() { return titulo; }
    public String getFecha() { return fecha; }
    public double getPrecio() { return precio; }
    public int getImagenResId() { return imagenResId; }
    public String getImagenUrl() { return imagenUrl; }

    // --- Setters ---
    public void setId(String id) { this.id = id; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public void setLugarNombre(String lugarNombre) { this.lugarNombre = lugarNombre; }
    public void setPalabrasClave(List<String> palabrasClave) { this.palabrasClave = palabrasClave; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public void setPrecio(double precio) { this.precio = precio; }
    public void setImagenResId(int imagenResId) { this.imagenResId = imagenResId; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    // --- Métodos de conveniencia para la UI ---

    // Para el DetailActivity y el Adapter
    public String getUbicacion() {
        return (lugarNombre != null ? lugarNombre : "") +
                (ciudad != null ? " - " + ciudad : "");
    }

    // Normaliza la categoría de BD a la vista del filtro
    public String getCategoriaUI() {
        if (categoriaId == null) return "Todos";
        switch (categoriaId.toLowerCase()) {
            case "musica": return "Música";
            case "cultura": return "Cultura";
            case "gastronomia": return "Gastronomía";
            default: return "Todos";
        }
    }
}