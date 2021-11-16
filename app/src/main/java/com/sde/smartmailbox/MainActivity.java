package com.sde.smartmailbox;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.sde.smartmailbox.databinding.ActivityMainBinding;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    MqttHelper mqttHelper;
    private ActivityMainBinding binding;
    public static final String CHANNEL_ID = "101";
    public static final String CHANNEL_NAME = "notifications";

    // Bluettoth configuration
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner scanner;
    BluetoothGatt bluetoothGatt;
    BluetoothDevice bluetoothDevice;
    BluetoothGattService service;
    BluetoothGattCharacteristic openCharacteristic;
    BluetoothGattCharacteristic pinCharacteristic;

    public static final String MAC_ADDRESS = "C8:C9:A3:D2:4C:BC";
    public static final UUID UUID_SERVICE = UUID.fromString("2af412d8-3e7e-11ec-9bbc-0242ac130002");
    public static final UUID UUID_CHARACTERISTIC_OPEN = UUID.fromString("e7a6ec42-2713-4484-81d5-39e5d3e9060b");
    public static final UUID UUID_CHARACTERISTIC_PIN = UUID.fromString("12fa1c0a-25cc-4281-9c22-8e7951b1ac03");
    public static final UUID UUID_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    UUID[] serviceUUIDs = new UUID[]{UUID_SERVICE};
    List<ScanFilter> filters = null;
    ScanSettings scanSettings = null;
    String[] bleName = new String[]{"SmartMailbox"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        MaterialButtonToggleGroup materialButtonToggleGroup =
                findViewById(R.id.toggleButton);

        LinearLayout bluetoothButtons = (LinearLayout) findViewById(R.id.bluetoothButtons);
        bluetoothButtons.setVisibility(View.INVISIBLE);

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
                    showMessage("Tienes que seleccionar un PIN");
                }
            }
        });

        materialButtonToggleGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if(group.getCheckedButtonId()==R.id.enableWifi)
                {
                    bluetoothButtons.setVisibility(View.INVISIBLE);
                }else if(group.getCheckedButtonId()==R.id.enableBLE) {
                    bluetoothButtons.setVisibility(View.VISIBLE);
                }
            }
        });

        final Button connectButton = findViewById(R.id.connectBLE);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start BLE
                startBLE();
            }
        });

        final Button disconnectButton = findViewById(R.id.disconnectBLE);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Disconnect BLE
                bleDisconnect();
            }
        });

        // Create notification channel
        createNotificationChannel();

        // Start mqtt service
        startMqtt();
    }

    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("BLE", "Detectando cambios en gatt");

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "A descubrir cosas");
                scanner.stopScan(scanCallback);
                //showMessage("Conectado por BLE");

                gatt.discoverServices();
            } else {
                gatt.close();
            }
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            final List<BluetoothGattService> services = gatt.getServices();
            Log.i("BLE", String.format(Locale.ENGLISH,"discovered %d services for '%s'", services.size(), services.get(0).getUuid()));

                service = gatt.getService(UUID_SERVICE);
                if (service != null) {
                    Log.i("BLE", "Service connected");
                    openCharacteristic = service.getCharacteristic(UUID_CHARACTERISTIC_OPEN);
                    if (openCharacteristic != null){
                        Log.i("BLE", "Tengo caracteristica de abrir");
                        //openCharacteristic.setValue("on");
                        //bluetoothGatt.writeCharacteristic(openCharacteristic);
                    }else{
                        Log.i("BLE", "No veo open");

                    }
                    pinCharacteristic = service.getCharacteristic(UUID_CHARACTERISTIC_PIN);
                    if (pinCharacteristic != null){
                        Log.i("BLE", "Tengo caracteristica de pin");
                        pinCharacteristic.setValue("3321");
                        bluetoothGatt.writeCharacteristic(pinCharacteristic);
                    }else {
                        Log.i("BLE", "No veo pin");

                    }
                }

            super.onServicesDiscovered(gatt, status);
        }
    };

    private void bleDisconnect(){
        bluetoothGatt.disconnect();
    }

    private void showMessage(String message){
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("BLE", "ha ido dpm");
            super.onScanResult(callbackType, result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d("BLEResuuuuuuuuults", "ya tengo los resultados");
            bluetoothDevice = results.get(0).getDevice();
            bluetoothGatt = bluetoothDevice.connectGatt(getApplicationContext(), false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d("BLEFallo", "ha fallao el escaneo");
            super.onScanFailed(errorCode);
        }
    };

    private void startBLE(){
        String TAG = "BLECristian";
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) {
            if(serviceUUIDs != null) {
                filters = new ArrayList<>();
                //for (UUID serviceUUID : serviceUUIDs) {
                //    ScanFilter filter = new ScanFilter.Builder()
                //            .setServiceUuid(new ParcelUuid(serviceUUID))
                //            .build();
                //    filters.add(filter);
                //}

                if(bleName != null) {
                    filters = new ArrayList<>();
                    for (String name : bleName) {
                        ScanFilter filter = new ScanFilter.Builder()
                                .setDeviceName(name)
                                .build();
                        filters.add(filter);
                    }
                }

                scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                        .setReportDelay(5000)
                        .build();
            }
            scanner.startScan(filters, scanSettings, scanCallback);
            Log.d(TAG, "scan started");
        }  else {
            Log.e(TAG, "could not get scanner object");
        }
    }

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
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(101, builder.build());
    }

}