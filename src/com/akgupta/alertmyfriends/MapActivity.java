package com.akgupta.alertmyfriends;

import android.content.Intent;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends FragmentActivity {

	private static final String MAP_FRAGMENT_TAG = "map";
	private GoogleMap mMap;
	private SupportMapFragment mMapFragment;
	private Location currentLocation;
	private Address currentAddress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		// get location from alert activity
		Intent intent = getIntent();
		currentLocation = intent
				.getParcelableExtra(AlertActivity.EXTRA_LOCATION);
		currentAddress = intent.getParcelableExtra(AlertActivity.EXTRA_ADDRESS);

		// Map fragment
		// It isn't possible to set a fragment's id programmatically so we set a
		// tag instead and
		// search for it using that.
		mMapFragment = (SupportMapFragment) getSupportFragmentManager()
				.findFragmentByTag(MAP_FRAGMENT_TAG);

		// We only create a fragment if it doesn't already exist.
		if (mMapFragment == null) {
			// To programmatically add the map, we first create a
			// SupportMapFragment.
			mMapFragment = SupportMapFragment.newInstance();

			// Then we add it using a FragmentTransaction.
			FragmentTransaction fragmentTransaction = getSupportFragmentManager()
					.beginTransaction();
			fragmentTransaction.add(R.id.mapContainer, mMapFragment,
					MAP_FRAGMENT_TAG);
			fragmentTransaction.commit();
		}

		// We can't be guaranteed that the map is available because Google Play
		// services might
		// not be available.
		setUpMapIfNeeded();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// In case Google Play services has since become available.
		setUpMapIfNeeded();
	}

	// ** Map Helpers **
	private void setUpMapIfNeeded() {
		// Do a null check to confirm that we have not already instantiated the
		// map.
		if (mMap == null) {
			// Try to obtain the map from the SupportMapFragment.
			mMap = mMapFragment.getMap();
			// Check if we were successful in obtaining the map and set it up.
			setUpMap();
		}
	}

	private void setUpMap() {
		if (mMap != null && currentLocation != null) {
			mMap.clear();
			LatLng currentLatlng = new LatLng(currentLocation.getLatitude(),
					currentLocation.getLongitude());
			CameraPosition cameraPosition = new CameraPosition.Builder()
					.target(currentLatlng) // Sets the center of the map to
											// current location
					.zoom(15) // Sets the zoom
					.tilt(30) // Sets the tilt of the camera to 30 degrees
					.build(); // Creates a CameraPosition from the builder
			mMap.animateCamera(CameraUpdateFactory
					.newCameraPosition(cameraPosition));
			mMap.addMarker(new MarkerOptions().position(currentLatlng).title(
					(currentAddress != null) ? AlertActivity
							.formatAddress(currentAddress)
							: getString(R.string.current_location)));
		}
	}

}
