package pl.trackidea.service;

import java.text.Normalizer;
import java.util.*;

/**
 * Prosta wektoryzacja tekstu po polsku: małe litery, usunięcie polskich znaków,
 * stop-słowa i „stemming” przez obcięcie do 6 znaków (radzi sobie z odmianą: mentoringu → mentor).
 * Działa offline i jest w pełni wytłumaczalna – można ją podmienić na embeddingi modelu językowego.
 */
public final class TextVectorizer {

    private static final int STEM_LENGTH = 6;

    private static final Set<String> STOP_WORDS = Set.copyOf(List.of(
            "a", "aby", "ale", "albo", "bez", "by", "byc", "byl", "byla", "byli", "bylo", "beda", "bedzie", "ci", "co",
            "czy", "dla", "do", "gdy", "gdzie", "go", "i", "ich", "ile", "im", "ja", "jak", "jaki", "jako", "je", "jego",
            "jej", "jest", "jesli", "juz", "ktora", "ktore", "ktory", "ktorych", "ktorzy", "lub", "ma", "maja", "moze",
            "na", "nad", "nie", "nich", "o", "od", "oraz", "po", "pod", "przez", "przy", "sa", "se", "sie", "so", "ta",
            "tak", "takze", "tam", "te", "tego", "tej", "ten", "to", "tu", "ty", "tym", "u", "w", "we", "wiec", "z", "za",
            "ze", "oraz", "ktorej", "jednak", "tylko", "bardzo", "wiecej", "ponad", "wszystkich", "kazdy", "nasz",
            "nasze", "naszej", "swoje", "swoich", "swoj", "jego", "tez", "the", "and", "of", "for", "in", "on", "with"));

    private TextVectorizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }

    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : normalize(text).split("[^a-z0-9]+")) {
            if (raw.length() < 3 || STOP_WORDS.contains(raw)) {
                continue;
            }
            out.add(raw.length() > STEM_LENGTH ? raw.substring(0, STEM_LENGTH) : raw);
        }
        return out;
    }

    public static Map<String, Integer> termCounts(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens(text)) {
            tf.merge(t, 1, Integer::sum);
        }
        return tf;
    }

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> large = small == a ? b : a;
        double dot = 0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double other = large.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        return dot / (norm(a) * norm(b));
    }

    private static double norm(Map<String, Double> v) {
        double s = 0;
        for (double x : v.values()) {
            s += x * x;
        }
        return Math.sqrt(s);
    }
}
