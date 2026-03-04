package com.example.tarraco_fest.Activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.tarraco_fest.Modelo.UsuarioPerfil;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.PushTokenRepository;
import com.example.tarraco_fest.Repository.UsuarioRepository;
import com.example.tarraco_fest.Util.PasswordPolicy;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Pantalla de perfil de usuario organizada por secciones.
 * Permite editar informacion personal y gestionar metodos de acceso.
 */
public class PerfilActivity extends AppCompatActivity {

    private static final int SEC_INFO = 0;
    private static final int SEC_SEGURIDAD = 1;
    private static final int SEC_EDITAR = 2;

    private final UsuarioRepository usuarioRepository = new UsuarioRepository();
    private final PushTokenRepository pushTokenRepository = new PushTokenRepository();

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private android.view.View perfilContent;
    private android.view.View sectionInfoGeneral;
    private android.view.View sectionSeguridad;
    private android.view.View sectionEditarDatos;
    private android.view.View layoutVincularPassword;

    private TextView tvInfoEmail;
    private TextView tvInfoNombre;
    private TextView tvInfoTelefono;
    private TextView tvInfoCiudad;
    private TextView tvInfoBiografia;
    private TextView tvSeguridadMetodo;
    private TextView tvSeguridadGoogleEstado;
    private TextView tvSeguridadPasswordEstado;

    private TextInputEditText etNombre;
    private TextInputEditText etTelefono;
    private TextInputEditText etCiudad;
    private TextInputEditText etBiografia;
    private TextInputEditText etPass1;
    private TextInputEditText etPass2;

    private MaterialButton btnGuardarNombre;
    private MaterialButton btnVincularGoogle;
    private MaterialButton btnVincularPassword;
    private MaterialButton btnCambiarPassword;

    private UsuarioPerfil perfilActual;
    private GoogleSignInClient googleClient;

    private final ActivityResultLauncher<Intent> googleLinkLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    btnVincularGoogle.setEnabled(true);

                    if (result.getData() == null) {
                        Toast.makeText(this, getString(R.string.profile_google_link_cancelled), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    GoogleSignInAccount account = GoogleSignIn
                            .getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);

                    if (account == null) {
                        Toast.makeText(this, getString(R.string.profile_google_link_error), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) {
                        Toast.makeText(this, getString(R.string.profile_load_error), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                    user.linkWithCredential(credential)
                            .addOnSuccessListener(r -> usuarioRepository.sincronizarMetodosAutenticacion(new UsuarioRepository.ActionCallback() {
                                // Gestiona on ok en este bloque.
                                @Override
                                public void onOk() {
                                    Toast.makeText(PerfilActivity.this, getString(R.string.profile_google_link_ok), Toast.LENGTH_SHORT).show();
                                    cargarPerfil();
                                }

                                // Gestiona on error en este bloque.
                                @Override
                                public void onError(Exception e) {
                                    Toast.makeText(PerfilActivity.this, getString(R.string.profile_google_link_error), Toast.LENGTH_LONG).show();
                                }
                            }))
                            .addOnFailureListener(e -> {
                                if (e instanceof FirebaseAuthUserCollisionException) {
                                    Toast.makeText(this, getString(R.string.profile_google_link_collision), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                Toast.makeText(this, getString(R.string.profile_google_link_error), Toast.LENGTH_LONG).show();
                            });

                } catch (ApiException e) {
                    if (e.getStatusCode() == CommonStatusCodes.CANCELED
                            || e.getStatusCode() == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                        Toast.makeText(this, getString(R.string.profile_google_link_cancelled), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, getString(R.string.profile_google_link_error), Toast.LENGTH_SHORT).show();
                }
            });

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);
        configurarBordesSistema();

        bindViews();
        aplicarInsetsSistema();
        configurarGoogle();
        configurarDrawer();
        configurarAcciones();
        mostrarSeccion(SEC_INFO);
        cargarPerfil();
    }

    // Aplica edge-to-edge para integrar fondo y barras del sistema sin bordes blancos.
    private void configurarBordesSistema() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    // Gestiona bind views en este bloque.
    private void bindViews() {
        drawerLayout = findViewById(R.id.drawerPerfil);
        navigationView = findViewById(R.id.navPerfil);

        perfilContent = findViewById(R.id.perfilContent);
        sectionInfoGeneral = findViewById(R.id.sectionInfoGeneral);
        sectionSeguridad = findViewById(R.id.sectionSeguridad);
        sectionEditarDatos = findViewById(R.id.sectionEditarDatos);
        layoutVincularPassword = findViewById(R.id.layoutVincularPassword);

        tvInfoEmail = findViewById(R.id.tvInfoEmail);
        tvInfoNombre = findViewById(R.id.tvInfoNombre);
        tvInfoTelefono = findViewById(R.id.tvInfoTelefono);
        tvInfoCiudad = findViewById(R.id.tvInfoCiudad);
        tvInfoBiografia = findViewById(R.id.tvInfoBiografia);
        tvSeguridadMetodo = findViewById(R.id.tvSeguridadMetodo);
        tvSeguridadGoogleEstado = findViewById(R.id.tvSeguridadGoogleEstado);
        tvSeguridadPasswordEstado = findViewById(R.id.tvSeguridadPasswordEstado);

        etNombre = findViewById(R.id.etNombre);
        etTelefono = findViewById(R.id.etTelefono);
        etCiudad = findViewById(R.id.etCiudad);
        etBiografia = findViewById(R.id.etBiografia);
        etPass1 = findViewById(R.id.etPass1);
        etPass2 = findViewById(R.id.etPass2);

        btnGuardarNombre = findViewById(R.id.btnGuardarNombre);
        btnVincularGoogle = findViewById(R.id.btnVincularGoogle);
        btnVincularPassword = findViewById(R.id.btnVincularPassword);
        btnCambiarPassword = findViewById(R.id.btnCambiarPassword);
    }

    // Configura google segun el contexto actual.
    private void configurarGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);
    }

    // Aplica insets sistema respetando el estado actual.
    private void aplicarInsetsSistema() {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) perfilContent.getLayoutParams();
        final int baseTop = lp.topMargin;
        final int baseBottom = lp.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(perfilContent, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int safeBottom = Math.max(systemBars.bottom, ime.bottom);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.topMargin = baseTop + systemBars.top;
            params.bottomMargin = baseBottom + safeBottom;
            v.setLayoutParams(params);
            return insets;
        });

        ViewCompat.requestApplyInsets(perfilContent);
    }

    // Configura drawer segun el contexto actual.
    private void configurarDrawer() {
        findViewById(R.id.btnPerfilMenu).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setCheckedItem(R.id.nav_info_general);
        navigationView.setNavigationItemSelectedListener(item -> {
            boolean handled = manejarClickDrawer(item.getItemId());
            if (handled) drawerLayout.closeDrawer(GravityCompat.START);
            return handled;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            // Gestiona handle on back pressed en este bloque.
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                setEnabled(false);
                PerfilActivity.super.onBackPressed();
            }
        });
    }

    // Gestiona manejar click drawer en este bloque.
    private boolean manejarClickDrawer(int itemId) {
        if (itemId == R.id.nav_inicio) {
            volverAInicio();
            return true;
        }
        if (itemId == R.id.nav_info_general) {
            mostrarSeccion(SEC_INFO);
            navigationView.setCheckedItem(R.id.nav_info_general);
            return true;
        }
        if (itemId == R.id.nav_seguridad) {
            mostrarSeccion(SEC_SEGURIDAD);
            navigationView.setCheckedItem(R.id.nav_seguridad);
            return true;
        }
        if (itemId == R.id.nav_editar_datos) {
            mostrarSeccion(SEC_EDITAR);
            navigationView.setCheckedItem(R.id.nav_editar_datos);
            return true;
        }
        if (itemId == R.id.nav_cerrar_sesion) {
            cerrarSesion();
            return true;
        }
        return false;
    }

    // Configura acciones segun el contexto actual.
    private void configurarAcciones() {
        btnGuardarNombre.setOnClickListener(v -> guardarCambiosPerfil());
        btnVincularGoogle.setOnClickListener(v -> vincularGoogle());
        btnVincularPassword.setOnClickListener(v -> vincularPassword());
        btnCambiarPassword.setOnClickListener(v -> cambiarPassword());
    }

    // Carga perfil desde la fuente correspondiente.
    private void cargarPerfil() {
        usuarioRepository.cargarPerfilActual(new UsuarioRepository.PerfilCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk(UsuarioPerfil perfil) {
                perfilActual = perfil;
                renderizarPerfil(perfil);
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(PerfilActivity.this, getString(R.string.profile_load_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Gestiona renderizar perfil en este bloque.
    private void renderizarPerfil(UsuarioPerfil perfil) {
        String noEstablecida = getString(R.string.profile_not_set);

        String nombre = perfil.getNombreMostrado();
        if (esNombrePredeterminado(nombre)) nombre = "";

        tvInfoEmail.setText(valorONoEstablecida(perfil.getEmail(), noEstablecida));
        tvInfoNombre.setText(valorONoEstablecida(nombre, noEstablecida));
        tvInfoTelefono.setText(valorONoEstablecida(perfil.getTelefono(), noEstablecida));
        tvInfoCiudad.setText(valorONoEstablecida(perfil.getCiudad(), noEstablecida));
        tvInfoBiografia.setText(valorONoEstablecida(perfil.getBiografia(), noEstablecida));

        tvSeguridadMetodo.setText(obtenerMetodoAcceso(perfil, noEstablecida));
        if (perfil.tieneMetodoGoogle()) {
            tvSeguridadGoogleEstado.setText(getString(R.string.profile_google_linked));
            btnVincularGoogle.setEnabled(false);
            btnVincularGoogle.setText(getString(R.string.profile_google_linked_button));
        } else {
            tvSeguridadGoogleEstado.setText(getString(R.string.profile_google_not_linked));
            btnVincularGoogle.setEnabled(true);
            btnVincularGoogle.setText(getString(R.string.profile_google_link_button));
        }

        if (perfil.tieneMetodoEmail()) {
            tvSeguridadPasswordEstado.setText(getString(R.string.profile_password_linked));
            layoutVincularPassword.setVisibility(android.view.View.GONE);
            btnCambiarPassword.setVisibility(android.view.View.VISIBLE);
            btnCambiarPassword.setEnabled(true);
        } else {
            tvSeguridadPasswordEstado.setText(getString(R.string.profile_password_not_linked));
            layoutVincularPassword.setVisibility(android.view.View.VISIBLE);
            btnCambiarPassword.setVisibility(android.view.View.GONE);
            btnVincularPassword.setEnabled(true);
            btnVincularPassword.setText(getString(R.string.profile_security_add_password));
        }

        etNombre.setText(nombre);
        etTelefono.setText(perfil.getTelefono());
        etCiudad.setText(perfil.getCiudad());
        etBiografia.setText(perfil.getBiografia());

        etPass1.setText("");
        etPass2.setText("");
    }

    // Vincula google con la cuenta autenticada.
    private void vincularGoogle() {
        if (perfilActual != null && perfilActual.tieneMetodoGoogle()) {
            Toast.makeText(this, getString(R.string.profile_google_linked_button), Toast.LENGTH_SHORT).show();
            return;
        }

        btnVincularGoogle.setEnabled(false);
        // Forzamos selector de cuenta para evitar que Google reutilice la ultima cuenta automaticamente.
        googleClient.signOut().addOnCompleteListener(task -> {
            Intent intent = googleClient.getSignInIntent();
            googleLinkLauncher.launch(intent);
        });
    }

    // Guarda cambios perfil y sincroniza cambios.
    private void guardarCambiosPerfil() {
        if (perfilActual == null) {
            Toast.makeText(this, getString(R.string.profile_load_error), Toast.LENGTH_SHORT).show();
            return;
        }

        String nombre = texto(etNombre);
        String telefono = texto(etTelefono);
        String ciudad = texto(etCiudad);
        String biografia = texto(etBiografia);

        if (TextUtils.isEmpty(nombre)) {
            etNombre.setError(getString(R.string.profile_name_required));
            etNombre.requestFocus();
            return;
        }

        if (!TextUtils.isEmpty(telefono) && !esTelefonoValido(telefono)) {
            etTelefono.setError(getString(R.string.profile_phone_invalid));
            etTelefono.requestFocus();
            return;
        }

        perfilActual.setNombreMostrado(nombre);
        perfilActual.setTelefono(telefono);
        perfilActual.setCiudad(ciudad);
        perfilActual.setBiografia(biografia);

        btnGuardarNombre.setEnabled(false);
        usuarioRepository.guardarInformacionGeneral(perfilActual, new UsuarioRepository.ActionCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk() {
                btnGuardarNombre.setEnabled(true);
                Toast.makeText(PerfilActivity.this, getString(R.string.profile_save_ok), Toast.LENGTH_SHORT).show();
                cargarPerfil();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                btnGuardarNombre.setEnabled(true);
                Toast.makeText(PerfilActivity.this, getString(R.string.profile_save_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Vincula password con la cuenta autenticada.
    private void vincularPassword() {
        if (perfilActual != null && perfilActual.tieneMetodoEmail()) {
            Toast.makeText(this, getString(R.string.profile_password_already_linked), Toast.LENGTH_SHORT).show();
            return;
        }

        String pass1 = texto(etPass1);
        String pass2 = texto(etPass2);

        if (TextUtils.isEmpty(pass1)) {
            etPass1.setError(getString(R.string.profile_password_required));
            etPass1.requestFocus();
            return;
        }
        PasswordPolicy.Result passwordResult = PasswordPolicy.validate(pass1);
        if (passwordResult != PasswordPolicy.Result.OK) {
            etPass1.setError(getString(PasswordPolicy.errorRes(passwordResult)));
            etPass1.requestFocus();
            return;
        }
        if (!pass1.equals(pass2)) {
            etPass2.setError(getString(R.string.profile_password_mismatch));
            etPass2.requestFocus();
            return;
        }

        btnVincularPassword.setEnabled(false);
        usuarioRepository.vincularPassword(pass1, new UsuarioRepository.ActionCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk() {
                btnVincularPassword.setEnabled(true);
                Toast.makeText(PerfilActivity.this, getString(R.string.profile_password_link_ok), Toast.LENGTH_SHORT).show();
                etPass1.setText("");
                etPass2.setText("");
                cargarPerfil();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                btnVincularPassword.setEnabled(true);
                Toast.makeText(PerfilActivity.this, getString(R.string.profile_password_link_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Gestiona cambiar password en este bloque.
    private void cambiarPassword() {
        String email = perfilActual != null ? perfilActual.getEmail() : "";
        if (TextUtils.isEmpty(email)) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getEmail() != null) {
                email = currentUser.getEmail().trim();
            }
        }

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, getString(R.string.profile_password_change_missing_email), Toast.LENGTH_LONG).show();
            return;
        }

        btnCambiarPassword.setEnabled(false);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    btnCambiarPassword.setEnabled(true);
                    Toast.makeText(this, getString(R.string.profile_password_change_sent), Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    btnCambiarPassword.setEnabled(true);
                    Toast.makeText(this, getString(R.string.profile_password_change_error), Toast.LENGTH_LONG).show();
                });
    }

    // Muestra seccion en la interfaz.
    private void mostrarSeccion(int seccion) {
        sectionInfoGeneral.setVisibility(seccion == SEC_INFO ? android.view.View.VISIBLE : android.view.View.GONE);
        sectionSeguridad.setVisibility(seccion == SEC_SEGURIDAD ? android.view.View.VISIBLE : android.view.View.GONE);
        sectionEditarDatos.setVisibility(seccion == SEC_EDITAR ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // Vuelve a ainicio manteniendo consistencia de navegacion.
    private void volverAInicio() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // Gestiona cerrar sesion en este bloque.
    private void cerrarSesion() {
        pushTokenRepository.desvincularTokenUsuarioActual();
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LandingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Gestiona valor ono establecida en este bloque.
    private String valorONoEstablecida(String valor, String fallback) {
        return TextUtils.isEmpty(valor == null ? "" : valor.trim()) ? fallback : valor.trim();
    }

    // Gestiona obtener metodo acceso en este bloque.
    private String obtenerMetodoAcceso(UsuarioPerfil perfil, String fallback) {
        boolean tieneGoogle = perfil.tieneMetodoGoogle();
        boolean tieneEmail = perfil.tieneMetodoEmail();

        if (tieneGoogle && tieneEmail) return getString(R.string.profile_method_google_email);
        if (tieneGoogle) return getString(R.string.profile_method_google);
        if (tieneEmail) return getString(R.string.profile_method_email);
        return fallback;
    }

    // Indica si nombre predeterminado.
    private boolean esNombrePredeterminado(String nombre) {
        return !TextUtils.isEmpty(nombre) && "Usuario".equalsIgnoreCase(nombre.trim());
    }

    // Gestiona texto en este bloque.
    private String texto(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    // Indica si telefono valido.
    private boolean esTelefonoValido(String telefono) {
        return telefono.matches("^[0-9+()\\-\\s]{6,20}$");
    }
}
