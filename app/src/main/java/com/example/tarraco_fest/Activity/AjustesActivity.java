package com.example.tarraco_fest.Activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.tarraco_fest.R;
import com.google.android.material.button.MaterialButton;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajustes);

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

    @Override
    protected void onResume() {
        super.onResume();
        actualizarEstados();
    }

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

    private void abrirAjustesSistema() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );
        startActivity(intent);
    }

    private boolean tienePermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean tienePermisoUbicacion() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return fine || coarse;
    }

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
}
