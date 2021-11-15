package com.sde.smartmailbox;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.sde.smartmailbox.databinding.ActivityMainBinding;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    MqttHelper mqttHelper;
    private ActivityMainBinding binding;
    public static final String CHANNEL_ID = "101";
    public static final String CHANNEL_NAME = "notifications";

    // Bluettoth configuration
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner scanner;
    UUID BLP_SERVICE_UUID = UUID.fromString("2af412d8-3e7e-11ec-9bbc-0242ac130002");
    UUID[] serviceUUIDs = new UUID[]{BLP_SERVICE_UUID};
    List<ScanFilter> filters = null;
    ScanSettings scanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0L)
            .build();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        final Button openButton = findViewById(R.id.openButton);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mqttHelper.publishMessage("smartmailbox/relay", "on");
            }
        });

        final Button changePinButton = findViewById(R.id.changePinButton);
        changePinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("Boton", "Cambio pin");
                EditText editText = findViewById(R.id.pinPasswordEditText);
                String pinText = editText.getText().toString();
                if (!"".equals(pinText)){
                    Log.d("PIN", pinText);
                    mqttHelper.publishMessage("smartmailbox/pin", pinText);
                } else{
                    Context context = getApplicationContext();
                    CharSequence text = "Tienes que seleccionar un PIN";
                    int duration = Toast.LENGTH_SHORT;

                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                }
            }
        });

        // Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        scanner = bluetoothAdapter.getBluetoothLeScanner();

        if( serviceUUIDs != null ) {
            filters = new ArrayList<>();
            for (UUID serviceUUID : serviceUUIDs) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setServiceUuid(new ParcelUuid(serviceUUID))
                        .build();
                filters.add(filter);
            }
        }

        if (scanner != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Log.d("BLE", "scan started");
                scanner.startScan(filters, scanSettings, scanCallback);
            } else {
                // Unless required permissions were acquired, scan does not start.

            }

        }  else {
            Log.e("BLE", "could not get scanner object");
        }


        // Create notification channel
        createNotificationChannel();

        // Start mqtt service
        startMqtt();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            // ...do whatever you want with this found device
            Log.d("BLE", "He encontrado el puto bleeeeeeeeeeee");

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            // Ignore for now
        }

        @Override
        public void onScanFailed(int errorCode) {
            // Ignore for now
            Log.d("ERROOOOOOOOR", "dwedwed");

        }
    };

    private void startMqtt(){
        mqttHelper = new MqttHelper(getApplicationContext());
        mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {

            }

            @Override
            public void connectionLost(Throwable throwable) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                if (topic.equals("smartmailbox/openrequest")){
                    Log.w("MQTT", "Abre la puta puerta.");
                    generateNotification("Abrir buzon", "Solicitud de apertura de buzon");

                }else if (topic.equals("smartmailbox/letter")){
                    Log.w("MQTT", "Carta recibida a las "+mqttMessage.toString());
                    generateNotification("Correo nuevo", "Tienes nuevo correo que recoger");

                }else if (topic.equals("smartmailbox/opened")){
                    Log.w("MQTT", "Buzon abierto a las "+mqttMessage.toString());
                    generateNotification("Buzon abierto", "Buzon abierto el "+mqttMessage.toString());

                } else{
                    Log.w("MQTT", "Buzon abierto a las "+mqttMessage.toString());
                    generateNotification("PIN cambiado", "Pin cambiado el "+mqttMessage.toString());
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void generateNotification(String title, String text) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(101, builder.build());
    }

}