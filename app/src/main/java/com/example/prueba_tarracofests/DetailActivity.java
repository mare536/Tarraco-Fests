package com.example.prueba_tarracofests;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;

public class DetailActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Evento e = (Evento) getIntent().getSerializableExtra("evento_obj");

        if (e != null) {
            TextView title = findViewById(R.id.detailTitle);
            TextView info = findViewById(R.id.detailInfo);
            TextView loc = findViewById(R.id.detailLocation);
            TextView desc = findViewById(R.id.detailDesc);
            ImageView img = findViewById(R.id.detailImg);

            title.setText(e.titulo);
            info.setText("🗓 " + e.fecha + "  •  ⏰ " + e.hora);
            loc.setText("📍 " + e.ubicacion);
            desc.setText(e.descripcion);

            Glide.with(this).load(e.imgUrl).centerCrop().into(img);
        }
    }
}