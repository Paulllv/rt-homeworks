package ssatr.service;

import java.util.LinkedHashMap;
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
    private static class DeviceState {
        double lastTemperature = Double.NaN;
        long lastMessageTime = 0;
        int alarmCount = 0;
        String lastStatus = "-";
    }

    private final Map<String, DeviceState> devices = new LinkedHashMap<>();

    public synchronized void updateTelemetry(String deviceId, long timestamp, double temperature) {
        DeviceState state = devices.computeIfAbsent(deviceId, k -> new DeviceState());
        state.lastTemperature = temperature;
        state.lastMessageTime = timestamp;
    }

    public synchronized void updateAlarm(String deviceId, long timestamp, double temperature) {
        DeviceState state = devices.computeIfAbsent(deviceId, k -> new DeviceState());
        state.lastTemperature = temperature;
        state.lastMessageTime = timestamp;
        state.alarmCount++;
    }

    public synchronized void updateStatus(String deviceId, long timestamp, String statusText) {
        DeviceState state = devices.computeIfAbsent(deviceId, k -> new DeviceState());
        state.lastMessageTime = timestamp;
        state.lastStatus = statusText;
    }

    /** Afișează un tabel cu starea tuturor device-urilor. */
    public synchronized void print() {
        // TODO 5: afișați câte un rând pentru fiecare device, de exemplu:
        //   DEVICE     TEMP    ULTIMUL MESAJ   ALARME
        //   device-1   23.41   acum 1 s        2
        if (devices.isEmpty()) {
            System.out.println("(niciun device activ în dashboard)");
            return;
        }

        System.out.printf("%-16s %-10s %-16s %-8s %-20s%n",
                "DEVICE", "TEMP", "ULTIMUL MESAJ", "ALARME", "ULTIMUL STATUS");
        long now = System.currentTimeMillis();

        for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {
            String id = entry.getKey();
            DeviceState s = entry.getValue();
            String tempStr = Double.isNaN(s.lastTemperature) ? "-" : String.format("%.2f", s.lastTemperature);
            long secondsAgo = Math.max(0, (now - s.lastMessageTime) / 1000);
            String agoStr = "acum " + secondsAgo + " s";

            System.out.printf("%-16s %-10s %-16s %-8d %-20s%n",
                    id, tempStr, agoStr, s.alarmCount, s.lastStatus);
        }
    }
}