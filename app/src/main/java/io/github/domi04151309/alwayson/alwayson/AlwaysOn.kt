package io.github.domi04151309.alwayson.alwayson

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.drawable.Icon
import android.graphics.drawable.TransitionDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.domi04151309.alwayson.OffActivity
import io.github.domi04151309.alwayson.R
import io.github.domi04151309.alwayson.adapters.NotificationGridAdapter
import io.github.domi04151309.alwayson.helpers.Date
import io.github.domi04151309.alwayson.objects.Global
import io.github.domi04151309.alwayson.objects.Root
import io.github.domi04151309.alwayson.receivers.CombinedServiceReceiver

class AlwaysOn : OffActivity(), SensorEventListener {

    companion object {
        private const val CLOCK_DELAY: Long = 60000
        private const val SENSOR_DELAY_SLOW: Int = 1000000
    }

    private var localManager: LocalBroadcastManager? = null
    private var content: View? = null
    private var rootMode: Boolean = false
    private var servicesRunning: Boolean = false
    private var screenSize: Float = 0F

    //Threads
    private var aoEdgeGlowThread: Thread = Thread()
    private var animationThread: Thread = Thread()

    //Settings
    private var aoClock: Boolean = true
    private var aoDate: Boolean = true
    private var aoBatteryIcn: Boolean = true
    private var aoBattery: Boolean = true
    private var aoNotifications: Boolean = true
    private var aoNotificationIcons: Boolean = false
    private var aoEdgeGlow: Boolean = true
    private var aoPocketMode: Boolean = false
    private var aoDND: Boolean = false
    private var aoHeadsUp: Boolean = false

    //Time
    private var clockTxt: TextView? = null
    private var clockFormat: SimpleDateFormat = SimpleDateFormat()
    private val clockHandler = Handler()
    private val clockRunnable = object : Runnable {
        override fun run() {
            clockTxt!!.text = clockFormat.format(Calendar.getInstance())
            clockHandler.postDelayed(this, CLOCK_DELAY)
        }
    }

    //Date
    private var dateTxt: TextView? = null
    private var dateFormat: SimpleDateFormat = SimpleDateFormat()
    private val mDateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            dateTxt!!.text = dateFormat.format(Calendar.getInstance())
        }
    }
    private val dateFilter = IntentFilter()

    //Battery
    private var batteryIcn: ImageView? = null
    private var batteryTxt: TextView? = null
    private var batteryFilter = IntentFilter()
    private val mBatInfoReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                    if (level <= rulesBattery) {
                        stopAndOff()
                        return
                    }

                    if (aoBattery) batteryTxt!!.text = resources.getString(R.string.percent, level)
                    if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
                        if (aoBatteryIcn) when {
                            level >= 100 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_100_charging)
                            level >= 90 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_90_charging)
                            level >= 80 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_80_charging)
                            level >= 60 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_60_charging)
                            level >= 50 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_50_charging)
                            level >= 30 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_30_charging)
                            level >= 20 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20_charging)
                            level >= 0 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_0_charging)
                            else -> batteryIcn!!.setImageResource(R.drawable.ic_battery_unknown_charging)
                        }
                    } else {
                        if (aoBatteryIcn) when {
                            level >= 100 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_100)
                            level >= 90 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_90)
                            level >= 80 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_80)
                            level >= 60 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_60)
                            level >= 50 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_50)
                            level >= 30 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_30)
                            level >= 20 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20)
                            level >= 10 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20_orange)
                            level >= 0 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_0)
                            else -> batteryIcn!!.setImageResource(R.drawable.ic_battery_unknown)
                        }
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    if (rulesChargingState == "discharging") stopAndOff()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    if (rulesChargingState == "charging") stopAndOff()
                }
            }
        }
    }

    //Notifications
    private var transition: TransitionDrawable? = null
    private var notificationAvailable: Boolean = false
    private var notifications: TextView? = null
    private var notificationGrid: RecyclerView? = null
    private val mNotificationReceiver = object : BroadcastReceiver() {

        override fun onReceive(c: Context, intent: Intent) {
            val count = intent.getIntExtra("count", 0)
            if (aoNotifications) {
                if (count == 0)
                    notifications!!.text = ""
                else
                    notifications!!.text = count.toString()
            }

            if (aoNotificationIcons) {
                val itemArray: java.util.ArrayList<Icon> = intent.getParcelableArrayListExtra("icons") ?: arrayListOf()
                notificationGrid!!.adapter = NotificationGridAdapter(itemArray)
            }

            if (aoEdgeGlow) {
                notificationAvailable = count != 0
            }
        }
    }

    //Battery saver
    private var powerSaving: Boolean = false
    private var userPowerSaving: Boolean = false

    //Proximity
    private var mSensorManager: SensorManager? = null
    private var mProximity: Sensor? = null

    //DND
    private var mNotificationManager: NotificationManager? = null
    private var notificationAccess: Boolean = false
    private var userDND: Int = 0

    //Stop
    private val mStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            finish()
        }
    }

    //Rules
    private var rulesChargingState: String = ""
    private var rulesBattery: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Check prefs
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        rootMode = prefs.getBoolean("root_mode", false)
        powerSaving = prefs.getBoolean("ao_power_saving", false)
        val userTheme = prefs.getString("ao_style", "google")
        aoClock = prefs.getBoolean("ao_clock", true)
        aoDate = prefs.getBoolean("ao_date", true)
        aoBatteryIcn = prefs.getBoolean("ao_batteryIcn", true)
        aoBattery = prefs.getBoolean("ao_battery", true)
        aoNotifications = prefs.getBoolean("ao_notifications", false)
        aoNotificationIcons = prefs.getBoolean("ao_notification_icons", true)
        aoEdgeGlow = prefs.getBoolean("ao_edgeGlow", false)
        aoPocketMode = prefs.getBoolean("ao_pocket_mode", false)
        aoDND = prefs.getBoolean("ao_dnd", false)
        aoHeadsUp = prefs.getBoolean("heads_up", false)
        val clock = prefs.getBoolean("hour", false)
        val amPm = prefs.getBoolean("am_pm", false)
        val aoForceBrightness = prefs.getBoolean("ao_force_brightness", false)
        val aoDoubleTapDisabled = prefs.getBoolean("ao_double_tap_disabled", false)

        //Cutouts
        if (prefs.getBoolean("hide_display_cutouts", false))
            setTheme(R.style.CutoutHide)
        else
            setTheme(R.style.CutoutIgnore)

        when (userTheme) {
            "google" -> setContentView(R.layout.activity_ao_google)
            "samsung" -> setContentView(R.layout.activity_ao_samsung)
            "samsung2" -> setContentView(R.layout.activity_ao_samsung_2)
            "oneplus" -> setContentView(R.layout.activity_ao_oneplus)
        }

        //Watch face
        clockTxt = findViewById(R.id.clockTxt)
        dateTxt = findViewById(R.id.dateTxt)
        batteryIcn = findViewById(R.id.batteryIcn)
        batteryTxt = findViewById(R.id.batteryTxt)
        notifications = findViewById(R.id.notifications)
        notificationGrid = findViewById(R.id.notifications_grid)

        if (!aoClock) clockTxt!!.visibility = View.GONE
        if (!aoDate) dateTxt!!.visibility = View.GONE
        if (!aoBatteryIcn) batteryIcn!!.visibility = View.GONE
        if (!aoBattery) batteryTxt!!.visibility = View.GONE
        if (!aoNotifications) notifications!!.visibility = View.GONE
        if (!aoNotificationIcons) notificationGrid!!.visibility = View.GONE

        clockFormat = SimpleDateFormat(
                if (userTheme == "samsung" || userTheme == "oneplus") {
                    if (clock) {
                        if (amPm) "hh\nmm\na"
                        else "hh\nmm"
                    } else "HH\nmm"
                } else {
                    if (clock) {
                        if (amPm) "h:mm a"
                        else "h:mm"
                    } else "H:mm"
                }
        )
        dateFormat = SimpleDateFormat(
                if (userTheme == "samsung2") {
                    "EEE, MMMM d"
                } else {
                    "EEE, MMM d"
                }
        )

        //Brightness
        if (aoForceBrightness) {
            val brightness: Float = prefs.getInt("ao_force_brightness_value", 50) / 255.toFloat()
            val lp = window.attributes
            lp.screenBrightness = brightness
            window.attributes = lp
        }

        //Variables
        localManager = LocalBroadcastManager.getInstance(this)
        val frame = findViewById<View>(R.id.frame)
        content = findViewById(R.id.fullscreen_content)
        userPowerSaving = (getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode

        //Show on lock screen
        Handler().postDelayed({
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }, 300L)

        //Hide UI
        hideUI()
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                hideUI()
        }

        //Time
        if (aoClock) clockTxt!!.text = clockFormat.format(Calendar.getInstance())

        //Date
        if (aoDate) {
            dateTxt!!.text = dateFormat.format(Calendar.getInstance())
            dateFilter.addAction(Intent.ACTION_DATE_CHANGED)
            dateFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        //Battery
        batteryFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
        batteryFilter.addAction(Intent.ACTION_POWER_CONNECTED)
        batteryFilter.addAction(Intent.ACTION_POWER_DISCONNECTED)

        //Notifications
        if(aoNotificationIcons) {
            val layoutManager = LinearLayoutManager(this)
            layoutManager.orientation = LinearLayoutManager.HORIZONTAL
            notificationGrid!!.layoutManager = layoutManager
        }

        //Proximity
        if (aoPocketMode) {
            mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            mProximity = mSensorManager!!.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            mSensorManager!!.registerListener(this, mProximity, SENSOR_DELAY_SLOW, SENSOR_DELAY_SLOW)
        }

        //DND
        if (aoDND) {
            mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationAccess = mNotificationManager!!.isNotificationPolicyAccessGranted
            if(notificationAccess) userDND = mNotificationManager!!.currentInterruptionFilter
        }

        //Edge Glow
        if (aoEdgeGlow) {
            val transitionTime = prefs.getInt("ao_glowDuration", 2000)
            if (transitionTime >= 100) {
                frame.background = when (prefs.getString("ao_glowStyle", "all")) {
                    "horizontal" -> ContextCompat.getDrawable(this, R.drawable.edge_glow_horizontal)
                    else -> ContextCompat.getDrawable(this, R.drawable.edge_glow)
                }
                transition = frame.background as TransitionDrawable
                aoEdgeGlowThread = object : Thread() {
                    override fun run() {
                        try {
                            while (!isInterrupted) {
                                if (notificationAvailable) {
                                    runOnUiThread { transition!!.startTransition(transitionTime) }
                                    sleep(transitionTime.toLong())
                                    runOnUiThread { transition!!.reverseTransition(transitionTime) }
                                    sleep(transitionTime.toLong())
                                } else
                                    sleep(1000)
                            }
                        } catch (e: Exception) {
                            Log.e(Global.LOG_TAG, e.toString())
                        }
                    }
                }
                aoEdgeGlowThread.start()
            }
        }

        // Power saving mode
        if (rootMode && powerSaving) {
            Root.shell("settings put global low_power 1")
            Root.shell("dumpsys deviceidle force-idle")
        }

        //Animation
        val animationDuration = 10000L
        val animationScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        val animationDelay = (prefs!!.getInt("ao_animation_delay", 2) * 60000 + animationDuration * animationScale + 1000).toLong()
        animationThread = object : Thread() {
            override fun run() {
                try {
                    while (content!!.height == 0) sleep(10)
                    screenSize = getScreenSize()
                    content!!.animate().translationY(screenSize / 4).duration = 0
                    while (!isInterrupted) {
                        sleep(animationDelay)
                        content!!.animate().translationY(screenSize / 2).duration = animationDuration
                        sleep(animationDelay)
                        content!!.animate().translationY(screenSize / 4).duration = animationDuration
                    }
                } catch (e: Exception) {
                    Log.e(Global.LOG_TAG, e.toString())
                }
            }
        }
        animationThread.start()

        //DoubleTap
        if (!aoDoubleTapDisabled) {
            frame.setOnTouchListener(object : View.OnTouchListener {
                private val gestureDetector = GestureDetector(this@AlwaysOn, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val duration = prefs.getInt("ao_vibration", 64).toLong()
                        if (duration > 0) {
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                vibrator.vibrate(duration)
                            }
                        }
                        finish()
                        return super.onDoubleTap(e)
                    }
                })

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(event)
                    v.performClick()
                    return true
                }
            })
        }

        //Stop
        localManager!!.registerReceiver(mStopReceiver, IntentFilter(Global.REQUEST_STOP))

        //Rules
        rulesChargingState = prefs.getString("rules_charging_state", "always") ?: "always"
        rulesBattery = prefs.getInt("rules_battery_level", 0)

        Handler().postDelayed({
            stopAndOff()
        }, Date(prefs).milliSecsTillEnd())

        val rulesTimeout = prefs.getInt("rules_timeout", 0)
        if (rulesTimeout != 0) {
            Handler().postDelayed({
                stopAndOff()
            }, rulesTimeout * 60000L)
        }
    }

    //Proximity
    override fun onSensorChanged(p0: SensorEvent?) {
        if (p0!!.sensor.type == Sensor.TYPE_PROXIMITY) {
            if (p0.values[0] == p0.sensor.maximumRange) {
                content!!.animate().alpha(1F).duration = 1000L
                startServices()
            } else {
                content!!.animate().alpha(0F).duration = 1000L
                stopServices()
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenSize = getScreenSize()
    }

    override fun onResume() {
        super.onResume()
        hideUI()
        CombinedServiceReceiver.isAlwaysOnRunning = true
        startServices()
    }

    override fun onPause() {
        super.onPause()
        stopServices()
    }

    override fun onDestroy() {
        super.onDestroy()
        CombinedServiceReceiver.isAlwaysOnRunning = false
        if (aoPocketMode) mSensorManager!!.unregisterListener(this)
        if (aoEdgeGlow) aoEdgeGlowThread.interrupt()
        animationThread.interrupt()
        if (rootMode && powerSaving && !userPowerSaving) Root.shell("settings put global low_power 0")
        localManager!!.unregisterReceiver(mStopReceiver)
    }


    private fun hideUI() {
        content!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    private fun getScreenSize(): Float {
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        return  (size.y - content!!.height).toFloat()
    }

    private fun stopAndOff() {
        CombinedServiceReceiver.hasRequestedStop = true
        Global.close(this)
    }

    private fun startServices() {
        if (!servicesRunning) {
            servicesRunning = true

            // Clock Handler
            if (aoClock) clockHandler.postDelayed(clockRunnable, CLOCK_DELAY)

            // Date Receiver
            if (aoDate) registerReceiver(mDateChangedReceiver, dateFilter)

            // Battery Receiver
            registerReceiver(mBatInfoReceiver, batteryFilter)

            // Notification Listener
            if (aoNotifications || aoNotificationIcons || aoEdgeGlow) {
                localManager!!.registerReceiver(mNotificationReceiver, IntentFilter(Global.NOTIFICATIONS))
                localManager!!.sendBroadcast(Intent(Global.REQUEST_NOTIFICATIONS))
            }

            // DND
            if (aoDND && notificationAccess) mNotificationManager!!.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)

            // Heads Up
            if (aoHeadsUp) Root.shell("settings put global heads_up_notifications_enabled 0")
        }
    }

    private fun stopServices() {
        if (servicesRunning) {
            servicesRunning = false

            // Clock Handler
            if (aoClock) clockHandler.removeCallbacksAndMessages(null)

            // Date Receiver
            if (aoDate) unregisterReceiver(mDateChangedReceiver)

            // Battery Receiver
            unregisterReceiver(mBatInfoReceiver)

            // Notification Listener
            if (aoNotifications || aoNotificationIcons || aoEdgeGlow) localManager!!.unregisterReceiver(mNotificationReceiver)

            // DND
            if (aoDND && notificationAccess) mNotificationManager!!.setInterruptionFilter(userDND)

            // Heads Up
            if (aoHeadsUp) Root.shell("settings put global heads_up_notifications_enabled 1")
        }
    }
}
