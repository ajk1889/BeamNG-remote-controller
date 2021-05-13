package com.ajk.controller

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.ajk.controller.EventCodes.ANALOG_LX
import com.ajk.controller.EventCodes.ANALOG_LY
import java.net.URI

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val conn = Connection(URI("ws://192.168.18.63:31415"))
        conn.connect()
        findViewById<SeekBar>(R.id.accelerator).apply {
            this.max = 256*256 - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    conn.sendMessage(listOf(
                        Event(ANALOG_LY, (progress - 256*256 / 2).toShort()),
                        Event(ANALOG_LX, 0)
                    ))
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }
}