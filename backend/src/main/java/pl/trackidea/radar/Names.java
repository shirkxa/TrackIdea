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
        s = s.replaceAll("(?i)\\s*SPOLKA Z OGRANICZONA ODPO?WIEDZIALNOSCIA", " SP. Z O.O.")
                .replaceAll("(?i)\\s*SPOLKA AKCYJNA", " S.A.");
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
                out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return out.toString();
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
