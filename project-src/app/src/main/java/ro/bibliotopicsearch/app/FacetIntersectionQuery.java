package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pure deterministic parser for v7 facet intersections. */
public final class FacetIntersectionQuery {
    private FacetIntersectionQuery() {}

    public static final class Parsed {
        public final List<IndexCoreDatabase.FacetFilter> filters;
        public final String pageFrom;
        public final String pageTo;

        Parsed(List<IndexCoreDatabase.FacetFilter> filters, String pageFrom, String pageTo) {
            this.filters = Collections.unmodifiableList(new ArrayList<>(filters));
            this.pageFrom = pageFrom == null ? "" : pageFrom;
            this.pageTo = pageTo == null ? "" : pageTo;
        }
    }

    /**
     * Syntax examples:
     * DOMAIN=HISTORY + DOMAIN=RELIGION + PRIMARY=PERSON - RELATION=EFFECT
     * PAGE=120..190 + RELATION=CAUSE
     * Bare CATEGORY:value also works, e.g. DOMAIN:HISTORY.
     */
    public static Parsed parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return new Parsed(Collections.emptyList(), "", "");

        List<IndexCoreDatabase.FacetFilter> filters = new ArrayList<>();
        String pageFrom = "", pageTo = "";
        String normalized = value.replace('−', '-').replace('–', '-').replace('—', '-');
        String[] pieces = normalized.split("(?=\\s[+-]\\s)|\\s+\\+\\s+");

        // Fallback tokenization for compact expressions with explicit + separators.
        if (pieces.length == 1 && normalized.contains("+")) pieces = normalized.split("\\+");

        for (String piece : pieces) {
            String token = piece == null ? "" : piece.trim();
            if (token.isEmpty()) continue;
            boolean exclude = false;
            if (token.startsWith("-")) { exclude = true; token = token.substring(1).trim(); }
            if (token.startsWith("+")) token = token.substring(1).trim();
            int split = token.indexOf('=');
            if (split < 0) split = token.indexOf(':');
            if (split <= 0 || split >= token.length() - 1) continue;
            String dimension = token.substring(0, split).trim().toUpperCase(Locale.ROOT);
            String facetValue = token.substring(split + 1).trim().toUpperCase(Locale.ROOT);
            if (dimension.equals("PAGE") || dimension.equals("PAGINA") || dimension.equals("PAG")) {
                String[] range = facetValue.split("\\.\\.", 2);
                if (range.length == 2) { pageFrom = digits(range[0]); pageTo = digits(range[1]); }
                else { pageFrom = digits(facetValue); pageTo = pageFrom; }
                continue;
            }
            filters.add(new IndexCoreDatabase.FacetFilter(dimension, facetValue, exclude));
        }
        return new Parsed(filters, pageFrom, pageTo);
    }

    public static String describe(Parsed parsed) {
        if (parsed == null) return "fără filtre";
        StringBuilder out = new StringBuilder();
        for (IndexCoreDatabase.FacetFilter filter : parsed.filters) {
            if (out.length() > 0) out.append(" ∩ ");
            if (filter.exclude) out.append("NOT ");
            out.append(filter.dimension).append('=').append(filter.value);
        }
        if (!parsed.pageFrom.isEmpty()) {
            if (out.length() > 0) out.append(" ∩ ");
            out.append("PAGE=").append(parsed.pageFrom);
            if (!parsed.pageTo.equals(parsed.pageFrom)) out.append("..").append(parsed.pageTo);
        }
        return out.length() == 0 ? "fără filtre" : out.toString();
    }

    private static String digits(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.matches("\\d{1,7}") ? clean : "";
    }
}