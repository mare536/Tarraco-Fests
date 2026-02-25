package com.example.tarraco_fest.Activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.EventosAdapter;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;
import com.example.tarraco_fest.Repository.EventosRepository;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private EventosAdapter adapter;
    private final EventosRepository repo = new EventosRepository();
    private final AdminAccessRepository adminAccessRepository = new AdminAccessRepository();

    // Listas para el motor de busqueda.
    private List<Evento> listaCompleta = new ArrayList<>();

    // Estado del filtro.
    private String categoriaActual = "Todos";
    private String textoBusqueda = "";

    // Vistas.
    private TextView chipTodos;
    private TextView chipMusica;
    private TextView chipCultura;
    private TextView chipGastronomia;
    private View homeContent;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        homeContent = findViewById(R.id.homeContent);
        aplicarInsetsSistema();
        configurarDrawer();
        configurarRecyclerView();
        configurarBuscadorYFiltros();
        cargarDatosDesdeFirebase();
    }

    private void aplicarInsetsSistema() {
        if (homeContent == null) return;

        final int baseLeft = homeContent.getPaddingLeft();
        final int baseTop = homeContent.getPaddingTop();
        final int baseRight = homeContent.getPaddingRight();
        final int baseBottom = homeContent.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(homeContent, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    baseLeft,
                    baseTop + systemBars.top,
                    baseRight,
                    baseBottom + systemBars.bottom
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(homeContent);
    }

    private void configurarDrawer() {
        drawerLayout = findViewById(R.id.drawerHome);
        navigationView = findViewById(R.id.navHome);

        findViewById(R.id.btnHomeMenu).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        navigationView.setCheckedItem(R.id.nav_home_events);
        ocultarItemAdmin();

        navigationView.setNavigationItemSelectedListener(item -> {
            boolean handled = manejarClickDrawer(item.getItemId());
            if (handled) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return handled;
        });

        adminAccessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            @Override
            public void onResult(boolean isAdmin) {
                MenuItem adminItem = navigationView.getMenu().findItem(R.id.nav_home_admin);
                if (adminItem != null) {
                    adminItem.setVisible(isAdmin);
                }
            }

            @Override
            public void onError(Exception e) {
                ocultarItemAdmin();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                setEnabled(false);
                HomeActivity.super.onBackPressed();
            }
        });
    }

    private void ocultarItemAdmin() {
        MenuItem adminItem = navigationView.getMenu().findItem(R.id.nav_home_admin);
        if (adminItem != null) {
            adminItem.setVisible(false);
        }
    }

    private boolean manejarClickDrawer(int itemId) {
        if (itemId == R.id.nav_home_events) {
            return true;
        }
        if (itemId == R.id.nav_home_profile) {
            startActivity(new Intent(this, PerfilActivity.class));
            return true;
        }
        if (itemId == R.id.nav_home_admin) {
            startActivity(new Intent(this, AdminPanelActivity.class));
            return true;
        }
        if (itemId == R.id.nav_home_settings) {
            Toast.makeText(this, getString(R.string.home_settings_pending), Toast.LENGTH_SHORT).show();
            return true;
        }
        if (itemId == R.id.nav_home_logout) {
            cerrarSesion();
            return true;
        }
        return false;
    }

    private void cerrarSesion() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(this, LandingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

        // Listener del buscador en tiempo real.
        etBuscador.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusqueda = s.toString().trim().toLowerCase();
                aplicarFiltros();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Listeners de chips.
        chipTodos.setOnClickListener(v -> seleccionarCategoria("Todos", chipTodos));
        chipMusica.setOnClickListener(v -> seleccionarCategoria(chipMusica.getText().toString(), chipMusica));
        chipCultura.setOnClickListener(v -> seleccionarCategoria(chipCultura.getText().toString(), chipCultura));
        chipGastronomia.setOnClickListener(v -> seleccionarCategoria(chipGastronomia.getText().toString(), chipGastronomia));
    }

    private void cargarDatosDesdeFirebase() {
        repo.cargarEventos(new EventosRepository.Callback() {
            @Override
            public void onOk(List<Evento> eventos) {
                listaCompleta = eventos;
                aplicarFiltros();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(HomeActivity.this, "Error cargando eventos", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void seleccionarCategoria(String categoria, TextView chipActivo) {
        categoriaActual = categoria;
        actualizarEstiloChips(chipActivo);
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        List<Evento> listaFiltrada = new ArrayList<>();

        for (Evento e : listaCompleta) {
            // 1) Coincidencia de texto (titulo o descripcion).
            String titulo = safeLower(e.getTitulo());
            String descripcion = safeLower(e.getDescripcion());
            boolean coincideTexto = titulo.contains(textoBusqueda) || descripcion.contains(textoBusqueda);

            // 2) Coincidencia de categoria.
            String categoriaEvento = e.getCategoriaUI() == null ? "" : e.getCategoriaUI();
            boolean coincideCategoria = categoriaActual.equals("Todos")
                    || categoriaActual.equalsIgnoreCase(categoriaEvento);

            if (coincideTexto && coincideCategoria) {
                listaFiltrada.add(e);
            }
        }

        adapter.setEventos(listaFiltrada);
    }

    private void actualizarEstiloChips(TextView chipActivo) {
        resetearChip(chipTodos);
        resetearChip(chipMusica);
        resetearChip(chipCultura);
        resetearChip(chipGastronomia);

        chipActivo.setTextColor(Color.parseColor("#FFFFFF"));
        chipActivo.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9800")));
    }

    private void resetearChip(TextView chip) {
        chip.setTextColor(Color.parseColor("#757575"));
        chip.setBackgroundTintList(null);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
