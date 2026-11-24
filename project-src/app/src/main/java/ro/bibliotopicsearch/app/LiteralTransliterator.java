package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A literal Unicode transducer, not a dictionary or translation engine.
 * Scan source once, select the longest explicit match at each position,
 * and never feed generated output back into the matcher. */
final class LiteralTransliterator {
    static final int MAX_RULES=20000, MAX_INPUT=200000, MAX_OUTPUT=1000000;
    static final int MAX_SOURCE=128, MAX_TARGET=4096;

    static final class Rule {
        final String source,target,sound,language;
        Rule(String source,String target){this(source,target,"","");}
        Rule(String source,String target,String sound,String language){
            this.source=source;this.target=target;this.sound=sound==null?"":sound;
            this.language=language==null?"":language;
        }
        boolean sameOutput(Rule other){
            return target.equals(other.target)&&sound.equals(other.sound)&&language.equals(other.language);
        }
    }
    static final class RuleMatch{
        final Rule rule;
        final int sourceStart,sourceEnd,start,end;
        RuleMatch(Rule rule,int sourceStart,int sourceEnd,int start,int end){
            this.rule=rule;this.sourceStart=sourceStart;this.sourceEnd=sourceEnd;this.start=start;this.end=end;
        }
    }
    static final class Result {
        final String text;
        final int substitutions,unmappedCharacters,unmappedNonWhitespace;
        final Map<Integer,Integer> unmapped;
        final int otherUnmapped;
        final List<RuleMatch> matches;
        Result(String text,int substitutions,int unmappedCharacters,int unmappedNonWhitespace,
               Map<Integer,Integer> unmapped,int otherUnmapped,List<RuleMatch> matches){
            this.text=text;this.substitutions=substitutions;
            this.unmappedCharacters=unmappedCharacters;this.unmappedNonWhitespace=unmappedNonWhitespace;
            this.unmapped=Collections.unmodifiableMap(unmapped);this.otherUnmapped=otherUnmapped;
            this.matches=Collections.unmodifiableList(matches);
        }
    }
    private static final class Node {
        final Map<Integer,Node> children=new HashMap<>();
        Rule rule;
    }
    private final Node root=new Node();
    private final List<Rule> rules;

    private LiteralTransliterator(){rules=Collections.emptyList();}
    /** Explicit source-preserving mode, not a transliteration rule set. */
    static LiteralTransliterator original(){return new LiteralTransliterator();}

    LiteralTransliterator(List<Rule> input){
        if(input==null||input.isEmpty()||input.size()>MAX_RULES)
            throw new IllegalArgumentException("Sistemul trebuie să conțină între 1 și "+MAX_RULES+" reguli.");
        Map<String,Rule> unique=new LinkedHashMap<>();
        for(Rule rule:input){
            if(rule==null||rule.source==null||rule.target==null||rule.source.isEmpty())
                throw new IllegalArgumentException("Sursa unei reguli nu poate fi goală.");
            validateUnicode(rule.source);validateUnicode(rule.target);validateUnicode(rule.sound);
            if(rule.sound.length()>200||rule.language.length()>35||
                    (!rule.language.isEmpty()&&!rule.language.matches("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")))
                throw new IllegalArgumentException("Sunet prea lung sau cod de limbă BCP-47 invalid.");
            if(rule.source.codePointCount(0,rule.source.length())>MAX_SOURCE||rule.target.length()>MAX_TARGET)
                throw new IllegalArgumentException("Regulă prea lungă.");
            Rule old=unique.putIfAbsent(rule.source,rule);
            if(old!=null&&!old.sameOutput(rule))
                throw new IllegalArgumentException("Reguli contradictorii pentru: "+describe(rule.source.codePointAt(0))+" („"+rule.source+"”).");
        }
        List<Rule> collected=new ArrayList<>();
        for(Rule rule:unique.values()){
            collected.add(rule);
            Node n=root;
            for(int i=0;i<rule.source.length();){
                int cp=rule.source.codePointAt(i);
                n=n.children.computeIfAbsent(cp,ignored->new Node());
                i+=Character.charCount(cp);
            }
            n.rule=rule;
        }
        rules=Collections.unmodifiableList(collected);
    }
    List<Rule> rules(){return rules;}

    Result apply(String input){return apply(input,false);}
    Result positioned(String input){return apply(input,true);}

    private Result apply(String input,boolean positions){
        if(input==null)input="";
        if(input.length()>MAX_INPUT)throw new IllegalArgumentException("Text prea lung. Folosește fragmente de maximum "+MAX_INPUT+" unități UTF-16.");
        validateUnicode(input);
        StringBuilder out=new StringBuilder(Math.min(input.length()*2,MAX_OUTPUT));
        Map<Integer,Integer> missing=new LinkedHashMap<>();
        List<RuleMatch> matches=positions?new ArrayList<>():Collections.emptyList();
        int replacements=0,unmapped=0,nonWhitespace=0,other=0;
        for(int i=0;i<input.length();){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Operațiune întreruptă.");
            Node n=root;Rule best=null;int end=i;
            for(int j=i;j<input.length();){
                int cp=input.codePointAt(j);n=n.children.get(cp);
                if(n==null)break;
                j+=Character.charCount(cp);
                if(n.rule!=null){best=n.rule;end=j;}
            }
            if(best!=null){
                if(out.length()>MAX_OUTPUT-best.target.length())throw new IllegalArgumentException("Rezultatul este prea lung.");
                int start=out.length();out.append(best.target);
                if(positions)matches.add(new RuleMatch(best,i,end,start,out.length()));
                i=end;replacements++;
            }else{
                int cp=input.codePointAt(i),width=Character.charCount(cp);
                if(out.length()>MAX_OUTPUT-width)throw new IllegalArgumentException("Rezultatul este prea lung.");
                out.appendCodePoint(cp);i+=width;unmapped++;
                if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)){
                    nonWhitespace++;
                    Integer count=missing.get(cp);
                    if(count!=null)missing.put(cp,count+1);
                    else if(missing.size()<200)missing.put(cp,1);
                    else other++;
                }
            }
        }
        return new Result(out.toString(),replacements,unmapped,nonWhitespace,missing,other,matches);
    }

    static void validateUnicode(String s){
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(i+1>=s.length()||!Character.isLowSurrogate(s.charAt(i+1)))
                    throw new IllegalArgumentException("Text Unicode invalid.");
                i++;
            }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Text Unicode invalid.");
        }
    }

    static String describe(int cp){
        String code=String.format(java.util.Locale.ROOT,"U+%04X",cp);
        if(Character.isISOControl(cp)||Character.isWhitespace(cp)||Character.isSpaceChar(cp)||Character.getType(cp)==Character.FORMAT)
            return code;
        return new String(Character.toChars(cp))+" · "+code;
    }
}
