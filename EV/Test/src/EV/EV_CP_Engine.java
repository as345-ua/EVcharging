package EV;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.awt.Color;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* EV_CP_Engine - Charging Point Engine with Kafka communication
* Receives authorization from Central and sends telemetry
* Integrated with CPEngineUI
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
   
   private CPEngineUI ui; // Task 2: UI reference

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
    * Set the UI for this engine
    */
   public void setUI(CPEngineUI ui) {
       this.ui = ui;
   }
  
   /**
    * Initialize Kafka producer and consumer
    */
   public void start() {
       log("🚀 Starting CP Engine for CP-" + cpId);
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
      
       log("✅ CP Engine started for CP-" + cpId);
       log("📡 Listening for authorizations on: " + TOPIC_AUTHORIZATIONS);
       log("📤 Publishing telemetry to: " + TOPIC_TELEMETRY);
       // Start consumer thread
       startConsumerThread();
       
       // Do NOT run menu, UI is in control
       // runMenu(); 
   }
  
   /**
    * Start consumer thread to listen for authorizations
    */
   private void startConsumerThread() {
       consumerThread = new Thread(() -> {
           log("👂 Consumer thread started, waiting for authorization messages...");
          
           while (running.get()) {
               try {
     
                   ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                  
                   for (ConsumerRecord<String, String> record : records) {
                       String message = record.value();
             
                       log("\n📨 Received from Central: " + message);
                       handleAuthorizationMessage(message);
                   }
               } catch (Exception e) {
                   if (running.get()) {
  
                       log("❌ Error polling messages: " + e.getMessage());
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
       log("🔍 Processing authorization: " + message);
       if (parts.length < 2) {
           log("❌ Invalid message format");
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
                      
                       log("\n✅ AUTHORIZATION RECEIVED!");
                       log("   Driver: " + driverId);
                       log("   Power: " + powerKw + " kW");
                       log("   Waiting for vehicle to plug in...");
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
                           log("\n🛑 STOP command received from Central");
                           stopCharging();
                       }
                   }
                   break;
               default:
                   log("⚠️  Unknown command: " + command);
                   break;
           }
       } catch (Exception e) {
           log("❌ Error handling authorization: " + e.getMessage());
           e.printStackTrace();
       }
   }
  
   /**
    * Start charging session
    */
   private void startCharging() {
       if (isCharging.get()) {
           log("❌ Already charging!");
           return;
       }
      
       isCharging.set(true);
       totalEnergyKwh = 0.0;
       log("\n⚡ CHARGING STARTED");
       log("   Driver: " + currentDriverId);
       log("   Power: " + currentPowerKw + " kW");
       
       if (ui != null) {
           ui.startCharging(currentDriverId, currentPowerKw);
       }
       
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
           log("📊 Telemetry thread started, sending updates every 1 second...");
           
           double assumedTotalEnergy = 50.0; // Assume 50 kWh is a "full" charge for progress bar
          
           while (isCharging.get()) {
               try {
  
                   Thread.sleep(1000); // 1 second
                  
                   // Calculate energy (power * time in hours)
                   totalEnergyKwh += currentPowerKw / 3600.0;
             
      
                   // Send telemetry
                   String telemetryMsg = String.format("TELEMETRY#%s#%s#%.4f#%.2f",
                       cpId, currentDriverId, totalEnergyKwh, currentPowerKw);
                   sendTelemetry(telemetryMsg);
       
                   // Don't log every single telemetry send, it's too noisy for UI
                   // log(String.format("📊 Telemetry sent: %.4f kWh delivered", totalEnergyKwh));
                   
                   if (ui != null) {
                       int progress = (int) ((totalEnergyKwh / assumedTotalEnergy) * 100);
                       ui.updateChargingProgress(totalEnergyKwh, progress);
                   }
                  
               } catch (InterruptedException e) {
                   Thread.currentThread().interrupt();
                   break;
               }
           }
          
           log("📊 Telemetry thread stopped");
       });
       telemetryThread.start();
   }
  
   /**
    * Stop charging session
    */
   public void stopCharging() {
       if (!isCharging.get()) {
           log("❌ Not charging!");
           return;
       }
      
       isCharging.set(false);
       // Stop telemetry thread
       if (telemetryThread != null && telemetryThread.isAlive()) {
           telemetryThread.interrupt();
       }
      
       log("\n🛑 CHARGING STOPPED");
       log(String.format("   Total energy delivered: %.4f kWh", totalEnergyKwh));
       
       if (ui != null) {
           ui.stopCharging();
       }
      
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
           log("❌ Error sending telemetry: " + e.getMessage());
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
           log("📤 Status update sent: " + status);
           
           if (ui != null) {
               Color color = Color.GRAY;
               if (status.equals("AVAILABLE")) color = new Color(0, 150, 0);
               else if (status.equals("CHARGING")) color = new Color(0, 100, 200);
               else if (status.equals("BROKEN")) color = Color.RED;
               else if (status.equals("OUT_OF_SERVICE")) color = Color.ORANGE;
               ui.updateStatus(status, color);
           }
       } catch (Exception e) {
           log("❌ Error sending status: " + e.getMessage());
       }
   }
  
   public void simulateFault() {
       log("\n🚨 SIMULATING FAULT");
       if (isCharging.get()) {
           stopCharging();
       }
       sendStatusUpdate("BROKEN");
       log("🔴 CP marked as BROKEN");
       if(ui != null) {
           ui.showFault();
       }
   }
   
   /**
    * Helper to log to UI if available, otherwise console
    */
   private void log(String message) {
       if (ui != null) {
           ui.appendLog(message);
       } else {
           System.out.println(message);
       }
   }
  
   /**
    * Stop the engine
    */
   public void stop() {
       if (!running.getAndSet(false)) {
           return; // Already stopped
       }
       
       log("🛑 Shutting down engine...");
       if (isCharging.get()) {
           stopCharging();
       }
       
       // First stop the consumer thread
       if (consumerThread != null && consumerThread.isAlive()) {
           consumerThread.interrupt();
           try {
               consumerThread.join(2000); // Wait up to 2 seconds
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
       
       log("✅ CP Engine stopped.");
       
       // Dispose the UI frame
       if (ui != null) {
           ui.dispose();
       }
   }
}