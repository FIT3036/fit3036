package com.craig.mapapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.github.oxo42.stateless4j.StateMachine;
import com.github.oxo42.stateless4j.StateMachineConfig;
import com.github.oxo42.stateless4j.delegates.Action;
import com.github.oxo42.stateless4j.delegates.Action1;
import com.github.oxo42.stateless4j.delegates.Action2;
import com.github.oxo42.stateless4j.triggers.TriggerWithParameters;
import com.github.oxo42.stateless4j.triggers.TriggerWithParameters1;
import com.google.android.gms.location.LocationListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by jarrad on 14/10/15.
 */
abstract public class AbstractVoiceSM {
    protected StateMachine<States, Triggers> sm;
    private StateMachineConfig<States, Triggers> smc;
    TextToSpeech tts;
    Map<String, AbstractSpeakThenAction> waitingPrompts;
    AbstractAskForResponse waitingForSpeech;
    AbstractAskForYesNo waitingForYesNo;
    final Activity context;
    final static String TAG = "AbstractVoiceSM";
    final static int REQ_CODE_SPEECH_INPUT = 100;
    protected Location lastKnownLocation;
    protected LocationListener locationListener;
    protected RotationListener rotationListener;
    protected float lastKnownRotation;

    public AbstractVoiceSM(Activity context) {

        this.context = context;

        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                sm.fire(BaseTriggers.TTSReady);
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
                Log.d(TAG, "started speaking");
            }

            @Override
            public void onDone(String s) {
                AbstractVoiceSM.this.waitingPrompts.get(s).finishedSpeech();

            }

            @Override
            public void onError(String s) {

            }
        });

        waitingPrompts = new HashMap<String, AbstractSpeakThenAction>();
        waitingForSpeech = null;

        smc = new StateMachineConfig<States, Triggers>();

        smc.configure(BaseStates.Preinit)
                .permit(BaseTriggers.TTSReady, BaseStates.Standby);


        GotSpeech = smc.setTriggerParameters(AbstractTriggers.GotSpeechAbstract, ArrayList.class);
        GotYesNo = smc.setTriggerParameters(AbstractTriggers.GotYesNoAbstract, Boolean.class);
        GotLocation = smc.setTriggerParameters(AbstractTriggers.GotLocationAbstract, Location.class);

        configureStateMachine(smc);

        sm = new StateMachine<States, Triggers>(BaseStates.Preinit, smc);
        /* // silence exceptions on unhandled states
        sm.onUnhandledTrigger(new Action2<States, Triggers>() {
            @Override
            public void doIt(States s, Triggers t) {
                Log.d(TAG, "unhandled trigger ignored");
                return;
            }
        });
        */
    }

    abstract void configureStateMachine(StateMachineConfig<States, Triggers> smc);

    protected interface States {
        String name();
    }
    protected interface Triggers {
        String name();
    }

    protected enum BaseStates implements States {
        Preinit, Standby
    }

    protected enum BaseTriggers implements Triggers {
        TTSReady, DoubleTap
    }

    //stateless requires triggers that take a parameter to go through some nasty boilerplate setup,
    // let's keep them separate here.
    protected enum AbstractTriggers implements Triggers {
        GotSpeechAbstract, GotYesNoAbstract, GotLocationAbstract
    }

    protected TriggerWithParameters1<ArrayList, States, Triggers> GotSpeech;
    protected TriggerWithParameters1<Boolean, States, Triggers> GotYesNo;
    protected TriggerWithParameters1<Location, States, Triggers> GotLocation;


    public void onTwoFingerTap() {
        Log.d(TAG, "firing doubletap");
        sm.fire(BaseTriggers.DoubleTap);
    }

    public void onLocationChanged(Location location) {
        this.lastKnownLocation = location;
        if (this.locationListener != null) {
            this.locationListener.onLocationChanged(location);
        }
    }

    public void onRotationChanged(float rotation) {
        this.lastKnownRotation = rotation;
        if (this.rotationListener != null) {
            this.rotationListener.onRotationChanged(rotation);
        }
    }

    public void handleSpeech(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Log.d(TAG, "got speech results!");
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            waitingForSpeech.onSpeech(result);
        }
        Log.d(TAG, "got callback from speech");
        waitingForSpeech = null;

    }

    abstract private class AbstractSpeakThenAction {
        String prompt;
        String speakId;

        protected void speakPrompt(String stringToSpeak) {
            tts.speak(stringToSpeak, tts.QUEUE_FLUSH, Bundle.EMPTY, speakId);
            AbstractVoiceSM.this.waitingPrompts.put(this.speakId, this);
        }

        protected AbstractSpeakThenAction(String prompt, String speakId) {
            this.prompt = prompt;
            this.speakId = speakId;
        }

        abstract public void finishedSpeech();

        public String generatePrompt() {
            return (this.prompt != null) ? this.prompt : "Default Prompt";
        }
    }

    abstract protected class SpeakThenAction extends AbstractSpeakThenAction implements Action {

        public SpeakThenAction(String prompt, String speakId) {
            super(prompt, speakId);
        }
        public SpeakThenAction(String speakId) {
            super(null, speakId);
        }

        final public void doIt() {
            this.speakPrompt(this.generatePrompt());
        }

    }
    abstract protected class SpeakThenAction1<T> extends AbstractSpeakThenAction implements Action1<T> {

        private T data;
        public SpeakThenAction1(String prompt, String speakId) {
            super(prompt, speakId);
        }

        @Override
        final public void doIt(T data) {
            this.data = data;
            this.speakPrompt(this.generatePrompt(data));
        }

        public SpeakThenAction1(String speakId) {
            this(null, speakId);
        }

        public String generatePrompt(T data) {
            return this.generatePrompt();
        }

        final public void finishedSpeech() {
            this.doItAfterSpeech(this.data);
        }

        abstract public void doItAfterSpeech(T data);
    }


    abstract private class AbstractAskForResponse extends AbstractSpeakThenAction {

        AbstractAskForResponse(String prompt, String speakId) {
            super(prompt, speakId);
        }

        public void finishedSpeech() {
            waitingForSpeech = this;
            this.getSpeechInput();
        }

        private void getSpeechInput() {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, this.prompt);

            try {
                context.startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
            } catch (ActivityNotFoundException a) {
                Toast.makeText(context, "No go Joe!", Toast.LENGTH_SHORT).show();
            }
        }

        abstract public void onSpeech(ArrayList<String> speech);

    }

    abstract protected class AskForResponse extends AbstractAskForResponse implements Action {
        @Override public void doIt() {
            this.speakPrompt(this.generatePrompt());
        }

        public AskForResponse(String prompt, String speakId) {
            super(prompt, speakId);
        }
    }

    abstract protected class AskForResponse1<T> extends AbstractAskForResponse implements Action1<T> {
        private T data;

        public AskForResponse1(String prompt, String speakId) {
            super(prompt, speakId);
        }

        public AskForResponse1(String speakId) {
            this(null, speakId);
        }

        public String generatePrompt(T data) {
            return this.generatePrompt();
        }

        @Override public void doIt(T data) {
            this.data = data;
            this.speakPrompt(this.generatePrompt(data));
        }

        public void onSpeech(ArrayList<String> speech) {
            this.onSpeechWithData(speech, this.data);
        }

        abstract public void onSpeechWithData(ArrayList<String> speech, T data);

    }

    abstract private class AbstractAskForYesNo extends AbstractSpeakThenAction {

        public AbstractAskForYesNo(String prompt, String speakId) {
            super(prompt, speakId);
        }

        public void finishedSpeech() {
            waitingForYesNo = this;
        }

        abstract public void onYesNo(boolean response);

    }

    protected void askForYesNo(AbstractAskForYesNo askForYesNo) {
        this.waitingForYesNo = askForYesNo;
    }

    protected boolean onTap(MotionEvent event, View capturingView) {
        if (this.waitingForYesNo != null) {
            float y = event.getY() / capturingView.getHeight();

            if (y > 0.4 && y < 0.6) {
                return false;
            }

            if (y <= 0.4) {
                this.waitingForYesNo.onYesNo(false);
            } else if (y >= 0.6) {
                this.waitingForYesNo.onYesNo(true);
            }
            this.waitingForYesNo = null;
            return true;
        }
        return false;
    }

    abstract protected class AskForYesNo extends AbstractAskForYesNo implements Action {
        public AskForYesNo(String prompt, String speakId) {
            super(prompt, speakId);
        }

        @Override final public void doIt() {
            speakPrompt(this.generatePrompt());
            askForYesNo(this);
        }
    }

    abstract protected class AskForYesNo1<T> extends AbstractAskForYesNo implements Action1<T> {
        private T data;

        public AskForYesNo1(String prompt, String speakId) {
            super(prompt, speakId);
        }
        public AskForYesNo1(String speakId) {
            super(null, speakId);
        }

        @Override final public void doIt(T data) {
            this.data = data;
            speakPrompt(this.generatePrompt(data));
            askForYesNo(this);
        }

        public String generatePrompt(T data) {
            return "Default prompt";
        }

        @Override final public void onYesNo(boolean response) {
            this.onYesNoWithData(response, this.data);
        }

        abstract public void onYesNoWithData(boolean response, T data);

    }


}
