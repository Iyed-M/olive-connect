package tn.oliveconnect.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.OliveVariety;
import tn.oliveconnect.orchard.OliveVarietyRepository;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.orchard.Plot;
import tn.oliveconnect.user.Delegation;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;

/**
 * orchards.csv (docs/07 §2): one line per plot, lines are grouped by externalRef into one orchard with its
 * plots[] (rule R2). Upsert on externalRef: the source owns name, location, delegation and plots; what was
 * entered by hand (harvest status, maturity, owner when the file gives no known member) is kept.
 */
@Component
public class OrchardCsvImporter implements DatasetImporter<OrchardCsvImporter.OrchardLines> {

    private static final List<String> COLUMNS = List.of("externalRef", "name", "memberNumber", "lng", "lat",
            "delegation", "plotCode", "varietyCode", "areaHa", "treeCount", "plantingYear", "irrigated");

    /** The orchard-level columns of the first line, and one PlotLine per csv line. */
    public record OrchardLines(String externalRef, String name, String memberNumber, double lng, double lat,
            String delegation, List<PlotLine> plots) {
    }

    public record PlotLine(int line, String code, String varietyCode, double areaHa, int treeCount,
            int plantingYear, boolean irrigated) {
    }

    private final OrchardRepository orchardRepository;
    private final OliveVarietyRepository varietyRepository;
    private final MongoTemplate mongoTemplate;

    public OrchardCsvImporter(OrchardRepository orchardRepository, OliveVarietyRepository varietyRepository,
            MongoTemplate mongoTemplate) {
        this.orchardRepository = orchardRepository;
        this.varietyRepository = varietyRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public String dataset() {
        return "ORCHARDS";
    }

    @Override
    public ImportSource source() {
        return ImportSource.REFERENCE;
    }

    @Override
    public boolean accepts(String fileName) {
        return fileName.equals("orchards.csv");
    }

    @Override
    public int order() {
        return 30;
    }

    /**
     * Groups the lines. A row with a bad value, or whose orchard columns differ from the first line of its
     * orchard, rejects the whole orchard (we never store half of an orchard).
     */
    @Override
    public ParseResult<OrchardLines> parse(Path file) throws IOException {
        CsvFile.Content csv = CsvFile.read(file, COLUMNS);
        List<ImportError> errors = new ArrayList<>(csv.errors());
        Map<String, OrchardLines> groups = new LinkedHashMap<>();
        Map<String, Integer> firstLines = new LinkedHashMap<>();
        Set<String> rejected = new HashSet<>();

        for (CsvFile.Line line : csv.lines()) {
            FieldReader reader = new FieldReader(line.number(), line.values()::get);
            String ref = reader.text("externalRef");
            String name = reader.text("name");
            String member = reader.optionalText("memberNumber");
            double lng = reader.decimal("lng");
            double lat = reader.decimal("lat");
            String delegation = reader.text("delegation");
            PlotLine plot = new PlotLine(line.number(), reader.text("plotCode"), reader.text("varietyCode"),
                    reader.decimal("areaHa"), reader.integer("treeCount"), reader.integer("plantingYear"),
                    reader.bool("irrigated"));
            if (reader.error() != null) {
                errors.add(reader.error());
                if (ref != null) {
                    rejected.add(ref);
                }
                continue;
            }
            OrchardLines group = groups.get(ref);
            if (group == null) {
                groups.put(ref, new OrchardLines(ref, name, member, lng, lat, toDelegationCode(delegation),
                        new ArrayList<>(List.of(plot))));
                firstLines.put(ref, line.number());
                continue;
            }
            String different = differentField(group, name, member, lng, lat, toDelegationCode(delegation));
            if (different != null) {
                errors.add(new ImportError(line.number(), different,
                        "differs from line " + firstLines.get(ref) + " of the same orchard " + ref));
                rejected.add(ref);
            } else {
                group.plots().add(plot);
            }
        }

        List<Row<OrchardLines>> rows = new ArrayList<>();
        groups.forEach((ref, group) -> {
            if (!rejected.contains(ref)) {
                rows.add(new Row<>(firstLines.get(ref), group));
            }
        });
        return new ParseResult<>(rows, errors);
    }

    private static String differentField(OrchardLines group, String name, String member, double lng, double lat,
            String delegation) {
        if (!group.name().equals(name)) {
            return "name";
        }
        if (!java.util.Objects.equals(group.memberNumber(), member)) {
            return "memberNumber";
        }
        if (group.lng() != lng) {
            return "lng";
        }
        if (group.lat() != lat) {
            return "lat";
        }
        return group.delegation().equals(delegation) ? null : "delegation";
    }

    /** "Sakiet Ezzit" in the files becomes SAKIET_EZZIT; the simulator spells Jebiniana "Jebeniana". */
    private static String toDelegationCode(String name) {
        String code = name.trim().toUpperCase().replaceAll("[\\s'-]+", "_");
        return code.equals("JEBENIANA") ? "JEBINIANA" : code;
    }

    /** Types and ranges of docs/07 §3 (the references are checked in findDuplicates). */
    @Override
    public Optional<ImportError> validate(Row<OrchardLines> row) {
        OrchardLines orchard = row.value();
        ImportError outside = RowChecks.coordinates(row.line(), orchard.lng(), orchard.lat());
        if (outside != null) {
            return Optional.of(outside);
        }
        try {
            Delegation.valueOf(orchard.delegation());
        } catch (IllegalArgumentException e) {
            return Optional.of(new ImportError(row.line(), "delegation", "'" + orchard.delegation() + "' is unknown"));
        }
        Set<String> codes = new HashSet<>();
        for (PlotLine plot : orchard.plots()) {
            String reason = null;
            String field = null;
            if (plot.areaHa() <= 0) {
                field = "areaHa";
                reason = plot.areaHa() + " must be greater than 0";
            } else if (plot.treeCount() < 0) {
                field = "treeCount";
                reason = plot.treeCount() + " is negative";
            } else if (plot.plantingYear() < 1900 || plot.plantingYear() > Year.now().getValue()) {
                field = "plantingYear";
                reason = plot.plantingYear() + " is not a valid year";
            } else if (!codes.add(plot.code())) {
                field = "plotCode";
                reason = "duplicate plotCode '" + plot.code() + "'";
            }
            if (reason != null) {
                return Optional.of(new ImportError(plot.line(), field, reason));
            }
        }
        return Optional.empty();
    }

    /**
     * Checks that need the database, done for the whole file at once: the varieties exist, and the plots
     * do not add more than 5 % of area to an orchard that is already stored. Errors use the line of the
     * orchard (its first line), which is what ImportService removes.
     */
    @Override
    public List<ImportError> findDuplicates(List<Row<OrchardLines>> validRows) {
        Set<String> varietyCodes = new HashSet<>();
        Set<String> refs = new HashSet<>();
        for (Row<OrchardLines> row : validRows) {
            refs.add(row.value().externalRef());
            row.value().plots().forEach(plot -> varietyCodes.add(plot.varietyCode()));
        }
        Set<String> knownVarieties = varietyRepository.findAllById(varietyCodes).stream()
                .map(OliveVariety::getCode).collect(Collectors.toSet());
        Map<String, Orchard> stored = orchardRepository.findAllByExternalRefIn(refs).stream()
                .collect(Collectors.toMap(Orchard::getExternalRef, Function.identity()));

        List<ImportError> errors = new ArrayList<>();
        for (Row<OrchardLines> row : validRows) {
            OrchardLines orchard = row.value();
            ImportError error = null;
            for (PlotLine plot : orchard.plots()) {
                if (!knownVarieties.contains(plot.varietyCode())) {
                    error = new ImportError(row.line(), "varietyCode",
                            "plot " + plot.code() + " (line " + plot.line() + "): varietyCode '" + plot.varietyCode() + "' unknown");
                    break;
                }
            }
            Orchard existing = stored.get(orchard.externalRef());
            double area = orchard.plots().stream().mapToDouble(PlotLine::areaHa).sum();
            if (error == null && existing != null && existing.getAreaHa() > 0 && area > existing.getAreaHa() * 1.05) {
                error = new ImportError(row.line(), "areaHa", "plots total " + round(area) + " ha, more than 5 % above the stored "
                        + round(existing.getAreaHa()) + " ha");
            }
            if (error != null) {
                errors.add(error);
            }
        }
        return errors;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    @Override
    public PersistResult persist(List<OrchardLines> records) {
        Set<String> refs = records.stream().map(OrchardLines::externalRef).collect(Collectors.toSet());
        Map<String, Orchard> stored = orchardRepository.findAllByExternalRefIn(refs).stream()
                .collect(Collectors.toMap(Orchard::getExternalRef, Function.identity()));
        Map<String, User> farmers = findFarmers(records.stream().map(OrchardLines::memberNumber).toList());

        int updated = 0;
        List<Orchard> toSave = new ArrayList<>();
        for (OrchardLines record : records) {
            Orchard orchard = stored.get(record.externalRef());
            if (orchard == null) {
                orchard = new Orchard();
                orchard.setExternalRef(record.externalRef());
            } else {
                updated++;
            }
            orchard.setName(record.name());
            orchard.setLocation(new GeoPoint(record.lng(), record.lat()));
            orchard.setDelegation(record.delegation());
            orchard.setPlots(record.plots().stream().map(OrchardCsvImporter::toPlot).toList());
            orchard.recomputeTotals();
            User farmer = record.memberNumber() == null ? null : farmers.get(record.memberNumber());
            if (farmer != null) {   // an unknown member leaves the orchard unassigned (or keeps its manual owner)
                orchard.setOwnerId(farmer.getId());
                orchard.setOwner(new LinkedHashMap<>(Map.of("displayName", displayName(farmer))));
            }
            toSave.add(orchard);
        }
        orchardRepository.saveAll(toSave);
        return new PersistResult(toSave.size() - updated, updated);
    }

    private Map<String, User> findFarmers(Collection<String> memberNumbers) {
        Set<String> numbers = memberNumbers.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (numbers.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where("farmerProfile.memberNumber").in(numbers).and("roles").is(Role.FARMER));
        return mongoTemplate.find(query, User.class).stream()
                .collect(Collectors.toMap(user -> user.getFarmerProfile().getMemberNumber(), Function.identity()));
    }

    private static String displayName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        return (first + " " + last).trim();
    }

    private static Plot toPlot(PlotLine line) {
        Plot plot = new Plot();
        plot.setCode(line.code());
        plot.setVarietyCode(line.varietyCode());
        plot.setAreaHa(line.areaHa());
        plot.setTreeCount(line.treeCount());
        plot.setPlantingYear(line.plantingYear());
        plot.setIrrigated(line.irrigated());
        return plot;
    }
}
