package com.tv.lg_webos;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    public static boolean m_debugMode = true;
    private LGTV mTv;
    private EditText ipInput;
    
    // Variables for Trackpad Math
    private float lastX, lastY;
    private boolean isMoving = false;

    // Command List for LG TV
    public enum KEY_INDEX {
        TV, YOUTUBE, NETFLIX, AMAZON,
        ON, OFF, MUTE, VOLUME_INCREASE, VOLUME_DECREASE,
        CHANNEL_INCREASE, CHANNEL_DECREASE, 
        HOME, BACK, UP, DOWN, LEFT, RIGHT, ENTER, EXIT
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Loads the native XML

        // 1. Initialize Backend
        mTv = new LGTV(this);
        mTv.loadMainPreferences();

        // 2. Setup IP and Connect Button
        ipInput = findViewById(R.id.ip_input);
        if (mTv.getMyIP() != null) ipInput.setText(mTv.getMyIP());

        findViewById(R.id.btn_connect).setOnClickListener(v -> {
            String ip = ipInput.getText().toString();
            mTv.setMyIP(ip);
            mTv.saveIPPreference();
            mTv.TV_Pairing();
            Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();
        });

        // 3. Setup Buttons (Simple Helper Function)
        setupBtn(R.id.btn_vol_up, "VOL_UP", KEY_INDEX.VOLUME_INCREASE);
        setupBtn(R.id.btn_vol_down, "VOL_DOWN", KEY_INDEX.VOLUME_DECREASE);
        setupBtn(R.id.btn_ch_up, "CH_UP", KEY_INDEX.CHANNEL_INCREASE);
        setupBtn(R.id.btn_ch_down, "CH_DOWN", KEY_INDEX.CHANNEL_DECREASE);
        
        setupBtn(R.id.btn_up, "UP", KEY_INDEX.UP);
        setupBtn(R.id.btn_down, "DOWN", KEY_INDEX.DOWN);
        setupBtn(R.id.btn_left, "LEFT", KEY_INDEX.LEFT);
        setupBtn(R.id.btn_right, "RIGHT", KEY_INDEX.RIGHT);
        setupBtn(R.id.btn_enter, "ENTER", KEY_INDEX.ENTER);
        
        setupBtn(R.id.btn_back, "BACK", KEY_INDEX.BACK);
        setupBtn(R.id.btn_home, "HOME", KEY_INDEX.HOME);

        // 4. Setup Native Trackpad
        setupTrackpad();
    }

    // Helper to connect a button to a TV command
    private void setupBtn(int id, String logName, KEY_INDEX key) {
        Button btn = findViewById(id);
        if (btn != null) {
            btn.setOnClickListener(v -> mTv.send_key(logName, key));
        }
    }

    // The Trackpad Logic
    private void setupTrackpad() {
        View trackpad = findViewById(R.id.trackpad_view);
        
        trackpad.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Finger touched screen - save starting position
                        lastX = event.getX();
                        lastY = event.getY();
                        isMoving = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // Finger moved - calculate difference (delta)
                        isMoving = true;
                        int dx = (int) (event.getX() - lastX);
                        int dy = (int) (event.getY() - lastY);
                        
                        // Send move command to TV
                        mTv.movePointer(dx, dy);
                        
                        // Update last position for next movement
                        lastX = event.getX();
                        lastY = event.getY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Finger lifted - if we didn't move much, treat as a CLICK
                        if (!isMoving) {
                            mTv.clickPointer();
                            v.performClick(); // Accessibility helper
                        }
                        return true;
                }
                return false;
            }
        });
    }
} 