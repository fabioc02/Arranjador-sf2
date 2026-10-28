package com.example

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrashTest {
    @Test
    fun testStartup() {
        try {
            val activity = Robolectric.setupActivity(MainActivity::class.java)
            println("Activity started successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
