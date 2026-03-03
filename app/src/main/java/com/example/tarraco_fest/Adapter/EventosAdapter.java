package com.example.tarraco_fest.Adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tarraco_fest.Activity.DetailActivity;
import com.example.tarraco_fest.Modelo.Evento;
import com.example.tarraco_fest.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Adapter de eventos para el flujo de usuario final.
 * Encapsula el binding de titulo, fecha, lugar e imagen.
 */
public class EventosAdapter extends RecyclerView.Adapter<EventosAdapter.EventoViewHolder> {

    private List<Evento> listaEventos;
    private final Context context;

    public EventosAdapter(List<Evento> listaEventos, Context context) {
        this.listaEventos = listaEventos == null ? Collections.emptyList() : new ArrayList<>(listaEventos);
        this.context = context;
        setHasStableIds(true);
    }

    // Actualiza eventos con el valor recibido.
    public void setEventos(List<Evento> nuevosEventos) {
        List<Evento> nuevaLista = nuevosEventos == null ? Collections.emptyList() : new ArrayList<>(nuevosEventos);
        List<Evento> listaAnterior = this.listaEventos == null ? Collections.emptyList() : this.listaEventos;

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return listaAnterior.size();
            }

            @Override
            public int getNewListSize() {
                return nuevaLista.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Evento oldItem = listaAnterior.get(oldItemPosition);
                Evento newItem = nuevaLista.get(newItemPosition);
                return construirClaveEstable(oldItem).equals(construirClaveEstable(newItem));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Evento oldItem = listaAnterior.get(oldItemPosition);
                Evento newItem = nuevaLista.get(newItemPosition);
                return mismoContenidoVisual(oldItem, newItem);
            }
        });

        this.listaEventos = nuevaLista;
        diffResult.dispatchUpdatesTo(this);
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

        // Evita artefactos por reciclado: cancela cualquier carga pendiente previa.
        Glide.with(context).clear(holder.imgEvento);
        holder.imgEvento.setImageResource(fallbackImage);

        if (evento.getImagenUrl() != null && !evento.getImagenUrl().isEmpty()) {
            Glide.with(context)
                    .load(evento.getImagenUrl())
                    .placeholder(fallbackImage)
                    .error(fallbackImage)
                    .centerCrop()
                    .into(holder.imgEvento);
        } else if (!TextUtils.isEmpty(evento.getImagenBase64())) {
            try {
                byte[] bytes = Base64.decode(evento.getImagenBase64(), Base64.DEFAULT);
                if (bytes != null && bytes.length > 0) {
                    holder.imgEvento.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                } else {
                    holder.imgEvento.setImageResource(fallbackImage);
                }
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

    // IDs estables para reducir inconsistencias visuales al reciclar celdas.
    @Override
    public long getItemId(int position) {
        if (listaEventos == null || position < 0 || position >= listaEventos.size()) return RecyclerView.NO_ID;
        return construirClaveEstable(listaEventos.get(position)).hashCode();
    }

    // Limpia request e imagen al reciclar para evitar arrastre visual entre tarjetas.
    @Override
    public void onViewRecycled(@NonNull EventoViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(context).clear(holder.imgEvento);
        holder.imgEvento.setImageDrawable(null);
    }

    // Genera una clave consistente para identificar cada evento en updates diferenciales.
    private String construirClaveEstable(Evento e) {
        if (e == null) return "";
        String id = e.getId();
        if (id != null && !id.trim().isEmpty()) return id.trim();
        String titulo = e.getTitulo() == null ? "" : e.getTitulo().trim();
        return titulo + "|" + e.getInicioMillis() + "|" + (e.getFecha() == null ? "" : e.getFecha());
    }

    // Compara solo los campos visibles para evitar rebinds innecesarios.
    private boolean mismoContenidoVisual(Evento oldItem, Evento newItem) {
        if (oldItem == null && newItem == null) return true;
        if (oldItem == null || newItem == null) return false;

        return Objects.equals(oldItem.getTitulo(), newItem.getTitulo())
                && Objects.equals(oldItem.getFecha(), newItem.getFecha())
                && Objects.equals(oldItem.getDistanciaKm(), newItem.getDistanciaKm())
                && oldItem.getImagenResId() == newItem.getImagenResId()
                && Objects.equals(oldItem.getImagenUrl(), newItem.getImagenUrl())
                && Objects.equals(oldItem.getImagenBase64(), newItem.getImagenBase64());
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
