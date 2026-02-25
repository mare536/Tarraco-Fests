package com.example.tarraco_fest.Activity;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.EventosAdapter;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.EventosRepository;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private EventosAdapter adapter;
    private final EventosRepository repo = new EventosRepository();

    // Listas para el motor de búsqueda
    private List<Evento> listaCompleta = new ArrayList<>();

    // Estado del filtro
    private String categoriaActual = "Todos";
    private String textoBusqueda = "";

    // Vistas
    private TextView chipTodos, chipMusica, chipCultura, chipGastronomia;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        configurarRecyclerView();
        configurarBuscadorYFiltros();
        cargarDatosDesdeFirebase();
    }

    private void configurarRecyclerView() {
        RecyclerView rv = findViewById(R.id.recyclerViewMain);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventosAdapter(new ArrayList<>(), this);
        rv.setAdapter(adapter);
    }

    private void configurarBuscadorYFiltros() {
        EditText etBuscador = findViewById(R.id.etBuscador);
        chipTodos = findViewById(R.id.chipTodos);
        chipMusica = findViewById(R.id.chipMusica);
        chipCultura = findViewById(R.id.chipCultura);
        chipGastronomia = findViewById(R.id.chipGastronomia);

        // Listener del Buscador en tiempo real
        etBuscador.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusqueda = s.toString().trim().toLowerCase();
                aplicarFiltros();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Listeners de los Chips
        chipTodos.setOnClickListener(v -> seleccionarCategoria("Todos", chipTodos));
        chipMusica.setOnClickListener(v -> seleccionarCategoria("Música", chipMusica));
        chipCultura.setOnClickListener(v -> seleccionarCategoria("Cultura", chipCultura));
        chipGastronomia.setOnClickListener(v -> seleccionarCategoria("Gastronomía", chipGastronomia));
    }

    private void cargarDatosDesdeFirebase() {
        repo.cargarEventos(new EventosRepository.Callback() {
            @Override
            public void onOk(List<Evento> eventos) {
                listaCompleta = eventos; // Guardamos el listado original intocable
                aplicarFiltros(); // Mandamos los datos iniciales al recycler
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(HomeActivity.this, "Error cargando eventos", Toast.LENGTH_LONG).show();
            }
        });
    }

    // --- LÓGICA DE FILTRADO ---

    private void seleccionarCategoria(String categoria, TextView chipActivo) {
        categoriaActual = categoria;
        actualizarEstiloChips(chipActivo);
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        List<Evento> listaFiltrada = new ArrayList<>();

        for (Evento e : listaCompleta) {
            // 1. Coincidencia de texto (título o descripción)
            boolean coincideTexto = e.getTitulo().toLowerCase().contains(textoBusqueda) ||
                    e.getDescripcion().toLowerCase().contains(textoBusqueda);

            // 2. Coincidencia de categoría
            boolean coincideCategoria = categoriaActual.equals("Todos") ||
                    categoriaActual.equalsIgnoreCase(e.getCategoriaUI());

            if (coincideTexto && coincideCategoria) {
                listaFiltrada.add(e);
            }
        }

        adapter.setEventos(listaFiltrada); // El Adapter repinta los cambios
    }

    // --- INTERFAZ DE USUARIO ---

    private void actualizarEstiloChips(TextView chipActivo) {
        // Resetear todos
        resetearChip(chipTodos);
        resetearChip(chipMusica);
        resetearChip(chipCultura);
        resetearChip(chipGastronomia);

        // Activar el seleccionado (Naranja)
        chipActivo.setTextColor(Color.parseColor("#FFFFFF"));
        chipActivo.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9800")));
    }

    private void resetearChip(TextView chip) {
        // Estilo inactivo (Gris)
        chip.setTextColor(Color.parseColor("#757575"));
        chip.setBackgroundTintList(null); // Quita el tinte para mostrar el fondo original transparente
    }
}