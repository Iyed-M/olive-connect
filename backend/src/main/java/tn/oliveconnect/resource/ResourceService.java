package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.BusinessRuleException;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.Correction;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.common.PageResponse;

/** Workers and equipment of the logistics manager (M4). No booking logic yet: deletion is a hard delete. */
@Service
public class ResourceService {

    private final WorkerRepository workerRepository;
    private final EquipmentRepository equipmentRepository;
    private final MongoTemplate mongoTemplate;

    public ResourceService(WorkerRepository workerRepository, EquipmentRepository equipmentRepository,
                           MongoTemplate mongoTemplate) {
        this.workerRepository = workerRepository;
        this.equipmentRepository = equipmentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ---- workers ----

    /** "active" is optional. Sorted by lastName (then id, so pages are stable). */
    public PageResponse<WorkerDto> listWorkers(Boolean active, int page, int size) {
        Query filter = new Query();
        if (active != null) {
            filter.addCriteria(Criteria.where("active").is(active));
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("lastName", "id"));
        List<Worker> workers = mongoTemplate.find(Query.of(filter).with(pageable), Worker.class);
        Page<Worker> result = PageableExecutionUtils.getPage(workers, pageable,
                () -> mongoTemplate.count(Query.of(filter).limit(-1).skip(-1), Worker.class));
        return PageResponse.of(result, WorkerDto::from);
    }

    public WorkerDto getWorker(String id) {
        return WorkerDto.from(findWorker(id));
    }

    public WorkerDto createWorker(WorkerRequestDto dto) {
        Worker worker = new Worker();
        applyWorker(worker, dto);
        return WorkerDto.from(workerRepository.save(worker));
    }

    public WorkerDto updateWorker(String id, WorkerRequestDto dto) {
        Worker worker = findWorker(id);
        applyWorker(worker, dto);
        return WorkerDto.from(workerRepository.save(worker));
    }

    public void deleteWorker(String id) {
        workerRepository.delete(findWorker(id));
    }

    public WorkerDto addWorkerUnavailability(String id, UnavailabilityDto dto) {
        Worker worker = findWorker(id);
        worker.getUnavailabilities().add(toUnavailability(dto));
        return WorkerDto.from(workerRepository.save(worker));
    }

    private Worker findWorker(String id) {
        return workerRepository.findById(id).orElseThrow(() -> new NotFoundException("Worker not found"));
    }

    private void applyWorker(Worker worker, WorkerRequestDto dto) {
        workerRepository.findByCin(dto.cin()).filter(other -> !other.getId().equals(worker.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("CIN_TAKEN", "This CIN is already used");
                });
        worker.setFirstName(dto.firstName().trim());
        worker.setLastName(dto.lastName().trim());
        worker.setCin(dto.cin());
        worker.setPhone(dto.phone());
        worker.setSkills(dto.skills() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(dto.skills()));
        worker.setDailyRate(dto.dailyRate());
        worker.setActive(dto.active() == null || dto.active());
    }

    // ---- equipment ----

    /** Both filters are optional. Sorted by code. */
    public PageResponse<EquipmentDto> listEquipment(EquipmentType type, EquipmentStatus status, int page, int size) {
        Query filter = new Query();
        if (type != null) {
            filter.addCriteria(Criteria.where("type").is(type));
        }
        if (status != null) {
            filter.addCriteria(Criteria.where("status").is(status));
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("code"));
        List<Equipment> equipment = mongoTemplate.find(Query.of(filter).with(pageable), Equipment.class);
        Page<Equipment> result = PageableExecutionUtils.getPage(equipment, pageable,
                () -> mongoTemplate.count(Query.of(filter).limit(-1).skip(-1), Equipment.class));
        return PageResponse.of(result, EquipmentDto::from);
    }

    public EquipmentDto getEquipment(String id) {
        return EquipmentDto.from(findEquipment(id));
    }

    public EquipmentDto createEquipment(EquipmentRequestDto dto) {
        Equipment equipment = switch (dto.type()) {
            case TRACTOR -> new Tractor();
            case BIN -> new Bin();
            case NET, SHAKER -> new Tool(ToolType.valueOf(dto.type().name()));
        };
        applyEquipment(equipment, dto);
        return EquipmentDto.from(equipmentRepository.save(equipment));
    }

    public EquipmentDto updateEquipment(String id, EquipmentRequestDto dto, String userId) {
        Equipment equipment = findEquipment(id);
        if (equipment.getType() != dto.type()) {
            throw new BusinessRuleException("EQUIPMENT_TYPE_IMMUTABLE", "The type of an equipment cannot change");
        }
        applyEquipment(equipment, dto);
        equipment.getCorrections().add(new Correction(Instant.now(), userId, "Equipment corrected by staff"));
        return EquipmentDto.from(equipmentRepository.save(equipment));
    }

    public void deleteEquipment(String id) {
        equipmentRepository.delete(findEquipment(id));
    }

    public EquipmentDto addEquipmentUnavailability(String id, UnavailabilityDto dto) {
        Equipment equipment = findEquipment(id);
        equipment.getUnavailabilities().add(toUnavailability(dto));
        return EquipmentDto.from(equipmentRepository.save(equipment));
    }

    private Equipment findEquipment(String id) {
        return equipmentRepository.findById(id).orElseThrow(() -> new NotFoundException("Equipment not found"));
    }

    /** Checks the fields the type needs, then copies them. Fields of other types are ignored. */
    private void applyEquipment(Equipment equipment, EquipmentRequestDto dto) {
        equipmentRepository.findByCode(dto.code()).filter(other -> !other.getId().equals(equipment.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("CODE_TAKEN", "This equipment code is already used");
                });
        equipment.setCode(dto.code());
        equipment.setStatus(dto.status() == null ? EquipmentStatus.AVAILABLE : dto.status());
        switch (equipment) {
            case Tractor t -> {
                t.setPlateNumber(required("plateNumber", dto.plateNumber()));
                t.setPowerHp(required("powerHp", dto.powerHp()));
            }
            case Bin b -> {
                b.setSerialNumber(dto.serialNumber());
                b.setCapacityKg(required("capacityKg", dto.capacityKg()));
            }
            case Tool t -> t.setStockQuantity(required("stockQuantity", dto.stockQuantity()));
            default -> throw new IllegalStateException("Unknown equipment class " + equipment.getClass());
        }
    }

    <T> T required(String field, T value) {
        if (value == null || value instanceof String s && s.isBlank()) {
            throw new FieldValidationException(field, "is required for this type");
        }
        return value;
    }

    Unavailability toUnavailability(UnavailabilityDto dto) {
        if (!dto.to().isAfter(dto.from())) {
            throw new FieldValidationException("to", "must be after from");
        }
        return new Unavailability(dto.from(), dto.to(), dto.reason());
    }
}
