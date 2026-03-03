package com.example.tarraco_fest.Adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Base64;
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

/**
 * Adapter de eventos para el flujo de usuario final.
 * Encapsula el binding de titulo, fecha, lugar e imagen.
 */
public class EventosAdapter extends RecyclerView.Adapter<EventosAdapter.EventoViewHolder> {

    private List<Evento> listaEventos;
    private final Context context;

    public EventosAdapter(List<Evento> listaEventos, Context context) {
        this.listaEventos = listaEventos;
        this.context = context;
    }

    // Actualiza eventos con el valor recibido.
    public void setEventos(List<Evento> nuevosEventos) {
        this.listaEventos = nuevosEventos;
        notifyDataSetChanged();
    }

    // Gestiona on create view holder en este bloque.
    @NonNull
    @Override
    public EventoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evento, parent, false);
        return new EventoViewHolder(view);
    }

    // Gestiona on bind view holder en este bloque.
    @Override
    public void onBindViewHolder(@NonNull EventoViewHolder holder, int position) {
        Evento evento = listaEventos.get(position);
        int fallbackImage = evento.getImagenResId() != 0 ? evento.getImagenResId() : R.drawable.card_festival;

        holder.txtTitulo.setText(evento.getTitulo());
        holder.txtFecha.setText(evento.getFecha());
        Double distanciaKm = evento.getDistanciaKm();
        if (distanciaKm != null && distanciaKm >= 0d) {
            holder.txtDistancia.setVisibility(View.VISIBLE);
            holder.txtDistancia.setText(context.getString(R.string.event_distance_km, distanciaKm));
        } else {
            holder.txtDistancia.setVisibility(View.GONE);
            holder.txtDistancia.setText("");
        }

        if (evento.getImagenUrl() != null && !evento.getImagenUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(context)
                    .load(evento.getImagenUrl())
                    .placeholder(fallbackImage)
                    .error(fallbackImage)
                    .centerCrop()
                    .into(holder.imgEvento);
        } else if (!TextUtils.isEmpty(evento.getImagenBase64())) {
            try {
                byte[] bytes = Base64.decode(evento.getImagenBase64(), Base64.DEFAULT);
                com.bumptech.glide.Glide.with(context)
                        .load(bytes)
                        .placeholder(fallbackImage)
                        .error(fallbackImage)
                        .centerCrop()
                        .into(holder.imgEvento);
            } catch (IllegalArgumentException ex) {
                holder.imgEvento.setImageResource(fallbackImage);
            }
        } else {
            holder.imgEvento.setImageResource(fallbackImage);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, DetailActivity.class);
            intent.putExtra("extra_evento", evento);
            context.startActivity(intent);
        });
    }

    // Devuelve item count.
    @Override
    public int getItemCount() {
        return listaEventos == null ? 0 : listaEventos.size();
    }

    public static class EventoViewHolder extends RecyclerView.ViewHolder {
        final TextView txtTitulo;
        final TextView txtFecha;
        final TextView txtDistancia;
        final ImageView imgEvento;

        public EventoViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitulo = itemView.findViewById(R.id.txtTituloEvento);
            txtFecha = itemView.findViewById(R.id.txtFechaEvento);
            txtDistancia = itemView.findViewById(R.id.txtDistanciaEvento);
            imgEvento = itemView.findViewById(R.id.imgEvento);
        }
    }
}
