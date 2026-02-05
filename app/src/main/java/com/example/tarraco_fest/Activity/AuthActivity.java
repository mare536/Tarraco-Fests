package com.example.tarraco_fest.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tarraco_fest.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText etEmail, etPassword;
    private TextView tvEstado;

    private GoogleSignInClient googleClient;

    private static final int ESTADO_INFO = 0;
    private static final int ESTADO_OK = 1;
    private static final int ESTADO_ERROR = 2;

    // Flujo "crear contraseña para cuenta Google"
    private boolean pendingLinkPassword = false;
    private String pendingLinkEmail = null;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    if (result.getData() == null) {
                        setEstado("Google: cancelado", ESTADO_INFO, false);
                        return;
                    }

                    GoogleSignInAccount account = GoogleSignIn
                            .getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);

                    if (account == null) {
                        setEstado("Google: cancelado", ESTADO_INFO, false);
                        return;
                    }

                    AuthCredential cred = GoogleAuthProvider.getCredential(account.getIdToken(), null);

                    auth.signInWithCredential(cred)
                            .addOnSuccessListener(r -> onLoginOkGoogle())
                            .addOnFailureListener(e -> setEstado("Google error: " + e.getMessage(), ESTADO_ERROR, true));

                } catch (ApiException e) {
                    setEstado("Google error (status): " + e.getStatusCode(), ESTADO_ERROR, true);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Si NO quieres forzar logout siempre, comenta esta línea
        // FirebaseAuth.getInstance().signOut();

        setContentView(R.layout.activity_auth);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvEstado = findViewById(R.id.tvEstado);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnGoogle = findViewById(R.id.btnGoogle);

        // Animación UI (tuya)
        ImageView bgNormal = findViewById(R.id.imgBgNormal);
        ImageView bgBlur = findViewById(R.id.imgBgBlur);
        View overlay = findViewById(R.id.overlay);
        View content = findViewById(R.id.authContent);

        bgBlur.setAlpha(0f);
        overlay.setAlpha(0f);
        content.setAlpha(0f);
        content.setTranslationY(22f);
        bgNormal.postDelayed(() -> {
            bgBlur.animate().alpha(1f).setDuration(650).start();
            overlay.animate().alpha(1f).setDuration(450).start();
            content.animate().alpha(1f).translationY(0f).setDuration(550).start();
        }, 180);

        // Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        btnLogin.setOnClickListener(v -> loginEmail());
        btnRegister.setOnClickListener(v -> mostrarDialogRegistroEmail(null));
        btnGoogle.setOnClickListener(v -> loginGoogle());

        // Limpia feedback al escribir
        etEmail.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { limpiarFeedbackLogin(); }
            public void afterTextChanged(Editable s) {}
        });
        etPassword.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { limpiarFeedbackLogin(); }
            public void afterTextChanged(Editable s) {}
        });

        boolean autoGoogle = getIntent().getBooleanExtra("AUTO_GOOGLE", false);
        if (autoGoogle) loginGoogle();
    }

    @Override
    protected void onStart() {
        super.onStart();

        boolean autoGoogle = getIntent().getBooleanExtra("AUTO_GOOGLE", false);
        if (autoGoogle) return;

        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;

        // Si entra por email/password y no verificado -> no entrar
        if (esProveedorPassword(u) && !u.isEmailVerified()) {
            setEstado("Cuenta no verificada. Revisa tu correo.", ESTADO_ERROR, false);
            auth.signOut();
            return;
        }

        goHome();
    }

    // ===================== LOGIN EMAIL =====================

    private void loginEmail() {
        limpiarFeedbackLogin();

        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            if (email.isEmpty()) { etEmail.setError("Introduce el email"); shake(etEmail); }
            if (pass.isEmpty()) { etPassword.setError("Introduce la contraseña"); shake(etPassword); }
            setEstado("Rellena email y contraseña", ESTADO_ERROR, false);
            return;
        }

        setEstado("Iniciando sesión…", ESTADO_INFO, false);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    FirebaseUser u = auth.getCurrentUser();
                    if (u == null) {
                        setEstado("Login error: user null", ESTADO_ERROR, true);
                        return;
                    }

                    u.reload().addOnSuccessListener(v -> {
                        if (esProveedorPassword(u) && !u.isEmailVerified()) {
                            setEstado("Cuenta no verificada. Revisa tu correo (puedes reenviarlo).", ESTADO_ERROR, false);
                            auth.signOut();
                            mostrarDialogoNoVerificado(email, pass);
                            return;
                        }
                        upsertUsuario(false, true, "email");
                    }).addOnFailureListener(e -> {
                        if (esProveedorPassword(u) && !u.isEmailVerified()) {
                            setEstado("Cuenta no verificada. Revisa tu correo (puedes reenviarlo).", ESTADO_ERROR, false);
                            auth.signOut();
                            mostrarDialogoNoVerificado(email, pass);
                            return;
                        }
                        upsertUsuario(false, true, "email");
                    });
                })
                .addOnFailureListener(e -> manejarErrorLoginEmail(e, email));
    }

    private void manejarErrorLoginEmail(Exception e, String email) {
        String code = null;
        if (e instanceof FirebaseAuthException) code = ((FirebaseAuthException) e).getErrorCode();

        if ("ERROR_INVALID_EMAIL".equals(code)) {
            etEmail.setError("Email no válido");
            etEmail.requestFocus();
            shake(etEmail);
            setEstado("Email no válido", ESTADO_ERROR, false);
            return;
        }

        // En password provider, Google-only suele llegar como USER_NOT_FOUND (o credenciales inválidas)
        if (e instanceof FirebaseAuthInvalidUserException || "ERROR_USER_NOT_FOUND".equals(code)
                || e instanceof FirebaseAuthInvalidCredentialsException
                || "ERROR_WRONG_PASSWORD".equals(code)
                || "ERROR_INVALID_CREDENTIAL".equals(code)
                || "ERROR_INVALID_LOGIN_CREDENTIALS".equals(code)) {

            // Aquí decidimos: ¿tiene password? -> "contraseña incorrecta"
            // ¿es Google-only? -> ofrecer crear contraseña (verificando con Google)
            // ¿no se puede saber? -> mostrar opciones sin afirmar "no existe"
            resolverCasoPasswordGoogleONoSePuedeSaber(email);
            return;
        }

        setEstado("Login error: " + e.getMessage(), ESTADO_ERROR, true);
    }

    private void resolverCasoPasswordGoogleONoSePuedeSaber(String email) {
        auth.fetchSignInMethodsForEmail(email)
                .addOnSuccessListener(res -> {
                    List<String> m = res.getSignInMethods();
                    boolean tieneGoogle = m != null && m.contains("google.com");
                    boolean tienePassword = m != null && m.contains("password");

                    if (tienePassword) {
                        marcarContrasenaIncorrecta();
                        return;
                    }

                    if (tieneGoogle && !tienePassword) {
                        // Existe como Google-only
                        iniciarFlujoCrearPassword(email);
                        return;
                    }

                    // Vacío o no concluyente (en algunos proyectos puede ser ambiguo)
                    iniciarFlujoCrearPassword(email);
                })
                .addOnFailureListener(x -> {
                    // Si no podemos consultar: no afirmamos "no existe"
                    iniciarFlujoCrearPassword(email);
                });
    }

    private void marcarContrasenaIncorrecta() {
        etPassword.setError("Contraseña incorrecta");
        etPassword.requestFocus();
        shake(etPassword);
        setEstado("Contraseña incorrecta", ESTADO_ERROR, false);
    }

    // ===================== DIALOGO BONITO (layout propio) =====================

    private void iniciarFlujoCrearPassword(String email) {
        // feedback visual
        etEmail.setError("Ese correo está asociado a Google");
        etEmail.requestFocus();
        shake(etEmail);
        setEstado("Para crear contraseña primero hay que verificar con Google (no cambia tu contraseña de Google).", ESTADO_ERROR, false);

        pendingLinkEmail = email;
        pendingLinkPassword = true;

        FirebaseUser u = auth.getCurrentUser();

        // Si ya está logueado con ESA cuenta, abrimos directamente el XML de contraseña
        if (u != null && u.getEmail() != null && u.getEmail().equalsIgnoreCase(email) && esProveedorGoogle(u)) {
            mostrarDialogCrearPasswordParaCuentaGoogle(email); // <-- usa dialog_crear_password.xml
            return;
        }

        // Si no hay sesión, hay que pasar por Google una vez (verificación)
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Crear contraseña")
                .setMessage("Necesitamos que entres con Google una vez para verificar que eres el dueño del correo.\n\n"
                        + "Esto NO cambia tu contraseña de Google, solo añade una contraseña en Firebase.")
                .setPositiveButton("Continuar con Google", (d, w) -> loginGoogle())
                .setNegativeButton("Cancelar", (d, w) -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                })
                .show();
    }

    private boolean esProveedorGoogle(FirebaseUser u) {
        if (u == null) return false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if ("google.com".equals(info.getProviderId())) return true;
        }
        return false;
    }


    // ===================== REGISTRO EMAIL =====================

    private void mostrarDialogRegistroEmail(String emailPrefill) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_registro, null);

        EditText etEmailReg = view.findViewById(R.id.etEmailReg);
        EditText etP1 = view.findViewById(R.id.etPassReg);
        EditText etP2 = view.findViewById(R.id.etPassReg2);
        CheckBox cb = view.findViewById(R.id.cbTerminos);
        TextView tvError = view.findViewById(R.id.tvErrorRegistro);

        if (emailPrefill != null && !emailPrefill.isEmpty()) etEmailReg.setText(emailPrefill);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setMessage("Al crear la cuenta te enviaremos un correo para verificar tu email.")
                .setView(view)
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Crear cuenta", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = etEmailReg.getText().toString().trim();
            String p1 = etP1.getText().toString().trim();
            String p2 = etP2.getText().toString().trim();

            tvError.setVisibility(View.GONE);

            if (email.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
                tvError.setText("Rellena email y contraseña");
                tvError.setVisibility(View.VISIBLE);
                setEstado("Faltan campos en el registro", ESTADO_ERROR, false);
                return;
            }
            if (p1.length() < 6) {
                tvError.setText("Contraseña mínimo 6");
                tvError.setVisibility(View.VISIBLE);
                setEstado("Contraseña demasiado corta", ESTADO_ERROR, false);
                return;
            }
            if (!p1.equals(p2)) {
                tvError.setText("Las contraseñas no coinciden");
                tvError.setVisibility(View.VISIBLE);
                setEstado("Las contraseñas no coinciden", ESTADO_ERROR, false);
                return;
            }
            if (!cb.isChecked()) {
                tvError.setText("Debes aceptar los términos");
                tvError.setVisibility(View.VISIBLE);
                setEstado("Debes aceptar los términos", ESTADO_ERROR, false);
                return;
            }

            setEstado("Creando cuenta…", ESTADO_INFO, false);

            auth.createUserWithEmailAndPassword(email, p1)
                    .addOnSuccessListener(r -> {
                        FirebaseUser user = r.getUser();
                        if (user == null) {
                            setEstado("Registro error: user null", ESTADO_ERROR, true);
                            return;
                        }

                        upsertUsuario(true, false, "email");

                        user.sendEmailVerification()
                                .addOnSuccessListener(vv -> {
                                    prepararLoginTrasRegistro(email);
                                    auth.signOut();
                                    dialog.dismiss();
                                })
                                .addOnFailureListener(ex -> {
                                    setEstado("No se pudo enviar verificación: " + ex.getMessage(), ESTADO_ERROR, true);
                                    auth.signOut();
                                    dialog.dismiss();
                                });
                    })
                    .addOnFailureListener(ex -> {
                        if (ex instanceof FirebaseAuthUserCollisionException) {
                            tvError.setText("Ese email ya está registrado");
                            tvError.setVisibility(View.VISIBLE);
                            setEstado("Ese email ya está registrado. Inicia sesión.", ESTADO_ERROR, false);
                            return;
                        }
                        setEstado("Registro error: " + ex.getMessage(), ESTADO_ERROR, true);
                    });
        });
    }

    private void prepararLoginTrasRegistro(String email) {
        etEmail.setText(email);
        etPassword.setText("");
        limpiarFeedbackLogin();
        setEstado("Cuenta creada. Verifica el email y luego inicia sesión.", ESTADO_OK, false);
    }

    // ===================== NO VERIFICADO =====================

    private void mostrarDialogoNoVerificado(String email, String pass) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Cuenta no verificada")
                .setMessage("Tienes que verificar tu correo para entrar.\n\n¿Quieres reenviar el correo de verificación?")
                .setPositiveButton("Reenviar", (d, w) -> reenviarVerificacion(email, pass))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void reenviarVerificacion(String email, String pass) {
        setEstado("Reenviando verificación…", ESTADO_INFO, false);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    FirebaseUser user = r.getUser();
                    if (user == null) {
                        setEstado("Error reenviando: user null", ESTADO_ERROR, true);
                        return;
                    }

                    user.sendEmailVerification()
                            .addOnSuccessListener(v -> setEstado("Correo reenviado. Revisa Bandeja/Spam.", ESTADO_OK, false))
                            .addOnFailureListener(ex -> setEstado("Error reenviando: " + ex.getMessage(), ESTADO_ERROR, true));

                    auth.signOut();
                })
                .addOnFailureListener(ex -> setEstado("No se pudo reenviar: " + ex.getMessage(), ESTADO_ERROR, true));
    }

    // ===================== GOOGLE LOGIN =====================

    private void loginGoogle() {
        googleClient.signOut().addOnCompleteListener(t -> {
            Intent intent = googleClient.getSignInIntent();
            googleLauncher.launch(intent);
        });
    }

    private void onLoginOkGoogle() {
        FirebaseUser u = auth.getCurrentUser();

        // Si estamos en flujo "crear contraseña"
        if (pendingLinkPassword && pendingLinkEmail != null) {

            // Seguridad UX: si eligió otra cuenta Google distinta, cancelamos
            if (u == null || u.getEmail() == null || !u.getEmail().equalsIgnoreCase(pendingLinkEmail)) {
                setEstado("Has elegido otra cuenta Google. Vuelve a intentarlo.", ESTADO_ERROR, false);
                pendingLinkEmail = null;
                pendingLinkPassword = false;
                auth.signOut();
                return;
            }

            // Misma cuenta -> abrimos el XML de crear contraseña
            mostrarDialogCrearPasswordParaCuentaGoogle(pendingLinkEmail);
            return;
        }

        setEstado("Login Google OK", ESTADO_OK, false);
        upsertUsuario(false, true, "google");
    }

    // ===================== CREAR PASSWORD (Firebase) =====================

    private void mostrarDialogCrearPasswordParaCuentaGoogle(String email) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_crear_password, null);

        EditText etP1 = view.findViewById(R.id.etPass1);
        EditText etP2 = view.findViewById(R.id.etPass2);
        TextView tvError = view.findViewById(R.id.tvError);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Crear contraseña")
                .setMessage("Esto NO cambia tu contraseña de Google.\nSolo añade una contraseña en Firebase para entrar también con email + contraseña.")
                .setView(view)
                .setNegativeButton("Cancelar", (d, w) -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                    d.dismiss();
                    upsertUsuario(false, true, "google");
                })
                .setPositiveButton("Guardar", null)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_glass);
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p1 = etP1.getText().toString().trim();
            String p2 = etP2.getText().toString().trim();

            tvError.setVisibility(View.GONE);

            if (p1.length() < 6) {
                tvError.setText("Contraseña mínimo 6");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            if (!p1.equals(p2)) {
                tvError.setText("Las contraseñas no coinciden");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            linkPasswordAlUsuarioActual(email, p1, dialog);
        });
    }

    private void linkPasswordAlUsuarioActual(String email, String password, AlertDialog dialog) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            setEstado("Error: no hay sesión", ESTADO_ERROR, true);
            return;
        }

        AuthCredential cred = EmailAuthProvider.getCredential(email, password);

        u.linkWithCredential(cred)
                .addOnSuccessListener(r -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;

                    setEstado("Contraseña creada. Ya puedes entrar con email.", ESTADO_OK, false);
                    dialog.dismiss();

                    // Sigues logueado con Google, pero ahora ya tiene provider password vinculado
                    upsertUsuario(false, true, "google");
                })
                .addOnFailureListener(e -> {
                    setEstado("No se pudo crear contraseña: " + e.getMessage(), ESTADO_ERROR, true);
                });
    }

    // ===================== FIRESTORE USUARIOS =====================

    private void upsertUsuario(boolean aceptaTerminosEnEstePaso, boolean irHome, String metodoLogin) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            setEstado("Error: usuario null", ESTADO_ERROR, true);
            return;
        }

        boolean hasGoogle = false, hasPassword = false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if ("google.com".equals(info.getProviderId())) hasGoogle = true;
            if ("password".equals(info.getProviderId())) hasPassword = true;
        }

        List<String> metodos = new ArrayList<>();
        if (hasGoogle) metodos.add("google");
        if (hasPassword) metodos.add("email");

        Map<String, Object> data = new HashMap<>();
        data.put("metodoAuth", metodoLogin);       // método de ESTA sesión: "google" / "email"
        data.put("metodosVinculados", metodos);    // métodos asociados reales
        data.put("nombreMostrado", u.getDisplayName() != null ? u.getDisplayName() : "Usuario");
        data.put("email", u.getEmail() != null ? u.getEmail() : "");
        data.put("rol", "usuario");
        data.put("bloqueado", false);

        if (aceptaTerminosEnEstePaso) {
            data.put("aceptaTerminos", true);
            data.put("aceptaTerminosEn", Timestamp.now());
        }

        String uid = u.getUid();

        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) data.put("creadoEn", Timestamp.now());

                    db.collection("usuarios").document(uid)
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                if (irHome) {
                                    setEstado("Acceso correcto", ESTADO_OK, false);
                                    goHome();
                                } else {
                                    setEstado("Cuenta creada. Falta verificar el email.", ESTADO_INFO, false);
                                }
                            })
                            .addOnFailureListener(e -> setEstado("Firestore usuarios error: " + e.getMessage(), ESTADO_ERROR, true));
                })
                .addOnFailureListener(e -> setEstado("Error leyendo usuarios: " + e.getMessage(), ESTADO_ERROR, true));
    }

    // ===================== UTIL =====================

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void limpiarFeedbackLogin() {
        if (tvEstado != null) tvEstado.setVisibility(View.GONE);
        if (etEmail != null) etEmail.setError(null);
        if (etPassword != null) etPassword.setError(null);
    }

    private boolean esProveedorPassword(FirebaseUser u) {
        if (u == null) return false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if ("password".equals(info.getProviderId())) return true;
        }
        return false;
    }

    private void setEstado(String msg, int tipo, boolean showToast) {
        Log.e("AUTH_UI", msg);
        if (tvEstado == null) return;

        tvEstado.setVisibility(View.VISIBLE);

        if (tipo == ESTADO_ERROR) {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_error);
            tvEstado.setText("⚠️ " + msg);
            if (showToast) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } else if (tipo == ESTADO_OK) {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_ok);
            tvEstado.setText("✅ " + msg);
        } else {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_info);
            tvEstado.setText("ℹ️ " + msg);
        }

        tvEstado.setAlpha(0f);
        tvEstado.animate().alpha(1f).setDuration(180).start();
    }

    private void shake(View v) {
        v.animate().cancel();
        v.setTranslationX(0f);
        v.animate().translationX(12f).setDuration(45).withEndAction(() ->
                v.animate().translationX(-12f).setDuration(45).withEndAction(() ->
                        v.animate().translationX(8f).setDuration(45).withEndAction(() ->
                                v.animate().translationX(-8f).setDuration(45).withEndAction(() ->
                                        v.animate().translationX(0f).setDuration(45)
                                )
                        )
                )
        );
    }
}
