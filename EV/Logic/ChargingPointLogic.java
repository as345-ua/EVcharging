package EV;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ChargingPointService {
    private List<ChargingPoint> chargingPoints;
    private final String jsonFilePath;
    private final ObjectMapper objectMapper;

    public ChargingPointService(String jsonFilePath) {
        this.jsonFilePath = jsonFilePath;
        this.objectMapper = new ObjectMapper();
        loadChargingPointsFromJson();
    }

    // Load charging points from JSON file
    private void loadChargingPointsFromJson() {
        try {
            File file = new File(jsonFilePath);
            if (file.exists()) {
                chargingPoints = objectMapper.readValue(file, new TypeReference<List<ChargingPoint>>() {});
            } else {
                chargingPoints = new ArrayList<>();
                System.out.println("JSON file not found. Starting with empty list.");
            }
        } catch (IOException e) {
            System.err.println("Error loading charging points from JSON: " + e.getMessage());
            chargingPoints = new ArrayList<>();
        }
    }

    // Save charging points to JSON file
    private void saveChargingPointsToJson() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(jsonFilePath), chargingPoints);
        } catch (IOException e) {
            System.err.println("Error saving charging points to JSON: " + e.getMessage());
        }
    }

    // Get all charging points
    public List<ChargingPoint> getAllChargingPoints() {
        return new ArrayList<>(chargingPoints);
    }

    // Get charging point by ID
    public ChargingPoint getChargingPointById(String id) {
        return chargingPoints.stream()
                .filter(cp -> cp.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Get charging points by state
    public List<ChargingPoint> getChargingPointsByState(String state) {
        return chargingPoints.stream()
                .filter(cp -> cp.getState().equals(state))
                .collect(Collectors.toList());
    }

    // Get available charging points
    public List<ChargingPoint> getAvailableChargingPoints() {
        return getChargingPointsByState("AVAILABLE");
    }

    // Get charging points that are currently charging
    public List<ChargingPoint> getChargingPointsInUse() {
        return getChargingPointsByState("CHARGING");
    }

    // Update charging point status
    public boolean updateChargingPointStatus(String id, String newStatus) {
        ChargingPoint point = getChargingPointById(id);
        if (point != null) {
            point.setStatus(newStatus);
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Update charging point state
    public boolean updateChargingPointState(String id, String newState) {
        ChargingPoint point = getChargingPointById(id);
        if (point != null && isValidState(newState)) {
            point.setState(newState);
            
            // If setting to non-charging state, clear vehicle connection
            if (!"CHARGING".equals(newState)) {
                point.setConnectedVehicleId(null);
                point.setCurrentPowerKw(0.0);
            }
            
            // Update last seen timestamp
            point.setLastSeen(java.time.Instant.now().toString());
            
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Start charging session
    public boolean startCharging(String chargingPointId, String vehicleId, double powerKw) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        
        if (point != null && "AVAILABLE".equals(point.getState()) && point.getConnectedVehicleId() == null) {
            point.setState("CHARGING");
            point.setConnectedVehicleId(vehicleId);
            point.setCurrentPowerKw(powerKw);
            point.setCurrentChargingCost(0.0);
            point.setLastSeen(java.time.Instant.now().toString());
            point.setStatus("working");
            
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Stop charging session
    public boolean stopCharging(String chargingPointId) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        
        if (point != null && "CHARGING".equals(point.getState())) {
            point.setState("AVAILABLE");
            point.setConnectedVehicleId(null);
            point.setCurrentPowerKw(0.0);
            point.setLastSeen(java.time.Instant.now().toString());
            point.setStatus("Active");
            
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Update charging session (add energy and calculate cost)
    public boolean updateChargingSession(String chargingPointId, double energyKwh) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        
        if (point != null && "CHARGING".equals(point.getState()) && point.getConnectedVehicleId() != null) {
            double newTotalEnergy = point.getTotalEnergySuppliedKwh() + energyKwh;
            double newCost = newTotalEnergy * point.getPriceEurKwh();
            
            point.setTotalEnergySuppliedKwh(newTotalEnergy);
            point.setCurrentChargingCost(newCost);
            point.setLastSeen(java.time.Instant.now().toString());
            
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Set charging point as out of service
    public boolean setOutOfService(String chargingPointId) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        if (point != null) {
            point.setState("OUT_OF_SERVICE");
            point.setConnectedVehicleId(null);
            point.setCurrentPowerKw(0.0);
            point.setLastSeen(java.time.Instant.now().toString());
            point.setStatus("offline");
            
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Set charging point as broken
    public boolean setBroken(String chargingPointId) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        if (point != null) {
            point.setState("BROKEN");
            point.setConnectedVehicleId(null);
            point.setCurrentPowerKw(0.0);
            point.setLastSeen(java.time.Instant.now().toString());
            point.setStatus("Damaged");
            
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Set charging point as available
    public boolean setAvailable(String chargingPointId) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        if (point != null) {
            point.setState("AVAILABLE");
            point.setConnectedVehicleId(null);
            point.setCurrentPowerKw(0.0);
            point.setLastSeen(java.time.Instant.now().toString());
            point.setStatus("Active");
            
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Set charging point as disconnected
    public boolean setDisconnected(String chargingPointId) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        if (point != null) {
            point.setState("DISCONNECTED");
            point.setConnectedVehicleId(null);
            point.setCurrentPowerKw(0.0);
            point.setLastSeen(java.time.Instant.now().toString());
            point.setStatus("offline");
            
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Check if charging point is available
    public boolean isAvailable(String chargingPointId) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        return point != null && "AVAILABLE".equals(point.getState()) && point.getConnectedVehicleId() == null;
    }

    // Check if charging point is charging
    public boolean isCharging(String chargingPointId) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        return point != null && "CHARGING".equals(point.getState()) && point.getConnectedVehicleId() != null;
    }

    // Get total energy supplied by all charging points
    public double getTotalEnergySupplied() {
        return chargingPoints.stream()
                .mapToDouble(ChargingPoint::getTotalEnergySuppliedKwh)
                .sum();
    }

    // Get total revenue from all charging points
    public double getTotalRevenue() {
        return chargingPoints.stream()
                .mapToDouble(cp -> cp.getTotalEnergySuppliedKwh() * cp.getPriceEurKwh())
                .sum();
    }

    // Get charging points by vehicle ID
    public ChargingPoint getChargingPointByVehicle(String vehicleId) {
        return chargingPoints.stream()
                .filter(cp -> vehicleId.equals(cp.getConnectedVehicleId()))
                .findFirst()
                .orElse(null);
    }

    // Update charging point price
    public boolean updateChargingPointPrice(String chargingPointId, double newPrice) {
        ChargingPoint point = getChargingPointById(chargingPointId);
        if (point != null && newPrice >= 0) {
            point.setPriceEurKwh(newPrice);
            saveChargingPointsToJson();
            return true;
        }
        return false;
    }

    // Refresh data from JSON file
    public void refreshData() {
        loadChargingPointsFromJson();
    }

    // Force save current data to JSON file
    public void saveData() {
        saveChargingPointsToJson();
    }

    // Validation helper
    private boolean isValidState(String state) {
        return state != null && (
            "AVAILABLE".equals(state) ||
            "CHARGING".equals(state) ||
            "OUT_OF_SERVICE".equals(state) ||
            "BROKEN".equals(state) ||
            "DISCONNECTED".equals(state)
        );
    }
}