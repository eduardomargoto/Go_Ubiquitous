/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * A {@link WearableListenerService} listening for {@link WatchFaceService} config messages
 * and updating the config {@link com.google.android.gms.wearable.DataItem} accordingly.
 */
public class WatchFaceListenerService extends WearableListenerService {

    private SharedPreferences mSharedPreferences;
    public static String mWeatherIdPrefKey;
    public static String mMaxTempPrefKey;
    public static String mMinTempPrefKey;


    @Override
    public void onCreate() {
        super.onCreate();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mWeatherIdPrefKey = getString(R.string.WEATHER_ID_PREF_KEY);
        mMaxTempPrefKey = getString(R.string.MAX_TEMP_PREF_KEY);
        mMinTempPrefKey = getString(R.string.MIN_TEMP_PREF_KEY);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

        for (DataEvent event : dataEventBuffer) {
         if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = event.getDataItem();
                DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putInt(mWeatherIdPrefKey, dataMap.getInt(mWeatherIdPrefKey));
                editor.putString(mMaxTempPrefKey, dataMap.getString(mMaxTempPrefKey));
                editor.putString(mMinTempPrefKey, dataMap.getString(mMinTempPrefKey));
                editor.apply();
            }
        }
    }
}
