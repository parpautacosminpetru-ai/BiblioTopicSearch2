package ro.bibliotopicsearch.app;

/** 28 hues x 4 saturation levels x 4 value levels. Stable, opaque RGB;
 * all 448 imported categories have a different exact RGB value. */
final class HistoricalLexiconColors {
    private HistoricalLexiconColors(){}
    static int color(int index){
        if(index<0||index>=448)throw new IllegalArgumentException("Index categorie invalid.");
        double h=((index*11)%28)*6d/28d;
        int tier=index/28;
        double s=.52+.11*(tier%4),v=.60+.12*(tier/4);
        double c=v*s,x=c*(1-Math.abs(h%2-1)),m=v-c;
        double r=0,g=0,b=0;
        switch((int)h){
            case 0:r=c;g=x;break;
            case 1:r=x;g=c;break;
            case 2:g=c;b=x;break;
            case 3:g=x;b=c;break;
            case 4:r=x;b=c;break;
            default:r=c;b=x;break;
        }
        return 0xff000000|((int)Math.round((r+m)*255)<<16)|((int)Math.round((g+m)*255)<<8)|(int)Math.round((b+m)*255);
    }
}
