package pl.trackidea.radar;

/** Źródło skończyło przygotowywać dane – Radar może od razu zrobić skan. */
public record SourceReadyEvent(String source) {
}
