package EV;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.*;
import java.io.*;
import java.util.*;

public class DriverService {
    private List<Driver> drivers;
    private String driverId;
    private String centralHost;
    private int centralPort;
    private Socket centralSocket;
    private BufferedReader in;
    private PrintWriter out;
    private List<String> chargingRequests;
    private final String jsonFilePath;
    private final ObjectMapper objectMapper;
    
    public DriverService(String driverId, String centralHost, int centralPort, 
                       List<String> chargingRequests, String jsonFilePath) {
        this.driverId = driverId;
        this.centralHost = centralHost;
        this.centralPort = centralPort;
        this.chargingRequests = chargingRequests != null ? chargingRequests : new ArrayList<>();
        this.jsonFilePath = jsonFilePath;
        this.objectMapper = new ObjectMapper();
        loadDriversFromJson();
    }
    
    // Load drivers from JSON file
    private void loadDriversFromJson() {
        try {
            File file = new File(jsonFilePath);
            if (file.exists()) {
                drivers = objectMapper.readValue(file, new TypeReference<List<Driver>>() {});
            } else {
                drivers = new ArrayList<>();
                System.out.println("JSON file not found. Starting with empty driver list.");
            }
        } catch (IOException e) {
            System.err.println("Error loading drivers from JSON: " + e.getMessage());
            drivers = new ArrayList<>();
        }
    }
    
    // Save drivers to JSON file
    private void saveDriversToJson() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(jsonFilePath), drivers);
        } catch (IOException e) {
            System.err.println("Error saving drivers to JSON: " + e.getMessage());
        }
    }
    
    // Get current driver information
    public Driver getCurrentDriver() {
        return drivers.stream()
                .filter(d -> d.getId().equals(driverId))
                .findFirst()
                .orElse(null);
    }
    
    // Get all drivers
    public List<Driver> getAllDrivers() {
        return new ArrayList<>(drivers);
    }
    
    // Get driver by ID
    public Driver getDriverById(String id) {
        return drivers.stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    // Get driver by email
    public Driver getDriverByEmail(String email) {
        return drivers.stream()
                .filter(d -> d.getGmail().equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);
    }
    
    // Add new driver
    public boolean addDriver(Driver driver) {
        if (getDriverById(driver.getId()) != null) {
            System.err.println("Driver with ID " + driver.getId() + " already exists.");
            return false;
        }
        drivers.add(driver);
        saveDriversToJson();
        return true;
    }
    
    // Update driver information
    public boolean updateDriver(String id, String newName, String newEmail) {
        Driver driver = getDriverById(id);
        if (driver != null) {
            driver.setName(newName);
            driver.setGmail(newEmail);
            saveDriversToJson();
            return true;
        }
        return false;
    }
    
    // Remove driver
    public boolean removeDriver(String id) {
        Driver driver = getDriverById(id);
        if (driver != null) {
            drivers.remove(driver);
            saveDriversToJson();
            return true;
        }
        return false;
    }
    
    // Start driver connection and processing
    public void start() {
        // Verify driver exists in database
        Driver currentDriver = getCurrentDriver();
        if (currentDriver == null) {
            System.err.println("❌ Driver not found in database: " + driverId);
            return;
        }
        
        System.out.println("🚗 Starting driver: " + currentDriver.getName() + " (" + currentDriver.getGmail() + ")");
        
        try {
            centralSocket = new Socket(centralHost, centralPort);
            in = new BufferedReader(new InputStreamReader(centralSocket.getInputStream()));
            out = new PrintWriter(centralSocket.getOutputStream(), true);
            
            // Register with central
            out.println("DRIVER#" + driverId);
            
            String response = in.readLine();
            if (response != null && response.startsWith("ACK")) {
                System.out.println("✅ Driver registered: " + currentDriver.getName());
                
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
                    System.out.println("🔌 Requesting charge at CP: " + cpId + " for driver: " + getCurrentDriver().getName());
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
                System.out.println("📢 Notification for " + getCurrentDriver().getName() + ": " + message);
                
                String[] parts = message.split("#");
                if ("CHARGING_STARTED".equals(parts[0])) {
                    System.out.println("⚡ Charging started at CP: " + parts[1]);
                    // Update driver status or log charging session
                    logChargingSession(parts[1], "STARTED");
                } else if ("CHARGING_STOPPED".equals(parts[0])) {
                    System.out.println("🛑 Charging stopped at CP: " + parts[1]);
                    System.out.println("📊 Energy: " + parts[2] + " kWh, Cost: " + parts[3] + "€");
                    // Update driver status or log charging session
                    logChargingSession(parts[1], "STOPPED", parts[2], parts[3]);
                } else if ("REQUEST_DENIED".equals(parts[0])) {
                    System.out.println("❌ Charging request denied for CP: " + parts[1]);
                    logChargingSession(parts[1], "DENIED");
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Notification error: " + e.getMessage());
        }
    }
    
    // Log charging session for the driver
    private void logChargingSession(String cpId, String status) {
        logChargingSession(cpId, status, "0", "0");
    }
    
    private void logChargingSession(String cpId, String status, String energy, String cost) {
        String timestamp = new java.util.Date().toString();
        String logMessage = String.format("[%s] Driver: %s, CP: %s, Status: %s, Energy: %s kWh, Cost: %s€",
                timestamp, getCurrentDriver().getName(), cpId, status, energy, cost);
        System.out.println("📝 Session Log: " + logMessage);
    }
    
    // Send custom message to central server
    public void sendMessageToCentral(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    // Disconnect from central server
    public void disconnect() {
        try {
            if (out != null) {
                out.println("DISCONNECT#" + driverId);
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (centralSocket != null) centralSocket.close();
            System.out.println("🔌 Disconnected from central server");
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }
    
    // Refresh driver data from JSON
    public void refreshDriverData() {
        loadDriversFromJson();
    }
    
    // Get charging requests for this driver
    public List<String> getChargingRequests() {
        return new ArrayList<>(chargingRequests);
    }
    
    // Add charging request
    public void addChargingRequest(String cpId) {
        if (!chargingRequests.contains(cpId)) {
            chargingRequests.add(cpId);
        }
    }
    
    // Clear all charging requests
    public void clearChargingRequests() {
        chargingRequests.clear();
    }
}