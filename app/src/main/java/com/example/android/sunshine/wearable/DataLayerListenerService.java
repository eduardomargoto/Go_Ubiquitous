package com.example.android.sunshine.wearable;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by EDUARDO_MARGOTO on 24/04/2017.
 */

public class DataLayerListenerService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        startService(new Intent(this, WearableDataService.class));
    }
}
