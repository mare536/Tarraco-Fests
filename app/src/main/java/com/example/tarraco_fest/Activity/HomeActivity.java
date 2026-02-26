package com.example.tarraco_fest.Activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.EventosAdapter;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;
import com.example.tarraco_fest.Repository.EventosRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private static final String PREF_PERMISOS = "permisos_app";
    private static final String KEY_NOTIF_SOLICITADO = "notif_solicitado";
    private static final String KEY_UBI_SOLICITADO = "ubi_solicitado";

    private EventosAdapter adapter;
    private final EventosRepository repo = new EventosRepository();
    private final AdminAccessRepository adminAccessRepository = new AdminAccessRepository();

    private List<Evento> listaCompleta = new ArrayList<>();
    private String categoriaActual = "Todos";
    private String textoBusqueda = "";

    private TextView chipTodos;
    private TextView chipMusica;
    private TextView chipCultura;
    private TextView chipEsport;
    private TextView chipFamiliar;
    private TextView chipGastronomia;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private androidx.appcompat.widget.AppCompatImageButton btnHomeMenu;
    private SharedPreferences permisosPrefs;
    private boolean loadedAtLeastOnce = false;

    private final ActivityResultLauncher<String> notificacionesPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    solicitarPermisosIniciales());

    private final ActivityResultLauncher<String[]> ubicacionPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        permisosPrefs = getSharedPreferences(PREF_PERMISOS, MODE_PRIVATE);

        configurarStatusBar();
        configurarDrawer();
        configurarRecyclerView();
        configurarBuscadorYFiltros();
        cargarDatosDesdeFirebase();
        solicitarPermisosIniciales();
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarSeleccionDrawerActual();
        if (loadedAtLeastOnce) {
            cargarDatosDesdeFirebase();
        }
    }

    private void configurarStatusBar() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.home_status_bar_fill));
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }
    }

    private void configurarDrawer() {
        drawerLayout = findViewById(R.id.drawerHome);
        navigationView = findViewById(R.id.navHome);
        btnHomeMenu = findViewById(R.id.btnHomeMenu);

        btnHomeMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        aplicarInsetSuperiorMenu();
        actualizarSeleccionDrawerActual();
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

    private void aplicarInsetSuperiorMenu() {
        if (btnHomeMenu == null) return;

        final android.view.ViewGroup.MarginLayoutParams lp =
                (android.view.ViewGroup.MarginLayoutParams) btnHomeMenu.getLayoutParams();
        final int baseTop = lp.topMargin;

        ViewCompat.setOnApplyWindowInsetsListener(btnHomeMenu, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            android.view.ViewGroup.MarginLayoutParams params =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.topMargin = baseTop + bars.top;
            v.setLayoutParams(params);
            return insets;
        });

        ViewCompat.requestApplyInsets(btnHomeMenu);
    }

    private void ocultarItemAdmin() {
        MenuItem adminItem = navigationView.getMenu().findItem(R.id.nav_home_admin);
        if (adminItem != null) {
            adminItem.setVisible(false);
        }
    }

    private boolean manejarClickDrawer(int itemId) {
        if (itemId == R.id.nav_home_events) {
            actualizarSeleccionDrawerActual();
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

    private void actualizarSeleccionDrawerActual() {
        if (navigationView == null) return;
        navigationView.setCheckedItem(R.id.nav_home_events);
    }

    private void solicitarPermisosIniciales() {
        if (debeSolicitarNotificaciones()) {
            mostrarDialogoPermisoNotificaciones();
            return;
        }
        if (debeSolicitarUbicacion()) {
            mostrarDialogoPermisoUbicacion();
        }
    }

    private boolean debeSolicitarNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return false;
        return !permisosPrefs.getBoolean(KEY_NOTIF_SOLICITADO, false);
    }

    private boolean debeSolicitarUbicacion() {
        boolean fineOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fineOk || coarseOk) return false;
        return !permisosPrefs.getBoolean(KEY_UBI_SOLICITADO, false);
    }

    private void mostrarDialogoPermisoNotificaciones() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Permiso de notificaciones")
                .setMessage("Activa notificaciones para avisos de eventos y recordatorios.")
                .setPositiveButton("Permitir", (d, w) -> {
                    permisosPrefs.edit().putBoolean(KEY_NOTIF_SOLICITADO, true).apply();
                    notificacionesPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                })
                .setNegativeButton("Ahora no", (d, w) -> {
                    permisosPrefs.edit().putBoolean(KEY_NOTIF_SOLICITADO, true).apply();
                    solicitarPermisosIniciales();
                })
                .show();
    }

    private void mostrarDialogoPermisoUbicacion() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Permiso de ubicacion")
                .setMessage("Activa ubicacion para funciones de eventos cercanos y mejoras de recomendacion.")
                .setPositiveButton("Permitir", (d, w) -> {
                    permisosPrefs.edit().putBoolean(KEY_UBI_SOLICITADO, true).apply();
                    ubicacionPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                })
                .setNegativeButton("Ahora no", (d, w) -> {
                    permisosPrefs.edit().putBoolean(KEY_UBI_SOLICITADO, true).apply();
                })
                .show();
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
        chipEsport = findViewById(R.id.chipEsport);
        chipFamiliar = findViewById(R.id.chipFamiliar);
        chipGastronomia = findViewById(R.id.chipGastronomia);

        etBuscador.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusqueda = s.toString().trim().toLowerCase(Locale.ROOT);
                aplicarFiltros();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        chipTodos.setOnClickListener(v -> seleccionarCategoria("Todos", chipTodos));
        chipMusica.setOnClickListener(v -> seleccionarCategoria("Musica", chipMusica));
        chipCultura.setOnClickListener(v -> seleccionarCategoria("Cultura", chipCultura));
        chipEsport.setOnClickListener(v -> seleccionarCategoria("Esport", chipEsport));
        chipFamiliar.setOnClickListener(v -> seleccionarCategoria("Familiar", chipFamiliar));
        if (chipGastronomia != null) {
            chipGastronomia.setOnClickListener(v -> seleccionarCategoria("Gastronomia", chipGastronomia));
        }
    }

    private void cargarDatosDesdeFirebase() {
        repo.cargarEventos(new EventosRepository.Callback() {
            @Override
            public void onOk(List<Evento> eventos) {
                loadedAtLeastOnce = true;
                listaCompleta = eventos;
                aplicarFiltros();
            }

            @Override
            public void onError(Exception e) {
                loadedAtLeastOnce = true;
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
            String titulo = safeLower(e.getTitulo());
            String descripcion = safeLower(e.getDescripcion());
            String ubicacion = safeLower(e.getUbicacion());
            String fecha = safeLower(e.getFecha());

            boolean coincideTexto = titulo.contains(textoBusqueda)
                    || descripcion.contains(textoBusqueda)
                    || ubicacion.contains(textoBusqueda)
                    || fecha.contains(textoBusqueda);

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
        resetearChip(chipEsport);
        resetearChip(chipFamiliar);
        resetearChip(chipGastronomia);

        if (chipActivo != null) {
            chipActivo.setTextColor(Color.parseColor("#FFFFFF"));
            chipActivo.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9800")));
        }
    }

    private void resetearChip(TextView chip) {
        if (chip == null) return;
        chip.setTextColor(Color.parseColor("#757575"));
        chip.setBackgroundTintList(null);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
