package pl.trackidea.service;

import pl.trackidea.domain.*;

import java.util.List;

/** Dane karty innowacji z formularza (tworzenie i edycja). */
public record InnovationForm(
        String title,
        String summary,
        String problem,
        String solution,
        String requirements,
        String results,
        InnovationType type,
        Sector sector,
        Maturity maturity,
        TransferModel transferModel,
        String ipStatus,
        List<String> tags,
        List<String> links,
        Long institutionId,
        /* true = zgłaszam cudzy pomysł (rola zwiadowcy) */
        Boolean onBehalf,
        /* imię i nazwisko / kontakt autora, gdy zgłasza zwiadowca */
        String authorInfo) {
}
