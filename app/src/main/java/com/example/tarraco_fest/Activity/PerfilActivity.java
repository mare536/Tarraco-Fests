package com.example.tarraco_fest.Activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tarraco_fest.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class PerfilActivity extends AppCompatActivity {

    private TextView tvInfo;
    private EditText etNombre;

    private EditText etPass1, etPass2;
    private Button btnGuardarNombre, btnVincularPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        tvInfo = findViewById(R.id.tvInfo);
        etNombre = findViewById(R.id.etNombre);

        etPass1 = findViewById(R.id.etPass1);
        etPass2 = findViewById(R.id.etPass2);

        btnGuardarNombre = findViewById(R.id.btnGuardarNombre);
        btnVincularPassword = findViewById(R.id.btnVincularPassword);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No hay sesión", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvInfo.setText("Cuenta: " + (user.getEmail() != null ? user.getEmail() : "(sin email)"));

        // Cargar nombre desde Auth como base (luego Firestore lo pisa si existe)
        if (user.getDisplayName() != null) etNombre.setText(user.getDisplayName());

        // (Opcional) cargar nombre desde Firestore si lo tienes
        FirebaseFirestore.getInstance().collection("usuarios").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String nombre = doc.getString("nombreMostrado");
                    if (nombre != null && !nombre.trim().isEmpty()) etNombre.setText(nombre);
                });

        btnGuardarNombre.setOnClickListener(v -> guardarNombre());
        btnVincularPassword.setOnClickListener(v -> vincularPassword());

        // Si ya tiene proveedor password, puedes ocultar la sección:
        if (tieneProviderPassword(user)) {
            btnVincularPassword.setEnabled(false);
            btnVincularPassword.setText("Ya tienes contraseña");
        }
    }

    private void guardarNombre() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String nombre = etNombre.getText().toString().trim();
        etNombre.setError(null);

        if (nombre.isEmpty()) {
            etNombre.setError("Nombre obligatorio");
            etNombre.requestFocus();
            return;
        }

        // 1) Guardar en Auth (displayName)
        UserProfileChangeRequest req =
                new UserProfileChangeRequest.Builder().setDisplayName(nombre).build();

        user.updateProfile(req)
                .addOnSuccessListener(v -> {
                    // 2) Guardar en Firestore
                    Map<String, Object> up = new HashMap<>();
                    up.put("nombreMostrado", nombre);
                    up.put("updatedAt", Timestamp.now());

                    FirebaseFirestore.getInstance()
                            .collection("usuarios").document(user.getUid())
                            .set(up, SetOptions.merge())
                            .addOnSuccessListener(x -> Toast.makeText(this, "Nombre actualizado", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Firestore error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Auth error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void vincularPassword() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "No se pudo obtener el email de la cuenta", Toast.LENGTH_LONG).show();
            return;
        }

        if (tieneProviderPassword(user)) {
            Toast.makeText(this, "Esta cuenta ya tiene contraseña", Toast.LENGTH_SHORT).show();
            return;
        }

        String p1 = etPass1.getText().toString();
        String p2 = etPass2.getText().toString();

        etPass1.setError(null);
        etPass2.setError(null);

        if (p1.isEmpty()) {
            etPass1.setError("Contraseña obligatoria");
            etPass1.requestFocus();
            return;
        }
        if (p1.length() < 6) {
            etPass1.setError("Mínimo 6 caracteres");
            etPass1.requestFocus();
            return;
        }
        if (!p1.equals(p2)) {
            etPass2.setError("Las contraseñas no coinciden");
            etPass2.requestFocus();
            return;
        }

        // Vincula email/password al usuario actual (normalmente logueado con Google)
        user.linkWithCredential(EmailAuthProvider.getCredential(user.getEmail(), p1))
                .addOnSuccessListener(r -> {
                    // Reflejar en Firestore
                    Map<String, Object> up = new HashMap<>();
                    up.put("tienePassword", true);
                    up.put("passwordVinculadaEn", Timestamp.now());

                    FirebaseFirestore.getInstance()
                            .collection("usuarios").document(user.getUid())
                            .set(up, SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, "Contraseña añadida", Toast.LENGTH_LONG).show();
                                btnVincularPassword.setEnabled(false);
                                btnVincularPassword.setText("Ya tienes contraseña");
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Link OK pero Firestore falló: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error vinculando contraseña: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private boolean tieneProviderPassword(FirebaseUser user) {
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) return true;
        }
        return false;
    }
}
