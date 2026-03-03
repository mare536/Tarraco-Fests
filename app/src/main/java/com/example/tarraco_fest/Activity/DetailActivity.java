package com.example.tarraco_fest.Activity;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.Patterns;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.URLUtil;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.FavoritosRepository;
import com.example.tarraco_fest.Repository.ReminderRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Muestra el detalle completo de un evento seleccionado.
 * Incluye informacion, enlace de fuente y configuracion de recordatorios.
 */
public class DetailActivity extends AppCompatActivity {

    private static final long REMINDER_MIN_LEAD_MS = 60L * 1000L;
    private static final String PREF_SYNC_FLAGS = "sync_flags";
    private static final String KEY_FAVORITOS_UPDATED_AT = "favoritos_updated_at";

    private ReminderRepository reminderRepository;
    private FavoritosRepository favoritosRepository;
    private Evento eventoActual;
    private MaterialButton btnReminder;
    private MaterialButton btnFavorite;
    private TextView tvReminderStatus;
    private String sourceUrl = "";
    private boolean reminderDisponible = true;
    private boolean favoritoActivo = false;

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        configurarBordesSistema();
        reminderRepository = new ReminderRepository(this);
        favoritosRepository = new FavoritosRepository();
        aplicarInsetSuperiorToolbar();
        aplicarInsetInferiorContenido();

        eventoActual = (Evento) getIntent().getSerializableExtra("extra_evento");
        if (eventoActual == null) {
            Toast.makeText(this, getString(R.string.detail_event_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        vincularDatos(eventoActual);
        configurarAcciones(eventoActual);
        configurarRecordatorios(eventoActual);
        configurarFavoritos(eventoActual);
        cargarEstadoRecordatorio(eventoActual);
    }

    // Aplica edge-to-edge para aprovechar el hero superior sin mostrar franjas del sistema.
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
            // El hero superior usa degradado oscuro, mantenemos iconos claros en status bar.
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(!modoOscuro);
        }
    }

    // Aplica inset superior toolbar respetando el estado actual.
    private void aplicarInsetSuperiorToolbar() {
        View toolbar = findViewById(R.id.detailHeroToolbar);
        if (toolbar == null) return;

        final android.view.ViewGroup.MarginLayoutParams lp =
                (android.view.ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
        final int baseTop = lp.topMargin;

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            android.view.ViewGroup.MarginLayoutParams params =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.topMargin = baseTop + bars.top;
            v.setLayoutParams(params);
            return insets;
        });
        ViewCompat.requestApplyInsets(toolbar);
    }

    // Aplica inset inferior contenido respetando el estado actual.
    private void aplicarInsetInferiorContenido() {
        NestedScrollView scroll = findViewById(R.id.detailScroll);
        View content = findViewById(R.id.detailContent);
        if (scroll == null || content == null) return;

        final int baseBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            content.setPadding(
                    content.getPaddingLeft(),
                    content.getPaddingTop(),
                    content.getPaddingRight(),
                    baseBottom + bars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(scroll);
    }

    // Vincula datos con la cuenta autenticada.
    private void vincularDatos(Evento evento) {
        TextView chipCategoria = findViewById(R.id.tvDetailCategoryChip);
        TextView txtTitulo = findViewById(R.id.detailTitle);
        TextView txtFecha = findViewById(R.id.detailDate);
        TextView txtUbicacion = findViewById(R.id.detailLocation);
        TextView txtPrecio = findViewById(R.id.detailPrice);
        TextView txtDescripcion = findViewById(R.id.detailDescription);
        TextView txtInfoAddress = findViewById(R.id.tvDetailInfoAddress);
        TextView txtInfoCategory = findViewById(R.id.tvDetailInfoCategory);
        TextView tvSourceLabel = findViewById(R.id.tvDetailSourceLabel);
        TextView tvSourceValue = findViewById(R.id.tvDetailSourceValue);
        MaterialButton btnOpenSource = findViewById(R.id.btnDetailOpenSource);

        ImageView imgView = findViewById(R.id.detailImage);
        int fallbackImage = evento.getImagenResId() != 0 ? evento.getImagenResId() : R.drawable.card_festival;

        if (evento.getImagenUrl() != null && !evento.getImagenUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(evento.getImagenUrl())
                    .placeholder(fallbackImage)
                    .error(fallbackImage)
                    .centerCrop()
                    .into(imgView);
        } else if (!TextUtils.isEmpty(evento.getImagenBase64())) {
            try {
                byte[] bytes = Base64.decode(evento.getImagenBase64(), Base64.DEFAULT);
                com.bumptech.glide.Glide.with(this)
                        .load(bytes)
                        .placeholder(fallbackImage)
                        .error(fallbackImage)
                        .centerCrop()
                        .into(imgView);
            } catch (IllegalArgumentException ex) {
                imgView.setImageResource(fallbackImage);
            }
        } else {
            imgView.setImageResource(fallbackImage);
        }

        String categoria = safeText(evento.getCategoriaUI(), "General");
        chipCategoria.setText(categoria.toUpperCase(Locale.ROOT));
        txtTitulo.setText(evento.getTitulo());
        txtFecha.setText(evento.getFecha());
        txtUbicacion.setText(evento.getUbicacion());

        String rawDescripcion = safeText(evento.getDescripcion(), "");
        String sourceUrlRaw = extraerPrimerUrl(rawDescripcion);
        sourceUrl = normalizarUrl(sourceUrlRaw);
        String descripcionLimpia = rawDescripcion;
        if (!sourceUrlRaw.isEmpty()) {
            descripcionLimpia = rawDescripcion.replace(sourceUrlRaw, "").trim();
        }
        if (descripcionLimpia.isEmpty()) {
            descripcionLimpia = getString(R.string.detail_description_fallback);
        }
        txtDescripcion.setText(construirResumenEvento(evento, categoria));

        String precioFinal = evento.getPrecio() == 0.0
                ? getString(R.string.detail_price_free)
                : getString(R.string.detail_price_paid_fmt, evento.getPrecio());
        txtPrecio.setText(precioFinal);

        txtInfoAddress.setText(formatearDescripcionParaVista(descripcionLimpia));
        txtInfoCategory.setText(obtenerEstadoEvento(evento));

        if (!sourceUrl.isEmpty()) {
            tvSourceLabel.setVisibility(View.VISIBLE);
            tvSourceValue.setVisibility(View.VISIBLE);
            btnOpenSource.setVisibility(View.GONE);
            aplicarHipervinculoFuente(tvSourceValue, sourceUrl);
        } else {
            tvSourceLabel.setVisibility(View.GONE);
            tvSourceValue.setVisibility(View.GONE);
            btnOpenSource.setVisibility(View.GONE);
        }
    }

    // Configura acciones segun el contexto actual.
    private void configurarAcciones(Evento evento) {
        findViewById(R.id.btnDetailBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.btnDetailShareTop).setOnClickListener(v -> compartirEvento(evento));

        MaterialButton btnDirections = findViewById(R.id.btnDetailDirections);
        MaterialButton btnShare = findViewById(R.id.btnDetailShare);
        btnDirections.setOnClickListener(v -> abrirMapa(evento));
        btnShare.setOnClickListener(v -> compartirEvento(evento));
    }

    // Configura recordatorios segun el contexto actual.
    private void configurarRecordatorios(Evento evento) {
        btnReminder = findViewById(R.id.btnDetailReminder);
        tvReminderStatus = findViewById(R.id.tvDetailReminderStatus);
        long now = System.currentTimeMillis();

        if (evento.getInicioMillis() <= 0L) {
            reminderDisponible = false;
            btnReminder.setEnabled(false);
            btnReminder.setAlpha(0.7f);
            btnReminder.setText(getString(R.string.detail_reminder_unavailable_button));
            tvReminderStatus.setText(getString(R.string.detail_reminder_invalid_event_date));
            return;
        }

        if (evento.getInicioMillis() <= now) {
            reminderDisponible = false;
            btnReminder.setEnabled(false);
            btnReminder.setAlpha(0.7f);
            btnReminder.setText(getString(R.string.detail_reminder_event_finished_button));
            tvReminderStatus.setText(getString(R.string.detail_reminder_event_finished_status));
            return;
        }

        long minPermitido = System.currentTimeMillis() + REMINDER_MIN_LEAD_MS;
        long maxPermitido = evento.getInicioMillis() - REMINDER_MIN_LEAD_MS;
        if (maxPermitido <= minPermitido) {
            reminderDisponible = false;
            btnReminder.setEnabled(false);
            btnReminder.setAlpha(0.7f);
            btnReminder.setText(getString(R.string.detail_reminder_unavailable_button));
            tvReminderStatus.setText(getString(R.string.detail_reminder_event_soon));
            return;
        }

        reminderDisponible = true;
        btnReminder.setEnabled(true);
        btnReminder.setAlpha(1f);
        mostrarEstadoSinRecordatorio();
        btnReminder.setOnClickListener(v -> seleccionarFechaYHoraRecordatorio(evento));
    }

    // Configura favoritos por usuario y sincroniza estado visual en la pantalla.
    private void configurarFavoritos(Evento evento) {
        btnFavorite = findViewById(R.id.btnDetailFavorite);
        if (btnFavorite == null) return;

        favoritoActivo = evento != null && evento.isFavorito();
        renderizarEstadoFavorito();

        btnFavorite.setOnClickListener(v -> toggleFavorito(evento));
        cargarEstadoFavorito(evento);
    }

    // Carga estado favorito del evento para el usuario actual.
    private void cargarEstadoFavorito(Evento evento) {
        if (evento == null || evento.getId() == null || evento.getId().trim().isEmpty()) {
            if (btnFavorite != null) btnFavorite.setEnabled(false);
            return;
        }

        favoritosRepository.cargarFavoritosIds(new FavoritosRepository.FavoritosIdsCallback() {
            @Override
            public void onOk(java.util.Set<String> favoritosIds) {
                boolean marcado = favoritosIds != null && favoritosIds.contains(evento.getId());
                favoritoActivo = marcado;
                evento.setFavorito(marcado);
                renderizarEstadoFavorito();
            }

            @Override
            public void onError(Exception e) {
                renderizarEstadoFavorito();
            }
        });
    }

    // Alterna favorito para el evento actual del usuario autenticado.
    private void toggleFavorito(Evento evento) {
        if (evento == null || evento.getId() == null || evento.getId().trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.detail_favorite_error), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean nuevoEstado = !favoritoActivo;
        btnFavorite.setEnabled(false);

        FavoritosRepository.Callback cb = new FavoritosRepository.Callback() {
            @Override
            public void onOk() {
                favoritoActivo = nuevoEstado;
                evento.setFavorito(nuevoEstado);
                renderizarEstadoFavorito();
                btnFavorite.setEnabled(true);
                Toast.makeText(
                        DetailActivity.this,
                        getString(nuevoEstado ? R.string.detail_favorite_added : R.string.detail_favorite_removed),
                        Toast.LENGTH_SHORT
                ).show();
                marcarFavoritosActualizados();
            }

            @Override
            public void onError(Exception e) {
                btnFavorite.setEnabled(true);
                String msg = (e != null && e.getMessage() != null && e.getMessage().contains("No hay usuario autenticado"))
                        ? getString(R.string.detail_favorite_login_required)
                        : getString(R.string.detail_favorite_error);
                Toast.makeText(DetailActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        };

        if (nuevoEstado) {
            favoritosRepository.marcarFavorito(evento.getId(), evento.getTitulo(), cb);
        } else {
            favoritosRepository.quitarFavorito(evento.getId(), cb);
        }
    }

    // Refresca aspecto del boton favorito segun estado actual.
    private void renderizarEstadoFavorito() {
        if (btnFavorite == null) return;

        if (favoritoActivo) {
            btnFavorite.setBackgroundResource(R.drawable.btn_auth_primary);
            btnFavorite.setText(getString(R.string.detail_favorite_remove));
            btnFavorite.setTextColor(ContextCompat.getColor(this, R.color.auth_btn_primary_text));
            btnFavorite.setIconResource(R.drawable.ic_favorite_filled);
            btnFavorite.setIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.auth_btn_primary_text)));
        } else {
            btnFavorite.setBackgroundResource(R.drawable.btn_detail_secondary);
            btnFavorite.setText(getString(R.string.detail_favorite_add));
            btnFavorite.setTextColor(ContextCompat.getColor(this, R.color.detail_action_secondary_text));
            btnFavorite.setIconResource(R.drawable.ic_favorite_border);
            btnFavorite.setIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.detail_action_secondary_text)));
        }
    }

    // Marca favoritos actualizados para que Home refresque al volver.
    private void marcarFavoritosActualizados() {
        SharedPreferences prefs = getSharedPreferences(PREF_SYNC_FLAGS, MODE_PRIVATE);
        prefs.edit().putLong(KEY_FAVORITOS_UPDATED_AT, System.currentTimeMillis()).apply();
    }

    // Gestiona seleccionar fecha yhora recordatorio en este bloque.
    private void seleccionarFechaYHoraRecordatorio(Evento evento) {
        if (evento.getInicioMillis() <= 0L) {
            Toast.makeText(this, getString(R.string.detail_reminder_invalid_event_date), Toast.LENGTH_LONG).show();
            return;
        }

        if (evento.getInicioMillis() <= System.currentTimeMillis()) {
            reminderDisponible = false;
            btnReminder.setEnabled(false);
            btnReminder.setAlpha(0.7f);
            btnReminder.setText(getString(R.string.detail_reminder_event_finished_button));
            tvReminderStatus.setText(getString(R.string.detail_reminder_event_finished_status));
            Toast.makeText(this, getString(R.string.detail_reminder_event_finished_status), Toast.LENGTH_LONG).show();
            return;
        }

        long minPermitido = System.currentTimeMillis() + REMINDER_MIN_LEAD_MS;
        long maxPermitido = evento.getInicioMillis() - REMINDER_MIN_LEAD_MS;
        if (maxPermitido <= minPermitido) {
            reminderDisponible = false;
            btnReminder.setEnabled(false);
            btnReminder.setAlpha(0.7f);
            btnReminder.setText(getString(R.string.detail_reminder_unavailable_button));
            tvReminderStatus.setText(getString(R.string.detail_reminder_event_soon));
            Toast.makeText(this, getString(R.string.detail_reminder_no_available_slot), Toast.LENGTH_LONG).show();
            return;
        }

        Calendar sugerida = Calendar.getInstance();
        long unaHoraAntes = evento.getInicioMillis() - (60L * 60L * 1000L);
        long sugeridaMillis = Math.max(unaHoraAntes, minPermitido);
        sugeridaMillis = Math.min(sugeridaMillis, maxPermitido);
        sugerida.setTimeInMillis(sugeridaMillis);

        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar seleccion = Calendar.getInstance();
                    seleccion.setTimeInMillis(sugerida.getTimeInMillis());
                    seleccion.set(Calendar.YEAR, year);
                    seleccion.set(Calendar.MONTH, month);
                    seleccion.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    mostrarSelectorHora(evento, seleccion, minPermitido, maxPermitido);
                },
                sugerida.get(Calendar.YEAR),
                sugerida.get(Calendar.MONTH),
                sugerida.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.getDatePicker().setMinDate(minPermitido);
        datePicker.getDatePicker().setMaxDate(maxPermitido);
        datePicker.show();
    }

    // Muestra selector hora en la interfaz.
    private void mostrarSelectorHora(Evento evento, Calendar seleccion, long minPermitido, long maxPermitido) {
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTitleText(R.string.detail_reminder_pick_time_title)
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(seleccion.get(Calendar.HOUR_OF_DAY))
                .setMinute(seleccion.get(Calendar.MINUTE))
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            Calendar resultado = (Calendar) seleccion.clone();
            resultado.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
            resultado.set(Calendar.MINUTE, timePicker.getMinute());
            resultado.set(Calendar.SECOND, 0);
            resultado.set(Calendar.MILLISECOND, 0);

            long remindAt = resultado.getTimeInMillis();
            long minDinamico = System.currentTimeMillis() + REMINDER_MIN_LEAD_MS;
            long minFinal = Math.max(minPermitido, minDinamico);
            long maxFinal = Math.min(maxPermitido, evento.getInicioMillis() - REMINDER_MIN_LEAD_MS);

            if (remindAt < minFinal) {
                Toast.makeText(
                        this,
                        getString(R.string.detail_reminder_after_now_required) + ". "
                                + getString(R.string.detail_reminder_range_hint),
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            if (remindAt > maxFinal) {
                Toast.makeText(
                        this,
                        getString(R.string.detail_reminder_before_event_required) + ". "
                                + getString(R.string.detail_reminder_range_hint),
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            guardarRecordatorio(evento, remindAt);
        });

        timePicker.show(getSupportFragmentManager(), "reminder_time_picker");
    }

    // Guarda recordatorio y sincroniza cambios.
    private void guardarRecordatorio(Evento evento, long remindAtMillis) {
        reminderRepository.guardarRecordatorio(
                evento.getId(),
                evento.getTitulo(),
                evento.getInicioMillis(),
                remindAtMillis,
                new ReminderRepository.Callback() {
                    // Gestiona on ok en este bloque.
                    @Override
                    public void onOk() {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                        String fecha = sdf.format(new Date(remindAtMillis));
                        String ok = getString(R.string.detail_reminder_saved_at, fecha);
                        Toast.makeText(DetailActivity.this, ok, Toast.LENGTH_LONG).show();
                        mostrarEstadoRecordatorio(remindAtMillis);
                    }

                    // Gestiona on error en este bloque.
                    @Override
                    public void onError(Exception e) {
                        String msg = (e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty())
                                ? e.getMessage()
                                : getString(R.string.detail_reminder_error);
                        Toast.makeText(DetailActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    // Carga estado recordatorio desde la fuente correspondiente.
    private void cargarEstadoRecordatorio(Evento evento) {
        reminderRepository.obtenerRecordatorio(evento.getId(), new ReminderRepository.ReminderInfoCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk(ReminderRepository.ReminderInfo info) {
                if (reminderDisponible) {
                    mostrarEstadoRecordatorio(info.remindAtMillis);
                }
            }

            // Gestiona on empty en este bloque.
            @Override
            public void onEmpty() {
                if (reminderDisponible) {
                    mostrarEstadoSinRecordatorio();
                }
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                if (reminderDisponible) {
                    mostrarEstadoSinRecordatorio();
                }
            }
        });
    }

    // Muestra estado recordatorio en la interfaz.
    private void mostrarEstadoRecordatorio(long remindAtMillis) {
        if (tvReminderStatus == null || btnReminder == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        String fecha = sdf.format(new Date(remindAtMillis));
        tvReminderStatus.setText(getString(R.string.detail_reminder_status_active, fecha));
        btnReminder.setText(getString(R.string.detail_reminder_edit_button));
    }

    // Muestra estado sin recordatorio en la interfaz.
    private void mostrarEstadoSinRecordatorio() {
        if (tvReminderStatus == null || btnReminder == null) return;
        tvReminderStatus.setText(getString(R.string.detail_reminder_status_none));
        btnReminder.setText(getString(R.string.detail_reminder_button));
    }

    // Gestiona abrir mapa en este bloque.
    private void abrirMapa(Evento evento) {
        String query = safeText(evento.getDireccion(), evento.getUbicacion());
        if (query.isEmpty()) {
            Toast.makeText(this, getString(R.string.detail_map_error), Toast.LENGTH_SHORT).show();
            return;
        }

        Uri geo = Uri.parse("geo:0,0?q=" + Uri.encode(query));
        Intent intent = new Intent(Intent.ACTION_VIEW, geo);
        intent.setPackage("com.google.android.apps.maps");

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Intent fallback = new Intent(Intent.ACTION_VIEW, geo);
            if (fallback.resolveActivity(getPackageManager()) != null) {
                startActivity(fallback);
            } else {
                Toast.makeText(this, getString(R.string.detail_map_error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Gestiona compartir evento en este bloque.
    private void compartirEvento(Evento evento) {
        String titulo = safeText(evento.getTitulo(), "");
        String fecha = safeText(evento.getFecha(), "");
        String lugar = safeText(evento.getUbicacion(), "");
        String base = getString(R.string.detail_share_text, titulo, fecha, lugar);
        String body = sourceUrl.isEmpty() ? base : (base + "\n" + sourceUrl);

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, titulo);
        send.putExtra(Intent.EXTRA_TEXT, body);
        startActivity(Intent.createChooser(send, getString(R.string.detail_share)));
    }

    // Gestiona abrir enlace en este bloque.
    private void abrirEnlace(String url) {
        String normalizada = normalizarUrl(url);
        if (normalizada.isEmpty()) {
            Toast.makeText(this, getString(R.string.detail_source_open_error), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalizada));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            String guessed = URLUtil.guessUrl(normalizada);
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(guessed));
                fallback.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(fallback);
            } catch (Exception ignored) {
                Toast.makeText(this, getString(R.string.detail_source_open_error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Gestiona extraer primer url en este bloque.
    private String extraerPrimerUrl(String texto) {
        if (texto == null || texto.trim().isEmpty()) return "";
        Matcher matcher = Patterns.WEB_URL.matcher(texto);
        if (matcher.find()) {
            String url = matcher.group();
            return url == null ? "" : url.trim();
        }
        return "";
    }

    // Aplica hipervinculo fuente respetando el estado actual.
    private void aplicarHipervinculoFuente(TextView textView, String url) {
        if (textView == null || url == null || url.trim().isEmpty()) return;

        String urlFinal = normalizarUrl(url);
        if (urlFinal.isEmpty()) return;

        String host = obtenerNombreHost(urlFinal);
        String label = getString(R.string.detail_source_link_fmt, host);
        SpannableString spannable = new SpannableString(label);

        ClickableSpan link = new ClickableSpan() {
            // Gestiona on click en este bloque.
            @Override
            public void onClick(View widget) {
                abrirEnlace(urlFinal);
            }

            // Actualiza draw state con la logica de negocio actual.
            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                ds.setColor(ContextCompat.getColor(DetailActivity.this, R.color.detail_icon_tint));
            }
        };

        spannable.setSpan(link, 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(spannable);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setHighlightColor(Color.TRANSPARENT);
        textView.setLinksClickable(true);
        textView.setClickable(true);
        textView.setOnClickListener(v -> abrirEnlace(urlFinal));
    }

    // Gestiona obtener nombre host en este bloque.
    private String obtenerNombreHost(String url) {
        try {
            String normalizada = normalizarUrl(url);
            if (normalizada.isEmpty()) return "sitio web";
            Uri uri = Uri.parse(normalizada);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return "sitio web";
            String clean = host.toLowerCase(Locale.ROOT);
            if (clean.startsWith("www.")) {
                clean = clean.substring(4);
            }
            return clean;
        } catch (Exception ignored) {
            return "sitio web";
        }
    }

    // Normaliza url para evitar inconsistencias de comparacion.
    private String normalizarUrl(String raw) {
        if (raw == null) return "";
        String url = raw.trim();
        if (url.isEmpty()) return "";

        url = url.replaceAll("[),.;]+$", "");
        if (url.isEmpty()) return "";

        if (url.startsWith("www.")) {
            url = "https://" + url;
        } else if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            url = "https://" + url;
        }

        url = url.replace(" ", "%20");

        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return "";
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                String guessed = URLUtil.guessUrl(url);
                Uri guessedUri = Uri.parse(guessed);
                String guessedHost = guessedUri.getHost();
                String guessedScheme = guessedUri.getScheme();
                if (guessedHost == null || guessedHost.trim().isEmpty()) return "";
                if (guessedScheme == null) return "";
                return guessedUri.toString();
            }
            return uri.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // Gestiona formatear descripcion para vista en este bloque.
    private CharSequence formatearDescripcionParaVista(String rawDescripcion) {
        String base = safeText(rawDescripcion, getString(R.string.detail_description_fallback));

        boolean tieneEtiquetas = base.contains("Fecha:")
                || base.contains("Lugar:")
                || base.contains("Categoria:")
                || base.contains("Tematica:");

        String visual = base;
        if (tieneEtiquetas) {
            visual = visual
                    .replace(". Fecha:", ".\n\n- Fecha:")
                    .replace(". Lugar:", ".\n- Lugar:")
                    .replace(". Categoria:", ".\n- Categoria:")
                    .replace(". Tematica:", ".\n- Tematica:")
                    .replace(". Consulta la web oficial para mas detalles.", ".\n\nConsulta la web oficial para mas detalles.");
        }

        SpannableStringBuilder sb = new SpannableStringBuilder(visual);
        aplicarNegritaEtiqueta(sb, "Fecha:");
        aplicarNegritaEtiqueta(sb, "Lugar:");
        aplicarNegritaEtiqueta(sb, "Categoria:");
        aplicarNegritaEtiqueta(sb, "Tematica:");
        return sb;
    }

    // Gestiona construir resumen evento en este bloque.
    private CharSequence construirResumenEvento(Evento evento, String categoria) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String estado = obtenerEstadoEvento(evento);
        String precio = evento.getPrecio() == 0.0
                ? getString(R.string.detail_price_free)
                : getString(R.string.detail_price_paid_fmt, evento.getPrecio());

        appendResumenLinea(sb, getString(R.string.detail_info_status), estado);
        appendResumenLinea(sb, getString(R.string.detail_info_category), categoria);
        appendResumenLinea(sb, getString(R.string.detail_price_label), precio);
        return sb;
    }

    // Gestiona append resumen linea en este bloque.
    private void appendResumenLinea(SpannableStringBuilder sb, String etiqueta, String valor) {
        if (sb.length() > 0) sb.append('\n');
        int start = sb.length();
        String label = etiqueta + ": ";
        sb.append(label);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(safeText(valor, "-"));
    }

    // Gestiona obtener estado evento en este bloque.
    private String obtenerEstadoEvento(Evento evento) {
        if (evento == null || evento.getInicioMillis() <= 0L) {
            return getString(R.string.detail_status_no_date);
        }

        long now = System.currentTimeMillis();
        long inicio = evento.getInicioMillis();
        if (inicio <= now) {
            return getString(R.string.detail_status_finished);
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String hoy = df.format(new Date(now));
        String fechaEvento = df.format(new Date(inicio));
        if (hoy.equals(fechaEvento)) {
            return getString(R.string.detail_status_today);
        }
        return getString(R.string.detail_status_upcoming);
    }

    // Aplica negrita etiqueta respetando el estado actual.
    private void aplicarNegritaEtiqueta(SpannableStringBuilder sb, String etiqueta) {
        String texto = sb.toString();
        int desde = 0;
        while (true) {
            int pos = texto.indexOf(etiqueta, desde);
            if (pos < 0) break;
            int fin = pos + etiqueta.length();
            sb.setSpan(new StyleSpan(Typeface.BOLD), pos, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            desde = fin;
        }
    }

    // Gestiona safe text en este bloque.
    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }
}

