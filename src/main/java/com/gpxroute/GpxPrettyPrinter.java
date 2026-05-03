package com.gpxroute;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * Serializes a {@link Route} to GPX 1.1 XML using StAX ({@link XMLOutputFactory}).
 * No external dependencies — uses only the Java standard library.
 */
public class GpxPrettyPrinter {

    private static final String GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1";
    private static final String GPX_VERSION   = "1.1";
    private static final String GPX_CREATOR   = "gpx-route-generator";

    /**
     * Writes a Route to a GPX file at the given path.
     *
     * @param route      the route to serialize
     * @param metrics    pre-computed metrics for the route (written to {@code <desc>})
     * @param outputPath destination file path
     * @return {@link Result#success(Object)} with the path on success, or
     *         {@link Result#failure(String)} with a descriptive message if the path
     *         is not writable or an I/O error occurs
     */
    public Result<Path> write(Route route, RouteMetrics.Metrics metrics, Path outputPath) {
        return write(route, metrics, Set.of(), outputPath);
    }

    /**
     * Writes a Route to a GPX file, including a {@code <src>} element listing data sources.
     *
     * @param route      the route to serialize
     * @param metrics    pre-computed metrics
     * @param sources    set of source labels, e.g. {"GPX library", "OpenStreetMap"}
     * @param outputPath destination file path
     * @return {@link Result#success(Object)} with the path on success, or
     *         {@link Result#failure(String)} with a descriptive message on error
     */
    public Result<Path> write(Route route, RouteMetrics.Metrics metrics, Set<String> sources, Path outputPath) {
        try {
            String gpxContent = toGpxString(route, metrics, sources);
            Files.writeString(outputPath, gpxContent, StandardCharsets.UTF_8);
            return Result.success(outputPath);
        } catch (IOException e) {
            return Result.failure(
                    "Cannot write to output path '" + outputPath + "': " + e.getMessage());
        }
    }

    /**
     * Serializes a Route to a GPX XML string (used in testing).
     *
     * @param route   the route to serialize
     * @param metrics pre-computed metrics for the route
     * @return the GPX 1.1 XML as a string
     */
    public String toGpxString(Route route, RouteMetrics.Metrics metrics) {
        return toGpxString(route, metrics, Set.of());
    }

    /**
     * Serializes a Route to a GPX XML string, including a {@code <src>} element.
     *
     * @param route   the route to serialize
     * @param metrics pre-computed metrics for the route
     * @param sources set of source labels; if non-empty, a {@code <src>} element is written
     * @return the GPX 1.1 XML as a string
     */
    public String toGpxString(Route route, RouteMetrics.Metrics metrics, Set<String> sources) {
        StringWriter sw = new StringWriter();
        try {
            writeGpx(route, metrics, sources, sw);
        } catch (XMLStreamException e) {
            // StringWriter never throws IOException; XMLStreamException here is unexpected
            throw new IllegalStateException("Unexpected error serializing GPX", e);
        }
        return sw.toString();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void writeGpx(Route route, RouteMetrics.Metrics metrics, Set<String> sources, Writer out)
            throws XMLStreamException {

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(out);

        try {
            // <?xml version="1.0" encoding="UTF-8"?>
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            // <gpx version="1.1" creator="gpx-route-generator" xmlns="...">
            writer.writeStartElement("gpx");
            writer.writeDefaultNamespace(GPX_NAMESPACE);
            writer.writeAttribute("version", GPX_VERSION);
            writer.writeAttribute("creator", GPX_CREATOR);
            writer.writeCharacters("\n");

            // <metadata>
            writer.writeCharacters("  ");
            writer.writeStartElement("metadata");
            writer.writeCharacters("\n");

            // <desc>Distance: X.XX km, Elevation Gain: Y m</desc>
            writer.writeCharacters("    ");
            writer.writeStartElement("desc");
            writer.writeCharacters(formatDesc(metrics));
            writer.writeEndElement(); // </desc>
            writer.writeCharacters("\n");

            // <src>...</src> — only written when sources is non-empty
            if (!sources.isEmpty()) {
                String srcContent = String.join(", ", new TreeSet<>(sources));
                writer.writeCharacters("    ");
                writer.writeStartElement("src");
                writer.writeCharacters(srcContent);
                writer.writeEndElement(); // </src>
                writer.writeCharacters("\n");
            }

            writer.writeCharacters("  ");
            writer.writeEndElement(); // </metadata>
            writer.writeCharacters("\n");

            // <trk>
            writer.writeCharacters("  ");
            writer.writeStartElement("trk");
            writer.writeCharacters("\n");

            // <trkseg>
            writer.writeCharacters("    ");
            writer.writeStartElement("trkseg");
            writer.writeCharacters("\n");

            for (Waypoint wp : route.waypoints()) {
                // <trkpt lat="..." lon="..."><ele>...</ele></trkpt>
                writer.writeCharacters("      ");
                writer.writeStartElement("trkpt");
                writer.writeAttribute("lat", formatCoord(wp.lat()));
                writer.writeAttribute("lon", formatCoord(wp.lon()));
                writer.writeStartElement("ele");
                writer.writeCharacters(formatEle(wp.ele()));
                writer.writeEndElement(); // </ele>
                writer.writeEndElement(); // </trkpt>
                writer.writeCharacters("\n");
            }

            writer.writeCharacters("    ");
            writer.writeEndElement(); // </trkseg>
            writer.writeCharacters("\n");

            writer.writeCharacters("  ");
            writer.writeEndElement(); // </trk>
            writer.writeCharacters("\n");

            writer.writeEndElement(); // </gpx>
            writer.writeCharacters("\n");

            writer.writeEndDocument();
        } finally {
            writer.close();
        }
    }

    /**
     * Formats the {@code <desc>} content.
     * Distance: 2 decimal places; elevation gain: 0 decimal places (rounded).
     */
    private static String formatDesc(RouteMetrics.Metrics metrics) {
        return String.format("Distance: %.2f km, Elevation Gain: %.0f m",
                metrics.distanceKm(), metrics.elevationGainM());
    }

    /**
     * Formats a lat/lon coordinate with 6 decimal places to preserve precision.
     */
    private static String formatCoord(double value) {
        return String.format("%.6f", value);
    }

    /**
     * Formats an elevation value with 2 decimal places.
     */
    private static String formatEle(double value) {
        return String.format("%.2f", value);
    }
}
