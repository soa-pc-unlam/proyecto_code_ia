package com.ashencostha.mqtt;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.text.InputType;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
        implements MatrixAdapter.OnCellEditListener, SensorEventListener {

    // --- Matriz y sus dimensiones ---
    private static final int ROWS = 16;
    private static final int COLS = 4;
    private int[][] matrixVals = new int[ROWS][COLS];
    // ---------------------------------

    private static final int LOAD_SONG_REQUEST_CODE = 1;

    // --- Componentes de la UI ---
    private GridView matrixGridView;
    private MatrixAdapter matrixAdapter;
    private TextView txtJson;
    private TextView txtEspStatus;
    private TextView txtPhoneState;
    // ------------------------------
    // --- Botones Menú Principal (Idle) ---
    private Button cmdEditar;
    private Button cmdStop;
    private Button cmdReproducir;
    private Button cmdSaveSong;
    private Button cmdOpenLibrary;
    private Button cmdSync;
    private LinearLayout menuPrincipalLayout;
    // ----------------------------

    // --- Botones Menú Edición ---
    private Button cmdPlayRow;
    private Button cmdSave;
    private Button cmdBackToMenu;
    private LinearLayout menuEdicionLayout;
    // ----------------------------

    // --- Botones Menú Sincronizacion ---
    private Button cmdSyncBack;
    private Button cmdSendMatrix;
    private Button cmdReceiveMatrix;
    private LinearLayout menuSyncLayout;
    // ----------------------------

    // --- Gestión de Estado y Selección ---
    private enum AppState { IDLE, EDITING, SYNC }
    private AppState currentState = AppState.IDLE;
    private int selectedRow = -1;
    private int selectedCol = -1;
    // ------------------------------------

    // --- MQTT y BroadcastReceiver ---
    private MqttHandler mqttHandler;
    public IntentFilter filterReceive;
    public IntentFilter filterConnectionLost;
    private final ReceptorOperacion receiver = new ReceptorOperacion();
    private final ConnectionLost connectionLost = new ConnectionLost();
    // --------------------------------

    // --- Sensor (Giroscopio + Acelerómetro) ---
    private SensorManager sensorManager;
    private Sensor gyroscope;
    private Sensor accelerometer;
    private long lastSensorUpdateTime = 0;
    // ----------------------------

    // --- Shake Detection ---
    private float lastX, lastY, lastZ;
    private static final int SHAKE_THRESHOLD = 800;
    private boolean firstShakeSample = true;
    // ----------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // --- Vinculación de Vistas ---
        txtJson       = findViewById(R.id.txtJson);
        txtEspStatus  = findViewById(R.id.txtEspStatus);
        txtPhoneState = findViewById(R.id.txtPhoneState);
        matrixGridView = findViewById(R.id.matrixGridView);
        // ------------------------------

        // Vistas del menú principal
        cmdSaveSong         = findViewById(R.id.cmdSaveSong);
        cmdOpenLibrary      = findViewById(R.id.cmdOpenLibrary);
        cmdEditar           = findViewById(R.id.cmdEditar);
        cmdStop             = findViewById(R.id.cmdStop);
        cmdReproducir       = findViewById(R.id.cmdReproducir);
        cmdSync             = findViewById(R.id.cmdSync);
        menuPrincipalLayout = findViewById(R.id.menuPrincipalLayout);
        // ------------------------------

        // Vistas del menú de edición
        cmdPlayRow        = findViewById(R.id.cmdPlayRow);
        cmdSave           = findViewById(R.id.cmdSave);
        cmdBackToMenu     = findViewById(R.id.cmdBackToMenu);
        menuEdicionLayout = findViewById(R.id.menuEdicionLayout);
        // ------------------------------

        // Vistas del menú de sincronización
        cmdSyncBack      = findViewById(R.id.cmdSyncBack);
        cmdSendMatrix    = findViewById(R.id.cmdSendMatrix);
        cmdReceiveMatrix = findViewById(R.id.cmdReceiveMatrix);
        menuSyncLayout   = findViewById(R.id.menuSyncLayout);
        // ------------------------------

        // --- Configuración de Listeners ---
        View.OnClickListener botonesListeners = this::onButtonClick;
        cmdSaveSong.setOnClickListener(botonesListeners);
        cmdOpenLibrary.setOnClickListener(botonesListeners);
        cmdEditar.setOnClickListener(botonesListeners);
        cmdStop.setOnClickListener(botonesListeners);
        cmdReproducir.setOnClickListener(botonesListeners);
        cmdPlayRow.setOnClickListener(botonesListeners);
        cmdSave.setOnClickListener(botonesListeners);
        cmdBackToMenu.setOnClickListener(botonesListeners);
        cmdSync.setOnClickListener(botonesListeners);
        cmdSyncBack.setOnClickListener(botonesListeners);
        cmdSendMatrix.setOnClickListener(botonesListeners);
        cmdReceiveMatrix.setOnClickListener(botonesListeners);
        // ----------------------------------

        // --- Inicialización del Sensor Manager ---
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer == null) {
                Toast.makeText(this,
                        "Acelerómetro no encontrado. No se podrá agitar para reproducir.",
                        Toast.LENGTH_LONG).show();
            }
        }

        // --- Lógica de la Matriz ---
        initializeMatrix();
        matrixAdapter = new MatrixAdapter(this, matrixVals, this);
        matrixGridView.setAdapter(matrixAdapter);
        // ---------------------------

        // --- Estado Inicial de la UI ---
        updateUIVisibility();
        setPhoneState(AppState.IDLE);
        // ---------------------------

        // --- Conexión MQTT y Configuración de Receivers ---
        mqttHandler = new MqttHandler(getApplicationContext());
        configurarBroadcastReceiver();
        connect();
        // -------------------------------------------------
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (isTaskRoot()) {
            Intent intent = new Intent(MainActivity.this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        } else {
            super.onBackPressed();
        }
    }

    private void updateUIVisibility() {
        menuPrincipalLayout.setVisibility(View.GONE);
        menuEdicionLayout.setVisibility(View.GONE);
        menuSyncLayout.setVisibility(View.GONE);

        if (currentState == AppState.IDLE) {
            menuPrincipalLayout.setVisibility(View.VISIBLE);
            matrixAdapter.setSelection(-1, -1);
            selectedRow = -1;
            selectedCol = -1;
        } else if (currentState == AppState.EDITING) {
            menuEdicionLayout.setVisibility(View.VISIBLE);
        } else if (currentState == AppState.SYNC) {
            menuSyncLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCellEdited(int row, int col, int value) {
        if (currentState == AppState.IDLE) {
            selectedRow = row;
            selectedCol = col;
            matrixAdapter.setSelection(row, col);
            txtJson.setText("Celda (" + row + ", " + col + ") seleccionada. Presiona EDITAR.");
        }
    }

    private void initializeMatrix() {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                matrixVals[i][j] = 0;
            }
        }
    }

    // ============================
    //   CONEXIÓN MQTT
    // ============================
    private void connect() {
        ConfigMQTT.useServerSequencer();

        mqttHandler.connect(
                ConfigMQTT.mqttServer,
                ConfigMQTT.CLIENT_ID,
                ConfigMQTT.userName,
                ConfigMQTT.userPass
        );

        try {
            Thread.sleep(1000);
            // Estado del ESP
            subscribeToTopic(ConfigMQTT.topicStatus);
            // Matriz que manda el ESP32 (/simulator/cellval)
            subscribeToTopic(ConfigMQTT.topicReceiveMatrix);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void configurarBroadcastReceiver() {
        filterReceive        = new IntentFilter(MqttHandler.ACTION_DATA_RECEIVE);
        filterConnectionLost = new IntentFilter(MqttHandler.ACTION_CONNECTION_LOST);

        filterReceive.addCategory(Intent.CATEGORY_DEFAULT);
        filterConnectionLost.addCategory(Intent.CATEGORY_DEFAULT);

        registerReceiver(receiver,        filterReceive);
        registerReceiver(connectionLost,  filterConnectionLost);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttHandler != null) {
            mqttHandler.disconnect();
        }
        unregisterReceiver(receiver);
        unregisterReceiver(connectionLost);
    }

    private void publishMessage(String topic, String message) {
        txtJson.setText("Publicando: " + message + " en " + topic);
        if (mqttHandler != null) {
            mqttHandler.publish(topic, message);
        }
    }

    private void subscribeToTopic(String topic) {
        Toast.makeText(this, "Suscribiendo a " + topic, Toast.LENGTH_SHORT).show();
        if (mqttHandler != null) {
            mqttHandler.subscribe(topic);
        }
    }

    // ============================
    //  LISTENER DE BOTONES
    // ============================
    private void onButtonClick(View view) {
        int id = view.getId();

        // --- Menú principal ---
        if (id == R.id.cmdEditar) {
            if (selectedRow != -1 && selectedCol != -1) {
                setPhoneState(AppState.EDITING);
                matrixAdapter.setEditing(true);
            } else {
                Toast.makeText(this,
                        "Por favor, seleccione una celda primero",
                        Toast.LENGTH_SHORT).show();
            }

        } else if (id == R.id.cmdReproducir) {
            publishMessage(ConfigMQTT.topicState, "PLAY_ALL");

        } else if (id == R.id.cmdStop) {
            publishMessage(ConfigMQTT.topicState, "IDLE");

        } else if (id == R.id.cmdSaveSong) {
            showSaveSongDialog();

        } else if (id == R.id.cmdOpenLibrary) {
            Intent intent = new Intent(MainActivity.this, SavedSongsActivity.class);
            startActivityForResult(intent, LOAD_SONG_REQUEST_CODE);

        } else if (id == R.id.cmdSync) {
            setPhoneState(AppState.SYNC);
        }

        // --- Menú de edición ---
        else if (id == R.id.cmdPlayRow) {
            if (selectedRow != -1) {
                publishMessage(ConfigMQTT.topicPlayRow, String.valueOf(selectedRow));
            }

        } else if (id == R.id.cmdSave) {
            if (selectedRow != -1 && selectedCol != -1) {
                int valueToSave = matrixVals[selectedRow][selectedCol];
                try {
                    String msg = selectedRow + " " + selectedCol + " " + valueToSave;
                    publishMessage(ConfigMQTT.topicEdit, msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this,
                            "Error al enviar el mensaje",
                            Toast.LENGTH_SHORT).show();
                }
            }

        } else if (id == R.id.cmdBackToMenu) {
            currentState = AppState.IDLE;
            matrixAdapter.setEditing(false);
            updateUIVisibility();
            setPhoneState(AppState.IDLE);
            publishMessage(ConfigMQTT.topicState, "IDLE");
        }

        // --- Menú de Sincronización ---
        else if (id == R.id.cmdSyncBack) {
            setPhoneState(AppState.IDLE);

        } else if (id == R.id.cmdSendMatrix) {
            // Celu → ESP32
            sendMatrixAsString();

        } else if (id == R.id.cmdReceiveMatrix) {
            // Ya estamos suscriptos a topicReceiveMatrix en connect()
            // Pedimos la matriz actual al ESP32
            publishMessage(ConfigMQTT.topicSendMatrix, "SEND_MATRIX");
            Toast.makeText(this,
                    "Esperando matriz del ESP...",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ============================
    //   BROADCASTS MQTT
    // ============================

    public class ConnectionLost extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(getApplicationContext(),
                    "Conexión Perdida. Reconectando...",
                    Toast.LENGTH_SHORT).show();
            connect();
        }
    }

    public class ReceptorOperacion extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getExtras() != null) {
                String topic   = intent.getStringExtra("topic");
                String message = intent.getStringExtra("msgJson");

                if (topic == null || message == null) {
                    return;
                }

                // Por las dudas, limpiamos espacios
                topic = topic.trim();

                // Debug en pantalla
                txtJson.setText(String.format("Tópico: %s, Mensaje: %s", topic, message));

                try {
                    // Estado del ESP (/simulator/status)
                    if (topic.equals(ConfigMQTT.topicStatus)) {
                        txtEspStatus.setText(String.format("Estado ESP: %s", message));
                    }
                    // Matriz recibida desde el ESP (/simulator/cellval)
                    else if (topic.equals(ConfigMQTT.topicReceiveMatrix)) {
                        if (currentState == AppState.SYNC) {
                            updateMatrixFromString(message);
                            Toast.makeText(MainActivity.this,
                                    "Matriz recibida y actualizada!",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Llegó matriz, pero no estás en pantalla de Sync",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ============================
    //   ESTADO DEL CELU
    // ============================

    private void setPhoneState(AppState newState) {
        currentState = newState;
        if (newState == AppState.IDLE) {
            txtPhoneState.setText("Estado App: Idle");
            if (gyroscope != null) {
                sensorManager.unregisterListener(this, gyroscope);
            }
        } else if (newState == AppState.EDITING) {
            txtPhoneState.setText("Estado App: Editando");
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
            } else {
                Toast.makeText(this, "Giroscopio no encontrado", Toast.LENGTH_SHORT).show();
            }
        } else if (newState == AppState.SYNC) {
            txtPhoneState.setText("Estado App: Sync");
            if (gyroscope != null) {
                sensorManager.unregisterListener(this, gyroscope);
            }
        }
        updateUIVisibility();
    }

    // ============================
    //   ACELERÓMETRO / GIRO
    // ============================

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // requerido por la interfaz, no usamos nada acá
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // --- Giroscopio para editar valor ---
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE &&
                currentState == AppState.EDITING) {

            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastSensorUpdateTime) < 200) {
                return;
            }
            lastSensorUpdateTime = currentTime;

            float rotationY = event.values[1];
            float threshold = 0.8f;

            if (rotationY > threshold) { // Giro a la derecha -> Aumentar valor
                incrementCellValue();
            } else if (rotationY < -threshold) { // Giro a la izquierda -> Disminuir valor
                decrementCellValue();
            }
        }

        // --- Acelerómetro para shake / reproducir ---
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastSensorUpdateTime) > 100) {
                long timeDiff = (currentTime - lastSensorUpdateTime);
                lastSensorUpdateTime = currentTime;

                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                if (firstShakeSample) {
                    lastX = x;
                    lastY = y;
                    lastZ = z;
                    firstShakeSample = false;
                } else {
                    float speed = Math.abs(x + y + z - lastX - lastY - lastZ)
                            / timeDiff * 10000;

                    if (speed > SHAKE_THRESHOLD) {
                        if (currentState == AppState.IDLE) {
                            Toast.makeText(this,
                                    "¡Shake detectado! Reproduciendo...",
                                    Toast.LENGTH_SHORT).show();
                            publishMessage(ConfigMQTT.topicState, "PLAY_ALL");
                            lastSensorUpdateTime = currentTime + 1000;
                        }
                    }
                    lastX = x;
                    lastY = y;
                    lastZ = z;
                }
            }
        }
    }

    // --- Helpers para cambiar valor de celda con giroscopio ---
    private void incrementCellValue() {
        if (selectedRow == -1 || selectedCol == -1) return;

        int currentValue = matrixVals[selectedRow][selectedCol];
        int newValue = currentValue + 1;
        int maxValue = (selectedCol == 0) ? 15 : 127;

        if (newValue > maxValue) {
            newValue = maxValue;
        }
        updateCellValue(newValue);
    }

    private void decrementCellValue() {
        if (selectedRow == -1 || selectedCol == -1) return;

        int currentValue = matrixVals[selectedRow][selectedCol];
        int newValue = currentValue - 1;

        if (newValue < 0) {
            newValue = 0;
        }

        updateCellValue(newValue);
    }

    private void updateCellValue(int newValue) {
        matrixVals[selectedRow][selectedCol] = newValue;
        matrixAdapter.notifyDataSetChanged();
        txtJson.setText("Valor cambiado por giroscopio: " + newValue);
    }

    // ============================
    //   GUARDAR CANCIONES
    // ============================

    private void showSaveSongDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Guardar Canción");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Nombre de la canción");
        builder.setView(input);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String songName = input.getText().toString();
            if (!songName.isEmpty()) {
                saveSong(songName);
            } else {
                Toast.makeText(MainActivity.this,
                        "El nombre no puede estar vacío",
                        Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveSong(String name) {
        SharedPreferences prefs = getSharedPreferences(
                SavedSongsActivity.SONGS_PREFS_KEY, MODE_PRIVATE);
        String json = prefs.getString(SavedSongsActivity.SONGS_LIST_KEY, null);
        Gson gson = new Gson();
        Type type = new com.google.gson.reflect.TypeToken<ArrayList<Song>>() {}.getType();
        ArrayList<Song> songList = gson.fromJson(json, type);
        if (songList == null) {
            songList = new ArrayList<>();
        }

        int[][] matrixToSave = new int[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                matrixToSave[i][j] = matrixVals[i][j];
            }
        }

        songList.add(new Song(name, matrixToSave));

        SharedPreferences.Editor editor = prefs.edit();
        String updatedJson = gson.toJson(songList);
        editor.putString(SavedSongsActivity.SONGS_LIST_KEY, updatedJson);
        editor.apply();

        Toast.makeText(this, "Canción '" + name + "' guardada.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LOAD_SONG_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.hasExtra("loadedSong")) {
                Song loadedSong = (Song) data.getSerializableExtra("loadedSong");
                if (loadedSong != null) {
                    this.matrixVals = loadedSong.getMatrix();
                    matrixAdapter = new MatrixAdapter(this, matrixVals, this);
                    matrixGridView.setAdapter(matrixAdapter);

                    Toast.makeText(this,
                            "Canción '" + loadedSong.getName() + "' cargada.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ============================
    //   SYNC MATRIZ <-> ESP32
    // ============================

    private void sendMatrixAsString() {
        StringBuilder matrixString = new StringBuilder();
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                matrixString.append(matrixVals[i][j]);
                if (i < ROWS - 1 || j < COLS - 1) {
                    matrixString.append(" ");
                }
            }
        }
        publishMessage(ConfigMQTT.topicSendMatrix, matrixString.toString());
        Toast.makeText(this, "Matriz enviada!", Toast.LENGTH_SHORT).show();
    }

    private void updateMatrixFromString(String matrixString) {
        String[] values = matrixString.trim().split("\\s+");
        int valueIndex = 0;

        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                if (valueIndex < values.length) {
                    try {
                        matrixVals[i][j] = Integer.parseInt(values[valueIndex]);
                        valueIndex++;
                    } catch (NumberFormatException e) {
                        matrixVals[i][j] = 0;
                    }
                } else {
                    matrixVals[i][j] = 0;
                }
            }
        }

        if (matrixAdapter != null) {
            matrixAdapter.setMatrix(matrixVals);
            matrixGridView.invalidateViews();
        }
    }
}
