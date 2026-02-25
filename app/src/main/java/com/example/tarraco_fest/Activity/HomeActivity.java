package com.example.tarraco_fest.Activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private TextView tvHome;
    private Button btnLogout;
    private Button btnAdmin;
    private final AdminAccessRepository adminAccessRepository = new AdminAccessRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvHome = findViewById(R.id.tvHome);
        btnLogout = findViewById(R.id.btnLogout);
        btnAdmin = findViewById(R.id.btnAdmin);

        Button btnEventos = findViewById(R.id.btnEventos);
        btnEventos.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, EventosActivity.class))
        );

        Button btnPerfil = findViewById(R.id.btnPerfil);
        btnPerfil.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, PerfilActivity.class))
        );

        btnAdmin.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, AdminPanelActivity.class))
        );

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(HomeActivity.this, AuthActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        mostrarNombreUsuario();
        comprobarTerminos();
        comprobarAccesoAdmin();
    }

    private void mostrarNombreUsuario() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            tvHome.setText("No hay sesion");
            volverAuth();
            return;
        }

        String uid = u.getUid();

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String nombre = doc.getString(FirestoreSchema.UsuarioFields.NOMBRE_MOSTRADO);

                    if (nombre == null || nombre.trim().isEmpty()) {
                        nombre = u.getDisplayName();
                    }
                    if (nombre == null || nombre.trim().isEmpty()) {
                        nombre = u.getEmail();
                    }

                    tvHome.setText("Has iniciado sesion\n" + nombre);
                })
                .addOnFailureListener(e -> {
                    String nombre = u.getDisplayName();
                    if (nombre == null || nombre.trim().isEmpty()) nombre = u.getEmail();
                    tvHome.setText("Has iniciado sesion\n" + nombre);
                });
    }

    private void comprobarTerminos() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            volverAuth();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(u.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    Boolean acepta = doc.getBoolean(FirestoreSchema.UsuarioFields.ACEPTA_TERMINOS);
                    boolean ok = acepta != null && acepta;
                    if (!ok) mostrarDialogTerminos(u.getUid());
                })
                .addOnFailureListener(e -> volverAuth());
    }

    private void mostrarDialogTerminos(String uid) {
        new AlertDialog.Builder(this)
                .setTitle("Terminos y condiciones")
                .setMessage("Para usar la app debes aceptar los terminos.")
                .setCancelable(false)
                .setNegativeButton("Salir", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    volverAuth();
                })
                .setPositiveButton("Aceptar", (d, w) -> {
                    Map<String, Object> up = new HashMap<>();
                    up.put(FirestoreSchema.UsuarioFields.ACEPTA_TERMINOS, true);
                    up.put(FirestoreSchema.UsuarioFields.ACEPTA_TERMINOS_EN, Timestamp.now());

                    FirebaseFirestore.getInstance()
                            .collection(FirestoreSchema.Collections.USUARIOS)
                            .document(uid)
                            .set(up, SetOptions.merge());
                })
                .show();
    }

    private void comprobarAccesoAdmin() {
        btnAdmin.setVisibility(View.GONE);

        adminAccessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            @Override
            public void onResult(boolean isAdmin) {
                btnAdmin.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception e) {
                btnAdmin.setVisibility(View.GONE);
            }
        });
    }

    private void volverAuth() {
        Intent i = new Intent(this, AuthActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
