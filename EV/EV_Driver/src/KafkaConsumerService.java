import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class KafkaConsumerService implements Runnable {
    private KafkaConsumer<String, String> consumer;
    private String driverId;

    public KafkaConsumerService(Config config, String driverId) {
        this.driverId = driverId;

        Properties props = new Properties();
        props.put("bootstrap.servers", config.getBootstrapServers());
        props.put("group.id", "driver-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumer = new KafkaConsumer<>(props);

        // Suscribirse a topic de notificaciones para este driver
        consumer.subscribe(Collections.singletonList("driver-notifications-" + driverId));
    }

    @Override
    public void run() {
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                records.forEach(record -> {
                    System.out.println("Notificación recibida: " + record.value());
                    // Aquí procesar mensajes de estado, autorización, etc.
                });
            }
        } finally {
            consumer.close();
        }
    }
}