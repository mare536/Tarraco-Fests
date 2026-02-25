package com.example.tarraco_fest.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tarraco_fest.Data.FirestoreSchema;
import com.example.tarraco_fest.Modelo.AdminUser;
import com.example.tarraco_fest.R;

import java.util.ArrayList;
import java.util.List;

public class AdminUsersAdapter extends RecyclerView.Adapter<AdminUsersAdapter.VH> {

    public interface Listener {
        void onToggleBloqueo(AdminUser user);
        void onToggleRol(AdminUser user);
    }

    private final List<AdminUser> data = new ArrayList<>();
    private final Listener listener;

    public AdminUsersAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<AdminUser> users) {
        data.clear();
        if (users != null) data.addAll(users);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AdminUser u = data.get(pos);

        h.tvNombre.setText(u.nombre.isEmpty() ? "(Sin nombre)" : u.nombre);
        h.tvEmail.setText(u.email.isEmpty() ? "(Sin email)" : u.email);
        h.tvRol.setText(h.itemView.getContext().getString(R.string.admin_user_role_fmt, u.rol));
        h.tvEstado.setText(h.itemView.getContext().getString(
                u.bloqueado ? R.string.admin_user_blocked : R.string.admin_user_active));

        h.btnBloqueo.setText(h.itemView.getContext().getString(
                u.bloqueado ? R.string.admin_user_unblock : R.string.admin_user_block));

        boolean isAdmin = FirestoreSchema.UserRoles.ADMIN.equalsIgnoreCase(u.rol);
        h.tvRol.setBackgroundResource(isAdmin ? R.drawable.bg_admin_role_admin : R.drawable.bg_admin_role_user);
        h.tvRol.setTextColor(ContextCompat.getColor(
                h.itemView.getContext(),
                isAdmin ? R.color.role_admin_text : R.color.role_user_text
        ));

        h.tvEstado.setBackgroundResource(u.bloqueado
                ? R.drawable.bg_admin_status_inactive
                : R.drawable.bg_admin_status_active);

        h.btnRol.setText(h.itemView.getContext().getString(
                isAdmin ? R.string.admin_user_make_user : R.string.admin_user_make_admin));

        h.btnBloqueo.setOnClickListener(v -> listener.onToggleBloqueo(u));
        h.btnRol.setOnClickListener(v -> listener.onToggleRol(u));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNombre, tvEmail, tvRol, tvEstado;
        Button btnBloqueo, btnRol;

        VH(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvAdminUserNombre);
            tvEmail = itemView.findViewById(R.id.tvAdminUserEmail);
            tvRol = itemView.findViewById(R.id.tvAdminUserRol);
            tvEstado = itemView.findViewById(R.id.tvAdminUserEstado);
            btnBloqueo = itemView.findViewById(R.id.btnAdminUserBloqueo);
            btnRol = itemView.findViewById(R.id.btnAdminUserRol);
        }
    }
}
