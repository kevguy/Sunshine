package com.example.android.sunshine.app.wearable;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by kev on 7/17/16.
 */
public class MyWearableListenerService extends WearableListenerService
    implements DataApi.DataListener {
    private static final String TAG = "WearableListenerService";

    private static final String REQ_WEATHER_PATH = "/weather-req";

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " +  messageEvent);

        String path = messageEvent.getPath();
        Log.d(TAG, "message path: " + path);

        // Check to see if the message is to start an activity
        if (path.equals(REQ_WEATHER_PATH)) {
            // start the service sending the updated weather data to the wearable
            Context context = this.getApplicationContext();
            context.startService(new Intent(context, WearableIntentService.class));
        }
    }
}