package EV;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.net.*;
import java.io.*;
import java.lang.reflect.Type;
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
    
    public EV_Central(int port, String jsonFilePath) {
        this.port = port;
        this.jsonFilePath = jsonFilePath;
        this.allChargingPoints = new Vector<>();
        this.authenticatedCPs = new Vector<>();
        this.connectedClients = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.running = false;
    }
    
    /**
     * Load all charging points from JSON file into allChargingPoints vector
     */
    private void loadChargingPointsFromJSON() {
        System.out.println("📂 Loading charging points from: " + jsonFilePath);
        
        try (FileReader reader = new FileReader(jsonFilePath)) {
            Type listType = new TypeToken<ArrayList<ChargingPoint>>(){}.getType();
            List<ChargingPoint> cpList = gson.fromJson(reader, listType);
            
            allChargingPoints.clear();
            allChargingPoints.addAll(cpList);
            
            System.out.println("✅ Loaded " + allChargingPoints.size() + " charging points from JSON:");
            for (ChargingPoint cp : allChargingPoints) {
                System.out.printf("   - CP ID: %s | Status: %s | State: %s | Location: (%.1f, %.1f)%n",
                    cp.getId(), cp.getStatus(), cp.getState(), cp.getPosX(), cp.getPosY());
            }
            System.out.println();
            
        } catch (FileNotFoundException e) {
            System.err.println("❌ JSON file not found: " + jsonFilePath);
            System.err.println("   Make sure charging_points.json is in the correct location");
        } catch (Exception e) {
            System.err.println("❌ Error loading JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * SAVE all charging points to JSON file (PERSIST CHANGES)
     */
    private synchronized void saveChargingPointsToJSON() {
        try (FileWriter writer = new FileWriter(jsonFilePath)) {
            gson.toJson(allChargingPoints, writer);
            System.out.println("💾 Database updated: charging_points.json saved");
        } catch (IOException e) {
            System.err.println("❌ Error saving to JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Update a charging point in the vector AND save to JSON
     */
    private synchronized void updateChargingPoint(ChargingPoint updatedCP) {
        // Update in main vector
        for (int i = 0; i < allChargingPoints.size(); i++) {
            if (allChargingPoints.get(i).getId().equals(updatedCP.getId())) {
                allChargingPoints.set(i, updatedCP);
                break;
            }
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
     * Auto-save thread - saves database every 30 seconds
     */
    private void startAutoSaveThread() {
        Thread autoSaveThread = new Thread(() -> {
            System.out.println("💾 Auto-save enabled: Database will be saved every 30 seconds");
            while (running) {
                try {
                    Thread.sleep(30000); // 30 seconds
                    if (running) {
                        System.out.println("\n⏰ Auto-save: Saving database...");
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
            System.err.println("❌ No charging points loaded. Cannot start server.");
            return;
        }
        
        // 2. Start server socket
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("🚀 CENTRAL SERVER started on port " + port);
            System.out.println("⏳ Waiting for Charging Point connections...");
            System.out.println("===========================================\n");
            
            // Start auto-save thread (saves every 30 seconds)
            startAutoSaveThread();
            
            // 3. Accept connections in loop
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("🔌 New connection from: " + clientSocket.getInetAddress());
                
                // Handle each client in a new thread
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            }
            
        } catch (IOException e) {
            if (running) {
                System.err.println("❌ Server error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Display current system status
     */
    private void displayStatus() {
        System.out.println("\n========== SYSTEM STATUS ==========");
        System.out.println("Total CPs in Database: " + allChargingPoints.size());
        System.out.println("Authenticated CPs:     " + authenticatedCPs.size());
        System.out.println("-----------------------------------");
        
        if (!authenticatedCPs.isEmpty()) {
            System.out.println("Connected Charging Points:");
            for (ChargingPoint cp : authenticatedCPs) {
                String statusIcon = getStatusIcon(cp.getState());
                System.out.printf("  %s CP-%s | %s | Location: (%.1f, %.1f) | Price: €%.2f/kWh",
                    statusIcon, cp.getId(), cp.getState(), 
                    cp.getPosX(), cp.getPosY(), cp.getPriceEurKwh());
                
                if (cp.isCharging() && cp.getConnectedVehicleId() != null) {
                    System.out.printf(" | Vehicle: %s | Power: %.1f kW%n", 
                        cp.getConnectedVehicleId(), cp.getCurrentPowerKw());
                } else {
                    System.out.println();
                }
            }
        } else {
            System.out.println("  (No charging points connected yet)");
        }
        System.out.println("===================================\n");
    }
    
    private String getStatusIcon(String state) {
        if (state == null) return "⚪";
        switch (state.toUpperCase()) {
            case "AVAILABLE": return "🟢";
            case "CHARGING": return "🔵";
            case "OUT_OF_SERVICE": return "🟠";
            case "BROKEN": return "🔴";
            case "DISCONNECTED": return "⚫";
            default: return "⚪";
        }
    }
    
    /**
     * Client Handler - handles each connected client
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientId;
        private ChargingPoint myCP; // Reference to this client's CP
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.err.println("❌ Error setting up client handler: " + e.getMessage());
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
                
                System.out.println("📨 Received authentication request: " + authMessage);
                
                // Parse authentication message
                String[] parts = authMessage.split("#");
                if (parts.length < 2 || !parts[0].equals("AUTH")) {
                    out.println("ERROR#Invalid authentication format. Expected: AUTH#<CP_ID>");
                    System.err.println("❌ Invalid auth format from " + socket.getInetAddress());
                    socket.close();
                    return;
                }
                
                clientId = parts[1];
                
                // Authenticate the charging point
                ChargingPoint cp = authenticateChargingPoint(clientId);
                
                if (cp == null) {
                    // Authentication FAILED
                    out.println("ERROR#Authentication failed. Charging Point ID '" + clientId + "' not found in database");
                    System.err.println("❌ Authentication FAILED for CP ID: " + clientId);
                    socket.close();
                    return;
                }
                
                // Authentication SUCCESS
                myCP = cp; // Keep reference
                authenticatedCPs.add(cp);
                connectedClients.put(clientId, socket);
                
                // Update state to AVAILABLE and save to JSON
                cp.setState("AVAILABLE");
                cp.setLastSeen(new java.util.Date().toString());
                updateChargingPoint(cp);
                
                out.println("SUCCESS#Authentication successful. Welcome CP-" + clientId);
                System.out.println("✅ Authentication SUCCESS for CP ID: " + clientId);
                System.out.println("   Location: (" + cp.getPosX() + ", " + cp.getPosY() + ")");
                System.out.println("   Price: €" + cp.getPriceEurKwh() + "/kWh");
                System.out.println("   State updated to: AVAILABLE (saved to database)");
                
                // Display updated status
                displayStatus();
                
                // Keep connection alive and handle messages
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("📨 Message from CP-" + clientId + ": " + message);
                    handleMessage(message);
                }
                
            } catch (IOException e) {
                System.err.println("❌ Connection lost with CP-" + clientId);
            } finally {
                // Clean up on disconnect
                disconnect();
            }
        }
        
        private void handleMessage(String message) {
            String[] parts = message.split("#");
            String command = parts[0];
            
            try {
                switch (command) {
                    case "STATUS":
                        out.println("ACK#Status: " + myCP.getState());
                        break;
                        
                    case "HEARTBEAT":
                        myCP.setLastSeen(new java.util.Date().toString());
                        updateChargingPoint(myCP);
                        out.println("ACK#Alive");
                        break;
                        
                    case "START_CHARGING":
                        if (parts.length >= 3) {
                            String vehicleId = parts[1];
                            double powerKw = Double.parseDouble(parts[2]);
                            
                            // Update to CHARGING state
                            myCP.setState("CHARGING");
                            myCP.setConnectedVehicleId(vehicleId);
                            myCP.setCurrentPowerKw(powerKw);
                            myCP.setLastSeen(new java.util.Date().toString());
                            
                            // Save to database
                            updateChargingPoint(myCP);
                            
                            out.println("ACK#Charging started for vehicle: " + vehicleId);
                            System.out.println("⚡ CP-" + clientId + " CHARGING vehicle: " + vehicleId + " at " + powerKw + " kW");
                            System.out.println("   💾 State changed to CHARGING (saved to database)");
                            displayStatus();
                        }
                        break;
                        
                    case "UPDATE_CHARGING":
                        if (parts.length >= 2) {
                            double energyKwh = Double.parseDouble(parts[1]);
                            double cost = energyKwh * myCP.getPriceEurKwh();
                            
                            myCP.setTotalEnergySuppliedKwh(energyKwh);
                            myCP.setCurrentChargingCost(cost);
                            myCP.setLastSeen(new java.util.Date().toString());
                            
                            // Save to database
                            updateChargingPoint(myCP);
                            
                            out.println("ACK#Updated: " + energyKwh + " kWh, €" + String.format("%.2f", cost));
                            System.out.printf("📊 CP-%s charging update: %.2f kWh, €%.2f (saved to database)%n", 
                                clientId, energyKwh, cost);
                        }
                        break;
                        
                    case "STOP_CHARGING":
                        double finalEnergy = myCP.getTotalEnergySuppliedKwh();
                        double finalCost = myCP.getCurrentChargingCost();
                        String vehicleId = myCP.getConnectedVehicleId();
                        
                        // Update to AVAILABLE state
                        myCP.setState("AVAILABLE");
                        myCP.setConnectedVehicleId(null);
                        myCP.setCurrentPowerKw(0.0);
                        myCP.setTotalEnergySuppliedKwh(0.0);
                        myCP.setCurrentChargingCost(0.0);
                        myCP.setLastSeen(new java.util.Date().toString());
                        
                        // Save to database
                        updateChargingPoint(myCP);
                        
                        out.println("ACK#Charging stopped. Total: " + finalEnergy + " kWh, €" + String.format("%.2f", finalCost));
                        System.out.println("🛑 CP-" + clientId + " STOPPED charging");
                        System.out.printf("   Vehicle: %s | Total: %.2f kWh | Cost: €%.2f%n", 
                            vehicleId, finalEnergy, finalCost);
                        System.out.println("   💾 State changed to AVAILABLE (saved to database)");
                        displayStatus();
                        break;
                        
                    case "SET_STATE":
                        if (parts.length >= 2) {
                            String newState = parts[1];
                            myCP.setState(newState);
                            myCP.setLastSeen(new java.util.Date().toString());
                            
                            // Save to database
                            updateChargingPoint(myCP);
                            
                            out.println("ACK#State changed to: " + newState);
                            System.out.println("🔧 CP-" + clientId + " state changed to: " + newState + " (saved to database)");
                            displayStatus();
                        }
                        break;
                        
                    case "UPDATE_POSITION":
                        if (parts.length >= 3) {
                            double newX = Double.parseDouble(parts[1]);
                            double newY = Double.parseDouble(parts[2]);
                            
                            myCP.setPosX(newX);
                            myCP.setPosY(newY);
                            myCP.setLastSeen(new java.util.Date().toString());
                            
                            // Save to database
                            updateChargingPoint(myCP);
                            
                            out.println("ACK#Position updated to: (" + newX + ", " + newY + ")");
                            System.out.printf("📍 CP-%s position updated to: (%.1f, %.1f) (saved to database)%n", 
                                clientId, newX, newY);
                        }
                        break;
                        
                    case "UPDATE_PRICE":
                        if (parts.length >= 2) {
                            double newPrice = Double.parseDouble(parts[1]);
                            
                            myCP.setPriceEurKwh(newPrice);
                            myCP.setLastSeen(new java.util.Date().toString());
                            
                            // Save to database
                            updateChargingPoint(myCP);
                            
                            out.println("ACK#Price updated to: €" + newPrice + "/kWh");
                            System.out.printf("💰 CP-%s price updated to: €%.2f/kWh (saved to database)%n", 
                                clientId, newPrice);
                        }
                        break;
                        
                    case "UPDATE_STATUS":
                        if (parts.length >= 2) {
                            String newStatus = parts[1];
                            
                            myCP.setStatus(newStatus);
                            myCP.setLastSeen(new java.util.Date().toString());
                            
                            // Save to database
                            updateChargingPoint(myCP);
                            
                            out.println("ACK#Status updated to: " + newStatus);
                            System.out.println("📝 CP-" + clientId + " status field updated to: " + newStatus + " (saved to database)");
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
                    
                    System.out.println("🔌 CP-" + clientId + " disconnected");
                    System.out.println("   💾 State changed to DISCONNECTED (saved to database)");
                    displayStatus();
                }
                
                socket.close();
            } catch (IOException e) {
                System.err.println("❌ Error closing connection: " + e.getMessage());
            }
        }
    }
    
    public void stop() {
        running = false;
        
        System.out.println("\n🛑 Shutting down CENTRAL SERVER...");
        
        // IMPORTANT: Save all data to JSON before closing
        System.out.println("💾 Saving final state to database...");
        saveChargingPointsToJSON();
        
        try {
            // Close all client connections
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
            
            System.out.println("✅ Database saved successfully");
            System.out.println("🛑 CENTRAL SERVER stopped");
            
        } catch (IOException e) {
            System.err.println("❌ Error stopping server: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        // Check arguments
        if (args.length < 2) {
            System.out.println("Usage: java EV.EV_Central <port> <json_file_path>");
            System.out.println("Example: java EV.EV_Central 5000 charging_points.json");
            return;
        }
        
        int port = Integer.parseInt(args[0]);
        String jsonFilePath = args[1];
        
        // Create and start central server
        EV_Central central = new EV_Central(port, jsonFilePath);
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down server...");
            central.stop();
        }));
        
        central.start();
    }
}