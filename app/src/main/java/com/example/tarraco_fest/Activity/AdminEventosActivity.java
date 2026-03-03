package com.example.tarraco_fest.Activity;

import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tarraco_fest.Adapter.AdminEventosAdapter;
import com.example.tarraco_fest.Modelo.AdminEvento;
import com.example.tarraco_fest.R;
import com.example.tarraco_fest.Repository.AdminAccessRepository;
import com.example.tarraco_fest.Repository.AdminEventosRepository;
import com.example.tarraco_fest.Repository.EventosRepository;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;
import com.google.firebase.storage.StorageException;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pantalla de administracion para crear, editar y activar/desactivar eventos.
 * Tambien gestiona la seleccion y subida de imagenes del evento en Storage.
 */
public class AdminEventosActivity extends AppCompatActivity {
    private static final String TAG = "AdminEventosActivity";
    private static final String PREF_SYNC_FLAGS = "sync_flags";
    private static final String KEY_EVENTOS_UPDATED_AT = "eventos_updated_at";

    private static final String FILTRO_TODOS = "todos";
    private static final String FILTRO_ACTIVOS = "activos";
    private static final String FILTRO_INACTIVOS = "inactivos";
    private static final String CATEGORIA_CULTURA = "cultura";
    private static final String CATEGORIA_MUSICA = "musica";
    private static final String CATEGORIA_ESPORT = "esport";
    private static final String CATEGORIA_FAMILIAR = "familiar";
    private static final String CATEGORIA_GASTRONOMIA = "gastronomia";

    private final AdminAccessRepository accessRepository = new AdminAccessRepository();
    private final AdminEventosRepository eventosRepository = new AdminEventosRepository();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private AdminEventosAdapter adapter;
    private final List<AdminEvento> eventos = new ArrayList<>();
    private String busquedaNormalizada = "";
    private String filtroEstadoActual = FILTRO_TODOS;
    private Uri imagenSeleccionadaUri;
    private ImageView ivImagenDialog;
    private TextView tvImagenEstadoDialog;
    private boolean imagenPersonalizadaEnDialogo = false;

    private final ActivityResultLauncher<String> pickerImagenLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                imagenSeleccionadaUri = uri;
                imagenPersonalizadaEnDialogo = true;
                if (ivImagenDialog != null) {
                    Glide.with(this)
                            .load(uri)
                            .placeholder(R.drawable.card_festival)
                            .error(R.drawable.card_festival)
                            .centerCrop()
                            .into(ivImagenDialog);
                }
                if (tvImagenEstadoDialog != null) {
                    tvImagenEstadoDialog.setText(getString(R.string.admin_event_image_selected));
                }
            });

    // Gestiona on create en este bloque.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_eventos);

        RecyclerView rv = findViewById(R.id.rvAdminEventos);
        rv.setLayoutManager(new LinearLayoutManager(this));
        configurarBordesSistema(rv);

        adapter = new AdminEventosAdapter(new AdminEventosAdapter.Listener() {
            // Gestiona on editar en este bloque.
            @Override
            public void onEditar(AdminEvento evento) {
                mostrarDialogEvento(evento);
            }

            // Gestiona on toggle activo en este bloque.
            @Override
            public void onToggleActivo(AdminEvento evento) {
                toggleActivo(evento);
            }

            // Gestiona on eliminar en este bloque.
            @Override
            public void onEliminar(AdminEvento evento) {
                confirmarEliminarEvento(evento);
            }
        });
        rv.setAdapter(adapter);

        configurarBuscadorYFiltros();
        findViewById(R.id.btnAdminEventosCrear).setOnClickListener(v -> mostrarDialogEvento(null));
        findViewById(R.id.btnAdminEventosRecargar).setOnClickListener(v -> cargarEventos());
        findViewById(R.id.btnAdminEventosVolver).setOnClickListener(v -> finish());

        validarAccesoYCargar();
    }

    // Aplica edge-to-edge y ajusta insets para evitar franjas blancas y solapes con barras del sistema.
    private void configurarBordesSistema(RecyclerView rv) {
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

        View root = findViewById(R.id.rootAdminEventos);
        View appBar = findViewById(R.id.appBarAdminEventos);

        int baseAppBarPaddingTop = appBar.getPaddingTop();
        int baseRecyclerPaddingBottom = rv.getPaddingBottom();
        ViewGroup.MarginLayoutParams lpLista = (ViewGroup.MarginLayoutParams) rv.getLayoutParams();
        int baseListaMarginBottom = lpLista.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int safeBottom = Math.max(bars.bottom, ime.bottom);

            appBar.setPadding(
                    appBar.getPaddingLeft(),
                    baseAppBarPaddingTop + bars.top,
                    appBar.getPaddingRight(),
                    appBar.getPaddingBottom()
            );

            ViewGroup.MarginLayoutParams listaLp = (ViewGroup.MarginLayoutParams) rv.getLayoutParams();
            listaLp.bottomMargin = baseListaMarginBottom + safeBottom;
            rv.setLayoutParams(listaLp);

            rv.setPadding(
                    rv.getPaddingLeft(),
                    rv.getPaddingTop(),
                    rv.getPaddingRight(),
                    baseRecyclerPaddingBottom + safeBottom + dpToPx(8)
            );

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    // Convierte dp a px para padding/margenes calculados por codigo.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // Configura buscador yfiltros segun el contexto actual.
    private void configurarBuscadorYFiltros() {
        EditText etBuscar = findViewById(R.id.etAdminEventosBuscar);
        RadioGroup rgEstado = findViewById(R.id.rgAdminEventosEstado);

        rgEstado.check(R.id.rbAdminEventosEstadoTodos);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            // Gestiona on text changed en este bloque.
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

    // Valida acceso ycargar antes de continuar el flujo.
    private void validarAccesoYCargar() {
        accessRepository.verificarAccesoAdmin(new AdminAccessRepository.Callback() {
            // Gestiona on result en este bloque.
            @Override
            public void onResult(boolean isAdmin) {
                if (!isAdmin) {
                    Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_access_denied), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                cargarEventos();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_access_error), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    // Carga eventos desde la fuente correspondiente.
    private void cargarEventos() {
        eventosRepository.cargarEventos(new AdminEventosRepository.ListCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk(List<AdminEvento> eventos) {
                AdminEventosActivity.this.eventos.clear();
                AdminEventosActivity.this.eventos.addAll(eventos);
                aplicarFiltrosLocales();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_events_load_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Aplica filtros locales respetando el estado actual.
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

    // Muestra dialog evento en la interfaz.
    private void mostrarDialogEvento(AdminEvento original) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_evento, null);
        EditText etTitulo = view.findViewById(R.id.etAdminEventoTitulo);
        EditText etLugar = view.findViewById(R.id.etAdminEventoLugar);
        MaterialAutoCompleteTextView actCategoria = view.findViewById(R.id.actAdminEventoCategoria);
        EditText etFecha = view.findViewById(R.id.etAdminEventoFecha);
        EditText etHora = view.findViewById(R.id.etAdminEventoHora);
        TextInputLayout tilFecha = view.findViewById(R.id.tilAdminEventoFecha);
        TextInputLayout tilHora = view.findViewById(R.id.tilAdminEventoHora);
        CheckBox cbActivo = view.findViewById(R.id.cbAdminEventoActivo);
        ImageView ivImagen = view.findViewById(R.id.ivAdminEventoImagen);
        TextView tvImagenEstado = view.findViewById(R.id.tvAdminEventoImagenEstado);
        Button btnSeleccionarImagen = view.findViewById(R.id.btnAdminEventoSeleccionarImagen);
        Button btnFecha = view.findViewById(R.id.btnAdminEventoFecha);
        Button btnHora = view.findViewById(R.id.btnAdminEventoHora);
        Calendar calendarioInicio = Calendar.getInstance();
        boolean[] fechaSeleccionada = {false};
        boolean[] horaSeleccionada = {false};
        String[] categoriaSeleccionada = {CATEGORIA_CULTURA};

        ArrayAdapter<String> categoriaAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                obtenerCategoriasUi()
        );
        actCategoria.setAdapter(categoriaAdapter);
        actCategoria.setOnClickListener(v -> actCategoria.showDropDown());
        actCategoria.setOnItemClickListener((parent, itemView, position, id) -> {
            String categoriaUi = (String) parent.getItemAtPosition(position);
            categoriaSeleccionada[0] = categoriaIdDesdeUi(categoriaUi);
            if (!imagenPersonalizadaEnDialogo) {
                aplicarImagenPredeterminada(categoriaSeleccionada[0], ivImagen, tvImagenEstado);
            }
        });

        imagenSeleccionadaUri = null;
        imagenPersonalizadaEnDialogo = false;
        ivImagenDialog = ivImagen;
        tvImagenEstadoDialog = tvImagenEstado;
        btnSeleccionarImagen.setOnClickListener(v -> pickerImagenLauncher.launch("image/*"));

        if (original != null) {
            etTitulo.setText(original.titulo);
            etLugar.setText(original.lugarNombre);
            categoriaSeleccionada[0] = normalizarCategoriaId(original.categoriaId);
            actCategoria.setText(categoriaUiDesdeId(categoriaSeleccionada[0]), false);
            if (original.inicio != null) {
                calendarioInicio.setTime(original.inicio.toDate());
                fechaSeleccionada[0] = true;
                horaSeleccionada[0] = true;
            }
            cbActivo.setChecked(original.activo);
            if (!TextUtils.isEmpty(original.imagenUrl)) {
                imagenPersonalizadaEnDialogo = true;
                Glide.with(this)
                        .load(original.imagenUrl)
                        .placeholder(R.drawable.card_festival)
                        .error(R.drawable.card_festival)
                        .centerCrop()
                        .into(ivImagen);
                tvImagenEstado.setText(getString(R.string.admin_event_image_current));
            } else if (!TextUtils.isEmpty(original.imagenBase64)) {
                byte[] bytes = decodeBase64Image(original.imagenBase64);
                if (bytes != null) {
                    imagenPersonalizadaEnDialogo = true;
                    Glide.with(this)
                            .load(bytes)
                            .placeholder(R.drawable.card_festival)
                            .error(R.drawable.card_festival)
                            .centerCrop()
                            .into(ivImagen);
                    tvImagenEstado.setText(getString(R.string.admin_event_image_current));
                } else {
                    imagenPersonalizadaEnDialogo = false;
                    aplicarImagenPredeterminada(categoriaSeleccionada[0], ivImagen, tvImagenEstado);
                }
            } else {
                imagenPersonalizadaEnDialogo = false;
                aplicarImagenPredeterminada(categoriaSeleccionada[0], ivImagen, tvImagenEstado);
            }
        } else {
            cbActivo.setChecked(true);
            categoriaSeleccionada[0] = CATEGORIA_CULTURA;
            actCategoria.setText(categoriaUiDesdeId(categoriaSeleccionada[0]), false);
            aplicarImagenPredeterminada(categoriaSeleccionada[0], ivImagen, tvImagenEstado);
            calendarioInicio.set(Calendar.SECOND, 0);
            calendarioInicio.set(Calendar.MILLISECOND, 0);
        }

        actualizarCamposFechaHora(etFecha, etHora, calendarioInicio, fechaSeleccionada[0], horaSeleccionada[0]);
        configurarCampoSelector(etFecha, v -> abrirSelectorFecha(calendarioInicio, () -> {
            fechaSeleccionada[0] = true;
            tilFecha.setError(null);
            actualizarCamposFechaHora(etFecha, etHora, calendarioInicio, fechaSeleccionada[0], horaSeleccionada[0]);
        }));
        configurarCampoSelector(etHora, v -> abrirSelectorHora(calendarioInicio, () -> {
            horaSeleccionada[0] = true;
            tilHora.setError(null);
            actualizarCamposFechaHora(etFecha, etHora, calendarioInicio, fechaSeleccionada[0], horaSeleccionada[0]);
        }));
        btnFecha.setOnClickListener(v -> abrirSelectorFecha(calendarioInicio, () -> {
            fechaSeleccionada[0] = true;
            tilFecha.setError(null);
            actualizarCamposFechaHora(etFecha, etHora, calendarioInicio, fechaSeleccionada[0], horaSeleccionada[0]);
        }));
        btnHora.setOnClickListener(v -> abrirSelectorHora(calendarioInicio, () -> {
            horaSeleccionada[0] = true;
            tilHora.setError(null);
            actualizarCamposFechaHora(etFecha, etHora, calendarioInicio, fechaSeleccionada[0], horaSeleccionada[0]);
        }));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle(original == null ? getString(R.string.admin_event_create_title) : getString(R.string.admin_event_edit_title))
                .setView(view)
                .setNegativeButton(getString(R.string.admin_cancel), (d, w) -> d.dismiss())
                .setPositiveButton(getString(R.string.admin_save), null)
                .create();

        dialog.setOnDismissListener(d -> {
            imagenSeleccionadaUri = null;
            imagenPersonalizadaEnDialogo = false;
            ivImagenDialog = null;
            tvImagenEstadoDialog = null;
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String titulo = texto(etTitulo);
            String lugar = texto(etLugar);

            if (titulo.isEmpty()) {
                etTitulo.setError(getString(R.string.admin_event_title_required));
                return;
            }

            if (!fechaSeleccionada[0]) {
                tilFecha.setError(getString(R.string.admin_event_pick_date_required));
                return;
            }
            if (!horaSeleccionada[0]) {
                tilHora.setError(getString(R.string.admin_event_pick_time_required));
                return;
            }
            tilFecha.setError(null);
            tilHora.setError(null);
            Timestamp inicio = new Timestamp(calendarioInicio.getTime());

            AdminEvento evento = (original == null) ? new AdminEvento() : original;
            evento.titulo = titulo;
            evento.categoriaId = categoriaIdDesdeUi(texto(actCategoria));
            evento.lugarNombre = lugar;
            evento.inicio = inicio;
            evento.activo = cbActivo.isChecked();
            if (!imagenPersonalizadaEnDialogo) {
                evento.imagenUrl = "";
                evento.imagenBase64 = "";
            }
            guardarEventoConImagen(evento, original == null, dialog);
        });
    }

    // Configura un campo de texto como selector para evitar errores de escritura manual.
    private void configurarCampoSelector(EditText campo, View.OnClickListener onClick) {
        campo.setKeyListener(null);
        campo.setFocusable(false);
        campo.setFocusableInTouchMode(false);
        campo.setCursorVisible(false);
        campo.setOnClickListener(onClick);
    }

    // Refresca los campos visibles de fecha y hora segun la seleccion actual.
    private void actualizarCamposFechaHora(EditText etFecha, EditText etHora, Calendar calendario,
                                           boolean fechaSeleccionada, boolean horaSeleccionada) {
        etFecha.setText(fechaSeleccionada ? dateFormat.format(calendario.getTime()) : "");
        etHora.setText(horaSeleccionada ? timeFormat.format(calendario.getTime()) : "");
    }

    // Abre calendario Material para elegir fecha valida sin escribir formato manual.
    private void abrirSelectorFecha(Calendar calendario, Runnable onSeleccionado) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.admin_event_pick_date_button))
                .setSelection(calendario.getTimeInMillis())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) return;
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(selection);

            calendario.set(Calendar.YEAR, utc.get(Calendar.YEAR));
            calendario.set(Calendar.MONTH, utc.get(Calendar.MONTH));
            calendario.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH));
            if (onSeleccionado != null) onSeleccionado.run();
        });

        datePicker.show(getSupportFragmentManager(), "admin_event_date_picker");
    }

    // Abre reloj Material para elegir hora valida sin escribir formato manual.
    private void abrirSelectorHora(Calendar calendario, Runnable onSeleccionado) {
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTitleText(getString(R.string.admin_event_pick_time_button))
                .setHour(calendario.get(Calendar.HOUR_OF_DAY))
                .setMinute(calendario.get(Calendar.MINUTE))
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            calendario.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
            calendario.set(Calendar.MINUTE, timePicker.getMinute());
            calendario.set(Calendar.SECOND, 0);
            calendario.set(Calendar.MILLISECOND, 0);
            if (onSeleccionado != null) onSeleccionado.run();
        });

        timePicker.show(getSupportFragmentManager(), "admin_event_time_picker");
    }

    // Guarda evento con imagen y sincroniza cambios.
    private void guardarEventoConImagen(AdminEvento evento, boolean crear, AlertDialog dialog) {
        Button btnGuardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btnGuardar != null) btnGuardar.setEnabled(false);

        if (imagenSeleccionadaUri != null) {
            if (tvImagenEstadoDialog != null) {
                tvImagenEstadoDialog.setText(getString(R.string.admin_event_image_uploading));
            }
            Log.d(TAG, "Intentando subir imagen URI: " + imagenSeleccionadaUri);
            eventosRepository.subirImagenEvento(AdminEventosActivity.this, imagenSeleccionadaUri, new AdminEventosRepository.ImageUploadCallback() {
                // Gestiona on ok en este bloque.
                @Override
                public void onOk(String imageBase64) {
                    evento.imagenBase64 = imageBase64;
                    // Si existe imagen inline nueva, evitamos depender de Storage.
                    evento.imagenUrl = "";
                    guardarEventoEnFirestore(evento, crear, dialog, btnGuardar);
                }

                // Gestiona on error en este bloque.
                @Override
                public void onError(Exception e) {
                    if (btnGuardar != null) btnGuardar.setEnabled(true);
                    int messageRes = R.string.admin_event_image_upload_error;
                    String messageCustom = null;
                    StorageException se = extraerStorageException(e);
                    if (se != null) {
                        Log.e(TAG, "Error Storage al subir imagen. code=" + se.getErrorCode() + ", msg=" + se.getMessage(), se);
                        switch (se.getErrorCode()) {
                            case StorageException.ERROR_NOT_AUTHORIZED:
                                messageRes = R.string.admin_event_image_upload_no_permission;
                                break;
                            case StorageException.ERROR_NOT_AUTHENTICATED:
                                messageRes = R.string.admin_event_image_upload_not_authenticated;
                                break;
                            case StorageException.ERROR_BUCKET_NOT_FOUND:
                            case StorageException.ERROR_PROJECT_NOT_FOUND:
                                messageRes = R.string.admin_event_image_upload_bucket_error;
                                break;
                            case StorageException.ERROR_QUOTA_EXCEEDED:
                                messageRes = R.string.admin_event_image_upload_quota_error;
                                break;
                            case StorageException.ERROR_RETRY_LIMIT_EXCEEDED:
                                messageRes = R.string.admin_event_image_upload_network_error;
                                break;
                            case StorageException.ERROR_OBJECT_NOT_FOUND:
                                // En algunas configuraciones, -13010 llega durante putFile
                                // cuando el bucket configurado no coincide con el real.
                                messageRes = R.string.admin_event_image_upload_bucket_error;
                                break;
                            case StorageException.ERROR_UNKNOWN:
                                messageCustom = getString(
                                        R.string.admin_event_image_upload_unknown_fmt,
                                        se.getErrorCode(),
                                        textoErrorSeguro(se.getMessage())
                                );
                                break;
                            default:
                                messageCustom = getString(
                                        R.string.admin_event_image_upload_unknown_fmt,
                                        se.getErrorCode(),
                                        textoErrorSeguro(se.getMessage())
                                );
                                break;
                        }
                    } else if (e instanceof IllegalArgumentException) {
                        messageRes = R.string.admin_event_image_upload_invalid_source;
                    } else if (tieneCausa(e, FileNotFoundException.class) || tieneCausa(e, SecurityException.class)) {
                        messageRes = R.string.admin_event_image_upload_invalid_source;
                    } else {
                        Log.e(TAG, "Error no controlado al subir imagen: " + e.getMessage(), e);
                        messageCustom = getString(
                                R.string.admin_event_image_upload_unknown_exception_fmt,
                                e.getClass().getSimpleName(),
                                textoErrorSeguro(e.getMessage())
                        );
                    }

                    String finalMessage = messageCustom != null ? messageCustom : getString(messageRes);
                    Toast.makeText(AdminEventosActivity.this, finalMessage, Toast.LENGTH_LONG).show();
                    if (tvImagenEstadoDialog != null) {
                        tvImagenEstadoDialog.setText(finalMessage);
                    }
                }
            });
            return;
        }

        guardarEventoEnFirestore(evento, crear, dialog, btnGuardar);
    }

    // Extrae StorageException aunque venga envuelta en otras excepciones.
    private StorageException extraerStorageException(Throwable throwable) {
        Throwable actual = throwable;
        while (actual != null) {
            if (actual instanceof StorageException) return (StorageException) actual;
            actual = actual.getCause();
        }
        return null;
    }

    // Verifica si una excepcion concreta existe en la cadena de causas.
    private boolean tieneCausa(Throwable throwable, Class<? extends Throwable> tipo) {
        Throwable actual = throwable;
        while (actual != null) {
            if (tipo.isInstance(actual)) return true;
            actual = actual.getCause();
        }
        return false;
    }

    // Devuelve un texto de error legible para UI cuando Firebase no incluye detalle.
    private String textoErrorSeguro(String value) {
        if (value == null) return "sin detalle";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "sin detalle" : trimmed;
    }

    // Decodifica Base64 de imagen sin lanzar excepciones hacia la UI.
    private byte[] decodeBase64Image(String encoded) {
        if (encoded == null) return null;
        String value = encoded.trim();
        if (value.isEmpty()) return null;
        try {
            return Base64.decode(value, Base64.DEFAULT);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Guarda evento en firestore y sincroniza cambios.
    private void guardarEventoEnFirestore(AdminEvento evento, boolean crear, AlertDialog dialog, Button btnGuardar) {
        AdminEventosRepository.ActionCallback callback = new AdminEventosRepository.ActionCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk() {
                EventosRepository.invalidarCacheApi();
                marcarEventosActualizados();
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_saved_ok), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                cargarEventos();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                if (btnGuardar != null) btnGuardar.setEnabled(true);
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_save_error), Toast.LENGTH_LONG).show();
            }
        };

        if (crear) {
            eventosRepository.crearEvento(evento, callback);
        } else {
            eventosRepository.actualizarEvento(evento, callback);
        }
    }

    // Alterna el estado de activo.
    private void toggleActivo(AdminEvento evento) {
        eventosRepository.actualizarActivo(evento.id, !evento.activo, new AdminEventosRepository.ActionCallback() {
            // Gestiona on ok en este bloque.
            @Override
            public void onOk() {
                EventosRepository.invalidarCacheApi();
                marcarEventosActualizados();
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_saved_ok), Toast.LENGTH_SHORT).show();
                cargarEventos();
            }

            // Gestiona on error en este bloque.
            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_save_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Muestra confirmacion antes de eliminar un evento inactivo.
    private void confirmarEliminarEvento(AdminEvento evento) {
        if (evento == null || evento.id == null || evento.id.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.admin_event_delete_error), Toast.LENGTH_LONG).show();
            return;
        }

        String titulo = TextUtils.isEmpty(evento.titulo) ? "-" : evento.titulo;

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TarracoFests_RegisterDialog)
                .setTitle(getString(R.string.admin_event_delete_confirm_title))
                .setMessage(getString(R.string.admin_event_delete_confirm_message, titulo))
                .setNegativeButton(getString(R.string.admin_cancel), null)
                .setPositiveButton(getString(R.string.admin_event_delete), (d, w) -> eliminarEvento(evento))
                .show();
    }

    // Elimina el evento y refresca la lista local.
    private void eliminarEvento(AdminEvento evento) {
        eventosRepository.eliminarEvento(evento.id, new AdminEventosRepository.ActionCallback() {
            @Override
            public void onOk() {
                EventosRepository.invalidarCacheApi();
                marcarEventosActualizados();
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_deleted_ok), Toast.LENGTH_SHORT).show();
                cargarEventos();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AdminEventosActivity.this, getString(R.string.admin_event_delete_error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Marca en almacenamiento local que hubo cambios de eventos para forzar refresco en Home.
    private void marcarEventosActualizados() {
        SharedPreferences prefs = getSharedPreferences(PREF_SYNC_FLAGS, MODE_PRIVATE);
        prefs.edit().putLong(KEY_EVENTOS_UPDATED_AT, System.currentTimeMillis()).apply();
    }

    // Devuelve las categorias visibles para el selector del formulario admin.
    private String[] obtenerCategoriasUi() {
        return new String[]{
                getString(R.string.admin_event_category_cultura),
                getString(R.string.admin_event_category_musica),
                getString(R.string.admin_event_category_esport),
                getString(R.string.admin_event_category_familiar),
                getString(R.string.admin_event_category_gastronomia)
        };
    }

    // Mapea texto visible de categoria a id interno persistido en Firestore.
    private String categoriaIdDesdeUi(String categoriaUi) {
        String normalized = normalizar(categoriaUi);
        if (normalized.contains("music")) return CATEGORIA_MUSICA;
        if (normalized.contains("esport") || normalized.contains("sport") || normalized.contains("deport")) {
            return CATEGORIA_ESPORT;
        }
        if (normalized.contains("famil")) return CATEGORIA_FAMILIAR;
        if (normalized.contains("gastronom")) return CATEGORIA_GASTRONOMIA;
        return CATEGORIA_CULTURA;
    }

    // Mapea id interno de categoria al texto visible para el formulario.
    private String categoriaUiDesdeId(String categoriaId) {
        String normalized = normalizarCategoriaId(categoriaId);
        if (CATEGORIA_MUSICA.equals(normalized)) return getString(R.string.admin_event_category_musica);
        if (CATEGORIA_ESPORT.equals(normalized)) return getString(R.string.admin_event_category_esport);
        if (CATEGORIA_FAMILIAR.equals(normalized)) return getString(R.string.admin_event_category_familiar);
        if (CATEGORIA_GASTRONOMIA.equals(normalized)) return getString(R.string.admin_event_category_gastronomia);
        return getString(R.string.admin_event_category_cultura);
    }

    // Normaliza categoria interna para evitar nulos o valores incompletos.
    private String normalizarCategoriaId(String categoriaId) {
        String normalized = normalizar(categoriaId);
        if (normalized.contains("music")) return CATEGORIA_MUSICA;
        if (normalized.contains("esport") || normalized.contains("sport") || normalized.contains("deport")) {
            return CATEGORIA_ESPORT;
        }
        if (normalized.contains("famil")) return CATEGORIA_FAMILIAR;
        if (normalized.contains("gastronom")) return CATEGORIA_GASTRONOMIA;
        return CATEGORIA_CULTURA;
    }

    // Asigna imagen predeterminada de categoria cuando no existe imagen personalizada.
    private void aplicarImagenPredeterminada(String categoriaId, ImageView imageView, TextView estadoView) {
        imageView.setImageResource(obtenerImagenPredeterminadaCategoria(categoriaId));
        estadoView.setText(getString(R.string.admin_event_image_default_by_category));
    }

    // Resuelve drawable predeterminado de tarjeta segun categoria.
    private int obtenerImagenPredeterminadaCategoria(String categoriaId) {
        String normalized = normalizarCategoriaId(categoriaId);
        if (CATEGORIA_MUSICA.equals(normalized)) return R.drawable.card_musica;
        if (CATEGORIA_ESPORT.equals(normalized)) return R.drawable.card_esport;
        if (CATEGORIA_FAMILIAR.equals(normalized)) return R.drawable.card_familiar;
        if (CATEGORIA_GASTRONOMIA.equals(normalized)) return R.drawable.card_gastronomia;
        return R.drawable.card_cultura;
    }

    // Gestiona texto en este bloque.
    private String texto(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    // Normaliza el flujo para evitar inconsistencias de comparacion.
    private String normalizar(String value) {
        if (value == null) return "";
        String base = value.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
