package com.switchroot.joyconmapper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private TextView statusText;
    private Switch enableSwitch;
    private Button settingsButton;
    private TextView joyconStatusText;
    private SharedPreferences prefs;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Создаем простой UI программно (без layout файлов)
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        // Заголовок
        TextView title = new TextView(this);
        title.setText("JoyCon Mapper for Switch");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);
        
        // Статус сервиса
        statusText = new TextView(this);
        statusText.setText("Service Status: Checking...");
        statusText.setTextSize(16);
        statusText.setPadding(0, 0, 0, 10);
        layout.addView(statusText);
        
        // Статус Joy-Con
        joyconStatusText = new TextView(this);
        joyconStatusText.setText("Joy-Con Status: Scanning...");
        joyconStatusText.setTextSize(16);
        joyconStatusText.setPadding(0, 0, 0, 20);
        layout.addView(joyconStatusText);
        
        // Переключатель включения
        enableSwitch = new Switch(this);
        enableSwitch.setText("Enable JoyCon Mapping");
        enableSwitch.setTextSize(18);
        enableSwitch.setPadding(0, 10, 0, 10);
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enableService();
            } else {
                disableService();
            }
        });
        layout.addView(enableSwitch);
        
        // Кнопка настроек доступности
        settingsButton = new Button(this);
        settingsButton.setText("Open Accessibility Settings");
        settingsButton.setOnClickListener(v -> openAccessibilitySettings());
        layout.addView(settingsButton);
        
        // Кнопка сканирования Bluetooth
        Button scanButton = new Button(this);
        scanButton.setText("Scan for Joy-Cons");
        scanButton.setOnClickListener(v -> scanForJoyCons());
        layout.addView(scanButton);
        
        // Инструкции
        TextView instructions = new TextView(this);
        instructions.setText("\nInstructions:\n" +
            "1. Enable Bluetooth and pair your Joy-Cons\n" +
            "2. Enable Accessibility Service in settings\n" +
            "3. Toggle 'Enable JoyCon Mapping'\n" +
            "4. Joy-Cons will work as Xbox controller\n\n" +
            "Note: Works with GeForce Now and other apps!");
        instructions.setTextSize(14);
        instructions.setPadding(0, 20, 0, 0);
        layout.addView(instructions);
        
        setContentView(layout);
        
        prefs = getSharedPreferences("joycon_mapper", MODE_PRIVATE);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkServiceStatus();
        scanForJoyCons();
    }
    
    private void checkServiceStatus() {
        boolean isEnabled = isAccessibilityServiceEnabled();
        statusText.setText("Service Status: " + (isEnabled ? "Enabled" : "Disabled"));
        enableSwitch.setChecked(isEnabled);
        
        if (!isEnabled) {
            statusText.append("\n⚠️ Please enable Accessibility Service");
        }
    }
    
    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        
        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getId().contains(getPackageName())) {
                return true;
            }
        }
        return false;
    }
    
    private void enableService() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            enableSwitch.setChecked(false);
        } else {
            prefs.edit().putBoolean("service_enabled", true).apply();
            Toast.makeText(this, "JoyCon Mapping Enabled", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void disableService() {
        prefs.edit().putBoolean("service_enabled", false).apply();
        Toast.makeText(this, "JoyCon Mapping Disabled", Toast.LENGTH_SHORT).show();
    }
    
    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Enable 'JoyCon Mapper' in the list", Toast.LENGTH_LONG).show();
    }
    
    private void scanForJoyCons() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        if (bluetoothAdapter == null) {
            joyconStatusText.setText("Joy-Con Status: Bluetooth not available");
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            joyconStatusText.setText("Joy-Con Status: Bluetooth disabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
            return;
        }
        
        // Получаем список сопряженных устройств
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        boolean leftFound = false;
        boolean rightFound = false;
        
        for (BluetoothDevice device : pairedDevices) {
            String name = device.getName();
            if (name != null) {
                String nameLower = name.toLowerCase();
                if (nameLower.contains("joy-con") || nameLower.contains("joycon")) {
                    if (nameLower.contains("left") || nameLower.contains("(l)")) {
                        leftFound = true;
                    } else if (nameLower.contains("right") || nameLower.contains("(r)")) {
                        rightFound = true;
                    }
                }
            }
        }
        
        // Обновляем статус
        StringBuilder status = new StringBuilder("Joy-Con Status:\n");
        status.append("Left Joy-Con: ").append(leftFound ? "✓ Connected" : "✗ Not found").append("\n");
        status.append("Right Joy-Con: ").append(rightFound ? "✓ Connected" : "✗ Not found");
        
        if (leftFound && rightFound) {
            status.append("\n✓ Ready to map!");
        } else if (!leftFound && !rightFound) {
            status.append("\n⚠️ Please pair your Joy-Cons via Bluetooth");
        }
        
        joyconStatusText.setText(status.toString());
    }
}