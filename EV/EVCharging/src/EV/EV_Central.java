package EV;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.net.*;
import java.io.*;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class EV_Central {
    private ServerSocket serverSocket;
    private int port;
    private boolean running;
    // Vector to store ALL charging points from JSON
    private Vector<ChargingPoint> allChargingPoints;
    // Vector to store AUTHENTICATED (connected) charging points
    private Vector<ChargingPoint> authenticatedCPs;
    // Vector to store ALL drivers from JSON
    private Vector<Driver> allDrivers;
    // Vector to store AUTHENTICATED (connected) drivers
    private Vector<Driver> authenticatedDrivers;
    // Map to keep track of client sockets
    private Map<String, Socket> connectedClients;
    private Gson gson;
    private String jsonFilePath;
    private String driversJsonFilePath;
    
    // Kafka
    private String kafkaBootstrapServers;
    private KafkaProducer<String, String> kafkaProducer;
    private KafkaConsumer<String, String> kafkaConsumer;
    private Thread kafkaConsumerThread;
    
    // Kafka Topics
    private static final String TOPIC_REQUESTS = "charging.requests";
    private static final String TOPIC_AUTHORIZATIONS = "charging.authorizations";
    private static final String TOPIC_TELEMETRY = "charging.telemetry";
    private static final String TOPIC_NOTIFICATIONS = "charging.notifications";
    private static final String TOPIC_CP_STATUS = "cp.status";

    // Task 4: UI Reference
    private CentralUI ui;

    public EV_Central(int port, String jsonFilePath, String driversJsonFilePath, String kafkaBootstrapServers) {
        this.port = port;
        this.jsonFilePath = jsonFilePath;
        this.driversJsonFilePath = driversJsonFilePath;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.allChargingPoints = new Vector<>();
        this.authenticatedCPs = new Vector<>();
        this.allDrivers = new Vector<>();
        this.authenticatedDrivers = new Vector<>();
        this.connectedClients = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.running = false;
    }

    // Task 4: Method to link UI
    public void setUI(CentralUI ui) {
        this.ui = ui;
    }

    // Task 4: Helper for logging to console and UI
    private void log(String message) {
        System.out.println(message);
        if (ui != null) {
            ui.appendLog(message);
        }
    }

    // Task 4: Helper for logging errors to console and UI
    private void logError(String message) {
        System.err.println(message);
        if (ui != null) {
            ui.appendLog("[ERROR] " + message);
        }
    }

    /**
     * Load all charging points from JSON file into allChargingPoints vector
     */
    private void loadChargingPointsFromJSON() {
        log("Loading charging points from: " + jsonFilePath);
        try (FileReader reader = new FileReader(jsonFilePath)) {
            Type listType = new TypeToken<ArrayList<ChargingPoint>>() {}.getType();
            List<ChargingPoint> cpList = gson.fromJson(reader, listType);

            allChargingPoints.clear();
            allChargingPoints.addAll(cpList);

            log("[INFO] Loaded " + allChargingPoints.size() + " charging points from JSON.");
            
            // Task 4: Populate UI table on startup
            if (ui != null) {
                for (ChargingPoint cp : allChargingPoints) {
                    ui.addOrUpdateCP(cp.getId(),
                            String.format("(%.1f, %.1f)", cp.getPosX(), cp.getPosY()),
                            cp.getState(),
                            cp.getPriceEurKwh(),
                            cp.getCurrentPowerKw(),
                            cp.getConnectedVehicleId(),
                            cp.getTotalEnergySuppliedKwh(),
                            cp.getCurrentChargingCost());
                }
            }
            log("");
        } catch (FileNotFoundException e) {
            logError("JSON file not found: " + jsonFilePath);
            logError("   Make sure charging_points.json is in the correct location");
        } catch (Exception e) {
            logError("Error loading JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load all drivers from JSON file into allDrivers vector
     */
    private void loadDriversFromJSON() {
        log("Loading drivers from: " + driversJsonFilePath);
        try (FileReader reader = new FileReader(driversJsonFilePath)) {
            Type listType = new TypeToken<ArrayList<Driver>>() {}.getType();
            List<Driver> driverList = gson.fromJson(reader, listType);

            allDrivers.clear();
            allDrivers.addAll(driverList);

            log("[INFO] Loaded " + allDrivers.size() + " drivers from JSON.");
            for (Driver driver : allDrivers) {
                System.out.printf("   - Driver ID: %s | Name: %s | Email: %s%n",
                        driver.getId(), driver.getName(), driver.getGmail());
            }
            log("");

            // Task 4: Update driver count label
            if (ui != null) {
                ui.updateDriverCount(allDrivers.size());
            }
        } catch (FileNotFoundException e) {
            logError("Drivers JSON file not found: " + driversJsonFilePath);
            logError("   Make sure DR.json is in the correct location");
        } catch (Exception e) {
            logError("Error loading drivers JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * SAVE all charging points to JSON file (PERSIST CHANGES)
     */
    private synchronized void saveChargingPointsToJSON() {
        try (FileWriter writer = new FileWriter(jsonFilePath)) {
            gson.toJson(allChargingPoints, writer);
            log("[INFO] Database updated: charging_points.json saved");
        } catch (IOException e) {
            logError("Error saving to JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update a charging point in the vector AND save to JSON
     */
    private synchronized void updateChargingPoint(ChargingPoint updatedCP) {
        boolean found = false;
        // Update in main vector
        for (int i = 0; i < allChargingPoints.size(); i++) {
            if (allChargingPoints.get(i).getId().equals(updatedCP.getId())) {
                allChargingPoints.set(i, updatedCP);
                found = true;
                break;
            }
        }
        // If not found (e.g., new CP), add it
        if (!found) {
            allChargingPoints.add(updatedCP);
            log("[INFO] New CP-" + updatedCP.getId() + " added to database.");
        }

        // Update in authenticated vector if exists
        for (int i = 0; i < authenticatedCPs.size(); i++) {
            if (authenticatedCPs.get(i).getId().equals(updatedCP.getId())) {
                authenticatedCPs.set(i, updatedCP);
                break;
            }
        }

        // Save to JSON file
        saveChargingPointsToJSON();

        // Task 4: Update UI Table
        if (ui != null) {
            ui.addOrUpdateCP(updatedCP.getId(),
                    String.format("(%.1f, %.1f)", updatedCP.getPosX(), updatedCP.getPosY()),
                    updatedCP.getState(),
                    updatedCP.getPriceEurKwh(),
                    updatedCP.getCurrentPowerKw(),
                    updatedCP.getConnectedVehicleId(),
                    updatedCP.getTotalEnergySuppliedKwh(),
                    updatedCP.getCurrentChargingCost());
        }
    }

    /**
     * Authenticate a charging point by ID
     * Returns the ChargingPoint if found in allChargingPoints, null otherwise
     */
    private ChargingPoint authenticateChargingPoint(String cpId) {
        for (ChargingPoint cp : allChargingPoints) {
            if (cp.getId().equals(cpId)) {
                return cp;
            }
        }
        return null;
    }

    /**
     * Authenticate a driver by ID
     * Returns the Driver if found in allDrivers, null otherwise
     */
    private Driver authenticateDriver(String driverId) {
        for (Driver driver : allDrivers) {
            if (driver.getId().equals(driverId)) {
                return driver;
            }
        }
        return null;
    }

    /**
     * Get a charging point by ID
     */
    private ChargingPoint getChargingPointById(String cpId) {
        for (ChargingPoint cp : allChargingPoints) {
            if (cp.getId().equals(cpId)) {
                return cp;
            }
        }
        return null;
    }

    /**
     * Initialize Kafka producer and consumer
     */
    private void initializeKafka() {
        log("Initializing Kafka...");
        // Producer configuration
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "1");
        kafkaProducer = new KafkaProducer<>(producerProps);

        // Consumer configuration
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "central-server");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaConsumer = new KafkaConsumer<>(consumerProps);
        
        // Subscribe to topics
        kafkaConsumer.subscribe(Arrays.asList(TOPIC_REQUESTS, TOPIC_TELEMETRY, TOPIC_CP_STATUS));

        log("[INFO] Kafka initialized");
        log("   Listening on: " + TOPIC_REQUESTS + ", " + TOPIC_TELEMETRY + ", " + TOPIC_CP_STATUS);
        log("   Publishing to: " + TOPIC_AUTHORIZATIONS + ", " + TOPIC_NOTIFICATIONS);
    }

    /**
     * Start Kafka consumer thread
     */
    private void startKafkaConsumerThread() {
        kafkaConsumerThread = new Thread(() -> {
            log("Kafka consumer thread started\n");

            while (running) {
                try {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));

                    for (ConsumerRecord<String, String> record : records) {
                        String message = record.value();
                        handleKafkaMessage(message, record.topic());
                    }
                } catch (Exception e) {
                    if (running) {
                        logError("Kafka consumer error: " + e.getMessage());
                        // Task 3: Add stack trace for better debugging
                        e.printStackTrace();
                    }
                }
            }
        });
        kafkaConsumerThread.start();
    }

    /**
     * Handle messages from Kafka
     */
    private void handleKafkaMessage(String message, String topic) {
        String[] parts = message.split("#");
        log("Received Kafka message on topic " + topic + ": " + message);
        if (parts.length < 2) {
            return;
        }

        String command = parts[0];
        try {
            if (topic.equals(TOPIC_REQUESTS)) {
                // Handle charging request from driver
                handleChargingRequest(parts);
            } else if (topic.equals(TOPIC_TELEMETRY)) {
                // Handle telemetry from CP
                handleTelemetry(parts);
            } else if (topic.equals(TOPIC_CP_STATUS)) {
                // Handle status update from CP
                handleCPStatus(parts);
            }
        } catch (Exception e) {
            logError("Error handling Kafka message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle charging request from driver
     */
    private void handleChargingRequest(String[] parts) {
        if (parts.length < 3) {
            return;
        }

        String driverId = parts[1];
        String cpId = parts[2];
        log("\nCHARGING REQUEST received");
        log("   Driver: " + driverId);
        log("   Requested CP: " + cpId);
        
        // Validate driver
        Driver driver = authenticateDriver(driverId);
        if (driver == null) {
            logError("Driver not found in database");
            sendNotification(driverId, "AUTH_DENIED#" + driverId + "#Driver not registered");
            return;
        }

        // Validate CP
        ChargingPoint cp = getChargingPointById(cpId);
        if (cp == null) {
            logError("Charging Point not found");
            sendNotification(driverId, "AUTH_DENIED#" + driverId + "#Charging Point not found");
            return;
        }

        // Check if CP is available
        if (!"AVAILABLE".equals(cp.getState())) {
            logError("CP not available. Current state: " + cp.getState());
            sendNotification(driverId, "AUTH_DENIED#" + driverId + "#CP not available");
            return;
        }

        // Authorization granted
        log("[INFO] Authorization GRANTED");
        // Send authorization to CP
        double powerKw = 50.0; // Default power
        String authMessage = String.format("AUTH#%s#%s#%.2f", cpId, driverId, powerKw);
        sendToKafka(TOPIC_AUTHORIZATIONS, cpId, authMessage);
        // Notify driver
        sendNotification(driverId, "AUTH_SUCCESS#" + driverId + "#" + cpId);
        log("Authorization sent to CP-" + cpId + " and Driver-" + driverId);
    }

    /**
     * Handle telemetry from CP
     */
    private void handleTelemetry(String[] parts) {
        String command = parts[0];
        if (command.equals("START") && parts.length >= 4) {
            String cpId = parts[1];
            String driverId = parts[2];
            double powerKw = Double.parseDouble(parts[3]);

            log("CHARGING STARTED: CP-" + cpId + " -> Driver-" + driverId);
            // Update CP in database
            ChargingPoint cp = getChargingPointById(cpId);
            if (cp != null) {
                cp.setState("CHARGING");
                cp.setConnectedVehicleId(driverId);
                cp.setCurrentPowerKw(powerKw);
                cp.setLastSeen(new java.util.Date().toString());
                updateChargingPoint(cp);
            }

            // Notify driver
            sendNotification(driverId, "CHARGING_STARTED#" + driverId + "#" + cpId);
        } else if (command.equals("TELEMETRY") && parts.length >= 5) {
            String cpId = parts[1];
            String driverId = parts[2];
            double energyKwh = Double.parseDouble(parts[3]);
            double powerKw = Double.parseDouble(parts[4]);
            // Update CP in database
            ChargingPoint cp = getChargingPointById(cpId);
            if (cp != null) {
                cp.setTotalEnergySuppliedKwh(energyKwh);
                double cost = energyKwh * cp.getPriceEurKwh();
                cp.setCurrentChargingCost(cost);
                cp.setLastSeen(new java.util.Date().toString());
                updateChargingPoint(cp);
                System.out.printf("   [INFO] CP-%s telemetry: %.4f kWh (%.2f EUR)%n", cpId, energyKwh, cost);
            }

        } else if (command.equals("STOP") && parts.length >= 4) {
            String cpId = parts[1];
            String driverId = parts[2];
            double totalEnergyKwh = Double.parseDouble(parts[3]);

            log("CHARGING STOPPED: CP-" + cpId);
            // Update CP in database
            ChargingPoint cp = getChargingPointById(cpId);
            if (cp != null) {
                double totalCost = totalEnergyKwh * cp.getPriceEurKwh();
                cp.setState("AVAILABLE");
                cp.setConnectedVehicleId(null);
                cp.setCurrentPowerKw(0.0);
                cp.setTotalEnergySuppliedKwh(0.0);
                cp.setCurrentChargingCost(0.0);
                cp.setLastSeen(new java.util.Date().toString());
                updateChargingPoint(cp);
                
                // Send ticket to driver
                String ticketMsg = String.format("CHARGING_STOPPED#%s#%.4f#%.2f",
                        driverId, totalEnergyKwh, totalCost);
                sendNotification(driverId, ticketMsg);

                log(String.format("   Ticket sent to Driver-%s: %.4f kWh, %.2f EUR%n",
                        driverId, totalEnergyKwh, totalCost));
            }
        }
    }

    /**
     * Handle status update from CP
     */
    private void handleCPStatus(String[] parts) {
        if (parts.length < 3) {
            return;
        }

        String cpId = parts[1];
        String status = parts[2];
        ChargingPoint cp = getChargingPointById(cpId);

        // Task 1: If CP is not found, REJECT IT
        if (cp == null) {
            logError("[WARN] Received status from unknown Kafka CP: " + cpId + ". Ignoring.");
            return; // Do not create new entry
        }

        // Task 3: Update lastSeen and status
        cp.setState(status);
        cp.setLastSeen(new java.util.Date().toString());
        updateChargingPoint(cp);
        log("[INFO] CP-" + cpId + " status updated via Kafka: " + status);
    }

    /**
     * Send message to Kafka topic
     */
    private void sendToKafka(String topic, String key, String message) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message);
            kafkaProducer.send(record);
        } catch (Exception e) {
            logError("Error sending to Kafka: " + e.getMessage());
        }
    }

    /**
     * Send notification to driver
     */
    private void sendNotification(String driverId, String message) {
        sendToKafka(TOPIC_NOTIFICATIONS, driverId, message);
    }

    /**
     * Auto-save thread - saves database every 30 seconds
     */
    private void startAutoSaveThread() {
        Thread autoSaveThread = new Thread(() -> {
            log("Auto-save enabled: Database will be saved every 30 seconds");
            while (running) {
                try {
                    Thread.sleep(30000); // 30 seconds
                    if (running) {
                        log("\nAuto-save: Saving database...");
                        saveChargingPointsToJSON();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        autoSaveThread.setDaemon(true);
        autoSaveThread.start();
    }

    /**
     * Start the central server
     */
    public void start() {
        // 1. Load charging points from JSON
        loadChargingPointsFromJSON();
        if (allChargingPoints.isEmpty()) {
            logError("[WARN] No charging points loaded. Database is empty.");
        }

        // 2. Load drivers from JSON
        loadDriversFromJSON();
        if (allDrivers.isEmpty()) {
            logError("[WARN] No drivers loaded.");
        }

        // 3. Initialize Kafka
        initializeKafka();
        running = true;

        // 4. Start Kafka consumer thread
        startKafkaConsumerThread();
        
        // 5. Start server socket
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            log("CENTRAL SERVER started on port " + port);
            log("Kafka connected: " + kafkaBootstrapServers);
            log("Waiting for socket connections...\n");

            // Start auto-save thread
            startAutoSaveThread();
            // Accept connections
            while (running) {
                Socket clientSocket = serverSocket.accept();
                log("New socket connection from: " + clientSocket.getInetAddress());
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            if (running) {
                logError("Server error: " + e.getMessage());
            }
        }
    }

    // Task 4: displayStatus() and getStatusIcon() are no longer needed.
    // The UI handles this.

    /**
     * Client Handler - handles each connected client (Socket-based)
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientId;
        private ChargingPoint myCP;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                logError("Error setting up client handler: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                // First message should be authentication: "AUTH#<CP_ID>"
                String authMessage = in.readLine();
                if (authMessage == null || authMessage.isEmpty()) {
                    out.println("ERROR#No authentication message received");
                    socket.close();
                    return;
                }

                log("Received authentication request: " + authMessage);
                // Parse authentication message
                String[] parts = authMessage.split("#");
                if (parts.length < 2 || !parts[0].equals("AUTH")) {
                    out.println("ERROR#Invalid authentication format. Expected: AUTH#<CP_ID>");
                    logError("Invalid auth format from " + socket.getInetAddress());
                    socket.close();
                    return;
                }

                clientId = parts[1];
                
                // Authenticate the charging point
                ChargingPoint cp = authenticateChargingPoint(clientId);

                // Task 1: If CP is not found, REJECT IT
                if (cp == null) {
                    logError("[WARN] New Socket CP detected: " + clientId + ". REJECTING connection.");
                    out.println("ERROR#AUTH_FAILED#CP ID not registered in database");
                    socket.close();
                    return; 
                }

                // Authentication SUCCESS
                myCP = cp;
                authenticatedCPs.add(cp);
                connectedClients.put(clientId, socket);

                // Update state to AVAILABLE and save to JSON
                cp.setState("AVAILABLE");
                cp.setLastSeen(new java.util.Date().toString());
                updateChargingPoint(cp);
                
                out.println("SUCCESS#Authentication successful. Welcome CP-" + clientId);
                log("[INFO] Authentication SUCCESS for CP ID: " + clientId);
                log("   Location: (" + cp.getPosX() + ", " + cp.getPosY() + ")");
                log("   Price: " + cp.getPriceEurKwh() + " EUR/kWh");
                log("   State updated to: AVAILABLE (saved to database)");

                // Task 4: Update UI
                if (ui != null) {
                    ui.updateCPCount(authenticatedCPs.size());
                }

                // Keep connection alive and handle messages
                String message;
                while ((message = in.readLine()) != null) {
                    log("Message from CP-" + clientId + ": " + message);
                    handleMessage(message);
                }

            } catch (IOException e) {
                if (running) {
                    logError("Connection lost with CP-" + clientId);
                }
            } finally {
                // Clean up on disconnect
                disconnect();
            }
        }

        private void handleMessage(String message) {
            String[] parts = message.split("#");
            String command = parts[0];

            try {
                // Task 3: Update lastSeen on ANY message
                myCP.setLastSeen(new java.util.Date().toString());

                switch (command) {
                    case "STATUS":
                        out.println("ACK#Status: " + myCP.getState());
                        break;

                    case "HEARTBEAT":
                        // lastSeen already updated above
                        updateChargingPoint(myCP);
                        out.println("ACK#Alive");
                        break;

                    case "START_CHARGING":
                        if (parts.length >= 3) {
                            String vehicleId = parts[1];
                            double powerKw = Double.parseDouble(parts[2]);

                            myCP.setState("CHARGING");
                            myCP.setConnectedVehicleId(vehicleId);
                            myCP.setCurrentPowerKw(powerKw);
                            updateChargingPoint(myCP);

                            out.println("ACK#Charging started for vehicle: " + vehicleId);
                            log("CP-" + clientId + " CHARGING vehicle: " + vehicleId + " at " + powerKw + " kW");
                            log("   State changed to CHARGING (saved to database)");
                        }
                        break;

                    case "UPDATE_CHARGING":
                        if (parts.length >= 2) {
                            double energyKwh = Double.parseDouble(parts[1]);
                            double cost = energyKwh * myCP.getPriceEurKwh();

                            myCP.setTotalEnergySuppliedKwh(energyKwh);
                            myCP.setCurrentChargingCost(cost);
                            updateChargingPoint(myCP);
                            out.println("ACK#Updated: " + energyKwh + " kWh, " + String.format("%.2f", cost) + " EUR");
                            System.out.printf("   [INFO] CP-%s charging update: %.2f kWh, %.2f EUR (saved to database)%n",
                                    clientId, energyKwh, cost);
                        }
                        break;

                    case "STOP_CHARGING":
                        double finalEnergy = myCP.getTotalEnergySuppliedKwh();
                        double finalCost = myCP.getCurrentChargingCost();
                        String vehicleId = myCP.getConnectedVehicleId();

                        myCP.setState("AVAILABLE");
                        myCP.setConnectedVehicleId(null);
                        myCP.setCurrentPowerKw(0.0);
                        myCP.setTotalEnergySuppliedKwh(0.0);
                        myCP.setCurrentChargingCost(0.0);
                        updateChargingPoint(myCP);
                        
                        out.println("ACK#Charging stopped. Total: " + finalEnergy + " kWh, " + String.format("%.2f", finalCost) + " EUR");
                        log("CP-" + clientId + " STOPPED charging");
                        System.out.printf("   Vehicle: %s | Total: %.2f kWh | Cost: %.2f EUR%n",
                                vehicleId, finalEnergy, finalCost);
                        log("   State changed to AVAILABLE (saved to database)");
                        break;

                    case "SET_STATE":
                        if (parts.length >= 2) {
                            String newState = parts[1];
                            myCP.setState(newState);
                            updateChargingPoint(myCP);

                            out.println("ACK#State changed to: " + newState);
                            log("CP-" + clientId + " state changed to: " + newState + " (saved to database)");
                        }
                        break;

                    case "UPDATE_POSITION":
                        if (parts.length >= 3) {
                            double newX = Double.parseDouble(parts[1]);
                            double newY = Double.parseDouble(parts[2]);

                            myCP.setPosX(newX);
                            myCP.setPosY(newY);
                            updateChargingPoint(myCP);

                            out.println("ACK#Position updated to: (" + newX + ", " + newY + ")");
                            System.out.printf("   [INFO] CP-%s position updated to: (%.1f, %.1f) (saved to database)%n",
                                    clientId, newX, newY);
                        }
                        break;

                    case "UPDATE_PRICE":
                        if (parts.length >= 2) {
                            double newPrice = Double.parseDouble(parts[1]);
                            myCP.setPriceEurKwh(newPrice);
                            updateChargingPoint(myCP);

                            out.println("ACK#Price updated to: " + newPrice + " EUR/kWh");
                            System.out.printf("   [INFO] CP-%s price updated to: %.2f EUR/kWh (saved to database)%n",
                                    clientId, newPrice);
                        }
                        break;

                    case "UPDATE_STATUS":
                        if (parts.length >= 2) {
                            String newStatus = parts[1];
                            myCP.setStatus(newStatus);
                            updateChargingPoint(myCP);

                            out.println("ACK#Status updated to: " + newStatus);
                            log("CP-" + clientId + " status field updated to: " + newStatus + " (saved to database)");
                        }
                        break;

                    default:
                        out.println("ERROR#Unknown command: " + command);
                        break;
                }
            } catch (Exception e) {
                out.println("ERROR#" + e.getMessage());
                e.printStackTrace();
            }
        }

        private void disconnect() {
            try {
                if (clientId != null && myCP != null) {
                    // Remove from authenticated list
                    authenticatedCPs.removeIf(cp -> cp.getId().equals(clientId));
                    connectedClients.remove(clientId);
                    
                    // Update state to DISCONNECTED in database
                    myCP.setState("DISCONNECTED");
                    myCP.setConnectedVehicleId(null);
                    myCP.setCurrentPowerKw(0.0);
                    myCP.setLastSeen(new java.util.Date().toString());
                    updateChargingPoint(myCP);

                    log("CP-" + clientId + " disconnected");
                    log("   State changed to DISCONNECTED (saved to database)");
                    
                    // Task 4: Update UI
                    if (ui != null) {
                        ui.updateCPCount(authenticatedCPs.size());
                    }
                }

                socket.close();
            } catch (IOException e) {
                logError("Error closing connection: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        log("\nShutting down CENTRAL SERVER...");

        // Save database
        log("Saving final state to database...");
        saveChargingPointsToJSON();
        
        // Close Kafka
        if (kafkaConsumer != null) {
            kafkaConsumer.close();
        }
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }

        try {
            for (Socket socket : connectedClients.values()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            connectedClients.clear();
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            log("[INFO] Database saved successfully");
            log("CENTRAL SERVER stopped");

        } catch (IOException e) {
            logError("Error stopping server: " + e.getMessage());
        }
    }

    // Task 4: Main method modified to launch UI
    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java EV.EV_Central <port> <charging_points_json> <drivers_json> <kafka_bootstrap_servers>");
            System.out.println("Example: java EV.EV_Central 5000 charging_points.json DR.json localhost:9092");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String jsonFilePath = args[1];
        String driversJsonFilePath = args[2];
        String kafkaServers = args[3];

        // Create and link backend
        EV_Central central = new EV_Central(port, jsonFilePath, driversJsonFilePath, kafkaServers);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown hook activated...");
            central.stop();
        }));

        // Launch UI
        javax.swing.SwingUtilities.invokeLater(() -> {
            CentralUI centralUI = new CentralUI();
            central.setUI(centralUI); // Link backend to frontend
            centralUI.setVisible(true);

            // Start the server logic in a new thread to not block the UI
            new Thread(() -> central.start()).start();
        });
    }
}