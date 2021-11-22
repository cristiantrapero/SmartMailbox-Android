# SmartMailbox-Android
Sistemas Distribuidos y Empotrados final project. A smart mailbox to receive packages when you aren't at home. Android app.

This project implements the Android application for connecting with Smart Mailbox.

## Communication schema
![Connection diagram](https://i.ibb.co/3c475kc/SDE-Aplicaci-n-final.png)

## How to run
1. Download this repository
2. Open the project in Android Studio
3. Change _SERVER_URI_, _USERNAME_ and _PASSWORD_ in the MqttHelper.java
4. Build and run in Android phone

## Android libraries
1. MQTT android library: https://github.com/eclipse/paho.mqtt.android

## TODO
1. Save notifications in SQlite
2. BLE notifications
3. BLE auto-connection with ESP32

## References:
- Making Android BLE work: https://medium.com/@martijn.van.welie/making-android-ble-work-part-1-a736dcd53b02
- MQTT Android Cliente Tutorial: https://wildanmsyah.wordpress.com/2017/05/11/mqtt-android-client-tutorial/
- Android Oficial Documentation: https://developer.android.com/guide/topics/connectivity/bluetooth-le?hl=es-419

