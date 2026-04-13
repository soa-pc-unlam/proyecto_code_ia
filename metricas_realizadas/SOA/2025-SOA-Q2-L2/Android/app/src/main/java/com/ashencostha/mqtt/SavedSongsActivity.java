package com.ashencostha.mqtt;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class SavedSongsActivity extends AppCompatActivity {

    private ListView songsListView;
    private Button loadSongButton, deleteSongButton, backSongButton;;

    private ArrayList<Song> songList;
    private ArrayAdapter<Song> songAdapter;
    private Song selectedSong = null;

    public static final String SONGS_PREFS_KEY = "SavedSongs";
    public static final String SONGS_LIST_KEY = "SongListJSON";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_songs);

        songsListView = findViewById(R.id.songsListView);
        loadSongButton = findViewById(R.id.loadSongButton);
        deleteSongButton = findViewById(R.id.deleteSongButton);
        backSongButton = findViewById(R.id.backSongButton);

        loadSongsFromPrefs();

        songAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, songList);
        songsListView.setAdapter(songAdapter);
        songsListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        // --- Listeners ---

        songsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedSong = songList.get(position);
            }
        });

        backSongButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               finish();
            }
        });

        loadSongButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedSong != null) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("loadedSong", selectedSong);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                } else {
                    Toast.makeText(SavedSongsActivity.this, "Por favor, seleccione una canción para cargar", Toast.LENGTH_SHORT).show();
                }
            }
        });

        deleteSongButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedSong != null) {
                    showDeleteConfirmationDialog();
                } else {
                    Toast.makeText(SavedSongsActivity.this, "Por favor, seleccione una canción para borrar", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadSongsFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(SONGS_PREFS_KEY, MODE_PRIVATE);
        String json = prefs.getString(SONGS_LIST_KEY, null);
        Gson gson = new Gson();
        Type type = new TypeToken<ArrayList<Song>>() {}.getType();
        songList = gson.fromJson(json, type);

        if (songList == null) {
            songList = new ArrayList<>();
        }
    }

    private void saveSongsToPrefs() {
        SharedPreferences prefs = getSharedPreferences(SONGS_PREFS_KEY, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(songList);
        editor.putString(SONGS_LIST_KEY, json);
        editor.apply();
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Borrar Canción")
                .setMessage("¿Está seguro de que desea borrar '" + selectedSong.getName() + "'? Esta acción no se puede deshacer.")
                .setPositiveButton("Borrar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        songList.remove(selectedSong);
                        songAdapter.notifyDataSetChanged();
                        saveSongsToPrefs();
                        selectedSong = null;
                        songsListView.clearChoices();
                        Toast.makeText(SavedSongsActivity.this, "Canción borrada", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
