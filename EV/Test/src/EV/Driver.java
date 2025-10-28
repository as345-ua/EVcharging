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

    // Constructor with all fields
    public Driver(String id, String gmail, String name) {
        this.id = id;
        this.gmail = gmail;
        this.name = name;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getGmail() {
        return gmail;
    }

    public String getName() {
        return name;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setGmail(String gmail) {
        this.gmail = gmail;
    }

    public void setName(String name) {
        this.name = name;
    }

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
