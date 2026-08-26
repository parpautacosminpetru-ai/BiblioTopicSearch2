package ro.bibliotopicsearch.app;

import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maps automatic paragraph detections back to concrete OCR word boxes. */
public final class SemanticTextMarker {
    private SemanticTextMarker() {}

    private static final class Token {
        final String raw;
        final String normalized;
        final RectF box;

        Token(String raw, RectF box) {
            this.raw = raw == null ? "" : raw;
            this.normalized = TopicMatcher.normalize(this.raw, true);
            this.box = box;
        }
    }

    public static List<SemanticTextMark> build(
            Text text,
            List<UniversalParagraphDetector.Detection> detections
    ) {
        if (text == null || detections == null || detections.isEmpty()) {
            return Collections.emptyList();
        }

        List<SemanticTextMark> out = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        List<Text.TextBlock> blocks = text.getTextBlocks();

        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            UniversalParagraphDetector.Detection detection = detectionFor(detections, blockIndex);
            if (detection == null) continue;

            List<Token> tokens = tokensFor(blocks.get(blockIndex));
            if (tokens.isEmpty()) continue;

            addMatches(
                    out,
                    dedupe,
                    tokens,
                    detection.subject(),
                    SemanticTextMark.Kind.SUBJECT,
                    "SUBIECT",
                    detection.subjectConfidence(),
                    blockIndex,
                    4
            );

            LinkedHashSet<String> evidence = functionEvidence(detection);
            int before = out.size();
            for (String phrase : evidence) {
                addMatches(
                        out,
                        dedupe,
                        tokens,
                        phrase,
                        SemanticTextMark.Kind.FUNCTION,
                        "FUNCȚIE · " + functionLabel(detection.function()),
                        detection.functionConfidence(),
                        blockIndex,
                        8
                );
            }

            // Structural fallback only when the detector has a classified function but
            // no explicit lexical marker was mapped back to OCR text.
            if (out.size() == before) {
                for (String cue : fallbackCues(detection.function())) {
                    addMatches(
                            out,
                            dedupe,
                            tokens,
                            cue,
                            SemanticTextMark.Kind.FUNCTION,
                            "FUNCȚIE · " + functionLabel(detection.function()),
                            detection.functionConfidence(),
                            blockIndex,
                            4
                    );
                }
            }
        }
        return out;
    }

    private static UniversalParagraphDetector.Detection detectionFor(
            List<UniversalParagraphDetector.Detection> detections,
            int paragraphIndex
    ) {
        for (UniversalParagraphDetector.Detection detection : detections) {
            if (detection != null && detection.paragraphIndex() == paragraphIndex) return detection;
        }
        return null;
    }

    private static List<Token> tokensFor(Text.TextBlock block) {
        List<Token> out = new ArrayList<>();
        if (block == null) return out;
        for (Text.Line line : block.getLines()) {
            for (Text.Element element : line.getElements()) {
                Rect rect = element.getBoundingBox();
                if (rect == null) continue;
                Token token = new Token(element.getText(), new RectF(rect));
                if (!token.normalized.isEmpty()) out.add(token);
            }
        }
        return out;
    }

    private static void addMatches(
            List<SemanticTextMark> out,
            Set<String> dedupe,
            List<Token> tokens,
            String phrase,
            SemanticTextMark.Kind kind,
            String label,
            double confidence,
            int paragraphIndex,
            int maxMatches
    ) {
        String normalized = TopicMatcher.normalize(phrase, true);
        if (normalized.isEmpty()) return;
        String[] wanted = normalized.split("\\s+");
        if (wanted.length == 0 || wanted.length > tokens.size()) return;

        int added = 0;
        for (int start = 0; start + wanted.length <= tokens.size() && added < maxMatches; start++) {
            boolean match = true;
            for (int offset = 0; offset < wanted.length; offset++) {
                if (!wanted[offset].equals(tokens.get(start + offset).normalized)) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;

            RectF union = null;
            StringBuilder raw = new StringBuilder();
            for (int offset = 0; offset < wanted.length; offset++) {
                Token token = tokens.get(start + offset);
                if (union == null) union = new RectF(token.box);
                else union.union(token.box);
                if (raw.length() > 0) raw.append(' ');
                raw.append(token.raw);
            }
            if (union == null) continue;

            String key = kind.name() + '|' + paragraphIndex + '|'
                    + Math.round(union.left / 3f) + '|' + Math.round(union.top / 3f) + '|'
                    + Math.round(union.right / 3f) + '|' + Math.round(union.bottom / 3f);
            if (dedupe.add(key)) {
                out.add(new SemanticTextMark(
                        kind,
                        union,
                        raw.toString(),
                        label,
                        confidence,
                        paragraphIndex
                ));
                added++;
            }
        }
    }

    private static LinkedHashSet<String> functionEvidence(
            UniversalParagraphDetector.Detection detection
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String primaryPrefix = detection.function().name() + ":";
        String secondaryPrefix = detection.secondaryFunction() == UniversalDetectionLexicon.Function.UNKNOWN
                ? ""
                : detection.secondaryFunction().name() + ":";

        for (String marker : detection.matchedMarkers()) {
            if (marker == null) continue;
            if (marker.startsWith(primaryPrefix)) {
                out.add(marker.substring(primaryPrefix.length()).trim());
            } else if (!secondaryPrefix.isEmpty() && marker.startsWith(secondaryPrefix)) {
                out.add(marker.substring(secondaryPrefix.length()).trim());
            }
        }
        return out;
    }

    private static List<String> fallbackCues(UniversalDetectionLexicon.Function function) {
        if (function == null) return Collections.emptyList();
        switch (function) {
            case DEFINITION:
                return list("este", "sunt", "reprezintă", "înseamnă", "constituie");
            case DESCRIPTION:
                return list("are", "au", "prezintă", "include", "cuprinde");
            case EXPLANATION:
                return list("se explică", "mecanismul", "prin");
            case CAUSE_EFFECT:
                return list("deoarece", "fiindcă", "din cauza", "duce la", "conduce la", "determină");
            case PURPOSE:
                return list("pentru a", "în vederea", "cu scopul");
            case CONDITION:
                return list("dacă", "cu condiția", "în cazul în care");
            case EXAMPLE:
                return list("de exemplu", "spre exemplu", "cum ar fi");
            case ENUMERATION:
                return list("în primul rând", "apoi", "următoarele");
            case CLASSIFICATION:
                return list("se clasifică", "se împart", "tipuri", "categorii");
            case COMPARISON:
                return list("în comparație cu", "similar", "asemănător");
            case CONTRAST:
                return list("dar", "însă", "totuși", "în schimb", "spre deosebire de");
            case ARGUMENTATION:
                return list("argument", "arată că", "demonstrează că", "teza");
            case EVIDENCE:
                return list("datele arată", "studiul arată", "rezultatele indică", "dovadă");
            case PROBLEM:
                return list("problema", "dificultatea", "limitare", "risc");
            case SOLUTION:
                return list("soluția", "se poate rezolva", "este necesar", "măsuri");
            case SEQUENCE:
                return list("mai întâi", "apoi", "ulterior", "în cele din urmă");
            case TRANSITION:
                return list("în ceea ce privește", "cât despre", "referitor la", "un alt aspect");
            case SUMMARY:
                return list("pe scurt", "în rezumat", "în sinteză", "în ansamblu");
            case CONCLUSION:
                return list("în concluzie", "așadar", "în final", "se poate concluziona");
            case INTRODUCTION:
                return list("vom analiza", "vom examina", "tema este", "subiectul este");
            case DEVELOPMENT:
                return list("de asemenea", "în plus", "totodată");
            case UNKNOWN:
            default:
                return Collections.emptyList();
        }
    }

    private static List<String> list(String... values) {
        List<String> out = new ArrayList<>();
        Collections.addAll(out, values);
        return out;
    }

    private static String functionLabel(UniversalDetectionLexicon.Function function) {
        if (function == null) return "NEDETERMINATĂ";
        switch (function) {
            case INTRODUCTION: return "INTRODUCERE";
            case DEFINITION: return "DEFINIRE";
            case DESCRIPTION: return "DESCRIERE";
            case EXPLANATION: return "EXPLICARE";
            case CAUSE_EFFECT: return "CAUZĂ–EFECT";
            case PURPOSE: return "SCOP";
            case CONDITION: return "CONDIȚIE";
            case EXAMPLE: return "EXEMPLIFICARE";
            case ENUMERATION: return "ENUMERARE";
            case CLASSIFICATION: return "CLASIFICARE";
            case COMPARISON: return "COMPARARE";
            case CONTRAST: return "CONTRASTARE";
            case ARGUMENTATION: return "ARGUMENTARE";
            case EVIDENCE: return "DOVADĂ";
            case PROBLEM: return "PROBLEMĂ";
            case SOLUTION: return "SOLUȚIE";
            case SEQUENCE: return "SECVENȚĂ";
            case TRANSITION: return "TRANZIȚIE";
            case SUMMARY: return "SINTETIZARE";
            case CONCLUSION: return "CONCLUZIE";
            case DEVELOPMENT: return "DEZVOLTARE";
            case UNKNOWN:
            default: return "NEDETERMINATĂ";
        }
    }
}
