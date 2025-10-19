import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import java.util.Properties;

public class KafkaProducerService {
    private KafkaProducer<String, String> producer;
    private String topicRequests = "charging-requests";

    public KafkaProducerService(Config config) {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getBootstrapServers());
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<>(props);
    }

    public void sendChargingRequest(String driverId, String cpId) {
        String message = String.format("REQUEST:%s:%s", driverId, cpId);
        ProducerRecord<String, String> record = new ProducerRecord<>(topicRequests, driverId, message);
        producer.send(record);
        System.out.println("Solicitud enviada: " + message);
    }

    public void close() {
        producer.close();
    }
}
