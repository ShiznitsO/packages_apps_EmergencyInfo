/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.emergency.view;

import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.android.emergency.EmergencyTabActivity;
import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.edit.EditInfoActivity;
import com.android.emergency.preferences.BirthdayPreference;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Activity for viewing emergency information.
 */
public class ViewInfoActivity extends EmergencyTabActivity {
    private TextView mPersonalCardLargeItem;
    private TextView mPersonalCardSmallItem;
    private SharedPreferences mSharedPreferences;
    private LinearLayout mPersonalCard;
    private ViewFlipper mViewFlipper;

    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");
    private DateFormat mDateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_activity_layout);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPersonalCard = (LinearLayout) findViewById(R.id.name_and_dob_linear_layout);
        mPersonalCardLargeItem = (TextView) findViewById(R.id.personal_card_large);
        mPersonalCardSmallItem = (TextView) findViewById(R.id.personal_card_small);
        mViewFlipper = (ViewFlipper) findViewById(R.id.view_flipper);

        mDateFormat = DateFormat.getDateInstance();
        mDateFormat.setTimeZone(UTC_TIME_ZONE);

        MetricsLogger.visible(this, MetricsEvent.ACTION_VIEW_EMERGENCY_INFO);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfileCard();
        // Update the tabs: new info might have been added/deleted from the edit screen that
        // could lead to adding/removing a fragment
        setupTabs();
        maybeHideTabs();
    }

    private void loadProfileCard() {
        String name = mSharedPreferences.getString(PreferenceKeys.KEY_NAME, "");
        long dateOfBirthTimeMillis = mSharedPreferences.getLong(PreferenceKeys.KEY_DATE_OF_BIRTH,
                BirthdayPreference.DEFAULT_UNSET_VALUE);
        boolean nameEmpty = TextUtils.isEmpty(name);
        boolean dateOfBirthNotSet = dateOfBirthTimeMillis == BirthdayPreference.DEFAULT_UNSET_VALUE;
        if (nameEmpty && dateOfBirthNotSet) {
            mPersonalCard.setVisibility(View.GONE);
        } else {
            mPersonalCard.setVisibility(View.VISIBLE);
            if (!dateOfBirthNotSet) {
                mPersonalCardSmallItem.setVisibility(View.VISIBLE);
                int age = computeAge(dateOfBirthTimeMillis);
                String localizedDob = String.format(getString(R.string.dob),
                        mDateFormat.format(new Date(dateOfBirthTimeMillis)));
                Spannable spannableDob = new SpannableString(localizedDob);
                spannableDob.setSpan(new ForegroundColorSpan(
                                getResources().getColor(R.color.white_with_alpha)), 0,
                        localizedDob.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (nameEmpty) {
                    // Display date of birth info in two lines: age and then date of birth
                    mPersonalCardLargeItem.setText(String.format(getString(R.string.age), age));
                    mPersonalCardSmallItem.setText(spannableDob);
                } else {
                    mPersonalCardLargeItem.setText(name);
                    mPersonalCardSmallItem.setText(String.format(getString(R.string.age), age));
                    mPersonalCardSmallItem.append(" ");
                    mPersonalCardSmallItem.append(spannableDob);
                }
            } else {
                mPersonalCardSmallItem.setVisibility(View.GONE);
                mPersonalCardLargeItem.setText(name);
                mPersonalCardSmallItem.setText("");
            }
        }
    }

    private void maybeHideTabs() {
        // Show a TextView with "No information provided" if there are no fragments.
        if (getNumberFragments() == 0) {
            mViewFlipper.setDisplayedChild(
                    mViewFlipper.indexOfChild(findViewById(R.id.no_info_text_view)));
        } else {
            mViewFlipper.setDisplayedChild(mViewFlipper.indexOfChild(findViewById(R.id.tabs)));
        }

        TabLayout tabLayout = getTabLayout();
        if (getNumberFragments() <= 1) {
            tabLayout.setVisibility(View.GONE);
        } else {
            tabLayout.setVisibility(View.VISIBLE);
        }
    }

    static int computeAge(long dateOfBirthTimeMillis) {
        Calendar today = Calendar.getInstance(UTC_TIME_ZONE);
        Calendar dateOfBirthCalendar = Calendar.getInstance(UTC_TIME_ZONE);
        dateOfBirthCalendar.setTimeInMillis(dateOfBirthTimeMillis);
        int age = today.get(Calendar.YEAR) - dateOfBirthCalendar.get(Calendar.YEAR);
        if (today.get(Calendar.MONTH) < dateOfBirthCalendar.get(Calendar.MONTH) ||
                (today.get(Calendar.MONTH) == dateOfBirthCalendar.get(Calendar.MONTH) &&
                        today.get(Calendar.DAY_OF_MONTH) <
                                dateOfBirthCalendar.get(Calendar.DAY_OF_MONTH))) {
            age--;
        }
        // Return 0 if the user specifies a date of birth in the future.
        return Math.max(0, age);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.view_info_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_edit:
                Intent intent = new Intent(this, EditInfoActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected ArrayList<Pair<String, Fragment>> setUpFragments() {
        // Return only the fragments that have at least one piece of information set:
        ArrayList<Pair<String, Fragment>> fragments = new ArrayList<>(2);

        if (ViewEmergencyInfoFragment.hasAtLeastOnePreferenceSet(this)) {
            fragments.add(Pair.create(getResources().getString(R.string.tab_title_info),
                    ViewEmergencyInfoFragment.newInstance()));
        }
        if (ViewEmergencyContactsFragment.hasAtLeastOneEmergencyContact(this)) {
            fragments.add(Pair.create(getResources().getString(R.string.tab_title_contacts),
                    ViewEmergencyContactsFragment.newInstance()));
        }
        return fragments;
    }
}