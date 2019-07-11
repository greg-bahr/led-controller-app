package dev.gregbahr.ledcontroller

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.LifecycleService
import dagger.android.AndroidInjection
import java.util.*
import javax.inject.Inject

class BluetoothService : LifecycleService() {

    private val TAG = "BluetoothService"
    private val binder: IBinder = LocalBinder()

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var btLeScanner: BluetoothLeScanner
    private lateinit var ledControllerDevice: BluetoothDevice
    private lateinit var ledControllerGatt: BluetoothGatt

    private val LED_CONTROLLER_BRIGHTNESS_CHARACTERISTIC_ID = UUID.fromString("de6e22e4-07a0-4924-bc3c-a054ed998e31")
    private val LED_CONTROLLER_ANIMATION_CHARACTERISTIC_ID = UUID.fromString("a96ac45a-57af-4b56-b92d-e9dfb800b521")
    private val LED_CONTROLLER_COLOR_CHARACTERISTIC_ID = UUID.fromString("75e42479-5c7e-494d-b391-1d1311153bf5")
    private val LED_CONTROLLER_DELAYTIME_CHARACTERISTIC_ID = UUID.fromString("778decdc-ef0f-4151-9ab6-000150d7d21a")
    private val LED_CONTROLLER_SERVICE_ID = UUID.fromString("318e961b-a7ba-4acf-95a3-11d94bf554b1")

    private lateinit var gatt: BluetoothGatt
    private lateinit var brightnessCharacteristic: BluetoothGattCharacteristic
    private lateinit var animationCharacteristic: BluetoothGattCharacteristic
    private lateinit var colorCharacteristic: BluetoothGattCharacteristic
    private lateinit var delayCharacteristic: BluetoothGattCharacteristic
    private lateinit var ledControllerService: BluetoothGattService

    @Inject
    lateinit var ledControllerRepository: LedControllerRepository

    private val timer: Timer = Timer()

    fun getBrightness(): Int {
        return brightnessCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
    }

    fun writeBrightness(brightness: Int) {
        brightnessCharacteristic.value = byteArrayOf(brightness.toByte())
        gatt.writeCharacteristic(brightnessCharacteristic)
    }

    private val ledControllerScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            Log.i(TAG, result.toString())
            if (result != null) {
                result.device.createBond()
                this@BluetoothService.ledControllerDevice = result.device
                stopScan()
                connectGatt()
            }
        }
    }

    private val ledControllerGattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            when (characteristic) {
                brightnessCharacteristic -> {
                    Log.i(
                        TAG,
                        "Brightness: " + brightnessCharacteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT8,
                            0
                        )
                    )
                    ledControllerRepository.brightness.postValue(
                        brightnessCharacteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT8,
                            0
                        )
                    )
                    gatt?.readCharacteristic(colorCharacteristic)
                }
                animationCharacteristic -> {
                    Log.i(TAG, "Animation: " + characteristic.value.get(0))
                    gatt?.readCharacteristic(delayCharacteristic)
                }
                delayCharacteristic -> {
                    Log.i(TAG, "Delay: " + characteristic.value.get(0))
                }
                colorCharacteristic -> {
                    val h = characteristic.value.get(0).toUByte().toString(16)
                    val s = characteristic.value.get(1).toUByte().toString(16)
                    val v = characteristic.value.get(2).toUByte().toString(16)
                    Log.i(TAG, "Color: $h, $s, $v")
                    gatt?.readCharacteristic(animationCharacteristic)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            when (characteristic) {
                brightnessCharacteristic -> {
                    Log.i(
                        TAG,
                        "Set brightness to: " + brightnessCharacteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT8,
                            0
                        )
                    )
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.i(TAG, "Services discovered.")

            if (gatt != null) {
                this@BluetoothService.gatt = gatt
                ledControllerService = gatt.getService(LED_CONTROLLER_SERVICE_ID)
                brightnessCharacteristic =
                    ledControllerService.getCharacteristic(LED_CONTROLLER_BRIGHTNESS_CHARACTERISTIC_ID)
                colorCharacteristic = ledControllerService.getCharacteristic(LED_CONTROLLER_COLOR_CHARACTERISTIC_ID)
                animationCharacteristic =
                    ledControllerService.getCharacteristic(LED_CONTROLLER_ANIMATION_CHARACTERISTIC_ID)
                delayCharacteristic = ledControllerService.getCharacteristic(LED_CONTROLLER_DELAYTIME_CHARACTERISTIC_ID)

                Log.i(TAG, "LED service discovered.")

                gatt.readCharacteristic(brightnessCharacteristic)
            }

        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothGatt connected")
                gatt?.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothGatt disconnected")
            }
        }
    }

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()

        bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = this.bluetoothManager.adapter
        btLeScanner = this.bluetoothAdapter.bluetoothLeScanner

        bluetoothAdapter.takeIf { !it.isEnabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
        }

        startScan()
    }

    fun startScan() {
        val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(LED_CONTROLLER_SERVICE_ID)).build()
        btLeScanner.startScan(listOf(scanFilter), ScanSettings.Builder().build(), ledControllerScanCallback)
    }

    fun stopScan() {
        btLeScanner.stopScan(ledControllerScanCallback)
    }

    fun connectGatt() {
        this.ledControllerGatt =
            this.ledControllerDevice.connectGatt(this, true, ledControllerGattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    override fun onDestroy() {
        super.onDestroy()
        btLeScanner.stopScan(ledControllerScanCallback)
        this.ledControllerGatt.disconnect()
        this.ledControllerGatt.close()
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return this.binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService {
            return this@BluetoothService
        }
    }
}