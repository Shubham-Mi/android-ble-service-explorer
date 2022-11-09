# android-ble-service-explorer
A service Explorer app which will scan manually through the use of start/stop buttons.

## What are BLE Profiles?
BLE profiles are the description of the behavior of that BLE device. For example, Blood Pressure Profile.
<br/><br/>
<img alt="BLE Profiles" height="450" src="/screenshots/Profiles.png" width="333"/>
<br/>

## What does the app do?
The app searches for all the nearby BLE devices and then displays them on the UI. When user clicks on a device, the app establishes a GATT connection with the device. Once the connection has been established, it explores all the services present in the profile. For now, when the app encounters a <b>Device Information Service</b> or <b>Battery Service</b>, it reads all the characteristics present in them, reads them one by one, and then displays them on the UI.

### Device Information Service
* This service exposes manufacturer and/or vendor information about a device.
* This service is not dependent on any other service. 
* This service is compatible with Bluetooth core specification host that includes the <b>Generic Attribute Profile (GATT)</b>.
* [More Information on Device Information Service](https://www.bluetooth.com/specifications/specs/device-information-service-1-1)

### Battery Service
* The Battery Service exposes the state of a battery within a device.
* This service has no dependencies on other GATT-based services.
* This service is compatible with Bluetooth core specification host that includes the Generic Attribute Profile (GATT).
* [More information on Battery Service](https://developer.nordicsemi.com/nRF_Connect_SDK/doc/1.0.0/nrf/include/bluetooth/services/bas_c.html#:~:text=The%20Battery%20Service%20Client%20can,Battery%20Service%20with%20one%20characteristic.)

## How the app works?
* As soon as the app is run on the device, if not enabled, the app asks to enable Bluetooth on the device.
* Since, we only need Location service when we start searching for nearby Bluetooth devices, Location permission is asked when we start searching.
* Once, we click on <b>Start Scanning</b> button, <b>BluetoothLeScanner</b> starts scanning for nearby devices through <b>ScanCallback</b>. ScanCallback scans all the devices and adds them to the <b>ArrayAdapter</b> list. A device list is also maintained to avoid same device to be displayed multiple times.
* When we find the device we want to connect, we can select it from the list and establish a <b>GATT</b> connection through <b>GattCallback</b> function.
* In GattCallback, we discover all the services present in the device and and read their characteristics through <b>OnCharacteristicRead</b> function.

### Bluetooth Enable Request

<img alt="Bluetooth Enable Request" height="650" src="/screenshots/Bluetooth%20Enable%20Request.png" width="333"/>

### Main Page

<img alt="Main page" height="650" src="/screenshots/Home%20Page.png" width="333"/>

### List of Devices

<img alt="List of device" height="650" src="/screenshots/List%20of%20devices.png" width="333"/>

### Display important Services and their characteristics

<img alt="Services and characteristics" height="650" src="/screenshots/Service%20and%20Characteristics.png" width="333"/>

