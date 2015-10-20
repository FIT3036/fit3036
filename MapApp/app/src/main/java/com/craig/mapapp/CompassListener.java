package com.craig.mapapp;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Created by jarrad on 16/10/15.
 */
public class CompassListener implements SensorEventListener {
    private Sensor accelerometer;
    private Sensor magnetometer;
    private SensorManager sensorManager;
    private RotationListener rotationListener;
    private static final String TAG = "CompasListener";

    public CompassListener(Context context) {
        sensorManager = (SensorManager) context.getSystemService(context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void startListening() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void stopListening() {
        sensorManager.unregisterListener(this);
    }

    float[] gravity;
    float[] geomagnetic;

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                gravity = event.values.clone();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                geomagnetic = event.values.clone();
                break;
            default:
                return;
        }
        notifyListener();
    }

    protected float calculateRotation() throws Exception {
        Log.d(TAG, "Calculating rotation");
        if (gravity == null || geomagnetic == null) {
            throw new Exception();
        }

        float rotation[] = new float[9];
        float inclination[] = new float[9];

        boolean success = SensorManager.getRotationMatrix(rotation, inclination, gravity, geomagnetic);

        if (!success) {
            throw new Exception();
        }

        float orientation[] = new float[3];
        SensorManager.getOrientation(rotation, orientation);
        float azimuthalRotation = orientation[0];

        Log.d(TAG, String.format("rotation calculated to be %f", azimuthalRotation));
        return azimuthalRotation;

    }

    protected void notifyListener() {
        try {
            float rotation = this.calculateRotation();
            rotationListener.onRotationChanged(rotation);
        } catch (Exception e) { }
    }

    public void setRotationListener(RotationListener rotationListener) {
        this.rotationListener = rotationListener;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}

