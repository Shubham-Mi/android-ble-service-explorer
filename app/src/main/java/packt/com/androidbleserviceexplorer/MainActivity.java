package packt.com.androidbleserviceexplorer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2;
    public static final String BATTERY_SERVICE_UUID = "180f";
    public static final String BATTERY_LEVEL_UUID = "00002a19-0000-1000-8000-00805f9b34fb";

    Button startScanningButton;
    Button stopScanningButton;
    ListView deviceListView;

    //    The ListViews in Android are backed by adapters, which hold the data being displayed in a ListView
    //    deviceList will hold the data to be displayed in ListView
    ArrayAdapter<String> listAdapter;
    ArrayList<BluetoothDevice> deviceList;

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceListView = findViewById(R.id.deviceListView);
        startScanningButton = findViewById(R.id.startScanButton);
        stopScanningButton = findViewById(R.id.stopScanButton);
        stopScanningButton.setVisibility(View.INVISIBLE);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        deviceListView.setAdapter(listAdapter);
        deviceList = new ArrayList<>();

        startScanningButton.setOnClickListener(view -> startScanning());
        stopScanningButton.setOnClickListener(view -> stopScanning());
        initializeBluetooth();

        deviceListView.setOnItemClickListener((adapterView, view, position, id) -> {
            stopScanning();
            listAdapter.clear();
            BluetoothDevice device = deviceList.get(position);
            device.connectGatt(MainActivity.this, true, gattCallback);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!bluetoothAdapter.isEnabled()) {
            promptEnableBluetooth();
        }
    }

    @SuppressLint("MissingPermission")
    private void promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activityResultLauncher.launch(enableIntent);
        }
    }

    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != MainActivity.RESULT_OK) {
                    promptEnableBluetooth();
                }
            }
    );

    private boolean hasPermission(String permissionType) {
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return;
        }
        runOnUiThread(() -> {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Location Permission Required");
            alertDialog.setMessage("This app needs location access to detect peripherals.");
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE));
            alertDialog.show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                requestLocationPermission();
            } else {
                startScanning();
            }
        }
    }

    public void initializeBluetooth() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    @SuppressLint("MissingPermission")
    public void startScanning() {
        //    We only need Location when we scanning
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestLocationPermission();
        } else {
            listAdapter.clear();
            deviceList.clear();
            startScanningButton.setVisibility(View.INVISIBLE);
            stopScanningButton.setVisibility(View.VISIBLE);
            AsyncTask.execute(() -> bluetoothLeScanner.startScan(leScanCallBack));
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        AsyncTask.execute(() -> bluetoothLeScanner.stopScan(leScanCallBack));
    }

    //    The BluetoothLEScanner requires a callback function, which would be called for every device found.
    private final ScanCallback leScanCallBack = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getDevice() != null) {
                if (!isDuplicate(result.getDevice())) {
                    synchronized (result.getDevice()) {
                        @SuppressLint("MissingPermission") String itemDetail = result.getDevice().getName() == null ? result.getDevice().getAddress() : result.getDevice().getName();
                        listAdapter.add(itemDetail);
                        deviceList.add(result.getDevice());
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: code:" + errorCode);
        }
    };

    //    Called by ScanCallBack function to check if the device is already present in listAdapter or not.
    @SuppressLint("MissingPermission")
    private boolean isDuplicate(BluetoothDevice device) {
        for (int i = 0; i < listAdapter.getCount(); ++i) {
            String addedDeviceDetail = listAdapter.getItem(i);
            if (addedDeviceDetail.equals(device.getAddress()) || addedDeviceDetail.equals(device.getName())) {
                return true;
            }
        }
        return false;
    }

    //    The connectGatt method requires a BluetoothGattCallback
    //    Here the results of connection state changes and services discovery would be delivered asynchronously.
    protected BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String address = gatt.getDevice().getAddress();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.w(TAG, "onConnectionStateChange() - Successfully connected to " + address);
                    Toast.makeText(MainActivity.this, "Device Connected", Toast.LENGTH_LONG).show();
                    boolean discoverServicesOk = gatt.discoverServices();
                    Log.i(TAG, "onConnectionStateChange: discovered Services: " + discoverServicesOk);
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.w(TAG, "onConnectionStateChange() - Successfully disconnected from " + address);
                    gatt.close();
                }
            } else {
                Log.w(TAG, "onConnectionStateChange: Error " + status + " encountered for " + address);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            final List<BluetoothGattService> services = gatt.getServices();
            runOnUiThread(() -> {
                for (int i = 0; i < services.size(); i++) {
                    BluetoothGattService service = services.get(i);
                    StringBuilder buffer = new StringBuilder(service.getUuid().toString());
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (int j = 0; j < characteristics.size(); j++) {
                        buffer.append("\n");
                        buffer.append("Characteristic: ").append(characteristics.get(j).getUuid().toString());
                    }

                    if (service.getUuid().toString().contains(BATTERY_SERVICE_UUID)) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(BATTERY_LEVEL_UUID));
                        gatt.readCharacteristic(characteristic);
                        buffer.append("\nBattery Level ").append(Arrays.toString(characteristic.getValue()));
                    }

                    listAdapter.add(buffer.toString());
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onCharacteristicRead: Read characteristic " + characteristic.getUuid().toString());
            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                Log.e(TAG, "onCharacteristicRead: Read not permitted for " + characteristic.getUuid().toString());
            } else {
                Log.e(TAG, "onCharacteristicRead: Characteristic read failed for " + characteristic.getUuid().toString());
            }
        }
    };
}
