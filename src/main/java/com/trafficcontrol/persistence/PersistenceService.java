package com.trafficcontrol.persistence;

import com.trafficcontrol.engine.SimulationStatistics;
import com.trafficcontrol.exceptions.CityMapLoadException;
import com.trafficcontrol.model.CityMap;

/**
 * TODO(Ayesha): this interface is the real file-handling seam for the
 * project - see docs/TODO_Ayesha.md for the full task list and
 * src/main/resources/maps/default-city.csv for the expected city map
 * file format.
 *
 * implement this against actual files (CSV for the city map layout,
 * whatever format makes sense to you for snapshots/exports - document
 * the choice in docs/ProjectReport.md), then wire your implementation
 * into Main.java in place of DefaultCityMapFactory.createDefaultGrid().
 */
public interface PersistenceService {

    CityMap loadCityMap(String filePath) throws CityMapLoadException;

    void saveCityMap(CityMap cityMap, String filePath) throws CityMapLoadException;

    void exportStatistics(SimulationStatistics statistics, String filePath) throws CityMapLoadException;
}
