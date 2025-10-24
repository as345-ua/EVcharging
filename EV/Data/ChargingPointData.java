package EV;

import java.io.Serializable;
import java.util.Objects;

public class ChargingPoint implements Serializable {
    private String id;
    private String status;
    private double posX;
    private double posY;
    private double priceEurKwh;
    private String state;
    private String lastSeen;
    private String connectedVehicleId;
    private double currentPowerKw;
    private double totalEnergySuppliedKwh;
    private double currentChargingCost;

    // Default constructor for JSON deserialization
    public ChargingPoint() {
    }

    // Constructor with required fields
    public ChargingPoint(String id, String status, double posX, double posY, double priceEurKwh, String state) {
        this.id = id;
        this.status = status;
        this.posX = posX;
        this.posY = posY;
        this.priceEurKwh = priceEurKwh;
        this.state = state;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getPosX() { return posX; }
    public void setPosX(double posX) { this.posX = posX; }

    public double getPosY() { return posY; }
    public void setPosY(double posY) { this.posY = posY; }

    public double getPriceEurKwh() { return priceEurKwh; }
    public void setPriceEurKwh(double priceEurKwh) { this.priceEurKwh = priceEurKwh; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }

    public String getConnectedVehicleId() { return connectedVehicleId; }
    public void setConnectedVehicleId(String connectedVehicleId) { this.connectedVehicleId = connectedVehicleId; }

    public double getCurrentPowerKw() { return currentPowerKw; }
    public void setCurrentPowerKw(double currentPowerKw) { this.currentPowerKw = currentPowerKw; }

    public double getTotalEnergySuppliedKwh() { return totalEnergySuppliedKwh; }
    public void setTotalEnergySuppliedKwh(double totalEnergySuppliedKwh) { this.totalEnergySuppliedKwh = totalEnergySuppliedKwh; }

    public double getCurrentChargingCost() { return currentChargingCost; }
    public void setCurrentChargingCost(double currentChargingCost) { this.currentChargingCost = currentChargingCost; }

    @Override
    public String toString() {
        return String.format("ChargingPoint[id=%s, status=%s, posX=%.1f, posY=%.1f, state=%s, vehicle=%s]", 
                           id, status, posX, posY, state, connectedVehicleId);
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