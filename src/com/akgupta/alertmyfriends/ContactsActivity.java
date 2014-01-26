package com.akgupta.alertmyfriends;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ContactsActivity extends ListActivity {

	private static final String DEBUG_TAG = "ContactsActivity";
	private static final int CONTACT_PICKER_RESULT = 1001;
	private static final String PREFS_DATA = "data";
	private static final String CONTACTS_KEY = "contacts";
	private static final String NAME = "name";
	private static final String PHONE = "phone";

	private ArrayList<Map<String, String>> selectedContacts;
	private SimpleAdapter adapter;
	private Gson gson;
	private Type contactsListType;

	// ** Activity overrides **

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_contacts);

		gson = new Gson();
		contactsListType = new TypeToken<ArrayList<Map<String, String>>>() {
		}.getType();

		// Contact list
		// Restore contacts from preferences
		readContacts();
		// Set list adapter
		String[] from = { NAME, PHONE };
		int[] to = { android.R.id.text1, android.R.id.text2 };

		adapter = new SimpleAdapter(this, selectedContacts,
				android.R.layout.simple_list_item_2, from, to);
		setListAdapter(adapter);
		registerForContextMenu(getListView());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case CONTACT_PICKER_RESULT:
				if (data != null) {
					Uri uri = data.getData();

					if (uri != null) {
						Cursor c = null;
						try {
							String[] projection = new String[] {
									ContactsContract.Contacts.DISPLAY_NAME,
									ContactsContract.CommonDataKinds.Phone.NUMBER,
									ContactsContract.CommonDataKinds.Phone.TYPE };
							c = getContentResolver().query(uri, projection,
									null, null, null);

							if (c != null && c.moveToFirst()) {
								String name = c
										.getString(c
												.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
								String phone = c
										.getString(c
												.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
								addSelectedContact(name, phone);
							}
						} finally {
							if (c != null) {
								c.close();
							}
						}
					}
				}
				break;
			}
		} else {
			Log.d(DEBUG_TAG, "Warning: activity result not ok");
		}
	}

	// ** Action handlers **

	public void doLaunchContactPicker() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
		startActivityForResult(intent, CONTACT_PICKER_RESULT);
	}

	// ** Contact list helpers **

	private void addSelectedContact(String name, String phone) {
		if (phone == null) {
			Toast.makeText(ContactsActivity.this, R.string.invalid_contact,
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (name == null) {
			name = "";
		}
		HashMap<String, String> item = new HashMap<String, String>();
		item.put(NAME, name);
		item.put(PHONE, phone);
		selectedContacts.add(item);
		adapter.notifyDataSetChanged();
		saveContacs();
	}

	private void deleteContact(int index) {
		selectedContacts.remove(index);
		adapter.notifyDataSetChanged();
		saveContacs();
	}

	private void saveContacs() {
		String jsonContacts = gson.toJson(selectedContacts, contactsListType);
		SharedPreferences data = getSharedPreferences(PREFS_DATA, 0);
		SharedPreferences.Editor editor = data.edit();
		editor.putString(CONTACTS_KEY, jsonContacts);
		// Commit the edits!
		editor.commit();
	}

	private void readContacts() {
		// Restore contacts from shared preferences
		SharedPreferences data = getSharedPreferences(PREFS_DATA, 0);
		String jsonContacts = data.getString(CONTACTS_KEY, "");
		selectedContacts = gson.fromJson(jsonContacts, contactsListType);
		if (selectedContacts == null) {
			selectedContacts = new ArrayList<Map<String, String>>();
		}
	}

	// ** Menu overrides **

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_contacts, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.menu_add:
			doLaunchContactPicker();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.delete:
			deleteContact(info.position);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}
}
