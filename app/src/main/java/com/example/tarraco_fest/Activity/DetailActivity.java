package com.example.tarraco_fest.Activity;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;

public class DetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // Recuperar el evento enviado desde el adapter
        Evento evento = (Evento) getIntent().getSerializableExtra("extra_evento");

        if (evento != null) {
            vincularDatos(evento);
        }
    }

    private void vincularDatos(Evento evento) {
        TextView txtTitulo = findViewById(R.id.detailTitle);
        TextView txtFecha = findViewById(R.id.detailDate);
        TextView txtUbicacion = findViewById(R.id.detailLocation);
        TextView txtPrecio = findViewById(R.id.detailPrice);
        TextView txtDescripcion = findViewById(R.id.detailDescription);

        ImageView imgView = findViewById(R.id.detailImage);

        // Cargar imagen grande
        if (evento.getImagenUrl() != null && !evento.getImagenUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(evento.getImagenUrl())
                    .placeholder(R.drawable.card_festival)
                    .error(R.drawable.card_festival)
                    .centerCrop()
                    .into(imgView);
        } else {
            imgView.setImageResource(R.drawable.card_festival);
        }

        // Descomentar si usas imágenes reales por ID o Glide
        // ImageView imgView = findViewById(R.id.detailImage);

        txtTitulo.setText(evento.getTitulo());
        txtFecha.setText(evento.getFecha());
        txtUbicacion.setText(evento.getUbicacion());
        txtDescripcion.setText(evento.getDescripcion());

        // Lógica simple para mostrar precio o "Gratis"
        String precioFinal = evento.getPrecio() == 0.0 ? "Gratis" : evento.getPrecio() + " €";
        txtPrecio.setText(precioFinal);
    }
}
