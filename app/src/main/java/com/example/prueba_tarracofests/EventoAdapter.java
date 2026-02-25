package com.example.prueba_tarracofests;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.R;
import com.bumptech.glide.Glide;

import java.util.List;

public class EventoAdapter extends RecyclerView.Adapter<EventoAdapter.ViewHolder> {
    private List<Evento> lista;
    private OnItemClickListener listener;

    public interface OnItemClickListener { void onItemClick(Evento e); }

    public EventoAdapter(List<Evento> lista, OnItemClickListener listener) {
        this.lista = lista;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_event, p, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Evento e = lista.get(pos);
        h.t.setText(e.titulo);
        h.f.setText(e.fecha);
        Glide.with(h.itemView.getContext()).load(e.imgUrl).into(h.i);
        h.itemView.setOnClickListener(v -> listener.onItemClick(e));
    }

    @Override
    public int getItemCount() { return lista.size(); }

    public void updateList(List<Evento> nuevaLista) {
        this.lista = nuevaLista;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView t, f; ImageView i;
        public ViewHolder(View v) { super(v);
            t = v.findViewById(R.id.tvTitle);
            f = v.findViewById(R.id.tvDate);
            i = v.findViewById(R.id.imgEvent);
        }
    }
}
