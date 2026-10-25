// Device-ul IoT din tema 01, de data aceasta pe un ESP32-C3 Super Mini.
//
// Aceeași logică și același protocol ca device-simulator din tema 01, cu trei
// taskuri FreeRTOS în loc de trei thread-uri Java:
//
//   task periodic --(coada de alarme)--> task sporadic
//        |                                     |
//        +---- telemetry ---> MQTT <--- alarm -+
//                              |
//                          commands --> command_received() --(coada de comenzi)--> task aperiodic ---> status
//
// Gata implementate (nu trebuie modificate):
//   config.h         setările: Wi-Fi, broker, STUDENT, valorile inițiale; DEVICE_ID
//                    se citește din adresa MAC a plăcii
//   connection.h/cpp Wi-Fi, ora exactă (SNTP), clientul MQTT, mqtt_publish(), epoch_ms()
//
// Așa cum este, schița se conectează la Wi-Fi și la broker și afișează pe
// Serial comenzile primite. Taskurile le scrieți voi: TODO 0 ... TODO 3.
// Exemplele de pornire sunt în laboratorul 0 din rt-labs-esp32c3; vezi
// exemple/README.md din folderul temei.
//
// Placa: ESP32C3 Dev Module, USB CDC On Boot: Enabled. Serial Monitor: 115200 baud.

#include <stdlib.h>
#include <string.h>
#include "config.h"
#include "connection.h"

// Variabile globale volatile, citite și modificate din taskuri diferite
static volatile uint32_t g_period_ms = PERIOD_MS_INITIAL;
static volatile float g_threshold = THRESHOLD_INITIAL;

// Structurile pentru elementele cozilor (copiate prin valoare de xQueueSend)
struct AlarmEvent {
    float temperature;
    float threshold;
};

struct CommandMsg {
    char text[COMMAND_MAX_LEN];
};

// Handle-urile celor două cozi FreeRTOS
static QueueHandle_t g_alarm_queue = NULL;
static QueueHandle_t g_command_queue = NULL;

// Prioritățile celor trei taskuri (între 2 și 19) și dimensiunea stivei
const UBaseType_t PRIO_SPORADIC  = 4;
const UBaseType_t PRIO_PERIODIC  = 3;
const UBaseType_t PRIO_APERIODIC = 2;
const uint32_t STACK_SIZE_BYTES  = 4096;

// Temperatura cipului, în °C, de la senzorul intern al ESP32-C3.
// Este temperatura siliciului, nu a camerei: imediat după pornirea Wi-Fi-ului
// poate fi în jur de 50 °C, apoi scade în câteva minute spre 35-40 °C.
// Rezoluția este de aproximativ 1 °C. Ca să provocați alarme, puneți pragul
// cu SET_THRESHOLD cu un grad sub valoarea citită.
float read_temperature(void) {
    return temperatureRead();
}

// TODO 1: TASK PERIODIC, cu perioada T (inițial PERIOD_MS_INITIAL).
//   Fiecare job:
//   - citește temperatura: read_temperature()
//   - publică telemetria: mqtt_publish("telemetry", "deviceId;timestamp;temperatura")
//     (timestamp = epoch_ms(), tipărit cu %lld; temperatura cu "%.2f")
//   - dacă temperatura depășește pragul (inițial THRESHOLD_INITIAL), pune
//     un eveniment în coada de alarme, fără să se blocheze (timeout 0).
//     Taskul periodic NU tratează alarma: o tratează taskul sporadic.
//   Eliberările respectă grila r_k = r_0 + kT: folosiți așteptare absolută
//   (xTaskDelayUntil), nu relativă (delay, vTaskDelay), ca să nu apară derivă.
//   Perioada și pragul se schimbă prin comenzi (taskul aperiodic), deci sunt
//   variabile globale volatile, citite la fiecare job.
static void periodic_telemetry_task(void* arg) {
    (void)arg;
    TickType_t last_wake_time = xTaskGetTickCount();

    for (;;) {
        float temp = read_temperature();
        int64_t ts = epoch_ms();
        float current_threshold = g_threshold;
        uint32_t current_period = g_period_ms;

        char payload[128];
        snprintf(payload, sizeof(payload), "%s;%lld;%.2f", DEVICE_ID, (long long)ts, temp);
        mqtt_publish("telemetry", payload);
        Serial.printf("[periodic] telemetrie publicată: %s (T=%lu ms, prag=%.2f C)\n",
                      payload, (unsigned long)current_period, current_threshold);

        if (temp > current_threshold) {
            AlarmEvent ev = { temp, current_threshold };
            if (xQueueSend(g_alarm_queue, &ev, 0) != pdTRUE) {
                Serial.println("[periodic] coada de alarme este plină, eveniment pierdut!");
            }
        }

        xTaskDelayUntil(&last_wake_time, pdMS_TO_TICKS(current_period));
    }
}

// TODO 2: TASK SPORADIC, tratează alarmele de temperatură.
//   - așteaptă un eveniment din coada de alarme
//   - dacă de la ultima alarmă publicată au trecut mai puțin de
//     MIN_INTERARRIVAL_MS, respinge evenimentul (afișați un mesaj pe Serial)
//   - altfel publică: mqtt_publish("alarm", "deviceId;timestamp;temperatura;prag")
//   Pentru intervale folosiți un timp monoton, millis(), nu epoch_ms().
static void sporadic_alarm_task(void* arg) {
    (void)arg;
    bool has_published_before = false;
    uint32_t last_alarm_ms = 0;

    for (;;) {
        AlarmEvent ev;
        if (xQueueReceive(g_alarm_queue, &ev, portMAX_DELAY) == pdTRUE) {
            uint32_t now_ms = millis();

            if (has_published_before && (now_ms - last_alarm_ms < MIN_INTERARRIVAL_MS)) {
                Serial.printf("[sporadic] alarmă RESPINSĂ (T=%.2f C > %.2f C): au trecut doar %lu ms (< %lu ms)\n",
                              ev.temperature, ev.threshold,
                              (unsigned long)(now_ms - last_alarm_ms),
                              (unsigned long)MIN_INTERARRIVAL_MS);
                continue;
            }

            last_alarm_ms = now_ms;
            has_published_before = true;

            int64_t ts = epoch_ms();
            char payload[128];
            snprintf(payload, sizeof(payload), "%s;%lld;%.2f;%.2f",
                     DEVICE_ID, (long long)ts, ev.temperature, ev.threshold);
            mqtt_publish("alarm", payload);
            Serial.printf("[sporadic] ALARMĂ publicată: %s\n", payload);
        }
    }
}

// TODO 3: TASK APERIODIC, execută comenzile primite de la business service.
//   Comenzile ajung în command_received() (mai jos), care le pune în coada de
//   comenzi; fiecare comandă eliberează un job al taskului:
//     SET_INTERVAL <ms>       schimbă perioada T a taskului periodic
//     SET_THRESHOLD <valoare> schimbă pragul de alarmă
//     RESET                   readuce perioada și pragul la valorile inițiale
//     STATUS                  raportează perioada și pragul curente
//   După fiecare comandă publică o confirmare:
//     mqtt_publish("status", "deviceId;timestamp;OK SET_INTERVAL 1000")
//   O comandă necunoscută sau un parametru invalid nu oprește taskul: se
//   publică "ERROR <motiv>".
static void aperiodic_command_task(void* arg) {
    (void)arg;

    for (;;) {
        CommandMsg cmd;
        if (xQueueReceive(g_command_queue, &cmd, portMAX_DELAY) == pdTRUE) {
            char result[96];

            if (strncmp(cmd.text, "SET_INTERVAL ", 13) == 0) {
                const char* param = cmd.text + 13;
                char* endptr = NULL;
                long val = strtol(param, &endptr, 10);
                if (param[0] != '\0' && *endptr == '\0' && val > 0) {
                    g_period_ms = (uint32_t)val;
                    snprintf(result, sizeof(result), "OK SET_INTERVAL %ld", val);
                } else {
                    snprintf(result, sizeof(result), "ERROR invalid interval: %s", param);
                }
            } else if (strncmp(cmd.text, "SET_THRESHOLD ", 14) == 0) {
                const char* param = cmd.text + 14;
                char* endptr = NULL;
                float val = strtof(param, &endptr);
                if (param[0] != '\0' && *endptr == '\0') {
                    g_threshold = val;
                    snprintf(result, sizeof(result), "OK SET_THRESHOLD %.2f", val);
                } else {
                    snprintf(result, sizeof(result), "ERROR invalid threshold: %s", param);
                }
            } else if (strcmp(cmd.text, "RESET") == 0) {
                g_period_ms = PERIOD_MS_INITIAL;
                g_threshold = THRESHOLD_INITIAL;
                snprintf(result, sizeof(result), "OK RESET (period=%lu, threshold=%.2f)",
                         (unsigned long)g_period_ms, g_threshold);
            } else if (strcmp(cmd.text, "STATUS") == 0) {
                snprintf(result, sizeof(result), "OK STATUS period=%lu threshold=%.2f",
                         (unsigned long)g_period_ms, g_threshold);
            } else {
                snprintf(result, sizeof(result), "ERROR unknown command: %s", cmd.text);
            }

            int64_t ts = epoch_ms();
            char payload[160];
            snprintf(payload, sizeof(payload), "%s;%lld;%s", DEVICE_ID, (long long)ts, result);
            mqtt_publish("status", payload);
            Serial.printf("[aperiodic] comandă executată -> %s\n", payload);
        }
    }
}

// Apelată de clientul MQTT pentru fiecare comandă primită (vezi connection.h).
void command_received(const char* text) {
    Serial.printf("[mqtt] comandă primită: %s\n", text);
    // TODO 3: puneți comanda în coada de comenzi, fără să vă blocați
    //   (timeout 0). text este valid doar în timpul acestui apel: elementul
    //   cozii trebuie să conțină textul însuși (de exemplu un struct cu
    //   char text[COMMAND_MAX_LEN]), nu un pointer.
    if (g_command_queue == NULL) {
        return;
    }
    CommandMsg msg;
    strncpy(msg.text, text, COMMAND_MAX_LEN - 1);
    msg.text[COMMAND_MAX_LEN - 1] = '\0';

    if (xQueueSend(g_command_queue, &msg, 0) != pdTRUE) {
        Serial.println("[mqtt] coada de comenzi este plină, comandă pierdută!");
    }
}

void setup() {
    Serial.begin(115200);
    // Fără așteptare la scriere: cu placa la USB, dar fără Serial Monitor deschis,
    // fiecare Serial.printf() ar aștepta până la 2 s să fie citit și ar întârzia
    // taskul care scrie. Așa, textul pe care nu-l citește nimeni se pierde.
    Serial.setTxTimeoutMs(0);
    uint32_t t = millis();
    while (!Serial && millis() - t < 3000) delay(10);  // așteptăm Serial Monitor, cel mult 3 s
    delay(200);
    Serial.println();
    Serial.printf("=== tema 02: STUDENT %s, DEVICE_ID %s ===\n", STUDENT, DEVICE_ID);

    wifi_connect();
    time_sync();

    // TODO 0 (a): creați cele două cozi (xQueueCreate): coada de alarme și
    //   coada de comenzi. Înainte de mqtt_start(): de acolo încolo pot sosi
    //   comenzi, iar command_received() le pune în coada de comenzi.
    g_alarm_queue = xQueueCreate(10, sizeof(AlarmEvent));
    g_command_queue = xQueueCreate(10, sizeof(CommandMsg));

    mqtt_start();

    // TODO 0 (b): porniți cele trei taskuri (xTaskCreate). După mqtt_start(),
    //   ca primele mesaje publicate să nu se piardă. Alegeți prioritățile
    //   între 2 și 19: peste loopTask (1), sub taskurile sistemului pentru
    //   evenimente și Wi-Fi (20, 23). Stiva, în octeți: cel puțin 4096.
    xTaskCreate(periodic_telemetry_task, "periodic",  STACK_SIZE_BYTES, NULL, PRIO_PERIODIC,  NULL);
    xTaskCreate(sporadic_alarm_task,     "sporadic",  STACK_SIZE_BYTES, NULL, PRIO_SPORADIC,  NULL);
    xTaskCreate(aperiodic_command_task,  "aperiodic", STACK_SIZE_BYTES, NULL, PRIO_APERIODIC, NULL);
}

void loop() {
    // Totul se face în taskuri: loop() nu are nimic de făcut.
    delay(1000);
}