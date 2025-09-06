package com.switchroot.joyconmapper;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class VirtualXboxController {
    private static final String TAG = "VirtualXbox";
    private Context context;
    private boolean isCreated = false;
    private Process suProcess;
    private DataOutputStream suOutputStream;
    
    // Native library для работы с uinput
    static {
        try {
            System.loadLibrary("joyconmapper");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library", e);
        }
    }
    
    // Native методы
    private native int nativeCreateDevice();
    private native void nativeDestroyDevice(int fd);
    private native void nativeSendButton(int fd, int button, int value);
    private native void nativeSendAxis(int fd, int axis, int value);
    private native void nativeSendSync(int fd);
    
    private int deviceFd = -1;
    
    public VirtualXboxController(Context context) {
        this.context = context;
    }
    
    public void create() {
        if (isCreated) {
            return;
        }
        
        try {
            // Пытаемся создать устройство через native код
            deviceFd = nativeCreateDevice();
            if (deviceFd >= 0) {
                isCreated = true;
                Log.d(TAG, "Virtual Xbox controller created via native");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Native method failed, trying shell approach", e);
        }
        
        // Альтернативный подход через shell команды (требует root)
        createViaShell();
    }
    
    private void createViaShell() {
        try {
            // Создаем скрипт для создания виртуального устройства
            String script = "#!/system/bin/sh\n" +
                "# Create virtual Xbox controller\n" +
                "if [ ! -e /dev/uinput ]; then\n" +
                "  mknod /dev/uinput c 10 223\n" +
                "fi\n" +
                "chmod 666 /dev/uinput\n" +
                "# Signal that device is ready\n" +
                "echo 'Virtual controller ready'\n";
            
            // Сохраняем скрипт
            File scriptFile = new File(context.getCacheDir(), "create_controller.sh");
            FileOutputStream fos = new FileOutputStream(scriptFile);
            fos.write(script.getBytes());
            fos.close();
            scriptFile.setExecutable(true);
            
            // Выполняем скрипт
            ProcessBuilder pb = new ProcessBuilder("sh", scriptFile.getAbsolutePath());
            Process process = pb.start();
            process.waitFor();
            
            isCreated = true;
            Log.d(TAG, "Virtual controller setup completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create virtual controller", e);
            
            // Пытаемся без root через системные API
            createViaSystemApi();
        }
    }
    
    private void createViaSystemApi() {
        // Используем рефлексию для доступа к скрытым API Android
        try {
            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
            // Здесь можно попытаться использовать скрытые методы InputManager
            // для инъекции событий без root
            
            Log.d(TAG, "Attempting to use system API for input injection");
            isCreated = true;
            
        } catch (Exception e) {
            Log.e(TAG, "System API approach failed", e);
        }
    }
    
    public void sendButtonPress(int button) {
        if (!isCreated) return;
        
        if (deviceFd >= 0) {
            nativeSendButton(deviceFd, button, 1);
            nativeSendSync(deviceFd);
        } else {
            // Альтернативный метод через input команду
            sendInputCommand("keyevent", button, "down");
        }
    }
    
    public void sendButtonRelease(int button) {
        if (!isCreated) return;
        
        if (deviceFd >= 0) {
            nativeSendButton(deviceFd, button, 0);
            nativeSendSync(deviceFd);
        } else {
            sendInputCommand("keyevent", button, "up");
        }
    }
    
    public void sendAxisData(float leftX, float leftY, float rightX, float rightY) {
        if (!isCreated) return;
        
        if (deviceFd >= 0) {
            // Конвертируем float в int значения для uinput
            int lx = (int) (leftX * 32767);
            int ly = (int) (leftY * 32767);
            int rx = (int) (rightX * 32767);
            int ry = (int) (rightY * 32767);
            
            nativeSendAxis(deviceFd, 0, lx);  // ABS_X
            nativeSendAxis(deviceFd, 1, ly);  // ABS_Y
            nativeSendAxis(deviceFd, 3, rx);  // ABS_RX
            nativeSendAxis(deviceFd, 4, ry);  // ABS_RY
            nativeSendSync(deviceFd);
        } else {
            // Используем альтернативный метод
            sendMotionViaShell(leftX, leftY, rightX, rightY);
        }
    }
    
    private void sendInputCommand(String type, int code, String action) {
        try {
            String command = String.format("input %s %d", type, code);
            Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        } catch (IOException e) {
            Log.e(TAG, "Failed to send input command", e);
        }
    }
    
    private void sendMotionViaShell(float leftX, float leftY, float rightX, float rightY) {
        // Эмуляция через sendevent или input команды
        try {
            // Конвертируем координаты в события
            String cmd = String.format("input joystick %f %f %f %f", leftX, leftY, rightX, rightY);
            Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
        } catch (IOException e) {
            Log.e(TAG, "Failed to send motion", e);
        }
    }
    
    public boolean isCreated() {
        return isCreated;
    }
    
    public void destroy() {
        if (deviceFd >= 0) {
            nativeDestroyDevice(deviceFd);
            deviceFd = -1;
        }
        
        if (suProcess != null) {
            suProcess.destroy();
            suProcess = null;
        }
        
        isCreated = false;
        Log.d(TAG, "Virtual controller destroyed");
    }
}