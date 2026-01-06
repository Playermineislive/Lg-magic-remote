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

    // --- CRITICAL FIX: This variable was missing ---
    public static boolean m_debugMode = true;
    // -----------------------------------------------

    private LGTV mTv;
    private ViewFlipper viewFlipper;
    private EditText ipInput;
    
    // Touchpad Variables
    private float lastX, lastY, scrollX, scrollY;
    private boolean isMoving = false;

    // Command Index mapping
    public enum KEY_INDEX {
        TV, YOUTUBE, NETFLIX, AMAZON, INTERNET,
        ON, OFF, SOURCE, MUTE, VOLUME_INCREASE, VOLUME_DECREASE,
        CHANNEL_INCREASE, CHANNEL_DECREASE, PLAY, PAUSE, STOP, REWIND, FORWARD,
        HOME, BACK, UP, DOWN, LEFT, RIGHT, ENTER, EXIT
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Immersive Fullscreen (Transparent Status Bar)
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        
        setContentView(R.layout.activity_main);

        // Initialize Backend
        mTv = new LGTV(this);
        mTv.loadMainPreferences();

        setupUI();
    }

    private void setupUI() {
        // --- Header & Connection ---
        viewFlipper = findViewById(R.id.view_flipper);
        ipInput = findViewById(R.id.ip_input);

        if (mTv.getMyIP() != null) ipInput.setText(mTv.getMyIP());

        findViewById(R.id.btn_link).setOnClickListener(v -> {
            animateButton(v);
            String ip = ipInput.getText().toString();
            mTv.setMyIP(ip);
            mTv.saveIPPreference();
            mTv.TV_Pairing();
            Toast.makeText(this, "Searching for " + ip + "...", Toast.LENGTH_SHORT).show();
        });

        // --- Tabs (Neon Switcher) ---
        setupTab(R.id.tab_nav, 0);
        setupTab(R.id.tab_touch, 1);
        setupTab(R.id.tab_type, 2);
        
        // Default Tab
        findViewById(R.id.tab_nav).setSelected(true);

        // --- Navigation Keys ---
        setupButton(R.id.btn_ok, "ENTER", KEY_INDEX.ENTER);
        setupButton(R.id.btn_up, "UP", KEY_INDEX.UP);
        setupButton(R.id.btn_down, "DOWN", KEY_INDEX.DOWN);
        setupButton(R.id.btn_left, "LEFT", KEY_INDEX.LEFT);
        setupButton(R.id.btn_right, "RIGHT", KEY_INDEX.RIGHT);

        // --- Media & Volume ---
        setupButton(R.id.btn_vol_up, "VOL+", KEY_INDEX.VOLUME_INCREASE);
        setupButton(R.id.btn_vol_down, "VOL-", KEY_INDEX.VOLUME_DECREASE);
        setupButton(R.id.btn_ch_up, "CH+", KEY_INDEX.CHANNEL_INCREASE);
        setupButton(R.id.btn_ch_down, "CH-", KEY_INDEX.CHANNEL_DECREASE);
        setupButton(R.id.btn_mute, "MUTE", KEY_INDEX.MUTE);
        setupButton(R.id.btn_home, "HOME", KEY_INDEX.HOME);
        setupButton(R.id.btn_back, "BACK", KEY_INDEX.BACK);
        setupButton(R.id.btn_power, "POWER", KEY_INDEX.ON);

        // --- App Shortcuts ---
        setupButton(R.id.btn_netflix, "NETFLIX", KEY_INDEX.NETFLIX);
        setupButton(R.id.btn_youtube, "YOUTUBE", KEY_INDEX.YOUTUBE);

        // --- Touchpad Logic ---
        setupTouchpad();
    }

    private void setupTab(int id, int childIndex) {
        View tab = findViewById(id);
        tab.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            
            // Reset all tabs
            findViewById(R.id.tab_nav).setSelected(false);
            findViewById(R.id.tab_touch).setSelected(false);
            findViewById(R.id.tab_type).setSelected(false);
            
            // Select new
            v.setSelected(true);
            viewFlipper.setDisplayedChild(childIndex);
        });
    }

    private void setupButton(int id, String logName, KEY_INDEX key) {
        View btn = findViewById(id);
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            animateButton(v);
            mTv.send_key(logName, key);
        });
    }

    // --- "World Best" Animation Engine ---
    private void animateButton(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        v.animate()
            .scaleX(0.9f).scaleY(0.9f)
            .setDuration(50)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .withEndAction(() -> 
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            )
            .start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchpad() {
        View pad = findViewById(R.id.touch_pad_surface);
        if (pad == null) return;
        
        pad.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    isMoving = false;
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        scrollX = event.getX(0);
                        scrollY = event.getY(0);
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    isMoving = true;
                    if (event.getPointerCount() == 1) {
                        int dx = (int)(event.getX() - lastX);
                        int dy = (int)(event.getY() - lastY);
                        mTv.movePointer(dx, dy);
                        lastX = event.getX();
                        lastY = event.getY();
                    } else if (event.getPointerCount() == 2) {
                        int dx = (int)((event.getX(0) - scrollX) / 2);
                        int dy = (int)((event.getY(0) - scrollY) / 2);
                        mTv.scroll(dx, dy);
                        scrollX = event.getX(0);
                        scrollY = event.getY(0);
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
