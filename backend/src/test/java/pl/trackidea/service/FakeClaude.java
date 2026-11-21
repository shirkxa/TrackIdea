package pl.trackidea.service;

/** Model AI podstawiony w testach: zwraca przygotowaną odpowiedź albo błąd, bez sieci. */
class FakeClaude extends ClaudeClient {

    int calls;
    String lastUserText;
    RuntimeException failure;
    Object answer;

    FakeClaude() {
        super("model-testowy", false);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public <T> T extract(Class<T> schema, String system, String userText) {
        calls++;
        lastUserText = userText;
        if (failure != null) {
            throw failure;
        }
        return schema.cast(answer);
    }

    static RecordClassifier.Answer answer(RecordAnalysis.Verdict verdict, RecordClassifier.Stage stage, int trl, int novelty) {
        return new RecordClassifier.Answer(verdict, "Uzasadnienie werdyktu.", "Szczepionka przeciw kleszczowemu zapaleniu mózgu",
                "Antygen szczepionkowy oparty na cząstkach wirusopodobnych.", "Brak skutecznej i taniej szczepionki.",
                "Rekombinowane cząstki wirusopodobne jako antygen.", "", "", pl.trackidea.domain.Area.TECHNOLOGICZNY,
                pl.trackidea.domain.InnovationType.TECHNICZNA, RecordClassifier.SectorChoice.ZDROWIE, stage, trl, novelty,
                "Nowy antygen, znana metoda wytwarzania.", java.util.List.of("Szczepionka", "wirusologia"));
    }
}
