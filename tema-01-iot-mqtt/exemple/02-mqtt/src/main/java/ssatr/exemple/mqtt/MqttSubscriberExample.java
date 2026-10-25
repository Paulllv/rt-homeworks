package ssatr.exemple.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * SUBSCRIBER MQTT
 *
 * Se abonează la topic-ul din MqttConfig și afișează fiecare mesaj primit.
 *
 * Mesajele sosesc pe un thread al bibliotecii Paho, prin metoda messageArrived.
 * În această metodă nu se fac operații lungi: pentru prelucrări mai costisitoare,
 * mesajul se pune într-o coadă și este preluat de alt thread
 * (vezi AperiodicTaskExample).
 */
public class MqttSubscriberExample {

    public static void main(String[] args) throws MqttException, InterruptedException {
        String clientId = MqttConfig.STUDENT + "-subscriber-" + System.currentTimeMillis();
        MqttClient client = new MqttClient(MqttConfig.BROKER, clientId, new MemoryPersistence());

        client.setCallback(new PrintingCallback());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        client.connect(options);
        client.subscribe(MqttConfig.TOPIC, 1);
        System.out.println("Abonat la " + MqttConfig.TOPIC + ". Aștept mesaje (Ctrl+C pentru oprire)...");

        // programul rămâne pornit; mesajele sunt primite în PrintingCallback
        Thread.currentThread().join();
    }

    static class PrintingCallback implements MqttCallback {

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String payload = new String(message.getPayload());
            System.out.println("Primit pe " + topic + ": " + payload);
        }

        @Override
        public void connectionLost(Throwable cause) {
            System.out.println("Conexiune pierdută: " + cause.getMessage());
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // folosit doar de publisher
        }
    }
}
