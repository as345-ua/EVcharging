package EV;

import java.io.Serializable;
import java.util.Objects;

public class Driver implements Serializable {
    private String id;
    private String gmail;
    private String name;

    // Default constructor for JSON deserialization
    public Driver() {
    }

    // Constructor with fields
    public Driver(String id, String gmail, String name) {
        this.id = id;
        this.gmail = gmail;
        this.name = name;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGmail() { return gmail; }
    public void setGmail(String gmail) { this.gmail = gmail; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String toString() {
        return String.format("Driver[id=%s, name=%s, gmail=%s]", id, name, gmail);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Driver driver = (Driver) o;
        return Objects.equals(id, driver.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}