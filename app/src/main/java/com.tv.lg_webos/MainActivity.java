
package com.tv.lg_webos;

import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ViewFlipper;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private LGTV m_tv;
    private ViewFlipper viewFlipper;
    private EditText ipInput;
    
    // Touchpad Variables
    private float lastX, lastY, scrollX, scrollY;
    private boolean isMoving = false;

    // Command Index for LGTV.java
    public enum KEY_INDEX {
        TV, YOUTUBE, NETFLIX, AMAZON, INTERNET,
        ON, OFF, SOURCE, MUTE, VOLUME_INCREASE, VOLUME_DECREASE,
        CHANNEL_INCREASE, CHANNEL_DECREASE, PLAY, PAUSE, STOP, REWIND, FORWARD,
        HOME, BACK, UP, DOWN, LEFT, RIGHT, ENTER, EXIT
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Loads your XML layout
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        m_tv = new LGTV(this);
        m_tv.loadMainPreferences();

        setupUI();
    }

    private void setupUI() {
        // 1. Splash Screen Auto-Hide
        View splash = findViewById(R.id.splash_screen);
        if (splash != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> 
                splash.animate().alpha(0f).withEndAction(() -> splash.setVisibility(View.GONE)), 2000);
        }

        // 2. Header & Tabs
        viewFlipper = findViewById(R.id.view_flipper);
        ipInput = findViewById(R.id.ip_input);
        if (m_tv.getMyIP() != null) ipInput.setText(m_tv.getMyIP());

        findViewById(R.id.btn_link).setOnClickListener(v -> {
            m_tv.setMyIP(ipInput.getText().toString());
            m_tv.saveIPPreference();
            m_tv.TV_Pairing();
            Toast.makeText(this, "Linking...", Toast.LENGTH_SHORT).show();
        });

        setupTabs();
        setupButtons();
        setupTouchpad();
    }

    private void setupTabs() {
        View t1 = findViewById(R.id.tab_nav), t2 = findViewById(R.id.tab_touch), t3 = findViewById(R.id.tab_type);
        if(t1==null) return; 

        View.OnClickListener l = v -> {
            t1.setSelected(false); t2.setSelected(false); t3.setSelected(false);
            v.setSelected(true);
            if (v == t1) viewFlipper.setDisplayedChild(0);
            if (v == t2) viewFlipper.setDisplayedChild(1);
            if (v == t3) viewFlipper.setDisplayedChild(2);
        };
        t1.setOnClickListener(l); t2.setOnClickListener(l); t3.setOnClickListener(l);
        t1.setSelected(true);
    }

    private void setupButtons() {
        // D-Pad
        findViewById(R.id.btn_ok).setOnClickListener(v -> m_tv.send_key("ENTER", KEY_INDEX.ENTER));
        findViewById(R.id.btn_up).setOnClickListener(v -> m_tv.send_key("UP", KEY_INDEX.UP));
        findViewById(R.id.btn_down).setOnClickListener(v -> m_tv.send_key("DOWN", KEY_INDEX.DOWN));
        findViewById(R.id.btn_left).setOnClickListener(v -> m_tv.send_key("LEFT", KEY_INDEX.LEFT));
        findViewById(R.id.btn_right).setOnClickListener(v -> m_tv.send_key("RIGHT", KEY_INDEX.RIGHT));
        
        // Vol/Ch
        findViewById(R.id.btn_vol_up).setOnClickListener(v -> m_tv.send_key("VOLUP", KEY_INDEX.VOLUME_INCREASE));
        findViewById(R.id.btn_vol_down).setOnClickListener(v -> m_tv.send_key("VOLDOWN", KEY_INDEX.VOLUME_DECREASE));
        findViewById(R.id.btn_ch_up).setOnClickListener(v -> m_tv.send_key("CHUP", KEY_INDEX.CHANNEL_INCREASE));
        findViewById(R.id.btn_ch_down).setOnClickListener(v -> m_tv.send_key("CHDOWN", KEY_INDEX.CHANNEL_DECREASE));
        
        // Apps & Dock
        findViewById(R.id.btn_netflix).setOnClickListener(v -> m_tv.send_key("NETFLIX", KEY_INDEX.NETFLIX));
        findViewById(R.id.btn_youtube).setOnClickListener(v -> m_tv.send_key("YOUTUBE", KEY_INDEX.YOUTUBE));
        findViewById(R.id.btn_power).setOnClickListener(v -> m_tv.send_key("POWER", KEY_INDEX.ON));
        findViewById(R.id.btn_home).setOnClickListener(v -> m_tv.send_key("HOME", KEY_INDEX.HOME));
        findViewById(R.id.btn_back).setOnClickListener(v -> m_tv.send_key("BACK", KEY_INDEX.BACK));
    }

    private void setupTouchpad() {
        View pad = findViewById(R.id.touch_pad_surface);
        if (pad == null) return;
        pad.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: lastX = event.getX(); lastY = event.getY(); isMoving = false; return true;
                case MotionEvent.ACTION_POINTER_DOWN: if(event.getPointerCount()==2) { scrollX=event.getX(0); scrollY=event.getY(0); } return true;
                case MotionEvent.ACTION_MOVE:
                    isMoving = true;
                    if (event.getPointerCount() == 1) {
                        m_tv.movePointer((int)(event.getX()-lastX), (int)(event.getY()-lastY));
                        lastX=event.getX(); lastY=event.getY();
                    } else if (event.getPointerCount() == 2) {
                        m_tv.scroll((int)((event.getX(0)-scrollX)/2), (int)((event.getY(0)-scrollY)/2));
                        scrollX=event.getX(0); scrollY=event.getY(0);
                    }
                    return true;
                case MotionEvent.ACTION_UP: if(!isMoving) { m_tv.clickPointer(); v.performClick(); } return true;
            }
            return false;
        });
    }
}
