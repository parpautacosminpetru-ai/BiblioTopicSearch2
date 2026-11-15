package ro.bibliotopicsearch.app;

import android.graphics.Color;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MatchHit {
    public RectF box;
    public final String originalText;
    public final String searchTerm;
    public final TopicNode node;
    public final long detectedAt;
    /** Other thematic indexes linked to precisely this OCR occurrence. */
    private final List<MatchHit> associations = new ArrayList<>();

    public MatchHit(RectF box, String originalText, String searchTerm, TopicNode node, long detectedAt) {
        this.box = new RectF(box);
        this.originalText = originalText;
        this.searchTerm = searchTerm;
        this.node = stylePunctuation(node, searchTerm);
        this.detectedAt = detectedAt;
        this.associations.add(this);
    }

    private static TopicNode stylePunctuation(TopicNode source, String mark) {
        if (!BuiltInMaps.isPunctuationNode(source) || mark == null || mark.isEmpty()) return source;

        TopicNode styled = new TopicNode(source.path, source.title, source.level);
        styled.enabled = source.enabled;
        styled.terms.addAll(source.terms);

        switch (mark) {
            case ".":
                styled.color = Color.rgb(220, 73, 79);       // finalizare
                styled.symbol = ".";
                break;
            case ",":
                styled.color = Color.rgb(46, 160, 197);      // separare locală
                styled.symbol = ",";
                break;
            case ";":
                styled.color = Color.rgb(230, 139, 55);      // separare puternică
                styled.symbol = ";";
                break;
            case ":":
                styled.color = Color.rgb(216, 175, 60);      // deschidere / explicație
                styled.symbol = ":";
                break;
            case "?":
                styled.color = Color.rgb(142, 99, 191);      // interogație
                styled.symbol = "?";
                break;
            case "!":
                styled.color = Color.rgb(213, 74, 124);      // exclamare
                styled.symbol = "!";
                break;
            case "…":
            case "...":
                styled.color = Color.rgb(119, 93, 169);      // suspendare
                styled.symbol = "…";
                break;
            case "—":
            case "–":
                styled.color = Color.rgb(63, 108, 180);      // pauză / inserție
                styled.symbol = "—";
                break;
            case "(":
            case ")":
                styled.color = Color.rgb(65, 157, 112);      // încadrare
                styled.symbol = "( )";
                break;
            case "[":
            case "]":
                styled.color = Color.rgb(54, 145, 145);      // încadrare secundară
                styled.symbol = "[ ]";
                break;
            case "„":
            case "”":
            case "«":
            case "»":
            case "\"":
                styled.color = Color.rgb(188, 115, 56);      // citare
                styled.symbol = "„ ”";
                break;
            case "-":
                styled.color = Color.rgb(92, 123, 168);      // cratimă / legare
                styled.symbol = "-";
                break;
            default:
                styled.color = source.color;
                styled.symbol = source.symbol;
                break;
        }

        return styled;
    }

    /**
     * Display ONE occurrence while retaining ALL its topic/term associations.
     * Merges only duplicate matches at the same OCR word/phrase position, not
     * repeated instances of the same word elsewhere on the page.
     */
    public void attach(MatchHit other) {
        if (other == null) return;
        Set<String> existing = new LinkedHashSet<>();
        for (MatchHit item : associations) {
            existing.add(item.node.path + "|" + item.searchTerm);
        }
        for (MatchHit item : other.associations) {
            String key = item.node.path + "|" + item.searchTerm;
            if (existing.add(key)) associations.add(item);
        }
    }

    public List<MatchHit> associations() {
        return Collections.unmodifiableList(associations);
    }

    public String identityKey() {
        return node.path + "|" + searchTerm;
    }
}
