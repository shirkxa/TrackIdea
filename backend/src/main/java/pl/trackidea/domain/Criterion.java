package pl.trackidea.domain;

/** Kryteria oceny potencjału z jawnymi wagami (suma 100%). */
public enum Criterion implements Labeled {
    SKALA("Skala problemu", 20, "Ilu instytucji / osób dotyczy problem?", "Jedna klasa lub urząd", "Problem w setkach instytucji"),
    NOWOSC("Nowość", 15, "Czy istnieją podobne rozwiązania (baza, patenty, publikacje)?", "Wiele identycznych", "Brak podobnych"),
    DOJRZALOSC("Dojrzałość", 15, "Na jakim etapie jest rozwiązanie?", "Pomysł", "Działa w kilku miejscach"),
    PRZENASZALNOSC("Przenaszalność", 20, "Jak łatwo wdrożyć to w innej instytucji?", "Wymaga unikalnych warunków", "Instrukcja + niski koszt"),
    WPLYW("Wpływ", 20, "Jaki efekt społeczny, ekonomiczny lub edukacyjny?", "Trudny do zmierzenia", "Zmierzone, duże efekty"),
    ZESPOL("Zespół i zasoby", 10, "Czy jest ktoś, kto może to rozwijać?", "Autor nieaktywny", "Zaangażowany zespół + zaplecze");

    private final String label;
    private final int weight;
    private final String question;
    private final String low;
    private final String high;

    Criterion(String label, int weight, String question, String low, String high) {
        this.label = label;
        this.weight = weight;
        this.question = question;
        this.low = low;
        this.high = high;
    }

    public String getLabel() {
        return label;
    }

    public int getWeight() {
        return weight;
    }

    public String getQuestion() {
        return question;
    }

    public String getLow() {
        return low;
    }

    public String getHigh() {
        return high;
    }
}
