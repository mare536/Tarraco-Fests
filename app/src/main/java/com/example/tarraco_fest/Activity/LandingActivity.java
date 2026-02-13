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

    private static final long DELAY_ANTES_BLUR = 900;   // tiempo para ver el fondo limpio
    private static final long DURACION_BLUR    = 1100;  // duración del blur
    private static final long DELAY_UI_EXTRA   = 120;   // extra para que el blur “asiente”

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);

        ImageView bgNormal = findViewById(R.id.imgBgNormal);
        ImageView bgBlur   = findViewById(R.id.imgBgBlur);
        View overlay       = findViewById(R.id.overlay);
        ImageView logo     = findViewById(R.id.imgLogo);

        View card          = findViewById(R.id.cardLanding);   // <-- nuevo
        LinearLayout btns  = findViewById(R.id.btnContainer);

        Button btnGoogle = findViewById(R.id.btnGoogleLanding);
        Button btnEmail  = findViewById(R.id.btnEmailLanding);

        btnEmail.setOnClickListener(v ->
                startActivity(new Intent(this, AuthActivity.class))
        );

        btnGoogle.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthActivity.class);
            i.putExtra("AUTO_GOOGLE", true);
            startActivity(i);
        });

        // ===== ESTADO INICIAL (para que NO tape la imagen) =====
        bgBlur.setAlpha(0f);
        overlay.setAlpha(0f);

        logo.setAlpha(0f);
        logo.setTranslationY(18f);

        // Oculta la CARD entera (no solo los botones)
        card.setAlpha(0f);
        card.setTranslationY(26f);

        // (opcional) ocultar el contenido interno para un fade más fino
        btns.setAlpha(0f);
        btns.setTranslationY(10f);

        // ===== SECUENCIA =====
        // 1) ver fondo limpio
        // 2) blur
        // 3) aparece UI (card+logo+botones)
        bgNormal.postDelayed(() -> {

            animarBlur(bgNormal, bgBlur, DURACION_BLUR);

            bgNormal.postDelayed(() -> {
                animarUI(overlay, logo, card, btns);
            }, DURACION_BLUR + DELAY_UI_EXTRA);

        }, DELAY_ANTES_BLUR);
    }

    private void animarUI(View overlay, View logo, View card, View btns) {
        overlay.animate()
                .alpha(1f)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        logo.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(550)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        btns.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(120)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animarBlur(ImageView bgNormal, ImageView bgBlur, long duracion) {
        ObjectAnimator fadeBlur = ObjectAnimator.ofFloat(bgBlur, "alpha", 0f, 1f);
        fadeBlur.setDuration(duracion);
        fadeBlur.setInterpolator(new DecelerateInterpolator());
        fadeBlur.start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ValueAnimator va = ValueAnimator.ofFloat(0f, 24f);
            va.setDuration(duracion);
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
