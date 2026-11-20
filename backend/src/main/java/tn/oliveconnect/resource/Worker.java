package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("workers")
@CompoundIndex(name = "active_skills", def = "{ 'active': 1, 'skills': 1 }")
public class Worker {

    @Id
    private String id;
    private String firstName;
    private String lastName;
    @Indexed(unique = true)
    private String cin;
    private String phone;
    private Set<String> skills = new LinkedHashSet<>();
    private double dailyRate;
    private boolean active = true;
    private List<Unavailability> unavailabilities = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;
    @CreatedBy
    private String createdBy;
    @LastModifiedDate
    private Instant lastModifiedAt;
    @LastModifiedBy
    private String lastModifiedBy;
    @Version
    private Long version;

    public String getId() { return id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Set<String> getSkills() { return skills; }
    public void setSkills(Set<String> skills) { this.skills = skills; }
    public double getDailyRate() { return dailyRate; }
    public void setDailyRate(double dailyRate) { this.dailyRate = dailyRate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<Unavailability> getUnavailabilities() { return unavailabilities; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getLastModifiedAt() { return lastModifiedAt; }
    public String getLastModifiedBy() { return lastModifiedBy; }
    public Long getVersion() { return version; }
}
