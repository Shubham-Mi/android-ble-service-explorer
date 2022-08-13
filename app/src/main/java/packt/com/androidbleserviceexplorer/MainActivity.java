package packt.com.androidbleserviceexplorer;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BLEPackt";

    //    Button and List-View declarations from layout
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

    private final static int REQUEST_ENABLE_BT = 1;
    private final String HEART_RATE_SERVICE_ID = "180d";
    public final String HEART_RATE_MEASUREMENT_ID = "2a37";

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

//        OnClick function to connect to the device clicked
        deviceListView.setOnItemClickListener((adapterView, view, position, id) -> {
            stopScanning();
            listAdapter.clear();
            BluetoothDevice device = deviceList.get(position);
            device.connectGatt(MainActivity.this, true, gattCallback);
        });

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("This app needs location access so this app can detect peripherals");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    //    The BluetoothLEScanner requires a callback function, which would be called for every device found
    //    The devices found would be delivered as a result to this callback
    private ScanCallback leScanCallBack = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getDevice() != null) {
                if (!isDuplicate(result.getDevice())) {
                    synchronized (result.getDevice()) {
                        @SuppressLint("MissingPermission")
                        String itemDetail = result.getDevice().getName() == null ? result.getDevice().getAddress() : result.getDevice().getName();
                        listAdapter.add(itemDetail);
                        deviceList.add(result.getDevice());
                    }
                }
            }
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
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange() - STATE_CONNECTED");
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "onConnectionStateChange() - STATE_DISCONNECTED");
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
                    StringBuilder buffer = new StringBuilder(services.get(i).getUuid().toString());
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (int j = 0; j < characteristics.size(); j++) {
                        buffer.append("\n");
                        buffer.append("Characteristics:").append(characteristics.get(j).getUuid().toString());
                        if (buffer.toString().contains(HEART_RATE_SERVICE_ID)) {
                            if (characteristics.get(j).getUuid().toString().contains(HEART_RATE_MEASUREMENT_ID)) {
                                gatt.setCharacteristicNotification(characteristics.get(j), true);
                            }
                        }
                    }
                    listAdapter.add(buffer.toString());
                }
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().toString().contains(HEART_RATE_MEASUREMENT_ID)) {
                int flag = characteristic.getProperties();
                int format;
                if ((flag & 0x01) != 0) {
                    format = BluetoothGattCharacteristic.FORMAT_UINT16;
                } else {
                    format = BluetoothGattCharacteristic.FORMAT_UINT8;
                }
                final int heartRate = characteristic.getIntValue(format, 1);
                Log.d(TAG, String.format("Received heart rate: %d", heartRate));
                // Write a message to the database
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                DatabaseReference myRef = database.getReference("heartrate");

                myRef.setValue(Integer.toString(heartRate));
            }
        }
    };

    public void initializeBluetooth() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    @SuppressLint("MissingPermission")
    public void startScanning() {
        listAdapter.clear();
        deviceList.clear();
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);
        AsyncTask.execute(() -> bluetoothLeScanner.startScan(leScanCallBack));
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        AsyncTask.execute(() -> bluetoothLeScanner.stopScan(leScanCallBack));
    }
}
