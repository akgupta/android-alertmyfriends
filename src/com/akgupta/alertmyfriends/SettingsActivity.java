package com.akgupta.alertmyfriends;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity {
	
	public static final String KEY_PREF_SIREN = "pref_siren";
	public static final String KEY_PREF_SHAKE = "pref_shake";
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
