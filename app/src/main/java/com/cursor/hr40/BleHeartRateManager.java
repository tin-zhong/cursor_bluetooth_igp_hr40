package com.cursor.hr40;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class BleHeartRateManager {
    public interface Listener {
        void onStatus(String status);

        void onDeviceFound(DeviceCandidate candidate);

        void onHeartRate(HeartRateSample sample);

        void onError(String message);
    }

    public static final class DeviceCandidate {
        public final String address;
        public final String name;
        public final int rssi;
        public final boolean hasHeartRateService;
        public final boolean likelyHeartRateBand;
        public final boolean bonded;

        DeviceCandidate(
                String address,
                String name,
                int rssi,
                boolean hasHeartRateService,
                boolean likelyHeartRateBand,
                boolean bonded) {
            this.address = address;
            this.name = name;
            this.rssi = rssi;
            this.hasHeartRateService = hasHeartRateService;
            this.likelyHeartRateBand = likelyHeartRateBand;
            this.bonded = bonded;
        }

        public String displayName() {
            if (name == null || name.isEmpty()) {
                return "未知 BLE 设备";
            }
            return name;
        }
    }

    public static final UUID HEART_RATE_SERVICE =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_MEASUREMENT =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context appContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private final Map<String, BluetoothDevice> discoveredDevices = new LinkedHashMap<>();
    private boolean scanning;

    public BleHeartRateManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager manager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) {
            adapter = manager.getAdapter();
        }
    }

    @SuppressLint("MissingPermission")
    public void startScan() {
        if (adapter == null) {
            listener.onError("此设备不支持蓝牙");
            return;
        }
        if (!adapter.isEnabled()) {
            listener.onError("请先开启蓝牙");
            return;
        }
        if (!hasBluetoothPermission()) {
            listener.onError("缺少蓝牙权限");
            return;
        }

        stopScan();
        discoveredDevices.clear();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onError("无法启动 BLE 扫描");
            return;
        }

        notifyBondedDevices();

        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L)
                .build();
        scanning = true;
        listener.onStatus("正在扫描 HR40。若 iGPSPORT 或其他 App 已连接，请先在对方 App 中解除绑定/断开连接。");
        scanner.startScan(filters, settings, scanCallback);

        mainHandler.postDelayed(() -> {
            if (scanning) {
                listener.onStatus("仍在扫描：请确认 HR40 已佩戴唤醒，并且没有被 iGPSPORT 或系统蓝牙占用连接。");
            }
        }, 8000L);
        mainHandler.postDelayed(() -> {
            if (scanning) {
                listener.onStatus("暂未发现可连接的 HR40。请关闭右侧 iGPSPORT 连接后，重新点击扫描。");
            }
        }, 18000L);
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (scanner != null && scanning && hasBluetoothPermission()) {
            scanner.stopScan(scanCallback);
        }
        scanning = false;
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        stopScan();
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(String address) {
        if (!hasBluetoothPermission()) {
            listener.onError("缺少蓝牙连接权限");
            return;
        }
        BluetoothDevice device = discoveredDevices.get(address);
        if (device == null && adapter != null) {
            try {
                device = adapter.getRemoteDevice(address);
            } catch (IllegalArgumentException ignored) {
                device = null;
            }
        }
        if (device == null) {
            listener.onError("无法连接该设备，请重新扫描");
            return;
        }
        stopScan();
        connect(device);
    }

    public void close() {
        disconnect();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                handleScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            listener.onError("BLE 扫描失败: " + errorCode);
        }
    };

    @SuppressLint("MissingPermission")
    private void connect(BluetoothDevice device) {
        if (!hasBluetoothPermission()) {
            listener.onError("缺少蓝牙连接权限");
            return;
        }
        String name = safeDeviceName(device, null);
        listener.onStatus("正在连接 " + (name.isEmpty() ? "心率带" : name) + "...");
        if (gatt != null) {
            gatt.close();
        }
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postError("连接异常: " + status);
                bluetoothGatt.close();
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                postStatus("心率带已连接，正在发现服务...");
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                postStatus("心率带已断开");
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postError("发现服务失败: " + status);
                return;
            }
            BluetoothGattService service = bluetoothGatt.getService(HEART_RATE_SERVICE);
            if (service == null) {
                postError("未找到标准心率服务 0x180D");
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(HEART_RATE_MEASUREMENT);
            if (characteristic == null) {
                postError("未找到心率测量特征 0x2A37");
                return;
            }
            bluetoothGatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor == null) {
                postError("设备未提供通知描述符");
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                bluetoothGatt.writeDescriptor(descriptor);
            }
            postStatus("已订阅心率数据，开始离线监测");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
            handleCharacteristic(characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt bluetoothGatt,
                BluetoothGattCharacteristic characteristic,
                byte[] value) {
            handleCharacteristic(value);
        }
    };

    private void handleCharacteristic(byte[] value) {
        HeartRateSample sample = parseMeasurement(value);
        if (sample == null) {
            return;
        }
        mainHandler.post(() -> listener.onHeartRate(sample));
    }

    static HeartRateSample parseMeasurement(byte[] value) {
        if (value == null || value.length < 2) {
            return null;
        }
        int flags = value[0] & 0xFF;
        boolean isUInt16 = (flags & 0x01) != 0;
        int offset = 1;
        int bpm;
        if (isUInt16) {
            if (value.length < 3) {
                return null;
            }
            bpm = (value[offset] & 0xFF) | ((value[offset + 1] & 0xFF) << 8);
            offset += 2;
        } else {
            bpm = value[offset] & 0xFF;
            offset += 1;
        }

        boolean contactSupported = (flags & 0x04) != 0;
        boolean contactDetected = contactSupported && ((flags & 0x02) != 0);

        Integer energyExpended = null;
        if ((flags & 0x08) != 0 && value.length >= offset + 2) {
            energyExpended = (value[offset] & 0xFF) | ((value[offset + 1] & 0xFF) << 8);
            offset += 2;
        }

        int rrCount = 0;
        if ((flags & 0x10) != 0 && value.length > offset) {
            rrCount = (value.length - offset) / 2;
        }

        return new HeartRateSample(
                System.currentTimeMillis(),
                bpm,
                contactSupported,
                contactDetected,
                energyExpended,
                rrCount);
    }

    private void handleScanResult(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        if (device == null) {
            return;
        }
        DeviceCandidate candidate = candidateFromScan(result);
        if (!candidate.likelyHeartRateBand && candidate.name.isEmpty()) {
            return;
        }
        discoveredDevices.put(candidate.address, device);
        mainHandler.post(() -> listener.onDeviceFound(candidate));

        if (candidate.likelyHeartRateBand) {
            stopScan();
            connect(device);
        }
    }

    @SuppressLint("MissingPermission")
    private void notifyBondedDevices() {
        if (adapter == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
            return;
        }
        Set<BluetoothDevice> bondedDevices;
        try {
            bondedDevices = adapter.getBondedDevices();
        } catch (SecurityException ignored) {
            return;
        }
        for (BluetoothDevice device : bondedDevices) {
            String name = safeDeviceName(device, null);
            boolean likely = looksLikeHeartRateName(name);
            if (!likely) {
                continue;
            }
            String address = safeAddress(device);
            discoveredDevices.put(address, device);
            DeviceCandidate candidate = new DeviceCandidate(address, name, 0, false, true, true);
            mainHandler.post(() -> listener.onDeviceFound(candidate));
        }
    }

    private DeviceCandidate candidateFromScan(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        ScanRecord record = result.getScanRecord();
        String advertisedName = record == null ? null : record.getDeviceName();
        String name = safeDeviceName(device, advertisedName);
        boolean hasHeartRateService = hasHeartRateService(record);
        boolean likely = hasHeartRateService || looksLikeHeartRateName(name);
        return new DeviceCandidate(
                safeAddress(device),
                name,
                result.getRssi(),
                hasHeartRateService,
                likely,
                false);
    }

    private boolean hasHeartRateService(ScanRecord record) {
        if (record == null || record.getServiceUuids() == null) {
            return false;
        }
        for (ParcelUuid uuid : record.getServiceUuids()) {
            if (HEART_RATE_SERVICE.equals(uuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeHeartRateName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        return lower.contains("hr40")
                || lower.contains("igp")
                || lower.contains("heart")
                || lower.contains("heartrate")
                || lower.contains("hrm");
    }

    @SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device, String fallback) {
        if (fallback != null && !fallback.isEmpty()) {
            return fallback;
        }
        try {
            String name = device.getName();
            return name == null ? "" : name;
        } catch (SecurityException ignored) {
            return "";
        }
    }

    @SuppressLint("MissingPermission")
    private String safeAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (SecurityException ignored) {
            return "";
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void postStatus(String message) {
        mainHandler.post(() -> listener.onStatus(message));
    }

    private void postError(String message) {
        mainHandler.post(() -> listener.onError(message));
    }
}
