package EV;

import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private Connection connection;
    
    public DatabaseManager(String url, String user, String password) throws SQLException {
        connection = DriverManager.getConnection(url, user, password);
    }
    
    // Insert new charging point
    public boolean insertChargingPoint(ChargingPoint cp) {
        String sql = "INSERT INTO charging_points (id, lat, lon, address, price_eur_kwh, state, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, cp.getId());
            pstmt.setDouble(2, cp.getLat());
            pstmt.setDouble(3, cp.getLon());
            pstmt.setString(4, cp.getAddress());
            pstmt.setDouble(5, cp.getPriceEurKwh());
            pstmt.setString(6, cp.getState());
            pstmt.setTimestamp(7, cp.getLastSeen());
            
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Insert failed: " + e.getMessage());
            return false;
        }
    }
    
    // Remove charging point by ID
    public boolean removeChargingPoint(String id) {
        String sql = "DELETE FROM charging_points WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete failed: " + e.getMessage());
            return false;
        }
    }
    
    // List all charging points
    public List<ChargingPoint> getAllChargingPoints() {
        List<ChargingPoint> points = new ArrayList<>();
        String sql = "SELECT * FROM charging_points";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ChargingPoint cp = new ChargingPoint(
                    rs.getString("id"),
                    rs.getDouble("lat"),
                    rs.getDouble("lon"),
                    rs.getString("address"),
                    rs.getDouble("price_eur_kwh")
                );
                
                cp.setState(rs.getString("state"));
                cp.setLastSeen(rs.getTimestamp("last_seen"));
                points.add(cp);
            }
        } catch (SQLException e) {
            System.err.println("Get all failed: " + e.getMessage());
        }
        
        return points;
    }
    
    // Update charging point
    public boolean updateChargingPoint(ChargingPoint cp) {
        String sql = "UPDATE charging_points SET lat=?, lon=?, address=?, price_eur_kwh=?, state=?, last_seen=? WHERE id=?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setDouble(1, cp.getLat());
            pstmt.setDouble(2, cp.getLon());
            pstmt.setString(3, cp.getAddress());
            pstmt.setDouble(4, cp.getPriceEurKwh());
            pstmt.setString(5, cp.getState());
            pstmt.setTimestamp(6, cp.getLastSeen());
            pstmt.setString(7, cp.getId());
            
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update failed: " + e.getMessage());
            return false;
        }
    }
    
    // Get charging point by ID
    public ChargingPoint getChargingPoint(String id) {
        String sql = "SELECT * FROM charging_points WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                ChargingPoint cp = new ChargingPoint(
                    rs.getString("id"),
                    rs.getDouble("lat"),
                    rs.getDouble("lon"),
                    rs.getString("address"),
                    rs.getDouble("price_eur_kwh")
                );
                
                cp.setState(rs.getString("state"));
                cp.setLastSeen(rs.getTimestamp("last_seen"));
                return cp;
            }
        } catch (SQLException e) {
            System.err.println("Get by ID failed: " + e.getMessage());
        }
        
        return null;
    }
    
    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            System.err.println("Close failed: " + e.getMessage());
        }
    }
}