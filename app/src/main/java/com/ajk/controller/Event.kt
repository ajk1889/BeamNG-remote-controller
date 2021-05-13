package com.ajk.controller

data class Event(val event: String, val value: Short) {
    val eventCode: Short
        get() = eventCodeMap[event]!!
}