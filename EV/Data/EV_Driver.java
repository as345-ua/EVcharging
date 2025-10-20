package EV;

import java.net.*;
import java.io.*;
import java.util.*;

public class EV_Driver {
    private String driverId;
    private String centralHost;
    private int centralPort;
    private Socket centralSocket;
    private BufferedReader in;
    private PrintWriter out;
    private List<String> chargingRequests;
    
    public EV_Driver(String driverId, String centralHost, int centralPort, List<String> chargingRequests) {
        this.driverId = driverId;
        this.centralHost = centralHost;
        this.centralPort = centralPort;
        this.chargingRequests = chargingRequests != null ? chargingRequests : new ArrayList<>();
    }
    
    public void start() {
        try {
            centralSocket = new Socket(centralHost, centralPort);
            in = new BufferedReader(new InputStreamReader(centralSocket.getInputStream()));
            out = new PrintWriter(centralSocket.getOutputStream(), true);
            
            // Register with central
            out.println("DRIVER#" + driverId);
            
            String response = in.readLine();
            if (response != null && response.startsWith("ACK")) {
                System.out.println("✅ Driver registered: " + driverId);
                
                // Start processing charging requests
                processChargingRequests();
                
                // Listen for notifications
                listenForNotifications();
            }
            
        } catch (IOException e) {
            System.err.println("❌ Connection failed: " + e.getMessage());
        }
    }
    
    private void processChargingRequests() {
        new Thread(() -> {
            try {
                for (String cpId : chargingRequests) {
                    System.out.println("🚗 Requesting charge at CP: " + cpId);
                    out.println("REQUEST_CHARGE#" + driverId + "#" + cpId);
                    
                    // Wait before next request (as per requirement #12)
                    Thread.sleep(4000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    private void listenForNotifications() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("📢 Notification: " + message);
                
                String[] parts = message.split("#");
                if ("CHARGING_STARTED".equals(parts[0])) {
                    System.out.println("⚡ Charging started at CP: " + parts[1]);
                } else if ("CHARGING_STOPPED".equals(parts[0])) {
                    System.out.println("🛑 Charging stopped at CP: " + parts[1]);
                    System.out.println("📊 Energy: " + parts[2] + " kWh, Cost: " + parts[3] + "€");
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Notification error: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java EV_Driver <driver_id> <central_host> <central_port> [cp1,cp2,...]");
            return;
        }
        
        String driverId = args[0];
        String centralHost = args[1];
        int centralPort = Integer.parseInt(args[2]);
        List<String> requests = new ArrayList<>();
        
        if (args.length > 3) {
            requests = Arrays.asList(args[3].split(","));
        } else {
            // Default test requests
            requests = Arrays.asList("CP_001", "CP_002");
        }
        
        EV_Driver driver = new EV_Driver(driverId, centralHost, centralPort, requests);
        driver.start();
    }
}