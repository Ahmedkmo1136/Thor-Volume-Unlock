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
import java.util.Locale;

public final class MainActivity extends Activity {

    private Prefs prefs;
    private TextView statusView;
    private TextView gainValueView;
    private TextView gainRangeHint;
    private SeekBar gainSlider;
    private TextView accBannerView;
    private Button addAppBtn;
    private LinearLayout perAppListContainer;

    // Independent DSP UI references (updated whenever a preset template is loaded)
    private Switch stereoWideSwitch;
    private TextView widthValueView;
    private SeekBar widthSlider;
    private Switch limiterSwitch;
    private TextView limThreshValueView;
    private SeekBar limThreshSlider;
    private TextView limReleaseValueView;
    private SeekBar limReleaseSlider;
    private Switch eqSwitch;
    private TextView makeupValueView;
    private SeekBar makeupSlider;
    private LinearLayout eqBandsContainer;
    private Button toggleBandsBtn;
    private final SeekBar[] eqSliders = new SeekBar[Prefs.EQ_FREQS_HZ.length];
    private final TextView[] eqValueViews = new TextView[Prefs.EQ_FREQS_HZ.length];
    private boolean bandsVisible = false;
    private boolean syncingUi = false;

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
        updateAccessibilityUi();
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

        // 1. Global Gain Card
        LinearLayout gainCard = makeCard();
        root.addView(gainCard);

        LinearLayout gainHeader = new LinearLayout(this);
        gainHeader.setOrientation(LinearLayout.HORIZONTAL);
        gainCard.addView(gainHeader);

        TextView gainLabel = new TextView(this);
        gainLabel.setText("Global dB (Default for All Apps)");
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

        // 2. Per-App Overrides Card
        LinearLayout perAppCard = makeCard();
        root.addView(perAppCard);

        TextView perAppTitle = new TextView(this);
        perAppTitle.setText("Per-App dB Overrides");
        perAppTitle.setTextColor(Color.WHITE);
        perAppTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        perAppTitle.setTypeface(null, Typeface.BOLD);
        perAppTitle.setPadding(0, 0, 0, dp(4));
        perAppCard.addView(perAppTitle);

        addSwitchRow(
            perAppCard,
            "Enable Per-App Overrides",
            "Listed apps use their own dB below; all other apps use Global dB above (no conflict).",
            Color.parseColor("#94A3B8"),
            prefs.isPerAppOverridesEnabled(),
            checked -> {
                prefs.setPerAppOverridesEnabled(checked);
                pushServiceUpdate();
            }
        );

        accBannerView = new TextView(this);
        accBannerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        accBannerView.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bannerLp.topMargin = dp(4);
        bannerLp.bottomMargin = dp(10);
        accBannerView.setLayoutParams(bannerLp);
        perAppCard.addView(accBannerView);

        LinearLayout perAppBtnRow = new LinearLayout(this);
        perAppBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        perAppCard.addView(perAppBtnRow);

        Button accBtn = new Button(this);
        accBtn.setText("1. Enable Accessibility");
        accBtn.setAllCaps(false);
        accBtn.setTextColor(Color.WHITE);
        accBtn.setBackground(makeRoundedBg("#1E293B", 10));
        LinearLayout.LayoutParams lpAcc = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lpAcc.rightMargin = dp(6);
        accBtn.setLayoutParams(lpAcc);
        accBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        perAppBtnRow.addView(accBtn);

        addAppBtn = new Button(this);
        addAppBtn.setText("2. + Add App");
        addAppBtn.setAllCaps(false);
        addAppBtn.setTextColor(Color.parseColor("#0B1120"));
        addAppBtn.setTypeface(null, Typeface.BOLD);
        addAppBtn.setBackground(makeRoundedBg("#38BDF8", 10));
        LinearLayout.LayoutParams lpAdd = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lpAdd.leftMargin = dp(6);
        addAppBtn.setLayoutParams(lpAdd);
        addAppBtn.setOnClickListener(v -> {
            if (!AppSwitchAccessibilityService.isConnected()) {
                new AlertDialog.Builder(this)
                    .setTitle("Enable Accessibility First")
                    .setMessage("To use per-app dB overrides, turn on 'Thor Audio Per-App Switcher' in Android Accessibility Settings first.\n\nIt only listens for app switches (0% CPU during gameplay).")
                    .setPositiveButton("Open Accessibility Settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
            } else {
                showAppPickerDialog();
            }
        });
        perAppBtnRow.addView(addAppBtn);

        perAppListContainer = new LinearLayout(this);
        perAppListContainer.setOrientation(LinearLayout.VERTICAL);
        perAppListContainer.setPadding(0, dp(10), 0, 0);
        perAppCard.addView(perAppListContainer);
        updateAccessibilityUi();
        rebuildPerAppList();

        // 3. Quick Preset Templates Card (populates independent EQ, Stereo, and Limiter controls)
        LinearLayout presetCard = makeCard();
        root.addView(presetCard);

        TextView presetTitle = new TextView(this);
        presetTitle.setText("Quick Profiles (Templates)");
        presetTitle.setTextColor(Color.WHITE);
        presetTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        presetTitle.setTypeface(null, Typeface.BOLD);
        presetCard.addView(presetTitle);

        TextView presetSub = new TextView(this);
        presetSub.setText("Tap a profile to load its EQ, stereo width, and limiter values. Every control below stays fully customizable.");
        presetSub.setTextColor(Color.parseColor("#94A3B8"));
        presetSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        presetSub.setPadding(0, dp(4), 0, dp(10));
        presetCard.addView(presetSub);

        Button joeyBtn = new Button(this);
        joeyBtn.setText("Load Joey's Retro Handhelds Profile (19-Band + 40% Stereo)");
        joeyBtn.setAllCaps(false);
        joeyBtn.setTextColor(Color.parseColor("#0B1120"));
        joeyBtn.setTypeface(null, Typeface.BOLD);
        joeyBtn.setBackground(makeRoundedBg("#38BDF8", 10));
        LinearLayout.LayoutParams joeyLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
        );
        joeyLp.bottomMargin = dp(8);
        joeyBtn.setLayoutParams(joeyLp);
        joeyBtn.setOnClickListener(v -> loadPresetAndRefresh(
            Prefs.PRESET_JOEY_EQ, 11, true, 40, true, -0.1f, 500
        ));
        presetCard.addView(joeyBtn);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetCard.addView(presetRow);

        addPresetBtn(presetRow, "Wide Stage", Prefs.PRESET_WIDE_EQ, 0, true, 70, true, -0.5f, 60);
        addPresetBtn(presetRow, "Vocal Clear", Prefs.PRESET_VOCAL_EQ, 0, true, 45, true, -0.5f, 60);
        addPresetBtn(presetRow, "Flat", Prefs.PRESET_FLAT_EQ, 0, false, 0, true, -0.5f, 60);

        // 4. Independent Stereo Stage Card
        LinearLayout stereoCard = makeCard();
        root.addView(stereoCard);

        TextView stereoTitle = new TextView(this);
        stereoTitle.setText("Stereo Stage Widener");
        stereoTitle.setTextColor(Color.WHITE);
        stereoTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        stereoTitle.setTypeface(null, Typeface.BOLD);
        stereoCard.addView(stereoTitle);

        stereoWideSwitch = addSwitchRow(
            stereoCard,
            "Enable Stereo Widener",
            "Combines Virtualizer + L/R complementary EQ tilt for wider soundstage",
            Color.parseColor("#94A3B8"),
            prefs.isStereoWideEnabled(),
            checked -> {
                if (syncingUi) return;
                prefs.setStereoWideEnabled(checked);
                pushServiceUpdate();
            }
        );

        LinearLayout widthHeader = new LinearLayout(this);
        widthHeader.setOrientation(LinearLayout.HORIZONTAL);
        widthHeader.setPadding(0, dp(6), 0, 0);
        stereoCard.addView(widthHeader);

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
        widthSlider.setPadding(0, dp(6), 0, dp(4));
        widthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                widthValueView.setText(progress + "%");
                if (fromUser && !syncingUi) {
                    prefs.setStageWidthPercent(progress);
                    pushServiceUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        stereoCard.addView(widthSlider);

        // 5. Independent Output Limiter Card
        LinearLayout limiterCard = makeCard();
        root.addView(limiterCard);

        TextView limiterTitle = new TextView(this);
        limiterTitle.setText("Output Limiter");
        limiterTitle.setTextColor(Color.WHITE);
        limiterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        limiterTitle.setTypeface(null, Typeface.BOLD);
        limiterCard.addView(limiterTitle);

        limiterSwitch = addSwitchRow(
            limiterCard,
            "Enable Output Limiter",
            "Prevents harsh digital clipping independently of any profile",
            Color.parseColor("#94A3B8"),
            prefs.isLimiterEnabled(),
            checked -> {
                if (syncingUi) return;
                prefs.setLimiterEnabled(checked);
                pushServiceUpdate();
            }
        );

        // Limiter Threshold (-6.0 to 0.0 dB)
        LinearLayout threshHeader = new LinearLayout(this);
        threshHeader.setOrientation(LinearLayout.HORIZONTAL);
        threshHeader.setPadding(0, dp(6), 0, 0);
        limiterCard.addView(threshHeader);

        TextView threshLabel = new TextView(this);
        threshLabel.setText("Limiter Threshold");
        threshLabel.setTextColor(Color.parseColor("#E2E8F0"));
        threshLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        threshLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        threshHeader.addView(threshLabel);

        limThreshValueView = new TextView(this);
        limThreshValueView.setText(String.format(Locale.US, "%.1f dB", prefs.getLimiterThresholdDb()));
        limThreshValueView.setTextColor(Color.parseColor("#38BDF8"));
        limThreshValueView.setTypeface(null, Typeface.BOLD);
        threshHeader.addView(limThreshValueView);

        limThreshSlider = new SeekBar(this);
        limThreshSlider.setMax(60); // 0..60 maps to -6.0..0.0 dB
        limThreshSlider.setProgress(Math.round((prefs.getLimiterThresholdDb() + 6.0f) * 10f));
        limThreshSlider.setPadding(0, dp(4), 0, dp(8));
        limThreshSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float db = -6.0f + (progress * 0.1f);
                limThreshValueView.setText(String.format(Locale.US, "%.1f dB", db));
                if (fromUser && !syncingUi) {
                    prefs.setLimiterThresholdDb(db);
                    pushServiceUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        limiterCard.addView(limThreshSlider);

        // Limiter Release (10 ms to 500 ms)
        LinearLayout relHeader = new LinearLayout(this);
        relHeader.setOrientation(LinearLayout.HORIZONTAL);
        limiterCard.addView(relHeader);

        TextView relLabel = new TextView(this);
        relLabel.setText("Limiter Release Time");
        relLabel.setTextColor(Color.parseColor("#E2E8F0"));
        relLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        relLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        relHeader.addView(relLabel);

        limReleaseValueView = new TextView(this);
        limReleaseValueView.setText(prefs.getLimiterReleaseMs() + " ms");
        limReleaseValueView.setTextColor(Color.parseColor("#38BDF8"));
        limReleaseValueView.setTypeface(null, Typeface.BOLD);
        relHeader.addView(limReleaseValueView);

        limReleaseSlider = new SeekBar(this);
        limReleaseSlider.setMax(49); // 0..49 maps to 10..500 ms in 10 ms steps
        limReleaseSlider.setProgress((prefs.getLimiterReleaseMs() - 10) / 10);
        limReleaseSlider.setPadding(0, dp(4), 0, dp(4));
        limReleaseSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int ms = 10 + (progress * 10);
                limReleaseValueView.setText(ms + " ms");
                if (fromUser && !syncingUi) {
                    prefs.setLimiterReleaseMs(ms);
                    pushServiceUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        limiterCard.addView(limReleaseSlider);

        // 6. Independent 19-Band Equalizer Card
        LinearLayout eqCard = makeCard();
        root.addView(eqCard);

        TextView eqTitle = new TextView(this);
        eqTitle.setText("19-Band Equalizer");
        eqTitle.setTextColor(Color.WHITE);
        eqTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        eqTitle.setTypeface(null, Typeface.BOLD);
        eqCard.addView(eqTitle);

        eqSwitch = addSwitchRow(
            eqCard,
            "Enable 19-Band Equalizer",
            "All 19 frequency bands (-32.0 to +12.0 dB) are independently editable",
            Color.parseColor("#94A3B8"),
            prefs.isEqEnabled(),
            checked -> {
                if (syncingUi) return;
                prefs.setEqEnabled(checked);
                pushServiceUpdate();
            }
        );

        // EQ Makeup Gain (0 to +15 dB)
        LinearLayout makeupHeader = new LinearLayout(this);
        makeupHeader.setOrientation(LinearLayout.HORIZONTAL);
        makeupHeader.setPadding(0, dp(6), 0, 0);
        eqCard.addView(makeupHeader);

        TextView makeupLabel = new TextView(this);
        makeupLabel.setText("EQ Makeup Gain (Compensates Heavy Cuts)");
        makeupLabel.setTextColor(Color.parseColor("#E2E8F0"));
        makeupLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        makeupLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        makeupHeader.addView(makeupLabel);

        makeupValueView = new TextView(this);
        makeupValueView.setText("+" + prefs.getEqMakeupDb() + " dB");
        makeupValueView.setTextColor(Color.parseColor("#38BDF8"));
        makeupValueView.setTypeface(null, Typeface.BOLD);
        makeupHeader.addView(makeupValueView);

        makeupSlider = new SeekBar(this);
        makeupSlider.setMax(15);
        makeupSlider.setProgress(prefs.getEqMakeupDb());
        makeupSlider.setPadding(0, dp(4), 0, dp(10));
        makeupSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                makeupValueView.setText("+" + progress + " dB");
                if (fromUser && !syncingUi) {
                    prefs.setEqMakeupDb(progress);
                    pushServiceUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        eqCard.addView(makeupSlider);

        toggleBandsBtn = new Button(this);
        toggleBandsBtn.setText("Show 19-Band EQ Sliders");
        toggleBandsBtn.setAllCaps(false);
        toggleBandsBtn.setTextColor(Color.WHITE);
        toggleBandsBtn.setBackground(makeRoundedBg("#1E293B", 10));
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
        );
        toggleBandsBtn.setLayoutParams(tbLp);
        toggleBandsBtn.setOnClickListener(v -> {
            bandsVisible = !bandsVisible;
            eqBandsContainer.setVisibility(bandsVisible ? View.VISIBLE : View.GONE);
            toggleBandsBtn.setText(bandsVisible ? "Hide 19-Band EQ Sliders" : "Show 19-Band EQ Sliders");
        });
        eqCard.addView(toggleBandsBtn);

        eqBandsContainer = new LinearLayout(this);
        eqBandsContainer.setOrientation(LinearLayout.VERTICAL);
        eqBandsContainer.setPadding(0, dp(10), 0, 0);
        eqBandsContainer.setVisibility(View.GONE);
        eqCard.addView(eqBandsContainer);

        for (int i = 0; i < Prefs.EQ_FREQS_HZ.length; i++) {
            final int bandIndex = i;
            float currentDb = prefs.getEqBandDb(i);

            LinearLayout bandHeader = new LinearLayout(this);
            bandHeader.setOrientation(LinearLayout.HORIZONTAL);
            eqBandsContainer.addView(bandHeader);

            TextView bandLabel = new TextView(this);
            bandLabel.setText(Prefs.EQ_LABELS[i]);
            bandLabel.setTextColor(Color.parseColor("#CBD5E1"));
            bandLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bandLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            bandHeader.addView(bandLabel);

            TextView bandVal = new TextView(this);
            bandVal.setText(formatSignedHalfDb(currentDb));
            bandVal.setTextColor(Color.parseColor("#38BDF8"));
            bandVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bandVal.setTypeface(null, Typeface.BOLD);
            bandHeader.addView(bandVal);
            eqValueViews[i] = bandVal;

            SeekBar sb = new SeekBar(this);
            sb.setMax(Prefs.EQ_SLIDER_STEPS);
            sb.setProgress(dbToSlider(currentDb));
            sb.setPadding(0, dp(2), 0, dp(8));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float db = sliderToDb(progress);
                    bandVal.setText(formatSignedHalfDb(db));
                    if (fromUser && !syncingUi) {
                        prefs.setEqBandDb(bandIndex, db);
                        pushServiceUpdate();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            eqBandsContainer.addView(sb);
            eqSliders[i] = sb;
        }

        // 7. System Card
        LinearLayout settingsCard = makeCard();
        root.addView(settingsCard);

        addSwitchRow(settingsCard, "Enable Boost Service", "Runs in native audio server with minimal RAM",
            Color.parseColor("#94A3B8"), prefs.isEnabled(), checked -> {
                prefs.setEnabled(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Auto-Bypass Headphones / BT", "Pauses boost when external audio is connected",
            Color.parseColor("#94A3B8"), prefs.isSpeakerOnly(), checked -> {
                prefs.setSpeakerOnly(checked);
                pushServiceUpdate();
            });

        addSwitchRow(settingsCard, "Auto-Start on Boot", "Starts automatically when your device boots",
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

    private void loadPresetAndRefresh(
        float[] eqBands,
        int makeupDb,
        boolean stereoOn,
        int stageWidthPct,
        boolean limiterOn,
        float limiterThresholdDb,
        int limiterReleaseMs
    ) {
        prefs.applyPresetTemplate(eqBands, makeupDb, stereoOn, stageWidthPct, limiterOn, limiterThresholdDb, limiterReleaseMs);
        syncingUi = true;
        try {
            if (eqSwitch != null) eqSwitch.setChecked(prefs.isEqEnabled());
            if (makeupSlider != null) makeupSlider.setProgress(prefs.getEqMakeupDb());
            if (makeupValueView != null) makeupValueView.setText("+" + prefs.getEqMakeupDb() + " dB");
            for (int i = 0; i < Prefs.EQ_FREQS_HZ.length; i++) {
                float db = prefs.getEqBandDb(i);
                if (eqSliders[i] != null) eqSliders[i].setProgress(dbToSlider(db));
                if (eqValueViews[i] != null) eqValueViews[i].setText(formatSignedHalfDb(db));
            }
            if (stereoWideSwitch != null) stereoWideSwitch.setChecked(prefs.isStereoWideEnabled());
            if (widthSlider != null) widthSlider.setProgress(prefs.getStageWidthPercent());
            if (widthValueView != null) widthValueView.setText(prefs.getStageWidthPercent() + "%");
            if (limiterSwitch != null) limiterSwitch.setChecked(prefs.isLimiterEnabled());
            if (limThreshSlider != null) limThreshSlider.setProgress(Math.round((prefs.getLimiterThresholdDb() + 6.0f) * 10f));
            if (limThreshValueView != null) limThreshValueView.setText(String.format(Locale.US, "%.1f dB", prefs.getLimiterThresholdDb()));
            if (limReleaseSlider != null) limReleaseSlider.setProgress((prefs.getLimiterReleaseMs() - 10) / 10);
            if (limReleaseValueView != null) limReleaseValueView.setText(prefs.getLimiterReleaseMs() + " ms");
        } finally {
            syncingUi = false;
        }
        pushServiceUpdate();
    }

    private void addPresetBtn(
        LinearLayout row,
        String label,
        float[] bands,
        int makeupDb,
        boolean stereoOn,
        int widthPct,
        boolean limOn,
        float limThresh,
        int limRelMs
    ) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setBackground(makeRoundedBg("#1E293B", 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> loadPresetAndRefresh(bands, makeupDb, stereoOn, widthPct, limOn, limThresh, limRelMs));
        row.addView(b);
    }

    private void updateAccessibilityUi() {
        if (accBannerView == null || addAppBtn == null) return;
        boolean connected = AppSwitchAccessibilityService.isConnected();
        if (connected) {
            accBannerView.setText("Step 1 Complete: Accessibility Connected (0% CPU during gameplay). You can now add per-app dB overrides.");
            accBannerView.setTextColor(Color.parseColor("#4ADE80"));
            accBannerView.setBackground(makeRoundedBg("#0F291E", 10));
            addAppBtn.setAlpha(1.0f);
        } else {
            accBannerView.setText("REQUIRED FIRST: Tap '1. Enable Accessibility' and turn on 'Thor Audio Per-App Switcher' before adding per-app dB overrides.");
            accBannerView.setTextColor(Color.parseColor("#FBBF24"));
            accBannerView.setBackground(makeRoundedBg("#2A1F0B", 10));
            addAppBtn.setAlpha(0.45f);
        }
    }

    private void rebuildPerAppList() {
        if (perAppListContainer == null) return;
        perAppListContainer.removeAllViews();

        List<String> pkgs = prefs.getPerAppPackages();
        if (pkgs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No per-app overrides added. All apps currently use Global dB (+" + prefs.getGainDb() + " dB).");
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
            .setTitle("Select App for Custom dB")
            .setItems(items, (dialog, which) -> {
                String label = apps.get(which)[0];
                String pkg = apps.get(which)[1];
                prefs.setPerAppGain(pkg, label, Math.min(prefs.getCurrentMaxDb(), 8));
                prefs.setPerAppOverridesEnabled(true);
                rebuildPerAppList();
                pushServiceUpdate();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private int dbToSlider(float db) {
        return Math.max(0, Math.min(Prefs.EQ_SLIDER_STEPS, Math.round((db - Prefs.EQ_MIN_DB) * 2.0f)));
    }

    private float sliderToDb(int progress) {
        return Prefs.EQ_MIN_DB + (progress * 0.5f);
    }

    private String formatSignedHalfDb(float db) {
        return String.format(Locale.US, "%+.1f dB", db);
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
            gainRangeHint.setText("Safe Range: 0 to +10 dB (applies to any app without a per-app override)");
            gainRangeHint.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    private interface ToggleListener {
        void onToggle(boolean checked);
    }

    private Switch addSwitchRow(
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
        return sw;
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
