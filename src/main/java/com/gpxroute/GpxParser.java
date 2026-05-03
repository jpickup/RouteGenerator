package com.gpxroute;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses GPX 1.1 XML files into {@link Route} objects using StAX streaming.
 *
 * <p>Handles both namespaced ({@code http://www.topografix.com/GPX/1/1}) and
 * non-namespaced GPX elements by matching on local names only.
 */
public class GpxParser {

    private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

    static {
        // Disable external entity processing for security
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    /**
     * Parses a GPX file from disk into a {@link Route}.
     *
     * @param path path to the GPX file
     * @return {@code Success} containing the Route, or {@code Failure} with a descriptive message
     */
    public Result<Route> parse(Path path) {
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            return Result.failure("Failed to parse GPX file '" + path + "': " + e.getMessage());
        }
        return parseStringWithPath(content, path.toString());
    }

    /**
     * Parses GPX XML from a string (used in testing).
     *
     * @param gpxXml the GPX XML content as a string
     * @return {@code Success} containing the Route, or {@code Failure} with a descriptive message
     */
    public Result<Route> parseString(String gpxXml) {
        return parseStringWithPath(gpxXml, "<string>");
    }

    private Result<Route> parseStringWithPath(String gpxXml, String pathLabel) {
        List<Waypoint> waypoints = new ArrayList<>();

        try {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(gpxXml));

            boolean inTrkpt = false;
            boolean inEle = false;
            Double currentLat = null;
            Double currentLon = null;
            boolean eleFound = false;
            StringBuilder eleText = new StringBuilder();

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        String localName = reader.getLocalName();
                        if ("trkpt".equals(localName)) {
                            inTrkpt = true;
                            eleFound = false;
                            eleText.setLength(0);
                            String latStr = reader.getAttributeValue(null, "lat");
                            String lonStr = reader.getAttributeValue(null, "lon");
                            currentLat = Double.parseDouble(latStr);
                            currentLon = Double.parseDouble(lonStr);
                        } else if ("ele".equals(localName) && inTrkpt) {
                            inEle = true;
                            eleText.setLength(0);
                        }
                    }
                    case XMLStreamConstants.CHARACTERS -> {
                        if (inEle) {
                            eleText.append(reader.getText());
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        String localName = reader.getLocalName();
                        if ("ele".equals(localName) && inTrkpt) {
                            inEle = false;
                            eleFound = true;
                        } else if ("trkpt".equals(localName)) {
                            if (!eleFound) {
                                reader.close();
                                return Result.failure(
                                    "GPX file '" + pathLabel + "' contains waypoints with missing altitude data"
                                );
                            }
                            double ele = Double.parseDouble(eleText.toString().trim());
                            waypoints.add(new Waypoint(currentLat, currentLon, ele));
                            inTrkpt = false;
                            currentLat = null;
                            currentLon = null;
                        }
                    }
                }
            }

            reader.close();

        } catch (XMLStreamException e) {
            return Result.failure("Failed to parse GPX file '" + pathLabel + "': " + e.getMessage());
        }

        if (waypoints.isEmpty()) {
            return Result.failure("GPX file '" + pathLabel + "' contains no track points");
        }

        return Result.success(new Route(waypoints));
    }
}
