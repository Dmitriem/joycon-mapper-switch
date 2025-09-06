#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <linux/uinput.h>
#include <string.h>
#include <errno.h>
#include <android/log.h>

#define LOG_TAG "JoyConMapper-Native"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

// Создание виртуального устройства
JNIEXPORT jint JNICALL
Java_com_switchroot_joyconmapper_VirtualXboxController_nativeCreateDevice(JNIEnv *env, jobject thiz) {
    int fd;
    struct uinput_setup usetup;
    
    // Открываем uinput
    fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        LOGE("Failed to open /dev/uinput: %s", strerror(errno));
        return -1;
    }
    
    // Включаем события кнопок
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    
    // Xbox кнопки
    ioctl(fd, UI_SET_KEYBIT, BTN_A);
    ioctl(fd, UI_SET_KEYBIT, BTN_B);
    ioctl(fd, UI_SET_KEYBIT, BTN_X);
    ioctl(fd, UI_SET_KEYBIT, BTN_Y);
    ioctl(fd, UI_SET_KEYBIT, BTN_TL);
    ioctl(fd, UI_SET_KEYBIT, BTN_TR);
    ioctl(fd, UI_SET_KEYBIT, BTN_TL2);
    ioctl(fd, UI_SET_KEYBIT, BTN_TR2);
    ioctl(fd, UI_SET_KEYBIT, BTN_SELECT);
    ioctl(fd, UI_SET_KEYBIT, BTN_START);
    ioctl(fd, UI_SET_KEYBIT, BTN_MODE);
    ioctl(fd, UI_SET_KEYBIT, BTN_THUMBL);
    ioctl(fd, UI_SET_KEYBIT, BTN_THUMBR);
    
    // D-pad
    ioctl(fd, UI_SET_KEYBIT, BTN_DPAD_UP);
    ioctl(fd, UI_SET_KEYBIT, BTN_DPAD_DOWN);
    ioctl(fd, UI_SET_KEYBIT, BTN_DPAD_LEFT);
    ioctl(fd, UI_SET_KEYBIT, BTN_DPAD_RIGHT);
    
    // Включаем абсолютные оси (стики)
    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    
    struct uinput_abs_setup abs_setup;
    
    // Левый стик X
    memset(&abs_setup, 0, sizeof(abs_setup));
    abs_setup.code = ABS_X;
    abs_setup.absinfo.minimum = -32768;
    abs_setup.absinfo.maximum = 32767;
    abs_setup.absinfo.fuzz = 250;
    abs_setup.absinfo.flat = 1500;
    ioctl(fd, UI_ABS_SETUP, &abs_setup);
    
    // Левый стик Y
    abs_setup.code = ABS_Y;
    ioctl(fd, UI_ABS_SETUP, &abs_setup);
    
    // Правый стик X
    abs_setup.code = ABS_RX;
    ioctl(fd, UI_ABS_SETUP, &abs_setup);
    
    // Правый стик Y
    abs_setup.code = ABS_RY;
    ioctl(fd, UI_ABS_SETUP, &abs_setup);
    
    // Триггеры
    abs_setup.code = ABS_Z;
    abs_setup.absinfo.minimum = 0;
    abs_setup.absinfo.maximum = 255;
    abs_setup.absinfo.fuzz = 0;
    abs_setup.absinfo.flat = 0;
    ioctl(fd, UI_ABS_SETUP, &abs_setup);
    
    abs_setup.code = ABS_RZ;
    ioctl(fd, UI_ABS_SETUP, &abs_setup);
    
    // Включаем Force Feedback (вибрация)
    ioctl(fd, UI_SET_EVBIT, EV_FF);
    ioctl(fd, UI_SET_FFBIT, FF_RUMBLE);
    ioctl(fd, UI_SET_FFBIT, FF_PERIODIC);
    
    // Настраиваем устройство
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor = 0x045e;  // Microsoft
    usetup.id.product = 0x02dd; // Xbox One Controller
    usetup.id.version = 0x0100;
    strcpy(usetup.name, "Xbox One Controller (JoyCon Mapper)");
    
    if (ioctl(fd, UI_DEV_SETUP, &usetup) < 0) {
        LOGE("UI_DEV_SETUP failed: %s", strerror(errno));
        close(fd);
        return -1;
    }
    
    // Создаем устройство
    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        LOGE("UI_DEV_CREATE failed: %s", strerror(errno));
        close(fd);
        return -1;
    }
    
    LOGD("Virtual Xbox controller created successfully");
    return fd;
}

// Уничтожение устройства
JNIEXPORT void JNICALL
Java_com_switchroot_joyconmapper_VirtualXboxController_nativeDestroyDevice(JNIEnv *env, jobject thiz, jint fd) {
    if (fd >= 0) {
        ioctl(fd, UI_DEV_DESTROY);
        close(fd);
        LOGD("Virtual device destroyed");
    }
}

// Отправка события кнопки
JNIEXPORT void JNICALL
Java_com_switchroot_joyconmapper_VirtualXboxController_nativeSendButton(JNIEnv *env, jobject thiz, jint fd, jint button, jint value) {
    if (fd < 0) return;
    
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    
    // Маппинг Android KeyEvent на Linux кнопки
    int linux_button;
    switch(button) {
        case 96:  // KEYCODE_BUTTON_A
            linux_button = BTN_A;
            break;
        case 97:  // KEYCODE_BUTTON_B
            linux_button = BTN_B;
            break;
        case 99:  // KEYCODE_BUTTON_X
            linux_button = BTN_X;
            break;
        case 100: // KEYCODE_BUTTON_Y
            linux_button = BTN_Y;
            break;
        case 102: // KEYCODE_BUTTON_L1
            linux_button = BTN_TL;
            break;
        case 103: // KEYCODE_BUTTON_R1
            linux_button = BTN_TR;
            break;
        case 104: // KEYCODE_BUTTON_L2
            linux_button = BTN_TL2;
            break;
        case 105: // KEYCODE_BUTTON_R2
            linux_button = BTN_TR2;
            break;
        case 109: // KEYCODE_BUTTON_SELECT
            linux_button = BTN_SELECT;
            break;
        case 108: // KEYCODE_BUTTON_START
            linux_button = BTN_START;
            break;
        case 110: // KEYCODE_BUTTON_MODE
            linux_button = BTN_MODE;
            break;
        case 106: // KEYCODE_BUTTON_THUMBL
            linux_button = BTN_THUMBL;
            break;
        case 107: // KEYCODE_BUTTON_THUMBR
            linux_button = BTN_THUMBR;
            break;
        case 19:  // KEYCODE_DPAD_UP
            linux_button = BTN_DPAD_UP;
            break;
        case 20:  // KEYCODE_DPAD_DOWN
            linux_button = BTN_DPAD_DOWN;
            break;
        case 21:  // KEYCODE_DPAD_LEFT
            linux_button = BTN_DPAD_LEFT;
            break;
        case 22:  // KEYCODE_DPAD_RIGHT
            linux_button = BTN_DPAD_RIGHT;
            break;
        default:
            return;
    }
    
    ev.type = EV_KEY;
    ev.code = linux_button;
    ev.value = value;
    
    write(fd, &ev, sizeof(ev));
}

// Отправка события оси
JNIEXPORT void JNICALL
Java_com_switchroot_joyconmapper_VirtualXboxController_nativeSendAxis(JNIEnv *env, jobject thiz, jint fd, jint axis, jint value) {
    if (fd < 0) return;
    
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    
    ev.type = EV_ABS;
    ev.code = axis;
    ev.value = value;
    
    write(fd, &ev, sizeof(ev));
}

// Синхронизация событий
JNIEXPORT void JNICALL
Java_com_switchroot_joyconmapper_VirtualXboxController_nativeSendSync(JNIEnv *env, jobject thiz, jint fd) {
    if (fd < 0) return;
    
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    
    ev.type = EV_SYN;
    ev.code = SYN_REPORT;
    ev.value = 0;
    
    write(fd, &ev, sizeof(ev));
}

}