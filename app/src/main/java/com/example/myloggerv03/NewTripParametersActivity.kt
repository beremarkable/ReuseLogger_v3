package com.example.myloggerv03

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class NewTripParametersActivity : AppCompatActivity() {

    private lateinit var switchGPS: Switch
    private lateinit var spinnerGPSInterval: Spinner
    private lateinit var switchTemperature: Switch
    private lateinit var spinnerTemperatureSetting: Spinner
    private lateinit var switchShock: Switch
    private lateinit var btnResetDefaults: Button
    private lateinit var btnNext: Button

    // Variables to store user settings
    private var isGPSEnabled: Boolean = true
    private var gpsInterval: String = "Every 10 minutes"
    private var isTemperatureEnabled: Boolean = true
    private var temperatureSetting: String = "Medium"
    private var isShockEnabled: Boolean = true

    private lateinit var usbManager: UsbManager
    private var isUsbDeviceConnected: Boolean = false

    private val ACTION_USB_PERMISSION = "com.example.myloggerv03.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    checkUsbDevice()
                }
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            // Permission granted
                            runOnUiThread {
                                switchTemperature.isEnabled = true
                            }
                        } else {
                            // Permission denied
                            runOnUiThread {
                                Toast.makeText(
                                    context,
                                    "Permission denied for USB device",
                                    Toast.LENGTH_SHORT
                                ).show()
                                switchTemperature.isEnabled = false
                                switchTemperature.isChecked = false
                                isTemperatureEnabled = false
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_trip_parameters)

        switchGPS = findViewById(R.id.switch_gps)
        spinnerGPSInterval = findViewById(R.id.spinner_gps_interval)
        switchTemperature = findViewById(R.id.switch_temperature)
        spinnerTemperatureSetting = findViewById(R.id.spinner_temperature_setting)
        switchShock = findViewById(R.id.switch_shock)
        btnResetDefaults = findViewById(R.id.btn_reset_defaults)
        btnNext = findViewById(R.id.btn_next)

        // Bottom Navigation View
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_new_trip -> {
                    true // Stay on the current screen
                }
                R.id.navigation_saved_trips -> {
                    val intent = Intent(this, SavedTripsActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        // Initialize GPS Interval Spinner
        val intervalOptions = resources.getStringArray(R.array.interval_options)
        val intervalAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalOptions)
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGPSInterval.adapter = intervalAdapter

        // Initialize Temperature Setting Spinner
        val temperatureOptions = resources.getStringArray(R.array.temperature_options)
        val temperatureAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, temperatureOptions)
        temperatureAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTemperatureSetting.adapter = temperatureAdapter

        // Set default selections
        spinnerGPSInterval.setSelection(0)
        spinnerTemperatureSetting.setSelection(1) // Default to "Medium"

        // Event Listeners
        switchGPS.setOnCheckedChangeListener { _, isChecked ->
            isGPSEnabled = isChecked
        }

        spinnerGPSInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                gpsInterval = intervalOptions[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        switchTemperature.setOnCheckedChangeListener { _, isChecked ->
            isTemperatureEnabled = isChecked
        }

        spinnerTemperatureSetting.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    temperatureSetting = temperatureOptions[position]
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Do nothing
                }
            }

        switchShock.setOnCheckedChangeListener { _, isChecked ->
            isShockEnabled = isChecked
        }

        btnResetDefaults.setOnClickListener {
            resetToDefaults()
        }

        btnNext.setOnClickListener {
            // Navigate to Review and Confirm Page
            val intent = Intent(this, ReviewConfirmActivity::class.java)
            // Pass parameters to the next activity
            intent.putExtra("isGPSEnabled", isGPSEnabled)
            intent.putExtra("gpsInterval", gpsInterval)
            intent.putExtra("isTemperatureEnabled", isTemperatureEnabled)
            intent.putExtra("temperatureSetting", temperatureSetting)
            intent.putExtra("isShockEnabled", isShockEnabled)
            startActivity(intent)
        }

        // USB Device Handling
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        registerUsbReceiver()
        checkUsbDevice()
    }

    private fun resetToDefaults() {
        switchGPS.isChecked = true
        spinnerGPSInterval.setSelection(0)
        switchTemperature.isChecked = true
        spinnerTemperatureSetting.setSelection(1) // Default to "Medium"
        switchShock.isChecked = true
        Toast.makeText(this, "Parameters reset to default.", Toast.LENGTH_SHORT).show()
    }

    private fun checkUsbDevice() {
        val deviceList = usbManager.deviceList
        val ourDevice = deviceList.values.find { device ->
            isOurUsbDevice(device)
        }

        isUsbDeviceConnected = ourDevice != null

        if (isUsbDeviceConnected) {
            val device = ourDevice!!
            if (!usbManager.hasPermission(device)) {
                // Request permission
                val permissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE
                )
                usbManager.requestPermission(device, permissionIntent)
            } else {
                // Permission already granted
                runOnUiThread {
                    switchTemperature.isEnabled = true
                }
            }
        } else {
            runOnUiThread {
                switchTemperature.isEnabled = false
                switchTemperature.isChecked = false
                isTemperatureEnabled = false
                Toast.makeText(this, "USB Temperature Logger not connected", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun isOurUsbDevice(device: UsbDevice): Boolean {
        // Replace with your device's vendor ID and product ID
        val vendorId = device.vendorId
        val productId = device.productId

        val OUR_VENDOR_ID = 6790
        val OUR_PRODUCT_ID = 29987 // real is 0x7523

        return vendorId == OUR_VENDOR_ID && productId == OUR_PRODUCT_ID
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}