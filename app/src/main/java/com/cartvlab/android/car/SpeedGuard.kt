package com.cartvlab.android.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import kotlin.math.abs

/** Why video is (or is not) hidden on the car screen. */
enum class LockState { CLEAR, MOVING, NEED_PERMISSION, LOCATION_OFF }

/**
 * Pure decision logic of the driving lock, kept free of Android types so it can be unit tested.
 *
 * - Video is hidden as soon as the most recent speed reading is above [LIMIT_KMH].
 * - It comes back only after readings stay at or below the limit for [RELEASE_MS].
 * - The latest reading is kept even when it gets old (e.g. GPS lost in a tunnel), so losing the
 *   signal while driving fast does not bring the video back.
 * - Without location permission, or with location turned off, video stays hidden (fail closed).
 * - Before the very first reading arrives (e.g. parked underground) video is allowed.
 */
class SpeedLockPolicy {
    /** Time of the last reading above the limit; null = never. */
    private var lastFastAt: Long? = null

    fun evaluate(nowMs: Long, hasPermission: Boolean, locationOn: Boolean, latestKmh: Float?): LockState {
        if (!hasPermission) return LockState.NEED_PERMISSION
        if (!locationOn) return LockState.LOCATION_OFF
        if (latestKmh != null && latestKmh > LIMIT_KMH) lastFastAt = nowMs
        val fastAt = lastFastAt
        return if (fastAt != null && nowMs - fastAt < RELEASE_MS) LockState.MOVING else LockState.CLEAR
    }

    fun reset() { lastFastAt = null }

    companion object {
        const val LIMIT_KMH = 30f
        const val RELEASE_MS = 10_000L
    }
}

/**
 * Measures speed with the phone's GPS (and the car's speed sensor when Android Auto provides it)
 * and reports a [LockState] once per second and on every new reading.
 */
class SpeedGuard(private val carContext: CarContext, private val onChange: (LockState) -> Unit) {
    private class Sample(val kmh: Float, val atMs: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val policy = SpeedLockPolicy()
    private val locationManager = carContext.getSystemService(LocationManager::class.java)
    private var gpsListener: LocationListener? = null
    private var carListener: OnCarDataAvailableListener<Speed>? = null
    private var lastFix: Location? = null
    private var gps: Sample? = null
    private var car: Sample? = null
    private var asked = false

    var running = false
        private set
    var state = LockState.CLEAR
        private set
    /** Last measured speed in km/h, or null before the first reading. */
    var speedKmh: Float? = null
        private set

    private val ticker = object : Runnable {
        override fun run() { evaluate(); if (running) handler.postDelayed(this, 1000) }
    }

    fun start() {
        if (running) return
        running = true
        startGps()
        startCarSpeed()
        handler.post(ticker)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        gpsListener?.let { runCatching { locationManager.removeUpdates(it) } }
        gpsListener = null
        carListener?.let { l -> runCatching { carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(l) } }
        carListener = null
        gps = null; car = null; lastFix = null; speedKmh = null
        policy.reset()
        update(LockState.CLEAR)
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun locationOn() = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    private fun askPermissionsOnce() {
        if (asked) return
        asked = true
        runCatching {
            carContext.requestPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, CAR_SPEED)) { granted, _ ->
                if (Manifest.permission.ACCESS_FINE_LOCATION in granted) startGps()
                if (CAR_SPEED in granted) startCarSpeed()
                evaluate()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsListener != null || !running) return
        if (!hasLocationPermission()) { askPermissionsOnce(); return }
        val listener = LocationListener { onLocation(it) }
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            gpsListener = listener
        }.onFailure { Log.w(TAG, "GPS updates unavailable", it) }
    }

    private fun onLocation(location: Location) {
        val kmh = if (location.hasSpeed()) location.speed * 3.6f else lastFix?.let { prev ->
            val seconds = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
            if (seconds > 0.5) (prev.distanceTo(location) / seconds * 3.6).toFloat() else null
        }
        lastFix = location
        if (kmh != null) gps = Sample(kmh, SystemClock.elapsedRealtime())
        evaluate()
    }

    /** The car's own speed sensor, if the car and Android Auto provide it (optional extra source). */
    private fun startCarSpeed() {
        if (carListener != null || !running) return
        val listener = OnCarDataAvailableListener<Speed> { speed ->
            val value = speed.rawSpeedMetersPerSecond
            if (value.status == CarValue.STATUS_SUCCESS) {
                car = Sample(abs(value.value ?: 0f) * 3.6f, SystemClock.elapsedRealtime())
                evaluate()
            }
        }
        try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            carListener = listener
        } catch (e: SecurityException) {
            askPermissionsOnce()
        } catch (e: Exception) {
            Log.w(TAG, "Car speed unavailable", e)
        }
    }

    private fun evaluate() {
        if (!running) return
        if (gpsListener == null && hasLocationPermission()) startGps()
        // Use the highest of the readings taken within 5 s of the newest one.
        val samples = listOfNotNull(gps, car)
        val newest = samples.maxOfOrNull { it.atMs }
        val kmh = newest?.let { n -> samples.filter { n - it.atMs <= 5_000 }.maxOf { it.kmh } }
        speedKmh = kmh
        update(policy.evaluate(SystemClock.elapsedRealtime(), hasLocationPermission(), locationOn(), kmh))
    }

    private fun update(next: LockState) {
        if (next != state) { state = next; onChange(next) }
    }

    companion object {
        const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
        private const val TAG = "SpeedGuard"
    }
}
