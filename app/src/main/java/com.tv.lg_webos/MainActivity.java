package com.tv.lg_webos;

import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class MainActivity extends AppCompatActivity {

    // Fixes "cannot find symbol" error
    public static boolean m_debugMode = true;

    private LGTV mTv;
    private ViewFlipper viewFlipper;
    private EditText ipInput;
    
    // Touchpad variables
    private float lastX, lastY, scrollX, scrollY;
    private boolean isMoving = false;

    // Command Index for LG TV
    public enum KEY_INDEX {
        TV, YOUTUBE, NETFLIX, AMAZON, INTERNET,
        ON, OFF, SOURCE, MUTE, VOLUME_INCREASE, VOLUME_DECREASE,
        CHANNEL_INCREASE, CHANNEL_DECREASE, PLAY, PAUSE, STOP, REWIND, FORWARD,
        HOME, BACK, UP, DOWN, LEFT, RIGHT, ENTER, EXIT
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // --- SAFE LAYOUT LOADING ---
        try {
            // Fullscreen mode
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );
            setContentView(R.layout.activity_main);
        } catch (Exception e) {
            // If XML fails, show error instead of crashing
            Toast.makeText(this, "Layout Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return; 
        }

        // Initialize Backend
        mTv = new LGTV(this);
        mTv.loadMainPreferences();

        setupUI();
    }

    private void setupUI() {
        // --- SAFE UI SETUP (Checks if views exist first) ---
        
        viewFlipper = findViewById(R.id.view_flipper);
        ipInput = findViewById(R.id.ip_input);

        // Only set text if ipInput was found
        if (ipInput != null && mTv.getMyIP() != null) {
            ipInput.setText(mTv.getMyIP());
        }

        // Connect Button Logic
        View btnLink = findViewById(R.id.btn_link);
        if (btnLink != null) {
            btnLink.setOnClickListener(v -> {
                animateButton(v);
                if (ipInput != null) {
                    String ip = ipInput.getText().toString();
                    mTv.setMyIP(ip);
                    mTv.saveIPPreference();
                    mTv.TV_Pairing();
                    Toast.makeText(this, "Connecting to " + ip + "...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // --- Tabs Setup ---
        setupTab(R.id.tab_nav, 0);
        setupTab(R.id.tab_touch, 1);
        setupTab(R.id.tab_type, 2);
        
        // Default Tab Selection
        View tabNav = findViewById(R.id.tab_nav);
        if (tabNav != null) tabNav.setSelected(true);

        // --- Navigation Buttons ---
        setupButton(R.id.btn_ok, "ENTER", KEY_INDEX.ENTER);
        setupButton(R.id.btn_up, "UP", KEY_INDEX.UP);
        setupButton(R.id.btn_down, "DOWN", KEY_INDEX.DOWN);
        setupButton(R.id.btn_left, "LEFT", KEY_INDEX.LEFT);
        setupButton(R.id.btn_right, "RIGHT", KEY_INDEX.RIGHT);

        // --- Media & Power ---
        setupButton(R.id.btn_vol_up, "VOL+", KEY_INDEX.VOLUME_INCREASE);
        setupButton(R.id.btn_vol_down, "VOL-", KEY_INDEX.VOLUME_DECREASE);
        setupButton(R.id.btn_ch_up, "CH+", KEY_INDEX.CHANNEL_INCREASE);
        setupButton(R.id.btn_ch_down, "CH-", KEY_INDEX.CHANNEL_DECREASE);
        setupButton(R.id.btn_mute, "MUTE", KEY_INDEX.MUTE);
        setupButton(R.id.btn_power, "POWER", KEY_INDEX.ON);

        // --- Controls ---
        setupButton(R.id.btn_home, "HOME", KEY_INDEX.HOME);
        setupButton(R.id.btn_back, "BACK", KEY_INDEX.BACK);
        setupButton(R.id.btn_netflix, "NETFLIX", KEY_INDEX.NETFLIX);
        setupButton(R.id.btn_youtube, "YOUTUBE", KEY_INDEX.YOUTUBE);

        // --- Touchpad ---
        setupTouchpad();
    }

    private void setupTab(int id, int childIndex) {
        View tab = findViewById(id);
        if (tab == null) return; // Skip if missing

        tab.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetTab(R.id.tab_nav);
            resetTab(R.id.tab_touch);
            resetTab(R.id.tab_type);
            v.setSelected(true);
            if (viewFlipper != null) viewFlipper.setDisplayedChild(childIndex);
        });
    }

    private void resetTab(int id) {
        View v = findViewById(id);
        if (v != null) v.setSelected(false);
    }

    private void setupButton(int id, String logName, KEY_INDEX key) {
        View btn = findViewById(id);
        if (btn == null) return; // Skip if missing (Prevents Crash)

        btn.setOnClickListener(v -> {
            animateButton(v);
            mTv.send_key(logName, key);
        });
    }

    private void animateButton(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
            .start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchpad() {
        View pad = findViewById(R.id.touch_pad_surface);
        if (pad == null) return; // Skip if missing
        
        pad.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX(); lastY = event.getY(); isMoving = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) { scrollX = event.getX(0); scrollY = event.getY(0); }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    isMoving = true;
                    if (event.getPointerCount() == 1) {
                        mTv.movePointer((int)(event.getX() - lastX), (int)(event.getY() - lastY));
                        lastX = event.getX(); lastY = event.getY();
                    } else if (event.getPointerCount() == 2) {
                        mTv.scroll((int)((event.getX(0) - scrollX) / 2), (int)((event.getY(0) - scrollY) / 2));
                        scrollX = event.getX(0); scrollY = event.getY(0);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isMoving) {
                        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        mTv.clickPointer();
                        animateButton(v);
                    }
                    return true;
            }
            return false;
        });
    }
}