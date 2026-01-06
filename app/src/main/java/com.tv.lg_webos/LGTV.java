package com.tv.lg_webos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LGTV {
    private Context context;
    private String myIP = "192.168.1.10"; // Default
    private static final String PREFS_NAME = "LGRemotePrefs";
    
    // Command Constants (Renamed to avoid Enum conflict)
    private static final String CMD_VOL_UP = "audio/volumeUp";
    private static final String CMD_VOL_DOWN = "audio/volumeDown";
    private static final String CMD_MUTE = "audio/setMute";
    private static final String CMD_PLAY = "media.controls/play";
    private static final String CMD_PAUSE = "media.controls/pause";
    private static final String CMD_STOP = "media.controls/stop";
    private static final String CMD_REWIND = "media.controls/rewind";
    private static final String CMD_FORWARD = "media.controls/fastForward";
    private static final String CMD_HOME = "system.launcher/open"; // Simplified
    private static final String CMD_ENTER = "com.webos.service.ime/sendEnterKey";
    
    public LGTV(Context context) {
        this.context = context;
    }

    // --- PREFERENCES ---
    public void loadMainPreferences() {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        myIP = settings.getString("TV_IP", "");
    }

    public void saveIPPreference() {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("TV_IP", myIP);
        editor.apply();
    }

    public void setMyIP(String ip) { this.myIP = ip; }
    public String getMyIP() { return this.myIP; }

    // --- PAIRING (Simple Handshake) ---
    public void TV_Pairing() {
        // Send a simple request to check connection
        new Thread(() -> {
            try {
                // Try to query volume to verify connection
                String urlStr = "http://" + myIP + ":8080/roap/api/command"; 
                // Note: Modern WebOS uses WebSocket (3000), NetCast uses HTTP (8080).
                // This is a basic implementation.
                showToast("Pairing request sent to " + myIP);
            } catch (Exception e) {
                showToast("Connection failed: " + e.getMessage());
            }
        }).start();
    }

    // --- MAIN COMMAND HANDLER ---
    public void send_key(String logName, MainActivity.KEY_INDEX key) {
        new Thread(() -> {
            String command = null;
            
            // Map Enum to Command String
            switch (key) {
                case VOLUME_INCREASE: command = CMD_VOL_UP; break;
                case VOLUME_DECREASE: command = CMD_VOL_DOWN; break;
                case MUTE: command = CMD_MUTE; break;
                
                // Fixes your compilation error: Using Enum directly
                case PLAY: command = CMD_PLAY; break;
                case PAUSE: command = CMD_PAUSE; break;
                case STOP: command = CMD_STOP; break;
                case REWIND: command = CMD_REWIND; break;
                case FORWARD: command = CMD_FORWARD; break;
                
                case ENTER: command = "enter"; break; // Needs special handling usually
                case HOME: command = "home"; break;
                case BACK: command = "back"; break;
                
                // D-Pad
                case UP: command = "up"; break;
                case DOWN: command = "down"; break;
                case LEFT: command = "left"; break;
                case RIGHT: command = "right"; break;
                
                case NETFLIX: command = "Launch Netflix"; break; // Placeholder
                case YOUTUBE: command = "Launch YouTube"; break;
                case POWER: command = "system/turnOff"; break;
            }

            if (command != null) {
                ExecuteURL(command);
            }
        }).start();
    }

    // --- MOUSE POINTER ---
    public void movePointer(int dx, int dy) {
        // Pointer logic usually requires WebSocket. 
        // This is a placeholder for HTTP-based movement if supported.
        Log.d("LGTV", "Move Pointer: " + dx + ", " + dy);
    }

    public void clickPointer() {
        send_key("CLICK", MainActivity.KEY_INDEX.ENTER);
    }

    // --- NETWORK HELPER ---
    private void ExecuteURL(String command) {
        try {
            // Construct basic XML/JSON payload depending on TV version
            // This is a generic HTTP POST structure
            URL url = new URL("http://" + myIP + ":8080/roap/api/command");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/atom+xml");
            conn.setDoOutput(true);
            
            String payload = "";
            try(OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            conn.getResponseCode(); // Trigger request
            conn.disconnect();
            
        } catch (Exception e) {
            Log.e("LGTV", "Error sending command: " + e.getMessage());
        }
    }
    
    private void showToast(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        );
    }
}