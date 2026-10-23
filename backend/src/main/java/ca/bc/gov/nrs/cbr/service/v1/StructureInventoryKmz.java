package ca.bc.gov.nrs.cbr.service.v1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * The Structure Inventory – Detailed report as a KMZ, to see on a map: a placemark per structure at
 * its site's latitude and longitude, named by its Structure #, its pop-up legacy's fields
 * ({@code CBRKMLUtils}) in legacy's groups — the site; its load and inspection; the bridge; the
 * culvert; where it is.
 *
 * <p>Written from the Detailed CSV's rows, so the two agree on every structure and value: a row per
 * structure, values as the CSV writes them, codes as their descriptions. Legacy's yellow pushpin,
 * one folder, the report's order. Where it differs from legacy: a structure whose site has no
 * latitude or longitude is left out, where legacy wrote a point with empty coordinates, which is
 * not valid KML; and the file is UTF-8, as it declares, where legacy wrote the server's default.
 */
final class StructureInventoryKmz {

  private static final String KML_NAMESPACE = "http://www.opengis.net/kml/2.2";
  private static final String SCHEMA = "StructureInventoryDetailReport";
  private static final String PUSHPIN =
      "http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png";
  private static final String NAME = "Structure #";
  private static final String LONGITUDE = "GPS Coordinates in decimal degrees Longs";
  private static final String LATITUDE = "GPS Coordinates in decimal degrees Lats";

  /** Legacy's pop-up: its fields, by the CSV's headings, a blank line between its groups. */
  private static final List<List<String>> GROUPS = List.of(
      List.of("Site #", NAME, "Site Status", "Site Type", "Structure Type/Class",
          "Forest District", "BCTS Business Area", "Project File ID #", "Br.", "Road Name", "KM",
          "User KM", "Crossing Name"),
      List.of("Current Load Rating GVW (tonnes)", "Design Vehicle", "Design Load GVW (tonnes)",
          "Load Rating Review Required", "Inspection Status", "Last Inspection Date",
          "Next Planned Inspection Date"),
      List.of("Superstructure Type", "Portable?", "Deck Type", "Deck Width", "Abutment Left End",
          "Abutment Right End", "# Spans", "Total Length (metres)"),
      List.of("Culvert Type", "Culvert Material", "Opening Height", "Opening Width",
          "Culvert Length", "Culvert Gradient", "Inlet Cover Depth", "Outlet Cover Depth"),
      List.of(LONGITUDE, LATITUDE));

  private static final List<String> FIELDS = GROUPS.stream().flatMap(List::stream).toList();

  private StructureInventoryKmz() {}

  /** The KMZ: the KML, zipped as {@code doc.kml}. */
  static byte[] kmz(List<String> columns, List<List<String>> rows) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("doc.kml"));
      zip.write(kml(columns, rows));
      zip.closeEntry();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  /** The KML: a placemark per row with both coordinates. */
  static byte[] kml(List<String> columns, List<List<String>> rows) {
    Map<String, Integer> index = new HashMap<>();
    for (int column = 0; column < columns.size(); column++) {
      index.put(columns.get(column), column);
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      XMLStreamWriter xml = XMLOutputFactory.newInstance()
          .createXMLStreamWriter(bytes, StandardCharsets.UTF_8.name());
      xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
      xml.writeStartElement("kml");
      xml.writeDefaultNamespace(KML_NAMESPACE);
      xml.writeStartElement("Document");
      element(xml, "name", "StructureInventoryDetailReport.kmz");
      schema(xml);
      style(xml);
      xml.writeStartElement("Folder");
      element(xml, "name", SCHEMA);
      element(xml, "open", "1");
      for (List<String> row : rows) {
        String longitude = row.get(index.get(LONGITUDE));
        String latitude = row.get(index.get(LATITUDE));
        if (!longitude.isBlank() && !latitude.isBlank()) {
          placemark(xml, row, index, longitude, latitude);
        }
      }
      xml.writeEndElement();
      xml.writeEndElement();
      xml.writeEndElement();
      xml.writeEndDocument();
      xml.close();
    } catch (XMLStreamException e) {
      throw new IllegalStateException("The KML could not be written", e);
    }
    return bytes.toByteArray();
  }

  /** Legacy's schema: each field typed as text, shown under its heading. */
  private static void schema(XMLStreamWriter xml) throws XMLStreamException {
    xml.writeStartElement("Schema");
    xml.writeAttribute("name", SCHEMA);
    xml.writeAttribute("id", SCHEMA);
    for (String field : FIELDS) {
      xml.writeStartElement("SimpleField");
      xml.writeAttribute("type", "string");
      xml.writeAttribute("name", key(field));
      element(xml, "displayName", field);
      xml.writeEndElement();
    }
    xml.writeEndElement();
  }

  /** Legacy's yellow pushpin, and its pop-up: a table of the fields, a blank row between groups. */
  private static void style(XMLStreamWriter xml) throws XMLStreamException {
    xml.writeStartElement("Style");
    xml.writeAttribute("id", "structure");
    xml.writeStartElement("IconStyle");
    xml.writeStartElement("Icon");
    element(xml, "href", PUSHPIN);
    xml.writeEndElement();
    xml.writeEndElement();
    xml.writeStartElement("BalloonStyle");
    StringBuilder table = new StringBuilder("<table border=\"0\">");
    for (int group = 0; group < GROUPS.size(); group++) {
      if (group > 0) {
        table.append("<tr><td>&nbsp;</td></tr>");
      }
      for (String field : GROUPS.get(group)) {
        table.append("<tr><td><b>").append(html(field)).append("</b></td><td>$[")
            .append(SCHEMA).append('/').append(key(field)).append("]</td></tr>");
      }
    }
    table.append("</table>");
    xml.writeStartElement("text");
    xml.writeCData(table.toString());
    xml.writeEndElement();
    xml.writeEndElement();
    xml.writeEndElement();
  }

  private static void placemark(XMLStreamWriter xml, List<String> row,
      Map<String, Integer> index, String longitude, String latitude) throws XMLStreamException {
    xml.writeStartElement("Placemark");
    element(xml, "name", row.get(index.get(NAME)));
    element(xml, "styleUrl", "#structure");
    xml.writeStartElement("ExtendedData");
    xml.writeStartElement("SchemaData");
    xml.writeAttribute("schemaUrl", "#" + SCHEMA);
    for (String field : FIELDS) {
      xml.writeStartElement("SimpleData");
      xml.writeAttribute("name", key(field));
      xml.writeCharacters(row.get(index.get(field)));
      xml.writeEndElement();
    }
    xml.writeEndElement();
    xml.writeEndElement();
    xml.writeStartElement("Point");
    element(xml, "coordinates", longitude + "," + latitude + ",0");
    xml.writeEndElement();
    xml.writeEndElement();
  }

  private static void element(XMLStreamWriter xml, String name, String text)
      throws XMLStreamException {
    xml.writeStartElement(name);
    xml.writeCharacters(text);
    xml.writeEndElement();
  }

  /** A field's name in the schema, as legacy made it: "Site #" is {@code Site__}. */
  static String key(String field) {
    return field.replaceAll("[^A-Za-z0-9]", "_");
  }

  private static String html(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
