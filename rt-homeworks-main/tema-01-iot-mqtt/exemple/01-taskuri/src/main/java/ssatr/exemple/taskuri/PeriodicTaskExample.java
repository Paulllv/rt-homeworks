package ssatr.exemple.taskuri;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TASK PERIODIC
 *
 * Un task periodic este eliberat la intervale exact egale, cu perioada T.
 * Fiecare execuție a lui este un job. Jobul k este eliberat pe grila
 *
 *     r_k = r_0 + k * T
 *
 * Exemplu tipic: citirea unui senzor la fiecare 1000 ms.
 *
 * Java standard nu are taskuri de timp real, dar ScheduledExecutorService
 * poate programa un cod pe o grilă fixă (scheduleAtFixedRate). Aceasta este o
 * așteptare absolută: următoarea eliberare se calculează din grilă, nu din
 * momentul în care a terminat jobul precedent, deci nu apare derivă.
 *
 * Pentru fiecare job, programul afișează:
 *  - întârzierea (lateness) L_k = s_k - r_k, adică cât de târziu a pornit jobul
 *    față de eliberarea lui de pe grilă. Jitterul este max L_k - min L_k.
 *  - timpul de calcul C al jobului (simulat aleator).
 * Pe un sistem fără garanții de timp real, întârzierea nu este zero și variază.
 */
public class PeriodicTaskExample {

    static final long PERIOD_MS = 1000;
    static final int JOBS = 10;

    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // r_0: prima eliberare este chiar acum (întârziere inițială 0)
        long firstRelease = System.nanoTime();
        SensorReadingTask task = new SensorReadingTask(firstRelease);
        scheduler.scheduleAtFixedRate(task, 0, PERIOD_MS, TimeUnit.MILLISECONDS);

        // lăsăm taskul să execute JOBS joburi, apoi oprim
        Thread.sleep(PERIOD_MS * JOBS - PERIOD_MS / 2);
        scheduler.shutdown();
        scheduler.awaitTermination(1, TimeUnit.SECONDS);
        System.out.println("Gata.");
    }

    /** Codul unui job al taskului periodic. */
    static class SensorReadingTask implements Runnable {
        private final Random random = new Random();
        private final long firstRelease;
        private int k = 0;

        SensorReadingTask(long firstRelease) {
            this.firstRelease = firstRelease;
        }

        @Override
        public void run() {
            // s_k: pornirea jobului; System.nanoTime() este un timp monoton,
            // nu sare la corecțiile ceasului
            long start = System.nanoTime();

            // eliberarea jobului k pe grilă: r_k = r_0 + k * T
            long release = firstRelease + k * PERIOD_MS * 1_000_000;
            double latenessMs = (start - release) / 1_000_000.0;

            // calculul jobului: citim senzorul (simulat)
            double temperature = 20 + random.nextDouble() * 5;
            long computationMs = 50 + random.nextInt(150);
            sleep(computationMs);

            System.out.printf("job #%2d  întârziere L = %6.3f ms  C = %3d ms  temperatura = %.2f%n",
                    k, latenessMs, computationMs, temperature);
            k++;
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
