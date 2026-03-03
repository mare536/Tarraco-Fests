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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.AdminUsersAdapter;
import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.AdminUser;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;
import com.example.tarraco_fest.Repository.AdminUsersRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private final AdminAccessRepository accessRepository = new AdminAccessRepository();
    private final AdminUsersRepository usersRepository = new AdminUsersRepository();
    private final List<AdminUser> users = new ArrayList<>();
    private AdminUsersAdapter adapter;
    private String busquedaNormalizada = "";
    private String filtroRolActual = FILTRO_TODOS;
    private String filtroEstadoActual = FILTRO_TODOS;

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
                toggleBloqueo(user);
            }

            // Gestiona on toggle rol en este bloque.
            @Override
            public void onToggleRol(AdminUser user) {
                toggleRol(user);
            }
        });
        rv.setAdapter(adapter);

        configurarBuscadorYFiltros();
        findViewById(R.id.btnAdminUsuariosRecargar).setOnClickListener(v -> cargarUsuarios());
        findViewById(R.id.btnAdminUsuariosVolver).setOnClickListener(v -> finish());

        validarAccesoYCargar();
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

            appBar.setPadding(
                    appBar.getPaddingLeft(),
                    baseAppBarPaddingTop + bars.top,
                    appBar.getPaddingRight(),
                    appBar.getPaddingBottom()
            );

            ViewGroup.MarginLayoutParams listaLp = (ViewGroup.MarginLayoutParams) rv.getLayoutParams();
            listaLp.bottomMargin = baseListaMarginBottom + bars.bottom;
            rv.setLayoutParams(listaLp);

            rv.setPadding(
                    rv.getPaddingLeft(),
                    rv.getPaddingTop(),
                    rv.getPaddingRight(),
                    baseRecyclerPaddingBottom + bars.bottom + dpToPx(8)
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
                busquedaNormalizada = normalizar(s == null ? "" : s.toString());
                aplicarFiltrosLocales();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        rgRol.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbAdminUsersRolAdmin) {
                filtroRolActual = FILTRO_ADMIN;
            } else if (checkedId == R.id.rbAdminUsersRolUsuario) {
                filtroRolActual = FILTRO_USUARIO;
            } else {
                filtroRolActual = FILTRO_TODOS;
            }
            aplicarFiltrosLocales();
        });

        rgEstado.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbAdminUsersEstadoActivos) {
                filtroEstadoActual = FILTRO_ACTIVOS;
            } else if (checkedId == R.id.rbAdminUsersEstadoBloqueados) {
                filtroEstadoActual = FILTRO_BLOQUEADOS;
            } else {
                filtroEstadoActual = FILTRO_TODOS;
            }
            aplicarFiltrosLocales();
        });
    }

    // Valida acceso ycargar antes de continuar el flujo.
    private void validarAccesoYCargar() {
        accessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            // Gestiona on result en este bloque.
            @Override
            public void onResult(boolean isAdmin) {
                if (!isAdmin) {
                    Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_access_denied), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                cargarUsuarios();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_access_error), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    // Carga usuarios desde la fuente correspondiente.
    private void cargarUsuarios() {
        usersRepository.cargarUsuarios(new AdminUsersRepository.ListCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk(List<AdminUser> data) {
                users.clear();
                users.addAll(data);
                aplicarFiltrosLocales();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_users_load_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Alterna el estado de bloqueo.
    private void toggleBloqueo(AdminUser user) {
        usersRepository.actualizarBloqueo(user.uid, !user.bloqueado, new AdminUsersRepository.ActionCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk() {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_user_updated_ok), Toast.LENGTH_SHORT).show();
                cargarUsuarios();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_user_update_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Alterna el estado de rol.
    private void toggleRol(AdminUser user) {
        String nuevoRol = FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(user.rol)
                ? FirestoreSchema.UserRoles.USUARIO
                : FirestoreSchema.UserRoles.ADMIN;

        usersRepository.actualizarRol(user.uid, nuevoRol, new AdminUsersRepository.ActionCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk() {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_user_updated_ok), Toast.LENGTH_SHORT).show();
                cargarUsuarios();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_user_update_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Aplica filtros locales respetando el estado actual.
    private void aplicarFiltrosLocales() {
        List<AdminUser> filtrados = new ArrayList<>();

        for (AdminUser u : users) {
            String nombre = normalizar(u.nombre);
            String email = normalizar(u.email);
            boolean coincideTexto = busquedaNormalizada.isEmpty()
                    || nombre.contains(busquedaNormalizada)
                    || email.contains(busquedaNormalizada);

            boolean esAdmin = FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(u.rol);
            boolean coincideRol = FILTRO_TODOS.equals(filtroRolActual)
                    || (FILTRO_ADMIN.equals(filtroRolActual) && esAdmin)
                    || (FILTRO_USUARIO.equals(filtroRolActual) && !esAdmin);

            boolean coincideEstado = FILTRO_TODOS.equals(filtroEstadoActual)
                    || (FILTRO_ACTIVOS.equals(filtroEstadoActual) && !u.bloqueado)
                    || (FILTRO_BLOQUEADOS.equals(filtroEstadoActual) && u.bloqueado);

            if (coincideTexto && coincideRol && coincideEstado) {
                filtrados.add(u);
            }
        }

        adapter.setData(filtrados);
    }

    // Normaliza el flujo para evitar inconsistencias de comparacion.
    private String normalizar(String value) {
        if (value == null) return "";
        String base = value.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
