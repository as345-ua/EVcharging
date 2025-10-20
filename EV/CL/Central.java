package EV;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.sql.Timestamp;

public class Central {
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Map<String, ChargingPoint> chargingPoints;
    private Map<String, Socket> connectedCPs;
    private Map<String, Socket> connectedDrivers;
    private DatabaseManager dbManager;
    private int port;
    private boolean running;
    
    public Central(int port, String dbUrl, String dbUser, String dbPassword) {
        this.port = port;
        this.chargingPoints = new ConcurrentHashMap<>();
        this.connectedCPs = new ConcurrentHashMap<>();
        this.connectedDrivers = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool();
        
        try {
            this.dbManager = new DatabaseManager(dbUrl, dbUser, dbPassword);
            loadChargingPointsFromDB();
        } catch (Exception e) {
            System.err.println("❌ Database initialization failed: " + e.getMessage());
        }
    }
    
    private void loadChargingPointsFromDB() {
        try {
            List<ChargingPoint> points = dbManager.getAllChargingPoints();
            for (ChargingPoint cp : points) {
                chargingPoints.put(cp.getId(), cp);
                System.out.println("📋 Loaded CP from DB: " + cp.getId() + " - " + cp.getState());
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load charging points from DB: " + e.getMessage());
        }
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("🚀 CENTRAL started on port " + port);
            System.out.println("⏳ Waiting for connections...");
            
            // Start monitoring thread for real-time updates
            startMonitoringThread();
            
            while (running) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("❌ Server error: " + e.getMessage());
        }
    }
    
    private void startMonitoringThread() {
        Thread monitorThread = new Thread(() -> {
            while (running) {
                try {
                    displaySystemStatus();
                    Thread.sleep(5000); // Update every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    private void displaySystemStatus() {
        System.out.println("\n=== EV CHARGING NETWORK STATUS ===");
        System.out.println("Total CPs: " + chargingPoints.size());
        System.out.println("Connected CPs: " + connectedCPs.size());
        System.out.println("Connected Drivers: " + connectedDrivers.size());
        
        for (ChargingPoint cp : chargingPoints.values()) {
            String statusColor = getStatusColor(cp.getState());
            System.out.printf("%s CP %s: %s - %s %s%n", 
                statusColor, cp.getId(), cp.getState(), cp.getAddress(), 
                cp.isCharging() ? "(Charging: " + cp.getConnectedVehicleId() + ")" : "");
        }
        System.out.println("=================================\n");
    }
    
    private String getStatusColor(String state) {
        switch (state) {
            case "AVAILABLE": return "🟢";
            case "CHARGING": return "🔵";
            case "OUT_OF_SERVICE": return "🟠";
            case "BROKEN": return "🔴";
            case "DISCONNECTED": return "⚫";
            default: return "⚪";
        }
    }
    
    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientType; // "CP" or "DRIVER"
        private String clientId;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.err.println("❌ Client handler setup failed: " + e.getMessage());
            }
        }
        
        @Override
        public void run() {
            try {
                // First message should identify client type and ID
                String initialMessage = in.readLine();
                if (initialMessage != null) {
                    handleInitialConnection(initialMessage);
                    
                    // Continue processing messages based on client type
                    String message;
                    while ((message = in.readLine()) != null) {
                        processMessage(message);
                    }
                }
            } catch (IOException e) {
                System.err.println("❌ Client connection lost: " + clientId);
            } finally {
                disconnectClient();
            }
        }
        
        private void handleInitialConnection(String message) {
            String[] parts = message.split("#");
            if (parts.length >= 2) {
                clientType = parts[0];
                clientId = parts[1];
                
                switch (clientType) {
                    case "CP":
                        handleCPConnection(parts);
                        break;
                    case "DRIVER":
                        handleDriverConnection(parts);
                        break;
                    default:
                        out.println("ERROR#Invalid client type");
                        break;
                }
            } else {
                out.println("ERROR#Invalid initial message format");
            }
        }
        
        private void handleCPConnection(String[] parts) {
            if (parts.length >= 5) {
                String cpId = parts[1];
                double lat = Double.parseDouble(parts[2]);
                double lon = Double.parseDouble(parts[3]);
                String address = parts[4];
                double price = parts.length >= 6 ? Double.parseDouble(parts[5]) : 0.15;
                
                ChargingPoint cp = chargingPoints.get(cpId);
                if (cp == null) {
                    // Register new charging point
                    cp = new ChargingPoint(cpId, lat, lon, address, price);
                    cp.setState("AVAILABLE");
                    chargingPoints.put(cpId, cp);
                    dbManager.insertChargingPoint(cp);
                    System.out.println("✅ Registered new CP: " + cpId);
                } else {
                    // Update existing CP connection
                    cp.setAvailable();
                    cp.updateLastSeen();
                    dbManager.updateChargingPoint(cp);
                    System.out.println("✅ CP reconnected: " + cpId);
                }
                
                connectedCPs.put(cpId, socket);
                out.println("ACK#CP registered successfully");
                
            } else {
                out.println("ERROR#Invalid CP registration format");
            }
        }
        
        private void handleDriverConnection(String[] parts) {
            String driverId = parts[1];
            connectedDrivers.put(driverId, socket);
            out.println("ACK#Driver connected successfully");
            System.out.println("👤 Driver connected: " + driverId);
        }
        
        private void processMessage(String message) {
            String[] parts = message.split("#");
            String command = parts[0];
            
            try {
                switch (command) {
                    case "REQUEST_CHARGE":
                        handleChargeRequest(parts);
                        break;
                    case "START_CHARGING":
                        handleStartCharging(parts);
                        break;
                    case "STOP_CHARGING":
                        handleStopCharging(parts);
                        break;
                    case "STATUS_UPDATE":
                        handleStatusUpdate(parts);
                        break;
                    case "SET_STATE":
                        handleSetState(parts);
                        break;
                    case "GET_AVAILABLE_CPs":
                        sendAvailableCPs();
                        break;
                    default:
                        out.println("ERROR#Unknown command: " + command);
                        break;
                }
            } catch (Exception e) {
                out.println("ERROR#Processing failed: " + e.getMessage());
            }
        }
        
        private void handleChargeRequest(String[] parts) {
            if (parts.length >= 3) {
                String driverId = parts[1];
                String cpId = parts[2];
                
                ChargingPoint cp = chargingPoints.get(cpId);
                if (cp == null) {
                    out.println("ERROR#Charging point not found");
                    return;
                }
                
                if (!cp.isAvailable()) {
                    out.println("ERROR#Charging point not available. Current state: " + cp.getState());
                    return;
                }
                
                // Notify CP to prepare for charging
                Socket cpSocket = connectedCPs.get(cpId);
                if (cpSocket != null) {
                    try {
                        PrintWriter cpOut = new PrintWriter(cpSocket.getOutputStream(), true);
                        cpOut.println("PREPARE_CHARGE#" + driverId);
                        out.println("ACK#Charging request sent to CP");
                    } catch (IOException e) {
                        out.println("ERROR#Cannot communicate with charging point");
                    }
                } else {
                    out.println("ERROR#Charging point not connected");
                }
            }
        }
        
        private void handleStartCharging(String[] parts) {
            if (parts.length >= 4) {
                String cpId = parts[1];
                String vehicleId = parts[2];
                double powerKw = Double.parseDouble(parts[3]);
                
                ChargingPoint cp = chargingPoints.get(cpId);
                if (cp != null && cp.startCharging(vehicleId, powerKw)) {
                    dbManager.updateChargingPoint(cp);
                    out.println("ACK#Charging started successfully");
                    System.out.println("⚡ Charging STARTED: CP " + cpId + " for vehicle " + vehicleId);
                    
                    // Notify driver if connected
                    notifyDriver(vehicleId, "CHARGING_STARTED#" + cpId);
                } else {
                    out.println("ERROR#Cannot start charging");
                }
            }
        }
        
        private void handleStopCharging(String[] parts) {
            if (parts.length >= 2) {
                String cpId = parts[1];
                String vehicleId = parts.length >= 3 ? parts[2] : null;
                
                ChargingPoint cp = chargingPoints.get(cpId);
                if (cp != null) {
                    if (vehicleId != null && !vehicleId.equals(cp.getConnectedVehicleId())) {
                        out.println("ERROR#Vehicle ID mismatch");
                        return;
                    }
                    
                    cp.stopCharging();
                    dbManager.updateChargingPoint(cp);
                    out.println("ACK#Charging stopped successfully");
                    System.out.println("🛑 Charging STOPPED: CP " + cpId);
                    
                    // Notify driver
                    if (vehicleId != null) {
                        notifyDriver(vehicleId, "CHARGING_STOPPED#" + cpId + "#" + 
                                   String.format("%.2f", cp.getTotalEnergySuppliedKwh()) + "#" +
                                   String.format("%.2f", cp.getCurrentChargingCost()));
                    }
                } else {
                    out.println("ERROR#Charging point not found");
                }
            }
        }
        
        private void handleStatusUpdate(String[] parts) {
            if (parts.length >= 3) {
                String cpId = parts[1];
                String state = parts[2];
                
                ChargingPoint cp = chargingPoints.get(cpId);
                if (cp != null) {
                    cp.setState(state);
                    cp.updateLastSeen();
                    
                    // Update energy if provided
                    if (parts.length >= 4 && cp.isCharging()) {
                        double energyKwh = Double.parseDouble(parts[3]);
                        cp.updateChargingSession(energyKwh);
                    }
                    
                    dbManager.updateChargingPoint(cp);
                    out.println("ACK#Status updated");
                }
            }
        }
        
        private void handleSetState(String[] parts) {
            if (parts.length >= 3) {
                String cpId = parts[1];
                String newState = parts[2];
                
                ChargingPoint cp = chargingPoints.get(cpId);
                if (cp != null) {
                    cp.setState(newState);
                    cp.updateLastSeen();
                    dbManager.updateChargingPoint(cp);
                    out.println("ACK#State changed to " + newState);
                    System.out.println("🔧 CP " + cpId + " state changed to: " + newState);
                } else {
                    out.println("ERROR#Charging point not found");
                }
            }
        }
        
        private void sendAvailableCPs() {
            StringBuilder response = new StringBuilder("AVAILABLE_CPs");
            for (ChargingPoint cp : chargingPoints.values()) {
                if (cp.isAvailable()) {
                    response.append("#").append(cp.getId())
                           .append("#").append(cp.getAddress())
                           .append("#").append(String.format("%.2f", cp.getPriceEurKwh()));
                }
            }
            out.println(response.toString());
        }
        
        private void notifyDriver(String driverId, String message) {
            Socket driverSocket = connectedDrivers.get(driverId);
            if (driverSocket != null) {
                try {
                    PrintWriter driverOut = new PrintWriter(driverSocket.getOutputStream(), true);
                    driverOut.println(message);
                } catch (IOException e) {
                    System.err.println("❌ Cannot notify driver " + driverId);
                }
            }
        }
        
        private void disconnectClient() {
            try {
                if (clientType != null && clientId != null) {
                    if ("CP".equals(clientType)) {
                        connectedCPs.remove(clientId);
                        ChargingPoint cp = chargingPoints.get(clientId);
                        if (cp != null) {
                            cp.setDisconnected();
                            dbManager.updateChargingPoint(cp);
                        }
                        System.out.println("🔌 CP disconnected: " + clientId);
                    } else if ("DRIVER".equals(clientType)) {
                        connectedDrivers.remove(clientId);
                        System.out.println("👤 Driver disconnected: " + clientId);
                    }
                }
                socket.close();
            } catch (IOException e) {
                System.err.println("❌ Error closing client connection: " + e.getMessage());
            }
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            threadPool.shutdown();
            if (dbManager != null) {
                dbManager.close();
            }
            System.out.println("🛑 CENTRAL stopped");
        } catch (IOException e) {
            System.err.println("❌ Error stopping server: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java Central <port> <db_url> <db_user> <db_password>");
            System.out.println("Example: java Central 8080 jdbc:mysql://localhost:3306/ev_charging root password");
            return;
        }
        
        int port = Integer.parseInt(args[0]);
        String dbUrl = args[1];
        String dbUser = args[2];
        String dbPassword = args[3];
        
        Central central = new Central(port, dbUrl, dbUser, dbPassword);
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(central::stop));
        
        central.start();
    }
}