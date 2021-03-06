package dev.gregbahr.ledcontroller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import com.rarepebble.colorpicker.ColorPickerView
import dagger.android.AndroidInjection
import java.util.*
import javax.inject.Inject

private const val TAG = "LedControlActivity"

class LedControlActivity : AppCompatActivity() {

    private var bound = false
    private lateinit var bluetoothService: BluetoothService

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.BluetoothBinder
            bluetoothService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    private var brightnessSliderDebounceTimer = Timer()
    private var delayTimeSliderDebounceTimer = Timer()

    @Inject
    lateinit var ledViewModel: LedViewModel

    override fun onStart() {
        super.onStart()

        Intent(applicationContext, BluetoothService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()

        unbindService(connection)
        bound = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_led_control)

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

            if (bound) {
                bluetoothService.writeBrightness(brightness)
            }

            brightnessSliderDebounceTimer.schedule(object : TimerTask() {
                override fun run() {
                    if (bound) {
                        bluetoothService.writeBrightness(brightness)
                    }
                }
            }, 250)
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

        var firstSelectDone = false
        val spinner: Spinner = findViewById(R.id.animation_spinner)
        ArrayAdapter.createFromResource(this, R.array.animations, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
            }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (firstSelectDone) {
                    ledViewModel.ledControllerRepository.animation.postValue(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                return
            }
        }
        ledViewModel.ledControllerRepository.animation.observe(this, Observer<Int> { animationType ->
            if (!firstSelectDone) {
                spinner.setSelection(animationType)
                firstSelectDone = true
            } else if (bound) {
                bluetoothService.writeAnimation(animationType)
            }
        })

        val delayTimeTextView: TextView = findViewById(R.id.delayTime)
        delayTimeTextView.text = "50"

        val delayTimeSeekBar: SeekBar = findViewById(R.id.delayBar)
        delayTimeSeekBar.min = 1
        delayTimeSeekBar.max = 255
        delayTimeSeekBar.progress = 50

        ledViewModel.ledControllerRepository.delayTime.observe(this, Observer<Int> { delayTime ->
            delayTimeSliderDebounceTimer.cancel()
            delayTimeSliderDebounceTimer = Timer()
            delayTimeSeekBar.progress = delayTime
            delayTimeTextView.text = delayTime.toString()
            if (bound) {
                bluetoothService.writeDelayTime(delayTime)
            }
            delayTimeSliderDebounceTimer.schedule(object : TimerTask() {
                override fun run() {
                    if (bound) {
                        bluetoothService.writeDelayTime(delayTime)
                    }
                }
            }, 250)
        })

        delayTimeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                ledViewModel.ledControllerRepository.delayTime.postValue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                return
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                return
            }
        })

        var firstColorPicked = false
        val colorPickerView: ColorPickerView = findViewById(R.id.colorPicker)
        colorPickerView.addColorObserver { observableColor ->
            if (firstColorPicked) {
                val h = ((observableColor.hue / 360) * 255).toByte().toUByte()
                val s = (observableColor.sat * 255).toByte().toUByte()
                val v = (observableColor.value * 255).toByte().toUByte()

                ledViewModel.ledControllerRepository.color.postValue(byteArrayOf(h.toByte(), s.toByte(), v.toByte()))
            }
        }
        ledViewModel.ledControllerRepository.color.observe(this, Observer<ByteArray> { arr ->
            if (!firstColorPicked) {
                colorPickerView.color = Color.HSVToColor(
                    floatArrayOf(
                        (arr[0].toUByte().toFloat() / 255) * 360,
                        arr[1].toUByte().toFloat() / 255,
                        arr[2].toUByte().toFloat() / 255
                    )
                )
                firstColorPicked = true
            } else if (bound) {
                bluetoothService.writeColor(arr)
            }
        })
    }
}