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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LandingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);

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

        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

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

    private boolean esProveedorPassword(FirebaseUser user) {
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) return true;
        }
        return false;
    }
}