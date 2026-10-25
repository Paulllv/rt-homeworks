package ssatr.exemple.taskuri;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * TASK APERIODIC
 *
 * Un task aperiodic este declanșat de evenimente despre care nu se știe nici
 * măcar cât de dese pot fi: pot sosi oricând, chiar și în rafală.
 * Nu are perioadă, deci nici o margine a cererii de procesor; de aceea poate
 * primi doar termene soft sau „cât mai repede”, iar în practică primește
 * prioritate mică sau un buget limitat de procesor.
 * Exemplu tipic: o comandă trimisă de un operator către un dispozitiv.
 *
 * Implementarea obișnuită: evenimentele se pun într-o coadă, iar un thread
 * de lucru le preia pe rând, în ordinea sosirii.
 * Observați în rezultate că, atunci când evenimentele sosesc în rafală,
 * ultimele așteaptă mai mult în coadă, deci timpul lor de răspuns crește.
 */
public class AperiodicTaskExample {

    static final int EVENTS = 10;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Command> queue = new LinkedBlockingQueue<>();

        Thread worker = new Thread(new CommandWorker(queue), "worker-aperiodic");
        worker.setDaemon(true);
        worker.start();

        // main simulează un operator care trimite comenzi la momente oarecare;
        // uneori trimite mai multe comenzi aproape simultan (rafală)
        Random random = new Random();
        String[] names = {"RESET", "SET_INTERVAL", "LED_ON", "LED_OFF", "STATUS"};
        for (int i = 1; i <= EVENTS; i++) {
            boolean burst = random.nextInt(3) == 0;
            Thread.sleep(burst ? 10 : 500 + random.nextInt(1500));

            Command c = new Command(i, names[random.nextInt(names.length)], System.currentTimeMillis());
            System.out.printf("[operator] comanda #%d %s trimisă%s%n", c.id, c.name, burst ? " (rafală)" : "");
            queue.put(c);
        }

        Thread.sleep(3000);
        System.out.println("Gata.");
    }

    /** O comandă: număr de ordine, nume și momentul sosirii. */
    static class Command {
        final int id;
        final String name;
        final long time;

        Command(int id, String name, long time) {
            this.id = id;
            this.name = name;
            this.time = time;
        }
    }

    /** Taskul aperiodic: execută comenzile în ordinea sosirii. */
    static class CommandWorker implements Runnable {
        private final BlockingQueue<Command> queue;

        CommandWorker(BlockingQueue<Command> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Command c = queue.take();
                    long waited = System.currentTimeMillis() - c.time;

                    Thread.sleep(400);   // calculul jobului: executarea comenzii (simulată)

                    long responseTime = System.currentTimeMillis() - c.time;
                    System.out.printf("   [aperiodic] #%d %s executată: așteptare în coadă = %d ms, R = %d ms%n",
                            c.id, c.name, waited, responseTime);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
