package tn.oliveconnect.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tn.oliveconnect.resource.Bin;
import tn.oliveconnect.resource.Equipment;
import tn.oliveconnect.resource.EquipmentRepository;
import tn.oliveconnect.resource.Tractor;
import tn.oliveconnect.resource.Worker;
import tn.oliveconnect.resource.WorkerRepository;

/**
 * resources.csv: externalRef;type;name;phone;capacityKg with type WORKER, TRACTOR or BIN. Workers are upserted on
 * externalRef, equipment on code (= externalRef); fields entered by hand (skills, rate, serial number...) are kept.
 */
@Component
public class ResourcesCsvImporter implements DatasetImporter<ResourcesCsvImporter.ResourceLine> {

    private static final List<String> COLUMNS = List.of("externalRef", "type", "name", "phone", "capacityKg");
    private static final List<String> TYPES = List.of("WORKER", "TRACTOR", "BIN");

    public record ResourceLine(String externalRef, String type, String name, String phone, double capacityKg) {
    }

    private final WorkerRepository workerRepository;
    private final EquipmentRepository equipmentRepository;

    public ResourcesCsvImporter(WorkerRepository workerRepository, EquipmentRepository equipmentRepository) {
        this.workerRepository = workerRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    public String dataset() {
        return "RESOURCES";
    }

    @Override
    public ImportSource source() {
        return ImportSource.REFERENCE;
    }

    @Override
    public boolean accepts(String fileName) {
        return fileName.equals("resources.csv");
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public ParseResult<ResourceLine> parse(Path file) throws IOException {
        CsvFile.Content csv = CsvFile.read(file, COLUMNS);
        List<ImportError> errors = new ArrayList<>(csv.errors());
        List<Row<ResourceLine>> rows = new ArrayList<>();
        for (CsvFile.Line line : csv.lines()) {
            FieldReader reader = new FieldReader(line.number(), line.values()::get);
            ResourceLine resource = new ResourceLine(reader.text("externalRef"), reader.text("type"),
                    reader.text("name"), reader.optionalText("phone"), reader.optionalDecimal("capacityKg"));
            if (reader.error() != null) {
                errors.add(reader.error());
            } else {
                rows.add(new Row<>(line.number(), resource));
            }
        }
        return new ParseResult<>(rows, errors);
    }

    @Override
    public Optional<ImportError> validate(Row<ResourceLine> row) {
        ResourceLine resource = row.value();
        if (!TYPES.contains(resource.type())) {
            return Optional.of(new ImportError(row.line(), "type",
                    "'" + resource.type() + "' is not one of " + String.join(", ", TYPES)));
        }
        if (resource.capacityKg() < 0) {
            return Optional.of(new ImportError(row.line(), "capacityKg", resource.capacityKg() + " is negative"));
        }
        return Optional.empty();
    }

    @Override
    public List<ImportError> findDuplicates(List<Row<ResourceLine>> validRows) {
        return RowChecks.duplicateKeys(validRows, ResourceLine::externalRef, "externalRef");
    }

    @Override
    public PersistResult persist(List<ResourceLine> records) {
        List<ResourceLine> workers = records.stream().filter(r -> r.type().equals("WORKER")).toList();
        List<ResourceLine> equipment = records.stream().filter(r -> !r.type().equals("WORKER")).toList();
        PersistResult workerResult = persistWorkers(workers);
        PersistResult equipmentResult = persistEquipment(equipment);
        return new PersistResult(workerResult.inserted() + equipmentResult.inserted(),
                workerResult.updated() + equipmentResult.updated());
    }

    private PersistResult persistWorkers(List<ResourceLine> lines) {
        Map<String, Worker> stored = workerRepository.findAllByExternalRefIn(
                lines.stream().map(ResourceLine::externalRef).toList()).stream()
                .collect(Collectors.toMap(Worker::getExternalRef, Function.identity()));
        int updated = 0;
        List<Worker> toSave = new ArrayList<>();
        for (ResourceLine line : lines) {
            Worker worker = stored.get(line.externalRef());
            if (worker == null) {
                worker = new Worker();
                worker.setExternalRef(line.externalRef());
                worker.setCin(line.externalRef());   // cin is unique and required: the admin replaces it in the intranet
            } else {
                updated++;
            }
            int space = line.name().indexOf(' ');
            worker.setFirstName(space < 0 ? line.name() : line.name().substring(0, space));
            worker.setLastName(space < 0 ? "" : line.name().substring(space + 1).trim());
            if (line.phone() != null) {
                worker.setPhone(line.phone());
            }
            toSave.add(worker);
        }
        workerRepository.saveAll(toSave);
        return new PersistResult(toSave.size() - updated, updated);
    }

    private PersistResult persistEquipment(List<ResourceLine> lines) {
        Map<String, Equipment> stored = equipmentRepository.findAllByCodeIn(
                lines.stream().map(ResourceLine::externalRef).toList()).stream()
                .collect(Collectors.toMap(Equipment::getCode, Function.identity()));
        int updated = 0;
        List<Equipment> toSave = new ArrayList<>();
        for (ResourceLine line : lines) {
            Equipment equipment = stored.get(line.externalRef());
            if (equipment == null) {
                equipment = line.type().equals("BIN") ? new Bin() : new Tractor();
                equipment.setCode(line.externalRef());
            } else {
                updated++;
            }
            if (equipment instanceof Bin bin && line.type().equals("BIN")) {
                bin.setCapacityKg(line.capacityKg());
            }
            toSave.add(equipment);
        }
        equipmentRepository.saveAll(toSave);
        return new PersistResult(toSave.size() - updated, updated);
    }
}
