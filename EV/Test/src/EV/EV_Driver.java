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
* Task 2: Integrated with DriverUI
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
   
   private DriverUI ui; // Task 2: UI Reference

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
    * Start the driver app and link it to the UI
    */
   public void startWithUI(DriverUI ui) {
       this.ui = ui;
       log("🚗 Starting Driver application for Driver-" + driverId);
       
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
      
       log("✅ Driver application started");
       log("📡 Listening for notifications on: " + TOPIC_NOTIFICATIONS);
       log("📊 Listening for telemetry on: " + TOPIC_TELEMETRY);
       log("📤 Publishing requests to: " + TOPIC_REQUESTS);
       
       if (ui != null) {
           ui.updateStatus("🟢 Connected");
       }
      
       // Start consumer thread
       startConsumerThread();
   }
  
   /**
    * Start consumer thread to listen for notifications
    */
   private void startConsumerThread() {
       consumerThread = new Thread(() -> {
           log("👂 Consumer thread started, waiting for messages...\n");
          
           while (running.get()) {
               try {
      
                   ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                  
                   for (ConsumerRecord<String, String> record : records) {
                       String message = record.value();
              
                       handleMessage(message, record.topic());
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
           log("❌ Error handling message: " + e.getMessage());
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
                   log("\n✅ AUTHORIZATION GRANTED!");
                   log("   Charging Point: CP-" + cpId);
                   log("   Please plug in your vehicle...");
                   if (ui != null) ui.updateStatus("🔵 Authorized. Waiting for charge...");
               }
               break;
           case "AUTH_DENIED":
               if (parts.length >= 3 && parts[1].equals(driverId)) {
                   String reason = parts[2];
                   log("\n❌ AUTHORIZATION DENIED!");
                   log("   Reason: " + reason);
                   if (ui != null) ui.updateStatus("🔴 Denied: " + reason);
               }
               break;
           case "CHARGING_STARTED":
               if (parts.length >= 3 && parts[1].equals(driverId)) {
                   String cpId = parts[2];
                   log("\n⚡ CHARGING STARTED!");
                   log("   Your vehicle is now charging at CP-" + cpId);
                   if (ui != null) {
                       ui.resetChargingInfo();
                       ui.updateStatus("⚡ CHARGING at CP-" + cpId);
                   }
               }
               break;
           case "CHARGING_STOPPED":
               if (parts.length >= 4 && parts[1].equals(driverId)) {
                   double totalKwh = Double.parseDouble(parts[2]);
                   double totalCost = Double.parseDouble(parts[3]);
                   log("\n🛑 CHARGING COMPLETED!");
                   log("   ───────────────────────");
                   log(String.format("   Energy delivered: %.4f kWh", totalKwh));
                   log(String.format("   Total cost: €%.2f", totalCost));
                   log("   ───────────────────────");
                   log("   Thank you for using EVCharging!");
                   
                   if (ui != null) {
                       ui.updateStatus("🟢 Charge Complete! Available.");
                       ui.updateFinalTicket(totalKwh, totalCost);
                   }
                   
                   // If there are more requests in queue, wait and request next
                   if (currentRequestIndex < requestQueue.size() - 1) {
                       scheduleNextRequest();
                   } else {
                       if (ui != null) ui.enableQueueButton();
                   }
               }
               break;
       }
   }
  
   /**
    * Handle telemetry messages
    */
   private void handleTelemetry(String command, String[] parts) {
       if (command.equals("TELEMETRY") && parts.length >= 5) {
           String cpId = parts[1];
           String targetDriverId = parts[2];
          
           // Only show telemetry for this driver
           if (targetDriverId.equals(driverId)) {
               double energyKwh = Double.parseDouble(parts[3]);
               double powerKw = Double.parseDouble(parts[4]);
              
               // Don't log to console, too noisy
               // System.out.printf("\r⚡ Charging: %.4f kWh | Power: %.1f kW", energyKwh, powerKw);
               
               if (ui != null) {
                   double assumedTotalEnergy = 50.0; // Assume 50 kWh for 100%
                   int progress = (int)((energyKwh / assumedTotalEnergy) * 100);
                   ui.updateChargingInfo(energyKwh, progress);
               }
           }
       } else if (command.equals("START") && parts.length >= 3) {
           String cpId = parts[1];
           String targetDriverId = parts[2];
          
           if (targetDriverId.equals(driverId)) {
               log("\n⚡ Charging session initiated at CP-" + cpId);
           }
       }
   }
  
   /**
    * Schedule next request after 4 seconds
    */
   private void scheduleNextRequest() {
       new Thread(() -> {
           try {
               log("\n⏳ Waiting 4 seconds before next request...");
               Thread.sleep(4000);
       
               currentRequestIndex++;
               String nextCpId = requestQueue.get(currentRequestIndex);
               log("\n📋 Processing next request: CP-" + nextCpId);
               requestChargingInternal(nextCpId);
           } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
        
           }
       }).start();
   }
  
   /**
    * Request charging at a specific CP (Internal)
    */
   private void requestChargingInternal(String cpId) {
       String message = String.format("REQUEST#%s#%s", driverId, cpId);
       try {
           ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_REQUESTS, driverId, message);
           producer.send(record);
           producer.flush();
           log("\n📤 Charging request sent!");
           log("   Charging Point: CP-" + cpId);
           log("   Waiting for authorization...");
           if (ui != null) ui.updateStatus("🟡 Requesting CP-" + cpId + "...");
       } catch (Exception e) {
           log("❌ Error sending request: " + e.getMessage());
           if (ui != null) ui.updateStatus("🔴 Error sending request");
       }
   }
   
   /**
    * Public method for UI to call
    */
   public void requestChargingManual(String cpId) {
       requestChargingInternal(cpId);
   }
  
   /**
    * Load requests from file
    */
   public void loadRequestsFromFile(String filename) {
       try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
           String line;
           requestQueue.clear();
          
           while ((line = reader.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty()) {
                   requestQueue.add(line);
               }
           }
          
           log("✅ Loaded " + requestQueue.size() + " requests from file:");
           for (int i = 0; i < requestQueue.size(); i++) {
               log("   " + (i + 1) + ". CP-" + requestQueue.get(i));
           }
          
       } catch (FileNotFoundException e) {
           log("❌ File not found: " + filename);
       } catch (IOException e) {
           log("❌ Error reading file: " + e.getMessage());
       }
   }
   
   /**
    * Public method for UI to start queue
    */
   public void startRequestQueue() {
       if (requestQueue.isEmpty()) {
           log("❌ Request queue is empty! Load file first.");
       } else {
           currentRequestIndex = 0;
           log("\n▶️  Starting request queue processing...");
           requestChargingInternal(requestQueue.get(currentRequestIndex));
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
    * Stop the driver application
    */
   public void stop() {
       if (!running.getAndSet(false)) {
           return; // Already stopped
       }
       
       log("🛑 Shutting down driver app...");

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
       
       log("✅ Driver app stopped.");
       
       // Dispose the UI frame
       if (ui != null) {
           ui.dispose();
       }
   }
}