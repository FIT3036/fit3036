package com.craig.mapapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import com.github.oxo42.stateless4j.StateMachine;
import com.github.oxo42.stateless4j.StateMachineConfig;
import com.github.oxo42.stateless4j.delegates.Action;
import com.github.oxo42.stateless4j.delegates.Action1;
import com.github.oxo42.stateless4j.triggers.TriggerWithParameters;
import com.github.oxo42.stateless4j.triggers.TriggerWithParameters1;

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
    AskForResponse waitingForSpeech;
    final Activity context;
    final static String TAG = "AbstractVoiceSM";
    final static int REQ_CODE_SPEECH_INPUT = 100;

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


        GotSpeech = smc.setTriggerParameters(BaseTriggers.GotSpeechAbstract, ArrayList.class);

        configureStateMachine(smc);

        sm = new StateMachine<States, Triggers>(BaseStates.Preinit, smc);
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
        TTSReady, GotSpeechAbstract
    }

    protected TriggerWithParameters1 GotSpeech;

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

        protected void speakPrompt() {
            tts.speak(prompt, tts.QUEUE_FLUSH, Bundle.EMPTY, speakId);
            AbstractVoiceSM.this.waitingPrompts.put(this.speakId, this);
        }

        protected AbstractSpeakThenAction(String prompt, String speakId) {
            this.prompt = prompt;
            this.speakId = speakId;
        }

        abstract public void finishedSpeech();
    }

    abstract protected class SpeakThenAction extends AbstractSpeakThenAction implements Action {

        public SpeakThenAction(String prompt, String speakId) {
            super(prompt, speakId);
        }


        final public void doIt() {
            this.speakPrompt();
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
            if (this.prompt == null) {
                this.prompt = this.generatePrompt(data);
            }
            this.speakPrompt();
        }

        public SpeakThenAction1(String speakId) {
            this(null, speakId);
        }

        public String generatePrompt(T data) {
            return "Default String";
        }

        final public void finishedSpeech() {
            this.doItAfterSpeech(this.data);
        }

        abstract public void doItAfterSpeech(T data);
    }


    abstract protected class AskForResponse extends AbstractSpeakThenAction implements Action {

        AskForResponse(String prompt, String speakId) {
            super(prompt, speakId);
        }

        @Override public void doIt() {
            this.speakPrompt();
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


}
