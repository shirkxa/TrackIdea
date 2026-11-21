package pl.trackidea.radar;

/** Źródło jeszcze przygotowuje dane (np. pobiera duży plik w tle) – to nie błąd, Radar spróbuje ponownie. */
public class SourceNotReady extends Exception {
    public SourceNotReady(String message) {
        super(message);
    }
}
