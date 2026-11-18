# Подготовка Raspberry Pi к работе

Инструкция для «чистой» Ubuntu Server на Raspberry Pi 4 (проверялось на
Ubuntu 24.04.2 aarch64; на 26.10 отличается только имя пакета JDK — см. шаг 2).

Что получится в итоге: плата, воткнутая в чужой компьютер одним USB-кабелем,
выглядит для него **одновременно** как COM-порт и как клавиатура, и на ней
крутится `proxy-server`.

---

## 0. Что нужно

- Raspberry Pi 4 (на Pi 5 OTG-порт тоже есть, но UDC называется иначе — сверьтесь
  с `/sys/class/udc`).
- Кабель **с данными** (не «только зарядка»), воткнутый в **USB-C разъём самой
  платы** (тот, что рядом с HDMI). USB-A разъёмы — это хост, OTG на них не бывает.
- Питание: слабое питание даёт `Undervoltage detected` в `dmesg`; при этом
  устройство может отваливаться на пиках.

## 1. Базовая система

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl
```

Дальше всё делается от root (или через `sudo`).

## 2. Java

Плата запускает JVM-версию (`proxy-server-jvm.jar`), нужен JDK/JRE 17+.

```bash
sudo apt install -y openjdk-21-jre-headless
java -version
```

Если в репозитории 26.10 пакета `openjdk-21-jre-headless` нет — поставьте
`sudo apt install -y default-jre-headless` и проверьте версию: должно быть 17+.

## 3. USB-контроллер в режим device (самый важный шаг)

Ядро должно уметь прикидываться USB-устройством. Проверка:

```bash
ls /sys/class/udc
```

- **пусто** → контроллер в режиме host, эмуляция невозможна, нужно править
  загрузчик;
- **`fe980000.usb`** → всё хорошо, идём дальше.

Правка (Ubuntu кладёт файлы в `/boot/firmware/`):

```bash
# 1. режим device для USB-контроллера (проверьте, что такой строки ещё нет)
grep -n 'dwc2' /boot/firmware/config.txt
echo 'dtoverlay=dwc2,dr_mode=peripheral' | sudo tee -a /boot/firmware/config.txt

# 2. то же самое параметром модуля в cmdline.txt
sudo sed -i 's/$/ dwc2.dr_mode=peripheral/' /boot/firmware/cmdline.txt

sudo reboot
```

После перезагрузки `ls /sys/class/udc` должен показать `fe980000.usb`.

> **Про «две строки dwc2».** В стоковом `config.txt` на Pi 4 уже есть
> `dtoverlay=dwc2,dr_mode=peripheral`, а `dtoverlay=dwc2,dr_mode=host` лежит
> **внутри секции `[cm4]`** — на Pi 4 она не применяется, так что это не
> конфликт. Важно лишь, чтобы в общей части файла (до `[cm4]`/`[pi3+]` и т.п.)
> была строка с `dr_mode=peripheral`, а в `cmdline.txt` — `dwc2.dr_mode=peripheral`
> (параметр модуля имеет приоритет).

### Модули ядра

Без `libcomposite` каталога `/sys/kernel/config/usb_gadget` не существует
вообще. Приложение в режиме фронта **само** делает
`modprobe libcomposite usb_f_acm usb_f_hid` (оно работает от root), поэтому
отдельный шаг не нужен. Если хотите, чтобы модули были готовы ещё до старта
приложения, добавьте в `cmdline.txt`:

```
modules-load=dwc2,libcomposite
```

## 4. Куда положить программу

```bash
sudo mkdir -p /opt/proxy
# скопировать jar и config.yaml в /opt/proxy
```

Два способа доставить файлы:

- **есть сеть**: `scp proxy-server-jvm.jar root@<pi>:/opt/proxy/`;
- **сети нет**: режим фронта (шаг 7) — файлы «печатаются» клавиатурой в Блокнот.

## 5. Автозапуск (systemd)

`/etc/systemd/system/proxy.service`:

```ini
[Unit]
Description=proxy-bridge
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=/opt/proxy
ExecStart=/usr/bin/java -jar /opt/proxy/proxy-server-jvm.jar
Restart=on-abnormal
RestartSec=2s
User=root
Group=root

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now proxy
sudo systemctl status proxy
```

`User=root` нужен не «на всякий случай»: настройка USB-гаджета пишет в
`/sys/kernel/config` (configfs), а это только root.

## 6. Проверка, что гаджет поднялся

В логе (`journalctl -u proxy -n 40`) в режиме фронта видно:

```
USB gadget: supported=true udc=fe980000.usb state=not attached functions=[] serial=null keyboard=null
USB gadget: нет /sys/kernel/config/usb_gadget: не загружен модуль libcomposite
USB gadget: настраиваю…
USB gadget: загружаю модули ядра (libcomposite/usb_f_acm/usb_f_hid)
USB gadget: готов (/dev/ttyGS0 + /dev/hidg0)
```

На уже настроенной системе сообщений меньше: `state=configured`,
`functions=[hid.usb0, acm.usb0]` — и приложение ничего не трогает.

На плате:

```bash
ls -l /dev/ttyGS0 /dev/hidg0          # узлы есть
cat /sys/class/udc/fe980000.usb/state # configured
```

На подключённом компьютере:

```bash
lsusb | grep 1d6b:0104                # Linux Foundation Multifunction Composite Gadget
ls /dev/ttyACM*                       # COM-порт
```

На Windows — «Диспетчер устройств»: раздел «Порты (COM и LPT)» и «Клавиатуры»
(устройство `USB Proxy Bridge (COM + Keyboard)`).

Если гаджет уже создан вручную (`deploy/usb-gadget/setup.sh`), приложение его не
трогает: пересоздание меняет имя COM-порта на хосте (COM3 → COM4).

## 7. Режим фронта (доставка программы на чужой компьютер)

`/opt/proxy/config.yaml`:

```yaml
front:
  enabled: true
  bind: 0.0.0.0
  port: 8900
  bootstrapDir: bootstrap
  serialPort: /dev/ttyGS0
  keyboardDevice: /dev/hidg0
  usbGadget:
    configure: true
```

Скопируйте каталог `bootstrap/` из репозитория в `/opt/proxy/bootstrap`
(варианты: `python/`, `csharp/`, `powershell/`, `standalone/`, `java/`).

```bash
sudo systemctl restart proxy
```

Откройте `http://<ip платы>:8900/` — там блок «USB-гаджет» (настроен/нет +
кнопка «Настроить гаджет») и группы файлов бутстрапа с кнопками.

Важно: **режимы взаимоисключающие**. Пока `front.enabled: true`, прокси-сервисы
не поднимаются — COM-порт и клавиатура нужны бутстрапу.

## 8. Рабочий режим (после доставки)

Плата — сторона с сервисами, ПК — шлюз с выходом в сеть.

`/opt/proxy/config.yaml` на плате:

```yaml
outcomes:
    com: !<com>
        port: "/dev/ttyGS0"
        speed: 115200

services:
    -   !<http-proxy>
        port: 8077
    -   !<socks5-proxy>
        port: 1080
    -   !<webdav>
        port: 8075
        basePath: "/dav"
        fs: "remote"

trafficRoute:
    -   !<always>
        rule: !<direct>

fileSystems:
    remote: !<remote>
        outcome: "com"
        fs: "local"
```

На ПК — `incomes: com` (его COM-порт), и `fileSystems.local.root`.

```bash
sudo systemctl restart proxy
curl -x http://<ip платы>:8077 http://example.com/ -o /dev/null -w "%{http_code}\n"
```

## 9. Диагностика

| Симптом | Что смотреть |
|---|---|
| `ls /sys/class/udc` пуст | шаг 3: `dtoverlay=dwc2,dr_mode=peripheral`, нет строки `dr_mode=host` |
| `state=not attached` | кабель не в USB-C платы, кабель без данных, или хост не видит устройство |
| `Device or resource busy` при записи UDC | UDC занят другим гаджетом: `cat /sys/kernel/config/usb_gadget/*/UDC` |
| `/dev/hidg0` нет, `/dev/ttyGS0` есть | не связана hid-функция: смотрите `problems` в `/api/gadget` |
| `нет /sys/kernel/config/usb_gadget` | не загружен `libcomposite` (приложение грузит сам; если прав нет — `modprobe libcomposite usb_f_acm usb_f_hid`) |
| Хост видит COM, но имя другое | после пересоздания гаджета порт переименовался: поправьте `settings.txt` бутстрапа |
| Устройство отваливается | питание: `vcgencmd get_throttled` (ненулевое значение = просадка) |
| `FRONT MODE: … проксирование выключено` | это норма: `front.enabled: true` отключает сервисы |

Полезные команды:

```bash
journalctl -u proxy -f                        # лог приложения
dmesg | tail -30                              # что видит ядро
cat /sys/class/udc/fe980000.usb/state         # configured / not attached
curl -s localhost:8900/api/gadget | python3 -m json.tool
```

## 10. Ручная настройка гаджета (без фронта)

Если фронт не нужен, но гаджет требуется:

```bash
sudo ./deploy/usb-gadget/setup.sh      # скрипт лежит в репозитории проекта
```

На тестовом стенде он установлен как `/opt/usb-com/setup.sh`.

Скрипт делает то же, что приложение: `acm.usb0` + `hid.usb0` (boot-клавиатура,
8-байтовые отчёты) и привязка к UDC. Приложение при старте увидит готовый
гаджет и ничего не будет менять.
