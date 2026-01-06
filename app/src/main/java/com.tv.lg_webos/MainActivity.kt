package com.tv.lg_webos

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity

// Assuming LGTV.java is still in Java (Interop works perfectly)
class MainActivity : AppCompatActivity() {

    private lateinit var mTv: LGTV
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var ipInput: EditText

    // Touchpad State
    private var lastX = 0f
    private var lastY = 0f
    private var scrollX = 0f
    private var scrollY = 0f
    private var isMoving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Immersive Fullscreen Mode
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContentView(R.layout.activity_main)

        // 2. Initialize Backend
        mTv = LGTV(this)
        mTv.loadMainPreferences()

        setupUI()
    }

    private fun setupUI() {
        // --- Status Bar Spacer ---
        // (Optional: Adjust top padding if content is hidden behind notch)
        
        // --- Header & Connection ---
        viewFlipper = findViewById(R.id.view_flipper)
        ipInput = findViewById(R.id.ip_input)
        
        mTv.myIP?.let { ipInput.setText(it) }

        findViewById<View>(R.id.btn_link).setOnClickListener { view ->
            animateButton(view)
            val ip = ipInput.text.toString()
            mTv.myIP = ip
            mTv.saveIPPreference()
            mTv.TV_Pairing()
            Toast.makeText(this, "Searching for $ip...", Toast.LENGTH_SHORT).show()
        }

        // --- Tabs (Neon Switcher) ---
        setupTab(R.id.tab_nav, 0)
        setupTab(R.id.tab_touch, 1)
        setupTab(R.id.tab_type, 2)
        
        // Set default
        findViewById<View>(R.id.tab_nav).isSelected = true

        // --- Navigation Keys ---
        setupButton(R.id.btn_ok, "ENTER", MainActivity.KEY_INDEX.ENTER)
        setupButton(R.id.btn_up, "UP", MainActivity.KEY_INDEX.UP)
        setupButton(R.id.btn_down, "DOWN", MainActivity.KEY_INDEX.DOWN)
        setupButton(R.id.btn_left, "LEFT", MainActivity.KEY_INDEX.LEFT)
        setupButton(R.id.btn_right, "RIGHT", MainActivity.KEY_INDEX.RIGHT)

        // --- Media & Volume ---
        setupButton(R.id.btn_vol_up, "VOL+", MainActivity.KEY_INDEX.VOLUME_INCREASE)
        setupButton(R.id.btn_vol_down, "VOL-", MainActivity.KEY_INDEX.VOLUME_DECREASE)
        setupButton(R.id.btn_ch_up, "CH+", MainActivity.KEY_INDEX.CHANNEL_INCREASE)
        setupButton(R.id.btn_ch_down, "CH-", MainActivity.KEY_INDEX.CHANNEL_DECREASE)
        setupButton(R.id.btn_mute, "MUTE", MainActivity.KEY_INDEX.MUTE)
        setupButton(R.id.btn_home, "HOME", MainActivity.KEY_INDEX.HOME)
        setupButton(R.id.btn_back, "BACK", MainActivity.KEY_INDEX.BACK)
        setupButton(R.id.btn_power, "POWER", MainActivity.KEY_INDEX.ON)

        // --- App Shortcuts ---
        setupButton(R.id.btn_netflix, "NETFLIX", MainActivity.KEY_INDEX.NETFLIX)
        setupButton(R.id.btn_youtube, "YOUTUBE", MainActivity.KEY_INDEX.YOUTUBE)

        // --- Touchpad Logic ---
        setupTouchpad()
    }

    private fun setupTab(id: Int, childIndex: Int) {
        findViewById<View>(id).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // Reset all tabs
            findViewById<View>(R.id.tab_nav).isSelected = false
            findViewById<View>(R.id.tab_touch).isSelected = false
            findViewById<View>(R.id.tab_type).isSelected = false
            
            // Select new
            v.isSelected = true
            viewFlipper.displayedChild = childIndex
        }
    }

    private fun setupButton(id: Int, logName: String, key: MainActivity.KEY_INDEX) {
        findViewById<View>(id).setOnClickListener { v ->
            animateButton(v)
            mTv.send_key(logName, key)
        }
    }

    // --- "World Best" Animation Engine ---
    private fun animateButton(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        v.animate()
            .scaleX(0.9f).scaleY(0.9f)
            .setDuration(50)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            .start()
    }

    private fun setupTouchpad() {
        val pad = findViewById<View>(R.id.touch_pad_surface) ?: return
        
        pad.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        scrollX = event.getX(0)
                        scrollY = event.getY(0)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    isMoving = true
                    if (event.pointerCount == 1) {
                        val dx = (event.x - lastX).toInt()
                        val dy = (event.y - lastY).toInt()
                        mTv.movePointer(dx, dy)
                        lastX = event.x
                        lastY = event.y
                    } else if (event.pointerCount == 2) {
                        val dx = ((event.getX(0) - scrollX) / 2).toInt()
                        val dy = ((event.getY(0) - scrollY) / 2).toInt()
                        mTv.scroll(dx, dy)
                        scrollX = event.getX(0)
                        scrollY = event.getY(0)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        mTv.clickPointer()
                        animateButton(v)
                    }
                    true
                }
                else -> false
            }
        }
    }
}
