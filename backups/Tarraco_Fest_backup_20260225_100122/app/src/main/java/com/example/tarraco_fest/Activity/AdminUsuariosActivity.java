package com.example.tarraco_fest.Activity;

import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;

public class AdminUsuariosActivity extends AppCompatActivity {

    private final AdminAccessRepository accessRepository = new AdminAccessRepository();
    private final AdminUsersRepository usersRepository = new AdminUsersRepository();
    private final List<AdminUser> users = new ArrayList<>();
    private AdminUsersAdapter adapter;

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

        findViewById(R.id.btnAdminUsuariosRecargar).setOnClickListener(v -> cargarUsuarios());
        findViewById(R.id.btnAdminUsuariosVolver).setOnClickListener(v -> finish());

        validarAccesoYCargar();
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
                adapter.setData(users);
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
}
