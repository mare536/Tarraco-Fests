package com.example.tarraco_fest.Activity;

import android.accounts.AccountManager;
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

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pantalla de autenticacion principal (Google y correo + contrasena).
 * Contiene la logica de inicio de sesion y vinculacion de password para cuentas Google.
 */
public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText etEmail;
    private EditText etPassword;
    private TextView tvEstado;

    private GoogleSignInClient googleClient;

    private static final int ESTADO_INFO = 0;
    private static final int ESTADO_OK = 1;
    private static final int ESTADO_ERROR = 2;

    // Flow para crear password en una cuenta Google existente.
    private boolean pendingLinkPassword = false;
    private String pendingLinkEmail = null;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    if (result.getData() == null) {
                        setEstado("Google cancelado", ESTADO_INFO, false);
                        return;
                    }

                    GoogleSignInAccount account = GoogleSignIn
                            .getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);

                    if (account == null) {
                        setEstado("Google cancelado", ESTADO_INFO, false);
                        return;
                    }

                    autenticarConCuentaGoogle(account, false);

                } catch (ApiException e) {
                    setEstado("Google error (status): " + e.getStatusCode(), ESTADO_ERROR, true);
                }
            });

    // Gestiona on create en este bloque.
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

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        btnLogin.setOnClickListener(v -> loginEmail());
        btnRegister.setOnClickListener(v -> mostrarDialogRegistroEmail(null));
        btnGoogle.setOnClickListener(v -> loginGoogle());

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

    // Gestiona on start en este bloque.
    @Override
    protected void onStart() {
        super.onStart();

        boolean autoGoogle = getIntent().getBooleanExtra("AUTO_GOOGLE", false);
        if (autoGoogle) return;

        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;

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
            if (email.isEmpty()) {
                etEmail.requestFocus();
                shake(etEmail);
            }
            if (pass.isEmpty()) {
                if (!email.isEmpty()) etPassword.requestFocus();
                shake(etPassword);
            }
            setEstado("Rellena email y contrasena", ESTADO_ERROR, false);
            return;
        }

        setEstado("Iniciando sesion...", ESTADO_INFO, false);

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

    // Gestiona manejar error login email en este bloque.
    private void manejarErrorLoginEmail(Exception e, String email) {
        String code = null;
        if (e instanceof FirebaseAuthException) code = ((FirebaseAuthException) e).getErrorCode();

        if ("ERROR_INVALID_EMAIL".equals(code)) {
            etEmail.requestFocus();
            shake(etEmail);
            setEstado("Email no valido", ESTADO_ERROR, false);
            return;
        }

        if ("ERROR_USER_NOT_FOUND".equals(code) || e instanceof FirebaseAuthInvalidUserException) {
            resolverCasoPasswordGoogleONoSePuedeSaber(email, false);
            return;
        }

        if ("ERROR_WRONG_PASSWORD".equals(code)
                || "ERROR_INVALID_CREDENTIAL".equals(code)
                || "ERROR_INVALID_LOGIN_CREDENTIALS".equals(code)
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            resolverCasoPasswordGoogleONoSePuedeSaber(email, true);
            return;
        }

        setEstado("Login error: " + e.getMessage(), ESTADO_ERROR, true);
    }

    // Gestiona resolver caso password google ono se puede saber en este bloque.
    private void resolverCasoPasswordGoogleONoSePuedeSaber(String email, boolean credencialInvalida) {
        auth.fetchSignInMethodsForEmail(email)
                .addOnSuccessListener(res -> {
                    List<String> m = res.getSignInMethods();
                    if (m == null || m.isEmpty()) {
                        resolverCasoConFirestore(email, credencialInvalida);
                        return;
                    }

                    boolean tieneGoogle = m != null && m.contains(GoogleAuthProvider.PROVIDER_ID);
                    boolean tienePassword = m != null && m.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD);

                    if (tienePassword) {
                        marcarContrasenaIncorrecta();
                        return;
                    }

                    if (tieneGoogle) {
                        mostrarOpcionesCuentaGoogle(email);
                        return;
                    }

                    resolverCasoConFirestore(email, credencialInvalida);
                })
                .addOnFailureListener(x -> resolverCasoConFirestore(email, credencialInvalida));
    }

    // Gestiona resolver caso con firestore en este bloque.
    private void resolverCasoConFirestore(String email, boolean credencialInvalida) {
        db.collection(FirestoreSchema.Collections.USUARIOS)
                .whereEqualTo(FirestoreSchema.UsuarioFields.EMAIL, email)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        if (credencialInvalida) {
                            marcarContrasenaIncorrecta();
                            return;
                        }
                        mostrarOpcionesCuentaNoDetectada(email);
                        return;
                    }

                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    boolean tieneGoogle = tieneMetodoVinculado(doc, FirestoreSchema.AuthMethods.GOOGLE);
                    boolean tienePassword = tieneMetodoVinculado(doc, FirestoreSchema.AuthMethods.EMAIL)
                            || Boolean.TRUE.equals(doc.getBoolean(FirestoreSchema.UsuarioFields.TIENE_PASSWORD));

                    if (tienePassword) {
                        marcarContrasenaIncorrecta();
                        return;
                    }

                    if (tieneGoogle) {
                        mostrarOpcionesCuentaGoogle(email);
                        return;
                    }

                    if (credencialInvalida) {
                        marcarContrasenaIncorrecta();
                    } else {
                        mostrarDialogRegistroEmail(email);
                    }
                })
                .addOnFailureListener(e -> {
                    if (credencialInvalida) {
                        marcarContrasenaIncorrecta();
                    } else {
                        mostrarOpcionesCuentaNoDetectada(email);
                    }
                });
    }

    // Indica si metodo vinculado esta disponible.
    private boolean tieneMetodoVinculado(DocumentSnapshot doc, String metodo) {
        Object raw = doc.get(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS);
        if (!(raw instanceof List<?>)) return false;
        for (Object item : (List<?>) raw) {
            if (!(item instanceof String)) continue;
            if (metodo.equalsIgnoreCase(((String) item).trim())) return true;
        }
        return false;
    }

    // Muestra opciones cuenta google en la interfaz.
    private void mostrarOpcionesCuentaGoogle(String email) {
        etEmail.requestFocus();
        shake(etEmail);
        setEstado("Esta cuenta ya existe con Google. Puedes anadir contrasena desde aqui.", ESTADO_INFO, false);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Cuenta con Google")
                .setMessage("Este correo esta registrado con Google.\n\n" +
                        "Si quieres entrar por email + password, puedes anadir una contrasena ahora mismo.")
                .setPositiveButton("Anadir contrasena", (d, w) -> iniciarFlujoCrearPassword(email))
                .setNeutralButton("Entrar con Google", (d, w) -> loginGoogle())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Muestra opciones cuenta no detectada en la interfaz.
    private void mostrarOpcionesCuentaNoDetectada(String email) {
        etEmail.requestFocus();
        shake(etEmail);
        setEstado("No se pudo validar el metodo de acceso. Puedes verificar con Google y anadir contrasena.", ESTADO_INFO, false);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Verificar cuenta")
                .setMessage("Si este correo se creo con Google, entra con Google una vez para poder anadir password.\n\n" +
                        "Si no tienes cuenta, puedes crearla ahora.")
                .setPositiveButton("Anadir contrasena", (d, w) -> iniciarFlujoCrearPassword(email))
                .setNeutralButton("Crear cuenta", (d, w) -> mostrarDialogRegistroEmail(email))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Gestiona marcar contrasena incorrecta en este bloque.
    private void marcarContrasenaIncorrecta() {
        etPassword.requestFocus();
        shake(etPassword);
        setEstado("Contrasena incorrecta", ESTADO_ERROR, false);
    }

    // ===================== FLUJO CREAR PASSWORD =====================

    private void iniciarFlujoCrearPassword(String email) {
        etEmail.requestFocus();
        shake(etEmail);
        setEstado("Para crear contrasena primero verifica con Google (no cambia tu password de Google).", ESTADO_ERROR, false);

        pendingLinkEmail = email;
        pendingLinkPassword = true;

        FirebaseUser u = auth.getCurrentUser();

        if (u != null && u.getEmail() != null && u.getEmail().equalsIgnoreCase(email) && esProveedorGoogle(u)) {
            mostrarDialogCrearPasswordParaCuentaGoogle(email);
            return;
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Crear contrasena")
                .setMessage("Necesitamos que entres con Google una vez para verificar que eres el dueno del correo.\n\n"
                        + "Esto NO cambia tu password de Google, solo anade una contrasena en Firebase.")
                .setPositiveButton("Continuar con Google", (d, w) -> loginGoogle())
                .setNegativeButton("Cancelar", (d, w) -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                })
                .show();
    }

    // Indica si proveedor google.
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
                tvError.setText("Rellena email y contrasena");
                tvError.setVisibility(View.VISIBLE);
                setEstado("Faltan campos en el registro", ESTADO_ERROR, false);
                return;
            }
            if (p1.length() < 6) {
                tvError.setText("Contrasena minimo 6");
                tvError.setVisibility(View.VISIBLE);
                setEstado("Contrasena demasiado corta", ESTADO_ERROR, false);
                return;
            }
            if (!p1.equals(p2)) {
                tvError.setText("Las contrasenas no coinciden");
                tvError.setVisibility(View.VISIBLE);
                setEstado("Las contrasenas no coinciden", ESTADO_ERROR, false);
                return;
            }
            if (!cb.isChecked()) {
                tvError.setText("Debes aceptar los terminos");
                tvError.setVisibility(View.VISIBLE);
                setEstado("Debes aceptar los terminos", ESTADO_ERROR, false);
                return;
            }

            setEstado("Creando cuenta...", ESTADO_INFO, false);

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
                                    setEstado("No se pudo enviar verificacion: " + ex.getMessage(), ESTADO_ERROR, true);
                                    auth.signOut();
                                    dialog.dismiss();
                                });
                    })
                    .addOnFailureListener(ex -> {
                        if (ex instanceof FirebaseAuthUserCollisionException) {
                            tvError.setText("Ese email ya esta registrado");
                            tvError.setVisibility(View.VISIBLE);
                            setEstado("Ese email ya esta registrado. Inicia sesion.", ESTADO_ERROR, false);
                            return;
                        }
                        setEstado("Registro error: " + ex.getMessage(), ESTADO_ERROR, true);
                    });
        });
    }

    // Gestiona preparar login tras registro en este bloque.
    private void prepararLoginTrasRegistro(String email) {
        etEmail.setText(email);
        etPassword.setText("");
        limpiarFeedbackLogin();
        setEstado("Cuenta creada. Verifica el email y luego inicia sesion.", ESTADO_OK, false);
    }

    // ===================== NO VERIFICADO =====================

    private void mostrarDialogoNoVerificado(String email, String pass) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Cuenta no verificada")
                .setMessage("Tienes que verificar tu correo para entrar.\n\nQuieres reenviar el correo de verificacion?")
                .setPositiveButton("Reenviar", (d, w) -> reenviarVerificacion(email, pass))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // Gestiona reenviar verificacion en este bloque.
    private void reenviarVerificacion(String email, String pass) {
        setEstado("Reenviando verificacion...", ESTADO_INFO, false);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    FirebaseUser user = r.getUser();
                    if (user == null) {
                        setEstado("Error reenviando: user null", ESTADO_ERROR, true);
                        return;
                    }

                    user.sendEmailVerification()
                            .addOnSuccessListener(v -> setEstado("Correo reenviado. Revisa bandeja o spam.", ESTADO_OK, false))
                            .addOnFailureListener(ex -> setEstado("Error reenviando: " + ex.getMessage(), ESTADO_ERROR, true));

                    auth.signOut();
                })
                .addOnFailureListener(ex -> setEstado("No se pudo reenviar: " + ex.getMessage(), ESTADO_ERROR, true));
    }

    // ===================== GOOGLE LOGIN =====================

    private void loginGoogle() {
        int totalGoogleAccounts = contarCuentasGoogleEnDispositivo();

        // Si hay varias cuentas, forzamos selector para que no se quede pegada la ultima.
        if (totalGoogleAccounts > 1) {
            lanzarGoogleConSelectorForzado();
            return;
        }

        // Si solo hay una cuenta, intentamos entrada dinamica sin UI.
        if (totalGoogleAccounts == 1) {
            setEstado("Comprobando cuenta Google...", ESTADO_INFO, false);
            googleClient.silentSignIn()
                    .addOnSuccessListener(account -> autenticarConCuentaGoogle(account, true))
                    .addOnFailureListener(e -> lanzarGoogleNormal());
            return;
        }

        // Si no se puede detectar con fiabilidad, abrimos selector para evitar errores de cuenta.
        lanzarGoogleConSelectorForzado();
    }

    // Gestiona lanzar google normal en este bloque.
    private void lanzarGoogleNormal() {
        Intent intent = googleClient.getSignInIntent();
        googleLauncher.launch(intent);
    }

    // Gestiona lanzar google con selector forzado en este bloque.
    private void lanzarGoogleConSelectorForzado() {
        googleClient.signOut()
                .addOnCompleteListener(task -> lanzarGoogleNormal());
    }

    // Gestiona contar cuentas google en dispositivo en este bloque.
    private int contarCuentasGoogleEnDispositivo() {
        try {
            return AccountManager.get(this).getAccountsByType("com.google").length;
        } catch (SecurityException e) {
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    // Gestiona autenticar con cuenta google en este bloque.
    private void autenticarConCuentaGoogle(GoogleSignInAccount account, boolean fallbackToPickerOnError) {
        if (account == null) {
            setEstado("Google cancelado", ESTADO_INFO, false);
            return;
        }

        AuthCredential cred = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        auth.signInWithCredential(cred)
                .addOnSuccessListener(r -> onLoginOkGoogle())
                .addOnFailureListener(e -> {
                    if (fallbackToPickerOnError) {
                        // Fallback: si el token silencioso no sirve, abrimos selector.
                        lanzarGoogleConSelectorForzado();
                        return;
                    }
                    setEstado("Google error: " + e.getMessage(), ESTADO_ERROR, true);
                });
    }

    // Gestiona on login ok google en este bloque.
    private void onLoginOkGoogle() {
        FirebaseUser u = auth.getCurrentUser();

        if (pendingLinkPassword && pendingLinkEmail != null) {
            if (u == null || u.getEmail() == null || !u.getEmail().equalsIgnoreCase(pendingLinkEmail)) {
                setEstado("Elegiste otra cuenta Google. Vuelve a intentarlo.", ESTADO_ERROR, false);
                pendingLinkEmail = null;
                pendingLinkPassword = false;
                auth.signOut();
                return;
            }

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
                .setTitle("Crear contrasena")
                .setMessage("Esto NO cambia tu password de Google.\nSolo anade una contrasena en Firebase para entrar tambien con email + contrasena.")
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
                tvError.setText("Contrasena minimo 6");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            if (!p1.equals(p2)) {
                tvError.setText("Las contrasenas no coinciden");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            linkPasswordAlUsuarioActual(email, p1, dialog);
        });
    }

    // Vincula password al usuario actual con la cuenta autenticada.
    private void linkPasswordAlUsuarioActual(String email, String password, AlertDialog dialog) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            setEstado("Error: no hay sesion", ESTADO_ERROR, true);
            return;
        }

        AuthCredential cred = EmailAuthProvider.getCredential(email, password);

        u.linkWithCredential(cred)
                .addOnSuccessListener(r -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;

                    setEstado("Contrasena creada. Ya puedes entrar con email.", ESTADO_OK, false);
                    dialog.dismiss();
                    upsertUsuario(false, true, "google");
                })
                .addOnFailureListener(e -> setEstado("No se pudo crear contrasena: " + e.getMessage(), ESTADO_ERROR, true));
    }

    // ===================== FIRESTORE USUARIOS =====================

    private void upsertUsuario(boolean aceptaTerminosEnEstePaso, boolean irHome, String metodoLogin) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            setEstado("Error: usuario null", ESTADO_ERROR, true);
            return;
        }

        boolean hasGoogle = false;
        boolean hasPassword = false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if ("google.com".equals(info.getProviderId())) hasGoogle = true;
            if ("password".equals(info.getProviderId())) hasPassword = true;
        }

        List<String> metodos = new ArrayList<>();
        if (hasGoogle) metodos.add(FirestoreSchema.AuthMethods.GOOGLE);
        if (hasPassword) metodos.add(FirestoreSchema.AuthMethods.EMAIL);

        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.UsuarioFields.METODO_AUTH, metodoLogin);
        data.put(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS, metodos);
        data.put(FirestoreSchema.UsuarioFields.NOMBRE_MOSTRADO, u.getDisplayName() != null ? u.getDisplayName() : "Usuario");
        data.put(FirestoreSchema.UsuarioFields.EMAIL, u.getEmail() != null ? u.getEmail() : "");
        data.put(FirestoreSchema.UsuarioFields.TIENE_PASSWORD, hasPassword);
        data.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

        if (aceptaTerminosEnEstePaso) {
            data.put(FirestoreSchema.UsuarioFields.ACEPTA_TERMINOS, true);
            data.put(FirestoreSchema.UsuarioFields.ACEPTA_TERMINOS_EN, Timestamp.now());
        }

        String uid = u.getUid();

        db.collection(FirestoreSchema.Collections.USUARIOS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        data.put(FirestoreSchema.UsuarioFields.ROL, FirestoreSchema.UserRoles.USUARIO);
                        data.put(FirestoreSchema.UsuarioFields.BLOQUEADO, false);
                        data.put(FirestoreSchema.UsuarioFields.CREADO_EN, Timestamp.now());
                    } else {
                        if (!doc.contains(FirestoreSchema.UsuarioFields.ROL)) {
                            data.put(FirestoreSchema.UsuarioFields.ROL, FirestoreSchema.UserRoles.USUARIO);
                        }
                        if (!doc.contains(FirestoreSchema.UsuarioFields.BLOQUEADO)) {
                            data.put(FirestoreSchema.UsuarioFields.BLOQUEADO, false);
                        }
                    }

                    db.collection(FirestoreSchema.Collections.USUARIOS).document(uid)
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

    // Gestiona limpiar feedback login en este bloque.
    private void limpiarFeedbackLogin() {
        if (tvEstado != null) tvEstado.setVisibility(View.GONE);
        if (etEmail != null) etEmail.setError(null);
        if (etPassword != null) etPassword.setError(null);
    }

    // Indica si proveedor password.
    private boolean esProveedorPassword(FirebaseUser u) {
        if (u == null) return false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if ("password".equals(info.getProviderId())) return true;
        }
        return false;
    }

    // Actualiza estado con el valor recibido.
    private void setEstado(String msg, int tipo, boolean showToast) {
        Log.e("AUTH_UI", msg);
        if (tvEstado == null) return;

        tvEstado.setVisibility(View.VISIBLE);

        if (tipo == ESTADO_ERROR) {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_error);
            tvEstado.setText("[!] " + msg);
            if (showToast) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } else if (tipo == ESTADO_OK) {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_ok);
            tvEstado.setText("[OK] " + msg);
        } else {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_info);
            tvEstado.setText("[i] " + msg);
        }

        tvEstado.setAlpha(0f);
        tvEstado.animate().alpha(1f).setDuration(180).start();
    }

    // Gestiona shake en este bloque.
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
