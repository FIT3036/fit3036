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

import static com.google.maps.android.SphericalUtil.computeDistanceBetween;


/**
 * Created by jarrad on 14/10/15.
 */
public class MapAppSM extends AbstractVoiceSM {

    private Navmesh navmesh;
    private RouteDirector routeDirector;
    private NavmeshViewer navmeshViewer;
    private Beeper beeper;

    public MapAppSM(Activity context, Navmesh navmesh, NavmeshViewer navmeshViewer, Beeper beeper) {
        super(context);
        this.navmesh = navmesh;
        this.navmeshViewer = navmeshViewer;
        this.beeper = beeper;
    }

    public static LatLng getLatLng(Location location) {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    public static LatLng getLatLng(Navmesh.Point point) {
        return new LatLng(point.x, point.y);
    }

    public Navmesh.Point getPoint(Location location) {
        return new Navmesh.Point(location.getLatitude(), location.getLongitude());
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
                .ignore(BaseTriggers.DoubleTap)
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
                        return "Do you want " + queueOfResults.peek().second.getName() + "?";
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
                            startPoint = new Navmesh.Point(-37.912198, 145.133097);
                        }
                        Log.d(TAG, targetCell.getName());
                        Log.d(TAG, String.format("Centre: Lat: %f, Long: %f", targetCell.centre.x, targetCell.centre.y));
                        Navmesh.Point[] points = targetCell.getPoints();
                        Log.d(TAG, String.format("Cell points: %f,%f; %f,%f; %f,%f; %f,%f", points[0].x, points[0].y, points[1].x, points[1].y, points[2].x, points[2].y, points[3].x, points[3].y));

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
                .permit(Trigger.ReachedPoint, State.GivingNextDirection)
                .permit(BaseTriggers.DoubleTap, State.GivingNextDirection);



        smc.configure(State.GivingNextDirection)
                .substateOf(State.GivingDirections)
                .onEntry( new SpeakThenAction("reachedPoint") {

                    @Override
                    public String generatePrompt() {
                        return routeDirector.generateDirections();
                    }

                    @Override
                    public void finishedSpeech() {
                        if (routeDirector.markPointReached()) {
                            sm.fire(Trigger.DirectionGiven);
                        } else {
                            sm.fire(Trigger.ReachedEnd);
                        }
                    }
                })
                .permit(Trigger.DirectionGiven, State.GivingDirections)
                .permit(Trigger.ReachedEnd, State.ReachedEnd);

        smc.configure(State.ReachedEnd)
                .onEntry( new Action() {

                    @Override
                    public void doIt() {
                        //we are done! may as well just stay here, they don't want to do anything else..
                        //we have already congratulated them on reaching the end so we don't even need to do that.
                    }
                })
                .permit(BaseTriggers.DoubleTap, State.AskDestination);

    }

    private class RouteDirector {
        private final Deque<Navmesh.Point> directionsList;
        public final RouteLocationListener locationListener;

        private class RouteLocationListener implements com.google.android.gms.location.LocationListener {
            @Override
            public void onLocationChanged(Location location) {

                double metres = computeDistanceBetween(getLatLng(location), getLatLng(directionsList.peek()));
                Navmesh.Point diff = directionsList.peek().subtract(new Navmesh.Point(location.getLatitude(), location.getLongitude()));
                double direction = Math.atan2(diff.y, diff.x);
                double directionDiff = lastKnownRotation - direction;

                beeper.setAngle((float) directionDiff);
                beeper.setDistance((float) metres);

                for (Navmesh.Point point :directionsList) {
                    //get distance in metres;
                    double distance = computeDistanceBetween(getLatLng(location), getLatLng(point));
                    if (distance < 1) {
                        reachedPoint(point);
                    }
                }
            }

        }


        private void reachedPoint(Navmesh.Point point) {
            while (directionsList.peek() != point)
            { directionsList.poll(); }
            //now the reached point is at the top of the queue
            Log.d(TAG, "you reached a point!");
            sm.fire(Trigger.ReachedPoint);
        }

        public boolean markPointReached() {
            try {
                directionsList.poll();
                return true;
            } catch (NoSuchElementException e) {
                return false;
            }
        }


        public String generateDirections() {
            Iterator<Navmesh.Point> pointI = directionsList.iterator();
            Navmesh.Point currentPoint;
            Navmesh.Point nextPoint;
            try {
                currentPoint = pointI.next();
                nextPoint = pointI.next();
            } catch (NoSuchElementException e) {
                return "Looks like you reached the end! Well done!";
            }

            double cutoffMetres = 5; // we are interested in landmarks in a five metre radius of our path.

            double cutoffLatLong = navmesh.metres2LatLong(cutoffMetres);

            Pair<Navmesh.Landmark, Navmesh.Point> result = navmesh.getClosestLandmarkTo(currentPoint, nextPoint, cutoffLatLong);
            Navmesh.Landmark landmark = result.first;
            Navmesh.Point landmarkToLine = result.second;

            //sentences as member names because it's better than comments!
            double distanceToWalkAfterPassingLandmark = 0;

            if (landmark != null) {
                Navmesh.Point closestPointOnLine = landmark.position.add(landmarkToLine);
                distanceToWalkAfterPassingLandmark = computeDistanceBetween(getLatLng(closestPointOnLine), getLatLng(nextPoint));
            }


            Navmesh.Point diff = nextPoint.subtract(currentPoint);

            double direction = Math.atan2(diff.y, diff.x);
            Log.d(TAG, String.format("direction is %f, or %f deg", direction, direction/Math.PI*180));

            double rotationDiff = direction - lastKnownRotation;
            rotationDiff = normalizeAngle(rotationDiff);
            Log.d(TAG, String.format("normalized angle is %f, or %f deg", rotationDiff, rotationDiff/Math.PI*180));

            double metres = computeDistanceBetween(getLatLng(currentPoint), getLatLng(nextPoint));


            double clockHour = (rotationDiff + Math.PI) / (2*Math.PI) * 12;
            int clockNumber = (int) Math.round(clockHour);

            if (clockNumber == 0) {
                clockNumber = 12;
            }

            int metresNum = (int) Math.round(metres / 5) * 5;

            if (landmark == null) {
                return String.format("Turn towards %d o'clock, and walk for about %d metres.", clockNumber, metresNum);
            } else if (distanceToWalkAfterPassingLandmark < cutoffMetres) {
                return String.format("Turn towards %d o'clock, and walk for about %d metres until %s.", clockNumber, metresNum, landmark.text);
            } else {
                int metresRemaining = (int) Math.round(distanceToWalkAfterPassingLandmark);
                return String.format("Turn towards %d o'clock, walk for about %d metres until %s, then walk for another %d metres.", clockNumber, metresNum, landmark.text, metresRemaining);
            }

        }

        public RouteDirector(Deque<Navmesh.Point> directionsList) {
            this.directionsList = directionsList;
            this.locationListener = new RouteLocationListener();
            navmeshViewer.drawPath(directionsList);
            beeper.startBeeping();
        }


    }


    protected enum State implements States {
        AskDestination, SearchDestinations, ConfirmDestination, ConfirmedDestination, GivingDirections, GivingNextDirection, ReachedEnd
    }

    protected enum Trigger implements Triggers {
        GotPossibleDestinationsAbstract, GotDestinationAbstract, StartDirectionsAbstract, ReachedPoint, DirectionGiven, ReachedEnd
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
