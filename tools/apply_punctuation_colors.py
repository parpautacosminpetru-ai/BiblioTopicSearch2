from pathlib import Path

ROOT = Path("project-src")
TM = ROOT / "app/src/main/java/ro/bibliotopicsearch/app/TopicMatcher.java"
MAIN = ROOT / "app/src/main/java/ro/bibliotopicsearch/app/MainActivity.java"
GRADLE = ROOT / "app/build.gradle.kts"

# --- TopicMatcher: punctuation types get synthetic child nodes with distinct colors ---
text = TM.read_text(encoding="utf-8")

if "import android.graphics.Color;" not in text:
    text = text.replace(
        "import android.content.Context;\nimport android.graphics.Rect;",
        "import android.content.Context;\nimport android.graphics.Color;\nimport android.graphics.Rect;",
        1,
    )

old_const = '    private static final String PUNCTUATION = ".,;:?!…—–()[]„”«»\\\"";'
new_const = '    private static final String PUNCTUATION = ".,;:?!…—–-()[]„”«»\\\"";'
if old_const in text:
    text = text.replace(old_const, new_const, 1)
elif new_const not in text:
    raise SystemExit("PUNCTUATION constant not found")

start = text.find("    private static void addPunctuationHits(")
end = text.find("    private static int countTokens(", start)
if start < 0 or end < 0:
    raise SystemExit("Punctuation method block not found")

new_block = r'''    private static void addPunctuationHits(
            List<MatchHit> hits,
            Set<String> dedupe,
            RectF box,
            String rawToken,
            TopicNode parentNode,
            long timestamp
    ) {
        // Multi-character punctuation is consumed first so "..." does not also become three periods.
        String scan = rawToken;
        if (scan.contains("...")) {
            addPunctuationHit(hits, dedupe, box, "...", parentNode, timestamp);
            scan = scan.replace("...", "");
        }
        if (scan.indexOf('…') >= 0) {
            addPunctuationHit(hits, dedupe, box, "…", parentNode, timestamp);
            scan = scan.replace("…", "");
        }

        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < scan.length(); i++) {
            char c = scan.charAt(i);
            if (PUNCTUATION.indexOf(c) < 0 || !seen.add(c)) continue;
            String mark = String.valueOf(c);
            addPunctuationHit(hits, dedupe, box, mark, parentNode, timestamp);
        }
    }

    private static void addPunctuationHit(
            List<MatchHit> hits,
            Set<String> dedupe,
            RectF box,
            String mark,
            TopicNode parentNode,
            long timestamp
    ) {
        TopicNode typedNode = punctuationNode(parentNode, mark);
        addHit(hits, dedupe, new RectF(box), mark, mark, typedNode, timestamp);
    }

    private static TopicNode punctuationNode(TopicNode parent, String mark) {
        String title;
        int color;
        String symbol = mark;

        switch (mark) {
            case ".":
                title = "PUNCT • FINALIZARE";
                color = Color.rgb(220, 73, 79);
                break;
            case ",":
                title = "VIRGULĂ • SEPARARE LOCALĂ";
                color = Color.rgb(46, 160, 197);
                break;
            case ";":
                title = "PUNCT ȘI VIRGULĂ • SEPARARE PUTERNICĂ";
                color = Color.rgb(230, 139, 55);
                break;
            case ":":
                title = "DOUĂ PUNCTE • DESCHIDERE";
                color = Color.rgb(216, 175, 60);
                break;
            case "?":
                title = "ÎNTREBARE";
                color = Color.rgb(142, 99, 191);
                break;
            case "!":
                title = "EXCLAMARE";
                color = Color.rgb(213, 74, 124);
                break;
            case "…":
            case "...":
                title = "SUSPENSIE";
                color = Color.rgb(119, 93, 169);
                symbol = "…";
                break;
            case "—":
            case "–":
                title = "LINIE • PAUZĂ / INSERȚIE";
                color = Color.rgb(63, 108, 180);
                symbol = "—";
                break;
            case "-":
                title = "CRATIMĂ • LEGARE";
                color = Color.rgb(92, 123, 168);
                break;
            case "(":
            case ")":
                title = "PARANTEZE • ÎNCADRARE";
                color = Color.rgb(65, 157, 112);
                symbol = "( )";
                break;
            case "[":
            case "]":
                title = "PARANTEZE DREPTE • ÎNCADRARE";
                color = Color.rgb(54, 145, 145);
                symbol = "[ ]";
                break;
            case "„":
            case "”":
            case "«":
            case "»":
            case "\"":
                title = "GHILIMELE • CITARE";
                color = Color.rgb(188, 115, 56);
                symbol = "„ ”";
                break;
            default:
                title = "PUNCTUAȚIE";
                color = parent.color;
                break;
        }

        TopicNode node = new TopicNode(parent.path + " > " + title, title, parent.level + 1);
        node.color = color;
        node.symbol = symbol;
        node.enabled = true;
        node.terms.add(mark);
        return node;
    }

'''
text = text[:start] + new_block + text[end:]
TM.write_text(text, encoding="utf-8")

# --- MainActivity: if parent PUNCTUAȚIE is visible, its colored synthetic children are visible too ---
main = MAIN.read_text(encoding="utf-8")
start = main.find("    private List<MatchHit> filterVisibleHits(")
end = main.find("    private void redrawVisibleHits()", start)
if start < 0 or end < 0:
    raise SystemExit("filterVisibleHits block not found")

new_filter = r'''    private List<MatchHit> filterVisibleHits(List<MatchHit> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<MatchHit> out = new java.util.ArrayList<>();
        for (MatchHit hit : source) {
            if (hit != null && hit.node != null && isHitPathVisible(hit.node.path)) out.add(hit);
        }
        return out;
    }

    private boolean isHitPathVisible(String path) {
        if (path == null) return false;
        if (visibleNodePaths.contains(path)) return true;

        // Punctuation subtypes are generated dynamically from the raw OCR token.
        // One parent switch keeps the UI clean while every subtype keeps its own color/legend entry.
        String punctuationParent = BuiltInMaps.TEXTUAL_PREFIX + " > " + BuiltInMaps.PUNCTUATION_TITLE;
        return path.startsWith(punctuationParent + " > ") && visibleNodePaths.contains(punctuationParent);
    }

'''
main = main[:start] + new_filter + main[end:]
MAIN.write_text(main, encoding="utf-8")

# --- Version bump ---
gradle = GRADLE.read_text(encoding="utf-8")
gradle = gradle.replace("versionCode = 4", "versionCode = 5", 1)
gradle = gradle.replace('versionName = "1.2.0"', 'versionName = "1.2.1"', 1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied punctuation subtype colors and version 1.2.1")
