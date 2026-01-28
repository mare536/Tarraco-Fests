package com.example.tarraco_fest.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventosAdapter extends RecyclerView.Adapter<EventosAdapter.VH> {

    public interface OnEventoClick {
        void onClick(Evento e);
    }

    private final List<Evento> data = new ArrayList<>();
    private final OnEventoClick listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public EventosAdapter(OnEventoClick listener) {
        this.listener = listener;
    }

    public void setData(List<Evento> nuevos) {
        data.clear();
        data.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evento, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Evento e = data.get(pos);

        h.tvTitulo.setText(e.titulo != null ? e.titulo : "(Sin título)");

        if (e.inicio != null) {
            Date d = e.inicio.toDate();
            h.tvFecha.setText(fmt.format(d));
        } else {
            h.tvFecha.setText("(Sin fecha)");
        }

        h.tvLugar.setText(e.lugar != null ? e.lugar : "(Sin lugar)");

        h.itemView.setOnClickListener(v -> listener.onClick(e));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvFecha, tvLugar;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitulo = itemView.findViewById(R.id.tvTituloEvento);
            tvFecha = itemView.findViewById(R.id.tvFechaEvento);
            tvLugar = itemView.findViewById(R.id.tvLugarEvento);
        }
    }
}
