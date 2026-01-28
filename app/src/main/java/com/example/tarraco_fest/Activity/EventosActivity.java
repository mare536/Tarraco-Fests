package com.example.tarraco_fest.Activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.EventosAdapter;
import com.example.tarraco_fest.Repository.EventosRepository;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.ReminderRepository;

public class EventosActivity extends AppCompatActivity {

    private EventosAdapter adapter;
    private final EventosRepository repo = new EventosRepository();
    private final ReminderRepository reminders = new ReminderRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eventos);

        RecyclerView rv = findViewById(R.id.rvEventos);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new EventosAdapter(this::onEventoClick);
        rv.setAdapter(adapter);

        cargar();
    }

    private void cargar() {
        repo.cargarEventos(new EventosRepository.Callback() {
            @Override public void onOk(java.util.List<Evento> eventos) {
                adapter.setData(eventos);
            }
            @Override public void onError(Exception e) {
                Toast.makeText(EventosActivity.this, "Error cargando eventos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onEventoClick(Evento e) {
        new AlertDialog.Builder(this)
                .setTitle("Recordatorio")
                .setMessage("¿Quieres que te notifique sobre:\n" + (e.titulo != null ? e.titulo : "este evento") + "?")
                .setNegativeButton("No", (d,w) -> d.dismiss())
                .setPositiveButton("Sí", (d,w) -> {
                    reminders.guardarRecordatorio(e.id, new ReminderRepository.Callback() {
                        @Override public void onOk() {
                            Toast.makeText(EventosActivity.this, "Recordatorio guardado", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(Exception ex) {
                            Toast.makeText(EventosActivity.this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }
}
