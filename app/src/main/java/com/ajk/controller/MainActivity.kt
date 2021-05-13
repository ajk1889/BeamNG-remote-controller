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
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.URI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private var rx = Short.MIN_VALUE
    private var ry = Short.MIN_VALUE
    private var lx = Short.MIN_VALUE
    private var ly = Short.MIN_VALUE

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

    private fun showConnectionDialog() {
        val view = layoutInflater.inflate(R.layout.connection_properties, null)
        view.findViewById<EditText>(R.id.ipAddress)
            .setText(preferences.getString("ipAddress", "192.168.18."))
        view.findViewById<EditText>(R.id.port)
            .setText(preferences.getString("port", "31415"))
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val ipAddress = view.findViewById<EditText>(R.id.ipAddress).text.toString()
                val port = view.findViewById<EditText>(R.id.port).text.toString()
                preferences.edit()
                    .putString("ipAddress", ipAddress)
                    .putString("port", port)
                    .apply()
                connect("ws://$ipAddress:$port")
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
            val now = System.currentTimeMillis()
            if (now - previousTime > MIN_TIME_BTW_INPUTS) {
                conn.sendMessage(events)
                return now
            }
            return previousTime
        }

        findViewById<SeekBar>(R.id.accelerator).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                var lastEventSentTimestamp = 0L
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val value = (progress - 50) / 50.0 * Short.MAX_VALUE
                    ly = value.toInt().toShort()
                    lastEventSentTimestamp = sendRequestConditionally(
                        lastEventSentTimestamp,
                        listOf(Event(ANALOG_LY, ly), Event(ANALOG_LX, lx))
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        findViewById<SeekBar>(R.id.brake).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                var lastEventSentTimestamp = 0L
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val value = (progress - 50) / 50.0 * Short.MAX_VALUE
                    ry = value.toInt().toShort()
                    lastEventSentTimestamp = sendRequestConditionally(
                        lastEventSentTimestamp,
                        listOf(Event(ANALOG_RY, ry), Event(ANALOG_RX, rx))
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        findViewById<SeekBar>(R.id.clutch).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                var lastEventSentTimestamp = 0L
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val value = (progress - 50) / 50.0 * Short.MAX_VALUE
                    rx = value.toInt().toShort()
                    lastEventSentTimestamp = sendRequestConditionally(
                        lastEventSentTimestamp,
                        listOf(Event(ANALOG_RY, ry), Event(ANALOG_RX, rx))
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
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
                lx = when {
                    scaledAngle > Short.MAX_VALUE -> Short.MAX_VALUE
                    scaledAngle < Short.MIN_VALUE -> Short.MIN_VALUE
                    else -> scaledAngle.toInt().toShort()
                }
                lastEventSentTimestamp = sendRequestConditionally(
                    lastEventSentTimestamp,
                    listOf(Event(ANALOG_LY, ly), Event(ANALOG_LX, lx))
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

    fun sqrtAvg(n0: Float, n1: Float, n2: Float) = sqrt(n0 * n0 + n1 * n1 + n2 * n2)
}