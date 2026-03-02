package com.example.tarraco_fest.Activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_usuarios);

        RecyclerView rv = findViewById(R.id.rvAdminUsuarios);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AdminUsersAdapter(new AdminUsersAdapter.Listener() {
            @Override
            public void onToggleBloqueo(AdminUser user) {
                toggleBloqueo(user);
            }

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

    private void configurarBuscadorYFiltros() {
        EditText etBuscar = findViewById(R.id.etAdminUsuariosBuscar);
        RadioGroup rgRol = findViewById(R.id.rgAdminUsersRol);
        RadioGroup rgEstado = findViewById(R.id.rgAdminUsersEstado);

        rgRol.check(R.id.rbAdminUsersRolTodos);
        rgEstado.check(R.id.rbAdminUsersEstadoTodos);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

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

    private void validarAccesoYCargar() {
        accessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            @Override
            public void onResult(boolean isAdmin) {
                if (!isAdmin) {
                    Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_access_denied), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                cargarUsuarios();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_access_error), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void cargarUsuarios() {
        usersRepository.cargarUsuarios(new AdminUsersRepository.ListCallback() {
            @Override
            public void onOk(List<AdminUser> data) {
                users.clear();
                users.addAll(data);
                aplicarFiltrosLocales();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_users_load_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toggleBloqueo(AdminUser user) {
        usersRepository.actualizarBloqueo(user.uid, !user.bloqueado, new AdminUsersRepository.ActionCallback() {
            @Override
            public void onOk() {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_user_updated_ok), Toast.LENGTH_SHORT).show();
                cargarUsuarios();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_user_update_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toggleRol(AdminUser user) {
        String nuevoRol = FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(user.rol)
                ? FirestoreSchema.UserRoles.USUARIO
                : FirestoreSchema.UserRoles.ADMIN;

        usersRepository.actualizarRol(user.uid, nuevoRol, new AdminUsersRepository.ActionCallback() {
            @Override
            public void onOk() {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_user_updated_ok), Toast.LENGTH_SHORT).show();
                cargarUsuarios();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminUsuariosActivity.this, getString(R.string.admin_user_update_error), Toast.LENGTH_LONG).show();
            }
        });
    }

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

    private String normalizar(String value) {
        if (value == null) return "";
        String base = value.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
