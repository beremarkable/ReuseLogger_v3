package com.example.myloggerv03

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.hardware.usb.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.*
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class TemperatureService : Service() {

    private val CHANNEL_ID = "TemperatureServiceChannel"
    private var tripId: Int = 0
    private var temperatureSetting: String = "Medium"
    private var isRunning = false

    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null

    private var serialIoManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val responseBuffer = ByteArrayOutputStream()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Handler and Runnable for scheduling periodic temperature readings
    private val modbusHandler = Handler(Looper.getMainLooper())
    private val modbusRunnable = object : Runnable {
        override fun run() {
            sendModbusRequest()
            modbusHandler.postDelayed(this, getTemperatureInterval())
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when (action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.d(TAG, "Permission granted for device")
                            connectToUsbDevice()
                        } else {
                            Log.e(TAG, "Permission denied for device")
                            stopSelf()
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB device attached")
                    connectToUsbDevice()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d(TAG, "USB device detached")
                    disconnectUsbDevice()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        createNotificationChannel()

        // Register the USB receiver
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        unregisterReceiver(usbReceiver)
        disconnectUsbDevice()
        scope.cancel()
        executor.shutdown()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tripId = intent?.getIntExtra("TRIP_ID", 0) ?: 0
        temperatureSetting = intent?.getStringExtra("temperatureSetting") ?: "Medium"

        startForegroundService()
        connectToUsbDevice()

        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Temperature Service")
            .setContentText("Logging temperature data")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    private fun connectToUsbDevice() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            Log.e(TAG, "No USB serial devices found")
            stopSelf()
            return
        }

        val driver = availableDrivers.find { isOurUsbDevice(it.device) } ?: availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
            Log.d(TAG, "Requesting USB permission")
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device")
            stopSelf()
            return
        }

        usbSerialPort = driver.ports[0]

        try {
            usbSerialPort?.open(connection)
            usbSerialPort?.setParameters(
                9600,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            Log.d(TAG, "Serial port opened")
            startIoManager()
            // Start periodic Modbus requests
            modbusHandler.post(modbusRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up device: ${e.message}")
            stopSelf()
        }
    }

    private fun sendModbusRequest() {
        try {
            val requestWithoutCRC = byteArrayOf(
                0x01.toByte(), // Slave ID
                0x03.toByte(), // Function Code (Read Holding Registers)
                0x00.toByte(), 0x00.toByte(), // Starting Address
                0x00.toByte(), 0x02.toByte()  // Quantity of Registers
            )
            val crc = calculateCRC(requestWithoutCRC)
            val request = requestWithoutCRC + crc

            Log.d(TAG, "Sending request: ${request.joinToString(" ") { String.format("%02X", it) }}")

            usbSerialPort?.write(request, WRITE_TIMEOUT)
        } catch (e: Exception) {
            Log.e(TAG, "Error during communication: ${e.message}")
        }
    }

    private fun startIoManager() {
        usbSerialPort?.let { port ->
            serialIoManager = SerialInputOutputManager(port, listener)
            executor.submit(serialIoManager)
            Log.d(TAG, "SerialIoManager started")
        }
    }

    private fun stopIoManager() {
        serialIoManager?.stop()
        serialIoManager = null
        Log.d(TAG, "SerialIoManager stopped")
    }

    private val listener = object : SerialInputOutputManager.Listener {
        override fun onRunError(e: Exception) {
            Log.e(TAG, "Runner stopped due to error: ${e.message}")
            // Attempt to reconnect
            disconnectUsbDevice()
            connectToUsbDevice()
        }

        override fun onNewData(data: ByteArray) {
            Log.d(TAG, "Received data: ${data.joinToString(" ") { String.format("%02X", it) }}")
            synchronized(responseBuffer) {
                responseBuffer.write(data)
                val responseBytes = responseBuffer.toByteArray()

                if (responseBytes.size >= 9) {
                    val response = responseBytes.copyOfRange(0, 9)

                    val crcReceived = ((response[8].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
                    val crcCalculated = calculateCRC(response.copyOfRange(0, 7))
                    val crcCalculatedValue = ((crcCalculated[1].toInt() and 0xFF) shl 8) or (crcCalculated[0].toInt() and 0xFF)

                    if (crcReceived == crcCalculatedValue) {
                        val temperature = parseTemperatureResponse(response)
                        logTemperatureToDatabase(temperature)
                        Log.d(TAG, "Parsed Temperature: $temperature°C")
                    } else {
                        Log.e(TAG, "CRC mismatch: received=$crcReceived, calculated=$crcCalculatedValue")
                    }

                    responseBuffer.reset()

                    if (responseBytes.size > 9) {
                        responseBuffer.write(responseBytes, 9, responseBytes.size - 9)
                    }
                }
            }
        }
    }

    private fun disconnectUsbDevice() {
        stopIoManager()
        modbusHandler.removeCallbacks(modbusRunnable) // Stop periodic requests
        try {
            usbSerialPort?.close()
        } catch (e: Exception) {
            // Ignore
        }
        usbSerialPort = null
        Log.d(TAG, "USB device disconnected")
    }

    private fun parseTemperatureResponse(response: ByteArray): Float {
        if (response[1] != 0x03.toByte() || response[2] != 0x04.toByte()) {
            Log.e(TAG, "Unexpected function code or byte count")
            return -1f
        }
        val highByte = response[3].toInt() and 0xFF
        val lowByte = response[4].toInt() and 0xFF
        val tempValue = (highByte shl 8) or lowByte
        return tempValue / 10.0f
    }

    private fun calculateCRC(data: ByteArray): ByteArray {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                if ((crc and 0x0001) != 0) {
                    crc = (crc shr 1) xor 0xA001
                } else {
                    crc = crc shr 1
                }
            }
        }
        return byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte()
        )
    }

    private fun logTemperatureToDatabase(temperature: Float) {
        val db = AppDatabase.getDatabase(applicationContext)
        val temperatureDao = db.temperatureDao()

        val temperatureEntity = TemperatureEntity(
            tripId = tripId,
            temperature = temperature,
            timestamp = System.currentTimeMillis()
        )

        scope.launch {
            temperatureDao.insert(temperatureEntity)
            Log.d(TAG, "Logged temperature: $temperature")
        }
    }

    private fun getTemperatureInterval(): Long {
        return when (temperatureSetting) {
            "High" -> 1 * 60 * 1000L  // 1 minute
            "Medium" -> 5 * 60 * 1000L // 5 minutes
            "Low" -> 15 * 60 * 1000L   // 15 minutes
            "Every 10 minutes" -> 10 * 60 * 1000L // 10 minutes
            "Every 30 minutes" -> 30 * 60 * 1000L // 30 minutes
            "Every 1 hour" -> 60 * 60 * 1000L // 60 minutes
            else -> 5 * 60 * 1000L
        }
    }

    private fun isOurUsbDevice(device: UsbDevice): Boolean {
        val OUR_VENDOR_ID = 6790  // Decimal equivalent of 0x1A86
        val OUR_PRODUCT_ID = 29987 // Decimal equivalent of 0x7523

        return device.vendorId == OUR_VENDOR_ID && device.productId == OUR_PRODUCT_ID
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Temperature Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.myloggerv03.USB_PERMISSION"
        private const val READ_BUFFER_SIZE = 1024
        private const val READ_TIMEOUT = 2000
        private const val WRITE_TIMEOUT = 2000
        private const val TAG = "TemperatureService"
    }
}