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

package com.mindsparkk.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


/**
 * Created by Anirudh on 31/08/15.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "MyWatchFace";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final long NORMAL_UPDATE_RATE_MS = 500;

    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        static final String COLON_STRING = ":";

        static final int MUTE_ALPHA = 100;

        static final int NORMAL_ALPHA = 255;

        static final int MSG_UPDATE_TIME = 0;


        private Bitmap mWeatherIcon;
        private String mWeatherMaxTemp = "";
        private String mWeatherMinTemp = "";

        static final String KEY_PATH = "/weather";
        static final String KEY_WEATHER_ID = "key_weather_id";
        static final String KEY_MAX_TEMP = "key_max_temp";
        static final String KEY_MIN_TEMP = "key_min_temp";

        /**
         * How often {@link #mUpdateTimeHandler} ticks in milliseconds.
         */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        /**
         * Handler to update the time periodically in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:

                        invalidate();
                        if (shouldTimerRun()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
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


        boolean mRegisteredReceiver = false;

        Paint mBackgroundPaint;
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mColonPaint;
        Paint mMinTempPaint;
        Paint mMaxTempPaint;
        float mColonWidth;
        boolean mMute;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;

        boolean mShouldDrawColons;
        float mXOffset;
        float mYOffset;
        float mLineHeight;

        int mInteractiveBackgroundColor =
                WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND;
        int mInteractiveHourDigitsColor =
                WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS;
        int mInteractiveMinuteDigitsColor =
                WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {

            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

            mInteractiveBackgroundColor = getResources().getColor(R.color.blue);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mInteractiveBackgroundColor);
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_date));
            mHourPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveMinuteDigitsColor);
            mMaxTempPaint = createTextPaint(resources.getColor(android.R.color.white), BOLD_TYPEFACE);
            mMinTempPaint = createTextPaint(resources.getColor(android.R.color.white));
            mColonPaint = createTextPaint(resources.getColor(R.color.digital_colons));

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            mWeatherIcon = BitmapFactory.decodeResource(getResources(),
                    R.mipmap.ic_launcher);
            initFormats();
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
                mGoogleApiClient.connect();

                registerReceiver();

                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }
            updateTimer();
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);

            mDateFormat = new SimpleDateFormat("MMM d yy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            MyWatchFace.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            MyWatchFace.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {

            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float tempSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_size_round : R.dimen.digital_temp_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mMaxTempPaint.setTextSize(tempSize);
            mMinTempPaint.setTextSize(tempSize);
            mColonPaint.setTextSize(textSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection
                        + ", low-bit ambient = " + mLowBitAmbient);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();

            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            adjustPaintColorToCurrentMode(mHourPaint, mInteractiveHourDigitsColor,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            adjustPaintColorToCurrentMode(mMinutePaint, mInteractiveMinuteDigitsColor,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mMaxTempPaint.setAntiAlias(antiAlias);
                mMinTempPaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor,
                                                   int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {

            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == android.support.wearable.watchface.WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mDatePaint.setAlpha(alpha);
                mHourPaint.setAlpha(alpha);
                mMinutePaint.setAlpha(alpha);
                mColonPaint.setAlpha(alpha);
                mMaxTempPaint.setAlpha(alpha);
                mMinTempPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerRun()) {
                updateTimer();
            }
        }

        private void updatePaintIfInteractive(Paint paint, int interactiveColor) {
            if (!isInAmbientMode() && paint != null) {
                paint.setColor(interactiveColor);
            }
        }

        private void setInteractiveHourDigitsColor(int color) {
            mInteractiveHourDigitsColor = color;
            updatePaintIfInteractive(mHourPaint, color);
        }

        private void setInteractiveMinuteDigitsColor(int color) {
            mInteractiveMinuteDigitsColor = color;
            updatePaintIfInteractive(mMinutePaint, color);
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(MyWatchFace.this);

            int width = bounds.width();
            int height = bounds.height();
            float centerX = width / 2f;
            float centerY = height / 2f;

            // Show colons for the first half of each second so the colons blink on when the time
            // updates.
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw the hours.
            float x = mXOffset;
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.format("%02d", hour);
            }
            canvas.drawText(hourString, centerX - mHourPaint.measureText(hourString), mYOffset, mHourPaint);

            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
            if (isInAmbientMode() || mMute || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, centerX, mYOffset, mColonPaint);
            }

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, centerX + mColonPaint.measureText(COLON_STRING), mYOffset, mMinutePaint);

            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {

                // Date
                String dateString = mDayOfWeekFormat.format(mDate) + ", " + mDateFormat.format(mDate);
                float datePosition = centerX - mDatePaint.measureText(dateString) / 2;
                canvas.drawText(dateString, datePosition, mYOffset + mLineHeight, mDatePaint);

                //for weather icon

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(mWeatherIcon, 40, 40, true);
                String maxTempString = mWeatherMaxTemp + "\u00b0";
                String minTempString = mWeatherMinTemp + "\u00b0";
                float imgPosition = centerX - resizedBitmap.getWidth() - mMinTempPaint.measureText(minTempString) / 2 - 15;
                float maxTempPosition = centerX - mMaxTempPaint.measureText(maxTempString) / 2;
                float minTempPosition = centerX + mMaxTempPaint.measureText(maxTempString) / 2 + 15;
                canvas.drawBitmap(resizedBitmap, imgPosition, mYOffset + 64, new Paint());

                canvas.drawText(maxTempString, maxTempPosition,
                        60 + mYOffset + 40, mMaxTempPaint);

                canvas.drawText(minTempString, minTempPosition,
                        60 + mYOffset + 40, mMinTempPaint);
            }
        }

        private void updateTimer() {

            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerRun()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerRun() {
            return isVisible() && !isInAmbientMode();
        }

        private void updateDataItemOnStartup() {
            WatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                    new WatchFaceUtil.FetchConfigDataMapCallback() {
                        @Override
                        public void onConfigDataMapFetched(DataMap startupConfig) {

                            setValuesForMissing(startupConfig);
                            WatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                            updateUiForConfigDataMap(startupConfig);
                        }
                    }
            );
        }

        private void setValuesForMissing(DataMap config) {
            addIntKeyIfMissing(config, WatchFaceUtil.BACKGROUND_COLOR,
                    getResources().getColor(R.color.blue));
            addIntKeyIfMissing(config, WatchFaceUtil.HOURS_COLOR,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            addIntKeyIfMissing(config, WatchFaceUtil.MINUTES_COLOR,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
            addIntKeyIfMissing(config, WatchFaceUtil.SECONDS_COLOR,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS);
        }

        private void addIntKeyIfMissing(DataMap config, String key, int color) {
            if (!config.containsKey(key)) {
                config.putInt(key, color);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged");
            for (DataEvent dataEvent : dataEvents) {

                DataItem dataItem = dataEvent.getDataItem();

                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    DataItem item = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().equals(KEY_PATH)) {

                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        int weatherId = dataMap.getInt(KEY_WEATHER_ID);
                        if (mWeatherIcon == null) {
                            mWeatherIcon = BitmapFactory.decodeResource(getResources(),
                                    R.mipmap.ic_launcher);
                        } else {
                            if (weatherId != 0) {
                                mWeatherIcon = BitmapFactory.decodeResource(getResources(),
                                        WatchFaceUtil.getIconsWeatherCondition(weatherId));
                            }
                        }

                        mWeatherMaxTemp = dataMap.getString(KEY_MAX_TEMP);
                        mWeatherMinTemp = dataMap.getString(KEY_MIN_TEMP);
                    }
                }
            }
        }

        private void updateUiForConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            for (String configKey : config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue;
                }
                int color = config.getInt(configKey);

                if (updateUiForKey(configKey, color)) {
                    uiUpdated = true;
                }
            }
            if (uiUpdated) {
                invalidate();
            }
        }


        private boolean updateUiForKey(String configKey, int color) {
            if (configKey.equals(WatchFaceUtil.HOURS_COLOR)) {
                setInteractiveHourDigitsColor(color);
            } else if (configKey.equals(WatchFaceUtil.MINUTES_COLOR)) {
                setInteractiveMinuteDigitsColor(color);
            } else {
                return false;
            }
            return true;
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateDataItemOnStartup();
        }

        @Override
        public void onConnectionSuspended(int cause) {

        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {

        }
    }
}