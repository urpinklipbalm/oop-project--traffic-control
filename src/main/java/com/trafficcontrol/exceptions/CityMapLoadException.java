package com.trafficcontrol.exceptions;

/**
 * thrown by a PersistenceService implementation when a city map file is
 * missing, malformed, or otherwise can't be turned into a valid CityMap.
 * kept separate from InvalidRouteException because this is a file/io
 * problem, not a graph problem - they get handled differently by callers.
 */
public class CityMapLoadException extends Exception {

    public CityMapLoadException(String message) {
        super(message);
    }

    public CityMapLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
