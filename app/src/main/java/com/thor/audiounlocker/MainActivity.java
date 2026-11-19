package com.thor.audiounlocker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    private static final String BG_DARK = "#090E1A";
    private static final String CARD_BG = "#111827";
    private static final String CARD_STROKE = "#1F2937";
    private static final String INNER_BG = "#1B2436";
    private static final String ACCENT = "#38BDF8";
    private static final String DANGER = "#EF4444";
    private static final String SUCCESS = "#22C55E";
    private static final String WARN = "#F59E0B";
    private static final String TEXT_MUTED = "#94A3B8";

    private Prefs prefs;
    private boolean syncingUi = false;
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedPush = this::pushServiceUpdateImmediate;

    // Header & Master Power
    private Switch masterSwitch;
    private TextView statusBadge;
    private TextView statusDetail;

    // Tabs
    private int activeTab = 0;
    private final Button[] tabButtons = new Button[3];
    private final LinearLayout[] tabPages = new LinearLayout[3];

    // Tab 0: Gain & Per-App
    private TextView gainValueView;
    private TextView gainRangeHint;
    private SeekBar gainSlider;
    private TextView accBannerView;
    private Button addAppBtn;
    private LinearLayout perAppListContainer;

    // Tab 1: EQ & Sound
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

    // Tab 2: Prerequisites & System
    private TextView prereqNotifStatus;
    private TextView prereqAccStatus;

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
        syncingUi = true;
        if (masterSwitch != null) {
            masterSwitch.setChecked(prefs.isEnabled());
        }
        syncingUi = false;
        refreshStatusUi(AudioBoostService.getLastStatus());
        updatePrerequisitesUi();
        rebuildPerAppList();
        AudioBoostService.setStatusCallback(text -> runOnUiThread(() -> refreshStatusUi(text)));
    }

    @Override
    protected void onPause() {
        debounceHandler.removeCallbacks(debouncedPush);
        AudioBoostService.setStatusCallback(null);
        super.onPause();
    }

    private View buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor(BG_DARK));

        // --- Fixed Top Bar + Hero Status + Tabs ---
        LinearLayout topArea = new LinearLayout(this);
        topArea.setOrientation(LinearLayout.VERTICAL);
        topArea.setPadding(dp(16), dp(14), dp(16), dp(8));
        outer.addView(topArea);

        // Hero Power Card
        LinearLayout hero = makeCard(dp(10));
        topArea.addView(hero);

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(heroTop);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heroTop.addView(titleCol);

        TextView appTitle = new TextView(this);
        appTitle.setText("Thor Audio Unlocker");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        appTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(appTitle);

        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);
        badgeRow.setPadding(0, dp(4), 0, 0);
        titleCol.addView(badgeRow);

        statusBadge = new TextView(this);
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statusBadge.setTypeface(null, Typeface.BOLD);
        statusBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
        badgeRow.addView(statusBadge);

        statusDetail = new TextView(this);
        statusDetail.setTextColor(Color.parseColor(TEXT_MUTED));
        statusDetail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusDetail.setPadding(dp(8), 0, 0, 0);
        badgeRow.addView(statusDetail);

        masterSwitch = new Switch(this);
        masterSwitch.setChecked(prefs.isEnabled());
        masterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingUi) return;
            prefs.setEnabled(isChecked);
            pushServiceUpdateImmediate();
        });
        heroTop.addView(masterSwitch);

        // 3-Tab Segmented Bar
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackground(makeRoundedBg(CARD_BG, 12, CARD_STROKE));
        tabBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        topArea.addView(tabBar);

        String[] tabNames = {"Gain & Apps", "EQ & Sound", "Setup & System"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button tb = new Button(this);
            tb.setText(tabNames[i]);
            tb.setAllCaps(false);
            tb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tb.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            tb.setLayoutParams(lp);
            tb.setOnClickListener(v -> selectTab(idx));
            tabButtons[i] = tb;
            tabBar.addView(tb);
        }

        // --- Scrollable Tab Content ---
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        outer.addView(scroll);

        LinearLayout contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setPadding(dp(16), dp(6), dp(16), dp(16));
        scroll.addView(contentHost);

        tabPages[0] = buildGainAndAppsTab();
        tabPages[1] = buildEqAndSoundTab();
        tabPages[2] = buildSetupAndSystemTab();

        for (LinearLayout page : tabPages) {
            contentHost.addView(page);
        }
        selectTab(0);

        // --- Bottom Sticky Footer ---
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setBackgroundColor(Color.parseColor("#0D1322"));
        footer.setPadding(dp(16), dp(10), dp(16), dp(12));
        outer.addView(footer);

        Button closeBtn = new Button(this);
        closeBtn.setText("Apply & Close UI (Keep Running in Background)");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(Color.parseColor(BG_DARK));
        closeBtn.setTypeface(null, Typeface.BOLD);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        closeBtn.setBackground(makeRoundedBg(ACCENT, 12, null));
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
        ));
        closeBtn.setOnClickListener(v -> {
            pushServiceUpdateImmediate();
            finishAndRemoveTask();
        });
        footer.addView(closeBtn);

        refreshStatusUi(AudioBoostService.getLastStatus());
        return outer;
    }

    private void selectTab(int index) {
        activeTab = index;
        for (int i = 0; i < 3; i++) {
            boolean selected = (i == index);
            tabPages[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            tabButtons[i].setBackground(makeRoundedBg(selected ? ACCENT : CARD_BG, 9, null));
            tabButtons[i].setTextColor(Color.parseColor(selected ? BG_DARK : TEXT_MUTED));
        }
    }

    private void refreshStatusUi(String statusText) {
        if (statusBadge == null || statusDetail == null) return;
        boolean on = prefs.isEnabled();
        boolean paused = statusText != null && statusText.startsWith("Paused");
        if (!on) {
            statusBadge.setText("OFF");
            statusBadge.setTextColor(Color.WHITE);
            statusBadge.setBackground(makeRoundedBg("#475569", 6, null));
        } else if (paused) {
            statusBadge.setText("PAUSED");
            statusBadge.setTextColor(Color.parseColor(BG_DARK));
            statusBadge.setBackground(makeRoundedBg(WARN, 6, null));
        } else {
            statusBadge.setText("ACTIVE");
            statusBadge.setTextColor(Color.parseColor(BG_DARK));
            statusBadge.setBackground(makeRoundedBg(SUCCESS, 6, null));
        }
        statusDetail.setText(statusText != null ? statusText : "Idle");
    }

    // ==================== TAB 0: GAIN & PER-APP ====================
    private LinearLayout buildGainAndAppsTab() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        // Global dB Card
        LinearLayout gainCard = makeCard(dp(12));
        page.addView(gainCard);

        LinearLayout gainHeader = new LinearLayout(this);
        gainHeader.setOrientation(LinearLayout.HORIZONTAL);
        gainHeader.setGravity(Gravity.CENTER_VERTICAL);
        gainCard.addView(gainHeader);

        LinearLayout gainTitleCol = new LinearLayout(this);
        gainTitleCol.setOrientation(LinearLayout.VERTICAL);
        gainTitleCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        gainHeader.addView(gainTitleCol);

        TextView gainLabel = new TextView(this);
        gainLabel.setText("Global Speaker Gain");
        gainLabel.setTextColor(Color.WHITE);
        gainLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        gainLabel.setTypeface(null, Typeface.BOLD);
        gainTitleCol.addView(gainLabel);

        TextView gainSub = new TextView(this);
        gainSub.setText("Default boost for all apps without an override");
        gainSub.setTextColor(Color.parseColor(TEXT_MUTED));
        gainSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        gainTitleCol.addView(gainSub);

        gainValueView = new TextView(this);
        gainValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        gainValueView.setTypeface(null, Typeface.BOLD);
        gainHeader.addView(gainValueView);

        gainSlider = createStyledSeekBar(prefs.getCurrentMaxDb(), prefs.getGainDb());
        gainSlider.setPadding(0, dp(12), 0, dp(6));
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
        gainRangeHint.setPadding(0, 0, 0, dp(8));
        updateGainRangeHint();
        gainCard.addView(gainRangeHint);

        addDivider(gainCard);

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
                pushServiceUpdateImmediate();
            }
        );

        // Per-App Overrides Card
        LinearLayout perAppCard = makeCard(dp(12));
        page.addView(perAppCard);

        TextView perAppTitle = new TextView(this);
        perAppTitle.setText("Per-App dB Overrides");
        perAppTitle.setTextColor(Color.WHITE);
        perAppTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        perAppTitle.setTypeface(null, Typeface.BOLD);
        perAppCard.addView(perAppTitle);

        addSwitchRow(
            perAppCard,
            "Use Per-App Overrides",
            "Listed apps replace Global dB automatically when open (they never stack or conflict).",
            Color.parseColor(TEXT_MUTED),
            prefs.isPerAppOverridesEnabled(),
            checked -> {
                prefs.setPerAppOverridesEnabled(checked);
                pushServiceUpdateImmediate();
            }
        );

        accBannerView = new TextView(this);
        accBannerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
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
        accBtn.setText("1. Accessibility");
        accBtn.setAllCaps(false);
        accBtn.setTextColor(Color.WHITE);
        accBtn.setBackground(makeRoundedBg(INNER_BG, 10, CARD_STROKE));
        LinearLayout.LayoutParams lpAcc = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lpAcc.rightMargin = dp(6);
        accBtn.setLayoutParams(lpAcc);
        accBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        perAppBtnRow.addView(accBtn);

        addAppBtn = new Button(this);
        addAppBtn.setText("2. + Add App");
        addAppBtn.setAllCaps(false);
        addAppBtn.setTextColor(Color.parseColor(BG_DARK));
        addAppBtn.setTypeface(null, Typeface.BOLD);
        addAppBtn.setBackground(makeRoundedBg(ACCENT, 10, null));
        LinearLayout.LayoutParams lpAdd = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lpAdd.leftMargin = dp(6);
        addAppBtn.setLayoutParams(lpAdd);
        addAppBtn.setOnClickListener(v -> {
            if (!AppSwitchAccessibilityService.isConnected()) {
                new AlertDialog.Builder(this)
                    .setTitle("Prerequisite Required")
                    .setMessage("Step 1: Turn on 'Thor Audio Per-App Switcher' in Android Accessibility Settings before adding per-app dB overrides.\n\nIt only listens for app switches (0% CPU during gameplay).")
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

        return page;
    }

    // ==================== TAB 1: EQ & SOUND ====================
    private LinearLayout buildEqAndSoundTab() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        // Quick Profile Templates Card
        LinearLayout presetCard = makeCard(dp(12));
        page.addView(presetCard);

        TextView presetTitle = new TextView(this);
        presetTitle.setText("Quick Profiles (Starting Templates)");
        presetTitle.setTextColor(Color.WHITE);
        presetTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        presetTitle.setTypeface(null, Typeface.BOLD);
        presetCard.addView(presetTitle);

        TextView presetSub = new TextView(this);
        presetSub.setText("Loads EQ, Stereo Width, and Limiter values below. Every control remains independently customizable.");
        presetSub.setTextColor(Color.parseColor(TEXT_MUTED));
        presetSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        presetSub.setPadding(0, dp(3), 0, dp(10));
        presetCard.addView(presetSub);

        Button joeyBtn = new Button(this);
        joeyBtn.setText("Joey's Retro Handhelds (19-Band EQ • 40% Stereo)");
        joeyBtn.setAllCaps(false);
        joeyBtn.setTextColor(Color.parseColor(BG_DARK));
        joeyBtn.setTypeface(null, Typeface.BOLD);
        joeyBtn.setBackground(makeRoundedBg(ACCENT, 10, null));
        LinearLayout.LayoutParams joeyLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
        );
        joeyLp.bottomMargin = dp(8);
        joeyBtn.setLayoutParams(joeyLp);
        joeyBtn.setOnClickListener(v -> loadPresetAndRefresh(
            Prefs.PRESET_JOEY_EQ, 16, true, 40, true, -0.1f, 500
        ));
        presetCard.addView(joeyBtn);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetCard.addView(presetRow);

        addPresetBtn(presetRow, "Wide Stage", Prefs.PRESET_WIDE_EQ, 0, true, 70, true, -0.5f, 60);
        addPresetBtn(presetRow, "Vocal Clear", Prefs.PRESET_VOCAL_EQ, 0, true, 45, true, -0.5f, 60);
        addPresetBtn(presetRow, "Flat", Prefs.PRESET_FLAT_EQ, 0, false, 0, true, -0.5f, 60);

        // Stereo Stage Card
        LinearLayout stereoCard = makeCard(dp(12));
        page.addView(stereoCard);

        TextView stereoTitle = new TextView(this);
        stereoTitle.setText("Stereo Stage Widener");
        stereoTitle.setTextColor(Color.WHITE);
        stereoTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        stereoTitle.setTypeface(null, Typeface.BOLD);
        stereoCard.addView(stereoTitle);

        stereoWideSwitch = addSwitchRow(
            stereoCard,
            "Enable Stereo Widener",
            "Virtualizer + subtle L/R complementary EQ tilt for wider speakers",
            Color.parseColor(TEXT_MUTED),
            prefs.isStereoWideEnabled(),
            checked -> {
                if (syncingUi) return;
                prefs.setStereoWideEnabled(checked);
                pushServiceUpdateImmediate();
            }
        );

        widthValueView = addSliderHeader(stereoCard, "Stage Width", prefs.getStageWidthPercent() + "%");
        widthSlider = createStyledSeekBar(100, prefs.getStageWidthPercent());
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

        // Output Limiter Card
        LinearLayout limiterCard = makeCard(dp(12));
        page.addView(limiterCard);

        TextView limiterTitle = new TextView(this);
        limiterTitle.setText("Output Limiter");
        limiterTitle.setTextColor(Color.WHITE);
        limiterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        limiterTitle.setTypeface(null, Typeface.BOLD);
        limiterCard.addView(limiterTitle);

        limiterSwitch = addSwitchRow(
            limiterCard,
            "Enable Output Limiter",
            "Catches peaks cleanly to prevent digital clipping at high volume",
            Color.parseColor(TEXT_MUTED),
            prefs.isLimiterEnabled(),
            checked -> {
                if (syncingUi) return;
                prefs.setLimiterEnabled(checked);
                pushServiceUpdateImmediate();
            }
        );

        limThreshValueView = addSliderHeader(
            limiterCard, "Limiter Threshold", String.format(Locale.US, "%.1f dB", prefs.getLimiterThresholdDb())
        );
        limThreshSlider = createStyledSeekBar(60, Math.round((prefs.getLimiterThresholdDb() + 6.0f) * 10f));
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

        limReleaseValueView = addSliderHeader(
            limiterCard, "Limiter Release Time", prefs.getLimiterReleaseMs() + " ms"
        );
        limReleaseSlider = createStyledSeekBar(49, (prefs.getLimiterReleaseMs() - 10) / 10);
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

        // 19-Band EQ Card
        LinearLayout eqCard = makeCard(dp(12));
        page.addView(eqCard);

        TextView eqTitle = new TextView(this);
        eqTitle.setText("19-Band Equalizer");
        eqTitle.setTextColor(Color.WHITE);
        eqTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        eqTitle.setTypeface(null, Typeface.BOLD);
        eqCard.addView(eqTitle);

        eqSwitch = addSwitchRow(
            eqCard,
            "Enable 19-Band Equalizer",
            "Fine-tune 70 Hz to 16 kHz (-32.0 to +12.0 dB in 0.5 dB steps)",
            Color.parseColor(TEXT_MUTED),
            prefs.isEqEnabled(),
            checked -> {
                if (syncingUi) return;
                prefs.setEqEnabled(checked);
                pushServiceUpdateImmediate();
            }
        );

        makeupValueView = addSliderHeader(eqCard, "EQ Makeup Gain (Compensates Deep Cuts)", "+" + prefs.getEqMakeupDb() + " dB");
        makeupSlider = createStyledSeekBar(20, prefs.getEqMakeupDb());
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
        toggleBandsBtn.setText("Show 19-Band Sliders");
        toggleBandsBtn.setAllCaps(false);
        toggleBandsBtn.setTextColor(Color.WHITE);
        toggleBandsBtn.setBackground(makeRoundedBg(INNER_BG, 10, CARD_STROKE));
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
        );
        tbLp.topMargin = dp(8);
        toggleBandsBtn.setLayoutParams(tbLp);
        toggleBandsBtn.setOnClickListener(v -> {
            bandsVisible = !bandsVisible;
            eqBandsContainer.setVisibility(bandsVisible ? View.VISIBLE : View.GONE);
            toggleBandsBtn.setText(bandsVisible ? "Hide 19-Band Sliders" : "Show 19-Band Sliders");
        });
        eqCard.addView(toggleBandsBtn);

        eqBandsContainer = new LinearLayout(this);
        eqBandsContainer.setOrientation(LinearLayout.VERTICAL);
        eqBandsContainer.setPadding(0, dp(10), 0, 0);
        eqBandsContainer.setVisibility(View.GONE);
        eqCard.addView(eqBandsContainer);

        for (int i = 0; i < Prefs.EQ_FREQS_HZ.length; i++) {
            final int bandIndex = i;
            if (i == 0) addSubSectionLabel(eqBandsContainer, "BASS & LOW-MIDS");
            else if (i == 6) addSubSectionLabel(eqBandsContainer, "MIDRANGE & VOCALS");
            else if (i == 13) addSubSectionLabel(eqBandsContainer, "PRESENCE & TREBLE");

            float currentDb = prefs.getEqBandDb(i);
            TextView bandVal = addSliderHeader(eqBandsContainer, Prefs.EQ_LABELS[i], formatSignedHalfDb(currentDb));
            eqValueViews[i] = bandVal;

            SeekBar sb = createStyledSeekBar(Prefs.EQ_SLIDER_STEPS, dbToSlider(currentDb));
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

        return page;
    }

    // ==================== TAB 2: SETUP & SYSTEM ====================
    private LinearLayout buildSetupAndSystemTab() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        // Prerequisites Checklist Card
        LinearLayout prereqCard = makeCard(dp(12));
        page.addView(prereqCard);

        TextView prereqTitle = new TextView(this);
        prereqTitle.setText("Setup & Prerequisites");
        prereqTitle.setTextColor(Color.WHITE);
        prereqTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        prereqTitle.setTypeface(null, Typeface.BOLD);
        prereqCard.addView(prereqTitle);

        TextView prereqSub = new TextView(this);
        prereqSub.setText("Complete these once so Android keeps the audio service alive and switches per-app dB cleanly.");
        prereqSub.setTextColor(Color.parseColor(TEXT_MUTED));
        prereqSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        prereqSub.setPadding(0, dp(3), 0, dp(10));
        prereqCard.addView(prereqSub);

        // Prereq 1: Notifications
        prereqNotifStatus = addPrereqRow(
            prereqCard,
            "1. Notification Permission (Required on Android 13+)",
            "Keeps the background audio service alive and gives you Turn On / Turn Off controls.",
            "Grant",
            v -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                } else {
                    openAppSettings();
                }
            }
        );

        addDivider(prereqCard);

        // Prereq 2: Accessibility
        prereqAccStatus = addPrereqRow(
            prereqCard,
            "2. Accessibility Service (Required for Per-App dB)",
            "Enable 'Thor Audio Per-App Switcher' so the app knows when you open a game (0% CPU during gameplay).",
            "Open",
            v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );

        addDivider(prereqCard);

        // Prereq 3: Battery Optimization
        addPrereqRow(
            prereqCard,
            "3. Battery Unrestricted (Recommended)",
            "In App Info > App Battery Usage, choose 'Unrestricted' so Android never kills the boost mid-game.",
            "App Info",
            v -> openAppSettings()
        );

        // Behavior & Protection Card
        LinearLayout sysCard = makeCard(dp(12));
        page.addView(sysCard);

        TextView sysTitle = new TextView(this);
        sysTitle.setText("Audio Protection & Boot");
        sysTitle.setTextColor(Color.WHITE);
        sysTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sysTitle.setTypeface(null, Typeface.BOLD);
        sysCard.addView(sysTitle);

        addSwitchRow(
            sysCard,
            "Auto-Bypass Headphones / Bluetooth",
            "Automatically pauses speaker boost when 3.5mm, USB-C, HDMI, or Bluetooth audio connects",
            Color.parseColor(TEXT_MUTED),
            prefs.isSpeakerOnly(),
            checked -> {
                prefs.setSpeakerOnly(checked);
                pushServiceUpdateImmediate();
            }
        );

        addDivider(sysCard);

        addSwitchRow(
            sysCard,
            "Auto-Start on Boot",
            "Automatically restores your audio service every time your device powers on",
            Color.parseColor(TEXT_MUTED),
            prefs.isStartOnBoot(),
            prefs::setStartOnBoot
        );

        addDivider(sysCard);

        TextView tileTip = new TextView(this);
        tileTip.setText("Tip: Swipe down Quick Settings, tap the pencil icon, and drag the 'Thor Boost' tile to toggle boost ON/OFF from any game.");
        tileTip.setTextColor(Color.parseColor(TEXT_MUTED));
        tileTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tileTip.setPadding(0, dp(8), 0, dp(10));
        sysCard.addView(tileTip);

        Button exitSvcBtn = new Button(this);
        exitSvcBtn.setText("Stop & Exit Background Service Completely");
        exitSvcBtn.setAllCaps(false);
        exitSvcBtn.setTextColor(Color.parseColor("#FCA5A5"));
        exitSvcBtn.setBackground(makeRoundedBg("#2A1215", 10, "#7F1D1D"));
        exitSvcBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
        ));
        exitSvcBtn.setOnClickListener(v -> {
            prefs.setEnabled(false);
            AudioBoostService.stopServiceNow(this);
            syncingUi = true;
            if (masterSwitch != null) masterSwitch.setChecked(false);
            syncingUi = false;
            refreshStatusUi("Off");
        });
        sysCard.addView(exitSvcBtn);

        return page;
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }

    private TextView addPrereqRow(
        LinearLayout parent,
        String title,
        String desc,
        String btnLabel,
        View.OnClickListener onClick
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        textCol.setPadding(0, 0, dp(10), 0);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(null, Typeface.BOLD);
        textCol.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(Color.parseColor(TEXT_MUTED));
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textCol.addView(d);

        TextView status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setTypeface(null, Typeface.BOLD);
        status.setPadding(0, dp(3), 0, 0);
        textCol.addView(status);

        Button b = new Button(this);
        b.setText(btnLabel);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setBackground(makeRoundedBg(INNER_BG, 8, CARD_STROKE));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(84), dp(36)));
        b.setOnClickListener(onClick);

        row.addView(textCol);
        row.addView(b);
        parent.addView(row);
        return status;
    }

    private void updatePrerequisitesUi() {
        boolean notifOk = Build.VERSION.SDK_INT < 33
            || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (prereqNotifStatus != null) {
            prereqNotifStatus.setText(notifOk ? "Status: Granted" : "Status: Not granted yet");
            prereqNotifStatus.setTextColor(Color.parseColor(notifOk ? SUCCESS : WARN));
        }

        boolean accOk = AppSwitchAccessibilityService.isConnected();
        if (prereqAccStatus != null) {
            prereqAccStatus.setText(accOk ? "Status: Connected (0% CPU during gameplay)" : "Status: Not enabled");
            prereqAccStatus.setTextColor(Color.parseColor(accOk ? SUCCESS : WARN));
        }

        if (accBannerView != null && addAppBtn != null) {
            if (accOk) {
                accBannerView.setText("Accessibility Connected (0% CPU during gameplay). Tap '+ Add App' to set per-app dB overrides.");
                accBannerView.setTextColor(Color.parseColor("#4ADE80"));
                accBannerView.setBackground(makeRoundedBg("#0F291E", 10, "#166534"));
                addAppBtn.setAlpha(1.0f);
            } else {
                accBannerView.setText("PREREQUISITE: Tap '1. Accessibility' and turn on 'Thor Audio Per-App Switcher' before adding per-app dB overrides.");
                accBannerView.setTextColor(Color.parseColor("#FBBF24"));
                accBannerView.setBackground(makeRoundedBg("#2A1F0B", 10, "#92400E"));
                addAppBtn.setAlpha(0.45f);
            }
        }
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
        pushServiceUpdateImmediate();
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
        b.setBackground(makeRoundedBg(INNER_BG, 10, CARD_STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> loadPresetAndRefresh(bands, makeupDb, stereoOn, widthPct, limOn, limThresh, limRelMs));
        row.addView(b);
    }

    private void rebuildPerAppList() {
        if (perAppListContainer == null) return;
        perAppListContainer.removeAllViews();

        List<String> pkgs = prefs.getPerAppPackages();
        if (pkgs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No per-app overrides yet. All apps currently use Global dB (+" + prefs.getGainDb() + " dB).");
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
            box.setBackground(makeRoundedBg(INNER_BG, 12, CARD_STROKE));
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
            dbView.setTextColor(Color.parseColor(appDb > Prefs.SAFE_MAX_DB ? DANGER : ACCENT));
            dbView.setTypeface(null, Typeface.BOLD);
            dbView.setPadding(0, 0, dp(10), 0);
            topRow.addView(dbView);

            Button removeBtn = new Button(this);
            removeBtn.setText("Remove");
            removeBtn.setAllCaps(false);
            removeBtn.setTextColor(Color.parseColor("#FCA5A5"));
            removeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            removeBtn.setBackground(makeRoundedBg("#2A1215", 8, "#7F1D1D"));
            removeBtn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)));
            removeBtn.setOnClickListener(v -> {
                prefs.removePerApp(pkg);
                rebuildPerAppList();
                pushServiceUpdateImmediate();
            });
            topRow.addView(removeBtn);

            SeekBar sb = createStyledSeekBar(maxDb, Math.min(maxDb, appDb));
            sb.setPadding(0, dp(6), 0, 0);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    dbView.setText("+" + progress + " dB");
                    dbView.setTextColor(Color.parseColor(progress > Prefs.SAFE_MAX_DB ? DANGER : ACCENT));
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
                pushServiceUpdateImmediate();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private TextView addSliderHeader(LinearLayout parent, String label, String initialValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        parent.addView(row);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Color.parseColor("#E2E8F0"));
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        TextView v = new TextView(this);
        v.setText(initialValue);
        v.setTextColor(Color.parseColor(ACCENT));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTypeface(null, Typeface.BOLD);
        row.addView(v);
        return v;
    }

    private void addSubSectionLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#64748B"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, dp(8), 0, dp(2));
        parent.addView(tv);
    }

    private SeekBar createStyledSeekBar(int max, int progress) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setPadding(0, dp(4), 0, dp(6));
        sb.setProgressTintList(ColorStateList.valueOf(Color.parseColor(ACCENT)));
        sb.setThumbTintList(ColorStateList.valueOf(Color.parseColor(ACCENT)));
        return sb;
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
        boolean danger = db > Prefs.SAFE_MAX_DB;
        String colorHex = danger ? DANGER : ACCENT;
        gainValueView.setText("+" + db + " dB");
        gainValueView.setTextColor(Color.parseColor(colorHex));
        if (gainSlider != null) {
            gainSlider.setProgressTintList(ColorStateList.valueOf(Color.parseColor(colorHex)));
            gainSlider.setThumbTintList(ColorStateList.valueOf(Color.parseColor(colorHex)));
        }
    }

    private void updateGainRangeHint() {
        if (prefs.isExtraGainUnlocked()) {
            gainRangeHint.setText("Range: 0 to +20 dB (DANGER ZONE UNLOCKED above +10 dB)");
            gainRangeHint.setTextColor(Color.parseColor("#F87171"));
        } else {
            gainRangeHint.setText("Safe Range: 0 to +10 dB (Recommended: +4 to +9 dB)");
            gainRangeHint.setTextColor(Color.parseColor(TEXT_MUTED));
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
        textCol.setPadding(0, 0, dp(10), 0);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
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

    private void addDivider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor(CARD_STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        );
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        v.setLayoutParams(lp);
        parent.addView(v);
    }

    private LinearLayout makeCard(int bottomMarginPx) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeRoundedBg(CARD_BG, 14, CARD_STROKE));
        int p = dp(14);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = bottomMarginPx;
        card.setLayoutParams(lp);
        return card;
    }

    private GradientDrawable makeRoundedBg(String fillHex, int radiusDp, String strokeHex) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(fillHex));
        d.setCornerRadius(dp(radiusDp));
        if (strokeHex != null) {
            d.setStroke(dp(1), Color.parseColor(strokeHex));
        }
        return d;
    }

    private void pushServiceUpdate() {
        debounceHandler.removeCallbacks(debouncedPush);
        debounceHandler.postDelayed(debouncedPush, 60);
    }

    private void pushServiceUpdateImmediate() {
        debounceHandler.removeCallbacks(debouncedPush);
        AudioBoostService.startOrUpdate(this);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
