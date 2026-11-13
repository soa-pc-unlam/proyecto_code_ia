package com.example.myapplication

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class ConfigModalFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.config_modal, null)

        val host = view.findViewById<android.widget.EditText>(R.id.host)
        val port = view.findViewById<android.widget.EditText>(R.id.port)
        val username = view.findViewById<android.widget.EditText>(R.id.username)
        val password = view.findViewById<android.widget.EditText>(R.id.password)

        return AlertDialog.Builder(requireContext())
            .setTitle("MQTT Settings")
            .setView(view)
            .setPositiveButton("Save") { dialog, _ ->
                val host = host.text.toString()
                val port = port.text.toString()
                val username = username.text.toString().trim()
                val password = password.text.toString()

                val serverUri = "tcp://$host:$port"
                val mqtt = ((requireActivity()).application as CustomApplication).mqttManager

                mqtt.configure(serverUri, username, password)

                mqtt.connect()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}