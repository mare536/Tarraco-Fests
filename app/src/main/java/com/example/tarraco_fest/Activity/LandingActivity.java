package com.example.tarraco_fest.Activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

/**
 * Pantalla de entrada de autenticacion.
 * Presenta acciones para continuar con Google o con correo.
 */
public class LandingActivity extends AppCompatActivity {

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);
        configurarBordesSistema();

        ImageView bgNormal = findViewById(R.id.imgBgNormal);
        ImageView bgBlur = findViewById(R.id.imgBgBlur);
        View overlay = findViewById(R.id.overlay);
        ImageView logo = findViewById(R.id.imgLogo);
        LinearLayout btns = findViewById(R.id.btnContainer);

        Button btnGoogle = findViewById(R.id.btnGoogleLanding);
        Button btnEmail = findViewById(R.id.btnEmailLanding);

        btnEmail.setOnClickListener(v -> startActivity(new Intent(this, AuthActivity.class)));

        btnGoogle.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthActivity.class);
            i.putExtra("AUTO_GOOGLE", true);
            startActivity(i);
        });

        bgNormal.postDelayed(() -> {
            animarBlur(bgNormal, bgBlur);
            animarUI(overlay, logo, btns);
        }, 180);
    }

    // Aplica edge-to-edge y margenes seguros para evitar solapes con notch y barra inferior.
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

        View root = findViewById(R.id.rootLanding);
        View logo = findViewById(R.id.imgLogo);
        View btns = findViewById(R.id.btnContainer);
        ViewGroup.MarginLayoutParams logoLp = (ViewGroup.MarginLayoutParams) logo.getLayoutParams();
        ViewGroup.MarginLayoutParams btnLp = (ViewGroup.MarginLayoutParams) btns.getLayoutParams();
        int baseLogoTop = logoLp.topMargin;
        int baseBtnBottom = btnLp.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.MarginLayoutParams dynamicLogoLp = (ViewGroup.MarginLayoutParams) logo.getLayoutParams();
            dynamicLogoLp.topMargin = baseLogoTop + bars.top + dpToPx(8);
            logo.setLayoutParams(dynamicLogoLp);

            ViewGroup.MarginLayoutParams dynamicBtnLp = (ViewGroup.MarginLayoutParams) btns.getLayoutParams();
            dynamicBtnLp.bottomMargin = baseBtnBottom + bars.bottom + dpToPx(8);
            btns.setLayoutParams(dynamicBtnLp);

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    // Gestiona on start en este bloque.
    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        // Si es cuenta password sin verificar, no se deja pasar.
        if (esProveedorPassword(user) && !user.isEmailVerified()) {
            FirebaseAuth.getInstance().signOut();
            return;
        }

        validarCuentaNoBloqueada(user);
    }

    // Gestiona animar ui en este bloque.
    private void animarUI(View overlay, View logo, View btns) {
        overlay.animate()
                .alpha(1f)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        logo.animate()
                .alpha(1f).translationY(0f)
                .setDuration(550)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        btns.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(80)
                .setDuration(550)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // Gestiona animar blur en este bloque.
    private void animarBlur(ImageView bgNormal, ImageView bgBlur) {
        ObjectAnimator fadeBlur = ObjectAnimator.ofFloat(bgBlur, "alpha", 0f, 1f);
        fadeBlur.setDuration(650);
        fadeBlur.setInterpolator(new DecelerateInterpolator());
        fadeBlur.start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ValueAnimator va = ValueAnimator.ofFloat(0f, 24f);
            va.setDuration(650);
            va.setInterpolator(new DecelerateInterpolator());
            va.addUpdateListener(anim -> {
                float r = (float) anim.getAnimatedValue();
                bgNormal.setRenderEffect(
                        RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                );
            });
            va.start();
        }
    }

    // Indica si proveedor password.
    private boolean esProveedorPassword(FirebaseUser user) {
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) return true;
        }
        return false;
    }

    // Valida que la cuenta no este bloqueada antes de abrir Home.
    private void validarCuentaNoBloqueada(FirebaseUser user) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreSchema.Collections.USUARIOS)
                .document(user.getUid())
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    boolean cuentaBloqueada = doc.exists()
                            && Boolean.TRUE.equals(doc.getBoolean(FirestoreSchema.UsuarioFields.BLOQUEADO));
                    boolean cuentaEliminada = doc.exists()
                            && Boolean.TRUE.equals(doc.getBoolean(FirestoreSchema.UsuarioFields.ELIMINADO));
                    if (cuentaBloqueada || cuentaEliminada) {
                        FirebaseAuth.getInstance().signOut();
                        Toast.makeText(this, getString(R.string.auth_account_blocked), Toast.LENGTH_LONG).show();
                        return;
                    }

                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(this, getString(R.string.auth_account_validation_failed), Toast.LENGTH_LONG).show();
                });
    }

    // Convierte dp a px para margenes dinamicos.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
