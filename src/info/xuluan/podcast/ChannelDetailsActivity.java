package info.xuluan.podcast;

import info.xuluan.podcast.provider.Subscription;
import info.xuluan.podcast.provider.SubscriptionColumns;
import info.xuluan.podcast.utils.Log;
import info.xuluan.podcastj.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class ChannelDetailsActivity extends HapiActivity {

	private Subscription mChannel;
	private Button episodes_btn;
	
	private final Log log = Log.getLog(getClass());

	private String getTimeString(long time){
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		Date date = new Date(time);
		return  formatter.format(date);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_details_activity);

		mChannel = getChannel();
		if (mChannel==null){
			finish();
			return;
		}
		Map<Integer,Integer> countsByStatusMap = mChannel.getEpisodeCounts(this);
		log.debug("Episode counts by status: "+countsByStatusMap);
		
		// set title:
		setTitle(mChannel.title);
		
		setViewContent(R.id.full_title_view, mChannel.title);
		setViewContent(R.id.url_view, mChannel.url);
		//setViewContent(R.id.link_view, "Link: "+ mChannel.link);
		setViewContent(R.id.description, mChannel.description);
		//setViewContent(R.id.comment, mChannel.comment);
		setViewContent(R.id.last_updated_view,
				"updated:"+getTimeString(mChannel.lastUpdated));
		setViewContent(R.id.last_item_updated_view,
				"item:"+getTimeString(mChannel.lastItemUpdated));
		setViewContent(R.id.fail_count_view,
				"fail_count:"+Long.toString(mChannel.failCount));
		setViewContent(R.id.auto_download_view,
				"auto download:"+(mChannel.autoDownload!=0?"yes":"no"));

		int episodeTotalCount = 0;
		ArrayList<Map<String,String>> countsAdapterList = new ArrayList<Map<String,String>>();
		int numberOfCounts = countsByStatusMap.size();
		Integer[] statusKeys = countsByStatusMap.keySet().toArray(new Integer[numberOfCounts]);
		Arrays.sort(statusKeys);
		for (Integer status : statusKeys) {
			final Integer count = countsByStatusMap.get(status);
			final int icon = EpisodeIcons.mapToIcon(status);
			countsAdapterList.add(new HashMap<String,String>() {{
				put("statusIcon",Integer.toString(icon));
				put("count",Integer.toString(count));
			}});
			episodeTotalCount += count;
		}

		String[] countsAdapterFrom = { "statusIcon", "count" };
		int[] countsAdapterTo = { R.id.icon, R.id.text1 };
		SimpleAdapter countsAdapter = new SimpleAdapter(this, countsAdapterList,
				R.layout.count_item, countsAdapterFrom, countsAdapterTo);
		LinearLayout countsByStatusList = (LinearLayout)findViewById(R.id.countsByStatus);
		for (int ii=0; ii<countsAdapterList.size(); ii++) {
			View listItem = countsAdapter.getView(ii,null,countsByStatusList);
			countsByStatusList.addView(listItem);
		}

		String episodeComment = "Number of episodes: "+episodeTotalCount;
		setViewContent(R.id.comment, episodeComment);
		episodes_btn = (Button) findViewById(R.id.ButtonEpisodes);			
		episodes_btn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Subscription.viewEpisodes(ChannelDetailsActivity.this,mChannel.id);
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		mChannel = getChannel();
		if (mChannel==null){
			finish();
			return;
		}
		
		setStatusIcon();
	}

	private void setViewContent(int viewId, String content) {
		TextView v = (TextView) findViewById(viewId);
		v.setText(content);
	}

	private void setStatusIcon() {
		ImageView statusIconView = (ImageView) findViewById(R.id.suspended_icon);
		if (mChannel.suspended!=0) {
			statusIconView.setImageResource(R.drawable.suspended);
		} else {
			statusIconView.setImageResource(R.drawable.blank);
		}
	}
	
	private Subscription getChannel() {
		Intent intent = getIntent();

		Uri uri = intent.getData();
		Cursor cursor = getContentResolver().query(uri,
				SubscriptionColumns.ALL_COLUMNS, null, null, null);
		if (!cursor.moveToFirst()) {
			// show404();
			return null;
		}		
		return Subscription.getByCursor(cursor);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.channel_details_activity, menu);
        return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		mChannel = getChannel();
		if (!BackupChannelsActivity.importExportZipEabled) {
	    	menu.findItem(R.id.exportAllToZip).setVisible(false);
	    	menu.findItem(R.id.exportUnplayedToZip).setVisible(false);
		}
		setStatusIcon();
        return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.exportAllToZip:
			mChannel.exportAllToZipFile(this);
			return true;
			
		case R.id.exportUnplayedToZip:
			mChannel.exportUnplayedToZipFile(this);
			return true;
			
		}
		setStatusIcon();
		return super.onOptionsItemSelected(item);
	}
}
