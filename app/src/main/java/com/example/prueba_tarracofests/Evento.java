package com.example.prueba_tarracofests;

import java.io.Serializable;

/**
 * Modelo legacy de evento del prototipo inicial.
 * Serializable para pasar datos entre activities del paquete de pruebas.
 */
public class Evento implements Serializable {
    public String titulo, fecha, descripcion, categoria, imgUrl, periodo, ubicacion, hora;

    public Evento(String t, String f, String d, String c, String img, String p, String u, String h) {
        this.titulo = t; this.fecha = f; this.descripcion = d;
        this.categoria = c; this.imgUrl = img; this.periodo = p;
        this.ubicacion = u; this.hora = h;
    }
}