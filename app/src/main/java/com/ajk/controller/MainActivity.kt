package com.ajk.controller

import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.URI

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val conn = Connection(URI("ws://192.168.18.63:31415"))
        conn.connect()
        findViewById<TextView>(R.id.upBtn).setOnTouchListener { view, event ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> conn.sendMessage(Event(EventCodes.UP, 1))
                KeyEvent.ACTION_UP -> conn.sendMessage(Event(EventCodes.UP, 0))
            }
            return@setOnTouchListener true
        }
    }
}