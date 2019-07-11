package dev.gregbahr.ledcontroller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import dagger.android.AndroidInjection
import java.util.*
import javax.inject.Inject

val REQUEST_APP_PERMS = 1

class MainActivity : AppCompatActivity() {
    private var bound = false
    @Inject
    lateinit var ledViewModel: LedViewModel
    private lateinit var btService: BluetoothService
    private var brightnessSliderDebounceTimer = Timer()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.LocalBinder
            btService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
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
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN
                ),
                REQUEST_APP_PERMS
            )
        }

        Intent(this, BluetoothService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        val brightnessTextView: TextView = findViewById(R.id.brightness_textview)
        val seekBar: SeekBar = findViewById(R.id.brightness_bar)
        seekBar.max = 255
        seekBar.min = 0
        seekBar.progress = 0
        brightnessTextView.text = resources.getString(R.string.brightness, 0)

        ledViewModel.ledControllerRepository.brightness.observe(this, Observer<Int> { brightness ->
            brightnessSliderDebounceTimer.cancel()
            brightnessSliderDebounceTimer = Timer()
            seekBar.progress = brightness
            brightnessTextView.text = resources.getString(R.string.brightness, brightness)
            brightnessSliderDebounceTimer.schedule(object : TimerTask() {
                override fun run() {
                    btService.writeBrightness(brightness)
                }
            }, 50)
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                ledViewModel.ledControllerRepository.brightness.postValue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                return
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                return
            }
        })
    }
}
