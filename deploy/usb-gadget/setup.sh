#!/bin/bash
#===============================================================================
# РУЧНОЙ вариант настройки. Приложение умеет то же самое само (:usb-gadget,
# front.usbGadget.configure) — скрипт нужен, если гаджет требуется без фронта.
#
# USB composite gadget для Raspberry Pi 4 (Ubuntu)
#
# Одно устройство, один кабель, две функции:
#   acm.usb0 — виртуальный COM-порт (Pi: /dev/ttyGS0, ПК: /dev/ttyACM0 / COMx)
#   hid.usb0 — клавиатура HID (boot protocol; Pi: /dev/hidg0)
#
# Клавиатура сделана по boot protocol: стандартный 8-байтовый отчёт
# [modifiers, reserved, key1..key6], поэтому работает даже в BIOS/UEFI,
# в установщике ОС и в консоли восстановления.
#===============================================================================
set -e

GADGET_NAME="g1"
GADGET_DIR="/sys/kernel/config/usb_gadget/$GADGET_NAME"
UDC_NAME="fe980000.usb"

#-------------------------------------------------------------------------------
# Функция очистки старого гаджета
#-------------------------------------------------------------------------------
cleanup_gadget() {
    if [ -d "$GADGET_DIR" ]; then
        echo "   Очистка старой конфигурации configfs..."
        echo "" > "$GADGET_DIR/UDC" 2>/dev/null || true
        sleep 0.3
        rm -f "$GADGET_DIR"/configs/*/acm.usb0 2>/dev/null || true
        rm -f "$GADGET_DIR"/configs/*/hid.usb0 2>/dev/null || true
        find "$GADGET_DIR" -depth -type d -exec rmdir {} \; 2>/dev/null || true
    fi
}

#-------------------------------------------------------------------------------
# Функция отвязки legacy-драйверов от UDC
#-------------------------------------------------------------------------------
unbind_legacy_drivers() {
    echo "   Отвязка legacy-драйверов от UDC..."
    for driver in g_cdc g_audio g_ether g_serial g_mass_storage g_hid; do
        if [ -d "/sys/bus/platform/drivers/$driver" ]; then
            echo "$UDC_NAME" > "/sys/bus/platform/drivers/$driver/unbind" 2>/dev/null || true
            echo "    → $driver отвязан"
        fi
    done
    sleep 0.5
}

#-------------------------------------------------------------------------------
# Функция проверки состояния UDC
#-------------------------------------------------------------------------------
check_udc_status() {
    if [ ! -d "/sys/class/udc/$UDC_NAME" ]; then
        echo "❌ ОШИБКА: UDC $UDC_NAME не найден!"
        echo "Проверьте:"
        echo "  1. Подключён ли кабель USB-C к ПК"
        echo "  2. Наличие dtoverlay=dwc2,dr_mode=peripheral в /boot/firmware/config.txt"
        echo "  3. Наличие dwc2.dr_mode=peripheral в /boot/firmware/cmdline.txt"
        exit 1
    fi

    DRIVER=$(cat /sys/class/udc/$UDC_NAME/uevent 2>/dev/null | grep "USB_UDC_DRIVER=" | cut -d'=' -f2)
    if [ -n "$DRIVER" ]; then
        echo "❌ ОШИБКА: UDC занят драйвером: $DRIVER"
        echo "Выполните: sudo rmmod $DRIVER 2>/dev/null || echo 'Модуль встроен в ядро'"
        exit 1
    fi
}

#-------------------------------------------------------------------------------
# Дескриптор boot-клавиатуры (стандартный, 8-байтовый отчёт)
#   byte 0   — модификаторы (bit0 L-Ctrl, bit1 L-Shift, ..., bit7 R-GUI)
#   byte 1   — reserved
#   bytes 2-7 — до 6 одновременно нажатых usage-кодов
#-------------------------------------------------------------------------------
write_keyboard_descriptor() {
    # ВАЖНО: configfs принимает дескриптор ТОЛЬКО одной записью — каждая
    # последующая перезаписывает файл с нуля, поэтому printf должен быть один.
    printf '\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x01\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x01\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0' > "$1"
}

#-------------------------------------------------------------------------------
# Основная часть
#-------------------------------------------------------------------------------
echo "═══════════════════════════════════════════════════════════"
echo "  USB composite gadget (CDC-ACM + HID keyboard)"
echo "═══════════════════════════════════════════════════════════"

# 1. Очистка
cleanup_gadget

# 2. Отвязка legacy-драйверов
unbind_legacy_drivers

# 3. Проверка UDC
check_udc_status

# 4. Загрузка модулей
echo "  📦 Загрузка модулей libcomposite / usb_f_acm / usb_f_hid..."
modprobe libcomposite
modprobe usb_f_acm
modprobe usb_f_hid
sleep 0.5

# 5. Создание структуры гаджета
echo "  🔨 Создание структуры USB Gadget..."
mkdir -p "$GADGET_DIR"
cd "$GADGET_DIR"

# ID вендора и продукта
echo 0x1d6b > idVendor   # Linux Foundation
echo 0x0104 > idProduct  # Multifunction Composite Gadget

# Строковые дескрипторы (0x409 = US English)
mkdir -p strings/0x409
echo "1234567890" > strings/0x409/serialnumber
echo "Raspberry Pi" > strings/0x409/manufacturer
echo "USB Proxy Bridge (COM + Keyboard)" > strings/0x409/product

# Конфигурация
mkdir configs/c.1
mkdir -p configs/c.1/strings/0x409
echo "Config 1: CDC ACM + HID" > configs/c.1/strings/0x409/configuration
echo 250 > configs/c.1/MaxPower

# Функция CDC-ACM (COM-порт)
mkdir functions/acm.usb0
ln -s functions/acm.usb0 configs/c.1/

# Функция HID (клавиатура)
mkdir functions/hid.usb0
echo 1 > functions/hid.usb0/protocol       # 1 = keyboard
echo 1 > functions/hid.usb0/subclass       # 1 = boot interface
echo 8 > functions/hid.usb0/report_length
write_keyboard_descriptor functions/hid.usb0/report_desc
ln -s functions/hid.usb0 configs/c.1/

# 6. Привязка к UDC
echo "  🔌 Привязка к UDC ($UDC_NAME)..."
if ! echo "$UDC_NAME" > UDC 2>/dev/null; then
    echo "❌ ОШИБКА: Не удалось привязаться к UDC!"
    echo "Проверьте: dmesg | tail -n 10"
    exit 1
fi

sleep 1

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ✅ Гаджет запущен: COM + клавиатура"
echo "═══════════════════════════════════════════════════════════"
for dev in /dev/ttyGS0 /dev/hidg0; do
    if [ -e "$dev" ]; then
        echo "  📍 $dev"
        ls -l "$dev"
    else
        echo "  ⚠️  $dev ещё не появился (подождите пару секунд)"
    fi
done
echo ""
echo "  🐧 На Linux: lsusb | grep 1d6b:0104 ; ls /dev/ttyACM* ; ls /dev/input/by-id/*"
echo "  🪟 На Windows: Диспетчер устройств → Порты (COM и LPT) + Клавиатуры"
echo ""
