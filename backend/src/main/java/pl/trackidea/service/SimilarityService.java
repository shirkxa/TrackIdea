package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.PriorArt;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.NeedRepository;
import pl.trackidea.repo.PriorArtRepository;

import java.util.*;

/**
 * Wyszukiwanie podobnych rozwiązań (TF-IDF + podobieństwo cosinusowe).
 * Służy do: ograniczania dublowania prac, kryterium „Nowość” i dopasowań.
 */
@Service
@Transactional(readOnly = true)
public class SimilarityService {

    private final InnovationRepository innovations;
    private final NeedRepository needs;
    private final PriorArtRepository priorArt;

    public SimilarityService(InnovationRepository innovations, NeedRepository needs, PriorArtRepository priorArt) {
        this.innovations = innovations;
        this.needs = needs;
        this.priorArt = priorArt;
    }

    /** Indeks liczony na bieżąco z całego korpusu (przy skali pilotażu to milisekundy). */
    public Index index() {
        List<String> corpus = new ArrayList<>();
        innovations.findAll().forEach(i -> corpus.add(i.searchText()));
        needs.findAll().forEach(n -> corpus.add(n.searchText()));
        priorArt.findAll().forEach(p -> corpus.add(p.searchText()));
        return new Index(corpus);
    }

    public List<SimilarItem> similarInnovations(String text, Long excludeId, int limit) {
        Index idx = index();
        Map<String, Double> query = idx.vector(text);
        Set<Long> exclude = new HashSet<>();
        if (excludeId != null) {
            exclude.add(excludeId);
            innovations.findById(excludeId).ifPresent(self -> {
                if (self.getForkedFrom() != null) {
                    exclude.add(self.getForkedFrom().getId());
                }
                innovations.findByForkedFrom(self).forEach(f -> exclude.add(f.getId()));
            });
        }
        List<SimilarItem> out = new ArrayList<>();
        for (Innovation i : innovations.findAll()) {
            if (exclude.contains(i.getId()) || i.getStatus() == InnovationStatus.POLACZONA
                    || i.getStatus() == InnovationStatus.ARCHIWALNA || i.getForkedFrom() != null) {
                continue;
            }
            int pct = percent(TextVectorizer.cosine(query, idx.vector(i.searchText())));
            if (pct >= 10) {
                String where = i.getInstitution() != null ? i.getInstitution().getName() : "";
                out.add(new SimilarItem("INNOWACJA", i.getId(), i.getTitle(), where, pct));
            }
        }
        return top(out, limit);
    }

    public List<SimilarItem> similarPriorArt(String text, int limit) {
        Index idx = index();
        Map<String, Double> query = idx.vector(text);
        List<SimilarItem> out = new ArrayList<>();
        for (PriorArt p : priorArt.findAll()) {
            int pct = percent(TextVectorizer.cosine(query, idx.vector(p.searchText())));
            if (pct >= 10) {
                String sub = p.getSource() + " " + p.getRef() + (p.getHolder() != null ? " · " + p.getHolder() : "");
                out.add(new SimilarItem(p.getKind(), p.getId(), p.getTitle(), sub, pct));
            }
        }
        return top(out, limit);
    }

    static int percent(double cosine) {
        return (int) Math.round(Math.min(1.0, cosine) * 100);
    }

    private static List<SimilarItem> top(List<SimilarItem> items, int limit) {
        items.sort(Comparator.comparingInt(SimilarItem::similarityPercent).reversed());
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }

    /** Wagi IDF policzone dla korpusu. */
    public static final class Index {
        private final Map<String, Double> idf = new HashMap<>();
        private final double defaultIdf;

        Index(List<String> corpus) {
            Map<String, Integer> df = new HashMap<>();
            for (String doc : corpus) {
                for (String term : new HashSet<>(TextVectorizer.tokens(doc))) {
                    df.merge(term, 1, Integer::sum);
                }
            }
            int n = Math.max(1, corpus.size());
            df.forEach((term, count) -> idf.put(term, Math.log((n + 1.0) / (count + 1.0)) + 1.0));
            defaultIdf = Math.log(n + 1.0) + 1.0;
        }

        public Map<String, Double> vector(String text) {
            Map<String, Double> v = new HashMap<>();
            TextVectorizer.termCounts(text).forEach((term, tf) ->
                    v.put(term, (1 + Math.log(tf)) * idf.getOrDefault(term, defaultIdf)));
            return v;
        }

        public double similarity(String a, String b) {
            return TextVectorizer.cosine(vector(a), vector(b));
        }
    }
}
