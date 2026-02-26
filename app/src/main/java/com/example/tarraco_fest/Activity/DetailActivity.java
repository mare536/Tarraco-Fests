package com.example.tarraco_fest.Activity;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.ReminderRepository;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {

    private final ReminderRepository reminderRepository = new ReminderRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Evento evento = (Evento) getIntent().getSerializableExtra("extra_evento");
        if (evento == null) {
            Toast.makeText(this, getString(R.string.detail_event_not_found), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        vincularDatos(evento);
        configurarRecordatorios(evento);
    }

    private void vincularDatos(Evento evento) {
        TextView txtTitulo = findViewById(R.id.detailTitle);
        TextView txtFecha = findViewById(R.id.detailDate);
        TextView txtUbicacion = findViewById(R.id.detailLocation);
        TextView txtPrecio = findViewById(R.id.detailPrice);
        TextView txtDescripcion = findViewById(R.id.detailDescription);

        ImageView imgView = findViewById(R.id.detailImage);
        int fallbackImage = evento.getImagenResId() != 0 ? evento.getImagenResId() : R.drawable.card_festival;

        if (evento.getImagenUrl() != null && !evento.getImagenUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(evento.getImagenUrl())
                    .placeholder(fallbackImage)
                    .error(fallbackImage)
                    .centerCrop()
                    .into(imgView);
        } else {
            imgView.setImageResource(fallbackImage);
        }

        txtTitulo.setText(evento.getTitulo());
        txtFecha.setText(evento.getFecha());
        txtUbicacion.setText(evento.getUbicacion());
        txtDescripcion.setText(evento.getDescripcion());

        String precioFinal = evento.getPrecio() == 0.0 ? "Gratis" : evento.getPrecio() + " EUR";
        txtPrecio.setText(precioFinal);
    }

    private void configurarRecordatorios(Evento evento) {
        MaterialButton btnReminder = findViewById(R.id.btnDetailReminder);
        btnReminder.setOnClickListener(v -> seleccionarFechaYHoraRecordatorio(evento));
    }

    private void seleccionarFechaYHoraRecordatorio(Evento evento) {
        if (evento.getInicioMillis() <= 0L) {
            Toast.makeText(this, getString(R.string.detail_reminder_invalid_event_date), Toast.LENGTH_LONG).show();
            return;
        }

        Calendar sugerida = Calendar.getInstance();
        long unaHoraAntes = evento.getInicioMillis() - (60L * 60L * 1000L);
        long minimo = System.currentTimeMillis() + (60L * 1000L);
        sugerida.setTimeInMillis(Math.max(unaHoraAntes, minimo));

        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar seleccion = Calendar.getInstance();
                    seleccion.setTimeInMillis(sugerida.getTimeInMillis());
                    seleccion.set(Calendar.YEAR, year);
                    seleccion.set(Calendar.MONTH, month);
                    seleccion.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    mostrarSelectorHora(evento, seleccion);
                },
                sugerida.get(Calendar.YEAR),
                sugerida.get(Calendar.MONTH),
                sugerida.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.getDatePicker().setMinDate(System.currentTimeMillis());
        datePicker.show();
    }

    private void mostrarSelectorHora(Evento evento, Calendar seleccion) {
        TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    seleccion.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    seleccion.set(Calendar.MINUTE, minute);
                    seleccion.set(Calendar.SECOND, 0);
                    seleccion.set(Calendar.MILLISECOND, 0);
                    guardarRecordatorio(evento, seleccion.getTimeInMillis());
                },
                seleccion.get(Calendar.HOUR_OF_DAY),
                seleccion.get(Calendar.MINUTE),
                true
        );
        timePicker.show();
    }

    private void guardarRecordatorio(Evento evento, long remindAtMillis) {
        reminderRepository.guardarRecordatorio(
                evento.getId(),
                evento.getTitulo(),
                evento.getInicioMillis(),
                remindAtMillis,
                new ReminderRepository.Callback() {
                    @Override
                    public void onOk() {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                        String fecha = sdf.format(new Date(remindAtMillis));
                        String ok = getString(R.string.detail_reminder_saved_at, fecha);
                        Toast.makeText(DetailActivity.this, ok, Toast.LENGTH_LONG).show();
                    }

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
}
