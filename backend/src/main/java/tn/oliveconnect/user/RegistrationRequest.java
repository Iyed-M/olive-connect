package tn.oliveconnect.user;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A visitor's request to join the cooperative. An Admin approves it (creates a Farmer) or rejects it. */
@Document("registration_requests")
@CompoundIndex(name = "status_createdAt", def = "{ 'status': 1, 'createdAt': -1 }")
public class RegistrationRequest {

    @Id
    private String id;
    private String firstName;
    private String lastName;
    @Indexed
    private String email;   // trimmed and lowercased
    private String phone;
    private String cin;
    private String message;
    private RegistrationStatus status = RegistrationStatus.PENDING;
    @CreatedDate
    private Instant createdAt;
    private String processedById;   // the Admin who approved or rejected
    private String createdUserId;   // set on approval

    public String getId() { return id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public RegistrationStatus getStatus() { return status; }
    public void setStatus(RegistrationStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getProcessedById() { return processedById; }
    public void setProcessedById(String processedById) { this.processedById = processedById; }
    public String getCreatedUserId() { return createdUserId; }
    public void setCreatedUserId(String createdUserId) { this.createdUserId = createdUserId; }
}
