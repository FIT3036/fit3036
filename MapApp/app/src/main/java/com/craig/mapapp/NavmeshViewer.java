package com.craig.mapapp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.craig.mapapp.Navmesh;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.Queue;

/**
 * Created by jarrad on 23/09/15.
 */
public class NavmeshViewer {
    private static final String TAG = "NavmeshViewer";
    private GoogleMap map;
    private Navmesh navmesh;
    public NavmeshViewer(GoogleMap newMap, Navmesh newNavmesh) {
        this.map = newMap;
        this.navmesh = newNavmesh;
    }

    public void draw() {
        Log.d(TAG, "drawing...");
        for (Navmesh.Cell cell : this.navmesh.getCells() ) {
            PolygonOptions cellOptions = new PolygonOptions();
            for (Navmesh.Point point: cell.getPoints()) {
                cellOptions.add(new LatLng(point.x, point.y));
                Log.d(TAG, String.format("added a points: %f,%f", point.x, point.y));
            }
            this.map.addPolygon(cellOptions);
            Log.d(TAG, "added a cell!");
        }

    }

    public void drawPath(final Queue<Navmesh.Point> points) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                _drawPath(points);
            }
        });
    }

    public void _drawPath(Queue<Navmesh.Point> points) {
        Log.d(TAG, "drawing path");
        PolylineOptions pathOptions = new PolylineOptions();
        for (Navmesh.Point point: points) {
            Log.d(TAG, String.format("added a point: %f,%f", point.x, point.y));
            pathOptions.add(new LatLng(point.x, point.y));
            pathOptions.color(0xffee2233);
        }
        this.map.addPolyline(pathOptions);
        Log.d(TAG, "added path!");
    }

}


