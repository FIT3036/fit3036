package com.craig.mapapp;

import android.app.Activity;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.github.oxo42.stateless4j.StateMachineConfig;
import com.github.oxo42.stateless4j.delegates.Action;
import com.github.oxo42.stateless4j.delegates.Action1;
import com.github.oxo42.stateless4j.triggers.TriggerWithParameters1;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Queue;


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

        GotPossibleDestinations = smc.setTriggerParameters(Trigger.GotPossibleDestinationsAbstract, Queue.class);
        GotDestination = smc.setTriggerParameters(Trigger.GotDestinationAbstract, Navmesh.Cell.class);

        smc.configure(BaseStates.Standby)
                .onEntry(new SpeakThenAction(context.getString(R.string.sm_intro), "intro") {

                    @Override
                    public void finishedSpeech() {
                        return;
                    }
                })
                .permit(BaseTriggers.DoubleTap, State.AskDestination);

        smc.configure(State.AskDestination)
                .onEntry(new AskForResponse("Where do you want to go?", "wherego") {
                    public void onSpeech(ArrayList<String> results) {
                        sm.fire(GotSpeech, results);
                    }
                })
                .permit(GotSpeech.getTrigger(), State.SearchDestinations);

        smc.configure(State.SearchDestinations)
                .onEntryFrom(GotSpeech, new Action1<ArrayList>() {

                    @Override
                    public void doIt(ArrayList voiceSearchMatchesUncast) {
                        ArrayList<String> voiceSearchMatches = (ArrayList<String>) voiceSearchMatchesUncast;
                        Queue<Pair<Double, Navmesh.Cell>> matchingCells = navmesh.getCellsMatchingString(voiceSearchMatches.get(0));
                        sm.fire(GotPossibleDestinations, matchingCells);
                    }
                }, ArrayList.class)
                .permit(GotPossibleDestinations.getTrigger(), State.ConfirmDestination);

        smc.configure(State.ConfirmDestination)
                .onEntryFrom(GotPossibleDestinations, new AskForYesNo1<Queue>("confirmDest") {

                    @Override
                    public String generatePrompt(Queue queueOfResultsUncast) {
                        Queue<Pair<Double, Navmesh.Cell>> queueOfResults = (Queue<Pair<Double, Navmesh.Cell>>) queueOfResultsUncast;

                        //use navmesh.get
                        return "Do you want " + queueOfResults.peek().second.getName()+"?";
                    }

                    @Override
                    public void onYesNoWithData(boolean response, Queue queueOfResultsUncast) {
                        Queue<Pair<Double, Navmesh.Cell>> queueOfResults = (Queue<Pair<Double, Navmesh.Cell>>) queueOfResultsUncast;
                        if (response) {
                            sm.fire(GotDestination, queueOfResults.peek().second);
                        } else {
                            queueOfResults.poll();
                            sm.fire(GotPossibleDestinations, queueOfResults);
                        }
                    }


                }, Queue.class)
                .permitReentry(GotPossibleDestinations.getTrigger())
                .permit(GotDestination.getTrigger(), State.ConfirmedDestination)
                .permit(BaseTriggers.DoubleTap, State.AskDestination);

        smc.configure(State.ConfirmedDestination)
                .onEntryFrom(GotDestination, new SpeakThenAction1<Navmesh.Cell>("maybedest") {

                    @Override
                    public String generatePrompt(Navmesh.Cell cell) {
                        return "I'm sending you off to "+cell.getName()+", good luck!";
                    }

                    @Override
                    public void doItAfterSpeech(Navmesh.Cell data) {
                        return;
                    }
                }, Navmesh.Cell.class)
                .permit(BaseTriggers.DoubleTap, State.AskDestination);;

    }

    protected enum State implements States {
        AskDestination, SearchDestinations, ConfirmDestination, ConfirmedDestination, StartDirections
    }

    protected enum Trigger implements Triggers {
        GotPossibleDestinationsAbstract, GotDestinationAbstract
    }
    private TriggerWithParameters1<Queue, States, Triggers> GotPossibleDestinations;
    private TriggerWithParameters1<Navmesh.Cell, States, Triggers> GotDestination;

}
