package com.example.tarraco_fest.Activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tarraco_fest.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText etEmail, etPassword;
    private TextView tvEstado;

    private GoogleSignInClient googleClient;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);

                    if (account == null) {
                        setEstado("Google: cancelado");
                        return;
                    }

                    AuthCredential cred = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                    auth.signInWithCredential(cred)
                            .addOnSuccessListener(r -> upsertUsuario(false)) // Google NO acepta términos aquí
                            .addOnFailureListener(e -> setEstado("Google error: " + e.getMessage()));

                } catch (ApiException e) {
                    setEstado("Google error (status): " + e.getStatusCode());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvEstado = findViewById(R.id.tvEstado);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnGoogle = findViewById(R.id.btnGoogle);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        btnLogin.setOnClickListener(v -> loginEmail());
        btnRegister.setOnClickListener(v -> mostrarDialogRegistroEmail());
        btnGoogle.setOnClickListener(v -> loginGoogle());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Si ya hay sesión, no enseñes login
        if (auth.getCurrentUser() != null) goHome();
    }

    private void loginEmail() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            setEstado("Rellena email y contraseña");
            return;
        }

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> upsertUsuario(false))
                .addOnFailureListener(e -> setEstado("Login error: " + e.getMessage()));
    }

    private void loginGoogle() {
        googleClient.signOut().addOnCompleteListener(t -> {
            Intent intent = googleClient.getSignInIntent();
            googleLauncher.launch(intent);
        });
    }

    // Crea/actualiza usuarios/{uid}. Si aceptaTerminosEnEstePaso=true (registro email) lo guarda.
    private void upsertUsuario(boolean aceptaTerminosEnEstePaso) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            setEstado("Error: usuario null");
            return;
        }

        String uid = u.getUid();

        // Método auth
        String metodo = "desconocido";
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            String p = info.getProviderId();
            if ("google.com".equals(p)) metodo = "google";
            if ("password".equals(p)) metodo = "email";
        }

        String nombre = u.getDisplayName();

        if (nombre == null || nombre.trim().isEmpty()) {
            String email = u.getEmail() != null ? u.getEmail() : "";
            if (email.contains("@")) {
                nombre = email.substring(0, email.indexOf("@"));
            } else if (!email.isEmpty()) {
                nombre = email;
            } else {
                nombre = "Usuario";
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("metodoAuth", metodo);
        data.put("nombreMostrado", nombre);
        data.put("email", u.getEmail() != null ? u.getEmail() : "");
        data.put("rol", "usuario");
        data.put("bloqueado", false);

        if (aceptaTerminosEnEstePaso) {
            data.put("aceptaTerminos", true);
            data.put("aceptaTerminosEn", Timestamp.now());
        }
        // Importante: NO pongas aceptaTerminos=false aquí, para no pisar si ya lo aceptó.

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) data.put("creadoEn", Timestamp.now());

                    db.collection("usuarios").document(uid)
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener(v -> goHome())
                            .addOnFailureListener(e -> setEstado("Firestore usuarios error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> setEstado("Error leyendo usuarios: " + e.getMessage()));
    }

    private void mostrarDialogRegistroEmail() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_registro, null);

        EditText etEmailReg = view.findViewById(R.id.etEmailReg);
        EditText etP1 = view.findViewById(R.id.etPassReg);
        EditText etP2 = view.findViewById(R.id.etPassReg2);
        CheckBox cb = view.findViewById(R.id.cbTerminos);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Registro")
                .setView(view)
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Crear cuenta", null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = etEmailReg.getText().toString().trim();
            String p1 = etP1.getText().toString();
            String p2 = etP2.getText().toString();

            // Limpia errores previos
            etEmailReg.setError(null);
            etP1.setError(null);
            etP2.setError(null);

            if (email.isEmpty()) {
                etEmailReg.setError("Email obligatorio");
                etEmailReg.requestFocus();
                return;
            }
            if (p1.isEmpty()) {
                etP1.setError("Contraseña obligatoria");
                etP1.requestFocus();
                return;
            }
            if (p1.length() < 6) {
                etP1.setError("Mínimo 6 caracteres");
                etP1.requestFocus();
                return;
            }
            if (p2.isEmpty()) {
                etP2.setError("Repite la contraseña");
                etP2.requestFocus();
                return;
            }
            if (!p1.equals(p2)) {
                etP2.setError("Las contraseñas no coinciden");
                etP2.requestFocus();
                return;
            }
            if (!cb.isChecked()) {
                // Aquí mejor Toast porque es un checkbox
                Toast.makeText(this, "Debes aceptar los términos", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.createUserWithEmailAndPassword(email, p1)
                    .addOnSuccessListener(r -> {
                        upsertUsuario(true);
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        // Error también dentro del popup
                        Toast.makeText(this, "Registro error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }


    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void setEstado(String msg) {
        Log.e("AUTH_UI", msg);
        tvEstado.setText(msg);
    }
}
