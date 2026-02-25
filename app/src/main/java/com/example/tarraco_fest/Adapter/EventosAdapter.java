package com.example.tarraco_fest.Adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Activity.DetailActivity;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;

import java.util.List;

public class EventosAdapter extends RecyclerView.Adapter<EventosAdapter.EventoViewHolder> {

    private List<Evento> listaEventos;
    private final Context context;

    // Constructor
    public EventosAdapter(List<Evento> listaEventos, Context context) {
        this.listaEventos = listaEventos;
        this.context = context;
    }

    // MÉTODO QUE FALTABA: Actualiza la lista cuando llegan datos de Firebase
    public void setEventos(List<Evento> nuevosEventos) {
        this.listaEventos = nuevosEventos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evento, parent, false);
        return new EventoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventoViewHolder holder, int position) {
        Evento evento = listaEventos.get(position);

        holder.txtTitulo.setText(evento.getTitulo());
        holder.txtFecha.setText(evento.getFecha());

        // --- LÓGICA DE IMAGEN CON GLIDE ---
        if (evento.getImagenUrl() != null && !evento.getImagenUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(context)
                    .load(evento.getImagenUrl())
                    .placeholder(R.drawable.card_festival) // Muestra esto mientras carga
                    .error(R.drawable.card_festival)       // Muestra esto si el link está roto
                    .centerCrop()
                    .into(holder.imgEvento);
        } else {
            // Si en Firebase no le has puesto URL, ponemos la imagen por defecto
            holder.imgEvento.setImageResource(R.drawable.card_festival);
        }

        // Clic para ir al detalle
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, DetailActivity.class);
            intent.putExtra("extra_evento", evento);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return listaEventos == null ? 0 : listaEventos.size();
    }

    public static class EventoViewHolder extends RecyclerView.ViewHolder {
        final TextView txtTitulo;
        final TextView txtFecha;
        final ImageView imgEvento;

        public EventoViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitulo = itemView.findViewById(R.id.txtTituloEvento);
            txtFecha = itemView.findViewById(R.id.txtFechaEvento);
            imgEvento = itemView.findViewById(R.id.imgEvento);
        }
    }
}