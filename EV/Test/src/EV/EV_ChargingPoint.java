package EV;

import java.net.*;
import java.io.*;
import java.util.Scanner;

public class EV_ChargingPoint {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String cpId;
    private String serverHost;
    private int serverPort;
    private boolean connected;
    
    public EV_ChargingPoint(String serverHost, int serverPort, String cpId) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.cpId = cpId;
        this.connected = false;
    }
    
    /**
     * Connect to Central and authenticate
     */
    public boolean connectAndAuthenticate() {
        try {
            System.out.println("🔌 Connecting to Central Server at " + serverHost + ":" + serverPort);
            socket = new Socket(serverHost, serverPort);
            
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            System.out.println("✅ Connected to server");
            System.out.println("🔐 Attempting authentication with CP ID: " + cpId);
            
            // Send authentication message
            out.println("AUTH#" + cpId);
            
            // Wait for response
            String response = in.readLine();
            System.out.println("📨 Server response: " + response);
            
            if (response != null && response.startsWith("SUCCESS")) {
                connected = true;
                System.out.println("✅ Authentication SUCCESSFUL!");
                System.out.println("🟢 Charging Point " + cpId + " is now ONLINE\n");
                return true;
            } else {
                System.err.println("❌ Authentication FAILED!");
                if (response != null && response.startsWith("ERROR")) {
                    String[] parts = response.split("#");
                    if (parts.length > 1) {
                        System.err.println("   Reason: " + parts[1]);
                    }
                }
                disconnect();
                return false;
            }
            
        } catch (UnknownHostException e) {
            System.err.println("❌ Unknown host: " + serverHost);
            return false;
        } catch (IOException e) {
            System.err.println("❌ Connection error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send a message to Central
     */
    public void sendMessage(String message) {
        if (connected && out != null) {
            out.println(message);
            System.out.println("📤 Sent: " + message);
        } else {
            System.err.println("❌ Not connected to server");
        }
    }
    
    /**
     * Listen for messages from Central
     */
    public void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                String message;
                while (connected && (message = in.readLine()) != null) {
                    System.out.println("📩 Received from Central: " + message);
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("❌ Connection lost to Central");
                    connected = false;
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    
    /**
     * Interactive menu for testing
     */
    public void runInteractiveMenu() {
        Scanner scanner = new Scanner(System.in);
        
        while (connected) {
            System.out.println("\n========== CHARGING POINT MENU ==========");
            System.out.println("1. Send STATUS request");
            System.out.println("2. Send HEARTBEAT");
            System.out.println("3. Start charging (simulate)");
            System.out.println("4. Stop charging");
            System.out.println("5. Disconnect");
            System.out.println("=========================================");
            System.out.print("Choose option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    sendMessage("STATUS");
                    break;
                    
                case "2":
                    sendMessage("HEARTBEAT");
                    break;
                    
                case "3":
                    System.out.print("Enter vehicle ID: ");
                    String vehicleId = scanner.nextLine().trim();
                    if (!vehicleId.isEmpty()) {
                        sendMessage("START_CHARGING#" + vehicleId);
                    }
                    break;
                    
                case "4":
                    sendMessage("STOP_CHARGING");
                    break;
                    
                case "5":
                    System.out.println("👋 Disconnecting...");
                    disconnect();
                    return;
                    
                default:
                    System.out.println("❌ Invalid option");
                    break;
            }
            
            // Small delay to see server response
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        scanner.close();
    }
    
    /**
     * Disconnect from Central
     */
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("🔌 Disconnected from Central");
            }
        } catch (IOException e) {
            System.err.println("❌ Error disconnecting: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        // Check arguments
        if (args.length < 3) {
            System.out.println("Usage: java EV.EV_ChargingPoint <server_host> <server_port> <cp_id>");
            System.out.println("Example: java EV.EV_ChargingPoint localhost 5000 1");
            System.out.println("\nAvailable CP IDs from JSON: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10");
            return;
        }
        
        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String cpId = args[2];
        
        // Create charging point client
        EV_ChargingPoint cp = new EV_ChargingPoint(serverHost, serverPort, cpId);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down Charging Point...");
            cp.disconnect();
        }));
        
        // Try to connect and authenticate
        if (cp.connectAndAuthenticate()) {
            // Start listening for messages from Central
            cp.startListening();
            
            // Run interactive menu
            cp.runInteractiveMenu();
        } else {
            System.err.println("❌ Failed to connect to Central. Exiting...");
            System.exit(1);
        }
    }
}
