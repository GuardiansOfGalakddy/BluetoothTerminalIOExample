package com.junhwa.bluetoothterminalioexample;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.pedro.library.AutoPermissions;
import com.pedro.library.AutoPermissionsListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements AutoPermissionsListener {
    private static final UUID BLUETOOTH_LE_TELIT_SERVICE = UUID.fromString("0000fefb-0000-1000-8000-00805f9b34fb");
    private static final UUID TELIT_DATA_RX = UUID.fromString("00000001-0000-1000-8000-008025000000");
    private static final UUID TELIT_DATA_TX = UUID.fromString("00000002-0000-1000-8000-008025000000");
    private static final UUID TELIT_DATA_RX_CREDIT = UUID.fromString("00000003-0000-1000-8000-008025000000");
    private static final UUID TELIT_DATA_TX_CREDIT = UUID.fromString("00000004-0000-1000-8000-008025000000");
    TextView textView;

    //common components
    BluetoothManager bluetoothManager = null;
    BluetoothAdapter adapter = null;
    BluetoothGatt bluetoothGatt = null;
    BluetoothGattCharacteristic rx, tx, rxCredit, txCredit;

    BluetoothDevice device = null;
    UUID exUuid = null;

    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("gattCallback", "new State = Connected");
                Log.i("gattCallback", "Attempting to start service discovery:" + bluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("gattCallback", "new State = Disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("onServicesDiscovered", "onServicesDiscovered received: " + gatt.getServices().size());
                for (BluetoothGattService gattService : gatt.getServices()) {
                    Log.d("onServicesDiscovered", gattService.getUuid().toString());
                    for (BluetoothGattCharacteristic characteristic : gattService.getCharacteristics()) {
                        Log.d("characteristic", characteristic.getUuid().toString());
                        if (gattService.getUuid().equals(BLUETOOTH_LE_TELIT_SERVICE)) {
                            if (characteristic.getUuid().equals(TELIT_DATA_RX_CREDIT)) {
                                rxCredit = characteristic;
                            } else if (characteristic.getUuid().equals(TELIT_DATA_TX_CREDIT)) {
                                txCredit = characteristic;
                            } else if (characteristic.getUuid().equals(TELIT_DATA_RX)) {
                                rx = characteristic;
                            } else if (characteristic.getUuid().equals(TELIT_DATA_TX)) {
                                tx = characteristic;
                            }
                        }
                        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                            Log.d("descriptor", descriptor.getUuid() + " " + descriptor.getPermissions());
                        }
                    }
                }
            } else {
                Log.w("onServicesDiscovered", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(TELIT_DATA_TX))
                Log.d("receive", new String(characteristic.getValue()));
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i("onDescriptorWrite", descriptor.toString() + " " + status);
            super.onDescriptorWrite(gatt, descriptor, status);
        }
    };
    //common components

    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler mHandler = new Handler();
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (result == null || result.getScanRecord().getServiceUuids() == null)
                return;
            exUuid = result.getScanRecord().getServiceUuids().get(0).getUuid();
            textView.setText(result.getDevice().getName());
            device = result.getDevice();
            Log.d("onScanResult", result.getDevice().toString());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);

        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = adapter.getBluetoothLeScanner();

        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ScanFilter filter = new ScanFilter.Builder()
                                .build();
                        List<ScanFilter> filters = new ArrayList<>();
                        filters.add(filter);

                        ScanSettings settings = new ScanSettings.Builder()
                                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                .build();

                        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);

                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mBluetoothLeScanner.stopScan(mScanCallback);
                            }
                        }, 3000);
                    }
                });
                thread.start();
            }
        });

        Button button2 = findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    device.createBond();
                    bluetoothGatt = device.connectGatt(getApplicationContext(), false, gattCallback, BluetoothDevice.TRANSPORT_AUTO);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Button button3 = findViewById(R.id.button3);
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.notify(txCredit);
            }
        });

        Button button4 = findViewById(R.id.button4);
        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.notify(tx);
            }
        });

        Button button5 = findViewById(R.id.button5);
        button5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trySend(rxCredit, 64);
            }
        });

        Button button6 = findViewById(R.id.button6);
        button6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trySend(rx, new byte[]{0x42, 0x52, 0x0d});
            }
        });

        Button button7 = findViewById(R.id.button7);
        button7.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothGatt.disconnect();
            }
        });

        AutoPermissions.Companion.loadAllPermissions(this, 101);
    }

    @Override
    public void onDenied(int i, String[] strings) {

    }

    @Override
    public void onGranted(int i, String[] strings) {

    }

    private void trySend(BluetoothGattCharacteristic characteristic, int nCredits) {
        Boolean bResult = characteristic.setValue(nCredits, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        Log.v("ble", "trySendCredits setValue(): " + bResult);
        bResult = bluetoothGatt.writeCharacteristic(characteristic);
        Log.v("ble", "trySendCredits writeCharacteristic(): " + bResult);
    }

    private void trySend(BluetoothGattCharacteristic characteristic, String string) {
        Boolean bResult = characteristic.setValue(string);
        Log.v("ble", "trySendCredits setValue(): " + bResult);
        bResult = bluetoothGatt.writeCharacteristic(characteristic);
        Log.v("ble", "trySendCredits writeCharacteristic(): " + bResult);
    }

    private void trySend(BluetoothGattCharacteristic characteristic, byte[] bytes) {
        Boolean bResult = characteristic.setValue(bytes);
        Log.v("ble", "trySendCredits setValue(): " + bResult);
        bResult = bluetoothGatt.writeCharacteristic(characteristic);
        Log.v("ble", "trySendCredits writeCharacteristic(): " + bResult);
    }

    private void notify(BluetoothGattCharacteristic characteristic) {
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        characteristic.setWriteType(2);
        UUID uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(uuid);
        if (characteristic.getUuid().equals(TELIT_DATA_TX_CREDIT))
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        else if (characteristic.getUuid().equals(TELIT_DATA_TX))
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
    }
}
