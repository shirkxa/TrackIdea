package pl.trackidea.domain;

public enum PathType implements Labeled {
    OTWARTA_REPLIKACJA("Otwarta replikacja (fork)"),
    ROZWOJ_PCI("Rozwój i prototypowanie w PCI"),
    KOMERCJALIZACJA("Ochrona IP i komercjalizacja"),
    GRANT("Finansowanie publiczne / grant"),
    PILOTAZ_BR("Pilotaż lub współpraca B+R"),
    POLACZENIE_SIL("Połączenie sił z podobną innowacją");

    private final String label;

    PathType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
