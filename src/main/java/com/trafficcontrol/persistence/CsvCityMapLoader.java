package com.trafficcontrol.persistence;

import com.trafficcontrol.engine.SimulationStatistics;
import com.trafficcontrol.exceptions.CityMapLoadException;
import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.Intersection;
import com.trafficcontrol.model.Position;
import com.trafficcontrol.model.Road;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes city map layouts in the CSV format documented at the
 * top of src/main/resources/maps/default-city.csv ([INTERSECTIONS] then
 * [ROADS], '#' comments skipped), writes SimulationStatistics out as a
 * metric,value CSV report, and reads/writes save/resume snapshots that
 * extend the same format with an optional third [STATISTICS] section.
 *
 * All parsing is fail-fast: every row is validated before anything is
 * added to the CityMap being built, and any error throws
 * CityMapLoadException immediately rather than returning a
 * partially-built result.
 */
public class CsvCityMapLoader implements PersistenceService {

    private static final String INTERSECTIONS_HEADER = "[INTERSECTIONS]";
    private static final String ROADS_HEADER = "[ROADS]";
    private static final String STATISTICS_HEADER = "[STATISTICS]";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private enum Section { NONE, INTERSECTIONS, ROADS, STATISTICS }

    /** Result of a single parsing pass: the map, plus any [STATISTICS] rows found (empty if none). */
    private record ParseResult(CityMap cityMap, Map<String, String> statistics) {
    }

    @Override
    public CityMap loadCityMap(String filePath) throws CityMapLoadException {
        return parseFile(filePath).cityMap();
    }

    /**
     * Loads a save/resume snapshot written by saveSnapshot(): the city map
     * plus the statistics totals captured at save time. Not part of the
     * PersistenceService interface - snapshot save/resume is the stretch
     * goal from docs/TODO_Ayesha.md, kept as extra methods on this class
     * rather than expanding the shared interface.
     */
    public SimulationSnapshot loadSnapshot(String filePath) throws CityMapLoadException {
        ParseResult result = parseFile(filePath);
        Map<String, String> stats = result.statistics();

        if (stats.isEmpty()) {
            throw new CityMapLoadException(
                    "File has no " + STATISTICS_HEADER + " section, so it isn't a snapshot: " + filePath);
        }

        long spawned = parseStatLong(stats, "vehiclesSpawned", filePath);
        long arrived = parseStatLong(stats, "vehiclesArrived", filePath);
        double avgWait = parseStatDouble(stats, "averageWaitTimeMillis", filePath);
        double avgTravel = parseStatDouble(stats, "averageTravelTimeMillis", filePath);

        return new SimulationSnapshot(result.cityMap(), spawned, arrived, avgWait, avgTravel);
    }

    private long parseStatLong(Map<String, String> stats, String key, String filePath)
            throws CityMapLoadException {
        String value = stats.get(key);
        if (value == null) {
            throw new CityMapLoadException("Snapshot file is missing \"" + key + "\": " + filePath);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new CityMapLoadException(
                    "Snapshot file has a non-numeric \"" + key + "\" (\"" + value + "\"): " + filePath);
        }
    }

    private double parseStatDouble(Map<String, String> stats, String key, String filePath)
            throws CityMapLoadException {
        String value = stats.get(key);
        if (value == null) {
            throw new CityMapLoadException("Snapshot file is missing \"" + key + "\": " + filePath);
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new CityMapLoadException(
                    "Snapshot file has a non-numeric \"" + key + "\" (\"" + value + "\"): " + filePath);
        }
    }

    private ParseResult parseFile(String filePath) throws CityMapLoadException {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(filePath));
        } catch (NoSuchFileException e) {
            throw new CityMapLoadException("File not found: " + filePath);
        } catch (IOException e) {
            throw new CityMapLoadException("Failed to read file: " + filePath, e);
        }

        CityMap cityMap = new CityMap();
        Map<String, Intersection> intersectionsById = new LinkedHashMap<>();
        Map<String, String> statistics = new LinkedHashMap<>();

        Section section = Section.NONE;
        boolean expectHeaderRow = false;

        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String rawLine = lines.get(i);
            String line = rawLine.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals(INTERSECTIONS_HEADER)) {
                section = Section.INTERSECTIONS;
                expectHeaderRow = true;
                continue;
            }
            if (line.equals(ROADS_HEADER)) {
                section = Section.ROADS;
                expectHeaderRow = true;
                continue;
            }
            if (line.equals(STATISTICS_HEADER)) {
                section = Section.STATISTICS;
                expectHeaderRow = true;
                continue;
            }
            if (expectHeaderRow) {
                // the "id,x,y" / "id,fromId,toId,..." / "metric,value" column-name
                // row - not data, skip it
                expectHeaderRow = false;
                continue;
            }

            switch (section) {
                case INTERSECTIONS ->
                        parseIntersectionRow(line, lineNumber, cityMap, intersectionsById);
                case ROADS ->
                        parseRoadRow(line, lineNumber, cityMap, intersectionsById);
                case STATISTICS ->
                        parseStatisticRow(line, lineNumber, statistics);
                case NONE ->
                        throw new CityMapLoadException(
                                "Data row at line " + lineNumber + " appears before an "
                                        + INTERSECTIONS_HEADER + ", " + ROADS_HEADER + ", or "
                                        + STATISTICS_HEADER + " section: " + rawLine);
            }
        }

        return new ParseResult(cityMap, statistics);
    }

    private void parseIntersectionRow(String line, int lineNumber, CityMap cityMap,
                                       Map<String, Intersection> intersectionsById)
            throws CityMapLoadException {
        String[] fields = line.split(",", -1);
        if (fields.length != 3) {
            throw new CityMapLoadException(
                    "Malformed intersection row at line " + lineNumber
                            + " (expected 3 columns, got " + fields.length + "): " + line);
        }

        String id = fields[0].trim();
        if (id.isEmpty()) {
            throw new CityMapLoadException("Intersection row at line " + lineNumber + " has an empty id");
        }
        if (intersectionsById.containsKey(id)) {
            throw new CityMapLoadException(
                    "Duplicate intersection id \"" + id + "\" at line " + lineNumber);
        }

        double x = parseDouble(fields[1].trim(), "x", lineNumber);
        double y = parseDouble(fields[2].trim(), "y", lineNumber);

        Intersection intersection = new Intersection(id, new Position(x, y));
        intersectionsById.put(id, intersection);
        cityMap.addIntersection(intersection);
    }

    private void parseRoadRow(String line, int lineNumber, CityMap cityMap,
                               Map<String, Intersection> intersectionsById)
            throws CityMapLoadException {
        String[] fields = line.split(",", -1);
        if (fields.length != 6) {
            throw new CityMapLoadException(
                    "Malformed road row at line " + lineNumber
                            + " (expected 6 columns, got " + fields.length + "): " + line);
        }

        String id = fields[0].trim();
        String fromId = fields[1].trim();
        String toId = fields[2].trim();

        Intersection from = intersectionsById.get(fromId);
        if (from == null) {
            throw new CityMapLoadException(
                    "Road \"" + id + "\" at line " + lineNumber
                            + " references unknown fromId \"" + fromId + "\"");
        }
        Intersection to = intersectionsById.get(toId);
        if (to == null) {
            throw new CityMapLoadException(
                    "Road \"" + id + "\" at line " + lineNumber
                            + " references unknown toId \"" + toId + "\"");
        }

        double lengthMeters = parseDouble(fields[3].trim(), "lengthMeters", lineNumber);
        int lanes = parseInt(fields[4].trim(), "lanes", lineNumber);

        String directionRaw = fields[5].trim();
        Direction approachDirection;
        try {
            approachDirection = Direction.valueOf(directionRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CityMapLoadException(
                    "Road \"" + id + "\" at line " + lineNumber
                            + " has an unknown approachDirection \"" + directionRaw
                            + "\" (expected NORTH, SOUTH, EAST or WEST)");
        }

        cityMap.addRoad(new Road(id, from, to, lengthMeters, lanes, approachDirection));
    }

    private void parseStatisticRow(String line, int lineNumber, Map<String, String> statistics)
            throws CityMapLoadException {
        String[] fields = line.split(",", -1);
        if (fields.length != 2) {
            throw new CityMapLoadException(
                    "Malformed statistics row at line " + lineNumber
                            + " (expected 2 columns, got " + fields.length + "): " + line);
        }
        statistics.put(fields[0].trim(), fields[1].trim());
    }

    private double parseDouble(String value, String fieldName, int lineNumber) throws CityMapLoadException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new CityMapLoadException(
                    "Non-numeric " + fieldName + " \"" + value + "\" at line " + lineNumber);
        }
    }

    private int parseInt(String value, String fieldName, int lineNumber) throws CityMapLoadException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CityMapLoadException(
                    "Non-numeric " + fieldName + " \"" + value + "\" at line " + lineNumber);
        }
    }

    @Override
    public void saveCityMap(CityMap cityMap, String filePath) throws CityMapLoadException {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(filePath))) {
            writeCityMapBody(writer, cityMap);
        } catch (IOException e) {
            throw new CityMapLoadException("Failed to write city map file: " + filePath, e);
        }
    }

    /**
     * Writes a save/resume snapshot: the city map plus the statistics
     * totals at the moment of saving, in one file. Not part of the
     * PersistenceService interface - see loadSnapshot()'s javadoc.
     */
    public void saveSnapshot(CityMap cityMap, SimulationStatistics statistics, String filePath)
            throws CityMapLoadException {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(filePath))) {
            writeCityMapBody(writer, cityMap);
            writer.newLine();
            writer.write(STATISTICS_HEADER);
            writer.newLine();
            writer.write("metric,value");
            writer.newLine();
            writeStat(writer, "vehiclesSpawned", statistics.getVehiclesSpawned());
            writeStat(writer, "vehiclesArrived", statistics.getVehiclesArrived());
            writeStat(writer, "vehiclesInTransit", statistics.getVehiclesInTransit());
            writeStat(writer, "averageWaitTimeMillis", statistics.getAverageWaitTimeMillis());
            writeStat(writer, "averageTravelTimeMillis", statistics.getAverageTravelTimeMillis());
        } catch (IOException e) {
            throw new CityMapLoadException("Failed to write snapshot file: " + filePath, e);
        }
    }

    private void writeCityMapBody(BufferedWriter writer, CityMap cityMap) throws IOException {
        writer.write(INTERSECTIONS_HEADER);
        writer.newLine();
        writer.write("id,x,y");
        writer.newLine();
        for (Intersection intersection : cityMap.getIntersections()) {
            Position position = intersection.getPosition();
            writer.write(intersection.getId() + "," + position.getX() + "," + position.getY());
            writer.newLine();
        }
        writer.newLine();

        writer.write(ROADS_HEADER);
        writer.newLine();
        writer.write("id,fromId,toId,lengthMeters,lanes,approachDirection");
        writer.newLine();
        for (Road road : cityMap.getRoads()) {
            writer.write(String.join(",",
                    road.getId(),
                    road.getFrom().getId(),
                    road.getTo().getId(),
                    String.valueOf(road.getLengthMeters()),
                    String.valueOf(road.getLanes()),
                    road.getApproachDirection().name()));
            writer.newLine();
        }
    }

    /**
     * Writes a simple "metric,value" CSV report of a completed (or
     * in-progress) simulation run. Kept as flat metric/value rows rather
     * than a single wide row so the report stays easy to read by eye and
     * easy to extend with new metrics later without reshuffling columns.
     */
    @Override
    public void exportStatistics(SimulationStatistics statistics, String filePath) throws CityMapLoadException {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(filePath))) {
            writer.write("# simulation statistics report");
            writer.newLine();
            writer.write("# generatedAt," + LocalDateTime.now().format(TIMESTAMP_FORMAT));
            writer.newLine();
            writer.write("metric,value");
            writer.newLine();

            writeStat(writer, "vehiclesSpawned", statistics.getVehiclesSpawned());
            writeStat(writer, "vehiclesArrived", statistics.getVehiclesArrived());
            writeStat(writer, "vehiclesInTransit", statistics.getVehiclesInTransit());
            writeStat(writer, "averageWaitTimeMillis", statistics.getAverageWaitTimeMillis());
            writeStat(writer, "averageTravelTimeMillis", statistics.getAverageTravelTimeMillis());
        } catch (IOException e) {
            throw new CityMapLoadException("Failed to write statistics export file: " + filePath, e);
        }
    }

    private void writeStat(BufferedWriter writer, String name, long value) throws IOException {
        writer.write(name + "," + value);
        writer.newLine();
    }

    private void writeStat(BufferedWriter writer, String name, double value) throws IOException {
        writer.write(name + "," + String.format("%.2f", value));
        writer.newLine();
    }
}