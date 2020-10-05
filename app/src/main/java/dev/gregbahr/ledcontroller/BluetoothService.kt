package dev.gregbahr.ledcontroller

import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import dagger.android.AndroidInjection
import java.util.*
import javax.inject.Inject

class BluetoothService : LifecycleService() {

    private val TAG = "BluetoothService"
    private val binder = BluetoothBinder()

    var isConnected: Boolean = false
        private set

    var isBonded: Boolean = false
        private set

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var ledControllerDevice: BluetoothDevice
    private lateinit var ledControllerGatt: BluetoothGatt

    private val LED_CONTROLLER_BRIGHTNESS_CHARACTERISTIC = UUID.fromString(LED_CONTROLLER_BRIGHTNESS_CHARACTERISTIC_UUID)
    private val LED_CONTROLLER_ANIMATION_CHARACTERISTIC = UUID.fromString(LED_CONTROLLER_ANIMATION_CHARACTERISTIC_UUID)
    private val LED_CONTROLLER_COLOR_CHARACTERISTIC = UUID.fromString(LED_CONTROLLER_COLOR_CHARACTERISTIC_UUID)
    private val LED_CONTROLLER_DELAYTIME_CHARACTERISTIC = UUID.fromString(LED_CONTROLLER_DELAYTIME_CHARACTERISTIC_UUID)
    private val LED_CONTROLLER_SERVICE = UUID.fromString(LED_CONTROLLER_SERVICE_UUID)

    private lateinit var gatt: BluetoothGatt
    private lateinit var brightnessCharacteristic: BluetoothGattCharacteristic
    private lateinit var animationCharacteristic: BluetoothGattCharacteristic
    private lateinit var colorCharacteristic: BluetoothGattCharacteristic
    private lateinit var delayCharacteristic: BluetoothGattCharacteristic
    private lateinit var ledControllerService: BluetoothGattService

    @Inject
    lateinit var ledControllerRepository: LedControllerRepository

    fun writeBrightness(brightness: Int) {
        brightnessCharacteristic.value = byteArrayOf(brightness.toByte())
        gatt.writeCharacteristic(brightnessCharacteristic)
    }

    fun writeDelayTime(delayTime: Int) {
        delayCharacteristic.value = byteArrayOf(delayTime.toByte())
        gatt.writeCharacteristic(delayCharacteristic)
    }

    fun writeAnimation(animationType: Int) {
        animationCharacteristic.value = byteArrayOf(animationType.toByte())
        gatt.writeCharacteristic(animationCharacteristic)
    }

    fun writeColor(array: ByteArray) {
        colorCharacteristic.value = array
        gatt.writeCharacteristic(colorCharacteristic)
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
                    ledControllerRepository.animation.postValue(animationCharacteristic.value.get(0).toInt())
                    gatt?.readCharacteristic(delayCharacteristic)
                }
                delayCharacteristic -> {
                    Log.i(TAG, "Delay: " + characteristic.value.get(0))
                    ledControllerRepository.delayTime.postValue(
                        delayCharacteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT8,
                            0
                        )
                    )
                }
                colorCharacteristic -> {
                    val h = characteristic.value.get(0).toUByte()
                    val s = characteristic.value.get(1).toUByte()
                    val v = characteristic.value.get(2).toUByte()
                    Log.i(TAG, "Color: ${h.toString(16)}, ${s.toString(16)}, ${v.toString(16)}")

                    ledControllerRepository.color.postValue(byteArrayOf(h.toByte(), s.toByte(), v.toByte()))
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
                ledControllerService = gatt.getService(LED_CONTROLLER_SERVICE)

                brightnessCharacteristic =
                    ledControllerService.getCharacteristic(LED_CONTROLLER_BRIGHTNESS_CHARACTERISTIC)
                colorCharacteristic =
                    ledControllerService.getCharacteristic(LED_CONTROLLER_COLOR_CHARACTERISTIC)
                animationCharacteristic =
                    ledControllerService.getCharacteristic(LED_CONTROLLER_ANIMATION_CHARACTERISTIC)
                delayCharacteristic =
                    ledControllerService.getCharacteristic(LED_CONTROLLER_DELAYTIME_CHARACTERISTIC)

                Log.i(TAG, "LED service discovered.")

                gatt.readCharacteristic(brightnessCharacteristic)
                isConnected = true
            }

        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothGatt connected")
                gatt?.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothGatt disconnected")
                isConnected = false
            }
        }
    }

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()

        bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = this.bluetoothManager.adapter

        checkForBondedDevice()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)

        connectToBondedDevice()

        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)

        if (isConnected) {
            this.ledControllerGatt.disconnect()
            this.ledControllerGatt.close()
        }

        return false
    }

    fun checkForBondedDevice() {
        val device = bluetoothAdapter.bondedDevices.find { it.name == "LED Receiver" }

        if (device != null) {
            this.ledControllerDevice = device
            isBonded = true
        } else {
            isBonded = false
        }
    }

    fun connectToBondedDevice() {
        if (isBonded) {
            this.ledControllerGatt =
                this.ledControllerDevice.connectGatt(this, true, ledControllerGattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    inner class BluetoothBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
}