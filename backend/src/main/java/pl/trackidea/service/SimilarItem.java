package pl.trackidea.service;

/** Wynik wyszukiwania podobnych: innowacja, patent/publikacja albo potrzeba. */
public record SimilarItem(String kind, Long id, String title, String subtitle, int similarityPercent) {
}
