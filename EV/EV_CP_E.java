package EV;

import java.net.*;
import java.io.*;
import java.util.*;

public class EV_CP_E {
    private String cpId;
    private String centralHost;
    private int centralPort;
    private Socket centralSocket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean running;
    
    public EV_CP_E(String cpId, String centralHost, int centralPort) {
        this.cpId = cpId;
        this.centralHost = centralHost;
        this.centralPort = centralPort;
    }
    
    public void start() {
        try {
            centralSocket = new Socket(centralHost, centralPort);
            in = new BufferedReader(new InputStreamReader(centralSocket.getInputStream()));
            out = new PrintWriter(centralSocket.getOutputStream(), true);
            
            // Register with central
            out.println("CP#" + cpId + "#40.7128#-74.0060#123 Main St#0.15");
            
            String response = in.readLine();
            if (response != null && response.startsWith("ACK")) {
                System.out.println("✅ CP Engine registered with central: " + cpId);
                running = true;
                
                // Start message processing
                processMessages();
            } else {
                System.err.println("❌ Registration failed: " + response);
            }
            
        } catch (IOException e) {
            System.err.println("❌ Connection failed: " + e.getMessage());
        }
    }
    
    private void processMessages() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                System.out.println("📨 Received: " + message);
                String[] parts = message.split("#");
                String command = parts[0];
                
                switch (command) {
                    case "PREPARE_CHARGE":
                        handlePrepareCharge(parts);
                        break;
                    case "START_CHARGING":
                        handleStartCharging(parts);
                        break;
                    case "STOP_CHARGING":
                        handleStopCharging(parts);
                        break;
                    default:
                        System.out.println("Unknown command: " + command);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Message processing error: " + e.getMessage());
        }
    }
    
    private void handlePrepareCharge(String[] parts) {
        if (parts.length >= 2) {
            String driverId = parts[1];
            System.out.println("🔌 Prepare charging for driver: " + driverId);
            // Simulate vehicle connection
            out.println("START_CHARGING#" + cpId + "#" + driverId + "#7.5");
        }
    }
    
    private void handleStartCharging(String[] parts) {
        // Charging started - simulate energy delivery
        System.out.println("⚡ Charging started");
        new Thread(() -> {
            try {
                for (int i = 0; i < 10 && running; i++) {
                    Thread.sleep(1000);
                    double energy = 0.5; // 0.5 kWh per second
                    out.println("STATUS_UPDATE#" + cpId + "#CHARGING#" + energy);
                }
                // Stop charging after simulation
                out.println("STOP_CHARGING#" + cpId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    private void handleStopCharging(String[] parts) {
        System.out.println("🛑 Charging stopped");
        out.println("STATUS_UPDATE#" + cpId + "#AVAILABLE#0");
    }
    
    public void stop() {
        running = false;
        try {
            if (centralSocket != null) centralSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java EV_CP_E <cp_id> <central_host> <central_port>");
            return;
        }
        
        String cpId = args[0];
        String centralHost = args[1];
        int centralPort = Integer.parseInt(args[2]);
        
        EV_CP_E cpEngine = new EV_CP_E(cpId, centralHost, centralPort);
        cpEngine.start();
    }
}