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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2;
    private static final String DEVICE_INFORMATION_SERVICE_UUID = "180a";
    private static final String BATTERY_SERVICE_UUID = "180f";
    private static final String BATTERY_LEVEL_UUID = "2a19";
    private static final String SYSTEM_ID_UUID = "2a23";
    private static final String MODEL_NUMBER_STRING_UUID = "2a24";
    private static final String SERIAL_NUMBER_STRING_UUID = "2a25";
    private static final String FIRMWARE_REVISION_STRING_UUID = "2a26";
    private static final String HARDWARE_REVISION_STRING_UUID = "2a27";
    private static final String SOFTWARE_REVISION_STRING_UUID = "2a28";
    private static final String MANUFACTURER_NAME_STRING_UUID = "2a29";
    private static final String PNP_ID_UUID = "2a50";

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
            BluetoothDevice device = deviceList.get(position);
            device.connectGatt(MainActivity.this, false, gattCallback);
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
        if (!bluetoothAdapter.isEnabled()) {
            promptEnableBluetooth();
        }

        //    We only need location permission when we start scanning
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
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getDevice() != null) {
                if (!isDuplicate(result.getDevice())) {
                    synchronized (result.getDevice()) {
//                        @SuppressLint("MissingPermission") String itemDetail = result.getDevice().getName() == null ? result.getDevice().getAddress() : result.getDevice().getName();
                        String itemDetails = result.getDevice().getAddress();
                        itemDetails += result.getDevice().getName() == null ? "" : "(" + result.getDevice().getName() + ")";
                        listAdapter.add(itemDetails);
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
        private volatile boolean isOnCharacteristicReadRunning = false;

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String address = gatt.getDevice().getAddress();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.w(TAG, "onConnectionStateChange() - Successfully connected to " + address);
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
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

                    if (service.getUuid().toString().contains(DEVICE_INFORMATION_SERVICE_UUID) || service.getUuid().toString().contains(BATTERY_SERVICE_UUID)) {
                        Log.d(TAG, "onServicesDiscovered: Device Information Service Discovered");
                        StringBuilder buffer = new StringBuilder("Service Id: " + service.getUuid().toString());
                        for (int j = 0; j < characteristics.size(); j++) {
                            BluetoothGattCharacteristic characteristic = characteristics.get(j);
                            String characteristicUuid = characteristic.getUuid().toString();
                            buffer.append("\n\nCharacteristic: ");
                            buffer.append("\nUUID: ").append(characteristicUuid);
                            isOnCharacteristicReadRunning = true;
                            gatt.readCharacteristic(characteristic);
                            while (isOnCharacteristicReadRunning) {
//                                Do nothing
//                                Wait while the characteristic is being read in onCharacteristicRead function
                            }

                            if (characteristicUuid.contains(SYSTEM_ID_UUID)) {
                                buffer.append("\nSystem Id: ").append(new BigInteger(characteristic.getValue()).longValue());
                            } else if (characteristicUuid.contains(MODEL_NUMBER_STRING_UUID)) {
                                buffer.append("\nModel Number: ").append(new String(characteristic.getValue(), StandardCharsets.UTF_8));
                            } else if (characteristicUuid.contains(SERIAL_NUMBER_STRING_UUID)) {
                                buffer.append("\nSerial Number: ").append(new String(characteristic.getValue(), StandardCharsets.UTF_8));
                            } else if (characteristicUuid.contains(FIRMWARE_REVISION_STRING_UUID)) {
                                buffer.append("\nFirmware Revision: ").append(new String(characteristic.getValue(), StandardCharsets.UTF_8));
                            } else if (characteristicUuid.contains(HARDWARE_REVISION_STRING_UUID)) {
                                buffer.append("\nHardware Revision: ").append(new String(characteristic.getValue(), StandardCharsets.UTF_8));
                            } else if (characteristicUuid.contains(SOFTWARE_REVISION_STRING_UUID)) {
                                buffer.append("\nSoftware Revision: ").append(new String(characteristic.getValue(), StandardCharsets.UTF_8));
                            } else if (characteristicUuid.contains(MANUFACTURER_NAME_STRING_UUID)) {
                                buffer.append("\nManufacturer Name: ").append(new String(characteristic.getValue(), StandardCharsets.UTF_8));
                            } else if (characteristicUuid.contains(PNP_ID_UUID)) {
                                buffer.append("\nPnP Id: ").append(new BigInteger(characteristic.getValue()).longValue());
                            } else if (characteristicUuid.contains(BATTERY_LEVEL_UUID)) {
                                buffer.append("\nBattery Level: ").append(new BigInteger(characteristic.getValue()).longValue());
                            }
                        }
                        Log.d(TAG, "onServicesDiscovered: New Service: " + buffer);
                        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setMessage(buffer.toString())
                                .setTitle("Device Information")
                                .setCancelable(false)
                                .setPositiveButton("OK", (dialog, which) -> dialog.cancel())
                                .create();
                        alertDialog.show();
                    }
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onCharacteristicRead: Read characteristic: UUID: " + characteristic.getUuid().toString());
            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                Log.e(TAG, "onCharacteristicRead: Read not permitted for " + characteristic.getUuid().toString());
            } else {
                Log.e(TAG, "onCharacteristicRead: Characteristic read failed for " + characteristic.getUuid().toString());
            }

            isOnCharacteristicReadRunning = false;
        }
    };
}
