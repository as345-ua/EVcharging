package EV;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EV_CP_Engine - Charging Point Engine with Kafka communication
 * Receives authorization from Central and sends telemetry
 */
public class EV_CP_Engine {
    private String kafkaBootstrapServers;
    private String cpId;
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private AtomicBoolean running;
    private AtomicBoolean isCharging;
    private String currentDriverId;
    private double currentPowerKw;
    private double totalEnergyKwh;
    private Thread consumerThread;
    private Thread telemetryThread;
    
    // Kafka Topics
    private static final String TOPIC_AUTHORIZATIONS = "charging.authorizations";
    private static final String TOPIC_TELEMETRY = "charging.telemetry";
    private static final String TOPIC_CP_STATUS = "cp.status";
    
    public EV_CP_Engine(String kafkaBootstrapServers, String cpId) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.cpId = cpId;
        this.running = new AtomicBoolean(false);
        this.isCharging = new AtomicBoolean(false);
        this.totalEnergyKwh = 0.0;
    }
    
    /**
     * Initialize Kafka producer and consumer
     */
    public void start() {
        System.out.println("🚀 Starting CP Engine for CP-" + cpId);
        
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
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "cp-engine-" + cpId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consumerProps);
        
        // Subscribe to authorization topic
        consumer.subscribe(Collections.singletonList(TOPIC_AUTHORIZATIONS));
        
        running.set(true);
        
        // Send initial status
        sendStatusUpdate("AVAILABLE");
        
        System.out.println("✅ CP Engine started for CP-" + cpId);
        System.out.println("📡 Listening for authorizations on: " + TOPIC_AUTHORIZATIONS);
        System.out.println("📤 Publishing telemetry to: " + TOPIC_TELEMETRY);
        
        // Start consumer thread
        startConsumerThread();
        
        // Interactive menu
        runMenu();
    }
    
    /**
     * Start consumer thread to listen for authorizations
     */
    private void startConsumerThread() {
        consumerThread = new Thread(() -> {
            System.out.println("👂 Consumer thread started, waiting for authorization messages...");
            
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        String message = record.value();
                        System.out.println("\n📨 Received from Central: " + message);
                        handleAuthorizationMessage(message);
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
     * Handle authorization message from Central
     * Format: AUTH#<cpId>#<driverId>#<powerKw>
     */
    private void handleAuthorizationMessage(String message) {
        String[] parts = message.split("#");
        System.out.println("🔍 Processing authorization: " + message);
        if (parts.length < 2) {
            System.err.println("❌ Invalid message format");
            return;
        }
        
        String command = parts[0];
        
        try {
            switch (command) {
                case "AUTH":
                    if (parts.length >= 4) {
                        String targetCpId = parts[1];
                        
                        // Check if this message is for this CP
                        if (!targetCpId.equals(cpId)) {
                            return; // Ignore messages for other CPs
                        }
                        
                        String driverId = parts[2];
                        double powerKw = Double.parseDouble(parts[3]);
                        
                        System.out.println("\n✅ AUTHORIZATION RECEIVED!");
                        System.out.println("   Driver: " + driverId);
                        System.out.println("   Power: " + powerKw + " kW");
                        System.out.println("   Waiting for vehicle to plug in...");
                        
                        // Store authorization data
                        currentDriverId = driverId;
                        currentPowerKw = powerKw;
                        
                        // Auto-start charging (simulating plug-in)
                        Thread.sleep(2000); // Simulate plug-in delay
                        startCharging();
                    }
                    break;
                    
                case "STOP":
                    if (parts.length >= 2) {
                        String targetCpId = parts[1];
                        if (targetCpId.equals(cpId)) {
                            System.out.println("\n🛑 STOP command received from Central");
                            stopCharging();
                        }
                    }
                    break;
                    
                default:
                    System.out.println("⚠️  Unknown command: " + command);
                    break;
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling authorization: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Start charging session
     */
    private void startCharging() {
        if (isCharging.get()) {
            System.out.println("❌ Already charging!");
            return;
        }
        
        isCharging.set(true);
        totalEnergyKwh = 0.0;
        
        System.out.println("\n⚡ CHARGING STARTED");
        System.out.println("   Driver: " + currentDriverId);
        System.out.println("   Power: " + currentPowerKw + " kW");
        
        // Send status update
        sendStatusUpdate("CHARGING");
        
        // Send initial charging start message
        String startMsg = String.format("START#%s#%s#%.2f", cpId, currentDriverId, currentPowerKw);
        sendTelemetry(startMsg);
        
        // Start telemetry thread
        startTelemetryThread();
    }
    
    /**
     * Start telemetry thread - sends data every 1 second
     */
    private void startTelemetryThread() {
        telemetryThread = new Thread(() -> {
            System.out.println("📊 Telemetry thread started, sending updates every 1 second...");
            
            while (isCharging.get()) {
                try {
                    Thread.sleep(1000); // 1 second
                    
                    // Calculate energy (power * time in hours)
                    totalEnergyKwh += currentPowerKw / 3600.0;
                    
                    // Send telemetry
                    String telemetryMsg = String.format("TELEMETRY#%s#%s#%.4f#%.2f", 
                        cpId, currentDriverId, totalEnergyKwh, currentPowerKw);
                    sendTelemetry(telemetryMsg);
                    
                    System.out.printf("📊 Telemetry sent: %.4f kWh delivered%n", totalEnergyKwh);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            System.out.println("📊 Telemetry thread stopped");
        });
        telemetryThread.start();
    }
    
    /**
     * Stop charging session
     */
    private void stopCharging() {
        if (!isCharging.get()) {
            System.out.println("❌ Not charging!");
            return;
        }
        
        isCharging.set(false);
        
        // Stop telemetry thread
        if (telemetryThread != null && telemetryThread.isAlive()) {
            telemetryThread.interrupt();
        }
        
        System.out.println("\n🛑 CHARGING STOPPED");
        System.out.printf("   Total energy delivered: %.4f kWh%n", totalEnergyKwh);
        
        // Send final telemetry
        String stopMsg = String.format("STOP#%s#%s#%.4f", cpId, currentDriverId, totalEnergyKwh);
        sendTelemetry(stopMsg);
        
        // Send status update
        sendStatusUpdate("AVAILABLE");
        
        // Reset
        currentDriverId = null;
        currentPowerKw = 0.0;
        totalEnergyKwh = 0.0;
    }
    
    /**
     * Send telemetry to Kafka
     */
    private void sendTelemetry(String message) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_TELEMETRY, cpId, message);
            producer.send(record);
        } catch (Exception e) {
            System.err.println("❌ Error sending telemetry: " + e.getMessage());
        }
    }
    
    /**
     * Send status update to Kafka
     */
    private void sendStatusUpdate(String status) {
        try {
            String message = String.format("STATUS#%s#%s", cpId, status);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_CP_STATUS, cpId, message);
            producer.send(record);
            System.out.println("📤 Status update sent: " + status);
        } catch (Exception e) {
            System.err.println("❌ Error sending status: " + e.getMessage());
        }
    }
    
    /**
     * Interactive menu
     */
    private void runMenu() {
        Scanner scanner = new Scanner(System.in);
        
        while (running.get()) {
            System.out.println("\n╔════════════════════════════════════════╗");
            System.out.println("║   CP ENGINE - CP " + cpId + " (Kafka Mode)    ║");
            System.out.println("╚════════════════════════════════════════╝");
            System.out.println("Status: " + (isCharging.get() ? "🔵 CHARGING" : "🟢 AVAILABLE"));
            if (isCharging.get()) {
                System.out.printf("Driver: %s | Power: %.1f kW | Energy: %.4f kWh%n", 
                    currentDriverId, currentPowerKw, totalEnergyKwh);
            }
            System.out.println("────────────────────────────────────────");
            System.out.println("1. 🛑 Stop charging manually");
            System.out.println("2. 📊 Show current session info");
            System.out.println("3. 🔧 Simulate fault");
            System.out.println("4. ❌ Shutdown engine");
            System.out.println("════════════════════════════════════════");
            System.out.print("Choose option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    if (isCharging.get()) {
                        stopCharging();
                    } else {
                        System.out.println("❌ Not charging!");
                    }
                    break;
                    
                case "2":
                    showSessionInfo();
                    break;
                    
                case "3":
                    simulateFault();
                    break;
                    
                case "4":
                    System.out.println("\n🛑 Shutting down engine...");
                    if (isCharging.get()) {
                        stopCharging();
                    }
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
    
    private void showSessionInfo() {
        if (isCharging.get()) {
            System.out.println("\n📊 CURRENT SESSION INFO");
            System.out.println("───────────────────────");
            System.out.println("CP ID: " + cpId);
            System.out.println("Driver: " + currentDriverId);
            System.out.printf("Power: %.1f kW%n", currentPowerKw);
            System.out.printf("Energy delivered: %.4f kWh%n", totalEnergyKwh);
        } else {
            System.out.println("❌ No active charging session");
        }
    }
    
    private void simulateFault() {
        System.out.println("\n🚨 SIMULATING FAULT");
        if (isCharging.get()) {
            stopCharging();
        }
        sendStatusUpdate("BROKEN");
        System.out.println("🔴 CP marked as BROKEN");
    }
    
    /**
     * Stop the engine
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
            System.out.println("Usage: java EV.EV_CP_Engine <kafka_bootstrap_servers> <cp_id>");
            System.out.println("Example: java EV.EV_CP_Engine localhost:9092 1");
            return;
        }
        
        String kafkaServers = args[0];
        String cpId = args[1];
        
        EV_CP_Engine engine = new EV_CP_Engine(kafkaServers, cpId);
        
        Runtime.getRuntime().addShutdownHook(new Thread(engine::stop));
        
        engine.start();
    }
}
