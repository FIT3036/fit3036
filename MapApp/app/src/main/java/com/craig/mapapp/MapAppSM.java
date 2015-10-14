package com.craig.mapapp;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.github.oxo42.stateless4j.StateMachineConfig;
import com.github.oxo42.stateless4j.delegates.Action;
import com.github.oxo42.stateless4j.delegates.Action1;

import java.util.ArrayList;

/**
 * Created by jarrad on 14/10/15.
 */
public class MapAppSM extends AbstractVoiceSM {

    private Navmesh navmesh;

    public MapAppSM(Activity context, Navmesh navmesh) {
        super(context);
        this.navmesh = navmesh;
    }

    protected void configureStateMachine(StateMachineConfig<States, Triggers> smc) {

        smc.configure(BaseStates.Standby)
                .permit(Trigger.DoubleTap, State.AskDestination);

        smc.configure(State.AskDestination)
                .onEntry(new AskForResponse("Where do you want to go?", "wherego") {
                    public void onSpeech(ArrayList<String> results) {
                        sm.fire(GotSpeech, results);
                    }
                })
                .permit(BaseTriggers.GotSpeechAbstract, State.ConfirmDestination);

        smc.configure(State.ConfirmDestination)
                .onEntryFrom(GotSpeech, new SpeakThenAction1<ArrayList>("allgood") {

                    @Override
                    public String generatePrompt(ArrayList results) {
                        ArrayList<String> castResults = (ArrayList<String>) results;
                        //use navmesh.get
                        return "You probably want "+navmesh.getCellsMatchingString(castResults.get(0)).entries().iterator().next().getValue().getName();
                    }

                    @Override
                    public void doItAfterSpeech(ArrayList results) {
                        Log.d(TAG, "Got results!!! "+results.get(0));

                    }


                }, ArrayList.class);

    }

    protected enum State implements States {
        AskDestination, ConfirmDestination, StartDirections
    }

    protected enum Trigger implements Triggers {
        DoubleTap, GotDestination
    }

    public void onTwoFingerTap() {
        Log.d(TAG, "firing doubletap");
        sm.fire(Trigger.DoubleTap);
    }
}
