# Контракт IPC v1: Солми Сенсор → Солми

Приложение-датчик: `ru.solmi.sensor`. Основное приложение: `ru.solmi.app`.

## Разрешение
`ru.solmi.permission.SENSOR_DATA`, `protectionLevel="signature"`. Объявлено в Солми Сенсор.
Основное приложение добавляет в манифест:

```xml
<uses-permission android:name="ru.solmi.permission.SENSOR_DATA" />
<queries><provider android:authorities="ru.solmi.sensor.data" /></queries>
```

Оба приложения должны быть подписаны одним ключом, иначе система откажет в доступе.

## ContentProvider
- `content://ru.solmi.sensor.data/latest` — основной адрес.
- `content://com.gismonitor.data/latest` — старый адрес, оставлен для совместимости.

Одна строка, колонки: `ts`, `raw_mgdl`, `cal_mgdl`, `trend` (-3…3), `sensor_name`, `sensor_start`, `sensor_end`, `signal`.
При каждом новом значении вызывается `notifyChange` на оба адреса — подписывайтесь через `ContentObserver`.

## Калибровка
Broadcast `ru.solmi.sensor.CALIBRATE` или старый `com.gismonitor.CALIBRATE` (extras `ts`, `meter_mgdl`), пакет `ru.solmi.sensor`.

## Всплывающее окно
Activity-интент `ru.solmi.sensor.POPUP` или старый `com.gismonitor.POPUP`.

## Прочее
Рассылки для AAPS, xDrip и Nightscout не менялись.
