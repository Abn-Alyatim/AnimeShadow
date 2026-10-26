package com.animeshadow.app.testutil

import com.animeshadow.app.core.Clock

class FakeClock(var time: Long = Fixtures.NOW) : Clock {
    override fun now(): Long = time
    fun advance(ms: Long) { time += ms }
}
