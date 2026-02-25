package com.example.prueba_tarracofests;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.R;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final List<Evento> listaMaestra = new ArrayList<>();
    private EventoAdapter adapter;
    private String fTiempo = "Todos";
    private String fCat = "Todos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pantalla demo heredada: solo se inicializa si el layout contiene los ids esperados.
        int rvId = getResources().getIdentifier("rvEvents", "id", getPackageName());
        if (rvId == 0) {
            return;
        }

        cargarDatosMasivos();

        RecyclerView rv = findViewById(rvId);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new EventoAdapter(new ArrayList<>(listaMaestra), e -> {
            Intent i = new Intent(this, DetailActivity.class);
            i.putExtra("evento_obj", e);
            startActivity(i);
        });
        rv.setAdapter(adapter);

        bindClick("btnHoy", v -> {
            fTiempo = "Hoy";
            aplicarFiltros();
        });
        bindClick("btnSemana", v -> {
            fTiempo = "Semana";
            aplicarFiltros();
        });
        bindClick("btnTodosTiempo", v -> {
            fTiempo = "Todos";
            aplicarFiltros();
        });

        bindClick("btnCatTodos", v -> {
            fCat = "Todos";
            aplicarFiltros();
        });
        bindClick("btnTrad", v -> {
            fCat = "Tradicional";
            aplicarFiltros();
        });
        bindClick("btnCult", v -> {
            fCat = "Cultural";
            aplicarFiltros();
        });
    }

    private void aplicarFiltros() {
        if (adapter == null) {
            return;
        }
        List<Evento> filtrados = new ArrayList<>();
        for (Evento e : listaMaestra) {
            boolean cumpleT = fTiempo.equals("Todos") || e.periodo.equals(fTiempo);
            boolean cumpleC = fCat.equals("Todos") || e.categoria.equals(fCat);
            if (cumpleT && cumpleC) {
                filtrados.add(e);
            }
        }
        adapter.updateList(filtrados);
    }

    private void cargarDatosMasivos() {
        listaMaestra.add(new Evento(
                "Castells Plaza Font",
                "28 Ene",
                "Torres humanas espectaculares.",
                "Tradicional",
                "https://images.unsplash.com/photo-1522202176988-66273c2fd55f",
                "Hoy",
                "Plaza de la Font",
                "12:00"
        ));
        listaMaestra.add(new Evento(
                "Taller Ceramica",
                "28 Ene",
                "Arte romano en vivo.",
                "Cultural",
                "https://images.unsplash.com/photo-1506806732259-39c2d4a68470",
                "Hoy",
                "Museo Arqueologico",
                "17:30"
        ));
        listaMaestra.add(new Evento(
                "Jazz Metropol",
                "30 Ene",
                "Musica suave y ambiente unico.",
                "Cultural",
                "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4",
                "Semana",
                "Teatro Metropol",
                "21:00"
        ));
        listaMaestra.add(new Evento(
                "Mercado Medieval",
                "31 Ene",
                "Artesania y gastronomia.",
                "Tradicional",
                "https://images.unsplash.com/photo-1464692805480-a69dfaaf2428",
                "Semana",
                "Carrer Major",
                "10:00"
        ));
        listaMaestra.add(new Evento(
                "Tarraco Viva",
                "15 May",
                "Jornadas de historia romana.",
                "Cultural",
                "https://images.unsplash.com/photo-1543269865-cbf427effbad",
                "Todos",
                "Anfiteatro Romano",
                "11:00"
        ));
        listaMaestra.add(new Evento(
                "Correfoc Santa Tecla",
                "23 Sep",
                "Fuego, musica y tradicion.",
                "Tradicional",
                "https://images.unsplash.com/photo-1513151233558-d860c5398176",
                "Todos",
                "Balcon del Mediterraneo",
                "22:00"
        ));
    }

    private void bindClick(String idName, View.OnClickListener listener) {
        int id = getResources().getIdentifier(idName, "id", getPackageName());
        if (id != 0) {
            View view = findViewById(id);
            if (view != null) {
                view.setOnClickListener(listener);
            }
        }
    }
}
