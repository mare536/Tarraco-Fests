package com.example.tarraco_fest.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;

/**
 * Entrada principal del modulo admin.
 * Valida permisos y redirige a gestion de usuarios o eventos.
 */
public class AdminPanelActivity extends AppCompatActivity {

    private final AdminAccessRepository accessRepository = new AdminAccessRepository();

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_panel);

        Button btnUsuarios = findViewById(R.id.btnAdminUsuarios);
        Button btnEventos = findViewById(R.id.btnAdminEventos);
        Button btnVolver = findViewById(R.id.btnAdminVolver);
        TextView tvEstado = findViewById(R.id.tvAdminEstado);

        btnUsuarios.setEnabled(false);
        btnEventos.setEnabled(false);
        tvEstado.setText(getString(R.string.admin_checking_access));

        accessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            // Gestiona on result en este bloque.
            @Override
            public void onResult(boolean isAdmin) {
                if (!isAdmin) {
                    Toast.makeText(AdminPanelActivity.this, getString(R.string.admin_access_denied), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                tvEstado.setText(getString(R.string.admin_access_granted));
                btnUsuarios.setEnabled(true);
                btnEventos.setEnabled(true);
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminPanelActivity.this, getString(R.string.admin_access_error), Toast.LENGTH_LONG).show();
                finish();
            }
        });

        btnUsuarios.setOnClickListener(v -> startActivity(new Intent(this, AdminUsuariosActivity.class)));
        btnEventos.setOnClickListener(v -> startActivity(new Intent(this, AdminEventosActivity.class)));
        btnVolver.setOnClickListener(v -> finish());
    }
}
