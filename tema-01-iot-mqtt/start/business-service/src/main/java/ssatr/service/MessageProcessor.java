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

        try {
            while (!Thread.currentThread().isInterrupted()) {
                String mesaj = inbox.take();
                storage.append(kind, mesaj);

                try {
                    String[] parts = mesaj.split(";", 3);
                    if (parts.length < 3) {
                        System.out.println("Mesaj cu format invalid (" + kind + "): " + mesaj);
                        continue;
                    }

                    String deviceId = parts[0];
                    long timestamp = Long.parseLong(parts[1]);

                    switch (kind) {
                        case "telemetry":
                            double temp = Double.parseDouble(parts[2]);
                            dashboard.updateTelemetry(deviceId, timestamp, temp);
                            break;

                        case "alarm":
                            String[] alarmFields = parts[2].split(";");
                            double alarmTemp = Double.parseDouble(alarmFields[0]);
                            dashboard.updateAlarm(deviceId, timestamp, alarmTemp);
                            System.out.println("[ALARM] " + mesaj);
                            break;

                        case "status":
                            dashboard.updateStatus(deviceId, timestamp);
                            System.out.println("[STATUS] " + mesaj);
                            break;
                    }
                } catch (Exception e) {
                    System.out.println("Mesaj invalid ignorat (" + kind + "): " + mesaj);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}