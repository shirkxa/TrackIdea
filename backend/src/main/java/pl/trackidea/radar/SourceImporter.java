package pl.trackidea.radar;

import java.util.List;

/** Jedno źródło Radaru. Dodanie nowego źródła = nowa implementacja tego interfejsu (bean Springa). */
public interface SourceImporter {

    /** Krótka nazwa źródła, zapisywana przy każdej innowacji (np. „OpenAlex”). */
    String name();

    /** Co to za źródło – pokazywane w panelu PCI. */
    String description();

    /** Adres strony źródła. */
    String homepage();

    /** Czy importer łączy się z internetem (wyłączany w trybie offline i w testach). */
    boolean live();

    /** Pobiera aktualne rekordy ze źródła. Może rzucić wyjątek – Radar zapisze błąd i przejdzie dalej. */
    List<SourceRecord> fetch() throws Exception;
}
