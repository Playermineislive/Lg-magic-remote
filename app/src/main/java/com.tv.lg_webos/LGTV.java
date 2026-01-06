package com.tv.lg_webos;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.UiThread;
import androidx.preference.PreferenceManager;

import com.tv.lg_webos.MainActivity.KEY_INDEX;
import com.stealthcopter.networktools.ARPInfo;
import com.stealthcopter.networktools.WakeOnLan;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

public class LGTV extends ContextWrapper {

    // --- Configuration Constants ---
    private final static String CST_WSS = "wss://";
    private final static String CST_WS = "ws://";
    private final static String KEY_TV_IP = "key_tv_ip";
    private final static String KEY_CLIENT_KEY = "key_client_key";
    
    public final static String DEFAULT_LGTV_IP = "192.168.1.10";
    
    // Port 3001 (WSS) is required for Magic Remote on modern TVs
    private final static String PORT_SECURE = "3001"; 
    private final static String PORT_LEGACY = "3000";
    private final static int WEBSOCKET_TIMEOUT = 5000; // 5 seconds

    // --- WebOS Protocol URIs ---
    private final static String SSAP_MUTE = "ssap://audio/setMute";
    private final static String SSAP_MOUSE_SOCKET = "ssap://com.webos.service.networkinput/getPointerInputSocket";
    private final static String SSAP_ON = "ssap://system/turnOn";
    private final static String SSAP_OFF = "ssap://system/turnOff";
    private final static String SSAP_VOLUME_UP = "ssap://audio/volumeUp";
    private final static String SSAP_VOLUME_DOWN = "ssap://audio/volumeDown";
    private final static String SSAP_CHANNEL_UP = "ssap://tv/channelUp";
    private final static String SSAP_CHANNEL_DOWN = "ssap://tv/channelDown";
    private final static String SSAP_PLAY = "ssap://media.controls/play";
    private final static String SSAP_PAUSE = "ssap://media.controls/pause";
    private final static String SSAP_STOP = "ssap://media.controls/stop";
    private final static String SSAP_REWIND = "ssap://media.controls/rewind";
    private final static String SSAP_FORWARD = "ssap://media.controls/fastForward";
    private final static String SSAP_UPDATE_INPUT = "ssap://tv/switchInput";
    private final static String SSAP_APP_LAUNCH = "ssap://system.launcher/launch";
    private final static String SSAP_APP_BROWSER = "ssap://system.launcher/open";
    
    // App IDs
    private final static String APP_YOUTUBE = "youtube.leanback.v4";
    private final static String APP_NETFLIX = "netflix";
    private final static String APP_AMAZON = "amazon";
    private final static String APP_LIVE_TV = "com.webos.app.livetv";

    // --- JSON Protocol Keys ---
    private final static String WS_REQUEST = "request";
    private final static String WS_REGISTER = "register";
    private final static String JS_TYPE = "type";
    private final static String JS_ID = "id";
    private final static String JS_URI = "uri";
    private final static String JS_PAYLOAD = "payload";
    private final static String JS_RESPONSE = "response";
    private final static String JS_CLIENT_KEY = "client-key";
    private final static String JS_REGISTERED = "registered";
    private final static String JS_SOCKET_PATH = "socketPath";

    // --- Magic Remote Button Codes ---
    private final static String BTN_HOME = "HOME";
    private final static String BTN_BACK = "BACK";
    private final static String BTN_UP = "UP";
    private final static String BTN_DOWN = "DOWN";
    private final static String BTN_LEFT = "LEFT";
    private final static String BTN_RIGHT = "RIGHT";
    private final static String BTN_ENTER = "ENTER";
    private final static String BTN_EXIT = "EXIT";

    // --- Global State ---
    private static WebSocketClient mWebSocketClient;
    private static WebSocketClient mInputSocket; // The Magic Remote Socket
    private static boolean m_isMute = false;
    private static int nextRequestId = 1;
    private static String m_keyName = null;
    private String myIP, myPort;
    private static String client_key;
    private String mPendingCommand = null;
    private boolean isRetryingLegacy = false; 
    
    private final static String PAIRING_FILE = "pairing.json";

    public LGTV(Context base) { super(base); }

    // --- Preference & IP Management ---
    public String getMyIP() { return myIP; }
    public String getMyPort() { return myPort; }
    public void setMyIP(String lmyIP) { myIP = lmyIP; }

    public void loadMainPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        myIP = prefs.getString(KEY_TV_IP, DEFAULT_LGTV_IP);
        myPort = prefs.getString("key_tv_port", PORT_SECURE);
        client_key = prefs.getString(KEY_CLIENT_KEY, "");
    }

    public void saveIPPreference() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putString(KEY_TV_IP, getMyIP()).apply();
    }

    // --- Payload Generators ---
    private String getRegisterPayload() {
        String tContents = "";
        try {
            InputStream stream = getAssets().open(PAIRING_FILE);
            int size = stream.available();
            byte[] buffer = new byte[size];
            stream.read(buffer); stream.close();
            tContents = new String(buffer);
        } catch (Exception e) { e.printStackTrace(); }
        
        JSONObject headers = new JSONObject();
        try {
            headers.put(JS_TYPE, WS_REGISTER);
            headers.put(JS_ID, "register_0");
            JSONObject obj = new JSONObject(tContents);
            if(!client_key.isEmpty()) obj.put(JS_CLIENT_KEY, client_key);
            headers.put(JS_PAYLOAD, obj);
        } catch (Exception e) { e.printStackTrace(); }
        return headers.toString();
    }
    
    private String getHelloPayload() {
        JSONObject headers = new JSONObject();
        try {
            headers.put(JS_ID, String.valueOf(nextRequestId++));
            headers.put(JS_TYPE, "hello");
            JSONObject payload = new JSONObject();
            payload.put("sdkVersion", "1.1.0");
            payload.put("deviceModel", "Android");
            headers.put(JS_PAYLOAD, payload);
        } catch (Exception e) { e.printStackTrace(); }
        return headers.toString();
    }

    // --- Connection Logic ---
    void ExecuteURL(String url, String keyValue) {
        if (myIP == null) return;
        try {
            boolean needsInput = (m_keyName != null);
            boolean mainOpen = (mWebSocketClient != null && mWebSocketClient.isOpen());
            boolean inputOpen = (mInputSocket != null && mInputSocket.isOpen());

            if (!mainOpen) {
                isRetryingLegacy = false;
                connectWebSocket(CST_WSS + getMyIP() + ":" + PORT_SECURE, url);
            } else if (needsInput && !inputOpen) {
                mWebSocketClient.send(getSimpleURL(SSAP_MOUSE_SOCKET));
            } else {
                if (needsInput) {
                    // Send navigation key via Pointer Socket
                    mInputSocket.send("type:button\nname:" + m_keyName + "\n\n");
                    m_keyName = null;
                } else {
                    mWebSocketClient.send(url);
                }
                if (!keyValue.isEmpty()) postToastMessage(keyValue, Toast.LENGTH_SHORT);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private boolean isNetworkAvailable() {
        if (MainActivity.m_debugMode) return true;
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.net.Network net = cm.getActiveNetwork();
            if (net != null) {
                android.net.NetworkCapabilities cap = cm.getNetworkCapabilities(net);
                return cap != null && (cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
            }
        }
        return false;
    }

    // --- ENHANCED POINTER & INPUT METHODS ---
    
    /**
     * Moves the mouse pointer.
     * UPDATED: Now supports drag (down:1) and standard move (down:0)
     */
    public void movePointer(int dx, int dy, boolean isDrag) {
        if (mInputSocket != null && mInputSocket.isOpen()) {
            //
            mInputSocket.send("type:move\ndx:" + dx + "\ndy:" + dy + "\ndown:" + (isDrag ? 1 : 0) + "\n\n");
        } else {
            m_keyName = null; 
            ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), "");
        }
    }

    // Overload for simple movement
    public void movePointer(int dx, int dy) {
        movePointer(dx, dy, false);
    }

    /**
     * Scrolls the content.
     *
     */
    public void scroll(int dx, int dy) {
        if (mInputSocket != null && mInputSocket.isOpen()) {
            mInputSocket.send("type:scroll\ndx:" + dx + "\ndy:" + dy + "\n\n");
        }
    }

    /**
     * Clicks the mouse pointer.
     */
    public void clickPointer() {
        if (mInputSocket != null && mInputSocket.isOpen()) {
            mInputSocket.send("type:click\n\n");
        }
    }

    /**
     * Sends a full string of text (Socket-based).
     * Faster than WebOSTVKeyboardInput's service call.
     */
    public void sendText(String text) {
        if (mInputSocket != null && mInputSocket.isOpen()) {
            mInputSocket.send("type:text\ntext:" + text + "\n\n");
        } else {
            postToastMessage("Connecting Keyboard...", Toast.LENGTH_SHORT);
            ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), "");
        }
    }

    /**
     * Sends the specific ENTER key command.
     */
    public void sendEnter() {
        if (mInputSocket != null && mInputSocket.isOpen()) {
            mInputSocket.send("type:button\nname:ENTER\n\n");
        }
    }

    /**
     * Sends a Delete command (Mapped to BACK button which acts as backspace).
     */
    public void sendDelete() {
        if (mInputSocket != null && mInputSocket.isOpen()) {
            mInputSocket.send("type:button\nname:BACK\n\n");
        }
    }

    public void sendSpecialKey(String keyName) {
        if (mInputSocket != null && mInputSocket.isOpen()) {
            mInputSocket.send("type:button\nname:" + keyName + "\n\n");
        }
    }

    // --- SSL Security ---
    private void trustEveryone() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[]{new X509TrustManager(){
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
            if (mInputSocket != null) mInputSocket.setSocketFactory(context.getSocketFactory());
            
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- Input Socket Connection ---
    public void connectPointer(URI uri) {
        try {
            if (mInputSocket != null) {
                mInputSocket.close();
                mInputSocket = null;
            }

            mInputSocket = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake arg0) {
                    Log.d("LGTV", "Pointer Socket Connected");
                    if (m_keyName != null) {
                        send("type:button\nname:" + m_keyName + "\n\n");
                        m_keyName = null;
                    }
                }

                @Override
                public void onMessage(String s) {
                    // Method required by WebSocketClient abstract class
                }

                @Override
                public void onClose(int i, String s, boolean b) { mInputSocket = null; }

                @Override
                public void onError(Exception e) { mInputSocket = null; }
            };
            
            trustEveryone(); // Bypass SSL for local IP connection
            mInputSocket.setConnectionLostTimeout(0); // Prevent auto-disconnect
            mInputSocket.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Main Socket Connection (Standard Logic) ---
    private void connectWebSocket(String wsUrl, String pendingCommand) {
        try {
            URI uri = new URI(wsUrl);
            mWebSocketClient = new WebSocketClient(uri, new Draft_6455(), null, WEBSOCKET_TIMEOUT) {
                @Override public void onOpen(ServerHandshake h) {
                    isRetryingLegacy = false;
                    send(getHelloPayload());
                    mPendingCommand = pendingCommand;
                }
                @Override public void onMessage(String s) {
                    try { handleMessage(new JSONObject(s)); } catch (Exception e) {}
                }
                @Override public void onClose(int i, String s, boolean b) { mWebSocketClient = null; }
                @Override public void onError(Exception e) { 
                    mWebSocketClient = null;
                    if (!isRetryingLegacy && wsUrl.contains(PORT_SECURE)) {
                        isRetryingLegacy = true;
                        new Handler(Looper.getMainLooper()).post(() -> 
                            connectWebSocket(CST_WS + getMyIP() + ":" + PORT_LEGACY, pendingCommand)
                        );
                    }
                }
            };
            
            if (wsUrl.startsWith(CST_WSS)) {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new X509TrustManager[]{new X509TrustManager(){
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, new SecureRandom());
                mWebSocketClient.setSocketFactory(sslContext.getSocketFactory());
            }
            mWebSocketClient.connect();
        } catch (Exception e) { e.printStackTrace(); }
    }

    protected void handleMessage(JSONObject message) {
        String type = message.optString(JS_TYPE);
        
        if ("hello".equals(type)) {
            mWebSocketClient.send(getRegisterPayload());
        } 
        else if (JS_REGISTERED.equals(type)) {
            client_key = message.optJSONObject(JS_PAYLOAD).optString(JS_CLIENT_KEY);
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(KEY_CLIENT_KEY, client_key).apply();
            
            if (mPendingCommand != null && !mPendingCommand.isEmpty()) { 
                mWebSocketClient.send(mPendingCommand); 
                mPendingCommand = null; 
            }
        } 
        // Handle Pointer Socket Path
        else if (JS_RESPONSE.equals(type) && message.optJSONObject(JS_PAYLOAD) != null) {
            JSONObject payload = message.optJSONObject(JS_PAYLOAD);
            if (payload.has(JS_SOCKET_PATH)) {
                try { connectPointer(new URI(payload.getString(JS_SOCKET_PATH))); } catch (Exception e) {}
            }
        }
    }

    public void send_key(String key, KEY_INDEX cmd_index) {
        switch (cmd_index) {
            case TV: ExecuteURL(getPayloadURL(SSAP_APP_LAUNCH, "id", APP_LIVE_TV), key); break;
            case YOUTUBE: ExecuteURL(getPayloadURL(SSAP_APP_LAUNCH, "id", APP_YOUTUBE), key); break;
            case NETFLIX: ExecuteURL(getPayloadURL(SSAP_APP_LAUNCH, "id", APP_NETFLIX), key); break;
            case AMAZON: ExecuteURL(getPayloadURL(SSAP_APP_LAUNCH, "id", APP_AMAZON), key); break;
            case ON: ExecuteURL(getSimpleURL(SSAP_ON), key); wakeOnLan(); break;
            case OFF: ExecuteURL(getSimpleURL(SSAP_OFF), key); break;
            case MUTE: m_isMute = !m_isMute; ExecuteURL(getPayloadURL(SSAP_MUTE, "mute", m_isMute), key); break;
            case VOLUME_INCREASE: ExecuteURL(getSimpleURL(SSAP_VOLUME_UP), key); break;
            case VOLUME_DECREASE: ExecuteURL(getSimpleURL(SSAP_VOLUME_DOWN), key); break;
            case CHANNEL_INCREASE: ExecuteURL(getSimpleURL(SSAP_CHANNEL_UP), key); break;
            case CHANNEL_DECREASE: ExecuteURL(getSimpleURL(SSAP_CHANNEL_DOWN), key); break;
            case PLAY: ExecuteURL(getSimpleURL(SSAP_PLAY), key); break;
            case PAUSE: ExecuteURL(getSimpleURL(SSAP_PAUSE), key); break;
            case STOP: ExecuteURL(getSimpleURL(SSAP_STOP), key); break;
            case REWIND: ExecuteURL(getSimpleURL(SSAP_REWIND), key); break;
            case FORWARD: ExecuteURL(getSimpleURL(SSAP_FORWARD), key); break;
            case SOURCE: ExecuteURL(getSimpleURL(SSAP_UPDATE_INPUT), key); break;
            case INTERNET: ExecuteURL(getSimpleURL(SSAP_APP_BROWSER), key); break;
            
            // Navigation (Uses Input Socket)
            case HOME: m_keyName = BTN_HOME; ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), key); break;
            case BACK: m_keyName = BTN_BACK; ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), key); break;
            case UP: m_keyName = BTN_UP; ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), key); break;
            case DOWN: m_keyName = BTN_DOWN; ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), key); break;
            case LEFT: m_keyName = BTN_LEFT; ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), key); break;
            case RIGHT: m_keyName = BTN_RIGHT; ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), key); break;
            case ENTER: m_keyName = BTN_ENTER; ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), key); break;
            case EXIT: m_keyName = BTN_EXIT; ExecuteURL(getSimpleURL(SSAP_MOUSE_SOCKET), key); break;
            
            default: break;
        }
    }

    public void TV_Pairing() {
        if(isNetworkAvailable()) ExecuteURL(getRegisterPayload(), "pairing");
        else postToastMessage(getString(R.string.wifi_not_connected), Toast.LENGTH_LONG);
    }

    private void wakeOnLan() {
        if(!isNetworkAvailable()) return;
        new Thread(() -> {
            try {
                String ip = getMyIP();
                if (ip != null && !ip.isEmpty()) {
                    String mac = ARPInfo.getMACFromIPAddress(ip); 
                    if (mac != null) {
                        WakeOnLan.sendWakeOnLan(ip, mac);
                        postToastMessage("Power On Signal Sent", Toast.LENGTH_SHORT);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @UiThread public void postToastMessage(String message, int duration) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, message, duration).show());
    }

    private String getSimpleURL(String url) {
        JSONObject h = new JSONObject();
        try { h.put(JS_TYPE, WS_REQUEST); h.put(JS_ID, String.valueOf(nextRequestId++)); h.put(JS_URI, url); } catch (Exception e) {}
        return h.toString();
    }

        private String getPayloadURL(String url, String name, Object val) {
        JSONObject h = new JSONObject();
        try { 
            JSONObject p = new JSONObject(); 
            p.put(name, val); // This was missing
            
            h.put(JS_TYPE, WS_REQUEST);
            h.put(JS_ID, String.valueOf(nextRequestId++));
            h.put(JS_URI, url);
            h.put(JS_PAYLOAD, p);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return h.toString();
    }
} // Closes the LGTV class
