package com.switchroot.joyconmapper;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashMap;
import java.util.Map;

public class JoyConMapperService extends AccessibilityService implements InputManager.InputDeviceListener {
    private static final String TAG = "JoyConMapper";
    private static final String CHANNEL_ID = "joycon_mapper_channel";
    
    private InputManager inputManager;
    private Handler handler;
    private VirtualXboxController virtualController;
    
    // Маппинг кнопок Joy-Con на Xbox
    private static final Map<Integer, Integer> BUTTON_MAP = new HashMap<>();
    static {
        // Left Joy-Con
        BUTTON_MAP.put(544, KeyEvent.KEYCODE_DPAD_UP);
        BUTTON_MAP.put(545, KeyEvent.KEYCODE_DPAD_DOWN);
        BUTTON_MAP.put(546, KeyEvent.KEYCODE_DPAD_LEFT);
        BUTTON_MAP.put(547, KeyEvent.KEYCODE_DPAD_RIGHT);
        BUTTON_MAP.put(310, KeyEvent.KEYCODE_BUTTON_L1);
        BUTTON_MAP.put(312, KeyEvent.KEYCODE_BUTTON_L2);
        BUTTON_MAP.put(314, KeyEvent.KEYCODE_BUTTON_SELECT);
        BUTTON_MAP.put(317, KeyEvent.KEYCODE_BUTTON_THUMBL);
        BUTTON_MAP.put(309, KeyEvent.KEYCODE_BUTTON_Z);
        
        // Right Joy-Con  
        BUTTON_MAP.put(304, KeyEvent.KEYCODE_BUTTON_A);
        BUTTON_MAP.put(305, KeyEvent.KEYCODE_BUTTON_B);
        BUTTON_MAP.put(307, KeyEvent.KEYCODE_BUTTON_Y);
        BUTTON_MAP.put(308, KeyEvent.KEYCODE_BUTTON_X);
        BUTTON_MAP.put(311, KeyEvent.KEYCODE_BUTTON_R1);
        BUTTON_MAP.put(313, KeyEvent.KEYCODE_BUTTON_R2);
        BUTTON_MAP.put(315, KeyEvent.KEYCODE_BUTTON_START);
        BUTTON_MAP.put(316, KeyEvent.KEYCODE_BUTTON_MODE);
        BUTTON_MAP.put(318, KeyEvent.KEYCODE_BUTTON_THUMBR);
    }
    
    private boolean leftJoyConConnected = false;
    private boolean rightJoyConConnected = false;
    
    // Хранение состояния стиков
    private float leftStickX = 0;
    private float leftStickY = 0;
    private float rightStickX = 0;
    private float rightStickY = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        handler = new Handler();
        virtualController = new VirtualXboxController(this);
        
        createNotificationChannel();
        startForegroundService();
        
        // Регистрируем слушатель устройств
        inputManager.registerInputDeviceListener(this, handler);
        
        Log.d(TAG, "JoyConMapper Service started");
        checkConnectedDevices();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "JoyCon Mapper Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Maps JoyCons to Xbox Controller");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void startForegroundService() {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("JoyCon Mapper")
            .setContentText("Mapping JoyCons to Xbox Controller")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build();
            
        // Используем ID 1 для foreground service
        startForeground(1, notification);
    }
    
    private void checkConnectedDevices() {
        int[] deviceIds = inputManager.getInputDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice device = inputManager.getInputDevice(deviceId);
            if (device != null) {
                checkJoyConDevice(device);
            }
        }
    }
    
    private void checkJoyConDevice(InputDevice device) {
        String deviceName = device.getName().toLowerCase();
        
        if (deviceName.contains("joy-con") || deviceName.contains("joycon")) {
            if (deviceName.contains("left")) {
                leftJoyConConnected = true;
                Log.d(TAG, "Left Joy-Con connected: " + device.getName());
            } else if (deviceName.contains("right")) {
                rightJoyConConnected = true;
                Log.d(TAG, "Right Joy-Con connected: " + device.getName());
            }
            
            // Если оба Joy-Con подключены, создаем виртуальный контроллер
            if (leftJoyConConnected && rightJoyConConnected) {
                virtualController.create();
            }
        }
    }
    
    @Override
    public void onInputDeviceAdded(int deviceId) {
        InputDevice device = inputManager.getInputDevice(deviceId);
        if (device != null) {
            Log.d(TAG, "Device added: " + device.getName());
            checkJoyConDevice(device);
        }
    }
    
    @Override
    public void onInputDeviceRemoved(int deviceId) {
        // Проверяем, отключился ли Joy-Con
        Log.d(TAG, "Device removed: " + deviceId);
        checkConnectedDevices();
    }
    
    @Override
    public void onInputDeviceChanged(int deviceId) {
        // Устройство изменилось
    }
    
    // Перехват событий через Accessibility Service
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Accessibility events не нужны для нашей задачи
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }
    
    // Обработка событий ввода
    public boolean handleKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        
        // Проверяем, есть ли маппинг для этой кнопки
        if (BUTTON_MAP.containsKey(keyCode)) {
            int mappedKey = BUTTON_MAP.get(keyCode);
            
            // Отправляем событие на виртуальный контроллер
            if (virtualController != null && virtualController.isCreated()) {
                if (action == KeyEvent.ACTION_DOWN) {
                    virtualController.sendButtonPress(mappedKey);
                } else if (action == KeyEvent.ACTION_UP) {
                    virtualController.sendButtonRelease(mappedKey);
                }
                return true; // Событие обработано
            }
        }
        
        return false;
    }
    
    public boolean handleMotionEvent(MotionEvent event) {
        int source = event.getSource();
        
        // Проверяем, что это джойстик
        if ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            // Получаем значения осей
            float x = event.getAxisValue(MotionEvent.AXIS_X);
            float y = event.getAxisValue(MotionEvent.AXIS_Y);
            float rx = event.getAxisValue(MotionEvent.AXIS_RX);
            float ry = event.getAxisValue(MotionEvent.AXIS_RY);
            
            // Определяем, какой это стик
            InputDevice device = event.getDevice();
            if (device != null) {
                String deviceName = device.getName().toLowerCase();
                
                if (deviceName.contains("left")) {
                    leftStickX = x;
                    leftStickY = y;
                } else if (deviceName.contains("right")) {
                    // Игнорируем правый стик как мышь, используем как обычный стик
                    rightStickX = rx;
                    rightStickY = ry;
                }
                
                // Отправляем объединенные данные стиков
                if (virtualController != null && virtualController.isCreated()) {
                    virtualController.sendAxisData(leftStickX, leftStickY, rightStickX, rightStickY);
                }
                
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (virtualController != null) {
            virtualController.destroy();
        }
        inputManager.unregisterInputDeviceListener(this);
        Log.d(TAG, "JoyConMapper Service stopped");
    }
}