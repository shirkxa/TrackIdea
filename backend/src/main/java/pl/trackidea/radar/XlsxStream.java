package pl.trackidea.radar;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Strumieniowe czytanie dużych plików XLSX z otwartych danych (np. 36 tys. projektów UE)
 * bez wczytywania całego pliku do pamięci. Najpierw wiersze (indeksy tekstów), potem tylko potrzebne teksty.
 */
final class XlsxStream {

    /** Komórka: indeks wspólnego tekstu albo wartość wpisana wprost (liczba, data jako liczba). */
    record Cell(boolean shared, String raw) {
        int index() {
            return Integer.parseInt(raw);
        }
    }

    private XlsxStream() {
    }

    /** Wiersze pierwszego arkusza; zapamiętuje tylko wskazane kolumny (np. „A”, „B”). */
    static List<Map<String, Cell>> rows(Path xlsx, Set<String> columns) throws IOException, XMLStreamException {
        List<Map<String, Cell>> out = new ArrayList<>();
        try (ZipFile zip = new ZipFile(xlsx.toFile()); InputStream in = open(zip, "xl/worksheets/sheet1.xml")) {
            XMLStreamReader x = factory().createXMLStreamReader(in);
            Map<String, Cell> row = null;
            String col = null;
            String type = null;
            StringBuilder text = null;
            while (x.hasNext()) {
                int ev = x.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (x.getLocalName()) {
                        case "row" -> row = new HashMap<>();
                        case "c" -> {
                            String ref = x.getAttributeValue(null, "r");
                            col = ref == null ? null : ref.replaceAll("\\d", "");
                            type = x.getAttributeValue(null, "t");
                        }
                        case "v", "t" -> text = new StringBuilder();
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS && text != null) {
                    text.append(x.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    switch (x.getLocalName()) {
                        case "v", "t" -> {
                            if (row != null && col != null && columns.contains(col)) {
                                row.put(col, new Cell("s".equals(type), text.toString()));
                            }
                            text = null;
                        }
                        case "row" -> {
                            out.add(row);
                            row = null;
                        }
                        default -> { }
                    }
                }
            }
        }
        return out;
    }

    /** Teksty wspólne (sharedStrings) – tylko o podanych indeksach. */
    static Map<Integer, String> sharedStrings(Path xlsx, Set<Integer> wanted) throws IOException, XMLStreamException {
        Map<Integer, String> out = new HashMap<>();
        try (ZipFile zip = new ZipFile(xlsx.toFile()); InputStream in = open(zip, "xl/sharedStrings.xml")) {
            XMLStreamReader x = factory().createXMLStreamReader(in);
            int index = -1;
            StringBuilder text = null;
            boolean inT = false;
            while (x.hasNext()) {
                int ev = x.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    if ("si".equals(x.getLocalName())) {
                        index++;
                        text = wanted.contains(index) ? new StringBuilder() : null;
                    } else if ("t".equals(x.getLocalName())) {
                        inT = true;
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS && inT && text != null) {
                    text.append(x.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if ("t".equals(x.getLocalName())) {
                        inT = false;
                    } else if ("si".equals(x.getLocalName()) && text != null) {
                        out.put(index, text.toString());
                    }
                }
            }
        }
        return out;
    }

    private static InputStream open(ZipFile zip, String name) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null) {
            throw new IOException("Brak " + name + " w pliku XLSX");
        }
        return zip.getInputStream(e);
    }

    /** Bez DTD i encji zewnętrznych – plik pochodzi z internetu. */
    private static XMLInputFactory factory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return f;
    }
}
