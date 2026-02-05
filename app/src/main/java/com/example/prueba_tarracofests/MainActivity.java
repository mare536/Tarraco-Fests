package com.example.prueba_tarracofests;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private List<Evento> listaMaestra = new ArrayList<>();
    private EventoAdapter adapter;
    private String fTiempo = "Todos", fCat = "Todos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cargarDatosMasivos();

        RecyclerView rv = findViewById(R.id.rvEvents);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new EventoAdapter(new ArrayList<>(listaMaestra), e -> {
            Intent i = new Intent(this, DetailActivity.class);
            i.putExtra("evento_obj", e);
            startActivity(i);
        });
        rv.setAdapter(adapter);

        // Listeners de Tiempo
        findViewById(R.id.btnHoy).setOnClickListener(v -> { fTiempo = "Hoy"; aplicarFiltros(); });
        findViewById(R.id.btnSemana).setOnClickListener(v -> { fTiempo = "Semana"; aplicarFiltros(); });
        findViewById(R.id.btnTodosTiempo).setOnClickListener(v -> { fTiempo = "Todos"; aplicarFiltros(); });

        // Listeners de Categoría
        findViewById(R.id.btnCatTodos).setOnClickListener(v -> { fCat = "Todos"; aplicarFiltros(); });
        findViewById(R.id.btnTrad).setOnClickListener(v -> { fCat = "Tradicional"; aplicarFiltros(); });
        findViewById(R.id.btnCult).setOnClickListener(v -> { fCat = "Cultural"; aplicarFiltros(); });
    }

    private void aplicarFiltros() {
        List<Evento> filtrados = new ArrayList<>();
        for (Evento e : listaMaestra) {
            boolean cumpleT = fTiempo.equals("Todos") || e.periodo.equals(fTiempo);
            boolean cumpleC = fCat.equals("Todos") || e.categoria.equals(fCat);
            if (cumpleT && cumpleC) filtrados.add(e);
        }
        adapter.updateList(filtrados);
    }

    private void cargarDatosMasivos() {
        // HOY
        listaMaestra.add(new Evento("Castells Plaza Font", "28 Ene", "Torres humanas espectaculares.", "Tradicional", "https://images.unsplash.com/photo-1522202176988-66273c2fd55f", "Hoy", "Plaza de la Font", "12:00"));
        listaMaestra.add(new Evento("Taller Cerámica", "28 Ene", "Arte romano en vivo.", "Cultural", "https://images.unsplash.com/photo-1506806732259-39c2d4a68470", "Hoy", "Museo Arqueológico", "17:30"));

        // SEMANA
        listaMaestra.add(new Evento("Jazz Metropol", "30 Ene", "Música suave y ambiente único.", "Cultural", "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4", "Semana", "Teatro Metropol", "21:00"));
        listaMaestra.add(new Evento("Mercado Medieval", "31 Ene", "Artesanía y gastronomía.", "Tradicional", "https://images.unsplash.com/photo-1464692805480-a69dfaaf2428", "Semana", "Carrer Major", "10:00"));

        // TODOS / FUTUROS
        listaMaestra.add(new Evento("Tarraco Viva", "15 May", "Jornadas de historia romana.", "Cultural", "https://images.unsplash.com/photo-1543269865-cbf427effbad", "Todos", "Anfiteatro Romano", "11:00"));
        listaMaestra.add(new Evento("Correfoc Santa Tecla", "23 Sep", "Fuego, música y tradición.", "Tradicional", "https://images.unsplash.com/photo-1513151233558-d860c5398176", "Todos", "Balcón del Mediterráneo", "22:00"));
    }
}