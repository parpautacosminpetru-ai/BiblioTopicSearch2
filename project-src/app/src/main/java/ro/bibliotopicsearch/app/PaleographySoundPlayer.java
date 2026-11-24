package ro.bibliotopicsearch.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Main-thread playback of explicit rule sounds. At most one utterance/clip
 * is in flight. Every cue must still belong to a visible confirmed line. */
final class PaleographySoundPlayer implements AutoCloseable{
    interface Listener {
        void highlight(PaleographyLine line,LiteralTransliterator.RuleMatch cue);
        void unavailable(String language);
    }
    private static final long FORGET_MS=2600L;
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioManager.OnAudioFocusChangeListener focusListener=change->{
        if(change<=0)main.post(this::cancelLine);
    };
    private static final class Heard{
        PaleographyLine line;long lastSeen;
        final PaleographyAudioProgress progress=new PaleographyAudioProgress();
        Heard(PaleographyLine line,long now){this.line=line;lastSeen=now;}
    }
    private final List<Heard> heard=new ArrayList<>();
    private final Set<String> warned=new HashSet<>();
    private List<PaleographyLine> visible=Collections.emptyList();
    private Map<String,File> clips=Collections.emptyMap();
    private PaleographyLine playing;
    private Heard activeHeard;
    private List<LiteralTransliterator.RuleMatch> queue=Collections.emptyList();
    private LiteralTransliterator.RuleMatch cue;
    private int index,ttsState;
    private long token;
    private boolean enabled,closed,focusHeld,audible,waitingForTts;
    private TextToSpeech tts;
    private MediaPlayer media;
    private Runnable timeout;

    PaleographySoundPlayer(Context context,Listener listener){
        this.context=context.getApplicationContext();this.listener=listener;
        audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    }
    void setEnabled(boolean enabled){
        if(closed)return;
        if(this.enabled==enabled)return;
        this.enabled=enabled;
        if(!enabled)clear(true);
    }

    static boolean same(PaleographyLine a,PaleographyLine b){
        return a.key().equals(b.key())&&PageEvidence.overlap(a.rectangle(),b.rectangle())>=.55f;
    }
    private static PaleographyLine find(PaleographyLine old,List<PaleographyLine> candidates){
        for(PaleographyLine l:candidates)if(same(old,l))return l;
        return null;
    }
    void update(List<PaleographyLine> lines,Map<String,File> files){
        if(closed||!enabled)return;
        visible=lines;clips=files;
        long now=SystemClock.elapsedRealtime();
        Iterator<Heard> it=heard.iterator();
        while(it.hasNext()){
            Heard h=it.next();
            PaleographyLine current=find(h.line,visible);
            if(current!=null){h.line=current;h.lastSeen=now;}
            else if(now-h.lastSeen>FORGET_MS)it.remove();
        }
        if(playing!=null){
            PaleographyLine current=find(playing,visible);
            if(current==null)cancelLine();
            else{
                playing=current;
                if(audible&&cue!=null)listener.highlight(playing,cue);
                return;
            }
        }
        startNext();
    }
    /** Stop audio immediately when the current overlay is invalidated.
     * Retain recent occurrences through short OCR gaps to avoid repetition. */
    void clear(boolean forget){
        visible=Collections.emptyList();cancelLine();
        if(forget)heard.clear();
    }
    private boolean hasSound(LiteralTransliterator.RuleMatch match){
        return clips.containsKey(match.rule.source)||!match.rule.sound.isEmpty();
    }
    private void startNext(){
        if(!enabled||closed||playing!=null)return;
        for(PaleographyLine line:visible){
            Heard previous=null;
            for(Heard h:heard)if(same(h.line,line)){previous=h;break;}
            if(previous!=null&&previous.progress.done())continue;
            List<LiteralTransliterator.RuleMatch> cues=new ArrayList<>();
            for(LiteralTransliterator.RuleMatch m:line.matches)if(hasSound(m))cues.add(m);
            if(cues.isEmpty())continue;
            if(!requestFocus())return;
            if(previous==null){
                if(heard.size()>=720)heard.remove(0);
                previous=new Heard(line,SystemClock.elapsedRealtime());heard.add(previous);
            }
            activeHeard=previous;playing=line;queue=cues;
            index=previous.progress.start(cues.size());
            next();return;
        }
        abandonFocus();
    }
    @SuppressWarnings("deprecation")
    private boolean requestFocus(){
        if(focusHeld)return true;
        if(audio==null)return false;
        focusHeld=audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focusHeld;
    }
    @SuppressWarnings("deprecation")
    private void abandonFocus(){
        if(focusHeld&&audio!=null)audio.abandonAudioFocus(focusListener);
        focusHeld=false;
    }
    private void next(){
        if(!enabled||closed||playing==null){cancelLine();return;}
        if(index>=queue.size()){
            if(activeHeard!=null)activeHeard.progress.finish();
            finishCue();playing=null;activeHeard=null;queue=Collections.emptyList();index=0;
            startNext();return;
        }
        finishCue();
        cue=queue.get(index++);
        long id=++token;
        File file=clips.get(cue.rule.source);
        if(file!=null){
            playClip(file,id);return;
        }
        if(cue.rule.sound.isEmpty()){main.post(()->{if(id==token)next();});return;}
        speakOrWait(id);
    }
    private void playClip(File file,long id){
        try{
            MediaPlayer player=new MediaPlayer();media=player;
            player.setAudioAttributes(attributes);player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(p->{
                if(id!=token||media!=p||playing==null)return;
                try{p.start();mark(id);}catch(RuntimeException error){warn("înregistrare audio");complete(id);}
            });
            player.setOnCompletionListener(p->complete(id));
            player.setOnErrorListener((p,w,e)->{warn("înregistrare audio");complete(id);return true;});
            player.prepareAsync();
            arm(id,12000L);
        }catch(Exception error){releaseMedia();warn("înregistrare audio");main.post(()->complete(id));}
    }
    private void speakOrWait(long id){
        if(id!=token||cue==null)return;
        if(ttsState==0){
            ttsState=1;waitingForTts=true;
            try{
                tts=new TextToSpeech(context,status->main.post(()->{
                    if(closed)return;
                    ttsState=status==TextToSpeech.SUCCESS?2:3;
                    if(ttsState==2&&tts!=null){
                        tts.setAudioAttributes(attributes);tts.setSpeechRate(.80f);
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                            @Override public void onStart(String utterance){postToken(utterance,true);}
                            @Override public void onDone(String utterance){postToken(utterance,false);}
                            @Override public void onError(String utterance){postToken(utterance,false);}
                        });
                    }
                    if(waitingForTts&&cue!=null){
                        waitingForTts=false;speakOrWait(token);
                    }
                }));
            }catch(RuntimeException error){ttsState=3;waitingForTts=false;main.post(()->speakOrWait(id));}
            arm(id,10000L);
            return;
        }
        if(ttsState==1){waitingForTts=true;arm(id,10000L);return;}
        String language=cue.rule.language.isEmpty()?"ro-RO":cue.rule.language;
        if(ttsState!=2||tts==null||!selectVoice(language)){
            warn(language);main.post(()->complete(id));return;
        }
        waitingForTts=false;
        int result;
        try{result=tts.speak(cue.rule.sound,TextToSpeech.QUEUE_FLUSH,null,Long.toString(id));}
        catch(RuntimeException error){result=TextToSpeech.ERROR;}
        if(result==TextToSpeech.ERROR){warn(language);main.post(()->complete(id));}
        else arm(id,12000L);
    }
    private boolean selectVoice(String tag){
        Locale wanted=Locale.forLanguageTag(tag);
        try{
            Set<Voice> voices=tts.getVoices();if(voices==null)return false;
            Voice best=null;
            for(Voice voice:voices){
                if(voice.isNetworkConnectionRequired())continue;
                Locale locale=voice.getLocale();
                if(!wanted.getLanguage().equalsIgnoreCase(locale.getLanguage()))continue;
                if(!wanted.getCountry().isEmpty()&&!wanted.getCountry().equalsIgnoreCase(locale.getCountry()))continue;
                if(!wanted.getScript().isEmpty()&&!wanted.getScript().equalsIgnoreCase(locale.getScript()))continue;
                if(voice.getFeatures()!=null&&voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))continue;
                if(best==null||voice.getQuality()>best.getQuality())best=voice;
            }
            return best!=null&&tts.setVoice(best)==TextToSpeech.SUCCESS;
        }catch(RuntimeException unavailable){return false;}
    }
    private void warn(String language){if(warned.add(language))listener.unavailable(language);}
    private void postToken(String utterance,boolean start){
        try{
            final long id=Long.parseLong(utterance);
            main.post(()->{if(start)mark(id);else complete(id);});
        }catch(NumberFormatException ignored){}
    }
    private void mark(long id){
        if(id!=token||playing==null||cue==null)return;
        audible=true;listener.highlight(playing,cue);
    }
    private void complete(long id){
        if(id!=token||playing==null)return;
        if(activeHeard!=null)activeHeard.progress.completeThrough(index);
        next();
    }
    private void arm(long id,long ms){
        if(timeout!=null)main.removeCallbacks(timeout);
        timeout=()->{
            if(id!=token||playing==null)return;
            if(waitingForTts){waitingForTts=false;ttsState=3;}
            if(cue!=null)warn(clips.containsKey(cue.rule.source)?"înregistrare audio":(cue.rule.language.isEmpty()?"ro-RO":cue.rule.language));
            complete(id);
        };
        main.postDelayed(timeout,ms);
    }
    private void releaseMedia(){
        if(media!=null){try{media.release();}catch(RuntimeException ignored){}media=null;}
    }
    private void finishCue(){
        ++token;waitingForTts=false;
        if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}
        releaseMedia();
        if(tts!=null)try{tts.stop();}catch(RuntimeException ignored){}
        cue=null;audible=false;listener.highlight(null,null);
    }
    private void cancelLine(){
        finishCue();playing=null;activeHeard=null;queue=Collections.emptyList();index=0;abandonFocus();
    }
    @Override public void close(){
        closed=true;enabled=false;clear(true);main.removeCallbacksAndMessages(null);
        if(tts!=null){tts.shutdown();tts=null;}
    }
}
