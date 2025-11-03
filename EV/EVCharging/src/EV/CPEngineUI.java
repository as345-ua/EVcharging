package EV;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Simple Swing UI for Charging Point Engine
 * Task 2: Integrated with EV_CP_Engine
 */
public class CPEngineUI extends JFrame {
    private JLabel cpIdLabel;
    private JLabel statusLabel;
    private JLabel driverLabel;
    private JLabel powerLabel;
    private JLabel energyLabel;
    private JProgressBar chargingProgress;
    private JTextArea logArea;
    private JButton stopButton;
    private JButton faultButton;

    private String cpId;
    private EV_CP_Engine engine; // Task 2: Backend engine reference

    public CPEngineUI(String cpId, String kafkaServers) {
        super("Charging Point Engine - CP " + cpId);
        this.cpId = cpId;

        // Set up UI
        setupUI();
        // Task 2: Initialize and start backend engine
        this.engine = new EV_CP_Engine(kafkaServers, cpId);
        this.engine.setUI(this);
        new Thread(() -> engine.start()).start();

        // Set frame properties
        setSize(600, 550);
        setLocationRelativeTo(null);
        // Task 2: Add window listener to stop engine on close
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // We handle close manually
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                engine.stop();
            }
        });
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        // Top Panel - CP Info
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        cpIdLabel = new JLabel("Charging Point: CP-" + cpId);
        cpIdLabel.setFont(new Font("Arial", Font.BOLD, 18));
        cpIdLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        statusLabel = new JLabel("INITIALIZING...");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(Color.GRAY);

        topPanel.add(cpIdLabel);
        topPanel.add(statusLabel);

        add(topPanel, BorderLayout.NORTH);
        
        // Center Panel - Charging Info
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Charging status panel
        JPanel statusPanel = new JPanel(new GridLayout(4, 1, 5, 10));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Current Session"));

        driverLabel = new JLabel("Driver: Not charging");
        driverLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        powerLabel = new JLabel("Power: 0.0 kW");
        powerLabel.setFont(new Font("Arial", Font.BOLD, 14));

        energyLabel = new JLabel("Energy delivered: 0.0000 kWh");
        energyLabel.setFont(new Font("Arial", Font.BOLD, 14));

        chargingProgress = new JProgressBar(0, 100);
        chargingProgress.setStringPainted(true);
        chargingProgress.setString("Idle");
        chargingProgress.setPreferredSize(new Dimension(400, 30));

        statusPanel.add(driverLabel);
        statusPanel.add(powerLabel);
        statusPanel.add(energyLabel);
        statusPanel.add(chargingProgress);

        centerPanel.add(statusPanel, BorderLayout.NORTH);
        
        // Log panel
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logPanel.add(scrollPane, BorderLayout.CENTER);

        centerPanel.add(logPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom Panel - Control Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        
        stopButton = new JButton("Stop Charging");
        stopButton.setFont(new Font("Arial", Font.BOLD, 12));
        stopButton.setEnabled(false);
        stopButton.setPreferredSize(new Dimension(150, 35));

        faultButton = new JButton("Simulate Fault");
        faultButton.setFont(new Font("Arial", Font.BOLD, 12));
        faultButton.setPreferredSize(new Dimension(150, 35));

        JButton clearButton = new JButton("Clear Log");
        clearButton.setFont(new Font("Arial", Font.BOLD, 12));
        clearButton.setPreferredSize(new Dimension(150, 35));

        bottomPanel.add(stopButton);
        bottomPanel.add(faultButton);
        bottomPanel.add(clearButton);

        add(bottomPanel, BorderLayout.SOUTH);

        // Add action listeners
        clearButton.addActionListener(e -> logArea.setText(""));
        
        // Task 2: Wire up buttons to engine
        setStopButtonListener(e -> engine.stopCharging());
        setFaultButtonListener(e -> engine.simulateFault());
    }

    public void setStopButtonListener(ActionListener listener) {
        stopButton.addActionListener(listener);
    }

    public void setFaultButtonListener(ActionListener listener) {
        faultButton.addActionListener(listener);
    }

    // Public methods for CP Engine to update UI
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            String icon;
            switch (status.toUpperCase()) {
                case "AVAILABLE":
                    icon = "[AVAILABLE]";
                    break;
                case "CHARGING":
                    icon = "[CHARGING]";
                    break;
                case "BROKEN":
                    icon = "[BROKEN]";
                    break;
                case "OUT_OF_SERVICE":
                    icon = "[OUT_OF_SERVICE]";
                    break;
                default:
                    icon = "[UNKNOWN]";
            }
            statusLabel.setText(icon + " " + status);
            statusLabel.setForeground(color);
        });
    }

    public void startCharging(String driverId, double powerKw) {
        SwingUtilities.invokeLater(() -> {
            driverLabel.setText("Driver: " + driverId);
            powerLabel.setText(String.format("Power: %.1f kW", powerKw));
            energyLabel.setText("Energy delivered: 0.0000 kWh");
            chargingProgress.setString("Charging...");
            chargingProgress.setValue(0);
            stopButton.setEnabled(true);
            faultButton.setEnabled(false); // Can't fault while charging (for this simulation)
            updateStatus("CHARGING", new Color(0, 100, 200));
        });
    }

    public void updateChargingProgress(double energyKwh, int progress) {
        SwingUtilities.invokeLater(() -> {
            energyLabel.setText(String.format("Energy delivered: %.4f kWh", energyKwh));
            int p = Math.min(progress, 100);
            chargingProgress.setValue(p);
            chargingProgress.setString(String.format("Charging: %d%%", p));
        });
    }

    public void stopCharging() {
        SwingUtilities.invokeLater(() -> {
            driverLabel.setText("Driver: Not charging");
            powerLabel.setText("Power: 0.0 kW");
            chargingProgress.setValue(0);
            chargingProgress.setString("Idle");
            stopButton.setEnabled(false);
            faultButton.setEnabled(true);
            updateStatus("AVAILABLE", new Color(0, 150, 0));
        });
    }

    public void showFault() {
        SwingUtilities.invokeLater(() -> {
            driverLabel.setText("Driver: Not charging");
            powerLabel.setText("Power: 0.0 kW");
            energyLabel.setText("Energy delivered: 0.0000 kWh");
            chargingProgress.setValue(0);
            chargingProgress.setString("FAULT");
            stopButton.setEnabled(false);
            faultButton.setEnabled(true);
            updateStatus("BROKEN", Color.RED);
        });
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java EV.CPEngineUI <kafka_bootstrap_servers> <cp_id>");
            System.out.println("Example: java EV.CPEngineUI localhost:9092 1");
            return;
        }

        String kafkaServers = args[0];
        String cpId = args[1];

        SwingUtilities.invokeLater(() -> {
            CPEngineUI ui = new CPEngineUI(cpId, kafkaServers);
            ui.setVisible(true);
        });
    }
}