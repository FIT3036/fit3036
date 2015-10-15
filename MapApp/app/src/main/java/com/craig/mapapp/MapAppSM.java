package com.craig.mapapp;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.github.oxo42.stateless4j.StateMachineConfig;
import com.github.oxo42.stateless4j.delegates.Action;
import com.github.oxo42.stateless4j.delegates.Action1;
import com.github.oxo42.stateless4j.triggers.TriggerWithParameters1;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;


/**
 * Created by jarrad on 14/10/15.
 */
public class MapAppSM extends AbstractVoiceSM {

    private Navmesh navmesh;
    private RouteDirector routeDirector;
    private NavmeshViewer navmeshViewer;

    public MapAppSM(Activity context, Navmesh navmesh, NavmeshViewer navmeshViewer) {
        super(context);
        this.navmesh = navmesh;
        this.navmeshViewer = navmeshViewer;
    }

    public static LatLng getLatLng(Location location) {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    public static LatLng getLatLng(Navmesh.Point point) {
        return new LatLng(point.x, point.y);
    }

    public Navmesh.Point getPoint(Location location) {
        return navmesh.new Point(location.getLatitude(), location.getLongitude());
    }
    public static double normalizeAngle(double angle) {
        while (angle <= Math.PI) angle += 2*Math.PI;
        while (angle > Math.PI) angle -= 2*Math.PI;
        return angle;
    }


    protected void configureStateMachine(StateMachineConfig<States, Triggers> smc) {

        initializeParameterizedTriggers(smc);

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
                    public String generatePrompt(Navmesh.Cell targetCell) {
                        return "I'm sending you off to " + targetCell.getName() + ", good luck!";
                    }

                    @Override
                    public void doItAfterSpeech(Navmesh.Cell targetCell) {
                        Navmesh.Point testPoint = getPoint(lastKnownLocation);
                        Navmesh.Point startPoint;
                        try {
                            Navmesh.Cell testCell = navmesh.getCellContaining(testPoint);
                            startPoint = testPoint;
                        } catch (NoSuchElementException e) {
                            startPoint = navmesh.new Point(-37.912198, 145.133097);
                        }
                        Log.d(TAG, targetCell.getName());
                        Log.d(TAG, String.format("Centre: Lat: %f, Long: %f", targetCell.centre.x, targetCell.centre.y));
                        Navmesh.Point[] points = targetCell.getPoints();
                        Log.d(TAG, String.format("Cell points: %f,%f; %f,%f; %f,%f; %f,%f", points[0].x,points[0].y,points[1].x,points[1].y,points[2].x,points[2].y,points[3].x,points[3].y));

                        Deque<Navmesh.Point> directionsList = navmesh.aStarPoints(startPoint, targetCell.centre);
                        sm.fire(StartDirections, directionsList);
                    }
                }, Navmesh.Cell.class)
                .permit(StartDirections.getTrigger(), State.GivingDirections)
                .permit(BaseTriggers.DoubleTap, State.AskDestination);

        smc.configure(State.GivingDirections)
                .onEntryFrom(StartDirections, new Action1<Deque>() {

                    @Override public void doIt(Deque directionsListUncast) {
                        Deque<Navmesh.Point> directionsList = (Deque<Navmesh.Point>) directionsListUncast;
                        routeDirector = new RouteDirector(directionsList);
                        locationListener = routeDirector.locationListener;
                    }

                }, Deque.class)
                .permit(Trigger.GiveDirectionTo, State.GivingNextDirection);

        smc.configure(State.GivingNextDirection)
                .substateOf(State.GivingDirections)
                .onEntryFrom(Trigger.GiveDirectionTo, new SpeakThenAction("reachedPoint") {

                    @Override public String generatePrompt() {
                        return routeDirector.generateDirections();
                    }

                    @Override
                    public void finishedSpeech() {
                        routeDirector.markPointReached();
                        sm.fire(Trigger.DirectionGiven);
                    }
                })
                .permit(Trigger.DirectionGiven, State.GivingDirections);

    }

    private class RouteDirector {
        private final Deque<Navmesh.Point> directionsList;
        public final RouteLocationListener locationListener;

        private class RouteLocationListener implements com.google.android.gms.location.LocationListener {
            @Override
            public void onLocationChanged(Location location) {
                for (Navmesh.Point point :directionsList) {
                    //get distance in metres;
                    double distance = SphericalUtil.computeDistanceBetween(getLatLng(location), getLatLng(point));
                    if (distance < 0.4) {
                        while (directionsList.peek() != point)
                            { directionsList.poll(); }
                        //now the reached point is at the top of the queue
                        Log.d(TAG, "you reached a point!");
                    }
                }
            }
        }

        public void markPointReached() {
            directionsList.poll();
        }

        public String generateDirections() {
            Iterator<Navmesh.Point> pointI = directionsList.iterator();
            Navmesh.Point currentPoint = pointI.next();
            Navmesh.Point nextPoint = pointI.next();

            Navmesh.Point diff = nextPoint.subtract(currentPoint);
            double direction = Math.atan2(diff.y, diff.x);

            double rotationDiff = direction - lastKnownRotation;
            rotationDiff = normalizeAngle(rotationDiff);

            double clockHour = (rotationDiff + Math.PI) / 2*Math.PI * 12;
            int clockNumber = (int) Math.round(clockHour);

            if (clockNumber == 0) {
                clockNumber = 12;
            }

            double metres = com.google.maps.android.SphericalUtil.computeDistanceBetween(getLatLng(currentPoint), getLatLng(nextPoint));
            int metresNum = (int) Math.round(metres / 5) * 5;

            return String.format("Turn towards %i o'clock, and walk for about %i metres.", clockNumber, metresNum);

        }

        public RouteDirector(Deque<Navmesh.Point> directionsList) {
            this.directionsList = directionsList;
            this.locationListener = new RouteLocationListener();
            navmeshViewer.drawPath(directionsList);
        }


    }


    protected enum State implements States {
        AskDestination, SearchDestinations, ConfirmDestination, ConfirmedDestination, GivingDirections, GivingNextDirection
    }

    protected enum Trigger implements Triggers {
        GotPossibleDestinationsAbstract, GotDestinationAbstract, StartDirectionsAbstract, GiveDirectionTo, DirectionGiven
    }

    private TriggerWithParameters1<Queue, States, Triggers> GotPossibleDestinations;
    private TriggerWithParameters1<Navmesh.Cell, States, Triggers> GotDestination;
    private TriggerWithParameters1<Deque, States, Triggers> StartDirections;


    private void initializeParameterizedTriggers(StateMachineConfig<States, Triggers> smc) {

        GotPossibleDestinations = smc.setTriggerParameters(Trigger.GotPossibleDestinationsAbstract, Queue.class);
        GotDestination = smc.setTriggerParameters(Trigger.GotDestinationAbstract, Navmesh.Cell.class);
        StartDirections = smc.setTriggerParameters(Trigger.StartDirectionsAbstract, Deque.class);

    }

}
