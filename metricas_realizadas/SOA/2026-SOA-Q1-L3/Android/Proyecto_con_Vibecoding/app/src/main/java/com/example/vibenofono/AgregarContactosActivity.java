package com.example.vibenofono;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AgregarContactosActivity extends AppCompatActivity {
    private ContactosManager contactosManager;
    private ContactosAdapter adapter;
    private EditText editContacto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agregar_contactos);

        contactosManager = new ContactosManager(this);
        editContacto = findViewById(R.id.edit_contacto);
        Button buttonAgregar = findViewById(R.id.button_agregar_contacto);
        RecyclerView recyclerView = findViewById(R.id.recycler_contactos);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ContactosAdapter(contactosManager.obtenerContactos(), contacto -> {
            boolean removed = contactosManager.eliminarContacto(contacto);
            if (removed) {
                actualizarLista();
                Toast.makeText(this, R.string.contacto_eliminado, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.error_eliminar_contacto, Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setAdapter(adapter);

        buttonAgregar.setOnClickListener(v -> {
            String contacto = editContacto.getText().toString().trim();
            if (TextUtils.isEmpty(contacto)) {
                Toast.makeText(this, R.string.error_contacto_vacio, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!ContactosManager.validarContacto(contacto)) {
                Toast.makeText(this, R.string.error_contacto_valido, Toast.LENGTH_SHORT).show();
                return;
            }
            boolean added = contactosManager.agregarContacto(contacto);
            if (added) {
                editContacto.setText("");
                actualizarLista();
                Toast.makeText(this, R.string.contacto_agregado, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.error_agregar_contacto, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void actualizarLista() {
        List<String> contactos = contactosManager.obtenerContactos();
        adapter.updateContactos(contactos);
    }
}
