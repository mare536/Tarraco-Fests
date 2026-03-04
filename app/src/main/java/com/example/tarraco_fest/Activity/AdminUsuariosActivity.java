package com.example.tarraco_fest.Activity;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.AdminUsersAdapter;
import com.example.tarraco_fest.Modelo.AdminUser;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.ViewModel.AdminUsuariosViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Pantalla para gestionar usuarios del sistema.
 * Permite cambiar rol y bloquear/desbloquear cuentas.
 */
public class AdminUsuariosActivity extends AppCompatActivity {

    private static final String FILTRO_TODOS = "todos";
    private static final String FILTRO_ADMIN = "admin";
    private static final String FILTRO_USUARIO = "usuario";
    private static final String FILTRO_ACTIVOS = "activos";
    private static final String FILTRO_BLOQUEADOS = "bloqueados";

    private AdminUsuariosViewModel viewModel;
    private AdminUsersAdapter adapter;
    private View btnRecargar;

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_usuarios);

        RecyclerView rv = findViewById(R.id.rvAdminUsuarios);
        rv.setLayoutManager(new LinearLayoutManager(this));
        configurarBordesSistema(rv);

        adapter = new AdminUsersAdapter(new AdminUsersAdapter.Listener() {
            // Gestiona on toggle bloqueo en este bloque.
            @Override
            public void onToggleBloqueo(AdminUser user) {
                viewModel.toggleBloqueo(user);
            }

            // Gestiona on toggle rol en este bloque.
            @Override
            public void onToggleRol(AdminUser user) {
                viewModel.toggleRol(user);
            }

            // Gestiona on eliminar en este bloque.
            @Override
            public void onEliminar(AdminUser user) {
                confirmarEliminarUsuario(user);
            }
        });
        rv.setAdapter(adapter);

        btnRecargar = findViewById(R.id.btnAdminUsuariosRecargar);
        configurarViewModel();
        configurarBuscadorYFiltros();
        btnRecargar.setOnClickListener(v -> viewModel.cargarUsuarios());
        findViewById(R.id.btnAdminUsuariosVolver).setOnClickListener(v -> finish());

        viewModel.verificarAccesoYCargar();
    }

    // Aplica edge-to-edge con insets para mantener diseño limpio en todos los moviles.
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
            controller.setAppearanceLightStatusBars(!modoOscuro);
            controller.setAppearanceLightNavigationBars(!modoOscuro);
        }

        View root = findViewById(R.id.rootAdminUsuarios);
        View appBar = findViewById(R.id.appBarAdminUsuarios);

        int baseAppBarPaddingTop = appBar.getPaddingTop();
        int baseRecyclerPaddingBottom = rv.getPaddingBottom();
        ViewGroup.MarginLayoutParams lpLista = (ViewGroup.MarginLayoutParams) rv.getLayoutParams();
        int baseListaMarginBottom = lpLista.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int safeBottom = Math.max(bars.bottom, ime.bottom);

            appBar.setPadding(
                    appBar.getPaddingLeft(),
                    baseAppBarPaddingTop + bars.top,
                    appBar.getPaddingRight(),
                    appBar.getPaddingBottom()
            );

            ViewGroup.MarginLayoutParams listaLp = (ViewGroup.MarginLayoutParams) rv.getLayoutParams();
            listaLp.bottomMargin = baseListaMarginBottom + safeBottom;
            rv.setLayoutParams(listaLp);

            rv.setPadding(
                    rv.getPaddingLeft(),
                    rv.getPaddingTop(),
                    rv.getPaddingRight(),
                    baseRecyclerPaddingBottom + safeBottom + dpToPx(8)
            );

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    // Convierte dp a px para margenes/padding adaptativos.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // Configura buscador yfiltros segun el contexto actual.
    private void configurarBuscadorYFiltros() {
        EditText etBuscar = findViewById(R.id.etAdminUsuariosBuscar);
        RadioGroup rgRol = findViewById(R.id.rgAdminUsersRol);
        RadioGroup rgEstado = findViewById(R.id.rgAdminUsersEstado);

        rgRol.check(R.id.rbAdminUsersRolTodos);
        rgEstado.check(R.id.rbAdminUsersEstadoTodos);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            // Gestiona on text changed en este bloque.
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.actualizarBusqueda(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        rgRol.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbAdminUsersRolAdmin) {
                viewModel.actualizarFiltroRol(FILTRO_ADMIN);
            } else if (checkedId == R.id.rbAdminUsersRolUsuario) {
                viewModel.actualizarFiltroRol(FILTRO_USUARIO);
            } else {
                viewModel.actualizarFiltroRol(FILTRO_TODOS);
            }
        });

        rgEstado.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbAdminUsersEstadoActivos) {
                viewModel.actualizarFiltroEstado(FILTRO_ACTIVOS);
            } else if (checkedId == R.id.rbAdminUsersEstadoBloqueados) {
                viewModel.actualizarFiltroEstado(FILTRO_BLOQUEADOS);
            } else {
                viewModel.actualizarFiltroEstado(FILTRO_TODOS);
            }
        });

        // Estado inicial de filtros para primer render.
        viewModel.actualizarBusqueda("");
        viewModel.actualizarFiltroRol(FILTRO_TODOS);
        viewModel.actualizarFiltroEstado(FILTRO_TODOS);
    }

    // Conecta la Activity con su ViewModel y observa estado de lista/mensajes.
    private void configurarViewModel() {
        viewModel = new ViewModelProvider(this).get(AdminUsuariosViewModel.class);

        viewModel.getUsuarios().observe(this, data -> adapter.setData(data));

        viewModel.getToastMessageRes().observe(this, messageRes -> {
            if (messageRes == null) return;
            Toast.makeText(this, getString(messageRes), Toast.LENGTH_LONG).show();
            viewModel.consumirToastMessage();
        });

        viewModel.getShouldCloseScreen().observe(this, shouldClose -> {
            if (shouldClose == null || !shouldClose) return;
            viewModel.consumirCloseScreen();
            finish();
        });

        viewModel.getLoading().observe(this, isLoading -> {
            if (btnRecargar == null) return;
            boolean loading = isLoading != null && isLoading;
            btnRecargar.setEnabled(!loading);
            btnRecargar.setAlpha(loading ? 0.6f : 1f);
        });
    }

    // Muestra confirmacion antes de eliminar un usuario bloqueado.
    private void confirmarEliminarUsuario(AdminUser user) {
        if (user == null || user.uid == null || user.uid.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.admin_user_delete_error), Toast.LENGTH_LONG).show();
            return;
        }

        if (!user.bloqueado) {
            Toast.makeText(this, getString(R.string.admin_user_delete_requires_block), Toast.LENGTH_LONG).show();
            return;
        }

        String nombre = user.nombre == null || user.nombre.trim().isEmpty() ? "-" : user.nombre.trim();

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle(getString(R.string.admin_user_delete_confirm_title))
                .setMessage(getString(R.string.admin_user_delete_confirm_message, nombre))
                .setNegativeButton(getString(R.string.admin_cancel), null)
                .setPositiveButton(getString(R.string.admin_user_delete), (d, w) -> viewModel.eliminarUsuarioBloqueado(user))
                .show();
    }
}
