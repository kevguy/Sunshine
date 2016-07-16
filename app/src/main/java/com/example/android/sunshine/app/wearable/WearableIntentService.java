package com.example.android.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by kev on 7/17/16.
 */
public class WearableIntentService extends IntentService implements GoogleApiClient.ConnectionCallbacks{
    private static final String TAG = "WearIntentServ";

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_SHORT_DESC = 1;
    private static final int INDEX_MAX_TEMP = 2;
    private static final int INDEX_MIN_TEMP = 3;

    private GoogleApiClient mGoogleApiClient;

    private static final String REQ_PATH = "/weather";
    private static final String KEY_WEATHER_ID = "com.example.key.weather_id";
    private static final String KEY_TEMP_MAX = "com.example.key.max_temp";
    private static final String KEY_TEMP_MIN = "com.example.key.min_temp";
    private static final String KEY_LOCATION = "com.example.key.location";

    private int mWeatherId;
    private double mMaxTemp;
    private double mMinTemp;
    private String mLocation;

    public WearableIntentService(){
        super("WearableIntentService");
    }

    @Override
    public void onCreate() {
        //Log.d(TAG, "onCreate");
        super.onCreate();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        //Log.d(TAG, "onStart");
        super.onStart(intent, startId);
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "onDestroy");
        super.onDestroy();
    }


    /**
     * This method is invoked on the worker thread with a request to process.
     * Only one Intent is processed at a time, but the processing happens on a
     * worker thread that runs independently from other application logic.
     * So, if this code takes a long time, it will hold up other requests to
     * the same IntentService, but it will not hold up anything else.
     * When all requests have been handled, the IntentService stops itself,
     * so you should not call {@link #stopSelf}.
     *
     * @param intent The value passed to {@link
     *                //Context#startService(Intent)}.
     */
    @Override
    protected void onHandleIntent(Intent intent){
        //Log.d(TAG, "onHandleIntent");
        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        mWeatherId = data.getInt(INDEX_WEATHER_ID);
        mMaxTemp = data.getDouble(INDEX_MAX_TEMP);
        mMinTemp = data.getDouble(INDEX_MIN_TEMP);
        mLocation = Utility.getPreferredLocation(this).trim().toUpperCase();
        data.close();

        // create or connect a Google API client
        if (mGoogleApiClient == null) {
            //Log.d(TAG, "mGoogleApiClient.build()");
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    //.addOnConnectionFailedListener(this)
                    .build();
        }
        if (!mGoogleApiClient.isConnected()) {
            //Log.d(TAG, "mGoogleApiClient.connect()");
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        // create and send a request to update the weather on wearable
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(REQ_PATH);
        putDataMapRequest.setUrgent();

        putDataMapRequest.getDataMap().putInt(KEY_WEATHER_ID, mWeatherId);
        putDataMapRequest.getDataMap().putDouble(KEY_TEMP_MAX, mMaxTemp);
        putDataMapRequest.getDataMap().putDouble(KEY_TEMP_MIN, mMinTemp);
        putDataMapRequest.getDataMap().putString(KEY_LOCATION, mLocation);

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(
                        new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult(DataApi.DataItemResult dataItemResult) {
                                if (dataItemResult.getStatus().isSuccess()) {
                                    Log.v(TAG, "Sent successful");
                                } else {
                                    Log.v(TAG, "Fails sending data");
                                }

                                mGoogleApiClient.disconnect();
                            }
                        }
                );
    }

    @Override
    public void onConnectionSuspended(int i) {
        //Log.d(TAG, "onConnectionSuspended");
    }
}
