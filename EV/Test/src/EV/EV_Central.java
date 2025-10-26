package EV;


import com.google.gson.Gson;
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
    
    // Map to keep track of client sockets
    private Map<String, Socket> connectedClients;
    
    private Gson gson;
    private String jsonFilePath;
    
    public EV_Central(int port, String jsonFilePath) {
        this.port = port;
        this.jsonFilePath = jsonFilePath;
        this.allChargingPoints = new Vector<>();
        this.authenticatedCPs = new Vector<>();
        this.connectedClients = new ConcurrentHashMap<>();
        this.gson = new Gson();
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
                System.out.printf("  %s CP-%s | %s | Location: (%.1f, %.1f) | Price: €%.2f/kWh%n",
                    statusIcon, cp.getId(), cp.getState(), 
                    cp.getPosX(), cp.getPosY(), cp.getPriceEurKwh());
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
                authenticatedCPs.add(cp);
                connectedClients.put(clientId, socket);
                cp.setState("AVAILABLE"); // Update state to available
                
                out.println("SUCCESS#Authentication successful. Welcome CP-" + clientId);
                System.out.println("✅ Authentication SUCCESS for CP ID: " + clientId);
                System.out.println("   Location: (" + cp.getPosX() + ", " + cp.getPosY() + ")");
                System.out.println("   Price: €" + cp.getPriceEurKwh() + "/kWh");
                
                // Display updated status
                displayStatus();
                
                // Keep connection alive and handle messages
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("📨 Message from CP-" + clientId + ": " + message);
                    handleMessage(cp, message);
                }
                
            } catch (IOException e) {
                System.err.println("❌ Connection lost with CP-" + clientId);
            } finally {
                // Clean up on disconnect
                disconnect();
            }
        }
        
        private void handleMessage(ChargingPoint cp, String message) {
            String[] parts = message.split("#");
            String command = parts[0];
            
            try {
                switch (command) {
                    case "STATUS":
                        out.println("ACK#Status: " + cp.getState());
                        break;
                        
                    case "HEARTBEAT":
                        out.println("ACK#Alive");
                        break;
                        
                    case "START_CHARGING":
                        if (parts.length >= 2) {
                            String vehicleId = parts[1];
                            cp.setState("CHARGING");
                            cp.setConnectedVehicleId(vehicleId);
                            out.println("ACK#Charging started for vehicle: " + vehicleId);
                            System.out.println("⚡ CP-" + clientId + " started charging vehicle: " + vehicleId);
                        }
                        break;
                        
                    case "STOP_CHARGING":
                        cp.setState("AVAILABLE");
                        cp.setConnectedVehicleId(null);
                        out.println("ACK#Charging stopped");
                        System.out.println("🛑 CP-" + clientId + " stopped charging");
                        break;
                        
                    default:
                        out.println("ERROR#Unknown command: " + command);
                        break;
                }
            } catch (Exception e) {
                out.println("ERROR#" + e.getMessage());
            }
        }
        
        private void disconnect() {
            try {
                if (clientId != null) {
                    // Remove from authenticated list
                    authenticatedCPs.removeIf(cp -> cp.getId().equals(clientId));
                    connectedClients.remove(clientId);
                    
                    // Update state in main list
                    for (ChargingPoint cp : allChargingPoints) {
                        if (cp.getId().equals(clientId)) {
                            cp.setState("DISCONNECTED");
                            break;
                        }
                    }
                    
                    System.out.println("🔌 CP-" + clientId + " disconnected");
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
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
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
