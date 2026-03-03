package com.example.tarraco_fest.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Modelo.AdminEvento;
import com.example.tarraco_fest.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter de eventos en el panel admin.
 * Pinta estado del evento y propaga acciones de editar/activar.
 */
public class AdminEventosAdapter extends RecyclerView.Adapter<AdminEventosAdapter.VH> {

    public interface Listener {
        void onEditar(AdminEvento evento);
        void onToggleActivo(AdminEvento evento);
    }

    private final List<AdminEvento> data = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public AdminEventosAdapter(Listener listener) {
        this.listener = listener;
    }

    // Actualiza data con el valor recibido.
    public void setData(List<AdminEvento> eventos) {
        data.clear();
        if (eventos != null) data.addAll(eventos);
        notifyDataSetChanged();
    }

    // Gestiona on create view holder en este bloque.
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_evento, parent, false);
        return new VH(v);
    }

    // Gestiona on bind view holder en este bloque.
    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AdminEvento e = data.get(pos);

        h.tvTitulo.setText(e.titulo.isEmpty() ? "(Sin titulo)" : e.titulo);
        h.tvLugar.setText(h.itemView.getContext().getString(
                R.string.admin_event_place_fmt,
                e.lugarNombre.isEmpty() ? "-" : e.lugarNombre));
        h.tvEstado.setText(h.itemView.getContext().getString(
                e.activo ? R.string.admin_event_active : R.string.admin_event_inactive));
        h.tvEstado.setBackgroundResource(e.activo
                ? R.drawable.bg_admin_status_active
                : R.drawable.bg_admin_status_inactive);

        if (e.inicio != null) {
            Date d = e.inicio.toDate();
            h.tvFecha.setText(h.itemView.getContext().getString(
                    R.string.admin_event_date_fmt,
                    fmt.format(d)));
        } else {
            h.tvFecha.setText(h.itemView.getContext().getString(
                    R.string.admin_event_date_fmt, "-"));
        }

        h.btnToggleActivo.setText(h.itemView.getContext().getString(
                e.activo ? R.string.admin_event_deactivate : R.string.admin_event_activate));
        h.btnToggleActivo.setBackgroundResource(e.activo
                ? R.drawable.btn_auth_secondary
                : R.drawable.btn_auth_primary);
        h.btnToggleActivo.setTextColor(ContextCompat.getColor(
                h.itemView.getContext(),
                e.activo ? R.color.auth_btn_secondary_text : R.color.auth_btn_primary_text
        ));

        h.btnEditar.setOnClickListener(v -> listener.onEditar(e));
        h.btnToggleActivo.setOnClickListener(v -> listener.onToggleActivo(e));
    }

    // Devuelve item count.
    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvFecha, tvLugar, tvEstado;
        Button btnEditar, btnToggleActivo;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitulo = itemView.findViewById(R.id.tvAdminEventoTitulo);
            tvFecha = itemView.findViewById(R.id.tvAdminEventoFecha);
            tvLugar = itemView.findViewById(R.id.tvAdminEventoLugar);
            tvEstado = itemView.findViewById(R.id.tvAdminEventoEstado);
            btnEditar = itemView.findViewById(R.id.btnAdminEventoEditar);
            btnToggleActivo = itemView.findViewById(R.id.btnAdminEventoActivo);
        }
    }
}
