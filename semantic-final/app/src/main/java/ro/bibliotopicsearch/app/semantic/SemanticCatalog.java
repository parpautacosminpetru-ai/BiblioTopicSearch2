package ro.bibliotopicsearch.app.semantic;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class SemanticCatalog {
    static final class Category {
        final String label;
        final String descriptor;
        final List<String> explicitCues;

        Category(String label, String descriptor, String... explicitCues) {
            this.label = label;
            this.descriptor = descriptor;
            this.explicitCues = explicitCues == null
                    ? Collections.emptyList()
                    : Arrays.asList(explicitCues);
        }

        boolean hasExplicitCue(String text) {
            String value = normalize(text);
            for (String cue : explicitCues) {
                if (value.contains(normalize(cue))) return true;
            }
            return false;
        }
    }

    static final List<Category> CATEGORIES = Arrays.asList(
            new Category("DEFINIȚIE", "definiție explicită: ce este sau ce înseamnă ceva",
                    "înseamnă", "se definește", "este definit", "reprezintă"),
            new Category("CAUZĂ", "cauză explicită: motivul pentru care se întâmplă ceva",
                    "pentru că", "deoarece", "fiindcă", "din cauza", "motivul este"),
            new Category("CONSECINȚĂ", "consecință explicită: rezultat sau efect afirmat direct",
                    "prin urmare", "în consecință", "astfel", "a dus la", "rezultatul"),
            new Category("CONDIȚIE", "condiție explicită pentru ca ceva să se întâmple",
                    "dacă", "în cazul în care", "cu condiția", "numai dacă"),
            new Category("SCOP", "scop explicit sau obiectiv exprimat direct",
                    "pentru a", "cu scopul", "în vederea", "obiectivul"),
            new Category("EXEMPLU", "exemplu explicit care ilustrează o afirmație",
                    "de exemplu", "cum ar fi", "precum", "spre exemplu"),
            new Category("COMPARAȚIE", "comparație explicită între două lucruri",
                    "mai mult decât", "mai puțin decât", "la fel ca", "similar cu", "comparativ cu"),
            new Category("CONTRAST", "contrast explicit sau opoziție afirmată direct",
                    "însă", "dar", "în schimb", "spre deosebire", "pe de altă parte"),
            new Category("RESTRICȚIE", "restricție explicită: limitare direct afirmată",
                    "limitat", "restricționat", "nu poate", "doar", "numai"),
            new Category("OBLIGAȚIE", "obligație explicită: ceva trebuie făcut",
                    "trebuie", "este obligatoriu", "are obligația", "este necesar să"),
            new Category("PERMISIUNE", "permisiune explicită: ceva este permis",
                    "are voie", "este permis", "poate să", "este autorizat"),
            new Category("INTERDICȚIE", "interdicție explicită: ceva nu este permis",
                    "nu are voie", "este interzis", "nu este permis", "este interzisă"),
            new Category("SCHIMBARE", "schimbare explicită de stare, cantitate sau situație",
                    "s-a schimbat", "a devenit", "a crescut", "a scăzut", "s-a transformat"),
            new Category("RELAȚIE", "relație explicită între entități sau concepte",
                    "depinde de", "este legat de", "este asociat cu", "relația dintre", "între")
    );

    private SemanticCatalog() {}

    static String normalize(String value) {
        if (value == null) return "";
        String out = value.toLowerCase(Locale.ROOT);
        out = Normalizer.normalize(out, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return out.replaceAll("[^\\p{L}\\p{N}\\s'-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
