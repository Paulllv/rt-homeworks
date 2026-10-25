package ssatr.service;

import java.util.concurrent.BlockingQueue;

/**
 * Preia mesajele de un anumit tip ("telemetry", "alarm" sau "status") din coada
 * inbox, le salvează și actualizează dashboard-ul.
 *
 * Formatul mesajelor (câmpuri separate prin ";"):
 *   telemetry:  deviceId;timestamp;temperatura
 *   alarm:      deviceId;timestamp;temperatura;prag
 *   status:     deviceId;timestamp;text
 */
public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(String kind, BlockingQueue<String> inbox, CsvStorage storage, Dashboard dashboard) {
        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        // TODO 4: într-o buclă:
        //   - preluați un mesaj: inbox.take()
        //   - salvați mesajul în fișierul CSV: storage.append(kind, mesaj)
        //     (CsvStorage este gata implementată)
        //   - actualizați dashboard-ul, în funcție de kind
        //   - alarmele și confirmările se afișează imediat în consolă
        //   - un mesaj greșit (format invalid) nu trebuie să oprească thread-ul
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = inbox.take();
                storage.append(kind, message);

                String[] parts = message.split(";", 3);
                if (parts.length < 3) {
                    System.out.println("[" + kind + "] mesaj cu format invalid ignorat: " + message);
                    continue;
                }

                String deviceId = parts[0].trim();
                long timestamp = Long.parseLong(parts[1].trim());
                String rest = parts[2].trim();

                switch (kind) {
                    case "telemetry": {
                        double temp = Double.parseDouble(rest);
                        dashboard.updateTelemetry(deviceId, timestamp, temp);
                        break;
                    }
                    case "alarm": {
                        String[] alarmParts = rest.split(";");
                        if (alarmParts.length < 2) {
                            System.out.println("[alarm] format invalid: " + message);
                            continue;
                        }
                        double temp = Double.parseDouble(alarmParts[0].trim());
                        double threshold = Double.parseDouble(alarmParts[1].trim());
                        dashboard.updateAlarm(deviceId, timestamp, temp);
                        System.out.printf("[ALARMĂ] %s: %.2f °C (prag %.2f °C)%n", deviceId, temp, threshold);
                        break;
                    }
                    case "status": {
                        dashboard.updateStatus(deviceId, timestamp, rest);
                        System.out.printf("[STATUS] %s: %s%n", deviceId, rest);
                        break;
                    }
                    default:
                        break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.out.println("[" + kind + "] eroare la prelucrarea mesajului: " + e.getMessage());
            }
        }
    }
}