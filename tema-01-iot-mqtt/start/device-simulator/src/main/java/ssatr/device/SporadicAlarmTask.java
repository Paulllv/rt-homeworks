package ssatr.device;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;

/**
 * TASK SPORADIC: tratează alarmele de temperatură.
 *
 * Evenimentele sosesc în coada alarmEvents (puse de taskul periodic).
 * Intervalul minim între sosiri este MIN_INTERARRIVAL_MS: între două eliberări
 * ale taskului (două alarme publicate) trebuie să treacă cel puțin atât.
 * Intervalul este impus de cod: evenimentele care sosesc mai des sunt
 * respinse, ca să nu fie inundat brokerul.
 */
public class SporadicAlarmTask implements Runnable {

    static final long MIN_INTERARRIVAL_MS = 5000;

    private final String deviceId;
    private final MqttConnection mqtt;
    private final BlockingQueue<Double> alarmEvents;
    private final PeriodicTelemetryTask periodic;

    public SporadicAlarmTask(String deviceId, MqttConnection mqtt, BlockingQueue<Double> alarmEvents) {
        this(deviceId, mqtt, alarmEvents, null);
    }

    public SporadicAlarmTask(String deviceId, MqttConnection mqtt, BlockingQueue<Double> alarmEvents,
                             PeriodicTelemetryTask periodic) {
        this.deviceId = deviceId;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
        this.periodic = periodic;
    }

    @Override
    public void run() {
        // TODO 2: implementați taskul sporadic:
        //   - așteptați un eveniment: alarmEvents.take()
        //   - dacă de la ultima eliberare au trecut mai puțin de
        //     MIN_INTERARRIVAL_MS, respingeți evenimentul (afișați un mesaj)
        //   - altfel publicați pe Config.alarmTopic(deviceId) mesajul
        //     "deviceId;timestamp;temperatura;prag"
        //     (pragul curent poate fi transmis împreună cu temperatura sau
        //      citit din PeriodicTelemetryTask; alegeți și justificați în raport)
        //
        // Vezi exemplul exemple/01-taskuri/.../SporadicTaskExample.java

        long lastRelease = 0;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                double t = alarmEvents.take();
                long now = System.currentTimeMillis();

                long sinceLast = now - lastRelease;
                if (sinceLast < MIN_INTERARRIVAL_MS) {
                    System.out.printf(Locale.US,
                            "[sporadic] Alarmă RESPINSĂ (%.2f): au trecut doar %d ms (interval minim %d ms)%n",
                            t, sinceLast, MIN_INTERARRIVAL_MS);
                    continue;
                }
                lastRelease = now;

                double prag = (periodic != null) ? periodic.getThreshold() : 28.0;
                String message = String.format(Locale.US, "%s;%d;%.2f;%.2f", deviceId, now, t, prag);
                mqtt.publish(Config.alarmTopic(deviceId), message);
                System.out.println("[sporadic] ALARMĂ publicată: " + message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}