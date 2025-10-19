import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DriverApp {
    private static KafkaProducerService producer;
    private static KafkaConsumerService consumer;
    private static String driverId;
    private static String requestsFile;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java DriverApp <bootstrap_servers> <driver_id> [requests_file]");
            return;
        }

        String bootstrapServers = args[0];
        driverId = args[1];
        requestsFile = args.length > 2 ? args[2] : null;

        Config config = new Config(bootstrapServers, driverId);

        producer = new KafkaProducerService(config);
        consumer = new KafkaConsumerService(config, driverId);

        // Iniciar consumidor en hilo separado
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(consumer);

        // Menú interactivo o lectura de archivo
        if (requestsFile != null) {
            processFileRequests();
        } else {
            startInteractiveMode();
        }

        executor.shutdown();
        producer.close();
    }

    private static void processFileRequests() {
        try (BufferedReader br = new BufferedReader(new FileReader(requestsFile))) {
            String cpId;
            while ((cpId = br.readLine()) != null) {
                if (!cpId.trim().isEmpty()) {
                    System.out.println("Solicitando carga en CP: " + cpId);
                    producer.sendChargingRequest(driverId, cpId.trim());
                    // Esperar 4 segundos entre solicitudes
                    Thread.sleep(4000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startInteractiveMode() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- EV Driver App ---");
            System.out.println("1. Solicitar carga");
            System.out.println("2. Salir");
            System.out.print("Opción: ");

            String option = scanner.nextLine();
            if ("2".equals(option)) break;

            if ("1".equals(option)) {
                System.out.print("ID del Punto de Carga: ");
                String cpId = scanner.nextLine();
                producer.sendChargingRequest(driverId, cpId);
            }
        }
        scanner.close();
    }
}
