package com.example.tarraco_fest.Activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tarraco_fest.R;

public class LandingActivity extends AppCompatActivity {

    public static final String EXTRA_FORCE_EMAIL_LOGIN = "EXTRA_FORCE_EMAIL_LOGIN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Carga la vista de la pantalla "landing"
        setContentView(R.layout.activity_landing);

        // Referencias a vistas para animación (fondo normal + blur, overlay, logo y botones)
        ImageView bgNormal = findViewById(R.id.imgBgNormal);
        ImageView bgBlur   = findViewById(R.id.imgBgBlur);
        View overlay       = findViewById(R.id.overlay);
        ImageView logo     = findViewById(R.id.imgLogo);
        LinearLayout btns  = findViewById(R.id.btnContainer);

        // Botones del landing (solo login por Google o Email)
        Button btnGoogle = findViewById(R.id.btnGoogleLanding);
        Button btnEmail  = findViewById(R.id.btnEmailLanding);

        // Email -> abre AuthActivity (allí se hace login/registro)
        btnEmail.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthActivity.class);
            i.putExtra(EXTRA_FORCE_EMAIL_LOGIN, true);
            startActivity(i);
        });

        // Google -> abre AuthActivity y le indica que lance Google automáticamente
        btnGoogle.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthActivity.class);
            i.putExtra("AUTO_GOOGLE", true);
            startActivity(i);
        });



        // Animación de entrada:
        // - primero se ve el fondo normal
        // - luego aparece el blur + overlay + contenido (logo y botones)
        bgNormal.postDelayed(() -> {
            animarBlur(bgNormal, bgBlur);
            animarUI(overlay, logo, btns);
        }, 180);
    }

    /**
     * Anima la aparición del overlay, logo y contenedor de botones.
     */
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

    /**
     * Efecto de blur:
     * - Siempre hace crossfade a una imagen ya borrosa (imgBgBlur).
     * - Si Android 12+ (API 31), además aplica blur real animado a imgBgNormal.
     */
    private void animarBlur(ImageView bgNormal, ImageView bgBlur) {
        // Fallback universal: crossfade a la imagen borrosa
        ObjectAnimator fadeBlur = ObjectAnimator.ofFloat(bgBlur, "alpha", 0f, 1f);
        fadeBlur.setDuration(650);
        fadeBlur.setInterpolator(new DecelerateInterpolator());
        fadeBlur.start();

        // Blur real solo disponible desde Android 12 (S)
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
}
