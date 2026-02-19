package com.example.tarraco_fest.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AuthActivity:
 * - Login email/contraseña (con verificación obligatoria del email)
 * - Registro email/contraseña (dialog_registro.xml + envío verificación)
 * - Login Google
 * - Caso especial: si el email pertenece a una cuenta Google sin contraseña,
 *   permite "añadir contraseña" (sin tocar la contraseña de Google) mediante linking.
 */
public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText etEmail, etPassword;
    private TextView tvEstado;
    private TextView tvForgotPassword;
    private Button btnLogin;
    private Button btnRegister;
    private Button btnGoogle;
    private View loadingAuth;

    private GoogleSignInClient googleClient;

    private static final int ESTADO_INFO = 0;
    private static final int ESTADO_OK = 1;
    private static final int ESTADO_ERROR = 2;

    // Flujo "añadir contraseña" a una cuenta Google
    private boolean pendingLinkPassword = false;
    private String pendingLinkEmail = null;

    // ============ GOOGLE RESULT ============
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    if (result.getData() == null) {
                        setEstado("Acción cancelada.", ESTADO_INFO);
                        setAuthUiEnabled(true);
                        return;
                    }

                    GoogleSignInAccount account = GoogleSignIn
                            .getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);

                    if (account == null) {
                        setEstado("Acción cancelada.", ESTADO_INFO);
                        setAuthUiEnabled(true);
                        return;
                    }

                    AuthCredential cred = GoogleAuthProvider.getCredential(account.getIdToken(), null);

                    auth.signInWithCredential(cred)
                            .addOnSuccessListener(r -> {
                                setAuthUiEnabled(true);
                                onLoginOkGoogle();
                            })
                            .addOnFailureListener(e -> {
                                logError("Google signInWithCredential", e);
                                setEstado("No se pudo verificar con Google. Inténtalo de nuevo.", ESTADO_ERROR);
                                setAuthUiEnabled(true);
                            });

                } catch (ApiException e) {
                    logError("Google ApiException status=" + e.getStatusCode(), e);
                    setEstado("No se pudo abrir Google. Inténtalo de nuevo.", ESTADO_ERROR);
                    setAuthUiEnabled(true);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        View root = findViewById(R.id.rootAuth);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });
        auth = FirebaseAuth.getInstance();
        auth.setLanguageCode("es");
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvEstado = findViewById(R.id.tvEstado);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        loadingAuth = findViewById(R.id.loadingAuth);

        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoogle = findViewById(R.id.btnGoogle);

        // Animación UI (la tuya)
        ImageView bgNormal = findViewById(R.id.imgBgNormal);
        ImageView bgBlur   = findViewById(R.id.imgBgBlur);
        View overlay       = findViewById(R.id.overlay);
        ImageView logo     = findViewById(R.id.imgLogo);
        View content       = findViewById(R.id.authContent);

        bgBlur.setAlpha(0f);
        overlay.setAlpha(0f);
        logo.setAlpha(0f);
        logo.setTranslationY(16f);
        content.setAlpha(0f);
        content.setTranslationY(22f);
        bgNormal.postDelayed(() -> {
            bgBlur.animate().alpha(1f).setDuration(650).start();
            overlay.animate().alpha(1f).setDuration(450).start();
            logo.animate().alpha(1f).translationY(0f).setDuration(520).start();
            content.animate().alpha(1f).translationY(0f).setDuration(550).start();
        }, 180);

        // Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        btnLogin.setOnClickListener(v -> loginEmail());
        btnRegister.setOnClickListener(v -> mostrarDialogRegistroEmail());
        btnGoogle.setOnClickListener(v -> loginGoogle());
        tvForgotPassword.setOnClickListener(v -> recuperarPassword());

        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String email = normalizarEmail(etEmail.getText().toString());
                if (!email.isEmpty() && !esEmailValido(email)) {
                    etEmail.setError("Email no válido");
                }
            }
        });

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loginEmail();
                return true;
            }
            return false;
        });

        // Limpieza de feedback al escribir
        etEmail.addTextChangedListener(simpleWatcher());
        etPassword.addTextChangedListener(simpleWatcher());

        // Si venimos desde Landing para login con Google automático
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

        // Si es login por email/contraseña y no está verificado -> no entrar
        if (esProveedorPassword(u) && !u.isEmailVerified()) {
            setEstado("Verifica tu correo para continuar.", ESTADO_ERROR);
            auth.signOut();
            return;
        }

        goHome();
    }

    // ====================== LOGIN EMAIL ======================

    private void loginEmail() {
        limpiarFeedbackLogin();

        String email = normalizarEmail(etEmail.getText().toString());
        String pass  = etPassword.getText().toString();

        if (email.isEmpty() || pass.isEmpty()) {
            if (email.isEmpty()) { etEmail.setError("Introduce el email"); shake(etEmail); }
            if (pass.isEmpty())  { etPassword.setError("Introduce la contraseña"); shake(etPassword); }
            setEstado("Rellena email y contraseña.", ESTADO_ERROR);
            return;
        }

        if (!esEmailValido(email)) {
            etEmail.setError("Email no válido");
            shake(etEmail);
            setEstado("Email no válido.", ESTADO_ERROR);
            return;
        }

        ocultarTeclado(etPassword);
        setAuthUiEnabled(false);
        setEstado("Iniciando sesión…", ESTADO_INFO);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    FirebaseUser u = auth.getCurrentUser();
                    if (u == null) {
                        setEstado("Error al iniciar sesión. Inténtalo de nuevo.", ESTADO_ERROR);
                        setAuthUiEnabled(true);
                        return;
                    }

                    u.reload().addOnSuccessListener(v -> {
                        if (esProveedorPassword(u) && !u.isEmailVerified()) {
                            auth.signOut();
                            setEstado("Cuenta no verificada. Revisa tu correo.", ESTADO_ERROR);
                            mostrarDialogoNoVerificado(email, pass);
                            setAuthUiEnabled(true);
                            return;
                        }

                        upsertUsuario(false, true, FirestoreSchema.AuthMethods.EMAIL);
                    }).addOnFailureListener(e -> {
                        logError("reload user", e);
                        setEstado("No se pudo validar la cuenta. Inténtalo de nuevo.", ESTADO_ERROR);
                        setAuthUiEnabled(true);
                    });
                })
                .addOnFailureListener(e -> manejarErrorLoginEmail(e, email));
    }

    @SuppressWarnings("deprecation")
    private void manejarErrorLoginEmail(Exception e, String email) {
        setAuthUiEnabled(true);
        String code = (e instanceof FirebaseAuthException) ? ((FirebaseAuthException) e).getErrorCode() : null;

        // Email inválido
        if ("ERROR_INVALID_EMAIL".equals(code)) {
            etEmail.setError("Email no válido");
            shake(etEmail);
            setEstado("Email no válido.", ESTADO_ERROR);
            return;
        }

        // Credenciales inválidas o usuario no encontrado: comprobamos proveedores reales del email
        if (e instanceof FirebaseAuthInvalidCredentialsException
                || "ERROR_WRONG_PASSWORD".equals(code)
                || "ERROR_INVALID_CREDENTIAL".equals(code)
                || "ERROR_INVALID_LOGIN_CREDENTIALS".equals(code)
                || e instanceof FirebaseAuthInvalidUserException
                || "ERROR_USER_NOT_FOUND".equals(code)) {
            resolverMetodoAccesoParaEmail(email);
            return;
        }

        logError("loginEmail failure code=" + code, e);
        setEstado("No se pudo iniciar sesión. Inténtalo de nuevo.", ESTADO_ERROR);
    }

    @SuppressWarnings("deprecation")
    private void resolverMetodoAccesoParaEmail(String email) {
        auth.fetchSignInMethodsForEmail(email)
                .addOnSuccessListener(res -> manejarMetodosParaEmailEnLogin(email, res.getSignInMethods()))
                .addOnFailureListener(ex -> {
                    logError("fetchSignInMethodsForEmail", ex);
                    setEstado("No se pudo comprobar la cuenta. Inténtalo de nuevo.", ESTADO_ERROR);
                });
    }

    private void manejarMetodosParaEmailEnLogin(String email, List<String> metodos) {
        List<String> m = (metodos == null) ? new ArrayList<>() : metodos;
        boolean tienePassword = m.contains("password");
        boolean tieneGoogle = m.contains("google.com");

        if (tienePassword) {
            etPassword.setError("Contraseña incorrecta");
            shake(etPassword);
            setEstado("Contraseña incorrecta.", ESTADO_ERROR);
            return;
        }

        if (tieneGoogle) {
            iniciarFlujoCrearPassword(email);
            return;
        }

        if (m.isEmpty()) {
            mostrarDialogoCuentaNoExiste(email);
            return;
        }

        setEstado("Ese correo usa otro método de acceso. Prueba con su método original.", ESTADO_ERROR);
    }

    private void mostrarDialogoCuentaNoExiste(String email) {
        etEmail.setError("No hay cuenta con este correo");
        shake(etEmail);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Cuenta no encontrada")
                .setMessage("No existe una cuenta con ese correo.\n\n¿Quieres crearla ahora?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear cuenta", (d, w) -> mostrarDialogRegistroEmail(email))
                .show();
    }

    // ====================== REGISTRO EMAIL (DIALOG) ======================

    private void mostrarDialogRegistroEmail() {
        String prefill = normalizarEmail(etEmail != null ? etEmail.getText().toString() : "");
        mostrarDialogRegistroEmail(prefill);
    }

    private void mostrarDialogRegistroEmail(String emailPrefill) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_registro, null);

        EditText etEmailReg = view.findViewById(R.id.etEmailReg);
        EditText etP1       = view.findViewById(R.id.etPassReg);
        EditText etP2       = view.findViewById(R.id.etPassReg2);
        CheckBox cb         = view.findViewById(R.id.cbTerminos);
        TextView tvError    = view.findViewById(R.id.tvErrorRegistro);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setView(view)
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Crear cuenta", null)
                .create();

        if (emailPrefill != null && !emailPrefill.isEmpty()) {
            etEmailReg.setText(emailPrefill);
            etEmailReg.setSelection(emailPrefill.length());
        }

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = normalizarEmail(etEmailReg.getText().toString());
            String p1 = etP1.getText().toString();
            String p2 = etP2.getText().toString();

            tvError.setVisibility(View.GONE);

            if (email.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
                tvError.setText("Rellena email y contraseña");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            if (!esEmailValido(email)) {
                tvError.setText("Email no válido");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
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
            if (!cb.isChecked()) {
                tvError.setText("Debes aceptar los términos");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            setEstado("Creando cuenta…", ESTADO_INFO);

            auth.createUserWithEmailAndPassword(email, p1)
                    .addOnSuccessListener(r -> {
                        FirebaseUser user = r.getUser();
                        if (user == null) {
                            setEstado("Error al crear la cuenta.", ESTADO_ERROR);
                            return;
                        }

                        // Guardar perfil (no entrar aún)
                        upsertUsuario(true, false, FirestoreSchema.AuthMethods.EMAIL);

                        // Enviar verificación
                        user.sendEmailVerification()
                                .addOnSuccessListener(vv -> {
                                    // Preparar login y evitar confusión visual
                                    etEmail.setText(email);
                                    etPassword.setText("");
                                    limpiarFeedbackLogin();
                                    setEstado("Cuenta creada. Revisa tu correo y verifica la cuenta.", ESTADO_OK);

                                    // Forzar que verifique antes de entrar
                                    auth.signOut();
                                    dialog.dismiss();
                                })
                                .addOnFailureListener(ex -> {
                                    logError("sendEmailVerification", ex);
                                    setEstado("No se pudo enviar el correo de verificación.", ESTADO_ERROR);
                                    auth.signOut();
                                    dialog.dismiss();
                                });
                    })
                    .addOnFailureListener(ex -> {
                        if (ex instanceof FirebaseAuthUserCollisionException) {
                            tvError.setText("Ese email ya está registrado");
                            tvError.setVisibility(View.VISIBLE);
                            return;
                        }
                        logError("createUserWithEmailAndPassword", ex);
                        tvError.setText("No se pudo crear la cuenta");
                        tvError.setVisibility(View.VISIBLE);
                    });
        });
    }

    // ====================== NO VERIFICADO ======================

    private void mostrarDialogoNoVerificado(String email, String pass) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Cuenta no verificada")
                .setMessage("Para continuar, abre tu correo y verifica la cuenta.\n\n¿Quieres reenviar el correo de verificación?")
                .setPositiveButton("Reenviar", (d, w) -> reenviarVerificacion(email, pass))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void reenviarVerificacion(String email, String pass) {
        setEstado("Reenviando correo…", ESTADO_INFO);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    FirebaseUser user = r.getUser();
                    if (user == null) {
                        setEstado("No se pudo reenviar el correo.", ESTADO_ERROR);
                        return;
                    }

                    user.sendEmailVerification()
                            .addOnSuccessListener(v -> setEstado("Correo reenviado. Revisa bandeja y spam.", ESTADO_OK))
                            .addOnFailureListener(e -> {
                                logError("reenviar verificación", e);
                                setEstado("No se pudo reenviar el correo.", ESTADO_ERROR);
                            });

                    auth.signOut();
                })
                .addOnFailureListener(e -> {
                    logError("signIn para reenviar verificación", e);
                    setEstado("No se pudo reenviar el correo.", ESTADO_ERROR);
                });
    }

    private void recuperarPassword() {
        limpiarFeedbackLogin();

        String email = normalizarEmail(etEmail.getText().toString());
        if (email.isEmpty()) {
            etEmail.setError("Introduce tu email para recuperar la contraseña");
            etEmail.requestFocus();
            shake(etEmail);
            setEstado("Necesitamos tu email para enviar el enlace.", ESTADO_ERROR);
            return;
        }

        if (!esEmailValido(email)) {
            etEmail.setError("Email no válido");
            etEmail.requestFocus();
            shake(etEmail);
            setEstado("Revisa el formato del email.", ESTADO_ERROR);
            return;
        }

        ocultarTeclado(etEmail);
        setAuthUiEnabled(false);
        setEstado("Enviando enlace de recuperación…", ESTADO_INFO);

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v ->
                        setEstado("Te hemos enviado un enlace para restablecer la contraseña.", ESTADO_OK)
                )
                .addOnFailureListener(e -> {
                    logError("sendPasswordResetEmail", e);
                    setEstado("No se pudo enviar el enlace. Inténtalo de nuevo.", ESTADO_ERROR);
                })
                .addOnCompleteListener(t -> setAuthUiEnabled(true));
    }

    // ====================== GOOGLE LOGIN ======================

    private void loginGoogle() {
        // Para forzar selector siempre (si no quieres, elimina este signOut)
        setAuthUiEnabled(false);
        googleClient.signOut().addOnCompleteListener(t -> {
            Intent intent = googleClient.getSignInIntent();
            googleLauncher.launch(intent);
        });
    }

    private void onLoginOkGoogle() {
        FirebaseUser u = auth.getCurrentUser();

        // Si venimos del flujo "añadir contraseña"
        if (pendingLinkPassword && pendingLinkEmail != null) {
            // Seguridad: si eligió otra cuenta distinta, cancelamos
            if (u == null || u.getEmail() == null || !u.getEmail().equalsIgnoreCase(pendingLinkEmail)) {
                setEstado("Has elegido otra cuenta. Vuelve a intentarlo.", ESTADO_ERROR);
                pendingLinkEmail = null;
                pendingLinkPassword = false;
                auth.signOut();
                return;
            }

            // Abrimos directamente el XML de crear contraseña
            mostrarDialogCrearPassword(pendingLinkEmail);
            return;
        }

        setEstado("Acceso correcto.", ESTADO_OK);
        upsertUsuario(false, true, FirestoreSchema.AuthMethods.GOOGLE);
    }

    // ====================== AÑADIR CONTRASEÑA (SIN MENCIONAR TECNOLOGÍA) ======================

    /**
     * Flujo "Añadir contraseña":
     * - Si ya estás logueado con esa cuenta de Google: abre directamente el diálogo de contraseña.
     * - Si no: pide "verificación con Google" (solo para confirmar la cuenta) y luego abre el diálogo.
     */
    private void iniciarFlujoCrearPassword(String email) {
        // Feedback visual
        etEmail.setError("No se puede iniciar con contraseña con este correo");
        shake(etEmail);

        pendingLinkEmail = email;
        pendingLinkPassword = true;

        FirebaseUser u = auth.getCurrentUser();
        if (u != null && u.getEmail() != null
                && u.getEmail().equalsIgnoreCase(email)
                && esProveedorGoogle(u)) {
            // Ya está confirmado con Google -> abre directamente el diálogo de contraseña
            mostrarDialogCrearPassword(email);
            return;
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Confirmación necesaria")
                .setMessage("Para crear una nueva contraseña necesitamos que confirmes tu cuenta de Google.")
                .setPositiveButton("Confirmar con Google", (d, w) -> loginGoogle())
                .setNegativeButton("Cancelar", (d, w) -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                })
                .show();
    }

    private void mostrarDialogCrearPassword(String email) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_crear_password, null);

        EditText etP1 = view.findViewById(R.id.etPass1);
        EditText etP2 = view.findViewById(R.id.etPass2);
        TextView tvError = view.findViewById(R.id.tvError);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle("Crear contraseña")
                .setMessage("Crea una contraseña para poder iniciar sesión con email y contraseña.")
                .setView(view)
                .setNegativeButton("Cancelar", (d, w) -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                    d.dismiss();
                    if (auth.getCurrentUser() != null) upsertUsuario(false, true, FirestoreSchema.AuthMethods.GOOGLE);
                })
                .setPositiveButton("Guardar", null)
                .create();

        dialog.show();

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

            linkPasswordAlUsuarioActual(email, p1, dialog, tvError);
        });
    }

    private void linkPasswordAlUsuarioActual(String email, String password, AlertDialog dialog, TextView tvError) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            setEstado("No se pudo completar la acción. Inténtalo de nuevo.", ESTADO_ERROR);
            return;
        }

        AuthCredential cred = EmailAuthProvider.getCredential(email, password);

        u.linkWithCredential(cred)
                .addOnSuccessListener(r -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;

                    setEstado("Contraseña creada. Ya puedes entrar con email.", ESTADO_OK);
                    dialog.dismiss();

                    // Sigues logueado con Google, pero ahora también tienes email+contraseña
                    upsertUsuario(false, true, FirestoreSchema.AuthMethods.GOOGLE);
                })
                .addOnFailureListener(e -> {
                    // Si ya tenía contraseña vinculada, damos mensaje claro
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        tvError.setText("Ya existe una cuenta con ese correo.");
                        tvError.setVisibility(View.VISIBLE);
                        return;
                    }
                    logError("linkWithCredential", e);
                    tvError.setText("No se pudo guardar la contraseña");
                    tvError.setVisibility(View.VISIBLE);
                });
    }

    // ====================== FIRESTORE (usuarios/{uid}) ======================

    private void upsertUsuario(boolean aceptaTerminosEnEstePaso, boolean irHome, String metodoLogin) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            setEstado("Error de sesión. Vuelve a intentarlo.", ESTADO_ERROR);
            setAuthUiEnabled(true);
            return;
        }

        boolean hasGoogle = false, hasPassword = false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if ("google.com".equals(info.getProviderId())) hasGoogle = true;
            if ("password".equals(info.getProviderId())) hasPassword = true;
        }

        List<String> metodosVinculados = new ArrayList<>();
        if (hasGoogle) metodosVinculados.add(FirestoreSchema.AuthMethods.GOOGLE);
        if (hasPassword) metodosVinculados.add(FirestoreSchema.AuthMethods.EMAIL);

        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.UsuarioFields.METODO_AUTH, metodoLogin);
        data.put(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS, metodosVinculados);
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
                        data.put(FirestoreSchema.UsuarioFields.CREADO_EN, Timestamp.now());
                        data.put(FirestoreSchema.UsuarioFields.ROL, FirestoreSchema.UserRoles.USUARIO);
                        data.put(FirestoreSchema.UsuarioFields.BLOQUEADO, false);
                    }

                    db.collection(FirestoreSchema.Collections.USUARIOS).document(uid)
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                if (irHome) {
                                    goHome();
                                } else {
                                    setAuthUiEnabled(true);
                                }
                            })
                            .addOnFailureListener(e -> {
                                logError("Firestore set usuarios/{uid}", e);
                                setEstado("No se pudo guardar el perfil.", ESTADO_ERROR);
                                setAuthUiEnabled(true);
                            });
                })
                .addOnFailureListener(e -> {
                    logError("Firestore get usuarios/{uid}", e);
                    setEstado("No se pudo guardar el perfil.", ESTADO_ERROR);
                    setAuthUiEnabled(true);
                });
    }

    // ====================== UTIL ======================

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void limpiarFeedbackLogin() {
        if (tvEstado != null) tvEstado.setVisibility(View.GONE);
        if (etEmail != null) etEmail.setError(null);
        if (etPassword != null) etPassword.setError(null);
    }

    private TextWatcher simpleWatcher() {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { limpiarFeedbackLogin(); }
            public void afterTextChanged(Editable s) {}
        };
    }

    private void setAuthUiEnabled(boolean enabled) {
        if (btnLogin != null) btnLogin.setEnabled(enabled);
        if (btnRegister != null) btnRegister.setEnabled(enabled);
        if (btnGoogle != null) btnGoogle.setEnabled(enabled);
        if (tvForgotPassword != null) tvForgotPassword.setEnabled(enabled);
        if (etEmail != null) etEmail.setEnabled(enabled);
        if (etPassword != null) etPassword.setEnabled(enabled);
        if (loadingAuth != null) loadingAuth.setVisibility(enabled ? View.GONE : View.VISIBLE);

        float alpha = enabled ? 1f : 0.62f;
        if (btnLogin != null) btnLogin.setAlpha(alpha);
        if (btnRegister != null) btnRegister.setAlpha(alpha);
        if (btnGoogle != null) btnGoogle.setAlpha(alpha);
    }

    private void ocultarTeclado(View anchor) {
        if (anchor == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(anchor.getWindowToken(), 0);
        }
    }

    private String normalizarEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean esEmailValido(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean esProveedorPassword(FirebaseUser u) {
        if (u == null) return false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if ("password".equals(info.getProviderId())) return true;
        }
        return false;
    }

    private boolean esProveedorGoogle(FirebaseUser u) {
        if (u == null) return false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if ("google.com".equals(info.getProviderId())) return true;
        }
        return false;
    }

    private void setEstado(String msg, int tipo) {
        Log.d("AUTH_UI", msg);

        if (tvEstado == null) return;

        tvEstado.setVisibility(View.VISIBLE);

        if (tipo == ESTADO_ERROR) {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_error);
            tvEstado.setText(getString(R.string.estado_error_fmt, msg));
        } else if (tipo == ESTADO_OK) {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_ok);
            tvEstado.setText(getString(R.string.estado_ok_fmt, msg));
        } else {
            tvEstado.setBackgroundResource(R.drawable.bg_estado_info);
            tvEstado.setText(msg);
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

    private void logError(String tag, Exception e) {
        Log.e("AUTH_DEBUG", tag + " -> " + (e != null ? e.getMessage() : "null"), e);
    }
}
