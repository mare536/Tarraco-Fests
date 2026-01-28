package com.example.tarraco_fest.Modelo;

import com.google.firebase.Timestamp;

public class Evento {
    public String id;
    public String titulo;
    public Timestamp inicio;
    public String lugar;

    public Evento() {}

    public Evento(String id, String titulo, Timestamp inicio, String lugar) {
        this.id = id;
        this.titulo = titulo;
        this.inicio = inicio;
        this.lugar = lugar;
    }
}
