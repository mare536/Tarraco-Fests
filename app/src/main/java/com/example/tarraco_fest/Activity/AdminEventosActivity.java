package com.example.tarraco_fest.Activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Adapter.AdminEventosAdapter;
import com.example.tarraco_fest.Modelo.AdminEvento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;
import com.example.tarraco_fest.Repository.AdminEventosRepository;
import com.example.tarraco_fest.Repository.EventosRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminEventosActivity extends AppCompatActivity {

    private static final String FILTRO_TODOS = "todos";
    private static final String FILTRO_ACTIVOS = "activos";
    private static final String FILTRO_INACTIVOS = "inactivos";

    private final AdminAccessRepository accessRepository = new AdminAccessRepository();
    private final AdminEventosRepository eventosRepository = new AdminEventosRepository();
    private final SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private AdminEventosAdapter adapter;
    private final List<AdminEvento> eventos = new ArrayList<>();
    private String busquedaNormalizada = "";
    private String filtroEstadoActual = FILTRO_TODOS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_eventos);

        RecyclerView rv = findViewById(R.id.rvAdminEventos);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AdminEventosAdapter(new AdminEventosAdapter.Listener() {
            @Override
            public void onEditar(AdminEvento evento) {
                mostrarDialogEvento(evento);
            }

            @Override
            public void onToggleActivo(AdminEvento evento) {
                toggleActivo(evento);
            }
        });
        rv.setAdapter(adapter);

        configurarBuscadorYFiltros();
        findViewById(R.id.btnAdminEventosCrear).setOnClickListener(v -> mostrarDialogEvento(null));
        findViewById(R.id.btnAdminEventosRecargar).setOnClickListener(v -> cargarEventos());
        findViewById(R.id.btnAdminEventosVolver).setOnClickListener(v -> finish());

        validarAccesoYCargar();
    }

    private void configurarBuscadorYFiltros() {
        EditText etBuscar = findViewById(R.id.etAdminEventosBuscar);
        RadioGroup rgEstado = findViewById(R.id.rgAdminEventosEstado);

        rgEstado.check(R.id.rbAdminEventosEstadoTodos);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                busquedaNormalizada = normalizar(s == null ? "" : s.toString());
                aplicarFiltrosLocales();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        rgEstado.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbAdminEventosEstadoActivos) {
                filtroEstadoActual = FILTRO_ACTIVOS;
            } else if (checkedId == R.id.rbAdminEventosEstadoInactivos) {
                filtroEstadoActual = FILTRO_INACTIVOS;
            } else {
                filtroEstadoActual = FILTRO_TODOS;
            }
            aplicarFiltrosLocales();
        });
    }

    private void validarAccesoYCargar() {
        accessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            @Override
            public void onResult(boolean isAdmin) {
                if (!isAdmin) {
                    Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_access_denied), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                cargarEventos();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_access_error), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void cargarEventos() {
        eventosRepository.cargarEventos(new AdminEventosRepository.ListCallback() {
            @Override
            public void onOk(List<AdminEvento> eventos) {
                AdminEventosActivity.this.eventos.clear();
                AdminEventosActivity.this.eventos.addAll(eventos);
                aplicarFiltrosLocales();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_events_load_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void aplicarFiltrosLocales() {
        List<AdminEvento> filtrados = new ArrayList<>();

        for (AdminEvento e : eventos) {
            String titulo = normalizar(e.titulo);
            String lugar = normalizar(e.lugarNombre);
            boolean coincideTexto = busquedaNormalizada.isEmpty()
                    || titulo.contains(busquedaNormalizada)
                    || lugar.contains(busquedaNormalizada);

            boolean coincideEstado = FILTRO_TODOS.equals(filtroEstadoActual)
                    || (FILTRO_ACTIVOS.equals(filtroEstadoActual) && e.activo)
                    || (FILTRO_INACTIVOS.equals(filtroEstadoActual) && !e.activo);

            if (coincideTexto && coincideEstado) {
                filtrados.add(e);
            }
        }

        adapter.setData(filtrados);
    }

    private void mostrarDialogEvento(AdminEvento original) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_evento, null);
        EditText etTitulo = view.findViewById(R.id.etAdminEventoTitulo);
        EditText etLugar = view.findViewById(R.id.etAdminEventoLugar);
        EditText etFecha = view.findViewById(R.id.etAdminEventoFecha);
        CheckBox cbActivo = view.findViewById(R.id.cbAdminEventoActivo);

        if (original != null) {
            etTitulo.setText(original.titulo);
            etLugar.setText(original.lugarNombre);
            if (original.inicio != null) {
                etFecha.setText(inputFormat.format(original.inicio.toDate()));
            }
            cbActivo.setChecked(original.activo);
        } else {
            cbActivo.setChecked(true);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle(original == null ? getString(R.string.admin_event_create_title) : getString(R.string.admin_event_edit_title))
                .setView(view)
                .setNegativeButton(getString(R.string.admin_cancel), (d, w) -> d.dismiss())
                .setPositiveButton(getString(R.string.admin_save), null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String titulo = texto(etTitulo);
            String lugar = texto(etLugar);
            String fechaRaw = texto(etFecha);

            if (titulo.isEmpty()) {
                etTitulo.setError(getString(R.string.admin_event_title_required));
                return;
            }

            Timestamp inicio = parseTimestamp(fechaRaw);
            if (inicio == null) {
                etFecha.setError(getString(R.string.admin_event_date_invalid));
                return;
            }

            AdminEvento evento = (original == null) ? new AdminEvento() : original;
            evento.titulo = titulo;
            evento.lugarNombre = lugar;
            evento.inicio = inicio;
            evento.activo = cbActivo.isChecked();

            if (original == null) {
                eventosRepository.crearEvento(evento, new AdminEventosRepository.ActionCallback() {
                    @Override
                    public void onOk() {
                        EventosRepository.invalidarCacheApi();
                        Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_saved_ok), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        cargarEventos();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_save_error), Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                eventosRepository.actualizarEvento(evento, new AdminEventosRepository.ActionCallback() {
                    @Override
                    public void onOk() {
                        EventosRepository.invalidarCacheApi();
                        Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_saved_ok), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        cargarEventos();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_save_error), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void toggleActivo(AdminEvento evento) {
        eventosRepository.actualizarActivo(evento.id, !evento.activo, new AdminEventosRepository.ActionCallback() {
            @Override
            public void onOk() {
                EventosRepository.invalidarCacheApi();
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_saved_ok), Toast.LENGTH_SHORT).show();
                cargarEventos();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_save_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private String texto(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private Timestamp parseTimestamp(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            Date date = inputFormat.parse(raw);
            if (date == null) return null;
            return new Timestamp(date);
        } catch (ParseException e) {
            return null;
        }
    }

    private String normalizar(String value) {
        if (value == null) return "";
        String base = value.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
