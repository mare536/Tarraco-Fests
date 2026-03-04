package com.example.tarraco_fest.Activity;

import android.accounts.AccountManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.PushTokenRepository;
import com.example.tarraco_fest.Util.PasswordPolicy;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
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
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final PushTokenRepository pushTokenRepository = new PushTokenRepository();

    private static final int ESTADO_INFO = 0;
    private static final int ESTADO_OK = 1;
    private static final int ESTADO_ERROR = 2;
    private int imeBottomInsetPx = 0;

    // Flow para crear password en una cuenta Google existente.
    private boolean pendingLinkPassword = false;
    private String pendingLinkEmail = null;
    private String pendingLinkPasswordDraft = null;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    if (result.getData() == null) {
                        setEstado(R.string.auth_google_cancelled, ESTADO_INFO, false);
                        return;
                    }

                    GoogleSignInAccount account = GoogleSignIn
                            .getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);

                    if (account == null) {
                        setEstado(R.string.auth_google_cancelled, ESTADO_INFO, false);
                        return;
                    }

                    autenticarConCuentaGoogle(account, false);

                } catch (ApiException e) {
                    if (pendingLinkPassword && pendingLinkEmail != null) {
                        manejarFalloGoogleParaVincularPassword(e.getStatusCode(), e.getMessage());
                        return;
                    }
                    setEstado(R.string.auth_google_error_status_fmt, ESTADO_ERROR, true, e.getStatusCode());
                }
            });

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        configurarBordesSistema();

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvEstado = findViewById(R.id.tvEstado);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnGoogle = findViewById(R.id.btnGoogle);
        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);

        ImageView bgNormal = findViewById(R.id.imgBgNormal);
        ImageView bgBlur = findViewById(R.id.imgBgBlur);
        View overlay = findViewById(R.id.overlay);
        View content = findViewById(R.id.authContent);
        aplicarInsetSuperiorLogin(content);

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
        tvForgotPassword.setOnClickListener(v -> mostrarDialogRecuperarPassword());

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

        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) asegurarCampoVisible(v);
        });
        etPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) asegurarCampoVisible(v);
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
            setEstado(R.string.auth_unverified_check_email, ESTADO_ERROR, false);
            auth.signOut();
            return;
        }

        validarCuentaNoBloqueada(u, this::goHome);
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
            setEstado(R.string.auth_fill_email_password, ESTADO_ERROR, false);
            return;
        }

        setEstado(getString(R.string.auth_status_login_start), ESTADO_INFO, false);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    FirebaseUser u = auth.getCurrentUser();
                    if (u == null) {
                        setEstado(R.string.auth_error_user_null, ESTADO_ERROR, true);
                        return;
                    }

                    u.reload().addOnSuccessListener(v -> {
                        if (esProveedorPassword(u) && !u.isEmailVerified()) {
                            setEstado(R.string.auth_unverified_check_email_resend, ESTADO_ERROR, false);
                            auth.signOut();
                            mostrarDialogoNoVerificado(email, pass);
                            return;
                        }
                        // Defensa en cliente: bloquea acceso con password debil (ej. reseteada desde web sin policy activa).
                        if (PasswordPolicy.validate(pass) != PasswordPolicy.Result.OK) {
                            mostrarDialogActualizarPasswordObligatoria(email, pass);
                            return;
                        }
                        upsertUsuario(false, true, "email");
                    }).addOnFailureListener(e -> {
                        if (esProveedorPassword(u) && !u.isEmailVerified()) {
                            setEstado(R.string.auth_unverified_check_email_resend, ESTADO_ERROR, false);
                            auth.signOut();
                            mostrarDialogoNoVerificado(email, pass);
                            return;
                        }
                        if (PasswordPolicy.validate(pass) != PasswordPolicy.Result.OK) {
                            mostrarDialogActualizarPasswordObligatoria(email, pass);
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
            setEstado(R.string.auth_reset_email_invalid, ESTADO_ERROR, false);
            return;
        }

        if ("ERROR_USER_NOT_FOUND".equals(code) || e instanceof FirebaseAuthInvalidUserException) {
            // Mensaje neutro: evita revelar si el correo existe.
            etPassword.requestFocus();
            shake(etPassword);
            setEstado(R.string.auth_login_failed_safe, ESTADO_ERROR, false);
            mostrarOpcionesCuentaNoDetectada(email, null);
            return;
        }

        if ("ERROR_WRONG_PASSWORD".equals(code)
                || "ERROR_INVALID_CREDENTIAL".equals(code)
                || "ERROR_INVALID_LOGIN_CREDENTIALS".equals(code)
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            // Mensaje neutro: evita distinguir entre password incorrecta y cuenta inexistente.
            etPassword.requestFocus();
            shake(etPassword);
            setEstado(R.string.auth_login_failed_safe, ESTADO_ERROR, false);
            mostrarOpcionesCuentaNoDetectada(email, null);
            return;
        }

        setEstado(R.string.auth_login_error_fmt, ESTADO_ERROR, true, e.getMessage());
    }

    // Gestiona resolver caso password google ono se puede saber en este bloque.
    // Se mantiene un flujo neutro para no revelar existencia ni proveedor de la cuenta.
    private void resolverCasoPasswordGoogleONoSePuedeSaber(String email, boolean credencialInvalida) {
        etPassword.requestFocus();
        shake(etPassword);
        setEstado(R.string.auth_login_failed_safe, ESTADO_ERROR, false);
        mostrarOpcionesCuentaNoDetectada(email, null);
    }

    // Gestiona resolver caso con firestore en este bloque.
    // Evita usar esta ruta para inferir existencia/proveedor.
    private void resolverCasoConFirestore(String email, boolean credencialInvalida) {
        etPassword.requestFocus();
        shake(etPassword);
        setEstado(R.string.auth_login_failed_safe, ESTADO_ERROR, false);
        mostrarOpcionesCuentaNoDetectada(email, null);
    }

    // Indica si metodo vinculado esta disponible.
    private boolean tieneMetodoVinculado(DocumentSnapshot doc, String metodo) {
        Object raw = doc.get(FirestoreSchema.UsuarioFields.METODOS_VINCULADOS);
        if (!(raw instanceof List<?>)) return false;
        for (Object item : (List<?>) raw) {
            if (!(item instanceof String)) continue;
            String valor = ((String) item).trim();
            if (FirestoreSchema.AuthMethods.GOOGLE.equalsIgnoreCase(metodo) && esMetodoGoogle(valor)) return true;
            if (FirestoreSchema.AuthMethods.EMAIL.equalsIgnoreCase(metodo) && esMetodoPassword(valor)) return true;
            if (metodo.equalsIgnoreCase(valor)) return true;
        }
        return false;
    }

    // Compatibilidad con datos legacy: "google", "google.com", etc.
    private boolean esMetodoGoogle(String raw) {
        if (raw == null) return false;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return FirestoreSchema.AuthMethods.GOOGLE.equals(normalized)
                || GoogleAuthProvider.PROVIDER_ID.equals(normalized)
                || normalized.contains("google");
    }

    // Compatibilidad con datos legacy: "email", "password", etc.
    private boolean esMetodoPassword(String raw) {
        if (raw == null) return false;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return FirestoreSchema.AuthMethods.EMAIL.equals(normalized)
                || EmailAuthProvider.PROVIDER_ID.equals(normalized)
                || EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD.equals(normalized)
                || normalized.contains("password")
                || normalized.contains("email");
    }

    private boolean listaContieneMetodoGoogle(List<String> metodos) {
        if (metodos == null || metodos.isEmpty()) return false;
        for (String metodo : metodos) {
            if (esMetodoGoogle(metodo)) return true;
        }
        return false;
    }

    private boolean listaContieneMetodoPassword(List<String> metodos) {
        if (metodos == null || metodos.isEmpty()) return false;
        for (String metodo : metodos) {
            if (esMetodoPassword(metodo)) return true;
        }
        return false;
    }

    // Muestra opciones cuenta google en la interfaz.
    private void mostrarOpcionesCuentaGoogle(String email) {
        mostrarOpcionesCuentaGoogle(email, null);
    }

    // Muestra opciones cuenta google con posibilidad de reutilizar password ya escrito en registro.
    private void mostrarOpcionesCuentaGoogle(String email, String passwordDraft) {
        etEmail.requestFocus();
        shake(etEmail);
        setEstado(R.string.auth_account_exists_google_hint, ESTADO_INFO, false);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_google_account_feedback, null);
        TextView tvEmail = view.findViewById(R.id.tvGoogleAccountEmail);
        MaterialButton btnAddPassword = view.findViewById(R.id.btnGoogleAddPassword);
        MaterialButton btnContinueGoogle = view.findViewById(R.id.btnGoogleContinue);
        TextView tvCancel = view.findViewById(R.id.tvGoogleCancel);

        tvEmail.setText(email);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setView(view)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_glass);
            dialog.getWindow().setDimAmount(0.72f);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        btnAddPassword.setOnClickListener(v -> {
            dialog.dismiss();
            iniciarFlujoCrearPassword(email, passwordDraft, false);
        });

        btnContinueGoogle.setOnClickListener(v -> {
            dialog.dismiss();
            loginGoogle();
        });

        tvCancel.setOnClickListener(v -> dialog.dismiss());
    }

    // Muestra opciones cuenta no detectada en la interfaz.
    private void mostrarOpcionesCuentaNoDetectada(String email) {
        mostrarOpcionesCuentaNoDetectada(email, null);
    }

    // Muestra opciones cuando no se puede inferir el metodo y permite arrastrar password propuesto.
    private void mostrarOpcionesCuentaNoDetectada(String email, String passwordDraft) {
        etEmail.requestFocus();
        shake(etEmail);
        setEstado(R.string.auth_access_method_unknown_hint, ESTADO_INFO, false);

        mostrarDialogAccesoPremium(
                email,
                R.string.auth_dialog_verify_account_title,
                R.string.auth_dialog_verify_account_message,
                R.string.auth_dialog_add_password,
                R.string.auth_create_account,
                true,
                true,
                () -> iniciarFlujoCrearPassword(email, passwordDraft),
                () -> mostrarDialogRegistroEmail(email)
        );
    }

    // Muestra opciones cuando no se puede determinar el estado de una cuenta de forma segura.
    private void mostrarOpcionesCorreoNoRegistrado(String email) {
        etEmail.requestFocus();
        shake(etEmail);
        setEstado(R.string.auth_login_failed_safe, ESTADO_ERROR, false);

        mostrarDialogAccesoPremium(
                email,
                R.string.auth_dialog_email_not_registered_title,
                R.string.auth_dialog_email_not_registered_message,
                R.string.auth_create_account,
                R.string.auth_dialog_add_password,
                true,
                false,
                () -> mostrarDialogRegistroEmail(email),
                null
        );
    }

    // Dialog premium para resolver acceso desde login (cuenta existente/no detectada).
    private void mostrarDialogAccesoPremium(
            String email,
            @StringRes int titleRes,
            @StringRes int messageRes,
            @StringRes int primaryRes,
            @StringRes int secondaryRes,
            boolean showPrimary,
            boolean showSecondary,
            Runnable onPrimary,
            Runnable onSecondary
    ) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_auth_access_premium, null);
        TextView tvTitle = view.findViewById(R.id.tvAccessTitle);
        TextView tvMessage = view.findViewById(R.id.tvAccessMessage);
        TextView tvEmail = view.findViewById(R.id.tvAccessEmail);
        MaterialButton btnPrimary = view.findViewById(R.id.btnAccessPrimary);
        MaterialButton btnSecondary = view.findViewById(R.id.btnAccessSecondary);
        TextView tvCancel = view.findViewById(R.id.tvAccessCancel);

        tvTitle.setText(titleRes);
        tvMessage.setText(messageRes);
        tvEmail.setText(email);

        if (showPrimary) {
            btnPrimary.setText(primaryRes);
            btnPrimary.setVisibility(View.VISIBLE);
        } else {
            btnPrimary.setVisibility(View.GONE);
        }

        if (showSecondary) {
            btnSecondary.setText(secondaryRes);
            btnSecondary.setVisibility(View.VISIBLE);
        } else {
            btnSecondary.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setView(view)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_glass);
            dialog.getWindow().setDimAmount(0.72f);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPrimary != null) onPrimary.run();
        });
        btnSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onSecondary != null) onSecondary.run();
        });
        tvCancel.setOnClickListener(v -> dialog.dismiss());
    }

    // Gestiona marcar contrasena incorrecta en este bloque.
    private void marcarContrasenaIncorrecta() {
        etPassword.requestFocus();
        shake(etPassword);
        setEstado(R.string.auth_wrong_password, ESTADO_ERROR, false);
    }

    // ===================== FLUJO CREAR PASSWORD =====================

    private void iniciarFlujoCrearPassword(String email) {
        iniciarFlujoCrearPassword(email, null);
    }

    private void iniciarFlujoCrearPassword(String email, String passwordDraft, boolean mostrarDialogoVerificacion) {
        etEmail.requestFocus();
        shake(etEmail);
        setEstado(R.string.auth_account_exists_google_hint, ESTADO_INFO, false);

        pendingLinkEmail = email;
        pendingLinkPassword = true;
        pendingLinkPasswordDraft = passwordDraft;

        FirebaseUser u = auth.getCurrentUser();

        if (u != null && u.getEmail() != null && u.getEmail().equalsIgnoreCase(email) && esProveedorGoogle(u)) {
            mostrarDialogCrearPasswordParaCuentaGoogle(email);
            return;
        }

        if (!mostrarDialogoVerificacion) {
            // Desde "Anadir contrasena" ya hubo confirmacion visual; vamos directo a Google.
            loginGoogle();
            return;
        }

        mostrarDialogVerificacionGooglePremium(
                email,
                R.string.dialog_create_password_title,
                R.string.auth_google_verify_password_message,
                R.string.auth_account_exists_google_hint,
                R.string.auth_continue_google,
                () -> loginGoogle(),
                () -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                    pendingLinkPasswordDraft = null;
                }
        );
    }

    // Inicia flujo de crear password para cuenta Google, con posible password preescrito.
    private void iniciarFlujoCrearPassword(String email, String passwordDraft) {
        iniciarFlujoCrearPassword(email, passwordDraft, true);
    }

    private void manejarFalloGoogleParaVincularPassword(Integer statusCode, String detalle) {
        if (pendingLinkEmail == null) return;

        if (statusCode != null
                && (statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED
                || statusCode == CommonStatusCodes.CANCELED)) {
            setEstado(R.string.auth_google_cancelled, ESTADO_INFO, false);
            return;
        }

        setEstado(R.string.auth_google_link_password_unavailable_status, ESTADO_ERROR, false);
        String emailPendiente = pendingLinkEmail;
        mostrarDialogVerificacionGooglePremium(
                emailPendiente,
                R.string.auth_google_link_password_unavailable_title,
                R.string.auth_google_link_password_unavailable_message,
                R.string.auth_need_google_verify_for_password,
                R.string.auth_retry_google,
                () -> loginGoogle(),
                () -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                    pendingLinkPasswordDraft = null;
                },
                R.string.auth_set_password_link_send,
                () -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                    pendingLinkPasswordDraft = null;
                    enviarEnlaceRespaldoCrearPassword(emailPendiente);
                }
        );
    }

    // Respaldo cuando Google falla: enviar enlace de recuperacion al correo para que el usuario no quede bloqueado.
    private void enviarEnlaceRespaldoCrearPassword(String email) {
        if (email == null || email.trim().isEmpty()) {
            setEstado(R.string.auth_set_password_link_error, ESTADO_ERROR, false);
            return;
        }
        etEmail.setText(email);
        auth.sendPasswordResetEmail(email.trim())
                .addOnCompleteListener(task -> setEstado(R.string.auth_set_password_link_sent_if_exists, ESTADO_INFO, false))
                .addOnFailureListener(e -> setEstado(R.string.auth_set_password_link_error, ESTADO_ERROR, false));
    }

    // Dialog premium reutilizable para confirmar/verificar con Google antes de crear password.
    private void mostrarDialogVerificacionGooglePremium(
            String email,
            @StringRes int titleRes,
            @StringRes int messageRes,
            @StringRes int hintRes,
            @StringRes int positiveRes,
            Runnable onPositive,
            Runnable onCancel
    ) {
        mostrarDialogVerificacionGooglePremium(
                email,
                titleRes,
                messageRes,
                hintRes,
                positiveRes,
                onPositive,
                onCancel,
                0,
                null
        );
    }

    private void mostrarDialogVerificacionGooglePremium(
            String email,
            @StringRes int titleRes,
            @StringRes int messageRes,
            @StringRes int hintRes,
            @StringRes int positiveRes,
            Runnable onPositive,
            Runnable onCancel,
            @StringRes int altActionRes,
            Runnable onAltAction
    ) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_google_verify_password_premium, null);
        TextView tvTitle = view.findViewById(R.id.tvGoogleVerifyTitle);
        TextView tvMessage = view.findViewById(R.id.tvGoogleVerifyMessage);
        TextView tvHint = view.findViewById(R.id.tvGoogleVerifyHint);
        TextView tvEmail = view.findViewById(R.id.tvGoogleVerifyEmail);
        TextView tvAlt = view.findViewById(R.id.tvGoogleVerifyAlt);
        MaterialButton btnContinue = view.findViewById(R.id.btnGoogleVerifyContinue);
        MaterialButton btnCancel = view.findViewById(R.id.btnGoogleVerifyCancel);

        tvTitle.setText(titleRes);
        tvMessage.setText(messageRes);
        tvHint.setText(hintRes);
        tvEmail.setText(email);
        btnContinue.setText(positiveRes);
        if (altActionRes != 0 && onAltAction != null) {
            tvAlt.setText(altActionRes);
            tvAlt.setVisibility(View.VISIBLE);
        } else {
            tvAlt.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setView(view)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_glass);
            dialog.getWindow().setDimAmount(0.72f);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        btnContinue.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPositive != null) onPositive.run();
        });

        btnCancel.setOnClickListener(v -> {
            if (onCancel != null) onCancel.run();
            dialog.dismiss();
        });

        tvAlt.setOnClickListener(v -> {
            if (onAltAction != null) onAltAction.run();
            dialog.dismiss();
        });
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
        MaterialButton btnCancel = view.findViewById(R.id.btnRegisterCancelDialog);
        MaterialButton btnCreate = view.findViewById(R.id.btnRegisterCreateDialog);

        if (emailPrefill != null && !emailPrefill.isEmpty()) etEmailReg.setText(emailPrefill);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setView(view)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_glass);
            dialog.getWindow().setDimAmount(0.72f);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCreate.setOnClickListener(v -> {
            String email = etEmailReg.getText().toString().trim();
            String p1 = etP1.getText().toString().trim();
            String p2 = etP2.getText().toString().trim();

            tvError.setVisibility(View.GONE);

            if (email.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
                tvError.setText(R.string.auth_fill_email_password);
                tvError.setVisibility(View.VISIBLE);
                setEstado(R.string.auth_register_missing_fields, ESTADO_ERROR, false);
                return;
            }
            PasswordPolicy.Result passwordResult = PasswordPolicy.validate(p1);
            if (passwordResult != PasswordPolicy.Result.OK) {
                String msg = getString(PasswordPolicy.errorRes(passwordResult));
                tvError.setText(msg);
                tvError.setVisibility(View.VISIBLE);
                setEstado(msg, ESTADO_ERROR, false);
                return;
            }
            if (!p1.equals(p2)) {
                tvError.setText(R.string.profile_password_mismatch);
                tvError.setVisibility(View.VISIBLE);
                setEstado(R.string.profile_password_mismatch, ESTADO_ERROR, false);
                return;
            }
            if (!cb.isChecked()) {
                tvError.setText(R.string.auth_register_terms_required);
                tvError.setVisibility(View.VISIBLE);
                setEstado(R.string.auth_register_terms_required, ESTADO_ERROR, false);
                return;
            }

            btnCreate.setEnabled(false);
            btnCancel.setEnabled(false);
            setEstado(R.string.auth_register_creating, ESTADO_INFO, false);

            auth.createUserWithEmailAndPassword(email, p1)
                    .addOnSuccessListener(r -> {
                        FirebaseUser user = r.getUser();
                        if (user == null) {
                            btnCreate.setEnabled(true);
                            btnCancel.setEnabled(true);
                            setEstado(R.string.auth_register_error_user_null, ESTADO_ERROR, true);
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
                                    setEstado(R.string.auth_register_send_verification_error_fmt, ESTADO_ERROR, true, ex.getMessage());
                                    auth.signOut();
                                    dialog.dismiss();
                                });
                    })
                    .addOnFailureListener(ex -> {
                        btnCreate.setEnabled(true);
                        btnCancel.setEnabled(true);
                        if (esColisionEmailRegistrado(ex)) {
                            manejarColisionRegistro(email, p1, tvError, dialog);
                            return;
                        }
                        setEstado(R.string.auth_register_error_fmt, ESTADO_ERROR, true, ex.getMessage());
                    });
        });
    }

    // Cubre variantes de Firebase para "email ya en uso" (SDK y backend no siempre lanzan la misma subclase).
    private boolean esColisionEmailRegistrado(Exception ex) {
        if (ex instanceof FirebaseAuthUserCollisionException) return true;
        if (!(ex instanceof FirebaseAuthException)) return false;
        String code = ((FirebaseAuthException) ex).getErrorCode();
        return "ERROR_EMAIL_ALREADY_IN_USE".equals(code)
                || "EMAIL_EXISTS".equals(code);
    }

    // Si el correo ya existe, detecta si es cuenta Google sin password y ofrece crear password en el mismo flujo.
    private void manejarColisionRegistro(String email, String passwordDraft, TextView tvError, AlertDialog dialogRegistro) {
        auth.fetchSignInMethodsForEmail(email)
                .addOnSuccessListener(res -> {
                    List<String> metodos = res == null ? null : res.getSignInMethods();
                    boolean tieneGoogle = listaContieneMetodoGoogle(metodos);
                    boolean tienePassword = listaContieneMetodoPassword(metodos);

                    // Solo abre el flujo especial cuando la colision es claramente cuenta Google sin password.
                    if (tieneGoogle && !tienePassword) {
                        mostrarOpcionesCuentaGoogleTrasCerrarRegistro(dialogRegistro, email, passwordDraft);
                        return;
                    }

                    if (metodos == null || metodos.isEmpty()) {
                        // Algunos proyectos tienen EEP activa y aqui no llegan metodos: usamos Firestore como respaldo.
                        manejarColisionRegistroConFirestore(email, passwordDraft, tvError, dialogRegistro);
                        return;
                    }

                    // Resto de casos: error directo en el formulario para evitar ciclos de registro.
                    tvError.setText(R.string.auth_register_email_exists_short);
                    tvError.setVisibility(View.VISIBLE);
                    setEstado(R.string.auth_register_email_exists_login, ESTADO_ERROR, false);
                    mostrarOpcionesCuentaGoogleTrasCerrarRegistro(dialogRegistro, email, passwordDraft);
                })
                .addOnFailureListener(fetchEx -> manejarColisionRegistroConFirestore(email, passwordDraft, tvError, dialogRegistro));
    }

    // Cierra el dialogo de registro y abre despues el alert premium para evitar conflictos de ventanas.
    private void mostrarOpcionesCuentaGoogleTrasCerrarRegistro(AlertDialog dialogRegistro, String email, String passwordDraft) {
        Runnable abrir = () -> {
            if (isFinishing() || isDestroyed()) return;
            etEmail.setText(email);
            etEmail.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                Log.e("AUTH_UI", "Showing premium existing-account dialog");
                mostrarOpcionesCuentaGoogle(email, passwordDraft);
            });
        };

        if (dialogRegistro != null && dialogRegistro.isShowing()) {
            dialogRegistro.setOnDismissListener(d -> abrir.run());
            dialogRegistro.dismiss();
            return;
        }
        abrir.run();
    }

    // Fallback para colision de registro cuando Firebase no devuelve metodos de acceso.
    private void manejarColisionRegistroConFirestore(String email, String passwordDraft, TextView tvError, AlertDialog dialogRegistro) {
        db.collection(FirestoreSchema.Collections.USUARIOS)
                .whereEqualTo(FirestoreSchema.UsuarioFields.EMAIL, email)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        DocumentSnapshot doc = snapshot.getDocuments().get(0);
                        boolean tieneGoogle = tieneMetodoVinculado(doc, FirestoreSchema.AuthMethods.GOOGLE);
                        String metodoAuth = doc.getString(FirestoreSchema.UsuarioFields.METODO_AUTH);
                        boolean metodoPrincipalGoogle = esMetodoGoogle(metodoAuth);
                        boolean tienePassword = tieneMetodoVinculado(doc, FirestoreSchema.AuthMethods.EMAIL)
                                || Boolean.TRUE.equals(doc.getBoolean(FirestoreSchema.UsuarioFields.TIENE_PASSWORD));

                        // Mismo criterio que en FirebaseAuth: solo caso Google sin password.
                        // Respaldo adicional: cuentas antiguas pueden no tener metodosVinculados,
                        // pero si metodoAuth=google seguimos mostrando la opcion especial.
                        if ((tieneGoogle || metodoPrincipalGoogle) && !tienePassword) {
                            mostrarOpcionesCuentaGoogleTrasCerrarRegistro(dialogRegistro, email, passwordDraft);
                            return;
                        }
                    }

                    tvError.setText(R.string.auth_register_email_exists_short);
                    tvError.setVisibility(View.VISIBLE);
                    setEstado(R.string.auth_register_email_exists_login, ESTADO_ERROR, false);
                    mostrarOpcionesCuentaGoogleTrasCerrarRegistro(dialogRegistro, email, passwordDraft);
                })
                .addOnFailureListener(e -> {
                    tvError.setText(R.string.auth_register_email_exists_short);
                    tvError.setVisibility(View.VISIBLE);
                    setEstado(R.string.auth_register_email_exists_login, ESTADO_ERROR, false);

                    // Con error de lectura de Firestore no perdemos el flujo UX: ofrecemos igualmente asignar password.
                    mostrarOpcionesCuentaGoogleTrasCerrarRegistro(dialogRegistro, email, passwordDraft);
                });
    }

    // Gestiona preparar login tras registro en este bloque.
    private void prepararLoginTrasRegistro(String email) {
        etEmail.setText(email);
        etPassword.setText("");
        limpiarFeedbackLogin();
        setEstado(R.string.auth_register_created_verify_then_login, ESTADO_OK, false);
    }

    // ===================== NO VERIFICADO =====================

    private void mostrarDialogoNoVerificado(String email, String pass) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle(R.string.auth_dialog_unverified_title)
                .setMessage(R.string.auth_dialog_unverified_message)
                .setPositiveButton(R.string.auth_dialog_resend, (d, w) -> reenviarVerificacion(email, pass))
                .setNegativeButton(R.string.auth_dialog_close, null)
                .show();
    }

    // Muestra dialogo para recuperar contrasena por email.
    private void mostrarDialogRecuperarPassword() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);

        EditText etEmailRecovery = view.findViewById(R.id.etForgotPasswordEmail);
        TextInputLayout tilEmailRecovery = view.findViewById(R.id.tilForgotPasswordEmail);
        TextView tvErrorRecovery = view.findViewById(R.id.tvForgotPasswordError);

        String emailActual = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();
        if (!emailActual.isEmpty()) {
            etEmailRecovery.setText(emailActual);
            etEmailRecovery.setSelection(emailActual.length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_ResetDialog)
                .setView(view)
                .setNegativeButton(R.string.detail_cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.auth_reset_send, null)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_festival);
            dialog.getWindow().setDimAmount(0.72f);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        Button btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (btnPositive != null) btnPositive.setAllCaps(false);
        if (btnNegative != null) btnNegative.setAllCaps(false);

        if (btnPositive == null) return;
        btnPositive.setOnClickListener(v -> {
            String email = etEmailRecovery.getText() == null
                    ? ""
                    : etEmailRecovery.getText().toString().trim();

            tilEmailRecovery.setError(null);
            tvErrorRecovery.setVisibility(View.GONE);

            if (TextUtils.isEmpty(email)) {
                tilEmailRecovery.setError(getString(R.string.auth_reset_email_required));
                etEmailRecovery.requestFocus();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmailRecovery.setError(getString(R.string.auth_reset_email_invalid));
                etEmailRecovery.requestFocus();
                return;
            }

            btnPositive.setEnabled(false);
            if (btnNegative != null) btnNegative.setEnabled(false);

            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        // Respuesta neutra anti-enumeracion: no revelar si el correo existe.
                        etEmail.setText(email);
                        dialog.dismiss();
                        setEstado(getString(R.string.auth_reset_sent_if_exists), ESTADO_OK, false);
                    });
        });
    }

    // Gestiona reenviar verificacion en este bloque.
    private void reenviarVerificacion(String email, String pass) {
        setEstado(R.string.auth_resend_verification_sending, ESTADO_INFO, false);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    FirebaseUser user = r.getUser();
                    if (user == null) {
                        setEstado(R.string.auth_resend_error_user_null, ESTADO_ERROR, true);
                        return;
                    }

                    user.sendEmailVerification()
                            .addOnSuccessListener(v -> setEstado(R.string.auth_resend_ok_check_inbox, ESTADO_OK, false))
                            .addOnFailureListener(ex -> setEstado(R.string.auth_resend_error_fmt, ESTADO_ERROR, true, ex.getMessage()));

                    auth.signOut();
                })
                .addOnFailureListener(ex -> setEstado(R.string.auth_resend_failed_fmt, ESTADO_ERROR, true, ex.getMessage()));
    }

    // Obliga a actualizar password debil antes de permitir acceso a la app.
    private void mostrarDialogActualizarPasswordObligatoria(String email, String passwordActual) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_crear_password, null);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvInfo = view.findViewById(R.id.tvInfo);
        EditText etP1 = view.findViewById(R.id.etPass1);
        EditText etP2 = view.findViewById(R.id.etPass2);
        TextView tvError = view.findViewById(R.id.tvError);

        tvTitle.setText(R.string.auth_upgrade_password_title);
        tvInfo.setText(R.string.auth_upgrade_password_message);
        tvError.setVisibility(View.GONE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setView(view)
                // No permitir omitir este paso y entrar con clave debil.
                .setCancelable(false)
                .setNegativeButton(R.string.auth_upgrade_password_logout, (d, w) -> {
                    auth.signOut();
                    setEstado(R.string.auth_upgrade_password_logout_done, ESTADO_INFO, false);
                    d.dismiss();
                })
                .setPositiveButton(R.string.auth_upgrade_password_action, null)
                .create();

        dialog.show();
        dialog.setCanceledOnTouchOutside(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_festival);
            dialog.getWindow().setDimAmount(0.86f);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        Button btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btnPositive == null) return;

        btnPositive.setOnClickListener(v -> {
            String p1 = etP1.getText() == null ? "" : etP1.getText().toString().trim();
            String p2 = etP2.getText() == null ? "" : etP2.getText().toString().trim();

            tvError.setVisibility(View.GONE);

            PasswordPolicy.Result passwordResult = PasswordPolicy.validate(p1);
            if (passwordResult != PasswordPolicy.Result.OK) {
                tvError.setText(getString(PasswordPolicy.errorRes(passwordResult)));
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            if (p1.equals(passwordActual)) {
                tvError.setText(R.string.auth_upgrade_password_different_required);
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            if (!p1.equals(p2)) {
                tvError.setText(R.string.profile_password_mismatch);
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                tvError.setText(R.string.auth_error_no_session);
                tvError.setVisibility(View.VISIBLE);
                auth.signOut();
                dialog.dismiss();
                return;
            }

            btnPositive.setEnabled(false);
            user.updatePassword(p1)
                    .addOnSuccessListener(unused -> {
                        // Tras reforzar la password, ya permitimos continuar al home.
                        dialog.dismiss();
                        setEstado(R.string.auth_upgrade_password_success, ESTADO_OK, false);
                        upsertUsuario(false, true, "email");
                    })
                    .addOnFailureListener(e -> {
                        btnPositive.setEnabled(true);
                        tvError.setText(getString(R.string.auth_password_create_failed_fmt, e.getMessage()));
                        tvError.setVisibility(View.VISIBLE);
                    });
        });
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
            setEstado(getString(R.string.auth_status_google_checking), ESTADO_INFO, false);
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
            setEstado(R.string.auth_google_cancelled, ESTADO_INFO, false);
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
                    if (pendingLinkPassword && pendingLinkEmail != null) {
                        manejarFalloGoogleParaVincularPassword(null, e.getMessage());
                        return;
                    }
                    setEstado(R.string.auth_google_error_fmt, ESTADO_ERROR, true, e.getMessage());
                });
    }

    // Gestiona on login ok google en este bloque.
    private void onLoginOkGoogle() {
        FirebaseUser u = auth.getCurrentUser();

        if (pendingLinkPassword && pendingLinkEmail != null) {
            if (u == null || u.getEmail() == null || !u.getEmail().equalsIgnoreCase(pendingLinkEmail)) {
                setEstado(R.string.auth_google_other_account_selected, ESTADO_ERROR, false);
                pendingLinkEmail = null;
                pendingLinkPassword = false;
                pendingLinkPasswordDraft = null;
                auth.signOut();
                return;
            }

            mostrarDialogCrearPasswordParaCuentaGoogle(pendingLinkEmail);
            return;
        }

        setEstado(getString(R.string.auth_status_google_login_ok), ESTADO_OK, false);
        upsertUsuario(false, true, "google");
    }

    // ===================== CREAR PASSWORD (Firebase) =====================

    private void mostrarDialogCrearPasswordParaCuentaGoogle(String email) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_crear_password, null);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvInfo = view.findViewById(R.id.tvInfo);
        EditText etP1 = view.findViewById(R.id.etPass1);
        EditText etP2 = view.findViewById(R.id.etPass2);
        TextView tvError = view.findViewById(R.id.tvError);
        tvTitle.setText(R.string.dialog_create_password_title);
        tvInfo.setText(R.string.auth_link_password_message);
        if (pendingLinkPasswordDraft != null && !pendingLinkPasswordDraft.isEmpty()) {
            etP1.setText(pendingLinkPasswordDraft);
            etP2.setText(pendingLinkPasswordDraft);
            etP2.setSelection(etP2.getText() == null ? 0 : etP2.getText().length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setView(view)
                .setNegativeButton(R.string.detail_cancel, (d, w) -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                    pendingLinkPasswordDraft = null;
                    d.dismiss();
                    auth.signOut();
                    setEstado(R.string.auth_link_password_cancelled, ESTADO_INFO, false);
                })
                .setPositiveButton(R.string.admin_save, null)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_festival);
            dialog.getWindow().setDimAmount(0.86f);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p1 = etP1.getText().toString().trim();
            String p2 = etP2.getText().toString().trim();

            tvError.setVisibility(View.GONE);

            PasswordPolicy.Result passwordResult = PasswordPolicy.validate(p1);
            if (passwordResult != PasswordPolicy.Result.OK) {
                tvError.setText(getString(PasswordPolicy.errorRes(passwordResult)));
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            if (!p1.equals(p2)) {
                tvError.setText(R.string.profile_password_mismatch);
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
            setEstado(R.string.auth_error_no_session, ESTADO_ERROR, true);
            return;
        }

        AuthCredential cred = EmailAuthProvider.getCredential(email, password);

        u.linkWithCredential(cred)
                .addOnSuccessListener(r -> {
                    pendingLinkEmail = null;
                    pendingLinkPassword = false;
                    pendingLinkPasswordDraft = null;

                    setEstado(R.string.auth_password_created_login_email, ESTADO_OK, false);
                    dialog.dismiss();
                    upsertUsuario(false, true, "google");
                })
                .addOnFailureListener(e -> setEstado(R.string.auth_password_create_failed_fmt, ESTADO_ERROR, true, e.getMessage()));
    }

    // ===================== FIRESTORE USUARIOS =====================

    private void upsertUsuario(boolean aceptaTerminosEnEstePaso, boolean irHome, String metodoLogin) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            setEstado(R.string.auth_error_user_null, ESTADO_ERROR, true);
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
        data.put(FirestoreSchema.UsuarioFields.NOMBRE_MOSTRADO, u.getDisplayName() != null ? u.getDisplayName() : getString(R.string.common_user_default_name));
        data.put(FirestoreSchema.UsuarioFields.EMAIL, u.getEmail() != null ? u.getEmail() : "");
        data.put(FirestoreSchema.UsuarioFields.TIENE_PASSWORD, hasPassword);
        data.put(FirestoreSchema.UsuarioFields.UPDATED_AT, Timestamp.now());

        if (aceptaTerminosEnEstePaso) {
            data.put(FirestoreSchema.UsuarioFields.ACEPTA_TERMINOS, true);
            data.put(FirestoreSchema.UsuarioFields.ACEPTA_TERMINOS_EN, Timestamp.now());
        }

        String uid = u.getUid();

        db.collection(FirestoreSchema.Collections.USUARIOS).document(uid).get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    boolean cuentaBloqueada = doc.exists()
                            && Boolean.TRUE.equals(doc.getBoolean(FirestoreSchema.UsuarioFields.BLOQUEADO));
                    boolean cuentaEliminada = doc.exists()
                            && Boolean.TRUE.equals(doc.getBoolean(FirestoreSchema.UsuarioFields.ELIMINADO));
                    if (cuentaBloqueada || cuentaEliminada) {
                        manejarCuentaBloqueada();
                        return;
                    }

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
                                pushTokenRepository.sincronizarTokenUsuarioActual();
                                if (irHome) {
                                    setEstado(getString(R.string.auth_status_access_ok), ESTADO_OK, false);
                                    goHome();
                                } else {
                                    setEstado(R.string.auth_account_created_pending_verification, ESTADO_INFO, false);
                                }
                            })
                            .addOnFailureListener(e -> setEstado(R.string.auth_firestore_users_error_fmt, ESTADO_ERROR, true, e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    auth.signOut();
                    setEstado(R.string.auth_validation_error_fmt, ESTADO_ERROR, true, e.getMessage());
                });
    }

    // Valida cuenta no bloqueada antes de entrar en la app.
    private void validarCuentaNoBloqueada(FirebaseUser user, Runnable onAllowed) {
        if (user == null) return;

        db.collection(FirestoreSchema.Collections.USUARIOS)
                .document(user.getUid())
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    boolean cuentaBloqueada = doc.exists()
                            && Boolean.TRUE.equals(doc.getBoolean(FirestoreSchema.UsuarioFields.BLOQUEADO));
                    boolean cuentaEliminada = doc.exists()
                            && Boolean.TRUE.equals(doc.getBoolean(FirestoreSchema.UsuarioFields.ELIMINADO));
                    if (cuentaBloqueada || cuentaEliminada) {
                        manejarCuentaBloqueada();
                        return;
                    }
                    onAllowed.run();
                })
                .addOnFailureListener(e -> {
                    auth.signOut();
                    setEstado(getString(R.string.auth_account_validation_failed), ESTADO_ERROR, true);
                });
    }

    // Gestiona bloqueo de cuenta cerrando la sesion activa.
    private void manejarCuentaBloqueada() {
        pendingLinkEmail = null;
        pendingLinkPassword = false;
        pendingLinkPasswordDraft = null;
        auth.signOut();
        setEstado(getString(R.string.auth_account_blocked), ESTADO_ERROR, true);
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

    // Helper para publicar estado leyendo string traducible con formato opcional.
    private void setEstado(@StringRes int messageRes, int tipo, boolean showToast, Object... args) {
        String msg = args == null || args.length == 0
                ? getString(messageRes)
                : getString(messageRes, args);
        setEstado(msg, tipo, showToast);
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

    // Aplica inset superior para evitar que el logo se solape con notch/camara.
    private void aplicarInsetSuperiorLogin(View content) {
        if (content == null) return;

        final int baseTop = content.getPaddingTop();
        final int baseBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            imeBottomInsetPx = ime.bottom;
            int safeBottom = Math.max(bars.bottom, ime.bottom);
            int extraTop = dpToPx(8);
            v.setPadding(
                    v.getPaddingLeft(),
                    baseTop + bars.top + extraTop,
                    v.getPaddingRight(),
                    baseBottom + safeBottom
            );

            if (ime.bottom > 0) {
                View focused = getCurrentFocus();
                if (focused != null) asegurarCampoVisible(focused);
            }
            return insets;
        });

        ViewCompat.requestApplyInsets(content);
    }

    // Asegura que el campo enfocado quede visible por encima del teclado.
    private void asegurarCampoVisible(View focused) {
        if (focused == null) return;
        View scrollCandidate = findViewById(R.id.authContent);
        if (!(scrollCandidate instanceof NestedScrollView)) return;
        NestedScrollView scroll = (NestedScrollView) scrollCandidate;
        View target = obtenerBloqueCampo(focused);

        Runnable makeVisible = () -> {
            Rect rect = new Rect();
            target.getDrawingRect(rect);
            scroll.offsetDescendantRectToMyCoords(target, rect);

            int scrollY = scroll.getScrollY();
            int visibleTop = scrollY + scroll.getPaddingTop() + dpToPx(8);
            int margenInferior = imeBottomInsetPx > 0 ? dpToPx(56) : dpToPx(16);
            int visibleBottom = scrollY + scroll.getHeight() - scroll.getPaddingBottom() - margenInferior;

            int dy = 0;
            if (rect.bottom > visibleBottom) {
                dy = rect.bottom - visibleBottom;
            } else if (rect.top < visibleTop) {
                dy = rect.top - visibleTop;
            }
            if (dy != 0) {
                scroll.smoothScrollBy(0, dy);
            }
        };

        scroll.postDelayed(makeVisible, 90);
        scroll.postDelayed(makeVisible, 230);
    }

    // Busca el TextInputLayout padre para asegurar visibilidad del bloque completo.
    private View obtenerBloqueCampo(View focused) {
        View current = focused;
        while (current != null) {
            if (current instanceof TextInputLayout) return current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return focused;
    }

    // Aplica edge-to-edge para usar todo el fondo sin franjas blancas en barras del sistema.
    private void configurarBordesSistema() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        boolean modoOscuro =
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(!modoOscuro);
            controller.setAppearanceLightNavigationBars(!modoOscuro);
        }
    }

    // Convierte dp a px para ajustes finos de layout.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
