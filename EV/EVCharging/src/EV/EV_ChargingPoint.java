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
    private boolean isCharging;
    private String currentVehicleId;
    private double currentEnergyKwh;
    private Thread chargingThread;

    public EV_ChargingPoint(String serverHost, int serverPort, String cpId) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.cpId = cpId;
        this.connected = false;
        this.isCharging = false;
        this.currentEnergyKwh = 0.0;
    }

    /**
     * Connect to Central and authenticate
     */
    public boolean connectAndAuthenticate() {
        try {
            System.out.println("Connecting to Central Server at " + serverHost + ":" + serverPort);
            socket = new Socket(serverHost, serverPort);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("[INFO] Connected to server");
            System.out.println("Attempting authentication with CP ID: " + cpId);

            // Send authentication message
            out.println("AUTH#" + cpId);
            // Wait for response
            String response = in.readLine();
            System.out.println("Server response: " + response);

            if (response != null && response.startsWith("SUCCESS")) {
                connected = true;
                System.out.println("[INFO] Authentication SUCCESSFUL!");
                System.out.println("Charging Point " + cpId + " is now AVAILABLE\n");
                return true;
            } else {
                System.err.println("[ERROR] Authentication FAILED!");
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
            System.err.println("[ERROR] Unknown host: " + serverHost);
            return false;
        } catch (IOException e) {
            System.err.println("[ERROR] Connection error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send a message to Central
     */
    public void sendMessage(String message) {
        if (connected && out != null) {
            out.println(message);
            System.out.println("Sent to Central: " + message);
        } else {
            System.err.println("[ERROR] Not connected to server");
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
                    System.out.println("\nResponse from Central: " + message);
                    System.out.print("\nChoose option: "); // Re-prompt after message
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("\n[ERROR] Connection lost to Central");
                    connected = false;
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Start charging simulation - sends updates every second
     */
    private void startChargingSimulation(String vehicleId, double powerKw) {
        isCharging = true;
        currentVehicleId = vehicleId;
        currentEnergyKwh = 0.0;

        chargingThread = new Thread(() -> {
            System.out.println("\nCHARGING STARTED");
            System.out.println("   Vehicle: " + vehicleId);
            System.out.println("   Power: " + powerKw + " kW");
            System.out.println("   Sending updates to Central every 1 second...\n");

            while (isCharging) {
                try {
                    Thread.sleep(1000); // Wait 1 second

                    // Calculate energy (power * time in hours)
                    currentEnergyKwh += powerKw / 3600.0; // Convert seconds to hours

                    // Send update to Central
                    sendMessage("UPDATE_CHARGING#" + String.format("%.4f", currentEnergyKwh));

                    System.out.printf("   Charging in progress... %.4f kWh delivered%n", currentEnergyKwh);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        chargingThread.start();
    }

    /**
     * Stop charging simulation
     */
    private void stopChargingSimulation() {
        if (isCharging) {
            isCharging = false;
            if (chargingThread != null) {
                chargingThread.interrupt();
            }
            System.out.println("\nCHARGING STOPPED");
            System.out.printf("   Total energy delivered: %.4f kWh%n", currentEnergyKwh);
            currentVehicleId = null;
            currentEnergyKwh = 0.0;
        }
    }

    /**
     * Interactive menu for testing
     */
    public void runInteractiveMenu() {
        Scanner scanner = new Scanner(System.in);
        while (connected) {
            System.out.println("\n=====================================================");
            System.out.println("      CHARGING POINT CONTROL PANEL - CP " + cpId);
            System.out.println("=====================================================");
            System.out.println("Current Status: " + (isCharging ? "CHARGING (Vehicle: " + currentVehicleId + ")" : "AVAILABLE"));
            System.out.println("-----------------------------------------------------");
            System.out.println("--- CHARGING OPERATIONS ---");
            System.out.println(" 1. Start charging (plug vehicle)");
            System.out.println(" 2. Stop charging (unplug vehicle)");
            System.out.println(" 3. Send heartbeat to Central");
            System.out.println("--- CONFIGURATION CHANGES ---");
            System.out.println(" 4. Update position (X, Y coordinates)");
            System.out.println(" 5. Update price (EUR/kWh)");
            System.out.println(" 6. Change state (AVAILABLE/OUT_OF_SERVICE/etc)");
            System.out.println(" 7. Update status field");
            System.out.println("--- INFORMATION & CONTROL ---");
            System.out.println(" 8. Request current status from Central");
            System.out.println(" 9. Simulate fault/breakdown");
            System.out.println(" 10. Disconnect from Central");
            System.out.println("-----------------------------------------------------");
            System.out.print("\nChoose option: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    handleStartCharging(scanner);
                    break;

                case "2":
                    handleStopCharging();
                    break;

                case "3":
                    System.out.println("\nSending heartbeat...");
                    sendMessage("HEARTBEAT");
                    break;

                case "4":
                    handleUpdatePosition(scanner);
                    break;

                case "5":
                    handleUpdatePrice(scanner);
                    break;

                case "6":
                    handleChangeState(scanner);
                    break;

                case "7":
                    handleUpdateStatus(scanner);
                    break;

                case "8":
                    System.out.println("\nRequesting status from Central...");
                    sendMessage("STATUS");
                    break;

                case "9":
                    handleSimulateFault(scanner);
                    break;

                case "10":
                    System.out.println("\nDisconnecting from Central...");
                    if (isCharging) {
                        stopChargingSimulation();
                        sendMessage("STOP_CHARGING");
                    }
                    disconnect();
                    scanner.close();
                    return;

                default:
                    System.out.println("\n[ERROR] Invalid option! Please choose 1-10.");
                    break;
            }

            // Small delay to see server response
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        scanner.close();
    }

    /**
     * Handle start charging option
     */
    private void handleStartCharging(Scanner scanner) {
        if (isCharging) {
            System.out.println("\n[ERROR] Already charging! Stop current session first.");
            return;
        }

        System.out.println("\n--- START CHARGING SESSION ---");
        System.out.print("Enter vehicle ID (e.g., VEH-001, VEH-2050): ");
        String vehicleId = scanner.nextLine().trim();
        if (vehicleId.isEmpty()) {
            System.out.println("[ERROR] Vehicle ID cannot be empty!");
            return;
        }

        System.out.print("Enter charging power in kW (e.g., 22, 50, 120): ");
        String powerStr = scanner.nextLine().trim();

        if (powerStr.isEmpty()) {
            System.out.println("[ERROR] Power cannot be empty!");
            return;
        }

        try {
            double powerKw = Double.parseDouble(powerStr);
            if (powerKw <= 0) {
                System.out.println("[ERROR] Power must be greater than 0!");
                return;
            }

            System.out.println("\nSending START_CHARGING command to Central...");
            // Send START_CHARGING to Central
            sendMessage("START_CHARGING#" + vehicleId + "#" + powerKw);
            // Start local simulation
            startChargingSimulation(vehicleId, powerKw);
        } catch (NumberFormatException e) {
            System.out.println("[ERROR] Invalid power value! Must be a number.");
        }
    }

    /**
     * Handle stop charging option
     */
    private void handleStopCharging() {
        if (!isCharging) {
            System.out.println("\n[ERROR] Not charging! Start a charging session first.");
            return;
        }

        System.out.println("\n--- STOPPING CHARGING SESSION ---");
        // Stop local simulation
        stopChargingSimulation();
        // Send STOP_CHARGING to Central
        System.out.println("Sending STOP_CHARGING command to Central...");
        sendMessage("STOP_CHARGING");
    }

    /**
     * Handle update position option
     */
    private void handleUpdatePosition(Scanner scanner) {
        System.out.println("\n--- UPDATE POSITION ---");
        System.out.print("Enter new X coordinate: ");
        String xStr = scanner.nextLine().trim();

        System.out.print("Enter new Y coordinate: ");
        String yStr = scanner.nextLine().trim();
        if (xStr.isEmpty() || yStr.isEmpty()) {
            System.out.println("[ERROR] Coordinates cannot be empty!");
            return;
        }

        try {
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);

            System.out.println("\nSending position update to Central...");
            System.out.printf("   New position: (%.2f, %.2f)%n", x, y);
            sendMessage("UPDATE_POSITION#" + x + "#" + y);

        } catch (NumberFormatException e) {
            System.out.println("[ERROR] Invalid coordinate values! Must be numbers.");
        }
    }

    /**
     * Handle update price option
     */
    private void handleUpdatePrice(Scanner scanner) {
        System.out.println("\n--- UPDATE PRICE ---");
        System.out.print("Enter new price (EUR/kWh, e.g., 0.35, 0.45): ");
        String priceStr = scanner.nextLine().trim();
        if (priceStr.isEmpty()) {
            System.out.println("[ERROR] Price cannot be empty!");
            return;
        }

        try {
            double price = Double.parseDouble(priceStr);
            if (price < 0) {
                System.out.println("[ERROR] Price cannot be negative!");
                return;
            }

            System.out.println("\nSending price update to Central...");
            System.out.printf("   New price: %.2f EUR/kWh%n", price);
            sendMessage("UPDATE_PRICE#" + price);
        } catch (NumberFormatException e) {
            System.out.println("[ERROR] Invalid price value! Must be a number.");
        }
    }

    /**
     * Handle change state option
     */
    private void handleChangeState(Scanner scanner) {
        System.out.println("\n--- CHANGE STATE ---");
        System.out.println("Available states:");
        System.out.println("  1. AVAILABLE       - Ready for charging");
        System.out.println("  2. OUT_OF_SERVICE  - Temporarily disabled");
        System.out.println("  3. BROKEN          - Hardware failure");
        System.out.println("  4. CHARGING        - Currently charging (automatic)");
        System.out.println("  5. DISCONNECTED    - Not connected (automatic)");
        System.out.print("\nEnter new state (type name or number): ");
        String input = scanner.nextLine().trim().toUpperCase();

        if (input.isEmpty()) {
            System.out.println("[ERROR] State cannot be empty!");
            return;
        }

        String state;
        switch (input) {
            case "1":
            case "AVAILABLE":
                state = "AVAILABLE";
                break;
            case "2":
            case "OUT_OF_SERVICE":
                state = "OUT_OF_SERVICE";
                break;
            case "3":
            case "BROKEN":
                state = "BROKEN";
                break;
            case "4":
            case "CHARGING":
                state = "CHARGING";
                break;
            case "5":
            case "DISCONNECTED":
                state = "DISCONNECTED";
                break;
            default:
                System.out.println("[ERROR] Invalid state! Use one of the listed options.");
                return;
        }

        System.out.println("\nSending state change to Central...");
        System.out.println("   New state: " + state);
        sendMessage("SET_STATE#" + state);
    }

    /**
     * Handle update status option
     */
    private void handleUpdateStatus(Scanner scanner) {
        System.out.println("\n--- UPDATE STATUS FIELD ---");
        System.out.println("Current examples: Active, working, Damaged, offline");
        System.out.print("Enter new status description: ");
        String status = scanner.nextLine().trim();
        if (status.isEmpty()) {
            System.out.println("[ERROR] Status cannot be empty!");
            return;
        }

        System.out.println("\nSending status update to Central...");
        System.out.println("   New status: " + status);
        sendMessage("UPDATE_STATUS#" + status);
    }

    /**
     * Handle simulate fault option
     */
    private void handleSimulateFault(Scanner scanner) {
        System.out.println("\n--- SIMULATE FAULT/BREAKDOWN ---");
        System.out.println("This will:");
        System.out.println("  - Stop any active charging session");
        System.out.println("  - Set state to BROKEN");
        System.out.println("  - Update database");
        System.out.print("\nAre you sure? (yes/no): ");

        String confirm = scanner.nextLine().trim().toLowerCase();
        if (confirm.equals("yes") || confirm.equals("y")) {
            System.out.println("\nSimulating fault...");
            // If charging, stop it
            if (isCharging) {
                stopChargingSimulation();
                sendMessage("STOP_CHARGING");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Set state to BROKEN
            System.out.println("Setting state to BROKEN...");
            sendMessage("SET_STATE#BROKEN");

        } else {
            System.out.println("Fault simulation cancelled.");
        }
    }

    /**
     * Disconnect from Central
     */
    public void disconnect() {
        connected = false;
        if (isCharging) {
            stopChargingSimulation();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Disconnected from Central");
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Error disconnecting: " + e.getMessage());
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
            System.out.println("\nShutting down Charging Point...");
            cp.disconnect();
        }));
        
        // Try to connect and authenticate
        if (cp.connectAndAuthenticate()) {
            // Start listening for messages from Central
            cp.startListening();
            // Run interactive menu
            cp.runInteractiveMenu();
        } else {
            System.err.println("[ERROR] Failed to connect to Central. Exiting...");
            System.exit(1);
        }
    }
}