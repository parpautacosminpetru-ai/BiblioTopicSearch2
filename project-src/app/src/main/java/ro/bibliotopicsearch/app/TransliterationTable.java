package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Two-column UTF-8 TSV and CSV. TSV uses explicit backslash escapes;
 * CSV uses normal RFC-style quoted cells. No case folding or trim on rules. */
final class TransliterationTable {
    static final int MAX_FILE_BYTES=1048576;
    private TransliterationTable(){}

    static List<LiteralTransliterator.Rule> parse(String text){
        if(text==null||text.isEmpty())throw new IllegalArgumentException("Fișier de reguli gol.");
        if(text.charAt(0)=='\uFEFF')text=text.substring(1);
        int firstStart=0;
        while(firstStart<text.length()&&(text.charAt(firstStart)=='\r'||text.charAt(firstStart)=='\n'))firstStart++;
        text=text.substring(firstStart);
        String first=text.substring(0,firstLineEnd(text));
        boolean tsv=first.indexOf('\t')>=0&&!first.startsWith("\"");
        return tsv?parseTsv(text):parseCsv(text);
    }
    private static int firstLineEnd(String text){
        int i=0;while(i<text.length()&&text.charAt(i)!='\r'&&text.charAt(i)!='\n')i++;
        return i;
    }
    static List<LiteralTransliterator.Rule> parseTsv(String text){
        List<LiteralTransliterator.Rule> out=new ArrayList<>();
        String[] lines=text.split("\\r\\n|\\n|\\r",-1);boolean first=true;
        for(int i=0;i<lines.length;i++){
            String line=lines[i];if(line.isEmpty())continue;
            String[] fields=line.split("\\t",-1);
            if(fields.length!=2)throw new IllegalArgumentException("Rândul "+(i+1)+": sunt necesare două coloane separate prin TAB.");
            if(first&&header(fields[0],fields[1])){first=false;continue;}
            first=false;
            add(out,unescape(fields[0]),unescape(fields[1]),i+1);
        }
        return new LiteralTransliterator(out).rules();
    }

    static List<LiteralTransliterator.Rule> parseCsv(String text){
        List<LiteralTransliterator.Rule> out=new ArrayList<>();
        List<String> row=new ArrayList<>(2);StringBuilder field=new StringBuilder();
        boolean quoted=false,afterQuote=false,first=true,recordStarted=false;
        int rowNumber=1;
        // Comma is the default; semicolon CSV is supported with an explicit header.
        String firstLine=text.substring(0,firstLineEnd(text)).toLowerCase(Locale.ROOT).replace("\"","");
        char delimiter=(firstLine.equals("source;target")||firstLine.equals("sursa;destinatie")
                ||firstLine.equals("sursă;destinație"))?';':',';
        for(int i=0;i<=text.length();i++){
            boolean eof=i==text.length();char c=eof?'\n':text.charAt(i);
            if(quoted){
                if(eof)throw new IllegalArgumentException("Ghilimele CSV neînchise la rândul "+rowNumber+".");
                if(c=='"'){
                    if(i+1<text.length()&&text.charAt(i+1)=='"'){field.append('"');i++;}
                    else{quoted=false;afterQuote=true;}
                }else field.append(c);
                continue;
            }
            if(c==delimiter){
                row.add(field.toString());field.setLength(0);afterQuote=false;recordStarted=true;
                if(row.size()>1)throw new IllegalArgumentException("Prea multe coloane la rândul "+rowNumber+".");
            }else if(c=='\r'||c=='\n'){
                if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;
                if(recordStarted||field.length()>0||afterQuote||!row.isEmpty()){
                    row.add(field.toString());
                    if(row.size()!=2)throw new IllegalArgumentException("Rândul "+rowNumber+": sunt necesare două coloane CSV.");
                    if(!(first&&header(row.get(0),row.get(1))))add(out,row.get(0),row.get(1),rowNumber);
                    first=false;
                }
                row.clear();field.setLength(0);afterQuote=false;recordStarted=false;rowNumber++;
            }else if(c=='"'&&field.length()==0&&!afterQuote){
                quoted=true;recordStarted=true;
            }else{
                if(afterQuote||c=='"')throw new IllegalArgumentException("Ghilimele CSV invalide la rândul "+rowNumber+".");
                field.append(c);recordStarted=true;
            }
        }
        return new LiteralTransliterator(out).rules();
    }
    private static boolean header(String a,String b){
        a=a.toLowerCase(Locale.ROOT);b=b.toLowerCase(Locale.ROOT);
        return (a.equals("source")&&b.equals("target"))||
                ((a.equals("sursa")||a.equals("sursă"))&&(b.equals("destinatie")||b.equals("destinație")));
    }
    private static void add(List<LiteralTransliterator.Rule> out,String a,String b,int line){
        if(a.isEmpty())throw new IllegalArgumentException("Sursă goală la regula "+line+".");
        if(out.size()>=LiteralTransliterator.MAX_RULES)throw new IllegalArgumentException("Prea multe reguli.");
        out.add(new LiteralTransliterator.Rule(a,b));
    }
    static String format(List<LiteralTransliterator.Rule> rules){
        StringBuilder out=new StringBuilder("source\ttarget\n");
        for(LiteralTransliterator.Rule rule:rules)out.append(escape(rule.source)).append('\t').append(escape(rule.target)).append('\n');
        return out.toString();
    }
    static String escape(String s){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            switch(c){
                case '\\':b.append("\\\\");break;
                case '\t':b.append("\\t");break;
                case '\n':b.append("\\n");break;
                case '\r':b.append("\\r");break;
                default:if(Character.isISOControl(c))b.append(String.format(Locale.ROOT,"\\u%04X",(int)c));else b.append(c);
            }
        }
        return b.toString();
    }
    static String unescape(String s){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c!='\\'){b.append(c);continue;}
            if(++i>=s.length())throw new IllegalArgumentException("Secvență de escapare incompletă.");
            char e=s.charAt(i);
            if(e=='\\')b.append('\\');
            else if(e=='t')b.append('\t');
            else if(e=='n')b.append('\n');
            else if(e=='r')b.append('\r');
            else if(e=='u'||e=='U'){
                int count=e=='u'?4:8;
                if(i+count>=s.length())throw new IllegalArgumentException("Cod Unicode incomplet.");
                long cp=0;
                for(int j=0;j<count;j++){
                    int d=Character.digit(s.charAt(++i),16);
                    if(d<0)throw new IllegalArgumentException("Cod Unicode invalid.");
                    cp=(cp<<4)|d;
                }
                if(e=='u')b.append((char)cp);
                else{
                    if(cp>0x10FFFF||(cp>=0xD800&&cp<=0xDFFF))throw new IllegalArgumentException("Cod Unicode invalid.");
                    b.appendCodePoint((int)cp);
                }
            }else throw new IllegalArgumentException("Escapare necunoscută: \\"+e);
        }
        String result=b.toString();LiteralTransliterator.validateUnicode(result);return result;
    }
}
