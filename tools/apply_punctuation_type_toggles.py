from pathlib import Path

ROOT = Path('project-src')
BUILT = ROOT / 'app/src/main/java/ro/bibliotopicsearch/app/BuiltInMaps.java'
MATCH = ROOT / 'app/src/main/java/ro/bibliotopicsearch/app/TopicMatcher.java'
MAIN = ROOT / 'app/src/main/java/ro/bibliotopicsearch/app/MainActivity.java'
GRADLE = ROOT / 'app/build.gradle.kts'

built = BUILT.read_text(encoding='utf-8')
old = '            "# TEXTUAL",\n            "## PUNCTUAȚIE",\n\n            "## TEMATIZARE / INTRODUCERE NOD",'
new = '''            "# TEXTUAL",\n            "## PUNCTUAȚIE",\n            "### PUNCT • FINALIZARE",\n            ".",\n            "### VIRGULĂ • SEPARARE LOCALĂ",\n            ",",\n            "### PUNCT ȘI VIRGULĂ • SEPARARE PUTERNICĂ",\n            ";",\n            "### DOUĂ PUNCTE • DESCHIDERE",\n            ":",\n            "### ÎNTREBARE",\n            "?",\n            "### EXCLAMARE",\n            "!",\n            "### SUSPENSIE",\n            "… | ...",\n            "### LINIE • PAUZĂ / INSERȚIE",\n            "— | –",\n            "### CRATIMĂ • LEGARE",\n            "-",\n            "### PARANTEZE • ÎNCADRARE",\n            "( | )",\n            "### PARANTEZE DREPTE • ÎNCADRARE",\n            "[ | ]",\n            "### GHILIMELE • CITARE",\n            "„ | ” | « | » | \\\"",\n\n            "## TEMATIZARE / INTRODUCERE NOD",'''
if old not in built:
    raise SystemExit('BuiltInMaps punctuation insertion point not found')
built = built.replace(old, new, 1)

old_method = '''    public static boolean isPunctuationNode(TopicNode node) {\n        return node != null\n                && PUNCTUATION_TITLE.equals(node.title)\n                && node.path != null\n                && node.path.startsWith(TEXTUAL_PREFIX + " > ");\n    }'''
new_method = '''    public static boolean isPunctuationNode(TopicNode node) {\n        return node != null\n                && PUNCTUATION_TITLE.equals(node.title)\n                && node.path != null\n                && node.path.startsWith(TEXTUAL_PREFIX + " > ");\n    }\n\n    public static boolean isPunctuationTypeNode(TopicNode node) {\n        return node != null\n                && node.level >= 3\n                && node.path != null\n                && node.path.startsWith(TEXTUAL_PREFIX + " > " + PUNCTUATION_TITLE + " > ");\n    }'''
if old_method not in built:
    raise SystemExit('BuiltInMaps punctuation method not found')
built = built.replace(old_method, new_method, 1)

needle = '''            if (node.level == 1) {\n                node.enabled = false;\n                continue;\n            }\n            node.enabled = true;\n            node.color = palette[index++ % palette.length];\n            node.symbol = textual ? "T" : "S";'''
replacement = '''            if (node.level == 1 || (textual && isPunctuationNode(node))) {\n                node.enabled = false;\n                continue;\n            }\n            node.enabled = true;\n            if (textual && isPunctuationTypeNode(node)) {\n                stylePunctuationType(node);\n            } else {\n                node.color = palette[index++ % palette.length];\n                node.symbol = textual ? "T" : "S";\n            }'''
if needle not in built:
    raise SystemExit('BuiltInMaps style block not found')
built = built.replace(needle, replacement, 1)

insert_point = '    private static final String TEXTUAL_RAW = String.join("\\n",'
helper = r'''    private static void stylePunctuationType(TopicNode node) {
        switch (node.title) {
            case "PUNCT • FINALIZARE": node.color = Color.rgb(220, 73, 79); node.symbol = "."; break;
            case "VIRGULĂ • SEPARARE LOCALĂ": node.color = Color.rgb(46, 160, 197); node.symbol = ","; break;
            case "PUNCT ȘI VIRGULĂ • SEPARARE PUTERNICĂ": node.color = Color.rgb(230, 139, 55); node.symbol = ";"; break;
            case "DOUĂ PUNCTE • DESCHIDERE": node.color = Color.rgb(216, 175, 60); node.symbol = ":"; break;
            case "ÎNTREBARE": node.color = Color.rgb(142, 99, 191); node.symbol = "?"; break;
            case "EXCLAMARE": node.color = Color.rgb(213, 74, 124); node.symbol = "!"; break;
            case "SUSPENSIE": node.color = Color.rgb(119, 93, 169); node.symbol = "…"; break;
            case "LINIE • PAUZĂ / INSERȚIE": node.color = Color.rgb(63, 108, 180); node.symbol = "—"; break;
            case "CRATIMĂ • LEGARE": node.color = Color.rgb(92, 123, 168); node.symbol = "-"; break;
            case "PARANTEZE • ÎNCADRARE": node.color = Color.rgb(65, 157, 112); node.symbol = "( )"; break;
            case "PARANTEZE DREPTE • ÎNCADRARE": node.color = Color.rgb(54, 145, 145); node.symbol = "[ ]"; break;
            case "GHILIMELE • CITARE": node.color = Color.rgb(188, 115, 56); node.symbol = "„ ”"; break;
            default: node.color = Color.rgb(80, 130, 160); node.symbol = "P"; break;
        }
    }

'''
if insert_point not in built:
    raise SystemExit('BuiltInMaps helper insertion point not found')
built = built.replace(insert_point, helper + insert_point, 1)
BUILT.write_text(built, encoding='utf-8')

match = MATCH.read_text(encoding='utf-8')
match = match.replace('private static final String PUNCTUATION = ".,;:?!…—–()[]„”«»\\\"";', 'private static final String PUNCTUATION = ".,;:?!…—–-()[]„”«»\\\"";', 1)
match = match.replace('private final TopicNode punctuationNode;', 'private final Map<String, TopicNode> punctuationNodes;', 1)
match = match.replace('TopicNode punctuationNode\n        ) {', 'Map<String, TopicNode> punctuationNodes\n        ) {', 1)
match = match.replace('this.punctuationNode = punctuationNode;', 'this.punctuationNodes = punctuationNodes;', 1)
match = match.replace('TopicNode punctuationNode = null;', 'Map<String, TopicNode> punctuationNodes = new HashMap<>();', 1)

old_compile = '''                // Special built-in structural detector. It must never pass through normalize(),\n                // because normalize intentionally removes punctuation for lexical search.\n                if (BuiltInMaps.isPunctuationNode(node)) {\n                    punctuationNode = node;\n                    termCount++;\n                    continue;\n                }'''
new_compile = '''                // Punctuation subtype nodes are structural detectors. Their raw characters\n                // never pass through normalize(), because normalize removes punctuation.\n                if (BuiltInMaps.isPunctuationTypeNode(node)) {\n                    List<String> marks = node.terms.isEmpty()\n                            ? Collections.singletonList(node.title)\n                            : node.terms;\n                    for (String mark : marks) {\n                        String raw = mark == null ? "" : mark.trim();\n                        if (raw.isEmpty()) continue;\n                        punctuationNodes.put(raw, node);\n                        termCount++;\n                    }\n                    continue;\n                }'''
if old_compile not in match:
    raise SystemExit('TopicMatcher compile punctuation block not found')
match = match.replace(old_compile, new_compile, 1)
match = match.replace('                punctuationNode\n        );', '                punctuationNodes\n        );', 1)

old_scan = '''                // Structural punctuation scan on the raw OCR token, before punctuation is stripped.\n                if (plan.punctuationNode != null) {\n                    for (int i = 0; i < size; i++) {\n                        if (boxes[i] == null || originalElements[i] == null) continue;\n                        addPunctuationHits(\n                                hits,\n                                dedupe,\n                                boxes[i],\n                                originalElements[i],\n                                plan.punctuationNode,\n                                now\n                        );\n                    }\n                }'''
new_scan = '''                // Structural punctuation scan on raw OCR tokens, with independently enabled types.\n                if (!plan.punctuationNodes.isEmpty()) {\n                    for (int i = 0; i < size; i++) {\n                        if (boxes[i] == null || originalElements[i] == null) continue;\n                        addPunctuationHits(\n                                hits,\n                                dedupe,\n                                boxes[i],\n                                originalElements[i],\n                                plan.punctuationNodes,\n                                now\n                        );\n                    }\n                }'''
if old_scan not in match:
    raise SystemExit('TopicMatcher scan block not found')
match = match.replace(old_scan, new_scan, 1)

start = match.find('    private static void addPunctuationHits(')
end = match.find('    private static int countTokens(', start)
if start < 0 or end < 0:
    raise SystemExit('TopicMatcher punctuation method block not found')
new_punct = r'''    private static void addPunctuationHits(
            List<MatchHit> hits,
            Set<String> dedupe,
            RectF box,
            String rawToken,
            Map<String, TopicNode> punctuationNodes,
            long timestamp
    ) {
        String scan = rawToken;
        if (scan.contains("...") && punctuationNodes.containsKey("...")) {
            addHit(hits, dedupe, new RectF(box), "...", "...", punctuationNodes.get("..."), timestamp);
            scan = scan.replace("...", "");
        }
        if (scan.indexOf('…') >= 0 && punctuationNodes.containsKey("…")) {
            addHit(hits, dedupe, new RectF(box), "…", "…", punctuationNodes.get("…"), timestamp);
            scan = scan.replace("…", "");
        }
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < scan.length(); i++) {
            char c = scan.charAt(i);
            if (PUNCTUATION.indexOf(c) < 0 || !seen.add(c)) continue;
            String mark = String.valueOf(c);
            TopicNode node = punctuationNodes.get(mark);
            if (node != null) addHit(hits, dedupe, new RectF(box), mark, mark, node, timestamp);
        }
    }

'''
match = match[:start] + new_punct + match[end:]
MATCH.write_text(match, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
gradle = gradle.replace('versionCode = 5', 'versionCode = 6', 1)
gradle = gradle.replace('versionName = "1.2.1"', 'versionName = "1.2.2"', 1)
GRADLE.write_text(gradle, encoding='utf-8')

print('Punctuation type toggles applied; version 1.2.2')
