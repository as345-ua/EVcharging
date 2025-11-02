package EV;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple Swing UI for Central Server
 */
public class CentralUI extends JFrame {
    private JTable cpTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel cpCountLabel;
    private JLabel driverCountLabel;
    
    private Map<String, Integer> cpRowMap; // CP ID -> row index
    
    public CentralUI() {
        super("EV Central - Monitoring Panel");
        
        cpRowMap = new HashMap<>();
        
        // Set up UI
        setupUI();
        
        // Set frame properties
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }
    
    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        
        // Top Panel - Status Bar
        JPanel topPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        
        statusLabel = new JLabel("🟢 Central Server Running");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        cpCountLabel = new JLabel("Connected CPs: 0");
        cpCountLabel.setFont(new Font("Arial", Font.BOLD, 14));
        cpCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        driverCountLabel = new JLabel("Active Drivers: 0");
        driverCountLabel.setFont(new Font("Arial", Font.BOLD, 14));
        driverCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        
        topPanel.add(statusLabel);
        topPanel.add(cpCountLabel);
        topPanel.add(driverCountLabel);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Center Panel - Split between table and log
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.6);
        
        // Top of split - Charging Points Table
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Charging Points Status"));
        
        String[] columns = {"ID", "Location", "State", "Price (€/kWh)", "Power (kW)", "Vehicle", "Energy (kWh)", "Cost (€)"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        cpTable = new JTable(tableModel);
        cpTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        cpTable.setRowHeight(25);
        cpTable.setDefaultRenderer(Object.class, new CPTableCellRenderer());
        
        JScrollPane tableScroll = new JScrollPane(cpTable);
        tablePanel.add(tableScroll, BorderLayout.CENTER);
        
        splitPane.setTopComponent(tablePanel);
        
        // Bottom of split - Activity Log
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logPanel.add(logScroll, BorderLayout.CENTER);
        
        splitPane.setBottomComponent(logPanel);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Bottom Panel - Control Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton clearLogButton = new JButton("🗑️ Clear Log");
        clearLogButton.addActionListener(e -> logArea.setText(""));
        
        bottomPanel.add(clearLogButton);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    // Custom cell renderer for coloring rows based on state
    private class CPTableCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (!isSelected) {
                String state = (String) table.getValueAt(row, 2); // State column
                
                if ("AVAILABLE".equals(state)) {
                    c.setBackground(new Color(200, 255, 200)); // Light green
                } else if ("CHARGING".equals(state)) {
                    c.setBackground(new Color(200, 220, 255)); // Light blue
                } else if ("OUT_OF_SERVICE".equals(state)) {
                    c.setBackground(new Color(255, 220, 180)); // Light orange
                } else if ("BROKEN".equals(state)) {
                    c.setBackground(new Color(255, 180, 180)); // Light red
                } else if ("DISCONNECTED".equals(state)) {
                    c.setBackground(new Color(220, 220, 220)); // Gray
                } else {
                    c.setBackground(Color.WHITE);
                }
            }
            
            return c;
        }
    }
    
    // Public methods for central server to update UI
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public void updateCPCount(int count) {
        SwingUtilities.invokeLater(() -> cpCountLabel.setText("Connected CPs: " + count));
    }
    
    public void updateDriverCount(int count) {
        SwingUtilities.invokeLater(() -> driverCountLabel.setText("Active Drivers: " + count));
    }
    
    public void addOrUpdateCP(String id, String location, String state, double price,
                               double powerKw, String vehicleId, double energyKwh, double cost) {
        SwingUtilities.invokeLater(() -> {
            Integer rowIndex = cpRowMap.get(id);
            
            Object[] rowData = {
                id,
                location,
                state,
                String.format("%.2f", price),
                String.format("%.1f", powerKw),
                vehicleId != null ? vehicleId : "-",
                String.format("%.4f", energyKwh),
                String.format("€%.2f", cost)
            };
            
            if (rowIndex == null) {
                // Add new row
                tableModel.addRow(rowData);
                cpRowMap.put(id, tableModel.getRowCount() - 1);
            } else {
                // Update existing row
                for (int col = 0; col < rowData.length; col++) {
                    tableModel.setValueAt(rowData[col], rowIndex, col);
                }
            }
        });
    }
    
    public void removeCP(String id) {
        SwingUtilities.invokeLater(() -> {
            Integer rowIndex = cpRowMap.get(id);
            if (rowIndex != null) {
                tableModel.removeRow(rowIndex);
                cpRowMap.remove(id);
                // Rebuild row map
                cpRowMap.clear();
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    cpRowMap.put((String) tableModel.getValueAt(i, 0), i);
                }
            }
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CentralUI ui = new CentralUI();
            ui.setVisible(true);
            
            // Example: Add some test data
            ui.addOrUpdateCP("1", "Madrid Centro", "AVAILABLE", 0.35, 0.0, null, 0.0, 0.0);
            ui.addOrUpdateCP("2", "Barcelona Port", "CHARGING", 0.40, 50.0, "DRV-001", 2.5, 1.0);
            ui.appendLog("Central server started successfully");
        });
    }
}
