package com.example.tarraco_fest.Activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.tarraco_fest.R;
import com.google.android.material.button.MaterialButton;

/**
 * Pantalla de ajustes generales de la aplicacion.
 * Centraliza opciones de preferencia visibles para el usuario.
 */
public class AjustesActivity extends AppCompatActivity {

    private TextView tvNotifStatus;
    private TextView tvLocationStatus;
    private MaterialButton btnNotifAction;
    private MaterialButton btnLocationAction;

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                actualizarEstados();
                if (!granted) {
                    Toast.makeText(this, getString(R.string.settings_permission_denied_hint), Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                actualizarEstados();
                boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (!fine && !coarse) {
                    Toast.makeText(this, getString(R.string.settings_permission_denied_hint), Toast.LENGTH_SHORT).show();
                }
            });

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajustes);
        configurarBordesSistema();

        tvNotifStatus = findViewById(R.id.tvSettingsNotifStatus);
        tvLocationStatus = findViewById(R.id.tvSettingsLocationStatus);
        btnNotifAction = findViewById(R.id.btnSettingsNotifAction);
        btnLocationAction = findViewById(R.id.btnSettingsLocationAction);

        btnNotifAction.setOnClickListener(v -> solicitarNotificaciones());
        btnLocationAction.setOnClickListener(v -> solicitarUbicacion());
        findViewById(R.id.btnSettingsOpenSystem).setOnClickListener(v -> abrirAjustesSistema());
        findViewById(R.id.btnSettingsBack).setOnClickListener(v -> finish());

        actualizarEstados();
    }

    // Aplica edge-to-edge y separa la tarjeta del sistema para evitar solapes visuales.
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

        View root = findViewById(R.id.rootAjustes);
        View card = findViewById(R.id.cardAjustes);
        ViewGroup.MarginLayoutParams lpCard = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        int baseTop = lpCard.topMargin;
        int baseBottom = lpCard.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
            params.topMargin = baseTop + bars.top + dpToPx(8);
            params.bottomMargin = baseBottom + bars.bottom + dpToPx(8);
            card.setLayoutParams(params);
            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    // Gestiona on resume en este bloque.
    @Override
    protected void onResume() {
        super.onResume();
        actualizarEstados();
    }

    // Gestiona solicitar notificaciones en este bloque.
    private void solicitarNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.settings_notifications_not_required), Toast.LENGTH_SHORT).show();
            return;
        }
        if (tienePermisoNotificaciones()) {
            Toast.makeText(this, getString(R.string.settings_already_granted), Toast.LENGTH_SHORT).show();
            return;
        }
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    // Gestiona solicitar ubicacion en este bloque.
    private void solicitarUbicacion() {
        if (tienePermisoUbicacion()) {
            Toast.makeText(this, getString(R.string.settings_already_granted), Toast.LENGTH_SHORT).show();
            return;
        }
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    // Gestiona abrir ajustes sistema en este bloque.
    private void abrirAjustesSistema() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );
        startActivity(intent);
    }

    // Indica si permiso notificaciones esta disponible.
    private boolean tienePermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Indica si permiso ubicacion esta disponible.
    private boolean tienePermisoUbicacion() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return fine || coarse;
    }

    // Actualiza estados con la logica de negocio actual.
    private void actualizarEstados() {
        boolean notifOk = tienePermisoNotificaciones();
        boolean locationOk = tienePermisoUbicacion();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            tvNotifStatus.setText(getString(R.string.settings_notifications_not_required));
            tvNotifStatus.setBackgroundResource(R.drawable.bg_estado_ok);
            btnNotifAction.setText(getString(R.string.settings_open_system));
            btnNotifAction.setOnClickListener(v -> abrirAjustesSistema());
        } else {
            tvNotifStatus.setText(getString(notifOk
                    ? R.string.settings_status_granted
                    : R.string.settings_status_pending));
            tvNotifStatus.setBackgroundResource(notifOk ? R.drawable.bg_estado_ok : R.drawable.bg_estado_error);
            btnNotifAction.setText(getString(notifOk
                    ? R.string.settings_open_system
                    : R.string.settings_request_permission));
            btnNotifAction.setOnClickListener(v -> {
                if (notifOk) abrirAjustesSistema();
                else solicitarNotificaciones();
            });
        }

        tvLocationStatus.setText(getString(locationOk
                ? R.string.settings_status_granted
                : R.string.settings_status_pending));
        tvLocationStatus.setBackgroundResource(locationOk ? R.drawable.bg_estado_ok : R.drawable.bg_estado_error);
        btnLocationAction.setText(getString(locationOk
                ? R.string.settings_open_system
                : R.string.settings_request_permission));
        btnLocationAction.setOnClickListener(v -> {
            if (locationOk) abrirAjustesSistema();
            else solicitarUbicacion();
        });
    }

    // Convierte dp a px para margenes dinamicos segun insets.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
