package pl.trackidea.radar;

import java.util.Locale;
import java.util.Set;

/** Porządkowanie nazw ze źródeł (np. „URZAD MIASTA RZESZOWA” → „Urzad Miasta Rzeszowa”). */
final class Names {

    private static final Set<String> SMALL = Set.of("w", "i", "z", "na", "we", "im.", "of", "and", "the", "for", "de");

    private Names() {
    }

    static String titleCase(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        s = s.replaceAll("(?iu)\\s*SP[OÓ][LŁ]KA Z OGRANICZON[AĄ] ODPO?WIEDZIALNO[SŚ]CI[AĄ]", " SP. Z O.O.")
                .replaceAll("(?iu)\\s*SP[OÓ][LŁ]KA AKCYJNA", " S.A.");
        if (!s.equals(s.toUpperCase(Locale.ROOT))) {
            return s.trim();
        }
        StringBuilder out = new StringBuilder();
        for (String w : s.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            if (!out.isEmpty() && SMALL.contains(w)) {
                out.append(w);
            } else if (Set.of("sp.", "s.a.", "o.o.").contains(w)) {
                out.append(w.equals("s.a.") ? "S.A." : w);
            } else {
                out.append(capitalize(w));
            }
        }
        return out.toString();
    }

    /** Wielka litera na początku słowa i po łączniku („Bielsko-Biała”, „Naukowo-Technologiczne”). */
    private static String capitalize(String word) {
        StringBuilder out = new StringBuilder(word.length());
        boolean start = true;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == '-';
        }
        return out.toString();
    }

    /** Typ instytucji z nazwy (gmina, szkoła, uczelnia, firma); gdy nie wiadomo – {@code fallback}. */
    static pl.trackidea.domain.InstitutionType institutionType(String name, pl.trackidea.domain.InstitutionType fallback) {
        String n = name == null ? "" : name.toUpperCase(Locale.ROOT);
        if (n.matches(".*(SZKOŁ|LICEUM|TECHNIKUM|PRZEDSZKOL|SZKOL).*")) return pl.trackidea.domain.InstitutionType.SZKOLA;
        if (n.matches(".*(UNIWERSYTET|POLITECHNIK|AKADEMIA|UCZELNIA|INSTYTUT|ŁUKASIEWICZ).*")) return pl.trackidea.domain.InstitutionType.UCZELNIA;
        if (n.matches(".*(GMINA|POWIAT|MIASTO|URZĄD|URZAD|WOJEWÓDZTW|STAROSTWO|ZWIĄZEK).*")) return pl.trackidea.domain.InstitutionType.JST;
        if (n.matches(".*(SP\\. ?Z ?O\\. ?O\\.|S\\.A\\.|SPÓŁKA|FIRMA|P\\.?P\\.?H\\.?U).*")) return pl.trackidea.domain.InstitutionType.FIRMA;
        return fallback;
    }

    static String shorten(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) {
            return t;
        }
        int cut = t.lastIndexOf(' ', max);
        return t.substring(0, cut > max / 2 ? cut : max) + "…";
    }
}
