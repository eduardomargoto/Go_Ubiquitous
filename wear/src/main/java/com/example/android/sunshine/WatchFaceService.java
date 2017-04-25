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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class WatchFaceService extends CanvasWatchFaceService {
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final long NORMAL_UPDATE_RATE_MS = 1000;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements SharedPreferences.OnSharedPreferenceChangeListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        static final String COLON_STRING = ":";

        static final int MSG_UPDATE_TIME = 0;

        final long  mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        boolean mRegisteredReceiver = false;
        private SharedPreferences mSharedPreferences;

        Paint mBackgroundPaint;
        Paint mDatePaint;
        Paint mTimePaint;
        Paint mWeatherIconBitmapPaint;
        Paint mLowTempTextPaint;
        Paint mHighTempTextPaint;
        Rect mBitmapRect;
        Rect mLowerRect;
        Rect mUpperRect;

        Calendar mCalendar;
        Date mDate;
        java.text.DateFormat mDateFormat;
        SimpleDateFormat mDayWeek;

        private int mWeatherIconId;
        private int width;
        private int upperRectYOffset;
        private int highTempTextYOffset;
        private int highTempTextXOffset;
        private int lowTempTextYOffset;
        private int lowTempTextXOffset;

        private float highTempTextSize;
        private float lowTempTextSize;

        /* Preference keys*/
        private String weatherIdPrefKey;
        private String maxTempPrefKey;
        private String minTempPrefKey;
        private Bitmap weatherIconBitmap;


        String highTempText;
        String lowTempText;

        private boolean shouldSendMessageToDevice = true;

        private final int white = Color.parseColor("White");
        boolean mLowBitAmbient;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        /**
         * Handler to update the time periodically in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this).build());
            Resources resources = WatchFaceService.this.getResources();

            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
            weatherIdPrefKey = getString(R.string.WEATHER_ID_PREF_KEY);

            maxTempPrefKey = getString(R.string.MAX_TEMP_PREF_KEY);
            minTempPrefKey = getString(R.string.MIN_TEMP_PREF_KEY);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(getColor(R.color.config_activity_background));

            mDatePaint = createTextPaint(white);
            mTimePaint = createTextPaint(white, BOLD_TYPEFACE);

            highTempTextSize = resources.getDimension(R.dimen.high_temp_text_size);
            lowTempTextSize = resources.getDimension(R.dimen.low_temp_text_size);

            mHighTempTextPaint = createTextPaint(white, BOLD_TYPEFACE);
            mHighTempTextPaint.setTextSize(highTempTextSize);

            mLowTempTextPaint = createTextPaint(white, NORMAL_TYPEFACE);
            mLowTempTextPaint.setTextSize(lowTempTextSize);

            mWeatherIconBitmapPaint = new Paint();
            mWeatherIconBitmapPaint.setDither(true);
            mWeatherIconBitmapPaint.setFilterBitmap(true);

            mWeatherIconId = mSharedPreferences.getInt(weatherIdPrefKey, -1);
            if (mWeatherIconId == -1) {
                shouldSendMessageToDevice = true;
                mGoogleApiClient.connect();
            } else {
                shouldSendMessageToDevice = false;
                mGoogleApiClient.disconnect();
            }

            weatherIconBitmap = WatchFaceUtil.getWeatherIconBitmapFromId(resources, mWeatherIconId);
            highTempText = mSharedPreferences.getString(maxTempPrefKey, "");
            lowTempText = mSharedPreferences.getString(minTempPrefKey, "");

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();
        }


        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            this.width = width;
            upperRectYOffset = (int) (height - height * 0.5);
            mUpperRect = new Rect();
            mLowerRect = new Rect();

            mUpperRect.set(0, 0, width, upperRectYOffset);
            upperRectYOffset = (int) (height - height * 0.3);
            mLowerRect.set(0, upperRectYOffset, width, height);

            calculateDataSpecificMeasurements();
        }

        private void calculateDataSpecificMeasurements() {
            Rect rect = new Rect();
            mHighTempTextPaint.getTextBounds(highTempText, 0, highTempText.length(), rect);
            int highTempTextHeight = rect.height();
            int highTempTextWidth = rect.width();

            mLowTempTextPaint.getTextBounds(lowTempText, 0, lowTempText.length(), rect);
            int lowTempTextHeight = rect.height();

            int spaceTemps = (int) (width * 0.15);

            highTempTextYOffset = mLowerRect.top + highTempTextHeight / 2;
            highTempTextXOffset = (mLowerRect.centerX() + 25) - (highTempTextWidth);

            lowTempTextYOffset = mLowerRect.top + lowTempTextHeight / 2;
            lowTempTextXOffset = (mLowerRect.centerX() + 10) + spaceTemps / 2;


            mBitmapRect = new Rect();
            int halfLength = (mWeatherIconId == -1) ? 12 : 12;

            int widthAlign = (int) (width * 0.20);
            int weatherIconBitmapOffset = width / halfLength;
            int weatherIconBitmapLeft = (mLowerRect.centerX() - widthAlign) - weatherIconBitmapOffset;
            int weatherIconBitmapTop = upperRectYOffset - weatherIconBitmapOffset;
            int weatherIconBitmapBottom = upperRectYOffset + weatherIconBitmapOffset;
            int weatherIconBitmapRight = (mLowerRect.centerX() - widthAlign) + weatherIconBitmapOffset;
            mBitmapRect.set(weatherIconBitmapLeft, weatherIconBitmapTop, weatherIconBitmapRight, weatherIconBitmapBottom);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                if (mGoogleApiClient != null && shouldSendMessageToDevice && !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
            }

            updateTimer();
        }

        private void initFormats() {
            mDayWeek = new SimpleDateFormat("EE", Locale.getDefault());
            mDateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault());
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            WatchFaceService.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            WatchFaceService.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            Resources resources = WatchFaceService.this.getResources();

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mTimePaint.setTextSize(resources.getDimension(R.dimen.digital_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mTimePaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            adjustPaintColorToCurrentMode(mBackgroundPaint, getColor(R.color.config_activity_background),
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);


            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mTimePaint.setAntiAlias(antiAlias);

                mBackgroundPaint.setAntiAlias(antiAlias);

                mHighTempTextPaint.setAntiAlias(antiAlias);
                mLowTempTextPaint.setAntiAlias(antiAlias);
                mWeatherIconBitmapPaint.setDither(false);
                mWeatherIconBitmapPaint.setFilterBitmap(false);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor, int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }


        private String formatTwoDigitNumber(int hour) {
            return String.format(getString(R.string.two_digit_format), hour);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(WatchFaceService.this);

            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw the hours.
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            String timeText = hourString + COLON_STRING + minuteString;
            String dateText = mDayWeek.format(mDate).toUpperCase() + ", " + mDateFormat.format(mDate).toUpperCase();


            Rect rect = new Rect();
            mTimePaint.getTextBounds(timeText, 0, timeText.length(), rect);
            int timeTextHeight = rect.height();
            int timeTextWidth = rect.width();
            mDatePaint.getTextBounds(dateText, 0, dateText.length(), rect);
            int dateTextWidth = rect.width();

            // x offsets
            int hourTextXOffset = mUpperRect.centerX() - timeTextWidth / 2;
            int colonTextXOffset = hourTextXOffset + (int) (mTimePaint.measureText(hourString));
            int minuteTextXOffset = colonTextXOffset + (int) (mTimePaint.measureText(COLON_STRING));
            int dateTextXOffset = mUpperRect.centerX() - dateTextWidth / 2;
            // y offsets
            int timeTextYOffset = timeTextHeight + mUpperRect.centerY() - (int) (upperRectYOffset * 0.1);
            int dateTextYOffset = timeTextYOffset + (int) (upperRectYOffset * 0.2);


            // Draw the time.
            canvas.drawText(hourString + COLON_STRING, hourTextXOffset, timeTextYOffset, mTimePaint);
            canvas.drawText(minuteString, minuteTextXOffset, timeTextYOffset, mTimePaint);

            // Date
            canvas.drawText(dateText, dateTextXOffset, dateTextYOffset, mDatePaint);

            if (!isInAmbientMode()) {
                //draw temperatures
                canvas.drawText(highTempText, highTempTextXOffset, highTempTextYOffset, mHighTempTextPaint);
                canvas.drawText(lowTempText, lowTempTextXOffset, lowTempTextYOffset, mLowTempTextPaint);
                //draw weatherIcon
                canvas.drawBitmap(weatherIconBitmap, null, mBitmapRect, mWeatherIconBitmapPaint);
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }


        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (shouldSendMessageToDevice) {
                requestDataFromDevice(getString(R.string.request_data_path), "");
            }
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(weatherIdPrefKey)) {
                mWeatherIconId = sharedPreferences.getInt(weatherIdPrefKey, -1);
                weatherIconBitmap = WatchFaceUtil.getWeatherIconBitmapFromId(getResources(), mWeatherIconId);
            } else if (key.equals(maxTempPrefKey)) {
                highTempText = sharedPreferences.getString(maxTempPrefKey, "");
            } else if (key.equals(minTempPrefKey)) {
                lowTempText = sharedPreferences.getString(minTempPrefKey, "");
            }
            calculateDataSpecificMeasurements();

            if (!isInAmbientMode()) {
                invalidate();
            }
        }

        private void requestDataFromDevice(String requestPath, String message) {
            new SendToDataLayerThread(requestPath, message).start();
        }

        private class SendToDataLayerThread extends Thread {
            private final String path;
            private final String message;

            public SendToDataLayerThread(String requestPath, String message) {
                path = requestPath;
                this.message = message;
            }

            @Override
            public void run() {
                // TODO: 01-Mar-17 Make sure if this is the right way
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

                for (Node node : nodes.getNodes()) {
                    Wearable.MessageApi.sendMessage(
                            mGoogleApiClient, node.getId(), path, message.getBytes()).await();
                }
                shouldSendMessageToDevice = false;
                mGoogleApiClient.disconnect();
            }
        }
    }
}
