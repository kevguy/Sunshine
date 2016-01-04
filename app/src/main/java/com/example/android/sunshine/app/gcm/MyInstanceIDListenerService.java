package com.example.android.sunshine.app.gcm;

/**
 * Created by kev on 1/4/16.
 */

import android.content.Intent;

import com.google.android.gms.iid.InstanceIDListenerService;

public class MyInstanceIDListenerService extends InstanceIDListenerService {
    private static final String TAG = "MyInstanceIDLS";

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. This call is initiated by the
     * InstanceID provider.
     */
    @Override
    public void onTokenRefresh() {
        // Fetch updated Instance ID token.
        Intent intent = new Intent(this, RegistrationIntentService.class);
        startService(intent);
    }
}
