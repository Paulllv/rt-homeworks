package ssatr.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ultima stare cunoscută a fiecărui device: ultima temperatură, momentul
 * ultimului mesaj, numărul de alarme etc.
 *
 * Este actualizată de thread-urile MessageProcessor și citită de CommandConsole,
 * deci accesul trebuie sincronizat.
 */
public class Dashboard {

    // TODO 5: alegeți structura de date pentru starea device-urilor
    //   (de exemplu un Map<String, ...> cu cheia deviceId)
    //   și adăugați metodele de actualizare apelate din MessageProcessor.

    private static class DeviceInfo {
        double lastTemp = Double.NaN;
        long lastMessageTime = 0;
        int alarmCount = 0;
    }

    private final Map<String, DeviceInfo> devices = new LinkedHashMap<>();

    public synchronized void updateTelemetry(String deviceId, long timestamp, double temp) {
        DeviceInfo info = devices.computeIfAbsent(deviceId, k -> new DeviceInfo());
        info.lastTemp = temp;
        info.lastMessageTime = timestamp;
    }

    public synchronized void updateAlarm(String deviceId, long timestamp, double temp) {
        DeviceInfo info = devices.computeIfAbsent(deviceId, k -> new DeviceInfo());
        info.lastTemp = temp;
        info.lastMessageTime = timestamp;
        info.alarmCount++;
    }

    public synchronized void updateStatus(String deviceId, long timestamp) {
        DeviceInfo info = devices.computeIfAbsent(deviceId, k -> new DeviceInfo());
        info.lastMessageTime = timestamp;
    }

    /** Afișează un tabel cu starea tuturor device-urilor. */
    public synchronized void print() {
        // TODO 5: afișați câte un rând pentru fiecare device, de exemplu:
        //   DEVICE     TEMP    ULTIMUL MESAJ   ALARME
        //   device-1   23.41   acum 1 s        2

        System.out.printf("%-10s %-7s %-15s %s%n", "DEVICE", "TEMP", "ULTIMUL MESAJ", "ALARME");
        long now = System.currentTimeMillis();
        for (Map.Entry<String, DeviceInfo> entry : devices.entrySet()) {
            DeviceInfo d = entry.getValue();
            long sec = Math.max(0, (now - d.lastMessageTime) / 1000);
            String tempStr = Double.isNaN(d.lastTemp) ? "-" : String.format(Locale.US, "%.2f", d.lastTemp);
            System.out.printf("%-10s %-7s %-15s %d%n",
                    entry.getKey(), tempStr, "acum " + sec + " s", d.alarmCount);
        }
    }
}