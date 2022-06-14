package it.necst.android.reservator.view;

import java.util.Calendar;

import it.necst.android.reservator.R;
import it.necst.android.reservator.model.DateTime;
import it.necst.android.reservator.model.TimeSpan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;

public class TimeBarView extends FrameLayout {
    private static final int MIN_SPAN_LENGTH = 60 * 120 * 1000;
    int animStep = 60000;
    boolean animationEnabled = false;
    Thread animatorThread = null;
    TimeSpan limits, span;
    Drawable background;
    Drawable reservationOwn;
    Drawable reservationOther;
    int tickColor;
    private long startDelta = 0, endDelta = 0;
    private TimeSpan targetTimeSpan = null;

    @BindView(R.id.textView1)
    TextView durationLabel;

    public TimeBarView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public TimeBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        inflate(context, R.layout.time_bar, this);
        ButterKnife.bind(this);
        this.setTimeLimits(new TimeSpan(null, Calendar.HOUR, 2));
        this.setSpan(new TimeSpan(null, Calendar.MINUTE, 90));

        background = getResources().getDrawable(R.drawable.timeline);
        reservationOwn = getResources().getDrawable(R.drawable.oma_varaus);
        reservationOther = getResources().getDrawable(R.drawable.muu_varaus);
        tickColor = getResources().getColor(R.color.TimeBarTickColor);

    }

    public void setTimeLimits(TimeSpan span) {
        this.limits = span.clone();
        invalidate();
    }

    public void enableAnimation() {
        animationEnabled = true;
    }

    public void disableAnimation() {
        animationEnabled = false;
    }

    public void setSpan(TimeSpan span) {
        if (this.span == null || !animationEnabled) {
            this.span = span;
            this.targetTimeSpan = span;
            return;
        }
        targetTimeSpan = span;
        startDelta = span.getStart().getTimeInMillis() - this.span.getStart().getTimeInMillis();
        endDelta = span.getEnd().getTimeInMillis() - this.span.getEnd().getTimeInMillis();
        animStep = (int) Math.max(Math.max(Math.abs(endDelta), Math.abs(startDelta)) / 10, 60000);


        if (animatorThread == null) {
            animatorThread = new Thread() {
                public void run() {
                    while (Math.abs(startDelta) > animStep || Math.abs(endDelta) > animStep) {
                        TimeBarView.this.span = new TimeSpan(
                            TimeBarView.this.span.getStart().add(Calendar.MILLISECOND, (int) Math.signum(startDelta) * animStep),
                            TimeBarView.this.span.getEnd().add(Calendar.MILLISECOND, (int) Math.signum(endDelta) * animStep));

                        startDelta -= Math.signum(startDelta) * animStep;
                        endDelta -= Math.signum(endDelta) * animStep;
                        postInvalidate();
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    TimeBarView.this.span = targetTimeSpan;
                    postInvalidate();
                    animatorThread = null;
                }
            };
            animatorThread.start();
        }
        invalidate();
    }

    @Override
    public void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);


        int startCenterX = getWidth() / 4;
        int endCenterX = getWidth() / 4 * 3;
        int w = 10;
        int left = 0;
        int bottom = durationLabel.getTop();
        int right = getWidth() - 1;
        int y = 0;

        final int padding = durationLabel.getTop() / 6;

        Paint p = new Paint();
        p.setColor(getResources().getColor(R.color.ReserveLine));

        // The horizontal lines
        c.drawLine(startCenterX - w, y, startCenterX + w, y, p);
        c.drawLine(endCenterX - w, y, endCenterX + w, y, p);

        // ... and vertical
        c.drawLine(startCenterX, y, startCenterX, y + padding + 1, p);
        c.drawLine(endCenterX, y, endCenterX, y + padding * 2 + 1, p); // 2* to shift the other line to bit lower
        y += padding;

        int width = getWidth() - 1;
        int startX = (int) (width * getProportional(span.getStart()));
        int endX = (int) (width * getProportional(span.getEnd()));

        // Dynamic horizontal lines
        c.drawLine(startCenterX, y, startX, y, p);
        c.drawLine(endCenterX, y + padding, endX, y + padding, p);

        // ... and vertical
        c.drawLine(startX, y, startX, bottom, p);
        c.drawLine(endX, y + padding, endX, bottom, p);

        y += 2 * padding;

        // Background
        background.setBounds(left, y, right, bottom);
        background.draw(c);

        // Reservation
        reservationOwn.setBounds(startX, y, endX, bottom);
        reservationOwn.draw(c);

        // Other reservation
        if (span.getLength() < MIN_SPAN_LENGTH) {
            reservationOther.setBounds((int) (width * getProportional(limits.getEnd())), y, width, bottom);
            reservationOther.draw(c);
        }

        // Ticks
        p.setStyle(Style.STROKE);
        p.setColor(tickColor);
        DateTime time = limits.getStart();
        DateTime end = getMaximum();

        // round time to the half an hour, and skip the first one
        time = time.set(Calendar.MINUTE, (time.get(Calendar.MINUTE) / 30) * 30);
        time = time.add(Calendar.MINUTE, 30);

        while (time.before(end)) {
            int x = (int) (width * getProportional(time));
            c.drawLine(x, y, x, bottom, p);

            time = time.add(Calendar.MINUTE, 30);
        }

        // Duration label
        int minutes = (int) (span.getLength() / 60000);
        int hours = minutes / 60;
        minutes = minutes % 60;

        String duration;
        if (hours > 0 && minutes == 0) {
            duration = hours + (hours == 1 ? " hour" : " hours");
        } else if (hours > 0) {
            duration = hours + (hours == 1 ? " hour " : " hours ") + minutes + " minutes";
        } else {
            duration = minutes + " minutes";
        }
        durationLabel.setText(duration);
    }

    private DateTime getMaximum() {
        return limits.getStart().add(Calendar.MILLISECOND, (int) (limits.getLength() > MIN_SPAN_LENGTH ? limits.getLength() : MIN_SPAN_LENGTH));
    }

    private float getProportional(DateTime time) {
        return (time.getTimeInMillis() - limits.getStart().getTimeInMillis()) / (float) (limits.getLength() > MIN_SPAN_LENGTH ? limits.getLength() : MIN_SPAN_LENGTH);
    }
}
