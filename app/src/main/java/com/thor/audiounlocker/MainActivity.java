package com.thor.audiounlocker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity {

    private Prefs prefs;
    private TextView statusView;
    private TextView gainValueView;
    private TextView gainRangeHint;
    private SeekBar gainSlider;
    private TextView accStatusView;
    private LinearLayout perAppListContainer;
    private TextView widthValueView;
    private SeekBar widthSlider;
    private final SeekBar[] eqSliders = new SeekBar[Prefs.EQ_CUTOFFS_HZ.length];
    private final TextView[] eqValueViews = new TextView[Prefs.EQ_CUTOFFS_HZ.length];

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
        updateAccessibilityStatus();
        rebuildPerAppList();
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
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // Header
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
        statusView.setPadding(0, dp(4), 0, dp(14));
        root.addView(statusView);

        // 1. Global Gain Card (0-10 dB safe, optional 20 dB danger toggle)
        LinearLayout gainCard = makeCard();
        root.addView(gainCard);

        LinearLayout gainHeader = new LinearLayout(this);
        gainHeader.setOrientation(LinearLayout.HORIZONTAL);
        gainCard.addView(gainHeader);

        TextView gainLabel = new TextView(this);
        gainLabel.setText("Global Speaker Gain");
        gainLabel.setTextColor(Color.WHITE);
        gainLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        gainLabel.setTypeface(null, Typeface.BOLD);
        gainLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        gainHeader.addView(gainLabel);

        gainValueView = new TextView(this);
        gainValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        gainValueView.setTypeface(null, Typeface.BOLD);
        gainHeader.addView(gainValueView);

        gainSlider = new SeekBar(this);
        gainSlider.setMax(prefs.getCurrentMaxDb());
        gainSlider.setProgress(prefs.getGainDb());
        gainSlider.setPadding(0, dp(12), 0, dp(8));
        updateGainReadout(prefs.getGainDb());
        gainSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateGainReadout(progress);
                if (fromUser) {
                    prefs.setGainDb(progress);
                    pushServiceUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        gainCard.addView(gainSlider);

        gainRangeHint = new TextView(this);
        gainRangeHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        updateGainRangeHint();
        gainCard.addView(gainRangeHint);

        addSwitchRow(
            gainCard,
            "Extra dB Mode (Up to +20 dB)",
            "DANGER: Above +10 dB causes heavy distortion and can damage your speakers.",
            Color.parseColor("#F87171"),
            prefs.isExtraGainUnlocked(),
            checked -> {
                prefs.setExtraGainUnlocked(checked);
                int newMax = prefs.getCurrentMaxDb();
                gainSlider.setMax(newMax);
                int clampedGain = prefs.getGainDb();
                gainSlider.setProgress(clampedGain);
                updateGainReadout(clampedGain);
                updateGainRangeHint();
                rebuildPerAppList();
                pushServiceUpdate();
            }
        );

        // 2. Per-App Gain Card
        LinearLayout perAppCard = makeCard();
        root.addView(perAppCard);

        TextView perAppTitle = new TextView(this);
        perAppTitle.setText("Per-App Gain (e.g. DroidDeck Only)");
        perAppTitle.setTextColor(Color.WHITE);
        perAppTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        perAppTitle.setTypeface(null, Typeface.BOLD);
        perAppTitle.setPadding(0, 0, 0, dp(4));
        perAppCard.addView(perAppTitle);

        addSwitchRow(
            perAppCard,
            "Per-App Boost Only",
            "ON = boosts ONLY apps listed below (0 dB standby for all other apps)",
            Color.parseColor("#94A3B8"),
            prefs.isPerAppMode(),
            checked -> {
                prefs.setPerAppMode(checked);
                pushServiceUpdate();
            }
        );

        accStatusView = new TextView(this);
        accStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        accStatusView.setPadding(0, dp(2), 0, dp(8));
        perAppCard.addView(accStatusView);
        updateAccessibilityStatus();

        LinearLayout perAppBtnRow = new LinearLayout(this);
        perAppBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        perAppCard.addView(perAppBtnRow);

        Button addAppBtn = new Button(this);
        addAppBtn.setText("+ Add App");
        addAppBtn.setAllCaps(false);
        addAppBtn.setTextColor(Color.parseColor("#0B1120"));
        addAppBtn.setTypeface(null, Typeface.BOLD);
        addAppBtn.setBackground(makeRoundedBg("#38BDF8", 10));
        LinearLayout.LayoutParams lpAdd = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lpAdd.rightMargin = dp(6);
        addAppBtn.setLayoutParams(lpAdd);
        addAppBtn.setOnClickListener(v -> showAppPickerDialog());
        perAppBtnRow.addView(addAppBtn);

        Button accBtn = new Button(this);
        accBtn.setText("Accessibility Settings");
        accBtn.setAllCaps(false);
        accBtn.setTextColor(Color.WHITE);
        accBtn.setBackground(makeRoundedBg("#1E293B", 10));
        LinearLayout.LayoutParams lpAcc = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lpAcc.leftMargin = dp(6);
        accBtn.setLayoutParams(lpAcc);
        accBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        perAppBtnRow.addView(accBtn);

        perAppListContainer = new LinearLayout(this);
        perAppListContainer.setOrientation(LinearLayout.VERTICAL);
        perAppListContainer.setPadding(0, dp(10), 0, 0);
        perAppCard.addView(perAppListContainer);
        rebuildPerAppList();

        // 3. Stereo Stage Widener & 5-Band EQ Card
        LinearLayout eqCard = makeCard();
        root.addView(eqCard);

        TextView eqTitle = new TextView(this);
        eqTitle.setText("Stereo Stage & 5-Band Equalizer");
        eqTitle.setTextColor(Color.WHITE);
        eqTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        eqTitle.setTypeface(null, Typeface.BOLD);
        eqTitle.setPadding(0, 0, 0, dp(8));
        eqCard.addView(eqTitle);

        addSwitchRow(
            eqCard,
            "Stereo Stage Widener",
            "Combines Virtualizer + L/R complementary EQ tilt for a wider soundstage",
            Color.parseColor("#94A3B8"),
            prefs.isStereoWideEnabled(),
            checked -> {
                prefs.setStereoWideEnabled(checked);
                pushServiceUpdate();
            }
        );

        LinearLayout widthHeader = new LinearLayout(this);
        widthHeader.setOrientation(LinearLayout.HORIZONTAL);
        widthHeader.setPadding(0, dp(6), 0, 0);
        eqCard.addView(widthHeader);

        TextView widthLabel = new TextView(this);
        widthLabel.setText("Stage Width");
        widthLabel.setTextColor(Color.parseColor("#E2E8F0"));
        widthLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        widthLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        widthHeader.addView(widthLabel);

        widthValueView = new TextView(this);
        widthValueView.setText(prefs.getStageWidthPercent() + "%");
        widthValueView.setTextColor(Color.parseColor("#38BDF8"));
        widthValueView.setTypeface(null, Typeface.BOLD);
        widthHeader.addView(widthValueView);

        widthSlider = new SeekBar(this);
        widthSlider.setMax(100);
        widthSlider.setProgress(prefs.getStageWidthPercent());
        widthSlider.setPadding(0, dp(6), 0, dp(10));
        widthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                widthValueView.setText(progress + "%");
                if (fromUser) {
                    prefs.setStageWidthPercent(progress);
                    pushServiceUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        eqCard.addView(widthSlider);

        addSwitchRow(
            eqCard,
            "5-Band Equalizer",
            "Shape bass, vocals, presence, and air (-12 to +12 dB)",
            Color.parseColor("#94A3B8"),
            prefs.isEqEnabled(),
            checked -> {
                prefs.setEqEnabled(checked);
                pushServiceUpdate();
            }
        );

        LinearLayout eqPresets = new LinearLayout(this);
        eqPresets.setOrientation(LinearLayout.HORIZONTAL);
        eqPresets.setPadding(0, dp(6), 0, dp(10));
        eqCard.addView(eqPresets);

        addPresetBtn(eqPresets, "Wide Stage", new int[]{-2, 1, 2, 3, 3}, 75);
        addPresetBtn(eqPresets, "Vocal Clear", new int[]{-3, 0, 4, 3, 1}, 50);
        addPresetBtn(eqPresets, "Flat", new int[]{0, 0, 0, 0, 0}, 0);

        for (int i = 0; i < Prefs.EQ_CUTOFFS_HZ.length; i++) {
            final int bandIndex = i;
            int currentDb = prefs.getEqBandDb(i);

            LinearLayout bandHeader = new LinearLayout(this);
            bandHeader.setOrientation(LinearLayout.HORIZONTAL);
            eqCard.addView(bandHeader);

            TextView bandLabel = new TextView(this);
            bandLabel.setText(Prefs.EQ_LABELS[i]);
            bandLabel.setTextColor(Color.parseColor("#CBD5E1"));
            bandLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bandLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            bandHeader.addView(bandLabel);

            TextView bandVal = new TextView(this);
            bandVal.setText(formatSignedDb(currentDb));
            bandVal.setTextColor(Color.parseColor("#38BDF8"));
            bandVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bandVal.setTypeface(null, Typeface.BOLD);
            bandHeader.addView(bandVal);
            eqValueViews[i] = bandVal;

            SeekBar sb = new SeekBar(this);
            sb.setMax(Prefs.EQ_MAX_DB - Prefs.EQ_MIN_DB);
            sb.setProgress(currentDb - Prefs.EQ_MIN_DB);
            sb.setPadding(0, dp(4), 0, dp(8));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int db = progress + Prefs.EQ_MIN_DB;
                    bandVal.setText(formatSignedDb(db));
                    if (fromUser) {
                        prefs.setEqBandDb(bandIndex, db);
                        pushServiceUpdate();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            eqCard.addView(sb);
            eqSliders[i] = sb;
        }

        // 4. Service & Protection Card
        LinearLayout settingsCard = makeCard();
        root.addView(settingsCard);

        addSwitchRow(settingsCard, "Enable Boost Service", "Runs in native audio server with minimal RAM",
            Color.parseColor("#94A3B8"), prefs.isEnabled(), checked -> {
                prefs.setEnabled(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Output Limiter (-0.5 dB)", "Keeps loudness clean and prevents harsh digital clipping",
            Color.parseColor("#94A3B8"), prefs.isLimiterEnabled(), checked -> {
                prefs.setLimiterEnabled(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Auto-Bypass Headphones / BT", "Pauses boost when external audio is connected",
            Color.parseColor("#94A3B8"), prefs.isSpeakerOnly(), checked -> {
                prefs.setSpeakerOnly(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Auto-Start on Boot", "Starts automatically when your AYN Thor boots",
            Color.parseColor("#94A3B8"), prefs.isStartOnBoot(), prefs::setStartOnBoot);

        Button closeBtn = new Button(this);
        closeBtn.setText("Apply & Close UI (Keep Service Running)");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(Color.parseColor("#0B1120"));
        closeBtn.setTypeface(null, Typeface.BOLD);
        closeBtn.setBackground(makeRoundedBg("#38BDF8", 12));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        );
        closeLp.topMargin = dp(6);
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> {
            pushServiceUpdate();
            finishAndRemoveTask();
        });
        root.addView(closeBtn);

        return scroll;
    }

    private void updateAccessibilityStatus() {
        if (accStatusView == null) return;
        if (AppSwitchAccessibilityService.isConnected()) {
            accStatusView.setText("Per-App Switcher: Connected (0% CPU during gameplay)");
            accStatusView.setTextColor(Color.parseColor("#4ADE80"));
        } else {
            accStatusView.setText("Per-App Switcher: Not enabled — tap Accessibility Settings and turn on 'Thor Audio Per-App Switcher'");
            accStatusView.setTextColor(Color.parseColor("#FBBF24"));
        }
    }

    private void rebuildPerAppList() {
        if (perAppListContainer == null) return;
        perAppListContainer.removeAllViews();

        List<String> pkgs = prefs.getPerAppPackages();
        if (pkgs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No apps added yet. Tap '+ Add App' to select DroidDeck or any game.");
            empty.setTextColor(Color.parseColor("#64748B"));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            perAppListContainer.addView(empty);
            return;
        }

        int maxDb = prefs.getCurrentMaxDb();
        for (String pkg : pkgs) {
            String label = prefs.getPerAppLabel(pkg);
            int appDb = Math.max(0, prefs.getPerAppGainDb(pkg));

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(makeRoundedBg("#1E293B", 12));
            int p = dp(12);
            box.setPadding(p, p, p, p);
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            boxLp.bottomMargin = dp(8);
            box.setLayoutParams(boxLp);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            box.addView(topRow);

            TextView nameView = new TextView(this);
            nameView.setText(label);
            nameView.setTextColor(Color.WHITE);
            nameView.setTypeface(null, Typeface.BOLD);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            topRow.addView(nameView);

            TextView dbView = new TextView(this);
            dbView.setText("+" + appDb + " dB");
            dbView.setTextColor(appDb > Prefs.SAFE_MAX_DB ? Color.parseColor("#EF4444") : Color.parseColor("#38BDF8"));
            dbView.setTypeface(null, Typeface.BOLD);
            dbView.setPadding(0, 0, dp(10), 0);
            topRow.addView(dbView);

            Button removeBtn = new Button(this);
            removeBtn.setText("Remove");
            removeBtn.setAllCaps(false);
            removeBtn.setTextColor(Color.parseColor("#F87171"));
            removeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            removeBtn.setBackground(makeRoundedBg("#0F172A", 8));
            removeBtn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)));
            removeBtn.setOnClickListener(v -> {
                prefs.removePerApp(pkg);
                rebuildPerAppList();
                pushServiceUpdate();
            });
            topRow.addView(removeBtn);

            SeekBar sb = new SeekBar(this);
            sb.setMax(maxDb);
            sb.setProgress(Math.min(maxDb, appDb));
            sb.setPadding(0, dp(6), 0, 0);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    dbView.setText("+" + progress + " dB");
                    dbView.setTextColor(progress > Prefs.SAFE_MAX_DB ? Color.parseColor("#EF4444") : Color.parseColor("#38BDF8"));
                    if (fromUser) {
                        prefs.setPerAppGain(pkg, label, progress);
                        pushServiceUpdate();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            box.addView(sb);
            perAppListContainer.addView(box);
        }
    }

    private void showAppPickerDialog() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);

        List<String[]> apps = new ArrayList<>();
        String myPkg = getPackageName();
        for (ResolveInfo ri : resolved) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(myPkg)) continue;
            CharSequence labelSeq = ri.loadLabel(pm);
            String label = (labelSeq != null && labelSeq.length() > 0) ? labelSeq.toString() : pkg;
            apps.add(new String[]{label, pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));

        String[] items = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            items[i] = apps.get(i)[0] + " (" + apps.get(i)[1] + ")";
        }

        new AlertDialog.Builder(this)
            .setTitle("Select App to Boost")
            .setItems(items, (dialog, which) -> {
                String label = apps.get(which)[0];
                String pkg = apps.get(which)[1];
                prefs.setPerAppGain(pkg, label, Math.min(prefs.getCurrentMaxDb(), 8));
                prefs.setPerAppMode(true);
                rebuildPerAppList();
                pushServiceUpdate();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void addPresetBtn(LinearLayout row, String label, int[] bands, int widthPct) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setBackground(makeRoundedBg("#1E293B", 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            for (int i = 0; i < bands.length; i++) {
                prefs.setEqBandDb(i, bands[i]);
                if (eqSliders[i] != null) eqSliders[i].setProgress(bands[i] - Prefs.EQ_MIN_DB);
                if (eqValueViews[i] != null) eqValueViews[i].setText(formatSignedDb(bands[i]));
            }
            prefs.setStageWidthPercent(widthPct);
            if (widthSlider != null) widthSlider.setProgress(widthPct);
            if (widthValueView != null) widthValueView.setText(widthPct + "%");
            pushServiceUpdate();
        });
        row.addView(b);
    }

    private void updateGainReadout(int db) {
        gainValueView.setText("+" + db + " dB");
        gainValueView.setTextColor(db > Prefs.SAFE_MAX_DB
            ? Color.parseColor("#EF4444")
            : Color.parseColor("#38BDF8"));
    }

    private void updateGainRangeHint() {
        if (prefs.isExtraGainUnlocked()) {
            gainRangeHint.setText("Range: 0 to +20 dB (DANGER ZONE UNLOCKED above +10 dB)");
            gainRangeHint.setTextColor(Color.parseColor("#F87171"));
        } else {
            gainRangeHint.setText("Safe Range: 0 to +10 dB (Recommended: +6 to +9 dB)");
            gainRangeHint.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    private String formatSignedDb(int db) {
        return (db > 0 ? "+" : "") + db + " dB";
    }

    private interface ToggleListener {
        void onToggle(boolean checked);
    }

    private void addSwitchRow(
        LinearLayout parent,
        String title,
        String subtitle,
        int subtitleColor,
        boolean initial,
        ToggleListener cb
    ) {
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
        s.setTextColor(subtitleColor);
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
