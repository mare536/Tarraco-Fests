package com.example.tarraco_fest.Activity;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.EventosAdapter;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.Repository.EventosRepository;
import com.example.tarraco_fest.R;

import java.util.ArrayList;
import java.util.List;

public class EventosActivity extends AppCompatActivity {

    private EventosAdapter adapter;
    private final EventosRepository repo = new EventosRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eventos);

        configurarRecyclerView();
        cargarDatos();
    }

    private void configurarRecyclerView() {
        RecyclerView rv = findViewById(R.id.recyclerViewMain);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // Inicializamos el adaptador con una lista vacía y pasamos el Context
        adapter = new EventosAdapter(new ArrayList<>(), this);
        rv.setAdapter(adapter);
    }

    private void cargarDatos() {
        repo.cargarEventos(new EventosRepository.Callback() {
            @Override
            public void onOk(List<Evento> eventos) {
                // Usamos el método correcto que creamos en el adaptador
                adapter.setEventos(eventos);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(EventosActivity.this, "Error cargando eventos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}