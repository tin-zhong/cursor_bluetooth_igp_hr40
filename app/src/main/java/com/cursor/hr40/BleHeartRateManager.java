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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class BleHeartRateManager {
    public interface Listener {
        void onStatus(String status);

        void onHeartRate(HeartRateSample sample);

        void onError(String message);
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
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onError("无法启动 BLE 扫描");
            return;
        }

        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanning = true;
        listener.onStatus("正在扫描 HR40 心率带...");
        scanner.startScan(filters, settings, scanCallback);

        mainHandler.postDelayed(() -> {
            if (scanning) {
                listener.onStatus("仍在扫描。请确认 HR40 已佩戴并处于唤醒状态。");
            }
        }, 8000L);
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

    public void close() {
        disconnect();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device == null || !looksLikeHeartRateBand(result)) {
                return;
            }
            stopScan();
            connect(device);
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
        String name = safeDeviceName(device);
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
                if (gatt == bluetoothGatt) {
                    gatt = null;
                }
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

    private boolean looksLikeHeartRateBand(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        if (record != null && record.getServiceUuids() != null) {
            for (ParcelUuid uuid : record.getServiceUuids()) {
                if (HEART_RATE_SERVICE.equals(uuid.getUuid())) {
                    return true;
                }
            }
        }
        String name = safeDeviceName(result.getDevice()).toLowerCase(Locale.US);
        return name.contains("hr40") || name.contains("igp") || name.contains("heart");
    }

    @SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? "" : name;
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
