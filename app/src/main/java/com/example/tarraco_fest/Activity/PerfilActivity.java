package com.example.tarraco_fest.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.tarraco_fest.Modelo.UsuarioPerfil;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.UsuarioRepository;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;

public class PerfilActivity extends AppCompatActivity {

    private static final int ESTADO_INFO = 0;
    private static final int ESTADO_OK = 1;
    private static final int ESTADO_ERROR = 2;

    private static final int SECCION_INFO = 1;
    private static final int SECCION_SEGURIDAD = 2;
    private static final int SECCION_EDITAR = 3;

    private UsuarioRepository usuarioRepository;
    private UsuarioPerfil perfilActual;

    private DrawerLayout drawerPerfil;
    private NavigationView navPerfil;
    private ImageButton btnMenuPerfil;

    private LinearLayout sectionInformacionPerfil;
    private LinearLayout sectionSeguridadPerfil;
    private LinearLayout sectionEditarPerfil;

    private TextView tvTituloSeccionPerfil;
    private TextView tvSubtituloSeccionPerfil;
    private TextView tvEstadoPerfil;

    private TextView tvInfoEmailPerfil;
    private TextView tvInfoNombreEstadoPerfil;
    private TextView tvInfoTelefonoEstadoPerfil;
    private TextView tvInfoCiudadEstadoPerfil;
    private TextView tvInfoBioEstadoPerfil;
    private TextView tvInfoCuentaEstadoPerfil;

    private TextView tvPasswordEstado;
    private EditText etPass1Perfil;
    private EditText etPass2Perfil;
    private TextInputLayout tilPass1Perfil;
    private TextInputLayout tilPass2Perfil;
    private Button btnVincularPassword;

    private EditText etNombrePerfil;
    private EditText etTelefonoPerfil;
    private EditText etCiudadPerfil;
    private EditText etBioPerfil;
    private Button btnGuardarPerfil;

    private View loadingPerfil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        View root = findViewById(R.id.rootPerfil);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        usuarioRepository = new UsuarioRepository();
        bindViews();
        configurarAnimacionEntrada();
        configurarDrawer();
        configurarAcciones();
        configurarBackHandler();
        mostrarSeccion(SECCION_INFO);
        cargarPerfil();
    }

    private void bindViews() {
        drawerPerfil = findViewById(R.id.drawerPerfil);
        navPerfil = findViewById(R.id.navPerfil);
        btnMenuPerfil = findViewById(R.id.btnMenuPerfil);

        sectionInformacionPerfil = findViewById(R.id.sectionInformacionPerfil);
        sectionSeguridadPerfil = findViewById(R.id.sectionSeguridadPerfil);
        sectionEditarPerfil = findViewById(R.id.sectionEditarPerfil);

        tvTituloSeccionPerfil = findViewById(R.id.tvTituloSeccionPerfil);
        tvSubtituloSeccionPerfil = findViewById(R.id.tvSubtituloSeccionPerfil);
        tvEstadoPerfil = findViewById(R.id.tvEstadoPerfil);

        tvInfoEmailPerfil = findViewById(R.id.tvInfoEmailPerfil);
        tvInfoNombreEstadoPerfil = findViewById(R.id.tvInfoNombreEstadoPerfil);
        tvInfoTelefonoEstadoPerfil = findViewById(R.id.tvInfoTelefonoEstadoPerfil);
        tvInfoCiudadEstadoPerfil = findViewById(R.id.tvInfoCiudadEstadoPerfil);
        tvInfoBioEstadoPerfil = findViewById(R.id.tvInfoBioEstadoPerfil);
        tvInfoCuentaEstadoPerfil = findViewById(R.id.tvInfoCuentaEstadoPerfil);

        tvPasswordEstado = findViewById(R.id.tvPasswordEstado);
        etPass1Perfil = findViewById(R.id.etPass1Perfil);
        etPass2Perfil = findViewById(R.id.etPass2Perfil);
        tilPass1Perfil = findViewById(R.id.tilPass1Perfil);
        tilPass2Perfil = findViewById(R.id.tilPass2Perfil);
        btnVincularPassword = findViewById(R.id.btnVincularPassword);

        etNombrePerfil = findViewById(R.id.etNombrePerfil);
        etTelefonoPerfil = findViewById(R.id.etTelefonoPerfil);
        etCiudadPerfil = findViewById(R.id.etCiudadPerfil);
        etBioPerfil = findViewById(R.id.etBioPerfil);
        btnGuardarPerfil = findViewById(R.id.btnGuardarPerfil);

        loadingPerfil = findViewById(R.id.loadingPerfil);
    }

    private void configurarAcciones() {
        btnGuardarPerfil.setOnClickListener(v -> guardarPerfil());
        btnVincularPassword.setOnClickListener(v -> vincularPassword());
    }

    private void configurarBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerPerfil.isDrawerOpen(GravityCompat.START)) {
                    drawerPerfil.closeDrawer(GravityCompat.START);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private void configurarDrawer() {
        btnMenuPerfil.setOnClickListener(v -> drawerPerfil.openDrawer(GravityCompat.START));

        navPerfil.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_info_general) {
                mostrarSeccion(SECCION_INFO);
            } else if (id == R.id.nav_seguridad) {
                mostrarSeccion(SECCION_SEGURIDAD);
            } else if (id == R.id.nav_editar_datos) {
                mostrarSeccion(SECCION_EDITAR);
            } else if (id == R.id.nav_cerrar_sesion) {
                cerrarSesion();
            }

            drawerPerfil.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void configurarAnimacionEntrada() {
        ImageView bgBlur = findViewById(R.id.imgBgBlur);
        View overlay = findViewById(R.id.overlay);
        View topBar = findViewById(R.id.topBarPerfil);
        View content = findViewById(R.id.perfilContent);

        bgBlur.setAlpha(0f);
        overlay.setAlpha(0f);
        topBar.setAlpha(0f);
        topBar.setTranslationY(-12f);
        content.setAlpha(0f);
        content.setTranslationY(22f);

        bgBlur.postDelayed(() -> {
            bgBlur.animate().alpha(1f).setDuration(650).start();
            overlay.animate().alpha(1f).setDuration(450).start();
            topBar.animate().alpha(1f).translationY(0f).setDuration(420).start();
            content.animate().alpha(1f).translationY(0f).setDuration(550).start();
        }, 150);
    }

    private void cargarPerfil() {
        setUiEnabled(false);
        setEstado(getString(R.string.auth_loading), ESTADO_INFO);

        usuarioRepository.cargarPerfilActual(new UsuarioRepository.PerfilCallback() {
            @Override
            public void onOk(UsuarioPerfil perfil) {
                perfilActual = perfil;
                renderPerfil(perfil);
                setUiEnabled(true);
                tvEstadoPerfil.setVisibility(View.GONE);
            }

            @Override
            public void onError(Exception e) {
                setUiEnabled(true);
                if (e != null && e.getMessage() != null
                        && e.getMessage().toLowerCase(Locale.ROOT).contains("sesion")) {
                    setEstado(getString(R.string.profile_error_no_session), ESTADO_ERROR);
                    volverAuth();
                    return;
                }
                setEstado(getString(R.string.profile_error_generic), ESTADO_ERROR);
            }
        });
    }

    private void renderPerfil(UsuarioPerfil perfil) {
        etNombrePerfil.setText(perfil.getNombreMostrado());
        etTelefonoPerfil.setText(perfil.getTelefono());
        etCiudadPerfil.setText(perfil.getCiudad());
        etBioPerfil.setText(perfil.getBiografia());

        String email = valorCampoResumen(perfil.getEmail());
        tvInfoEmailPerfil.setText(getString(R.string.profile_summary_email_fmt, email));

        tvInfoNombreEstadoPerfil.setText(getString(
                R.string.profile_summary_field_status_fmt,
                getString(R.string.profile_field_name_short),
                valorCampoResumen(perfil.getNombreMostrado())
        ));
        tvInfoTelefonoEstadoPerfil.setText(getString(
                R.string.profile_summary_field_status_fmt,
                getString(R.string.profile_field_phone_short),
                valorCampoResumen(perfil.getTelefono())
        ));
        tvInfoCiudadEstadoPerfil.setText(getString(
                R.string.profile_summary_field_status_fmt,
                getString(R.string.profile_field_city_short),
                valorCampoResumen(perfil.getCiudad())
        ));
        tvInfoBioEstadoPerfil.setText(getString(
                R.string.profile_summary_field_status_fmt,
                getString(R.string.profile_field_bio_short),
                valorCampoResumen(perfil.getBiografia())
        ));

        String cuentaEstado = perfil.isBloqueado()
                ? getString(R.string.profile_account_blocked)
                : getString(R.string.profile_account_active);
        tvInfoCuentaEstadoPerfil.setText(cuentaEstado);

        actualizarEstadoPassword(perfil.isTienePassword());
    }

    private void mostrarSeccion(int seccion) {
        sectionInformacionPerfil.setVisibility(seccion == SECCION_INFO ? View.VISIBLE : View.GONE);
        sectionSeguridadPerfil.setVisibility(seccion == SECCION_SEGURIDAD ? View.VISIBLE : View.GONE);
        sectionEditarPerfil.setVisibility(seccion == SECCION_EDITAR ? View.VISIBLE : View.GONE);

        if (seccion == SECCION_INFO) {
            navPerfil.setCheckedItem(R.id.nav_info_general);
            tvTituloSeccionPerfil.setText(R.string.profile_section_general);
            tvSubtituloSeccionPerfil.setText(R.string.profile_section_general_subtitle);
        } else if (seccion == SECCION_SEGURIDAD) {
            navPerfil.setCheckedItem(R.id.nav_seguridad);
            tvTituloSeccionPerfil.setText(R.string.profile_section_security);
            tvSubtituloSeccionPerfil.setText(R.string.profile_section_security_subtitle);
        } else {
            navPerfil.setCheckedItem(R.id.nav_editar_datos);
            tvTituloSeccionPerfil.setText(R.string.profile_section_edit);
            tvSubtituloSeccionPerfil.setText(R.string.profile_section_edit_subtitle);
        }
    }

    private void guardarPerfil() {
        limpiarErrores();

        String nombre = etNombrePerfil.getText() == null ? "" : etNombrePerfil.getText().toString().trim();
        String telefono = etTelefonoPerfil.getText() == null ? "" : etTelefonoPerfil.getText().toString().trim();
        String ciudad = etCiudadPerfil.getText() == null ? "" : etCiudadPerfil.getText().toString().trim();
        String bio = etBioPerfil.getText() == null ? "" : etBioPerfil.getText().toString().trim();

        if (nombre.isEmpty()) {
            etNombrePerfil.setError(getString(R.string.profile_field_required_name));
            etNombrePerfil.requestFocus();
            mostrarSeccion(SECCION_EDITAR);
            return;
        }

        if (!telefono.isEmpty() && !telefono.matches("[0-9+()\\-\\s]{7,20}")) {
            etTelefonoPerfil.setError(getString(R.string.profile_field_invalid_phone));
            etTelefonoPerfil.requestFocus();
            mostrarSeccion(SECCION_EDITAR);
            return;
        }

        if (perfilActual == null) perfilActual = new UsuarioPerfil();
        perfilActual.setNombreMostrado(nombre);
        perfilActual.setTelefono(telefono);
        perfilActual.setCiudad(ciudad);
        perfilActual.setBiografia(bio);

        setUiEnabled(false);
        setEstado(getString(R.string.profile_loading), ESTADO_INFO);

        usuarioRepository.guardarInformacionGeneral(perfilActual, new UsuarioRepository.ActionCallback() {
            @Override
            public void onOk() {
                setEstado(getString(R.string.profile_saved_ok), ESTADO_OK);
                cargarPerfil();
                mostrarSeccion(SECCION_INFO);
            }

            @Override
            public void onError(Exception e) {
                setUiEnabled(true);
                setEstado(getString(R.string.profile_error_generic), ESTADO_ERROR);
            }
        });
    }

    private void vincularPassword() {
        limpiarErroresPassword();

        if (perfilActual != null && perfilActual.isTienePassword()) {
            setEstado(getString(R.string.profile_password_exists), ESTADO_INFO);
            return;
        }

        String p1 = etPass1Perfil.getText() == null ? "" : etPass1Perfil.getText().toString().trim();
        String p2 = etPass2Perfil.getText() == null ? "" : etPass2Perfil.getText().toString().trim();

        if (p1.length() < 6) {
            etPass1Perfil.setError(getString(R.string.profile_password_min));
            etPass1Perfil.requestFocus();
            mostrarSeccion(SECCION_SEGURIDAD);
            return;
        }

        if (!p1.equals(p2)) {
            etPass2Perfil.setError(getString(R.string.profile_password_mismatch));
            etPass2Perfil.requestFocus();
            mostrarSeccion(SECCION_SEGURIDAD);
            return;
        }

        setUiEnabled(false);
        setEstado(getString(R.string.profile_loading), ESTADO_INFO);

        usuarioRepository.vincularPassword(p1, new UsuarioRepository.ActionCallback() {
            @Override
            public void onOk() {
                etPass1Perfil.setText("");
                etPass2Perfil.setText("");
                setEstado(getString(R.string.profile_password_linked_ok), ESTADO_OK);
                cargarPerfil();
                mostrarSeccion(SECCION_SEGURIDAD);
            }

            @Override
            public void onError(Exception e) {
                setUiEnabled(true);
                String msg = e != null && e.getMessage() != null ? e.getMessage() : "";
                if (msg.toLowerCase(Locale.ROOT).contains("ya tiene contrasena")) {
                    setEstado(getString(R.string.profile_password_exists), ESTADO_INFO);
                    return;
                }
                setEstado(getString(R.string.profile_error_generic), ESTADO_ERROR);
            }
        });
    }

    private void cerrarSesion() {
        setEstado(getString(R.string.profile_signing_out), ESTADO_INFO);
        FirebaseAuth.getInstance().signOut();
        volverAuth();
    }

    private void volverAuth() {
        Intent i = new Intent(this, AuthActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void limpiarErrores() {
        etNombrePerfil.setError(null);
        etTelefonoPerfil.setError(null);
        etCiudadPerfil.setError(null);
        etBioPerfil.setError(null);
    }

    private void limpiarErroresPassword() {
        etPass1Perfil.setError(null);
        etPass2Perfil.setError(null);
    }

    private void setUiEnabled(boolean enabled) {
        etNombrePerfil.setEnabled(enabled);
        etTelefonoPerfil.setEnabled(enabled);
        etCiudadPerfil.setEnabled(enabled);
        etBioPerfil.setEnabled(enabled);
        etPass1Perfil.setEnabled(enabled);
        etPass2Perfil.setEnabled(enabled);
        btnMenuPerfil.setEnabled(enabled);
        navPerfil.setEnabled(enabled);

        btnGuardarPerfil.setEnabled(enabled);

        if (perfilActual != null && perfilActual.isTienePassword()) {
            btnVincularPassword.setEnabled(false);
        } else {
            btnVincularPassword.setEnabled(enabled);
        }

        loadingPerfil.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void setEstado(String msg, int tipo) {
        if (TextUtils.isEmpty(msg)) {
            tvEstadoPerfil.setVisibility(View.GONE);
            return;
        }

        tvEstadoPerfil.setVisibility(View.VISIBLE);
        if (tipo == ESTADO_ERROR) {
            tvEstadoPerfil.setBackgroundResource(R.drawable.bg_estado_error);
            tvEstadoPerfil.setText(getString(R.string.estado_error_fmt, msg));
        } else if (tipo == ESTADO_OK) {
            tvEstadoPerfil.setBackgroundResource(R.drawable.bg_estado_ok);
            tvEstadoPerfil.setText(getString(R.string.estado_ok_fmt, msg));
        } else {
            tvEstadoPerfil.setBackgroundResource(R.drawable.bg_estado_info);
            tvEstadoPerfil.setText(msg);
        }

        tvEstadoPerfil.setAlpha(0f);
        tvEstadoPerfil.animate().alpha(1f).setDuration(180).start();
    }

    private void actualizarEstadoPassword(boolean tienePassword) {
        if (perfilActual != null) perfilActual.setTienePassword(tienePassword);

        if (tienePassword) {
            tvPasswordEstado.setText(getString(R.string.profile_password_exists));
            btnVincularPassword.setText(getString(R.string.profile_password_exists_button));
            btnVincularPassword.setEnabled(false);
            tilPass1Perfil.setVisibility(View.GONE);
            tilPass2Perfil.setVisibility(View.GONE);
        } else {
            tvPasswordEstado.setText(getString(R.string.profile_password_help));
            btnVincularPassword.setText(getString(R.string.profile_add_password_button));
            btnVincularPassword.setEnabled(true);
            tilPass1Perfil.setVisibility(View.VISIBLE);
            tilPass2Perfil.setVisibility(View.VISIBLE);
        }
    }

    private String valorCampoResumen(String valor) {
        if (TextUtils.isEmpty(valor)) return getString(R.string.profile_status_unset);
        String limpio = valor.trim();
        return limpio.isEmpty() ? getString(R.string.profile_status_unset) : limpio;
    }

}
