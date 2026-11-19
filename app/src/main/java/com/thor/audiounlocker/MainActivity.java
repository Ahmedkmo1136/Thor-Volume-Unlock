package com.thor.audiounlocker;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {

    private Prefs prefs;
    private TextView statusView;
    private TextView gainValueView;
    private SeekBar gainSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }

        if (prefs.isEnabled()) {
            AudioBoostService.startOrUpdate(this);
        }

        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusView != null) {
            statusView.setText(AudioBoostService.getLastStatus());
        }
        AudioBoostService.setStatusCallback(text -> runOnUiThread(() -> {
            if (statusView != null) statusView.setText(text);
        }));
    }

    @Override
    protected void onPause() {
        AudioBoostService.setStatusCallback(null);
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0B1120"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // Title
        TextView title = new TextView(this);
        title.setText("Thor Audio Unlocker");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText(AudioBoostService.getLastStatus());
        statusView.setTextColor(Color.parseColor("#38BDF8"));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusView.setPadding(0, dp(4), 0, dp(16));
        root.addView(statusView);

        // Gain card
        LinearLayout gainCard = makeCard();
        root.addView(gainCard);

        LinearLayout gainHeader = new LinearLayout(this);
        gainHeader.setOrientation(LinearLayout.HORIZONTAL);
        gainCard.addView(gainHeader);

        TextView gainLabel = new TextView(this);
        gainLabel.setText("Speaker Boost Gain");
        gainLabel.setTextColor(Color.WHITE);
        gainLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        gainLabel.setTypeface(null, Typeface.BOLD);
        gainLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        gainHeader.addView(gainLabel);

        gainValueView = new TextView(this);
        gainValueView.setText("+" + prefs.getGainDb() + " dB");
        gainValueView.setTextColor(Color.parseColor("#F97316"));
        gainValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        gainValueView.setTypeface(null, Typeface.BOLD);
        gainHeader.addView(gainValueView);

        gainSlider = new SeekBar(this);
        gainSlider.setMax(Prefs.MAX_GAIN_DB);
        gainSlider.setProgress(prefs.getGainDb());
        gainSlider.setPadding(0, dp(14), 0, dp(14));
        gainSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gainValueView.setText("+" + progress + " dB");
                if (fromUser) {
                    prefs.setGainDb(progress);
                    pushServiceUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        gainCard.addView(gainSlider);

        // Quick jump buttons (+10, +20, +30, +40 dB)
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        gainCard.addView(presetRow);

        int[] presets = {10, 20, 30, 40};
        for (int db : presets) {
            Button b = new Button(this);
            b.setText("+" + db);
            b.setAllCaps(false);
            b.setTextColor(Color.WHITE);
            b.setBackground(makeRoundedBg("#1E293B", 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                prefs.setGainDb(db);
                gainSlider.setProgress(db);
                gainValueView.setText("+" + db + " dB");
                pushServiceUpdate();
            });
            presetRow.addView(b);
        }

        // Settings card
        LinearLayout settingsCard = makeCard();
        root.addView(settingsCard);

        addSwitchRow(settingsCard, "Enable Boost Service", "Runs in native audio server with minimal RAM",
            prefs.isEnabled(), checked -> {
                prefs.setEnabled(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Overdrive Stack (DP + Loudness)", "Stacks two audio effects to bypass OEM dB caps",
            prefs.isOverdriveStack(), checked -> {
                prefs.setOverdriveStack(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Output Limiter (-0.5 dB)", "OFF = raw max loudness, ON = reduce clipping",
            prefs.isLimiterEnabled(), checked -> {
                prefs.setLimiterEnabled(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Auto-Bypass Headphones / BT", "Pauses boost when external audio is connected",
            prefs.isSpeakerOnly(), checked -> {
                prefs.setSpeakerOnly(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Auto-Start on Boot", "Starts automatically when your AYN Thor boots",
            prefs.isStartOnBoot(), prefs::setStartOnBoot);

        // Close UI button (keeps background service running with zero UI memory)
        Button closeBtn = new Button(this);
        closeBtn.setText("Apply & Close UI (Keep Service Running)");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(Color.parseColor("#0B1120"));
        closeBtn.setTypeface(null, Typeface.BOLD);
        closeBtn.setBackground(makeRoundedBg("#38BDF8", 12));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        );
        closeLp.topMargin = dp(8);
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> {
            pushServiceUpdate();
            finishAndRemoveTask();
        });
        root.addView(closeBtn);

        return scroll;
    }

    private interface ToggleListener {
        void onToggle(boolean checked);
    }

    private void addSwitchRow(LinearLayout parent, String title, String subtitle, boolean initial, ToggleListener cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(null, Typeface.BOLD);
        textCol.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(Color.parseColor("#94A3B8"));
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textCol.addView(s);

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> cb.onToggle(isChecked));

        row.addView(textCol);
        row.addView(sw);
        parent.addView(row);
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeRoundedBg("#141E33", 16));
        int p = dp(16);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private GradientDrawable makeRoundedBg(String hex, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(hex));
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void pushServiceUpdate() {
        if (prefs.isEnabled()) {
            AudioBoostService.startOrUpdate(this);
        } else {
            AudioBoostService.stopServiceNow(this);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
