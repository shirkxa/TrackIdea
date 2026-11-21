package pl.trackidea.domain;

/** Kryteria oceny z FAQ wyzwania „Radar innowacji” – jawne wagi (suma 100%). */
public enum Criterion implements Labeled {
    WDROZENIE("Poziom wdrożenia", 20, "Koncepcja, prototyp, testy, wdrożenie pilotażowe czy stosowane w praktyce?",
            "Koncepcja", "Stosowane w praktyce w wielu miejscach"),
    TRL("Gotowość technologiczna (TRL)", 15, "Na jakim poziomie TRL 1–9 jest rozwiązanie?",
            "TRL 1–2", "TRL 8–9"),
    INNOWACYJNOSC("Innowacyjność", 25, "Czy rozwiązanie jest nowe na tle podobnych (baza, patenty, publikacje)?",
            "Wiele identycznych", "Brak podobnych"),
    POTENCJAL("Potencjał zastosowania", 25, "Ile instytucji może to wykorzystać i jak łatwo to przenieść?",
            "Jedno miejsce, unikalne warunki", "Wiele instytucji, niski koszt wdrożenia"),
    WIARYGODNOSC("Wiarygodność", 15, "Czy informacje są potwierdzone przez źródło, efekty i inne bazy?",
            "Brak źródła i efektów", "Potwierdzone w kilku źródłach, zmierzone efekty");

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
