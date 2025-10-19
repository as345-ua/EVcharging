package utils;

public class Config {
    private String bootstrapServers;
    private String driverId;

    public Config(String bootstrapServers, String driverId) {
        this.bootstrapServers = bootstrapServers;
        this.driverId = driverId;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getDriverId() {
        return driverId;
    }
}
