package pl.trackidea.radar;

import java.util.List;

/**
 * Źródło potrzeb („kto czego szuka”) – odpowiednik {@link SourceImporter} dla tablicy potrzeb.
 * Dodanie nowego źródła = nowa implementacja tego interfejsu (bean Springa).
 */
public interface NeedSource {

    /** Krótka nazwa źródła, zapisywana przy każdej potrzebie (np. „BZP”). */
    String name();

    /** Co to za źródło – pokazywane w panelu PCI. */
    String description();

    /** Adres strony źródła. */
    String homepage();

    /** Czy źródło łączy się z internetem (wyłączane w trybie offline i w testach). */
    boolean live();

    /** Pobiera aktualne potrzeby. Może rzucić wyjątek – Radar zapisze błąd i przejdzie dalej. */
    List<NeedRecord> fetch() throws Exception;
}
