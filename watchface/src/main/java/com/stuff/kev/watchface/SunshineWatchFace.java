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

package com.stuff.kev.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private Typeface BASE_TYPEFACE = Typeface.SANS_SERIF;
    private Typeface NORMAL_TYPEFACE = Typeface.create(BASE_TYPEFACE, Typeface.NORMAL);
    private Typeface BOLD_TYPEFACE = Typeface.create(BASE_TYPEFACE, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    // see http://www.slideshare.net/rtc1/intro-todrawingtextandroid for the explanation of this awesome code snippet
    public enum TextVertAlign { Top, Middle, Baseline, Bottom } // Enumeration representing vertical alignment positions
    public static void drawHvAlignedText(Canvas canvas, float x, float y, String s, Paint p, Paint.Align horizAlign, TextVertAlign vertAlign ) {
        // Set horizontal alignment
        p.setTextAlign(horizAlign);

        // Get bounding rectangle which we’ll need below...
        Rect r = new Rect();
        p.getTextBounds(s, 0, s.length(), r);

        // Note: r.top will be negative
        // Compute y-coordinate we'll need for drawing text for specified vertical alignment
        float textX = x;
        float textY = y;
        switch (vertAlign) {
            case Top:
                textY = y - r.top; // Recall that r.top is negative
                break;
            case Middle:
                textY = y - r.top - r.height() / 2;
                break;
            case Baseline: // Default behavior - no changes to y-coordinate
                // break;
            case Bottom:
                textY = y - (r.height() + r.top);
                break;
        }

        canvas.drawText(s, textX, textY, p);
        // Now we can draw the text with the proper ( x, y ) coordinates
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final float mLeading = (float)1.8;

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mDatePaint;
        Paint mDateAmbientPaint;
        Paint mDividerPaint;
        Paint mDividerAmbientPaint;
        Paint mBackgroundPaint;
        Paint mBackgroundAmbientPaint;
        Paint mTextPaint;
        Paint mTextBoldPaint;
        Paint mHighTemperaturePaint;
        Paint mLowTemperaturePaint;

        Paint mHourPaint;
        Paint mMinutePaint;
        String mTimeSeparator;
        String mTemperatureFormat;
        String mDateFormat;
        String mHourFormat;
        String mMinuteFormat;

        //float mXOffset;
        float mYOffset;
        float mLineHeight;
        float mLineSpace;
        float mCharSpace;

        // dummy data
        float mHighTemperature = 31;
        float mLowTemperature = 20;

        //Paint mHandPaint;

        boolean mAmbient;

        Time mTime;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = SunshineWatchFace.this.getResources();

            // initialize date points
            mDatePaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text),
                    resources.getDimension(R.dimen.text_size_date), NORMAL_TYPEFACE);
            mDateAmbientPaint = createTextPaint(ContextCompat.getColor(getBaseContext(),
                    R.color.text_semitransparent), resources.getDimension(R.dimen.text_size_date),
                    NORMAL_TYPEFACE);

            // initialize horizontal divider paints
            mDividerPaint = new Paint();
            mDividerPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.text_semitransparent));

            //mDividerAmbientPaint = new Paint();
            //mDividerAmbientPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.divider_ambient));

            // initialize background paints
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.background));

            mBackgroundAmbientPaint = new Paint();
            mBackgroundAmbientPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.background_ambient));

            mTextPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text), NORMAL_TYPEFACE);
            mTextBoldPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text), BOLD_TYPEFACE);

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            //mLineHeight = resources.getDimension(R.dimen.line_height);
            mLineSpace = resources.getDimension(R.dimen.line_space);
            mCharSpace = resources.getDimension(R.dimen.char_space);

            //mHandPaint = new Paint();
            //mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            //mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            //mHandPaint.setAntiAlias(true);
            //mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            // initialize hour, minute paint
            mHourPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text),
                    resources.getDimension(R.dimen.text_size_time), BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text),
                    resources.getDimension(R.dimen.text_size_time), NORMAL_TYPEFACE);

            // initialize time separator
            mTimeSeparator = resources.getString(R.string.time_separator);

            // initialize high, low temperature paint
            mHighTemperaturePaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text),
                    resources.getDimension(R.dimen.text_size_temperature), NORMAL_TYPEFACE);
            mLowTemperaturePaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.text_semitransparent),
                    resources.getDimension(R.dimen.text_size_temperature), NORMAL_TYPEFACE);

            // initialize format strings
            mTemperatureFormat = resources.getString(R.string.format_temperature);

            mDateFormat = resources.getString(R.string.format_watchface_date);
            mHourFormat = resources.getString(R.string.format_hours);
            mMinuteFormat = resources.getString(R.string.format_minutes);



            mTime = new Time();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();

            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);

            return paint;
        }

        private Paint createTextPaint(int textColor, float textSize, Typeface typeface) {
            Paint paint = new Paint();

            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            paint.setTextSize(textSize);

            return paint;
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
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
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHourPaint.setAntiAlias(!inAmbientMode);
                    mMinutePaint.setAntiAlias(!inAmbientMode);
                    mDateAmbientPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            Paint backgroundPaint = mAmbient ? mBackgroundAmbientPaint : mBackgroundPaint;
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), backgroundPaint);

            // draw date
            String dateText = mTime.format(mDateFormat).toUpperCase();
            Paint datePaint = mAmbient ? mDateAmbientPaint : mDatePaint;
            Rect dateBounds = new Rect();

            datePaint.getTextBounds(dateText, 0, dateText.length(), dateBounds);
            drawHvAlignedText(canvas,
                    (bounds.width() - dateBounds.width()) / 2,
                    bounds.height() / 2 - mLeading * dateBounds.height(),
                    dateText,
                    datePaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

            // initialize separator
            Paint separatorPaint = mMinutePaint;
            Rect separatorBounds = new Rect();
            separatorPaint.getTextBounds(mTimeSeparator, 0, mTimeSeparator.length(), separatorBounds);

            // draw minutes
            String minuteText = mTime.format(mMinuteFormat);
            Paint minutePaint = mMinutePaint;
            Rect minuteBounds = new Rect();
            minutePaint.getTextBounds(minuteText, 0, minuteText.length(), minuteBounds);
            drawHvAlignedText(canvas,
                    (bounds.width() + separatorBounds.width()) / 2 ,
                    bounds.height() / 2 - dateBounds.height() - mLeading * minuteBounds.height(),
                    minuteText,
                    minutePaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

            // draw hour string
            String hourText = mTime.format(mHourFormat);
            Paint hourPaint = mHourPaint;
            Rect hourBounds = new Rect();
            hourPaint.getTextBounds(hourText, 0, hourText.length(), hourBounds);
            drawHvAlignedText(canvas,
                    (bounds.width() - separatorBounds.width()) / 2 - hourBounds.width(),
                    bounds.height() / 2 - dateBounds.height() - mLeading * minuteBounds.height(),
                    hourText,
                    hourPaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

            // draw time separator
            boolean drawSeaprator = mAmbient || (mTime.second % 2) == 0;
            if (drawSeaprator) {
                drawHvAlignedText(canvas,
                        (bounds.width() - separatorBounds.width()) / 2,
                        bounds.height() / 2 - dateBounds.height() - mLeading * minuteBounds.height() + minuteBounds.height() / 2,
                        // for some reason I cannot keep leading and trailing spaces added to the separator string in the strings.xml file;
                        // so I'm adding a magic character to replace it with a space character right before displaying the separator
                        mTimeSeparator.replace('|', ' '),
                        separatorPaint,
                        Paint.Align.LEFT,
                        TextVertAlign.Middle);
            }

            // draw a horizontal divider
            canvas.drawRect((bounds.width() * 3) / 8,
                    bounds.height() / 2 - 1,
                    (bounds.width() * 5) / 8,
                    bounds.height() / 2 + 1,
                    mDividerPaint);

            // draw high temperature
            String highTemperatureText =String.format(mTemperatureFormat, mHighTemperature);
            Paint highTemperaturePaint = mHighTemperaturePaint;
            Rect highTemperatureBounds = new Rect();
            highTemperaturePaint.getTextBounds(highTemperatureText, 0,
                    highTemperatureText.length(),
                    highTemperatureBounds);
            drawHvAlignedText(canvas,
                    (bounds.width() * 2 / 5 + (bounds.width() / 5 - highTemperatureBounds.width()) / 2),
                    bounds.height() / 2 + mLeading * dateBounds.height() - dateBounds.height(),
                    highTemperatureText,
                    highTemperaturePaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

            // draw low temperature
            String lowTemperatureText =String.format(mTemperatureFormat, mLowTemperature);
            Paint lowTemperaturePaint = mLowTemperaturePaint;
            Rect lowTemperatureBounds = new Rect();
            lowTemperaturePaint.getTextBounds(lowTemperatureText, 0,
                    lowTemperatureText.length(),
                    lowTemperatureBounds);
            drawHvAlignedText(canvas,
                    (bounds.width() * 3 / 5 + (bounds.width() / 5 - lowTemperatureBounds.width()) / 2),
                    bounds.height() / 2 + mLeading * dateBounds.height() - dateBounds.height(),
                    lowTemperatureText,
                    lowTemperaturePaint,
                    Paint.Align.LEFT,
                    TextVertAlign.Top);

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
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

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
