package com.example.tarraco_fest.Activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.tarraco_fest.R;
import com.example.tarraco_fest.ViewModel.AdminPanelViewModel;

/**
 * Entrada principal del modulo admin.
 * Valida permisos y redirige a gestion de usuarios o eventos.
 */
public class AdminPanelActivity extends AppCompatActivity {

    private AdminPanelViewModel viewModel;

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_panel);
        configurarBordesSistema();

        Button btnUsuarios = findViewById(R.id.btnAdminUsuarios);
        Button btnEventos = findViewById(R.id.btnAdminEventos);
        Button btnVolver = findViewById(R.id.btnAdminVolver);
        TextView tvEstado = findViewById(R.id.tvAdminEstado);

        configurarViewModel(btnUsuarios, btnEventos, tvEstado);

        btnUsuarios.setOnClickListener(v -> startActivity(new Intent(this, AdminUsuariosActivity.class)));
        btnEventos.setOnClickListener(v -> startActivity(new Intent(this, AdminEventosActivity.class)));
        btnVolver.setOnClickListener(v -> finish());
    }

    // Conecta Activity con ViewModel para desacoplar validacion de acceso.
    private void configurarViewModel(Button btnUsuarios, Button btnEventos, TextView tvEstado) {
        viewModel = new ViewModelProvider(this).get(AdminPanelViewModel.class);

        viewModel.getAdminEnabled().observe(this, enabled -> {
            boolean allow = enabled != null && enabled;
            btnUsuarios.setEnabled(allow);
            btnEventos.setEnabled(allow);
        });

        viewModel.getStatusMessageRes().observe(this, messageRes -> {
            if (messageRes == null) return;
            tvEstado.setText(getString(messageRes));
        });

        viewModel.getToastMessageRes().observe(this, messageRes -> {
            if (messageRes == null) return;
            Toast.makeText(this, getString(messageRes), Toast.LENGTH_LONG).show();
            viewModel.consumirToastMessage();
        });

        viewModel.getShouldCloseScreen().observe(this, shouldClose -> {
            if (shouldClose == null || !shouldClose) return;
            viewModel.consumirCloseScreen();
            finish();
        });

        viewModel.verificarAcceso();
    }

    // Aplica edge-to-edge para eliminar franjas blancas y separar la tarjeta de las barras del sistema.
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

        View root = findViewById(R.id.rootAdminPanel);
        View card = findViewById(R.id.cardAdminPanel);
        ViewGroup.MarginLayoutParams cardLp = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
        int baseTop = cardLp.topMargin;
        int baseBottom = cardLp.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
            lp.topMargin = baseTop + bars.top + dpToPx(8);
            lp.bottomMargin = baseBottom + bars.bottom + dpToPx(8);
            card.setLayoutParams(lp);
            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    // Convierte dp a px para insets y margenes dinamicos.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
