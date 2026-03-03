package com.example.tarraco_fest.Activity;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.EventosAdapter;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.EventosRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de listado de eventos en formato clasico.
 * Consume el repositorio y renderiza resultados en RecyclerView.
 */
public class EventosActivity extends AppCompatActivity {

    private EventosAdapter adapter;
    private final EventosRepository repo = new EventosRepository();

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eventos);

        RecyclerView rv = configurarRecyclerView();
        configurarBordesSistema(rv);
        cargarDatos();
    }

    // Configura recycler view segun el contexto actual.
    private RecyclerView configurarRecyclerView() {
        RecyclerView rv = findViewById(R.id.recyclerViewMain);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // Inicializamos el adaptador con una lista vacia y pasamos el Context.
        adapter = new EventosAdapter(new ArrayList<>(), this);
        rv.setAdapter(adapter);
        return rv;
    }

    // Aplica edge-to-edge para evitar solapes con notificaciones y barra de navegacion.
    private void configurarBordesSistema(RecyclerView rv) {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        boolean modoOscuro =
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(!modoOscuro);
        }

        View root = findViewById(R.id.rootEventos);
        View appBar = findViewById(R.id.appBarEventos);

        int baseTop = appBar.getPaddingTop();
        int baseBottom = rv.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int safeBottom = Math.max(bars.bottom, ime.bottom);

            appBar.setPadding(
                    appBar.getPaddingLeft(),
                    baseTop + bars.top,
                    appBar.getPaddingRight(),
                    appBar.getPaddingBottom()
            );

            rv.setPadding(
                    rv.getPaddingLeft(),
                    rv.getPaddingTop(),
                    rv.getPaddingRight(),
                    baseBottom + safeBottom + dpToPx(8)
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    // Carga datos desde la fuente correspondiente.
    private void cargarDatos() {
        repo.cargarEventos(new EventosRepository.Callback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk(List<Evento> eventos) {
                adapter.setEventos(eventos);
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(EventosActivity.this, "Error cargando eventos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Convierte dp a px para padding dinamico.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
