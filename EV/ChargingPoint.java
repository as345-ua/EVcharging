package EV;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;

public class ChargingPoint implements Serializable {
    private final String id;
    private double lat;
    private double lon;
    private String address;
    private double priceEurKwh;
    private String state; // "AVAILABLE", "CHARGING", "OUT_OF_SERVICE", "BROKEN", "DISCONNECTED"
    private Timestamp lastSeen;
    private String connectedVehicleId; // Only one vehicle at a time
    private double currentPowerKw;
    private double totalEnergySuppliedKwh;
    private double currentChargingCost;

    public ChargingPoint(String id, double lat, double lon, String address, double priceEurKwh) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        setLat(lat);
        setLon(lon);
        setAddress(address);
        setPriceEurKwh(priceEurKwh);
        this.state = "DISCONNECTED";
        this.lastSeen = new Timestamp(System.currentTimeMillis());
        this.connectedVehicleId = null;
        this.currentPowerKw = 0.0;
        this.totalEnergySuppliedKwh = 0.0;
        this.currentChargingCost = 0.0;
    }

    // Getters
    public String getId() { return id; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getAddress() { return address; }
    public double getPriceEurKwh() { return priceEurKwh; }
    public String getState() { return state; }
    public Timestamp getLastSeen() { return lastSeen; }
    public String getConnectedVehicleId() { return connectedVehicleId; }
    public double getCurrentPowerKw() { return currentPowerKw; }
    public double getTotalEnergySuppliedKwh() { return totalEnergySuppliedKwh; }
    public double getCurrentChargingCost() { return currentChargingCost; }

    // Setters with validation
    public void setLat(double lat) {
        validateRange(lat, -90, 90, "Latitude");
        this.lat = lat;
    }

    public void setLon(double lon) {
        validateRange(lon, -180, 180, "Longitude");
        this.lon = lon;
    }

    public void setAddress(String address) {
        this.address = validateNonEmpty(address, "Address");
    }

    public void setPriceEurKwh(double priceEurKwh) {
        validatePositive(priceEurKwh, "Price per kWh");
        this.priceEurKwh = priceEurKwh;
    }

    public void setState(String state) {
        if (isValidState(state)) {
            // If setting to non-charging state, stop any active charging
            if (!"CHARGING".equals(state) && "CHARGING".equals(this.state)) {
                stopCharging();
            }
            this.state = state;
            updateLastSeen();
        }
    }

    public void setLastSeen(Timestamp lastSeen) {
        this.lastSeen = Objects.requireNonNull(lastSeen, "Last seen timestamp cannot be null");
    }

    public void updateLastSeen() {
        this.lastSeen = new Timestamp(System.currentTimeMillis());
    }

    // Business methods
    public synchronized boolean startCharging(String vehicleId, double powerKw) {
        if (!"AVAILABLE".equals(this.state) || connectedVehicleId != null) {
            return false;
        }
        
        this.state = "CHARGING";
        this.connectedVehicleId = vehicleId;
        this.currentPowerKw = powerKw;
        this.totalEnergySuppliedKwh = 0.0;
        this.currentChargingCost = 0.0;
        updateLastSeen();
        return true;
    }

    public synchronized void stopCharging() {
        this.state = "AVAILABLE";
        this.connectedVehicleId = null;
        this.currentPowerKw = 0.0;
        updateLastSeen();
    }

    public synchronized void updateChargingSession(double energyKwh) {
        if ("CHARGING".equals(this.state) && connectedVehicleId != null) {
            this.totalEnergySuppliedKwh += energyKwh;
            this.currentChargingCost = this.totalEnergySuppliedKwh * this.priceEurKwh;
            updateLastSeen();
        }
    }

    public synchronized void setOutOfService() {
        setState("OUT_OF_SERVICE");
    }

    public synchronized void setAvailable() {
        setState("AVAILABLE");
    }

    public synchronized void setBroken() {
        setState("BROKEN");
    }

    public synchronized void setDisconnected() {
        setState("DISCONNECTED");
        this.connectedVehicleId = null;
        this.currentPowerKw = 0.0;
    }

    public boolean isAvailable() {
        return "AVAILABLE".equals(this.state) && connectedVehicleId == null;
    }

    public boolean isCharging() {
        return "CHARGING".equals(this.state) && connectedVehicleId != null;
    }

    // Validation helpers
    private void validateRange(double value, double min, double max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }
    }

    private String validateNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value;
    }

    private void validatePositive(double value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }

    private boolean isValidState(String state) {
        return state != null && (
            "AVAILABLE".equals(state) ||
            "CHARGING".equals(state) ||
            "OUT_OF_SERVICE".equals(state) ||
            "BROKEN".equals(state) ||
            "DISCONNECTED".equals(state)
        );
    }

    @Override
    public String toString() {
        return String.format("ChargingPoint[id=%s, address=%s, state=%s, vehicle=%s, power=%.1fkW, cost=%.2f€]", 
                           id, address, state, connectedVehicleId, currentPowerKw, currentChargingCost);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChargingPoint that = (ChargingPoint) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}