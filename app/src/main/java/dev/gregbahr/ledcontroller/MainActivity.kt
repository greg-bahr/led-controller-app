package dev.gregbahr.ledcontroller

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val REQUEST_APP_PERMS = 1
private const val REQUEST_ENABLE_BT = 2

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private var bound = false
    private lateinit var bluetoothService: BluetoothService

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.BluetoothBinder
            bluetoothService = binder.getService()
            bound = true

            if (bluetoothService.isBonded) {
                Log.i(TAG, "Is bonded, switching to LedControlActivity.")
                startActivity(Intent(this@MainActivity, LedControlActivity::class.java))
            } else {
                Log.i(TAG, "Not bonded, attempting to pair.")
                attemptToPair()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    private val deviceManager: CompanionDeviceManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(CompanionDeviceManager::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN
                ),
                REQUEST_APP_PERMS
            )
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter.isEnabled) {
            Log.i(TAG, "Bluetooth is enabled, binding to BluetoothService.")
            Intent(applicationContext, BluetoothService::class.java).also { intent ->
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        } else {
            Log.i(TAG, "Bluetooth is disabled, requesting the user to enable.")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    fun attemptToPair() {
        Log.i(TAG, "No devices bonded, attempting to pair.")

        val scanFilter: ScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(LED_CONTROLLER_SERVICE_UUID))
            .build()

        val deviceFilter: BluetoothLeDeviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(scanFilter)
            .build()

        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)
            .build()

        deviceManager.associate(pairingRequest,
            object : CompanionDeviceManager.Callback() {
                override fun onFailure(error: CharSequence?) {
                    Log.e(TAG, error.toString())
                }

                override fun onDeviceFound(chooserLauncher: IntentSender?) {
                    startIntentSenderForResult(chooserLauncher,
                        SELECT_DEVICE_REQUEST_CODE, null, 0,0, 0)
                }

            }, null)
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        bound = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            SELECT_DEVICE_REQUEST_CODE -> when(resultCode) {
                Activity.RESULT_OK -> {
                    val deviceToPair: ScanResult = data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)!!
                    Log.i(TAG, deviceToPair.toString())
                    deviceToPair.device.createBond()

                    startActivity(Intent(this, LedControlActivity::class.java))
                }
            }
            REQUEST_ENABLE_BT -> when(resultCode) {
                Activity.RESULT_OK -> {
                    Intent(applicationContext, BluetoothService::class.java).also { intent ->
                        bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    }
                }
                Activity.RESULT_CANCELED -> {
                    Log.i(TAG, "User denied bluetooth, guess they get a blank screen to stare at :(")
                }
            }
        }
    }
}
