package com.example.tarraco_fest.Modelo;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

/**
 * Modelo de dominio para eventos mostrados al usuario final.
 * Incluye datos de contenido, visual y metadatos de fuente.
 */
public class Evento implements Serializable {

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
    private String imagenBase64;

    private String fecha;
    private long inicioMillis;
    private double precio;
    private int imagenResId;
    private Double latitud;
    private Double longitud;
    private Double distanciaKm;

    public Evento() {
    }

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
    public long getInicioMillis() { return inicioMillis; }
    public double getPrecio() { return precio; }
    public int getImagenResId() { return imagenResId; }
    public String getImagenUrl() { return imagenUrl; }
    public String getImagenBase64() { return imagenBase64; }
    public Double getLatitud() { return latitud; }
    public Double getLongitud() { return longitud; }
    public Double getDistanciaKm() { return distanciaKm; }

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
    public void setInicioMillis(long inicioMillis) { this.inicioMillis = inicioMillis; }
    public void setPrecio(double precio) { this.precio = precio; }
    public void setImagenResId(int imagenResId) { this.imagenResId = imagenResId; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public void setImagenBase64(String imagenBase64) { this.imagenBase64 = imagenBase64; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
    public void setDistanciaKm(Double distanciaKm) { this.distanciaKm = distanciaKm; }

    // Indica si coordenadas esta disponible.
    public boolean tieneCoordenadas() {
        return latitud != null && longitud != null;
    }

    // Devuelve ubicacion.
    public String getUbicacion() {
        return (lugarNombre != null ? lugarNombre : "")
                + (ciudad != null ? " - " + ciudad : "");
    }

    // Devuelve categoria ui.
    public String getCategoriaUI() {
        if (categoriaId == null) return "Todos";

        switch (categoriaId.toLowerCase(Locale.ROOT)) {
            case "musica":
            case "musica ":
            case "música":
                return "Musica";
            case "cultura":
                return "Cultura";
            case "gastronomia":
            case "gastronomía":
                return "Gastronomia";
            case "esport":
            case "deporte":
            case "deportes":
                return "Esport";
            case "familiar":
            case "familia":
                return "Familiar";
            default:
                return "Todos";
        }
    }
}
