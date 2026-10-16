package com.example.vibenofono;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ContactosAdapter extends RecyclerView.Adapter<ContactosAdapter.ContactoViewHolder> {
    public interface OnContactoDeleted {
        void onContactoDeleted(String contacto);
    }

    private final List<String> contactos;
    private final OnContactoDeleted callback;

    public ContactosAdapter(List<String> contactos, OnContactoDeleted callback) {
        this.contactos = new ArrayList<>(contactos);
        this.callback = callback;
    }

    @NonNull
    @Override
    public ContactoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contacto, parent, false);
        return new ContactoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactoViewHolder holder, int position) {
        String contacto = contactos.get(position);
        holder.contactoText.setText(contacto);
        holder.deleteButton.setOnClickListener(v -> callback.onContactoDeleted(contacto));
    }

    @Override
    public int getItemCount() {
        return contactos.size();
    }

    public void updateContactos(List<String> nuevosContactos) {
        contactos.clear();
        contactos.addAll(nuevosContactos);
        notifyDataSetChanged();
    }

    static class ContactoViewHolder extends RecyclerView.ViewHolder {
        final TextView contactoText;
        final ImageButton deleteButton;

        ContactoViewHolder(@NonNull View itemView) {
            super(itemView);
            contactoText = itemView.findViewById(R.id.text_contacto_item);
            deleteButton = itemView.findViewById(R.id.button_delete_contacto);
        }
    }
}
