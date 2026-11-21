package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tn.oliveconnect.common.Correction;

/**
 * Root of the equipment hierarchy (rule R8): one collection, the subclass is told by "type" and by Spring's "_class".
 */
@Document("equipment")
@CompoundIndex(name = "type_status", def = "{ 'type': 1, 'status': 1 }")
public abstract class Equipment {

    @Id
    private String id;
    @Indexed(unique = true)
    private String code;
    private EquipmentType type;
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;
    private List<Unavailability> unavailabilities = new ArrayList<>();
    private List<Correction> corrections = new ArrayList<>();

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
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public EquipmentType getType() { return type; }
    protected void setType(EquipmentType type) { this.type = type; }
    public EquipmentStatus getStatus() { return status; }
    public void setStatus(EquipmentStatus status) { this.status = status; }
    public List<Unavailability> getUnavailabilities() { return unavailabilities; }
    public List<Correction> getCorrections() { return corrections; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getLastModifiedAt() { return lastModifiedAt; }
    public String getLastModifiedBy() { return lastModifiedBy; }
    public Long getVersion() { return version; }
}
