package EV;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Simple Swing UI for Driver Application
 * Task 2: Integrated with EV_Driver
 */
public class DriverUI extends JFrame {
    private JTextArea logArea;
    private JTextField cpIdField;
    private JButton requestButton;
    private JButton loadFileButton;
    private JButton startQueueButton;
    private JLabel statusLabel;
    private JProgressBar chargingProgress;
    private JLabel energyLabel;
    private JLabel costLabel;
    
    private EV_Driver driverApp; // Task 2: Backend driver reference
    
    public DriverUI(String driverId, String kafkaServers) {
        super("EV Driver - Driver " + driverId);
        
        // Task 2: Initialize driver app
        this.driverApp = new EV_Driver(kafkaServers, driverId);
        
        // Set up UI
        setupUI();
        
        // Set frame properties
        setSize(700, 600);
        setLocationRelativeTo(null);
        
        // Task 2: Add window listener for cleanup
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // We handle close manually
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                driverApp.stop();
            }
        });
        
        // Task 2: Start driver app
        new Thread(() -> driverApp.startWithUI(this)).start();
    }
    
    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        // Top Panel - Status
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        
        statusLabel = new JLabel("⚪ Initializing...");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        topPanel.add(statusLabel, BorderLayout.WEST);
        
        add(topPanel, BorderLayout.NORTH);
        // Center Panel - Log Area
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
        
        // Bottom Panel - Controls
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Charging info panel
        JPanel chargingPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        chargingPanel.setBorder(BorderFactory.createTitledBorder("Current Charging"));
        
        chargingProgress = new JProgressBar(0, 100);
        chargingProgress.setStringPainted(true);
        chargingProgress.setString("Not charging");
        chargingPanel.add(chargingProgress);
        
        energyLabel = new JLabel("Energy: 0.0000 kWh");
        energyLabel.setFont(new Font("Arial", Font.BOLD, 14));
        chargingPanel.add(energyLabel);
        
        costLabel = new JLabel("Cost: €0.00");
        costLabel.setFont(new Font("Arial", Font.BOLD, 14));
        chargingPanel.add(costLabel);
        
        bottomPanel.add(chargingPanel, BorderLayout.NORTH);
        // Control buttons panel
        JPanel controlPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        // Request charging panel
        JPanel requestPanel = new JPanel(new BorderLayout(5, 0));
        cpIdField = new JTextField("1");
        requestButton = new JButton("🔌 Request Charging");
        requestButton.setFont(new Font("Arial", Font.BOLD, 12));
        requestPanel.add(new JLabel("CP ID:"), BorderLayout.WEST);
        requestPanel.add(cpIdField, BorderLayout.CENTER);
        requestPanel.add(requestButton, BorderLayout.EAST);
        
        loadFileButton = new JButton("📁 Load Requests File");
        loadFileButton.setFont(new Font("Arial", Font.BOLD, 12));
        
        startQueueButton = new JButton("▶️ Start Queue");
        startQueueButton.setFont(new Font("Arial", Font.BOLD, 12));
        startQueueButton.setEnabled(false);
        
        JButton clearButton = new JButton("🗑️ Clear Log");
        clearButton.setFont(new Font("Arial", Font.BOLD, 12));
        
        controlPanel.add(requestPanel);
        controlPanel.add(loadFileButton);
        controlPanel.add(startQueueButton);
        controlPanel.add(clearButton);
        bottomPanel.add(controlPanel, BorderLayout.CENTER);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Add action listeners
        requestButton.addActionListener(e -> requestCharging());
        loadFileButton.addActionListener(e -> loadFile());
        startQueueButton.addActionListener(e -> startQueue());
        clearButton.addActionListener(e -> logArea.setText(""));
    }
    
    private void requestCharging() {
        String cpId = cpIdField.getText().trim();
        if (!cpId.isEmpty()) {
            driverApp.requestChargingManual(cpId);
            // appendLog("📤 Requesting charging at CP-" + cpId + "..."); // Logged by driverApp
        } else {
            JOptionPane.showMessageDialog(this, "Please enter a CP ID", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new java.io.File("."));
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            String filename = fileChooser.getSelectedFile().getPath();
            driverApp.loadRequestsFromFile(filename);
            startQueueButton.setEnabled(true);
        }
    }
    
    private void startQueue() {
        driverApp.startRequestQueue();
        startQueueButton.setEnabled(false);
    }
    
    // Public methods for driver app to update UI
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }
    
    // Called by TELEMETRY messages (real-time, no cost)
    public void updateChargingInfo(double energyKwh, int progress) {
        SwingUtilities.invokeLater(() -> {
            energyLabel.setText(String.format("Energy: %.4f kWh", energyKwh));
            int p = Math.min(progress, 100);
            chargingProgress.setValue(p);
            chargingProgress.setString(String.format("Charging: %d%%", p));
        });
    }
    
    // Called by CHARGING_STOPPED message (final ticket)
    public void updateFinalTicket(double totalKwh, double totalCost) {
        SwingUtilities.invokeLater(() -> {
            energyLabel.setText(String.format("Energy: %.4f kWh", totalKwh));
            costLabel.setText(String.format("Cost: €%.2f", totalCost));
            chargingProgress.setValue(100);
            chargingProgress.setString("Completed!");
        });
    }
    
    public void resetChargingInfo() {
        SwingUtilities.invokeLater(() -> {
            energyLabel.setText("Energy: 0.0000 kWh");
            costLabel.setText("Cost: €0.00");
            chargingProgress.setValue(0);
            chargingProgress.setString("Not charging");
        });
    }
    
    public void enableQueueButton() {
        SwingUtilities.invokeLater(() -> startQueueButton.setEnabled(true));
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java EV.DriverUI <kafka_bootstrap_servers> <driver_id>");
            System.out.println("Example: java EV.DriverUI localhost:9092 1");
            return;
        }
        
        String kafkaServers = args[0];
        String driverId = args[1];
        
        SwingUtilities.invokeLater(() -> {
            DriverUI ui = new DriverUI(driverId, kafkaServers);
            ui.setVisible(true);
        });
    }
}
