/*
 * Copyright (C) 2007 The Android Open Source Project
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

package info.xuluan.podcast;

import info.xuluan.podcastj.R;
import info.xuluan.podcast.service.PodcastService;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;

public class Pref extends HapiPreferenceActivity {

	public static final String HAPI_PREFS_FILE_NAME = "info.xuluan.podcastj_preferences";
		//Default filename is our package name (see manifest) with _preferences appended
	private PodcastService serviceBinder = null;
	ComponentName service = null;

	private ServiceConnection serviceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			serviceBinder = ((PodcastService.PodcastBinder) service)
					.getService();
		}

		public void onServiceDisconnected(ComponentName className) {
			serviceBinder = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);

		service = startService(new Intent(this, PodcastService.class));

		Intent bindIntent = new Intent(this, PodcastService.class);
		bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		super.onResume();

	}

	@Override
	protected void onPause() {

		super.onPause();
		if(serviceBinder!=null)
			serviceBinder.updateSetting();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
		// stopService(new Intent(this, service.getClass()));
	}

}
