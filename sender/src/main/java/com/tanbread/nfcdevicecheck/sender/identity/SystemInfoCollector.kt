package com.tanbread.nfcdevicecheck.sender.identity

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle

/**
 * Scrapes every stable system/hardware fact this phone will give us without
 * special permissions. Raw values stay on-device; only their SHA-256 hashes
 * ever leave (see [hashBundle]).
 *
 * Volatile values (battery, uptime, IPs, MACs) and identifiers that don't
 * survive re-signing are deliberately excluded. The user-settable device name
 * is excluded so renaming the phone can't break a match.
 */
object SystemInfoCollector {

    fun hashBundle(context: Context): HashBundle = HashBundle.fromRaw(collect(context))

    fun collect(context: Context): LinkedHashMap<String, String> {
        val info = LinkedHashMap<String, String>()

        fun add(key: String, value: String?) {
            val trimmed = value?.trim()
            if (!trimmed.isNullOrEmpty()) info[key] = trimmed
        }

        add("Manufacturer", Build.MANUFACTURER)
        add("Model", Build.MODEL)
        add("Brand", Build.BRAND)
        add("Device", Build.DEVICE)
        add("Product", Build.PRODUCT)
        add("Board", Build.BOARD)
        add("Hardware", Build.HARDWARE)
        add("Bootloader", Build.BOOTLOADER)
        add("Build ID", Build.ID)
        add("Build number", Build.DISPLAY)
        add("Build fingerprint", Build.FINGERPRINT)
        add("Build type", Build.TYPE)
        add("Build timestamp", Build.TIME.toString())
        add("Baseband version", Build.getRadioVersion())
        add("Android version", Build.VERSION.RELEASE)
        add("API level", Build.VERSION.SDK_INT.toString())
        add("Kernel version", System.getProperty("os.version"))
        add("CPU ABIs", Build.SUPPORTED_ABIS.joinToString(","))
        add("CPU cores", Runtime.getRuntime().availableProcessors().toString())
        add("Total RAM", totalRam(context))
        add("Display resolution", displayResolution(context))
        add("Display density", context.resources.displayMetrics.densityDpi.toString())
        add("Total storage", totalStorage())
        val (gpuRenderer, gpuVersion) = gpuStrings()
        add("GPU renderer", gpuRenderer)
        add("GPU version", gpuVersion)
        add("Sensors", sensorList(context))
        add("Cameras", cameraList(context))
        add("System features", systemFeatures(context))

        return info
    }

    private fun totalRam(context: Context): String? = try {
        val manager = context.getSystemService(ActivityManager::class.java)
        if (manager == null) {
            null
        } else {
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            memory.totalMem.toString()
        }
    } catch (e: Exception) {
        null
    }

    private fun displayResolution(context: Context): String? = try {
        val bounds = context.getSystemService(WindowManager::class.java)
            ?.maximumWindowMetrics?.bounds
        bounds?.let { "${it.width()}x${it.height()}" }
    } catch (e: Exception) {
        null
    }

    private fun totalStorage(): String? = try {
        StatFs(Environment.getDataDirectory().path).totalBytes.toString()
    } catch (e: Exception) {
        null
    }

    private fun gpuStrings(): Pair<String?, String?> {
        return try {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display === EGL14.EGL_NO_DISPLAY) return null to null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null to null
        try {
            val configAttr = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val chosen = EGL14.eglChooseConfig(
                display, configAttr, 0, configs, 0, 1, numConfigs, 0,
            )
            if (!chosen || numConfigs[0] == 0) return null to null
            val config = configs[0] ?: return null to null
            val context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            if (context === EGL14.EGL_NO_CONTEXT) return null to null
            val surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            if (surface === EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(display, context)
                return null to null
            }
            try {
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    return null to null
                }
                GLES20.glGetString(GLES20.GL_RENDERER) to
                    GLES20.glGetString(GLES20.GL_VERSION)
            } finally {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    } catch (e: Exception) {
        null to null
    }
    }

    private fun sensorList(context: Context): String? = try {
        val sensors = context.getSystemService(SensorManager::class.java)
            ?.getSensorList(Sensor.TYPE_ALL).orEmpty()
        if (sensors.isEmpty()) {
            null
        } else {
            sensors
                .sortedWith(compareBy({ it.type }, { it.name }, { it.vendor }))
                .joinToString(";") { "${it.type}|${it.name}|${it.vendor}|${it.version}" }
        }
    } catch (e: Exception) {
        null
    }

    private fun cameraList(context: Context): String? = try {
        val manager = context.getSystemService(CameraManager::class.java)
        if (manager == null) {
            null
        } else {
            manager.cameraIdList.sorted().joinToString(";") { id ->
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: -1
                val level = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString(",") ?: ""
                "$id:facing=$facing:hw=$level:focal=$focal"
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun systemFeatures(context: Context): String? = try {
        context.packageManager.systemAvailableFeatures
            .mapNotNull { it.name }
            .sorted()
            .joinToString(",")
            .takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}
