package com.akgupta.alertmyfriends;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class AlertActivity extends Activity implements LocationListener {

	private static final String DEBUG_TAG = "AlertActivity";
	private static final int TWO_MINUTES = 1000 * 60 * 2;
	private static final String URL_HELP_ANDROID = "http://alertmyfriends.zohosites.com";
	private static final String ACTION_SMS_SENT = "com.akgupta.alertmyfriends.SMS_SENT_ACTION";
	private static final String GOOGLE_MAPS_URL = "http://maps.google.com/?q=%1$f,%2$f";
	private static final String PHONE = "phone";
	private static final String PREFS_DATA = "data";
	private static final String CONTACTS_KEY = "contacts";
	public final static String EXTRA_LOCATION = "com.akgupta.alertmyfriends.LOCATION";
	public final static String EXTRA_ADDRESS = "com.akgupta.alertmyfriends.ADDRESS";

	private LocationManager locationManager;
	private Criteria criteria;
	private String provider;
	private Location currentLocation;
	private Address currentAddress;
	private SensorManager mSensorManager;
	private ShakeEventListener mSensorListener;
	private BroadcastReceiver smsSentReceiver;
	private SoundPool soundPool;
	private int soundID;
	private boolean soundLoaded = false;
	private ArrayList<Map<String, String>> selectedContacts;
	private Gson gson;
	private Type contactsListType;

	// ** Activity overrides **

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_alert);

		gson = new Gson();
		contactsListType = new TypeToken<ArrayList<Map<String, String>>>() {
		}.getType();

		// Get the location manager
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// Define the criteria how to select the location provider
		criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		// shake gesture
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mSensorListener = new ShakeEventListener();

		mSensorListener
				.setOnShakeListener(new ShakeEventListener.OnShakeListener() {
					public void onShake() {
						Log.d(DEBUG_TAG, "Shake");
						if (getBooleanPref(SettingsActivity.KEY_PREF_SHAKE)) {
							doSendAlerts(null);
						}
					}
				});

		// siren sound
		// Set the hardware buttons to control the music
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		// Load the sound
		soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
		soundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId,
					int status) {
				soundLoaded = true;
			}
		});
		soundID = soundPool.load(this, R.raw.siren, 1);

		// SMS sent intent receiver
		smsSentReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(DEBUG_TAG, "SMS sent intent received: " + getResultCode());
				boolean error = true;
				switch (getResultCode()) {
				case RESULT_OK:
					error = false;
					break;
				}
				Toast.makeText(
						AlertActivity.this,
						error ? R.string.sms_error_toast
								: R.string.sms_success_toast,
						Toast.LENGTH_SHORT).show();
			}
		};
	}

	@Override
	protected void onStart() {
		super.onStart();

		// This verification should be done during onStart() because the system
		// calls
		// this method when the user returns to the activity, which ensures the
		// desired
		// location provider is enabled each time the activity resumes from the
		// stopped state.
		final boolean gpsEnabled = locationManager
				.isProviderEnabled(LocationManager.GPS_PROVIDER);

		if (!gpsEnabled) {
			new AlertDialog.Builder(this)
					.setTitle(R.string.enable_gps)
					.setMessage(R.string.message_enable_gps)
					.setPositiveButton(R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									enableLocationSettings();
								}
							})
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							}).create().show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		selectBestProvider();
		if (provider != null) {
			locationManager.requestLocationUpdates(provider, 1000, // 1-second
					// interval.
					10, // 10 meters.
					this);
		}
		// resume sensor
		mSensorManager.registerListener(mSensorListener,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_UI);
		// resume sms sent receiver
		registerReceiver(smsSentReceiver, new IntentFilter(ACTION_SMS_SENT));
		// Contact list
		// Restore contacts from preferences
		readContacts();
	}

	@Override
	protected void onPause() {
		locationManager.removeUpdates(this);
		mSensorManager.unregisterListener(mSensorListener);
		unregisterReceiver(smsSentReceiver);
		super.onPause();
	}

	// ** Location manager overrides **
	@Override
	public void onLocationChanged(Location location) {
		if (isBetterLocation(location, currentLocation)) {
			currentLocation = location;
			updateLocationLabels();
			(new ReverseGeocodingTask(this)).execute(currentLocation);
		}
		Log.d(DEBUG_TAG, "Latitude:" + currentLocation.getLatitude()
				+ ", Longitude:" + currentLocation.getLongitude());
	}

	@Override
	public void onProviderDisabled(String arg0) {

	}

	@Override
	public void onProviderEnabled(String arg0) {

	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {

	}

	// ** Action handlers **

	public void doSendAlerts(View view) {
		if (selectedContacts.size() == 0) {
			Toast.makeText(AlertActivity.this, R.string.no_contacts_toast,
					Toast.LENGTH_SHORT).show();
			return;
		}
		String latLonLink = "";
		String addressText = "";
		if (currentLocation != null) {
			latLonLink = String.format(GOOGLE_MAPS_URL,
					currentLocation.getLatitude(),
					currentLocation.getLongitude());
			if (currentAddress != null) {
				// Format the first line of address (if available), city, and
				// country name.
				addressText = formatAddress(currentAddress);
			}
		}
		String message = String.format(getString(R.string.sms_text),
				addressText, latLonLink);
		Log.d(DEBUG_TAG, "SMS:" + message);
		// send SMS to everyone in selected contacts
		PendingIntent sentIntent = PendingIntent.getBroadcast(this, 0,
				new Intent(ACTION_SMS_SENT), 0);
		for (Map<String, String> contact : selectedContacts) {
			SmsManager.getDefault().sendTextMessage(contact.get(PHONE), null,
					message, sentIntent, null);
			sentIntent = null;
		}
		if (soundLoaded && getBooleanPref(SettingsActivity.KEY_PREF_SIREN)) {
			soundPool.play(soundID, 1f, 1f, 1, 2, 1f);
		}
	}

	/** Called when the user clicks on the location labels */
	public void showMap(View view) {
		if (currentLocation != null) {
			Intent intent = new Intent(this, MapActivity.class);
			intent.putExtra(EXTRA_LOCATION, currentLocation);
			intent.putExtra(EXTRA_ADDRESS, currentAddress);
			startActivity(intent);
		}
	}

	// ** Helpers **

	private void enableLocationSettings() {
		Intent settingsIntent = new Intent(
				Settings.ACTION_LOCATION_SOURCE_SETTINGS);
		startActivity(settingsIntent);
	}

	public static String formatAddress(Address address) {
		return String.format(
				"%s, %s, %s",
				address.getMaxAddressLineIndex() > 0 ? address
						.getAddressLine(0) : "",
				(address.getLocality() != null) ? address.getLocality() : "",
				address.getCountryName());
	}

	private void updateLocationLabels() {
		TextView coordinateLabel = (TextView) findViewById(R.id.textView1);
		TextView addressLabel = (TextView) findViewById(R.id.textView2);
		if (currentLocation != null) {
			coordinateLabel.setText(String.format("%f, %f",
					currentLocation.getLatitude(),
					currentLocation.getLongitude()));
		}
		if (currentAddress != null) {
			addressLabel.setText(formatAddress(currentAddress));
		}
	}

	// ** Location helpers **

	private void selectBestProvider() {
		provider = locationManager.getBestProvider(criteria, true);
		if (provider != null) {
			Location lastKnownLocation = locationManager
					.getLastKnownLocation(provider);
			if (lastKnownLocation != null)
				onLocationChanged(lastKnownLocation);
		} else {
			new AlertDialog.Builder(this)
					.setTitle(R.string.enable_location)
					.setMessage(R.string.message_enable_location)
					.setPositiveButton(R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									enableLocationSettings();
								}
							})
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							}).create().show();
		}
	}

	/**
	 * Determines whether one Location reading is better than the current
	 * Location fix
	 * 
	 * @param location
	 *            The new Location that you want to evaluate
	 * @param currentBestLocation
	 *            The current Location fix, to which you want to compare the new
	 *            one
	 */
	protected boolean isBetterLocation(Location location,
			Location currentBestLocation) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
		boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use
		// the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be
			// worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation
				.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(),
				currentBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and
		// accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isLessAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate
				&& isFromSameProvider) {
			return true;
		}
		return false;
	}

	/**
	 * Checks whether two providers are the same
	 */
	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) {
			return provider2 == null;
		}
		return provider1.equals(provider2);
	}

	// AsyncTask encapsulating the reverse-geocoding API. Since the geocoder API
	// is blocked,
	// we do not want to invoke it from the UI thread.
	private class ReverseGeocodingTask extends AsyncTask<Location, Void, Void> {
		Context mContext;

		public ReverseGeocodingTask(Context context) {
			super();
			mContext = context;
		}

		@Override
		protected Void doInBackground(Location... params) {
			Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
			Location loc = params[0];
			List<Address> addresses = null;

			try {
				// Call the synchronous getFromLocation() method by passing in
				// the lat/long values.
				addresses = geocoder.getFromLocation(loc.getLatitude(),
						loc.getLongitude(), 1);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (addresses != null && addresses.size() > 0) {
				currentAddress = addresses.get(0);
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			updateLocationLabels();
		}
	}

	// ** Menu overrides **

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_alert, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.menu_add_contacts:
			Intent contactsIntent = new Intent(this, ContactsActivity.class);
			startActivity(contactsIntent);
			return true;
		case R.id.menu_settings:
			Intent settingsIntent = new Intent(this, SettingsActivity.class);
			startActivity(settingsIntent);
			return true;
		case R.id.menu_help:
			Intent browserIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse(URL_HELP_ANDROID));
			startActivity(browserIntent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// ** Preferences **

	private boolean getBooleanPref(String key) {
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(this);
		return sharedPref.getBoolean(key, false);
	}

	// ** Contact list helpers **

	private void readContacts() {
		// Restore contacts from shared preferences
		SharedPreferences data = getSharedPreferences(PREFS_DATA, 0);
		String jsonContacts = data.getString(CONTACTS_KEY, "");
		selectedContacts = gson.fromJson(jsonContacts, contactsListType);
		if (selectedContacts == null) {
			selectedContacts = new ArrayList<Map<String, String>>();
		}
	}

}
