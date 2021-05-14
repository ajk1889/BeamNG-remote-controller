package com.ajk.controller

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.URI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private val left = AnalogValues(Short.MIN_VALUE, Short.MIN_VALUE)
    private val right = AnalogValues(Short.MIN_VALUE, Short.MIN_VALUE)

    private var connection: Connection? = null
    private lateinit var preferences: SharedPreferences

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preferences = getPreferences(MODE_PRIVATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        if (connection == null) showConnectionDialog()
        else connect(connection!!.uri.toString())
    }

    override fun onPause() {
        super.onPause()
        connection?.close()
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.action == KeyEvent.ACTION_DOWN)
                connection?.sendMessage(listOf(Event(ANALOG_RX, right.x), Event(ANALOG_RY, Short.MAX_VALUE)))
            else if (event.action == KeyEvent.ACTION_UP)
                connection?.sendMessage(listOf(Event(ANALOG_RX, right.x), Event(ANALOG_RY, Short.MIN_VALUE)))
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showConnectionDialog() {
        val view = layoutInflater.inflate(R.layout.connection_properties, null)
        val ipAddressView = view.findViewById<EditText>(R.id.ipAddress)
        val portView = view.findViewById<EditText>(R.id.port)
        val slowlyReleaseAcceleratorView = view.findViewById<CheckBox>(R.id.slowlyReleaseAccelerator)
        val slowlyReleaseBrakeView = view.findViewById<CheckBox>(R.id.slowlyReleaseBrake)
        val slowlyReleaseClutchView = view.findViewById<CheckBox>(R.id.slowlyReleaseClutch)
        ipAddressView.setText(preferences.getString("ipAddress", "192.168.18."))
        portView.setText(preferences.getString("port", "31415"))
        slowlyReleaseClutchView.isChecked = preferences.getBoolean("slowlyReleaseClutch", true)
        slowlyReleaseBrakeView.isChecked = preferences.getBoolean("slowlyReleaseBrake", false)
        slowlyReleaseAcceleratorView.isChecked = preferences.getBoolean("slowlyReleaseAccelerator", false)
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                preferences.edit()
                    .putString("ipAddress", ipAddressView.text.toString())
                    .putString("port", portView.text.toString())
                    .putBoolean("slowlyReleaseAccelerator", slowlyReleaseAcceleratorView.isChecked)
                    .putBoolean("slowlyReleaseBrake", slowlyReleaseBrakeView.isChecked)
                    .putBoolean("slowlyReleaseClutch", slowlyReleaseClutchView.isChecked)
                    .apply()
                connect("ws://${ipAddressView.text}:${portView.text}")
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun connect(url: String) {
        val conn = Connection(URI(url))
        connection = conn
        conn.connect()

        fun sendRequestConditionally(previousTime: Long, events: List<Event>): Long {
            val now = now()
            if (now - previousTime > MIN_TIME_BTW_INPUTS) {
                conn.sendMessage(events)
                return now
            }
            return previousTime
        }

        findViewById<SeekBar>(R.id.accelerator).apply {
            setOnSeekBarChangeListener(
                SeekChangeListener(
                    connection = conn,
                    analogValues = this@MainActivity.left,
                    direction = 'y',
                    position = 'l',
                    autoRelease = preferences.getBoolean("slowlyReleaseAccelerator", false)
                )
            )
        }

        findViewById<SeekBar>(R.id.brake).apply {
            setOnSeekBarChangeListener(
                SeekChangeListener(
                    connection = conn,
                    analogValues = this@MainActivity.right,
                    direction = 'y',
                    position = 'r',
                    autoRelease = preferences.getBoolean("slowlyReleaseBrake", false)
                )
            )
        }

        findViewById<SeekBar>(R.id.clutch).apply {
            setOnSeekBarChangeListener(
                SeekChangeListener(
                    connection = conn,
                    analogValues = this@MainActivity.right,
                    direction = 'x',
                    position = 'r',
                    autoRelease = preferences.getBoolean("slowlyReleaseClutch", true)
                )
            )
        }

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager.registerListener(object : SensorEventListener {
            var lastEventSentTimestamp = 0L
            var previousAngle = 0.0
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val average = sqrtAvg(event.values[0], event.values[1], event.values[2])
                val angle = asin(-event.values[1] / average) / (Math.PI / 2.0)
                if (abs(angle - previousAngle) < 0.035) return
                previousAngle = angle

                val scaledAngle = USHORT_MAX_VALUE * angle
                left.x = when {
                    scaledAngle > Short.MAX_VALUE -> Short.MAX_VALUE
                    scaledAngle < Short.MIN_VALUE -> Short.MIN_VALUE
                    else -> scaledAngle.toInt().toShort()
                }
                lastEventSentTimestamp = sendRequestConditionally(
                    lastEventSentTimestamp,
                    listOf(Event(ANALOG_LY, left.y), Event(ANALOG_LX, left.x))
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 20 * 1000)

        findViewById<TextView>(R.id.gearUp).setOnTouchListener { _, event ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> conn.sendMessage(Event(UP, 1))
                KeyEvent.ACTION_UP -> conn.sendMessage(Event(UP, 0))
            }
            return@setOnTouchListener true
        }

        findViewById<TextView>(R.id.gearDown).setOnTouchListener { _, event ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> conn.sendMessage(Event(DOWN, 1))
                KeyEvent.ACTION_UP -> conn.sendMessage(Event(DOWN, 0))
            }
            return@setOnTouchListener true
        }

        findViewById<TextView>(R.id.handBrake).setOnTouchListener { _, event ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> conn.sendMessage(Event(SQUARE, 1))
                KeyEvent.ACTION_UP -> conn.sendMessage(Event(SQUARE, 0))
            }
            return@setOnTouchListener true
        }

        findViewById<TextView>(R.id.engineStart).setOnTouchListener { _, event ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> conn.sendMessage(Event(START, 1))
                KeyEvent.ACTION_UP -> conn.sendMessage(Event(START, 0))
            }
            return@setOnTouchListener true
        }
    }

    private fun sqrtAvg(n0: Float, n1: Float, n2: Float) = sqrt(n0 * n0 + n1 * n1 + n2 * n2)

    private fun now() = System.currentTimeMillis()
}