package com.ajk.controller

const val ANALOG_LX = "ANALOG_LX"
const val ANALOG_LY = "ANALOG_LY"
const val ANALOG_RX = "ANALOG_RX"
const val ANALOG_RY = "ANALOG_RY"
const val L1 = "L1"
const val L2 = "L2"
const val R1 = "R1"
const val R2 = "R2"
const val TRIANGLE = "TRIANGLE"
const val CROSS = "CROSS"
const val SQUARE = "SQUARE"
const val CIRCLE = "CIRCLE"
const val UP = "UP"
const val DOWN = "DOWN"
const val LEFT = "LEFT"
const val RIGHT = "RIGHT"
const val SELECT = "SELECT"
const val START = "START"

val eventCodeMap = mutableMapOf<String, Short>()

const val MIN_TIME_BTW_INPUTS = 20
const val USHORT_MAX_VALUE = 256 * 256
const val MAX_RELEASE_DURATION = 200.0

data class AnalogValues(var x: Short, var y: Short)
