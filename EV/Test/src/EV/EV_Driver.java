package EV;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EV_Driver - Driver application with Kafka communication
 * Requests charging services and receives notifications
 */
public class EV_Driver {
    private String kafkaBootstrapServers;
    private String driverId;
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private AtomicBoolean running;
    private Thread consumerThread;
    private List<String> requestQueue;
    private int currentRequestIndex;
    
    // Kafka Topics
    private static final String TOPIC_REQUESTS = "charging.requests";
    private static final String TOPIC_NOTIFICATIONS = "charging.notifications";
    private static final String TOPIC_TELEMETRY = "charging.telemetry";
    
    public EV_Driver(String kafkaBootstrapServers, String driverId) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.driverId = driverId;
        this.running = new AtomicBoolean(false);
        this.requestQueue = new ArrayList<>();
        this.currentRequestIndex = 0;
    }
    
    /**
     * Initialize Kafka producer and consumer
     */
    public void start() {
        System.out.println("🚗 Starting Driver application for Driver-" + driverId);
        
        // Create Kafka producer
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "1");
        producer = new KafkaProducer<>(producerProps);
        
        // Create Kafka consumer
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "driver-" + driverId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consumerProps);
        
        // Subscribe to notifications and telemetry
        consumer.subscribe(Arrays.asList(TOPIC_NOTIFICATIONS, TOPIC_TELEMETRY));
        
        running.set(true);
        
        System.out.println("✅ Driver application started");
        System.out.println("📡 Listening for notifications on: " + TOPIC_NOTIFICATIONS);
        System.out.println("📊 Listening for telemetry on: " + TOPIC_TELEMETRY);
        System.out.println("📤 Publishing requests to: " + TOPIC_REQUESTS);
        
        // Start consumer thread
        startConsumerThread();
        
        // Interactive menu
        runMenu();
    }
    
    /**
     * Start consumer thread to listen for notifications
     */
    private void startConsumerThread() {
        consumerThread = new Thread(() -> {
            System.out.println("👂 Consumer thread started, waiting for messages...\n");
            
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        String message = record.value();
                        handleMessage(message, record.topic());
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        System.err.println("❌ Error polling messages: " + e.getMessage());
                    }
                }
            }
        });
        consumerThread.start();
    }
    
    /**
     * Handle messages from Kafka
     */
    private void handleMessage(String message, String topic) {
        String[] parts = message.split("#");
        
        if (parts.length < 2) {
            return;
        }
        
        String command = parts[0];
        
        try {
            if (topic.equals(TOPIC_NOTIFICATIONS)) {
                handleNotification(command, parts);
            } else if (topic.equals(TOPIC_TELEMETRY)) {
                handleTelemetry(command, parts);
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling message: " + e.getMessage());
        }
    }
    
    /**
     * Handle notification messages
     */
    private void handleNotification(String command, String[] parts) {
        switch (command) {
            case "AUTH_SUCCESS":
                if (parts.length >= 3 && parts[1].equals(driverId)) {
                    String cpId = parts[2];
                    System.out.println("\n✅ AUTHORIZATION GRANTED!");
                    System.out.println("   Charging Point: CP-" + cpId);
                    System.out.println("   Please plug in your vehicle...");
                }
                break;
                
            case "AUTH_DENIED":
                if (parts.length >= 3 && parts[1].equals(driverId)) {
                    String reason = parts[2];
                    System.out.println("\n❌ AUTHORIZATION DENIED!");
                    System.out.println("   Reason: " + reason);
                }
                break;
                
            case "CHARGING_STARTED":
                if (parts.length >= 2 && parts[1].equals(driverId)) {
                    System.out.println("\n⚡ CHARGING STARTED!");
                    System.out.println("   Your vehicle is now charging...");
                }
                break;
                
            case "CHARGING_STOPPED":
                if (parts.length >= 4 && parts[1].equals(driverId)) {
                    double totalKwh = Double.parseDouble(parts[2]);
                    double totalCost = Double.parseDouble(parts[3]);
                    System.out.println("\n🛑 CHARGING COMPLETED!");
                    System.out.println("   ───────────────────────");
                    System.out.printf("   Energy delivered: %.4f kWh%n", totalKwh);
                    System.out.printf("   Total cost: €%.2f%n", totalCost);
                    System.out.println("   ───────────────────────");
                    System.out.println("   Thank you for using EVCharging!");
                    
                    // If there are more requests in queue, wait and request next
                    if (currentRequestIndex < requestQueue.size() - 1) {
                        scheduleNextRequest();
                    }
                }
                break;
        }
    }
    
    /**
     * Handle telemetry messages
     */
    private void handleTelemetry(String command, String[] parts) {
        if (command.equals("TELEMETRY") && parts.length >= 4) {
            String cpId = parts[1];
            String targetDriverId = parts[2];
            
            // Only show telemetry for this driver
            if (targetDriverId.equals(driverId)) {
                double energyKwh = Double.parseDouble(parts[3]);
                double powerKw = Double.parseDouble(parts[4]);
                
                System.out.printf("\r⚡ Charging: %.4f kWh | Power: %.1f kW", energyKwh, powerKw);
            }
        } else if (command.equals("START") && parts.length >= 3) {
            String cpId = parts[1];
            String targetDriverId = parts[2];
            
            if (targetDriverId.equals(driverId)) {
                System.out.println("\n⚡ Charging session initiated at CP-" + cpId);
            }
        }
    }
    
    /**
     * Schedule next request after 4 seconds
     */
    private void scheduleNextRequest() {
        new Thread(() -> {
            try {
                System.out.println("\n⏳ Waiting 4 seconds before next request...");
                Thread.sleep(4000);
                currentRequestIndex++;
                String nextCpId = requestQueue.get(currentRequestIndex);
                System.out.println("\n📋 Processing next request: CP-" + nextCpId);
                requestCharging(nextCpId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * Request charging at a specific CP
     */
    private void requestCharging(String cpId) {
        String message = String.format("REQUEST#%s#%s", driverId, cpId);
        
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_REQUESTS, driverId, message);
            producer.send(record);
            producer.flush();
            System.out.println("\n📤 Charging request sent!");
            System.out.println("   Charging Point: CP-" + cpId);
            System.out.println("   Waiting for authorization...");
            
        } catch (Exception e) {
            System.err.println("❌ Error sending request: " + e.getMessage());
        }
    }
    
    /**
     * Load requests from file
     */
    private void loadRequestsFromFile(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            requestQueue.clear();
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    requestQueue.add(line);
                }
            }
            
            System.out.println("✅ Loaded " + requestQueue.size() + " requests from file:");
            for (int i = 0; i < requestQueue.size(); i++) {
                System.out.println("   " + (i + 1) + ". CP-" + requestQueue.get(i));
            }
            
        } catch (FileNotFoundException e) {
            System.err.println("❌ File not found: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Error reading file: " + e.getMessage());
        }
    }
    
    /**
     * Interactive menu
     */
    private void runMenu() {
        Scanner scanner = new Scanner(System.in);
        
        while (running.get()) {
            System.out.println("\n╔══════════════════════════════════════════╗");
            System.out.println("║     DRIVER APP - Driver " + driverId + "            ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.println("1. 📍 Request charging at specific CP");
            System.out.println("2. 📁 Load requests from file");
            System.out.println("3. ▶️  Start processing request queue");
            System.out.println("4. 📋 Show request queue");
            System.out.println("5. ❌ Exit");
            System.out.println("══════════════════════════════════════════");
            System.out.print("Choose option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    System.out.print("\nEnter CP ID (e.g., 1, 2, 3...): ");
                    String cpId = scanner.nextLine().trim();
                    if (!cpId.isEmpty()) {
                        requestCharging(cpId);
                    }
                    break;
                    
                case "2":
                    System.out.print("\nEnter filename (e.g., requests.txt): ");
                    String filename = scanner.nextLine().trim();
                    if (!filename.isEmpty()) {
                        loadRequestsFromFile(filename);
                    }
                    break;
                    
                case "3":
                    if (requestQueue.isEmpty()) {
                        System.out.println("❌ Request queue is empty! Load file first.");
                    } else {
                        currentRequestIndex = 0;
                        System.out.println("\n▶️  Starting request queue processing...");
                        requestCharging(requestQueue.get(currentRequestIndex));
                    }
                    break;
                    
                case "4":
                    if (requestQueue.isEmpty()) {
                        System.out.println("\n📋 Request queue is empty");
                    } else {
                        System.out.println("\n📋 REQUEST QUEUE (" + requestQueue.size() + " items):");
                        for (int i = 0; i < requestQueue.size(); i++) {
                            String marker = (i == currentRequestIndex) ? " ← Current" : "";
                            System.out.println("   " + (i + 1) + ". CP-" + requestQueue.get(i) + marker);
                        }
                    }
                    break;
                    
                case "5":
                    System.out.println("\n👋 Exiting driver application...");
                    stop();
                    scanner.close();
                    return;
                    
                default:
                    System.out.println("❌ Invalid option!");
                    break;
            }
        }
        
        scanner.close();
    }
    
    /**
     * Stop the driver application
     */
public void stop() {
    running.set(false);
    
    // First stop the consumer thread
    if (consumerThread != null && consumerThread.isAlive()) {
        consumerThread.interrupt();
        try {
            consumerThread.join(5000); // Wait up to 5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // Then close Kafka resources
    if (consumer != null) {
        consumer.close();
    }
    if (producer != null) {
        producer.close();
    }
}
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java EV.EV_Driver <kafka_bootstrap_servers> <driver_id>");
            System.out.println("Example: java EV.EV_Driver localhost:9092 1");
            return;
        }
        
        String kafkaServers = args[0];
        String driverId = args[1];
        
        EV_Driver driver = new EV_Driver(kafkaServers, driverId);
        
        Runtime.getRuntime().addShutdownHook(new Thread(driver::stop));
        
        driver.start();
    }
}
