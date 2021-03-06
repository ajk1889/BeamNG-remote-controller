package com.ajk.controller

import android.widget.SeekBar
import kotlinx.coroutines.*

class SeekChangeListener(
    private val connection: Connection,
    private val analogValues: AnalogValues,
    private val direction: Char,
    private var position: Char,
    private val autoRelease: Boolean
) : SeekBar.OnSeekBarChangeListener {
    private var lastEventSentTimestamp = 0L
    private var releasedTime = 0L
    private var progressWhileReleasing = 0
    private var slowlyReleasingJob: Job? = null
    private val ioScope = CoroutineScope(Dispatchers.Main)

    private fun sendRequestConditionally(previousTime: Long, events: List<Event>): Long {
        val now = now()
        if (now - previousTime > MIN_TIME_BTW_INPUTS) {
            connection.sendMessage(events)
            return now
        }
        return previousTime
    }

    override fun onProgressChanged(
        seekBar: SeekBar?,
        progress: Int,
        fromUser: Boolean
    ) {
        val value = (progress - 50) / 50.0 * Short.MAX_VALUE
        if (direction == 'x')
            analogValues.x = value.toInt().toShort()
        else analogValues.y = value.toInt().toShort()
        val data = if (position == 'l')
            listOf(Event(ANALOG_LY, analogValues.y), Event(ANALOG_LX, analogValues.x))
        else listOf(Event(ANALOG_RY, analogValues.y), Event(ANALOG_RX, analogValues.x))
        lastEventSentTimestamp = if (!fromUser) {
            connection.sendMessage(data)
            now()
        } else sendRequestConditionally(lastEventSentTimestamp, data)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        slowlyReleasingJob?.cancel()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        if (!autoRelease) return
        progressWhileReleasing = seekBar!!.progress
        releasedTime = now()
        val actualReleaseDuration = MAX_RELEASE_DURATION * progressWhileReleasing / 100.0
        slowlyReleasingJob = ioScope.launch {
            try {
                var remainingTime = actualReleaseDuration - (now() - releasedTime)
                while (remainingTime > 0) {
                    seekBar.progress = (progressWhileReleasing * remainingTime / actualReleaseDuration).toInt()
                    delay(MIN_TIME_BTW_INPUTS.toLong())
                    remainingTime = actualReleaseDuration - (now() - releasedTime)
                }
                seekBar.progress = 0
            } catch (exception: CancellationException){
            }
            slowlyReleasingJob = null
        }
    }
    private fun now() = System.currentTimeMillis()
}