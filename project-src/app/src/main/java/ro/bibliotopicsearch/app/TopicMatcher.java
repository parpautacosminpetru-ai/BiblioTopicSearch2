package ro.bibliotopicsearch.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A single index for all maps. Every emitted hit is tied to a current
 * literal OCR span, or to an explicitly enabled, labelled reconstruction. */
public final class TopicMatcher {
    private TopicMatcher() {}

    static final class CompiledTerm {
        final String raw, normalized;
        final int tokenCount;
        final TopicNode node;
        final boolean signOnly;
        CompiledTerm(String raw, String normalized, int tokenCount, TopicNode node) {
            this.raw = raw; this.normalized = normalized; this.tokenCount = tokenCount; this.node = node;
            signOnly = PunctuationText.isPattern(raw);
        }
    }

    interface SignConsumer { void match(int start,int end,CompiledTerm term); }

    interface WindowConsumer { void match(int start, int end, List<CompiledTerm> terms, MatchHit.Kind kind); }

    public static final class SearchPlan {
        private final boolean stripDiacritics, repairEnabled, joinEnabled;
        private final AppPrefs.MatchMode mode;
        private final Map<Integer, TermIndex<CompiledTerm>> exact;
        private final TermIndex<CompiledTerm> approximate, signs;
        private final boolean hasSigns;
        private final int maxTokens, termCount;
        private final Map<String, Set<String>> allowed;

        private SearchPlan(boolean strip, AppPrefs.MatchMode mode, boolean repair, boolean join,
                           Map<Integer, TermIndex<CompiledTerm>> exact, TermIndex<CompiledTerm> approximate,
                           int maxTokens, int termCount, Map<String, Set<String>> allowed, TermIndex<CompiledTerm> signs, boolean hasSigns) {
            stripDiacritics = strip; this.mode = mode; repairEnabled = repair; joinEnabled = join;
            this.exact = exact; this.approximate = approximate;
            this.maxTokens = maxTokens; this.termCount = termCount; this.allowed = allowed;
            this.signs=signs;this.hasSigns=hasSigns;
        }
        public int termCount() { return termCount; }

        void scanSigns(String raw,SignConsumer consumer) {
            if(!hasSigns||raw==null)return;
            for(int i=0;i<raw.length();){
                if(Thread.currentThread().isInterrupted())return;
                int cp=raw.codePointAt(i);
                if(PunctuationText.isSign(cp)){
                    for(CompiledTerm term:signs.prefixAt(raw,i)){
                        int end=i+term.raw.length();
                        if(end<=raw.length())consumer.match(i,end,term);
                    }
                }
                i+=Character.charCount(cp);
            }
        }

        List<CompiledTerm> exactCandidates(int count, String value) {
            TermIndex<CompiledTerm> index = exact.get(count);
            return index == null ? Collections.emptyList() : index.exact(value);
        }

        List<CompiledTerm> candidates(int count, String value) {
            List<CompiledTerm> literal = exactCandidates(count, value);
            // Partial matching is opt-in, alphabetic, single-word only.
            if (mode == AppPrefs.MatchMode.EXACT || count != 1 || !LiteralText.isAlphabetic(value))
                return literal;
            List<CompiledTerm> other = mode == AppPrefs.MatchMode.PREFIX
                    ? approximate.prefix(value) : approximate.contains(value);
            if (other.isEmpty()) return literal;
            Set<CompiledTerm> both = new LinkedHashSet<>(literal);
            both.addAll(other);
            return new ArrayList<>(both);
        }

        List<CompiledTerm> repairedCandidates(String token) {
            if (!repairEnabled || !candidates(1, token).isEmpty()) return Collections.emptyList();
            List<CompiledTerm> found = Collections.emptyList();
            for (String variant : OcrRepair.digitVariants(token)) {
                List<CompiledTerm> candidates = exactCandidates(1, variant);
                if (candidates.isEmpty()) continue;
                if (!found.isEmpty()) return Collections.emptyList();
                found = candidates;
            }
            return found;
        }

        /** Verify actual source evidence again, independently of trie lookup. */
        boolean verifies(CompiledTerm term, String raw, MatchHit.Kind kind) {
            Set<String> keys = allowed.get(term.node.identityKey());
            if (keys == null || !keys.contains(term.signOnly ? "\u0001" + term.raw : term.normalized)) return false;
            if(term.signOnly)return kind==MatchHit.Kind.LITERAL&&term.raw.equals(raw);
            List<String> values = LiteralText.values(raw, stripDiacritics);
            String key = LiteralText.key(values, 0, values.size());
            if (kind == MatchHit.Kind.OCR_REPAIR) {
                if (!repairEnabled || values.size() != 1) return false;
                return repairedCandidates(key).contains(term);
            }
            if (kind == MatchHit.Kind.LINE_JOIN) {
                if (!joinEnabled) return false;
                // The line-break reconstruction has already been resolved against
                // both variants; verify the original, visibly hyphenated source.
                int split = raw.indexOf('\n');
                if (split <= 0 || split != raw.lastIndexOf('\n')) return false;
                List<LiteralText.Lexeme> left = LiteralText.lex(raw.substring(0,split),stripDiacritics);
                List<String> right = LiteralText.values(raw.substring(split+1),stripDiacritics);
                int at = LiteralText.terminalFragment(raw.substring(0,split),left);
                if (at < 0 || right.isEmpty()) return false;
                for (String joined : OcrRepair.joinedVariants(left.get(at).value+"-",right.get(0))) {
                    List<String> both = new ArrayList<>();
                    for(int i=0;i<at;i++) both.add(left.get(i).value);
                    both.add(joined); both.addAll(right.subList(1,right.size()));
                    if(term.normalized.equals(LiteralText.key(both,0,both.size()))) return true;
                }
                return false;
            }
            if (term.normalized.equals(key)) return PunctuationText.sameSignGroups(term.raw,raw);
            return mode != AppPrefs.MatchMode.EXACT && values.size()==1 && candidates(1,key).contains(term);
        }

        /** End is exclusive. Barriers exclude incomplete fragments and unsafe
         * gaps; only actual contiguous token windows reach the index. */
        void scan(List<String> values, boolean[] barriers, boolean[] breaks,
                  int boundary, int joined, String alternative, WindowConsumer consumer) {
            if (termCount == 0) return;
            int pivot = joined >= 0 ? joined : boundary;
            int first = pivot < 0 ? 0 : Math.max(0, pivot-maxTokens+1);
            int limit = joined >= 0 ? joined+1 : boundary >= 0 ? boundary : values.size();
            for (int start = first; start < limit; start++) {
                if (Thread.currentThread().isInterrupted()) return;
                StringBuilder phrase = new StringBuilder();
                for (int i = start; i < values.size() && i-start < maxTokens; i++) {
                    if (barriers[i] || (i > start && breaks[i])) break;
                    if (phrase.length() > 0) phrase.append(' ');
                    phrase.append(values.get(i));
                    if (pivot >= 0 && i < pivot) continue;
                    int count = i-start+1;
                    if (!exact.containsKey(count)) continue;
                    String value = phrase.toString();
                    List<CompiledTerm> found = joined >= 0 ? exactCandidates(count, value) : candidates(count, value);
                    MatchHit.Kind kind = joined >= 0 ? MatchHit.Kind.LINE_JOIN : MatchHit.Kind.LITERAL;
                    if (joined >= 0 && !found.isEmpty() && alternative != null) {
                        StringBuilder alt = new StringBuilder();
                        for (int j = start; j <= i; j++) {
                            if (alt.length() > 0) alt.append(' ');
                            alt.append(j == joined ? alternative : values.get(j));
                        }
                        if (!exactCandidates(count, alt.toString()).isEmpty()) continue; // ambiguous
                    }
                    if (found.isEmpty() && joined < 0 && boundary < 0 && count == 1) {
                        found = repairedCandidates(value);
                        if (!found.isEmpty()) kind = MatchHit.Kind.OCR_REPAIR;
                    }
                    if (!found.isEmpty()) consumer.match(start, i+1, found, kind);
                }
            }
        }
    }

    public static SearchPlan compile(Context context, TopicMap map) {
        return compile(map, AppPrefs.ignoreDiacritics(context), AppPrefs.compareChars(context),
                AppPrefs.getMatchMode(context), AppPrefs.ocrRepair(context),
                AppPrefs.joinHyphenation(context), AppPrefs.searchNodeTitles(context));
    }
    static SearchPlan compile(TopicMap map, boolean strip, int chars, AppPrefs.MatchMode mode) {
        return compile(map, strip, chars, mode, false, false, false);
    }
    static SearchPlan compile(TopicMap map, boolean strip, int chars, AppPrefs.MatchMode mode, boolean repair) {
        return compile(map, strip, chars, mode, repair, false, false);
    }
    static SearchPlan compile(TopicMap map, boolean strip, int chars, AppPrefs.MatchMode mode,
                              boolean repair, boolean join, boolean titles) {
        Map<Integer, TermIndex<CompiledTerm>> exact = new HashMap<>();
        TermIndex<CompiledTerm> approximate = new TermIndex<>(), signs = new TermIndex<>();
        boolean hasSigns=false;
        int max = 0, count = 0;
        Map<String,Set<String>> allowed = new HashMap<>();
        if (map != null) for (TopicNode node : map.nodes) {
            if (!node.enabled) continue;
            List<String> terms = node.terms.isEmpty() && titles ? Collections.singletonList(node.title) : node.terms;
            Set<String> seen = new HashSet<>();
            for (String raw : terms) {
                if(raw==null)continue;
                if(PunctuationText.isPattern(raw)){
                    String key="\u0001"+raw;
                    if(!seen.add(key))continue;
                    CompiledTerm term=new CompiledTerm(raw,raw,0,node);
                    allowed.computeIfAbsent(node.identityKey(),unused->new HashSet<>()).add(key);
                    signs.add(raw,term);hasSigns=true;count++;
                    continue;
                }
                List<String> tokens = LiteralText.values(raw, strip);
                if (tokens.isEmpty()) continue;
                String key = LiteralText.key(tokens, 0, tokens.size());
                if (!seen.add(key)) continue;
                CompiledTerm term = new CompiledTerm(raw, key, tokens.size(), node);
                allowed.computeIfAbsent(node.identityKey(), unused -> new HashSet<>()).add(key);
                exact.computeIfAbsent(tokens.size(), unused -> new TermIndex<>()).add(key, term);
                if (mode != AppPrefs.MatchMode.EXACT && tokens.size() == 1 &&
                        key.length() >= 3 && LiteralText.isAlphabetic(key)) {
                    String part = chars > 0 ? key.substring(0, Math.min(key.length(), Math.max(3, chars))) : key;
                    approximate.add(part, term);
                }
                max = Math.max(max, tokens.size());
                count++;
            }
        }
        return new SearchPlan(strip, mode, repair, join, exact, approximate, max, count, allowed, signs, hasSigns);
    }

    /** Text-only diagnostic uses the actual compiled index and independent
     * evidence check; it deliberately has no camera geometry or OCR scores. */
    public static final class TextProbeHit {
        public final int start,end;
        public final TopicNode node;
        public final String original,term;
        public final MatchHit.Kind kind;
        TextProbeHit(int start,int end,TopicNode node,String original,String term,MatchHit.Kind kind) {
            this.start=start;this.end=end;this.node=node;this.original=original;this.term=term;this.kind=kind;
        }
    }

    public static List<TextProbeHit> probeText(String text, SearchPlan plan) {
        List<TextProbeHit> hits=new ArrayList<>();
        if(text==null||plan==null||plan.termCount==0)return hits;
        // Signs are searched literally, even within dates or compound words.
        plan.scanSigns(text,(start,end,term)->{
            String original=text.substring(start,end);
            if(plan.verifies(term,original,MatchHit.Kind.LITERAL))
                hits.add(new TextProbeHit(start,end,term.node,original,term.raw,MatchHit.Kind.LITERAL));
        });
        // Empty lines are hard paragraph boundaries. Nonempty line breaks
        // can separate words, but this text-only mode cannot validate columns.
        java.util.regex.Matcher paragraph=java.util.regex.Pattern.compile("[^\\r\\n]+(?:\\r?\\n[^\\r\\n]+)*").matcher(text);
        while(paragraph.find()){
            if(Thread.currentThread().isInterrupted())return Collections.emptyList();
            final int offset=paragraph.start();
            String raw=paragraph.group();
            List<LiteralText.Lexeme> lexemes=LiteralText.lex(raw,plan.stripDiacritics);
            List<String> values=new ArrayList<>(lexemes.size());
            boolean[] barriers=new boolean[lexemes.size()],breaks=new boolean[lexemes.size()];
            for(int i=0;i<lexemes.size();i++){
                LiteralText.Lexeme l=lexemes.get(i);values.add(l.value);
                if(i>0&&!LiteralText.separated(raw,lexemes.get(i-1),l))breaks[i]=true;
                // Never turn a partial line-final word into a literal term.
                if(i>0&&"-".equals(l.value)&&lexemes.get(i-1).end==l.start&&
                        LiteralText.isAlphabetic(lexemes.get(i-1).value)){
                    int next=i+1<lexemes.size()?lexemes.get(i+1).start:raw.length();
                    String gap=raw.substring(l.end,next);
                    if(gap.indexOf('\n')>=0){
                        barriers[i-1]=barriers[i]=true;
                        if(i+1<lexemes.size())barriers[i+1]=true;
                    }
                }
            }
            plan.scan(values,barriers,breaks,-1,-1,null,(start,end,terms,kind)->{
                int from=lexemes.get(start).start,to=lexemes.get(end-1).end;
                String original=raw.substring(from,to);
                for(CompiledTerm term:terms)if(plan.verifies(term,original,kind))
                    hits.add(new TextProbeHit(offset+from,offset+to,term.node,original,term.raw,kind));
            });
        }
        java.util.Collections.sort(hits,java.util.Comparator.comparingInt((TextProbeHit hit)->hit.start).thenComparingInt(hit->hit.end));
        return hits;
    }

    public static List<MatchHit> find(Context context, Text text, TopicMap map) {
        return find(text, compile(context, map));
    }

    private static final class ElementSpan {
        final Text.Element element;
        final int start, end;
        ElementSpan(Text.Element element, int start) {
            this.element = element; this.start = start; end = start + element.getText().length();
        }

        RectF geometry(int from, int to) {
            Rect box = element.getBoundingBox();
            if (box == null || box.width() <= 0 || box.height() <= 0) return null;
            if (from == start && to == end) return new RectF(box);
            String raw = element.getText();
            int a = from - start, b = to - start;
            // Symbol coordinates are used only when their text can be aligned
            // exactly to the element. Never estimate a position by character count.
            List<Text.Symbol> symbols = element.getSymbols();
            if (symbols != null && !symbols.isEmpty()) {
                List<String> values = new ArrayList<>(symbols.size());
                for (Text.Symbol symbol : symbols) values.add(symbol.getText());
                int[] offsets = LiteralText.align(raw, values);
                if (offsets != null) {
                    RectF union = null;
                    boolean valid = true;
                    int coveredStart = Integer.MAX_VALUE, coveredEnd = -1;
                    for (int i = 0; i < symbols.size(); i++) {
                        int x = offsets[i], y = x + values.get(i).length();
                        if (y <= a || x >= b) continue;
                        Rect symbolBox = symbols.get(i).getBoundingBox();
                        if (x < a || y > b || symbolBox == null || symbolBox.width() <= 0 || symbolBox.height() <= 0) {
                            valid = false; break;
                        }
                        coveredStart = Math.min(coveredStart, x);
                        coveredEnd = Math.max(coveredEnd, y);
                        if (union == null) union = new RectF(symbolBox);
                        else union.union(new RectF(symbolBox));
                    }
                    if (valid && union != null && coveredStart == a && coveredEnd == b) {
                        // Reject symbol bounds in a different coordinate system.
                        RectF padded = new RectF(box);
                        padded.inset(-box.height()*0.25f, -box.height()*0.25f);
                        if (padded.contains(union)) return union;
                    }
                }
            }
            // A whole word with only surrounding punctuation may use its
            // element box. Never highlight a neighbouring word as a fallback.
            if (LiteralText.hasWord(raw.substring(a,b)) &&
                    !LiteralText.hasWord(raw.substring(0,a)) && !LiteralText.hasWord(raw.substring(b)))
                return new RectF(box);
            return null;
        }
    }

    private static final class LineData {
        final String raw;
        final Rect bounds;
        final float[] corners;
        final LineGeometry axis;
        final List<LiteralText.Lexeme> lexemes;
        final List<ElementSpan> elements = new ArrayList<>();
        final List<Token> tokens = new ArrayList<>();
        int fragment = -1;

        LineData(Text.Line line, boolean strip) {
            raw = line.getText();
            bounds = line.getBoundingBox();
            android.graphics.Point[] points=line.getCornerPoints();
            if(points!=null&&points.length==4){
                corners=new float[8];
                for(int i=0;i<4;i++){corners[i*2]=points[i].x;corners[i*2+1]=points[i].y;}
            }else corners=null;
            axis=LineGeometry.from(corners);
            lexemes = LiteralText.lex(raw, strip);
            List<Text.Element> source = line.getElements();
            List<String> values = new ArrayList<>(source.size());
            for (Text.Element e : source) values.add(e.getText());
            int[] offsets = LiteralText.align(raw, values);
            if (offsets == null) return;
            for (int i = 0; i < source.size(); i++) elements.add(new ElementSpan(source.get(i), offsets[i]));
            for (int i = 0; i < lexemes.size(); i++) {
                LiteralText.Lexeme l = lexemes.get(i);
                Token token = new Token(l.value, this, l.start, l.end);
                if (i > 0 && !LiteralText.separated(raw, lexemes.get(i-1), l)) token.breakBefore = true;
                tokens.add(token);
            }
            fragment = LiteralText.terminalFragment(raw, lexemes);
            if (fragment >= 0) {
                tokens.get(fragment).barrier = true;
                tokens.get(fragment+1).barrier = true;
            }
        }

        /** Each OCR element gets its own box so a phrase and a nested
         * single-word match can share exactly the correct coloured mark. */
        void confidence(int from, int to, OcrConfidence confidence) {
            for (ElementSpan e:elements) {
                if(e.end<=from)continue;
                if(e.start>=to)break;
                String part=linePart(e,from,to);
                if(PunctuationText.isPattern(part)){
                    // A containing word's confidence is not the confidence of
                    // its comma or apostrophe. Use aligned symbol evidence.
                    List<Text.Symbol> symbols=e.element.getSymbols();
                    List<String> values=new ArrayList<>();
                    if(symbols!=null)for(Text.Symbol symbol:symbols)values.add(symbol.getText());
                    int[] offsets=LiteralText.align(e.element.getText(),values);
                    boolean found=false;
                    if(offsets!=null)for(int i=0;i<offsets.length;i++){
                        int x=e.start+offsets[i],y=x+values.get(i).length();
                        if(y<=from||x>=to)continue;
                        if(x<from||y>to){confidence.add(0f);continue;}
                        confidence.add(symbols.get(i).getConfidence());found=true;
                    }
                    if(!found)confidence.add(PunctuationText.isPattern(e.element.getText())
                            ?e.element.getConfidence():0f);
                }else confidence.add(e.element.getConfidence());
            }
        }

        private String linePart(ElementSpan e,int from,int to){
            return raw.substring(Math.max(from,e.start),Math.min(to,e.end));
        }

        List<RectF> geometry(int from, int to) {
            List<RectF> out = new ArrayList<>();
            for (ElementSpan e : elements) {
                if (e.end <= from) continue;
                if (e.start >= to) break;
                RectF box = e.geometry(Math.max(from,e.start), Math.min(to,e.end));
                if (box == null) return null;
                if (!out.isEmpty()) {
                    RectF prev = out.get(out.size()-1);
                    if(prev.equals(box))continue;
                    if(axis!=null){
                        // Bounding rectangles are conservative; project their
                        // extents along the recognized baseline, not screen X.
                        float aw=Math.abs(axis.ux)*prev.width()+Math.abs(axis.uy)*prev.height();
                        float bw=Math.abs(axis.ux)*box.width()+Math.abs(axis.uy)*box.height();
                        if(!axis.consecutive(prev.centerX(),prev.centerY(),box.centerX(),box.centerY(),aw,bw))return null;
                    }else{
                        float height=Math.max(Math.max(prev.height(),box.height()),bounds==null?0:Math.min(bounds.height(),Math.max(prev.height(),box.height())*3f));
                        if(box.left-prev.right>height*2.5f||Math.abs(box.centerY()-prev.centerY())>height*1.25f)return null;
                    }
                }
                out.add(box);
            }
            return out.isEmpty() ? null : out;
        }
    }

    private static final class Token {
        final String value;
        final LineData line;
        final int start, end;
        boolean barrier, breakBefore;
        Token left, right;
        Token(String value, LineData line, int start, int end) {
            this.value = value; this.line = line; this.start = start; this.end = end;
        }
    }

    private static boolean adjacent(LineData a, LineData b) {
        if (a == null || b == null || a.tokens.isEmpty() || b.tokens.isEmpty() ||
                a.bounds == null || b.bounds == null) return false;
        if(a.axis!=null&&b.axis!=null)return a.axis.nextLine(b.axis,b.corners);
        Rect x = a.bounds, y = b.bounds;
        return LiteralText.sameColumn(x.left,x.top,x.right,x.bottom,y.left,y.top,y.right,y.bottom);
    }

    public static List<MatchHit> find(Text text, SearchPlan plan) {
        List<MatchHit> hits = new ArrayList<>();
        if (text == null || plan == null || plan.termCount <= 0) return hits;
        Set<String> dedupe = new HashSet<>();
        long now = android.os.SystemClock.elapsedRealtime();
        for (Text.TextBlock block : text.getTextBlocks()) {
            List<LineData> lines = new ArrayList<>();
            for (Text.Line line : block.getLines()) {
                if (Thread.currentThread().isInterrupted()) return Collections.emptyList();
                lines.add(new LineData(line, plan.stripDiacritics));
            }
            for (int i = 1; i < lines.size(); i++) {
                LineData a = lines.get(i-1), b = lines.get(i);
                if (adjacent(a,b) && a.fragment >= 0 && LiteralText.isAlphabetic(b.tokens.get(0).value))
                    b.tokens.get(0).barrier = true;
            }
            for (int i = 0; i < lines.size(); i++) {
                LineData current = lines.get(i);
                scanSigns(current,plan,hits,dedupe,now);
                scan(current.tokens, -1,-1,null,plan,hits,dedupe,now);
                if (i == 0) continue;
                LineData before = lines.get(i-1);
                if (!adjacent(before,current)) continue;
                if (before.fragment >= 0) {
                    if (plan.joinEnabled) scanJoined(before,current,plan,hits,dedupe,now);
                } else {
                    List<Token> both = new ArrayList<>(before.tokens);
                    both.addAll(current.tokens);
                    scan(both,before.tokens.size(),-1,null,plan,hits,dedupe,now);
                }
            }
        }
        return hits;
    }

    private static void scanSigns(LineData line,SearchPlan plan,List<MatchHit> hits,
                                  Set<String> dedupe,long now) {
        plan.scanSigns(line.raw,(start,end,term)->{
            String original=line.raw.substring(start,end);
            if(!plan.verifies(term,original,MatchHit.Kind.LITERAL))return;
            List<RectF> boxes=line.geometry(start,end);
            // If a sign is attached to a word and ML Kit supplied no usable
            // symbol rectangle, do not colour the containing word.
            if(boxes==null)return;
            OcrConfidence confidence=new OcrConfidence();
            line.confidence(start,end,confidence);
            float score=confidence.value();
            if(OcrConfidence.requiredFrames(score,false)==0)return;
            addUnique(hits,dedupe,boxes,original,term,now,MatchHit.Kind.LITERAL,score);
        });
    }

    private static void scanJoined(LineData a, LineData b, SearchPlan plan,
                                   List<MatchHit> hits, Set<String> dedupe, long now) {
        Token left = a.tokens.get(a.fragment), hyphen = a.tokens.get(a.fragment+1), right = b.tokens.get(0);
        List<String> variants = OcrRepair.joinedVariants(left.value+"-",right.value);
        if (variants.size() != 2) return;
        for (int i = 0; i < variants.size(); i++) {
            List<Token> both = new ArrayList<>(a.tokens.subList(0,a.fragment));
            Token merged = new Token(variants.get(i),a,left.start,hyphen.end);
            merged.left = new Token(left.value+"-",a,left.start,hyphen.end);
            merged.right = right;
            merged.breakBefore = left.breakBefore;
            int at = both.size();
            both.add(merged);
            both.addAll(b.tokens.subList(1,b.tokens.size()));
            scan(both,-1,at,variants.get(1-i),plan,hits,dedupe,now);
        }
    }

    private static void scan(List<Token> tokens, int boundary, int joined, String alternative,
                             SearchPlan plan, List<MatchHit> hits, Set<String> dedupe, long now) {
        List<String> values = new ArrayList<>(tokens.size());
        boolean[] barriers = new boolean[tokens.size()], breaks = new boolean[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            values.add(t.value);
            barriers[i] = t.barrier;
            breaks[i] = t.breakBefore;
        }
        plan.scan(values,barriers,breaks,boundary,joined,alternative,
                (start,end,terms,kind) -> emit(tokens,start,end,terms,kind,plan,hits,dedupe,now));
    }

    private static void emit(List<Token> tokens, int start, int end, List<CompiledTerm> terms, MatchHit.Kind kind,
                             SearchPlan plan, List<MatchHit> hits, Set<String> dedupe, long now) {
        List<Token> evidence = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Token t = tokens.get(i);
            if (t.left == null) evidence.add(t);
            else { evidence.add(t.left); evidence.add(t.right); }
        }
        List<RectF> geometry = new ArrayList<>();
        OcrConfidence confidence = new OcrConfidence();
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < evidence.size();) {
            Token first = evidence.get(i);
            int j = i+1;
            while (j < evidence.size() && evidence.get(j).line == first.line) j++;
            int to = evidence.get(j-1).end;
            List<RectF> boxes = first.line.geometry(first.start,to);
            if (boxes == null) return;
            geometry.addAll(boxes);
            first.line.confidence(first.start,to,confidence);
            if (raw.length() > 0) raw.append('\n');
            raw.append(first.line.raw,first.start,to);
            i = j;
        }
        String original = raw.toString();
        float score = confidence.value();
        if (OcrConfidence.requiredFrames(score, kind != MatchHit.Kind.LITERAL) == 0) return;
        for (CompiledTerm term : terms) {
            if (!plan.verifies(term, original, kind)) continue;
            addUnique(hits,dedupe,geometry,original,term,now,kind,score);
        }
    }

    private static void addUnique(List<MatchHit> hits,Set<String> dedupe,List<RectF> geometry,
                                  String original,CompiledTerm term,long now,MatchHit.Kind kind,float score){
        StringBuilder key=new StringBuilder(term.node.identityKey()).append('|').append(term.raw);
        for(RectF rect:geometry)key.append('|').append(rect.left).append(':').append(rect.top)
                .append(':').append(rect.right).append(':').append(rect.bottom);
        if(dedupe.add(key.toString()))hits.add(new MatchHit(geometry,original,term.raw,term.node,now,kind,false,score));
    }

    /** Compatibility helper used for page anchors and not for matching. */
    public static String normalize(String value, boolean strip) {
        StringBuilder result = new StringBuilder();
        for (LiteralText.Lexeme token : LiteralText.lex(value,strip)) if (token.word) {
            if (result.length() > 0) result.append(' ');
            result.append(token.value);
        }
        return result.toString();
    }
    static List<String> tokenize(String original, boolean strip) {
        List<String> out = new ArrayList<>();
        for (LiteralText.Lexeme t : LiteralText.lex(original,strip)) if (t.word) out.add(t.value);
        return out;
    }

    public static boolean matches(String actual, String term, AppPrefs.MatchMode mode, int compareChars) {
        if (actual == null || term == null) return false;
        if (actual.equals(term)) return true;
        if (mode == AppPrefs.MatchMode.EXACT || !LiteralText.isAlphabetic(actual) ||
                !LiteralText.isAlphabetic(term) || term.length() < 3) return false;
        String t = compareChars > 0 ? term.substring(0,Math.min(term.length(),Math.max(3,compareChars))) : term;
        return mode == AppPrefs.MatchMode.PREFIX ? actual.startsWith(t) : actual.contains(t);
    }
}
