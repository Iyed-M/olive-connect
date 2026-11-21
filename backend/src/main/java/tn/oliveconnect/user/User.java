package tn.oliveconnect.user;

import java.time.Instant;
import java.util.EnumSet;
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

@Document("users")
@CompoundIndex(name = "roles_enabled", def = "{ 'roles': 1, 'enabled': 1 }")
public class User {

    @Id
    private String id;
    @Indexed(unique = true)
    private String email;   // always trimmed and lowercased
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String phone;
    private Set<Role> roles = EnumSet.noneOf(Role.class);
    private boolean enabled = true;
    private boolean onboardingDone = false;
    private boolean mustChangePassword = false;   // true after an admin approves a registration (temporary password)
    private FarmerProfile farmerProfile;   // only meaningful when roles contains FARMER
    private String assignedMillId;         // set by Admin for a Mill manager

    // filled by Mongo auditing
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
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isOnboardingDone() { return onboardingDone; }
    public void setOnboardingDone(boolean onboardingDone) { this.onboardingDone = onboardingDone; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public FarmerProfile getFarmerProfile() { return farmerProfile; }
    public void setFarmerProfile(FarmerProfile farmerProfile) { this.farmerProfile = farmerProfile; }
    public String getAssignedMillId() { return assignedMillId; }
    public void setAssignedMillId(String assignedMillId) { this.assignedMillId = assignedMillId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getLastModifiedAt() { return lastModifiedAt; }
    public String getLastModifiedBy() { return lastModifiedBy; }
    public Long getVersion() { return version; }
}
