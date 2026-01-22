package com.example.tarraco_fest;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvHome = findViewById(R.id.tvHome);
        btnLogout = findViewById(R.id.btnLogout);

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(HomeActivity.this, AuthActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        mostrarNombreUsuario();   // <-- esto es lo nuevo
        comprobarTerminos();      // <-- si ya lo tenías, déjalo aquí
    }

    private void mostrarNombreUsuario() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            tvHome.setText("No hay sesión");
            volverAuth();
            return;
        }

        String uid = u.getUid();

        FirebaseFirestore.getInstance()
                .collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String nombre = doc.getString("nombreMostrado");

                    if (nombre == null || nombre.trim().isEmpty()) {
                        nombre = u.getDisplayName();
                    }
                    if (nombre == null || nombre.trim().isEmpty()) {
                        nombre = u.getEmail();
                    }

                    tvHome.setText("Has iniciado sesión\n" + nombre);
                })
                .addOnFailureListener(e -> {
                    String nombre = u.getDisplayName();
                    if (nombre == null || nombre.trim().isEmpty()) nombre = u.getEmail();
                    tvHome.setText("Has iniciado sesión\n" + nombre);
                });
    }

    private void comprobarTerminos() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            volverAuth();
            return;
        }

        FirebaseFirestore.getInstance().collection("usuarios").document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    Boolean acepta = doc.getBoolean("aceptaTerminos");
                    boolean ok = acepta != null && acepta;
                    if (!ok) mostrarDialogTerminos(u.getUid());
                })
                .addOnFailureListener(e -> volverAuth());
    }

    private void mostrarDialogTerminos(String uid) {
        new AlertDialog.Builder(this)
                .setTitle("Términos y condiciones")
                .setMessage("Para usar la app debes aceptar los términos.")
                .setCancelable(false)
                .setNegativeButton("Salir", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    volverAuth();
                })
                .setPositiveButton("Aceptar", (d, w) -> {
                    Map<String, Object> up = new HashMap<>();
                    up.put("aceptaTerminos", true);
                    up.put("aceptaTerminosEn", Timestamp.now());

                    FirebaseFirestore.getInstance()
                            .collection("usuarios").document(uid)
                            .set(up, SetOptions.merge());
                })
                .show();
    }

    private void volverAuth() {
        Intent i = new Intent(this, AuthActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
