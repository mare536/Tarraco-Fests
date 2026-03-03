package com.example.tarraco_fest.Activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pantalla principal tras login.
 * Gestiona filtros, navegacion lateral y acceso al resto de modulos.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String PREF_PERMISOS = "permisos_app";
    private static final String PREF_HOME_FILTERS = "home_filters";
    private static final String KEY_NOTIF_SOLICITADO = "notif_solicitado";
    private static final String KEY_UBI_SOLICITADO = "ubi_solicitado";
    private static final String KEY_FILTER_CATEGORIA = "filter_categoria";
    private static final String KEY_FILTER_FECHA = "filter_fecha";
    private static final String KEY_FILTER_PRECIO = "filter_precio";
    private static final String KEY_FILTER_HORARIO = "filter_horario";
    private static final String KEY_FILTER_DISTANCIA = "filter_distancia";
    private static final String KEY_FILTER_ZONA = "filter_zona";
    private static final String FILTRO_FECHA_TODAS = "fecha_todas";
    private static final String FILTRO_FECHA_HOY = "fecha_hoy";
    private static final String FILTRO_FECHA_SEMANA = "fecha_semana";
    private static final String FILTRO_FECHA_FIN_SEMANA = "fecha_fin_semana";
    private static final String FILTRO_PRECIO_TODOS = "precio_todos";
    private static final String FILTRO_PRECIO_GRATIS = "precio_gratis";
    private static final String FILTRO_PRECIO_PAGO = "precio_pago";
    private static final String FILTRO_HORARIO_TODOS = "horario_todos";
    private static final String FILTRO_HORARIO_MANANA = "horario_manana";
    private static final String FILTRO_HORARIO_TARDE = "horario_tarde";
    private static final String FILTRO_HORARIO_NOCHE = "horario_noche";
    private static final String FILTRO_DISTANCIA_TODAS = "distancia_todas";
    private static final String FILTRO_DISTANCIA_CERCA = "distancia_cerca";
    private static final double DISTANCIA_CERCA_KM = 5.0d;
    private static final long AUTO_REFRESH_MIN_INTERVAL_MS = 180_000L;
    private static final long SYNC_HINT_HIDE_DELAY_MS = 1_500L;

    private EventosAdapter adapter;
    private final EventosRepository repo = new EventosRepository();
    private final AdminAccessRepository adminAccessRepository = new AdminAccessRepository();

    private List<Evento> listaCompleta = new ArrayList<>();
    private String categoriaActual = "Todos";
    private String textoBusqueda = "";
    private String filtroFechaActual = FILTRO_FECHA_TODAS;
    private String filtroPrecioActual = FILTRO_PRECIO_TODOS;
    private String filtroHorarioActual = FILTRO_HORARIO_TODOS;
    private String filtroDistanciaActual = FILTRO_DISTANCIA_TODAS;
    private String filtroZonaTexto = "";
    private String filtroZonaNormalizada = "";
    private double userLat = 0d;
    private double userLng = 0d;
    private boolean hasUserLocation = false;

    private TextView chipTodos;
    private TextView chipMusica;
    private TextView chipCultura;
    private TextView chipEsport;
    private TextView chipFamiliar;
    private TextView chipGastronomia;
    private View cardFiltersSummary;
    private TextView tvFilterSummary;
    private TextView tvOpenFilters;
    private TextView tvClearFilters;
    private TextView tvDataSyncHint;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private androidx.appcompat.widget.AppCompatImageButton btnHomeMenu;
    private SharedPreferences permisosPrefs;
    private SharedPreferences filtrosPrefs;
    private boolean loadedAtLeastOnce = false;
    private long lastDataRefreshAt = 0L;
    private String lastDataSignature = "";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideSyncHintRunnable = () -> {
        if (tvDataSyncHint == null) return;
        tvDataSyncHint.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(() -> {
                    if (tvDataSyncHint != null) {
                        tvDataSyncHint.setVisibility(View.GONE);
                    }
                })
                .start();
    };

    private final ActivityResultLauncher<String> notificacionesPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    solicitarPermisosIniciales());

    private final ActivityResultLauncher<String[]> ubicacionPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                refrescarUbicacionUsuario();
                aplicarFiltros();
                actualizarResumenFiltros();
            });

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        permisosPrefs = getSharedPreferences(PREF_PERMISOS, MODE_PRIVATE);
        filtrosPrefs = getSharedPreferences(PREF_HOME_FILTERS, MODE_PRIVATE);

        configurarStatusBar();
        configurarDrawer();
        cargarFiltrosPersistidos();
        configurarRecyclerView();
        configurarBuscadorYFiltros();
        cargarDatosDesdeFirebase(false);
        solicitarPermisosIniciales();
        refrescarUbicacionUsuario();
    }

    // Gestiona on resume en este bloque.
    @Override
    protected void onResume() {
        super.onResume();
        refrescarUbicacionUsuario();
        actualizarSeleccionDrawerActual();
        if (loadedAtLeastOnce && debeActualizarEnResume()) {
            cargarDatosDesdeFirebase(true);
        }
    }

    // Gestiona on destroy en este bloque.
    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(hideSyncHintRunnable);
        super.onDestroy();
    }

    // Configura status bar segun el contexto actual.
    private void configurarStatusBar() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.home_status_bar_fill));
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }
    }

    // Configura drawer segun el contexto actual.
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
            // Gestiona on result en este bloque.
            @Override
            public void onResult(boolean isAdmin) {
                MenuItem adminItem = navigationView.getMenu().findItem(R.id.nav_home_admin);
                if (adminItem != null) {
                    adminItem.setVisible(isAdmin);
                }
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                ocultarItemAdmin();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            // Gestiona handle on back pressed en este bloque.
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

    // Aplica inset superior menu respetando el estado actual.
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

    // Gestiona ocultar item admin en este bloque.
    private void ocultarItemAdmin() {
        MenuItem adminItem = navigationView.getMenu().findItem(R.id.nav_home_admin);
        if (adminItem != null) {
            adminItem.setVisible(false);
        }
    }

    // Gestiona manejar click drawer en este bloque.
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
            startActivity(new Intent(this, AjustesActivity.class));
            return true;
        }
        if (itemId == R.id.nav_home_logout) {
            cerrarSesion();
            return true;
        }
        return false;
    }

    // Actualiza seleccion drawer actual con la logica de negocio actual.
    private void actualizarSeleccionDrawerActual() {
        if (navigationView == null) return;
        navigationView.setCheckedItem(R.id.nav_home_events);
    }

    // Gestiona solicitar permisos iniciales en este bloque.
    private void solicitarPermisosIniciales() {
        if (debeSolicitarNotificaciones()) {
            mostrarDialogoPermisoNotificaciones();
            return;
        }
        if (debeSolicitarUbicacion()) {
            mostrarDialogoPermisoUbicacion();
        }
    }

    // Gestiona debe solicitar notificaciones en este bloque.
    private boolean debeSolicitarNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return false;
        return !permisosPrefs.getBoolean(KEY_NOTIF_SOLICITADO, false);
    }

    // Gestiona debe solicitar ubicacion en este bloque.
    private boolean debeSolicitarUbicacion() {
        boolean fineOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fineOk || coarseOk) return false;
        return !permisosPrefs.getBoolean(KEY_UBI_SOLICITADO, false);
    }

    // Indica si permiso ubicacion esta disponible.
    private boolean tienePermisoUbicacion() {
        boolean fineOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return fineOk || coarseOk;
    }

    // Gestiona refrescar ubicacion usuario en este bloque.
    private boolean refrescarUbicacionUsuario() {
        if (!tienePermisoUbicacion()) {
            hasUserLocation = false;
            return false;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            hasUserLocation = false;
            return false;
        }

        Location mejor = null;
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location passive = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

            if (gps != null) mejor = gps;
            if (net != null && (mejor == null || net.getTime() > mejor.getTime())) mejor = net;
            if (passive != null && (mejor == null || passive.getTime() > mejor.getTime())) mejor = passive;
        } catch (SecurityException ignored) {
            hasUserLocation = false;
            return false;
        }

        if (mejor == null) {
            hasUserLocation = false;
            return false;
        }

        userLat = mejor.getLatitude();
        userLng = mejor.getLongitude();
        hasUserLocation = true;
        return true;
    }

    // Muestra dialogo permiso notificaciones en la interfaz.
    private void mostrarDialogoPermisoNotificaciones() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle(R.string.permissions_notif_title)
                .setMessage(R.string.permissions_notif_message)
                .setPositiveButton(R.string.permissions_allow, (d, w) -> {
                    permisosPrefs.edit().putBoolean(KEY_NOTIF_SOLICITADO, true).apply();
                    notificacionesPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                })
                .setNegativeButton(R.string.permissions_not_now, (d, w) -> {
                    permisosPrefs.edit().putBoolean(KEY_NOTIF_SOLICITADO, true).apply();
                    solicitarPermisosIniciales();
                })
                .show();
    }

    // Muestra dialogo permiso ubicacion en la interfaz.
    private void mostrarDialogoPermisoUbicacion() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle(R.string.permissions_location_title)
                .setMessage(R.string.permissions_location_message)
                .setPositiveButton(R.string.permissions_allow, (d, w) -> {
                    permisosPrefs.edit().putBoolean(KEY_UBI_SOLICITADO, true).apply();
                    ubicacionPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                })
                .setNegativeButton(R.string.permissions_not_now, (d, w) -> {
                    permisosPrefs.edit().putBoolean(KEY_UBI_SOLICITADO, true).apply();
                })
                .show();
    }

    // Gestiona cerrar sesion en este bloque.
    private void cerrarSesion() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(this, LandingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Configura recycler view segun el contexto actual.
    private void configurarRecyclerView() {
        RecyclerView rv = findViewById(R.id.recyclerViewMain);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventosAdapter(new ArrayList<>(), this);
        rv.setAdapter(adapter);
        aplicarInsetInferiorLista(rv);
    }

    // Aplica un espacio seguro inferior para que el ultimo item no quede bajo la barra del sistema.
    private void aplicarInsetInferiorLista(RecyclerView rv) {
        if (rv == null) return;

        final int baseStart = ViewCompat.getPaddingStart(rv);
        final int baseTop = rv.getPaddingTop();
        final int baseEnd = ViewCompat.getPaddingEnd(rv);
        final int baseBottom = rv.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rv, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int extraBottom = dpToPx(16);
            ViewCompat.setPaddingRelative(v, baseStart, baseTop, baseEnd, baseBottom + bars.bottom + extraBottom);
            return insets;
        });

        ViewCompat.requestApplyInsets(rv);
    }

    // Configura buscador yfiltros segun el contexto actual.
    private void configurarBuscadorYFiltros() {
        EditText etBuscador = findViewById(R.id.etBuscador);
        chipTodos = findViewById(R.id.chipTodos);
        chipMusica = findViewById(R.id.chipMusica);
        chipCultura = findViewById(R.id.chipCultura);
        chipEsport = findViewById(R.id.chipEsport);
        chipFamiliar = findViewById(R.id.chipFamiliar);
        chipGastronomia = findViewById(R.id.chipGastronomia);
        cardFiltersSummary = findViewById(R.id.cardFiltersSummary);
        tvFilterSummary = findViewById(R.id.tvFilterSummary);
        tvOpenFilters = findViewById(R.id.tvOpenFilters);
        tvClearFilters = findViewById(R.id.tvClearFilters);
        tvDataSyncHint = findViewById(R.id.tvDataSyncHint);

        etBuscador.addTextChangedListener(new TextWatcher() {
            // Gestiona before text changed en este bloque.
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            // Gestiona on text changed en este bloque.
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoBusqueda = s.toString().trim().toLowerCase(Locale.ROOT);
                aplicarFiltros();
            }

            // Gestiona after text changed en este bloque.
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

        if (cardFiltersSummary != null) {
            cardFiltersSummary.setOnClickListener(v -> abrirDialogoFiltros());
        }
        if (tvOpenFilters != null) {
            tvOpenFilters.setOnClickListener(v -> abrirDialogoFiltros());
        }
        tvClearFilters.setOnClickListener(v -> limpiarFiltros());

        actualizarEstiloChips(obtenerChipCategoriaActual());
        actualizarResumenFiltros();
    }

    // Carga datos desde firebase desde la fuente correspondiente.
    private void cargarDatosDesdeFirebase(boolean showSyncFeedback) {
        final boolean hadDataBeforeRequest = loadedAtLeastOnce;

        if (showSyncFeedback && hadDataBeforeRequest) {
            mostrarEstadoSincronizacion(R.string.home_sync_updating, false);
        }

        repo.cargarEventos(new EventosRepository.Callback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk(List<Evento> eventos) {
                loadedAtLeastOnce = true;
                lastDataRefreshAt = System.currentTimeMillis();

                List<Evento> safeEventos = eventos == null ? Collections.emptyList() : eventos;
                String nuevaFirma = construirFirmaEventos(safeEventos);
                boolean hayCambios = !Objects.equals(nuevaFirma, lastDataSignature);

                if (hayCambios || listaCompleta.isEmpty()) {
                    listaCompleta = new ArrayList<>(safeEventos);
                    lastDataSignature = nuevaFirma;
                    aplicarFiltros();
                }

                if (showSyncFeedback && hadDataBeforeRequest) {
                    mostrarEstadoSincronizacion(
                            hayCambios ? R.string.home_sync_updated : R.string.home_sync_no_changes,
                            true
                    );
                }
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                loadedAtLeastOnce = true;
                if (showSyncFeedback && hadDataBeforeRequest) {
                    mostrarEstadoSincronizacion(R.string.home_sync_error, true);
                }
                Toast.makeText(HomeActivity.this, "Error cargando eventos", Toast.LENGTH_LONG).show();
            }
        });
    }

    // Indica si corresponde refrescar datos al volver a Home.
    private boolean debeActualizarEnResume() {
        long elapsed = System.currentTimeMillis() - lastDataRefreshAt;
        return elapsed >= AUTO_REFRESH_MIN_INTERVAL_MS;
    }

    // Muestra un hint temporal para hacer explicita la sincronizacion de datos.
    private void mostrarEstadoSincronizacion(@StringRes int textRes, boolean autoHide) {
        if (tvDataSyncHint == null) return;

        uiHandler.removeCallbacks(hideSyncHintRunnable);
        tvDataSyncHint.setText(textRes);
        tvDataSyncHint.setVisibility(View.VISIBLE);
        tvDataSyncHint.animate().cancel();
        tvDataSyncHint.setAlpha(1f);

        if (autoHide) {
            uiHandler.postDelayed(hideSyncHintRunnable, SYNC_HINT_HIDE_DELAY_MS);
        }
    }

    // Construye una firma compacta para evitar repintados cuando la lista no cambia.
    private String construirFirmaEventos(List<Evento> eventos) {
        if (eventos == null || eventos.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(eventos.size() * 24);
        for (Evento e : eventos) {
            if (e == null) continue;
            String imgUrl = e.getImagenUrl();
            String imgB64 = e.getImagenBase64();
            sb.append(safeLower(e.getId())).append('|')
                    .append(safeLower(e.getTitulo())).append('|')
                    .append(e.getInicioMillis()).append('|')
                    .append(safeLower(e.getFecha())).append('|')
                    .append(imgUrl == null ? "" : imgUrl).append('|')
                    .append(imgB64 == null ? 0 : imgB64.hashCode()).append(';');
        }
        return sb.toString();
    }

    // Gestiona seleccionar categoria en este bloque.
    private void seleccionarCategoria(String categoria, TextView chipActivo) {
        categoriaActual = categoria;
        actualizarEstiloChips(chipActivo);
        guardarFiltrosPersistidos();
        aplicarFiltros();
    }

    // Gestiona limpiar filtros en este bloque.
    private void limpiarFiltros() {
        categoriaActual = "Todos";
        filtroFechaActual = FILTRO_FECHA_TODAS;
        filtroPrecioActual = FILTRO_PRECIO_TODOS;
        filtroHorarioActual = FILTRO_HORARIO_TODOS;
        filtroDistanciaActual = FILTRO_DISTANCIA_TODAS;
        filtroZonaTexto = "";
        filtroZonaNormalizada = "";

        actualizarEstiloChips(chipTodos);
        actualizarResumenFiltros();
        guardarFiltrosPersistidos();
        aplicarFiltros();
    }

    // Aplica filtros respetando el estado actual.
    private void aplicarFiltros() {
        if (FILTRO_DISTANCIA_CERCA.equals(filtroDistanciaActual) && !hasUserLocation) {
            filtroDistanciaActual = FILTRO_DISTANCIA_TODAS;
            guardarFiltrosPersistidos();
            actualizarResumenFiltros();
        }

        List<Evento> listaFiltrada = new ArrayList<>();

        for (Evento e : listaCompleta) {
            Double distanciaKm = calcularDistanciaEventoKm(e);
            e.setDistanciaKm(distanciaKm);

            String titulo = safeLower(e.getTitulo());
            String descripcion = safeLower(e.getDescripcion());
            String ubicacion = safeLower(e.getUbicacion());
            String fecha = safeLower(e.getFecha());
            String direccion = safeLower(e.getDireccion());
            String lugar = safeLower(e.getLugarNombre());

            boolean coincideTexto = titulo.contains(textoBusqueda)
                    || descripcion.contains(textoBusqueda)
                    || ubicacion.contains(textoBusqueda)
                    || fecha.contains(textoBusqueda)
                    || direccion.contains(textoBusqueda)
                    || lugar.contains(textoBusqueda);

            String categoriaEvento = e.getCategoriaUI() == null ? "" : e.getCategoriaUI();
            boolean coincideCategoria = categoriaActual.equals("Todos")
                    || categoriaActual.equalsIgnoreCase(categoriaEvento);
            boolean coincideFecha = coincideFiltroFecha(e);
            boolean coincidePrecio = coincideFiltroPrecio(e.getPrecio());
            boolean coincideHorario = coincideFiltroHorario(e);
            boolean coincideDistancia = coincideFiltroDistancia(distanciaKm);
            boolean coincideZona = filtroZonaNormalizada.isEmpty()
                    || normalizarTexto(e.getDireccion()).contains(filtroZonaNormalizada)
                    || normalizarTexto(e.getLugarNombre()).contains(filtroZonaNormalizada);

            if (coincideTexto && coincideCategoria && coincideFecha && coincidePrecio
                    && coincideHorario && coincideDistancia && coincideZona) {
                listaFiltrada.add(e);
            }
        }

        if (FILTRO_DISTANCIA_CERCA.equals(filtroDistanciaActual)) {
            listaFiltrada.sort(Comparator.comparingDouble(ev -> {
                Double d = ev.getDistanciaKm();
                return d == null ? Double.MAX_VALUE : d;
            }));
        }

        adapter.setEventos(listaFiltrada);
    }

    // Gestiona abrir dialogo filtros en este bloque.
    private void abrirDialogoFiltros() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_home_filters, null);

        RadioGroup rgFecha = dialogView.findViewById(R.id.rgFiltroFecha);
        RadioGroup rgPrecio = dialogView.findViewById(R.id.rgFiltroPrecio);
        RadioGroup rgHorario = dialogView.findViewById(R.id.rgFiltroHorario);
        RadioGroup rgDistancia = dialogView.findViewById(R.id.rgFiltroDistancia);
        EditText etZona = dialogView.findViewById(R.id.etDialogFiltroZona);

        if (FILTRO_FECHA_HOY.equals(filtroFechaActual)) {
            rgFecha.check(R.id.rbFiltroFechaHoy);
        } else if (FILTRO_FECHA_SEMANA.equals(filtroFechaActual)) {
            rgFecha.check(R.id.rbFiltroFechaSemana);
        } else if (FILTRO_FECHA_FIN_SEMANA.equals(filtroFechaActual)) {
            rgFecha.check(R.id.rbFiltroFechaWeekend);
        } else {
            rgFecha.check(R.id.rbFiltroFechaTodas);
        }

        if (FILTRO_PRECIO_GRATIS.equals(filtroPrecioActual)) {
            rgPrecio.check(R.id.rbFiltroPrecioGratis);
        } else if (FILTRO_PRECIO_PAGO.equals(filtroPrecioActual)) {
            rgPrecio.check(R.id.rbFiltroPrecioPago);
        } else {
            rgPrecio.check(R.id.rbFiltroPrecioTodos);
        }

        if (FILTRO_HORARIO_MANANA.equals(filtroHorarioActual)) {
            rgHorario.check(R.id.rbFiltroHorarioManana);
        } else if (FILTRO_HORARIO_TARDE.equals(filtroHorarioActual)) {
            rgHorario.check(R.id.rbFiltroHorarioTarde);
        } else if (FILTRO_HORARIO_NOCHE.equals(filtroHorarioActual)) {
            rgHorario.check(R.id.rbFiltroHorarioNoche);
        } else {
            rgHorario.check(R.id.rbFiltroHorarioTodos);
        }

        if (FILTRO_DISTANCIA_CERCA.equals(filtroDistanciaActual)) {
            rgDistancia.check(R.id.rbFiltroDistanciaCerca);
        } else {
            rgDistancia.check(R.id.rbFiltroDistanciaTodas);
        }

        etZona.setText(filtroZonaTexto);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle(R.string.home_filter_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.detail_cancel, null)
                .setNeutralButton(R.string.home_filter_clear, (dlg, which) -> limpiarFiltros())
                .setPositiveButton(R.string.home_filter_apply, (dlg, which) -> {
                    int fechaId = rgFecha.getCheckedRadioButtonId();
                    if (fechaId == R.id.rbFiltroFechaHoy) {
                        filtroFechaActual = FILTRO_FECHA_HOY;
                    } else if (fechaId == R.id.rbFiltroFechaSemana) {
                        filtroFechaActual = FILTRO_FECHA_SEMANA;
                    } else if (fechaId == R.id.rbFiltroFechaWeekend) {
                        filtroFechaActual = FILTRO_FECHA_FIN_SEMANA;
                    } else {
                        filtroFechaActual = FILTRO_FECHA_TODAS;
                    }

                    int precioId = rgPrecio.getCheckedRadioButtonId();
                    if (precioId == R.id.rbFiltroPrecioGratis) {
                        filtroPrecioActual = FILTRO_PRECIO_GRATIS;
                    } else if (precioId == R.id.rbFiltroPrecioPago) {
                        filtroPrecioActual = FILTRO_PRECIO_PAGO;
                    } else {
                        filtroPrecioActual = FILTRO_PRECIO_TODOS;
                    }

                    int horarioId = rgHorario.getCheckedRadioButtonId();
                    if (horarioId == R.id.rbFiltroHorarioManana) {
                        filtroHorarioActual = FILTRO_HORARIO_MANANA;
                    } else if (horarioId == R.id.rbFiltroHorarioTarde) {
                        filtroHorarioActual = FILTRO_HORARIO_TARDE;
                    } else if (horarioId == R.id.rbFiltroHorarioNoche) {
                        filtroHorarioActual = FILTRO_HORARIO_NOCHE;
                    } else {
                        filtroHorarioActual = FILTRO_HORARIO_TODOS;
                    }

                    int distanciaId = rgDistancia.getCheckedRadioButtonId();
                    if (distanciaId == R.id.rbFiltroDistanciaCerca) {
                        if (!tienePermisoUbicacion()) {
                            filtroDistanciaActual = FILTRO_DISTANCIA_TODAS;
                            Toast.makeText(
                                    HomeActivity.this,
                                    getString(R.string.home_filter_location_unavailable),
                                    Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            boolean ok = refrescarUbicacionUsuario();
                            if (!ok) {
                                filtroDistanciaActual = FILTRO_DISTANCIA_TODAS;
                                Toast.makeText(
                                        HomeActivity.this,
                                        getString(R.string.home_filter_location_no_fix),
                                        Toast.LENGTH_SHORT
                                ).show();
                            } else {
                                filtroDistanciaActual = FILTRO_DISTANCIA_CERCA;
                            }
                        }
                    } else {
                        filtroDistanciaActual = FILTRO_DISTANCIA_TODAS;
                    }

                    filtroZonaTexto = etZona.getText() == null ? "" : etZona.getText().toString().trim();
                    filtroZonaNormalizada = normalizarTexto(filtroZonaTexto);

                    actualizarResumenFiltros();
                    guardarFiltrosPersistidos();
                    aplicarFiltros();
                })
                .create();

        dialog.show();

        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutral != null) {
            neutral.setAllCaps(false);
            neutral.setTextColor(ContextCompat.getColor(this, R.color.home_filter_action_text));
            neutral.setBackgroundResource(R.drawable.bg_home_filter_clear_chip);
            int hp = dpToPx(12);
            int vp = dpToPx(6);
            neutral.setPadding(hp, vp, hp, vp);
        }

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setAllCaps(false);
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setAllCaps(false);
        }
    }

    // Actualiza estilo chips con la logica de negocio actual.
    private void actualizarEstiloChips(TextView chipActivo) {
        resetearChip(chipTodos);
        resetearChip(chipMusica);
        resetearChip(chipCultura);
        resetearChip(chipEsport);
        resetearChip(chipFamiliar);
        resetearChip(chipGastronomia);

        activarChip(chipActivo);
    }

    // Gestiona activar chip en este bloque.
    private void activarChip(TextView chip) {
        if (chip == null) return;
        chip.setTextColor(ContextCompat.getColor(this, R.color.home_chip_text_active));
        chip.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.home_chip_active_bg)));
    }

    // Gestiona resetear chip en este bloque.
    private void resetearChip(TextView chip) {
        if (chip == null) return;
        chip.setTextColor(ContextCompat.getColor(this, R.color.home_chip_text_inactive));
        chip.setBackgroundTintList(null);
    }

    // Gestiona coincide filtro fecha en este bloque.
    private boolean coincideFiltroFecha(Evento e) {
        if (FILTRO_FECHA_TODAS.equals(filtroFechaActual)) return true;
        if (e == null || e.getInicioMillis() <= 0L) return false;

        Calendar ahora = Calendar.getInstance();
        Calendar eventoCal = Calendar.getInstance();
        eventoCal.setTimeInMillis(e.getInicioMillis());

        if (FILTRO_FECHA_HOY.equals(filtroFechaActual)) {
            return ahora.get(Calendar.YEAR) == eventoCal.get(Calendar.YEAR)
                    && ahora.get(Calendar.DAY_OF_YEAR) == eventoCal.get(Calendar.DAY_OF_YEAR);
        }

        if (FILTRO_FECHA_SEMANA.equals(filtroFechaActual)) {
            return ahora.get(Calendar.YEAR) == eventoCal.get(Calendar.YEAR)
                    && ahora.get(Calendar.WEEK_OF_YEAR) == eventoCal.get(Calendar.WEEK_OF_YEAR);
        }

        if (FILTRO_FECHA_FIN_SEMANA.equals(filtroFechaActual)) {
            int dia = eventoCal.get(Calendar.DAY_OF_WEEK);
            return dia == Calendar.SATURDAY || dia == Calendar.SUNDAY;
        }

        return true;
    }

    // Gestiona coincide filtro precio en este bloque.
    private boolean coincideFiltroPrecio(double precio) {
        if (FILTRO_PRECIO_TODOS.equals(filtroPrecioActual)) return true;
        if (FILTRO_PRECIO_GRATIS.equals(filtroPrecioActual)) return precio <= 0.009d;
        if (FILTRO_PRECIO_PAGO.equals(filtroPrecioActual)) return precio > 0.009d;
        return true;
    }

    // Gestiona coincide filtro horario en este bloque.
    private boolean coincideFiltroHorario(Evento e) {
        if (FILTRO_HORARIO_TODOS.equals(filtroHorarioActual)) return true;
        if (e == null || e.getInicioMillis() <= 0L) return false;

        Calendar eventoCal = Calendar.getInstance();
        eventoCal.setTimeInMillis(e.getInicioMillis());
        int hora = eventoCal.get(Calendar.HOUR_OF_DAY);

        if (FILTRO_HORARIO_MANANA.equals(filtroHorarioActual)) {
            return hora >= 6 && hora < 12;
        }
        if (FILTRO_HORARIO_TARDE.equals(filtroHorarioActual)) {
            return hora >= 12 && hora < 20;
        }
        if (FILTRO_HORARIO_NOCHE.equals(filtroHorarioActual)) {
            return hora >= 20 || hora < 6;
        }
        return true;
    }

    // Gestiona coincide filtro distancia en este bloque.
    private boolean coincideFiltroDistancia(Double distanciaKm) {
        if (FILTRO_DISTANCIA_TODAS.equals(filtroDistanciaActual)) return true;
        if (!FILTRO_DISTANCIA_CERCA.equals(filtroDistanciaActual)) return true;
        return distanciaKm != null && distanciaKm <= DISTANCIA_CERCA_KM;
    }

    // Gestiona calcular distancia evento km en este bloque.
    private Double calcularDistanciaEventoKm(Evento e) {
        if (!hasUserLocation || e == null || !e.tieneCoordenadas()) return null;
        Double lat = e.getLatitud();
        Double lng = e.getLongitud();
        if (lat == null || lng == null) return null;

        float[] result = new float[1];
        Location.distanceBetween(userLat, userLng, lat, lng, result);
        return result[0] / 1000d;
    }

    // Actualiza resumen filtros con la logica de negocio actual.
    private void actualizarResumenFiltros() {
        List<String> partes = new ArrayList<>();
        if (FILTRO_FECHA_HOY.equals(filtroFechaActual)) {
            partes.add(getString(R.string.home_filter_date_today));
        } else if (FILTRO_FECHA_SEMANA.equals(filtroFechaActual)) {
            partes.add(getString(R.string.home_filter_date_week));
        } else if (FILTRO_FECHA_FIN_SEMANA.equals(filtroFechaActual)) {
            partes.add(getString(R.string.home_filter_date_weekend));
        }

        if (FILTRO_PRECIO_GRATIS.equals(filtroPrecioActual)) {
            partes.add(getString(R.string.home_filter_price_free));
        } else if (FILTRO_PRECIO_PAGO.equals(filtroPrecioActual)) {
            partes.add(getString(R.string.home_filter_price_paid));
        }

        if (FILTRO_HORARIO_MANANA.equals(filtroHorarioActual)) {
            partes.add(getString(R.string.home_filter_time_morning));
        } else if (FILTRO_HORARIO_TARDE.equals(filtroHorarioActual)) {
            partes.add(getString(R.string.home_filter_time_afternoon));
        } else if (FILTRO_HORARIO_NOCHE.equals(filtroHorarioActual)) {
            partes.add(getString(R.string.home_filter_time_night));
        }

        if (FILTRO_DISTANCIA_CERCA.equals(filtroDistanciaActual)) {
            partes.add(getString(R.string.home_filter_distance_near_short));
        }

        if (!filtroZonaTexto.isEmpty()) {
            partes.add(getString(R.string.home_filter_zone_prefix, filtroZonaTexto));
        }

        if (tvFilterSummary != null) {
            if (partes.isEmpty()) {
                tvFilterSummary.setText(getString(R.string.home_filter_summary_default));
            } else {
                tvFilterSummary.setText(String.join(" | ", partes));
            }
        }
        if (tvClearFilters != null) {
            tvClearFilters.setVisibility(partes.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    // Gestiona obtener chip categoria actual en este bloque.
    private TextView obtenerChipCategoriaActual() {
        if ("Musica".equalsIgnoreCase(categoriaActual)) return chipMusica;
        if ("Cultura".equalsIgnoreCase(categoriaActual)) return chipCultura;
        if ("Esport".equalsIgnoreCase(categoriaActual)) return chipEsport;
        if ("Familiar".equalsIgnoreCase(categoriaActual)) return chipFamiliar;
        if ("Gastronomia".equalsIgnoreCase(categoriaActual)) return chipGastronomia;
        return chipTodos;
    }

    // Carga filtros persistidos desde la fuente correspondiente.
    private void cargarFiltrosPersistidos() {
        if (filtrosPrefs == null) return;

        categoriaActual = filtrosPrefs.getString(KEY_FILTER_CATEGORIA, "Todos");
        filtroFechaActual = filtrosPrefs.getString(KEY_FILTER_FECHA, FILTRO_FECHA_TODAS);
        filtroPrecioActual = filtrosPrefs.getString(KEY_FILTER_PRECIO, FILTRO_PRECIO_TODOS);
        filtroHorarioActual = filtrosPrefs.getString(KEY_FILTER_HORARIO, FILTRO_HORARIO_TODOS);
        filtroDistanciaActual = filtrosPrefs.getString(KEY_FILTER_DISTANCIA, FILTRO_DISTANCIA_TODAS);
        filtroZonaTexto = filtrosPrefs.getString(KEY_FILTER_ZONA, "");
        filtroZonaNormalizada = normalizarTexto(filtroZonaTexto);

        if (FILTRO_DISTANCIA_CERCA.equals(filtroDistanciaActual) && !tienePermisoUbicacion()) {
            filtroDistanciaActual = FILTRO_DISTANCIA_TODAS;
        }
    }

    // Guarda filtros persistidos y sincroniza cambios.
    private void guardarFiltrosPersistidos() {
        if (filtrosPrefs == null) return;

        filtrosPrefs.edit()
                .putString(KEY_FILTER_CATEGORIA, categoriaActual)
                .putString(KEY_FILTER_FECHA, filtroFechaActual)
                .putString(KEY_FILTER_PRECIO, filtroPrecioActual)
                .putString(KEY_FILTER_HORARIO, filtroHorarioActual)
                .putString(KEY_FILTER_DISTANCIA, filtroDistanciaActual)
                .putString(KEY_FILTER_ZONA, filtroZonaTexto)
                .apply();
    }

    // Normaliza texto para evitar inconsistencias de comparacion.
    private String normalizarTexto(String value) {
        String base = safeLower(value).trim();
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // Gestiona safe lower en este bloque.
    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    // Gestiona dp to px en este bloque.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

