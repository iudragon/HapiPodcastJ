package info.xuluan.podcast.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import info.xuluan.podcast.R;
import info.xuluan.podcast.fetcher.FeedFetcher;
import info.xuluan.podcast.fetcher.Response;
import info.xuluan.podcast.parser.FeedParser;
import info.xuluan.podcast.parser.FeedParserHandler;
import info.xuluan.podcast.parser.FeedParserListenerAdapter;

import info.xuluan.podcast.provider.FeedItem;
import info.xuluan.podcast.provider.ItemColumns;
import info.xuluan.podcast.provider.Subscription;
import info.xuluan.podcast.provider.SubscriptionColumns;
import info.xuluan.podcast.utils.Log;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.widget.Toast;

public class PodcastService extends Service {

	private static final int ERR_CODE_NO_ERROR = 0;
	public static final int NO_CONNECT = 1;
	public static final int WIFI_CONNECT = 2;
	public static final int MOBILE_CONNECT = 4;

	public static final int MAX_DOWNLOAD_FAIL = 5;

	private static final int MSG_TIMER = 0;
	
	private static final int REPEAT_UPDATE_FEED_COUNT = 3;
	

	private static final long ONE_MINUTE = 60L * 1000L;
	private static final long ONE_HOUR = 60L * ONE_MINUTE;
	private static final long ONE_DAY = 24L * ONE_HOUR;

	private static final long timer_freq = 3 * ONE_MINUTE;

	private long pref_update = 2 * 60 * ONE_MINUTE;

	public int pref_connection_sel = WIFI_CONNECT;

	public long pref_update_wifi = 0;
	public long pref_update_mobile = 0;
	public long pref_item_expire = 0;
	public long pref_download_file_expire = 0;
	public long pref_played_file_expire = 0;

	private static int mErrCode;

	private static boolean mDownloading = false;
	private FeedItem mDownloadingItem = null;
	private static final ReentrantReadWriteLock mDownloadLock = new ReentrantReadWriteLock();

	private static boolean mUpdate = false;
	private static final ReentrantReadWriteLock mUpdateLock = new ReentrantReadWriteLock();
	private static int mConnectStatus = NO_CONNECT;
	
	public static String SDCARD_DIR = "/sdcard"; 
	public static final String APP_DIR = "/xuluan.podcast";
	public static final String DOWNLOAD_DIR = "/download";
	//public static final String BASE_DOWNLOAD_DIRECTORY = "/sdcard/xuluan.podcast/download";
	
	

	public static final String UPDATE_DOWNLOAD_STATUS = PodcastService.class
			.getName()
			+ ".UPDATE_DOWNLOAD_STATUS";

	private final Log log = Log.getLog(getClass());


	private final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_TIMER:
				log.debug("Message: MSG_TIMER.");

				start_update();
				removeExpires();
				do_download(false);

				triggerNextTimer(timer_freq);

				break;
			}
		}
	};

	void triggerNextTimer(long delay) {
		Message msg = Message.obtain();
		msg.what = MSG_TIMER;
		handler.sendMessageDelayed(msg, delay);
	}
	
	private boolean getSDCardStatus()
	{
		if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
			createDir();
			return true;
		}else{
			return false;
		}

	}

	private boolean createDir()
	{
		File file = new File(getDownloadDir());
		boolean exists = (file.exists());
		if (!exists) {
			return file.mkdirs();
		}
		return true;
	}	
	
	private String getDownloadDir()
	{
		File sdDir = new File(Environment.getExternalStorageDirectory().getPath());
		SDCARD_DIR = sdDir.getAbsolutePath();
		log.debug("getDownloadDir: " + SDCARD_DIR + APP_DIR + DOWNLOAD_DIR);
		return SDCARD_DIR + APP_DIR + DOWNLOAD_DIR;
	}
	
	private int updateConnectStatus() {
		log.debug("updateConnectStatus");
		try {

			ConnectivityManager cm = (ConnectivityManager) this
					.getSystemService(CONNECTIVITY_SERVICE);
			NetworkInfo info = cm.getActiveNetworkInfo();
			if (info == null) {
				mConnectStatus = NO_CONNECT;
				return mConnectStatus;

			}

			if (info.isConnected() && (info.getType() == 1)) {
				mConnectStatus = WIFI_CONNECT;
				pref_update = pref_update_wifi;

				return mConnectStatus;
			} else {
				mConnectStatus = MOBILE_CONNECT;
				pref_update = pref_update_mobile;

				return mConnectStatus;
			}
		} catch (Exception e) {
			e.printStackTrace();
			mConnectStatus = NO_CONNECT;

			return mConnectStatus;
		}

	}

	private String findSubscriptionUrlByFreq() {
		Cursor cursor = null;
		try {
			Long now = Long.valueOf(System.currentTimeMillis());
			log.debug("pref_update = " + pref_update);

			String where = SubscriptionColumns.LAST_UPDATED + "<"
					+ (now - pref_update);
			String order = SubscriptionColumns.LAST_UPDATED + " ASC,"
			+ SubscriptionColumns.FAIL_COUNT +" ASC";
			
			cursor = getContentResolver().query(SubscriptionColumns.URI,
					SubscriptionColumns.ALL_COLUMNS, where, null, order);
			if (cursor.moveToFirst()) {

				String url = cursor.getString(cursor
						.getColumnIndex(SubscriptionColumns.URL));
				cursor.close();
				log.debug("findSubscriptionUrlByFreq OK : "+url);
				return url;

			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;

	}

	private FeedItem getDownloadItem() {
		Cursor cursor = null;
		try {
			String where = ItemColumns.STATUS + ">"
					+ ItemColumns.ITEM_STATUS_DOWNLOAD_PAUSE + " AND "
					+ ItemColumns.STATUS + "<"
					+ ItemColumns.ITEM_STATUS_MAX_DOWNLOADING_VIEW;

			cursor = getContentResolver().query(
					ItemColumns.URI,
					ItemColumns.ALL_COLUMNS,
					where,
					null,
					ItemColumns.STATUS + " DESC , " + ItemColumns.LAST_UPDATE
							+ " ASC");
			if (cursor.moveToFirst()) {
				FeedItem item = FeedItem.getByCursor(cursor);
				cursor.close();
				return item;
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;

	}

	
	public FeedItem getDownloadingItem() {
		return mDownloadingItem;
	}


	public int getErrCode() {
		return mErrCode;
	}

	
	public void start_update() {
		if (updateConnectStatus() == NO_CONNECT)
			return;

		log.debug("start_update()");
		mUpdateLock.readLock().lock();
		if (mUpdate) {
			log.debug("mUpdate = true ");
			mUpdateLock.readLock().unlock();
			return;

		}
		mUpdateLock.readLock().unlock();

		mUpdateLock.writeLock().lock();
		mUpdate = true;
		mUpdateLock.writeLock().unlock();

		new Thread() {
			public void run() {
				try {

					String url = findSubscriptionUrlByFreq();
					while (url != null) {
						FeedParserListenerAdapter listener = fetchFeed(url);
						if (listener != null)
							updateFeed(url, listener);
						else
							updateFetch(url);

						url = findSubscriptionUrlByFreq();
					}

				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					mUpdateLock.writeLock().lock();
					log.debug("mUpdate = false ");

					mUpdate = false;
					mUpdateLock.writeLock().unlock();
				}

			}
		}.start();
	}

	public void start_download() {


		
		 do_download(true);
		
	}
	private void do_download(boolean show){
		if (getSDCardStatus()==false){
			
			if(show)
				Toast.makeText(this, getResources().getString(R.string.sdcard_unmout), Toast.LENGTH_LONG).show();
			return;
		}

		
		if (updateConnectStatus() == NO_CONNECT){
			if(show)
				Toast.makeText(this, getResources().getString(R.string.no_connect), Toast.LENGTH_LONG).show();
			return;
		}
		mDownloadLock.readLock().lock();
		if (mDownloading) {
			mDownloadLock.readLock().unlock();
			return;

		}
		mDownloadLock.readLock().unlock();

		mDownloadLock.writeLock().lock();
		mDownloading = true;
		mDownloadLock.writeLock().unlock();

		new Thread() {
			public void run() {
				try {
					while ((updateConnectStatus() & pref_connection_sel) > 0) {

						mDownloadingItem = getDownloadItem();

						if (mDownloadingItem == null) {
							break;
						}

						/* ONLY_TEST
						
						File file = new File(getDownloadDir());
						if (file.exists()==false) {
							break;
						}
						*/

						// log.debug("start_download start");
						if (mDownloadingItem.pathname.equals("")) {
							String path_name = getDownloadDir()
									+ "/podcast_" + mDownloadingItem.id + ".mp3";
							mDownloadingItem.pathname = path_name;
						}

						// if(MAX_DOWNLOAD_FAIL<item.failcount){

						
						try {
							mDownloadingItem.status = ItemColumns.ITEM_STATUS_DOWNLOADING_NOW;
							mDownloadingItem.update(getContentResolver());
							FeedFetcher fetcher = new FeedFetcher();

							fetcher.download(mDownloadingItem);

						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							if (mDownloadingItem.status != ItemColumns.ITEM_STATUS_NO_PLAY) {
								mDownloadingItem.status = ItemColumns.ITEM_STATUS_DOWNLOAD_QUEUE;
							}

						}
						// }
						log.debug(mDownloadingItem.title + "  " + mDownloadingItem.length + "  "
								+ mDownloadingItem.offset);

						if (mDownloadingItem.status == ItemColumns.ITEM_STATUS_NO_PLAY) {
							mDownloadingItem.update = Long.valueOf(System.currentTimeMillis());
							mDownloadingItem.failcount = 0;
							mDownloadingItem.offset = 0;

						} else {
							mDownloadingItem.failcount++;
							if (mDownloadingItem.failcount > MAX_DOWNLOAD_FAIL) {
								mDownloadingItem.status = ItemColumns.ITEM_STATUS_DOWNLOAD_PAUSE;
								mDownloadingItem.failcount = 0;
							}
						}

						mDownloadingItem.update(getContentResolver());

					}

					// log.debug("start_download end");
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					mDownloadLock.writeLock().lock();
					mDownloading = false;
					mDownloadingItem = null;
					mDownloadLock.writeLock().unlock();
				}

			}

		}.start();
	}


	private void deleteExpireFile(Cursor cursor) {
		
		if(cursor==null)
			return;
		
		if (cursor.moveToFirst()) {
			do{
				FeedItem item = FeedItem.getByCursor(cursor);
				if(item!=null){
					item.delFile(getContentResolver());
				}
			}while (cursor.moveToNext());
		}
		cursor.close();
		
	}

	private void removeExpires() {
		long expiredTime = System.currentTimeMillis() - pref_item_expire;
		try {
			String where = ItemColumns.CREATED + "<" + expiredTime + " and "
					+ ItemColumns.STATUS + "<"
					+ ItemColumns.ITEM_STATUS_MAX_READING_VIEW;

			getContentResolver().delete(ItemColumns.URI, where, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (getSDCardStatus()==false){
			return;
		}

		expiredTime = System.currentTimeMillis() - pref_played_file_expire;
		try {
			String where = ItemColumns.LAST_UPDATE + "<" + expiredTime
					+ " and " + ItemColumns.STATUS + "="
					+ ItemColumns.ITEM_STATUS_PLAYED;

			Cursor cursor = getContentResolver().query(ItemColumns.URI,
					ItemColumns.ALL_COLUMNS, where, null, null);
			deleteExpireFile(cursor);

		} catch (Exception e) {
			e.printStackTrace();
		}

		expiredTime = System.currentTimeMillis() - pref_download_file_expire;
		try {
			String where = ItemColumns.LAST_UPDATE + "<" + expiredTime
					+ " and " + ItemColumns.STATUS + "="
					+ ItemColumns.ITEM_STATUS_NO_PLAY;

			Cursor cursor = getContentResolver().query(ItemColumns.URI,
					ItemColumns.ALL_COLUMNS, where, null, null);
			deleteExpireFile(cursor);

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			String where = ItemColumns.STATUS + "="
					+ ItemColumns.ITEM_STATUS_DELETE;

			Cursor cursor = getContentResolver().query(ItemColumns.URI,
					ItemColumns.ALL_COLUMNS, where, null, null);
			deleteExpireFile(cursor);

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		String where = ItemColumns.STATUS + "="
		+ ItemColumns.ITEM_STATUS_DELETED;		
		getContentResolver().delete(ItemColumns.URI, where, null);

	}

	public FeedParserListenerAdapter fetchFeed(String url) {
		mErrCode = 0;
		log.debug("fetchFeed start");

		FeedFetcher fetcher = new FeedFetcher();
		FeedParserListenerAdapter listener = new FeedParserListenerAdapter();
		FeedParserHandler handler = new FeedParserHandler(listener);

		try {
			Response response = fetcher.fetch(url);

			log.debug("fetcher.fetch end");
			if (response != null)
				FeedParser.getDefault().parse(
						new ByteArrayInputStream(response.content), handler);
			else{
				log.debug("response == null");
				mErrCode = R.string.network_fail;
			}

		} catch (Exception e) {
			mErrCode = R.string.feed_format_error;

			log.debug("Parse XML error:", e);
			// e.printStackTrace();

		}

		log.debug("fetchFeed getFeedItemsSize = "
						+ listener.getFeedItemsSize());

		if (listener.getFeedItemsSize() > 0) {
			return listener;
		}

		return null;
	}

	private void updateFetch(String url) {

		log.debug("updateFetch start");
		try {

				Subscription sub = Subscription.getByUrl(getContentResolver(),
						url);
				
				if(sub==null)
					return;
				
				if(sub.fail_count<REPEAT_UPDATE_FEED_COUNT){
					sub.fail_count++;
				}else{
					sub.fail_count=0;
				}
				sub.update(getContentResolver());
				
				log.debug("updateFetch OK : "+url);

		} finally {

		}
	}

	public void updateFeed(String url, FeedParserListenerAdapter listener) {
		// sort feed items:
		String feedTitle = listener.getFeedTitle();
		String feedDescription = listener.getFeedDescription();
		FeedItem[] feedItems = listener.getSortItems();
		log.debug("updateFeed start:"+url);
		/*
		 * for (FeedItem item : feedItems) {
		 * 
		 * log.warn("item_date: " + item.date); }
		 */
		
		/*
		Arrays.sort(feedItems, new Comparator<FeedItem>() {
			public int compare(FeedItem i1, FeedItem i2) {
				long d1 = i1.getDate();
				long d2 = i2.getDate();

				if (d1 == d2)
					return i1.title.compareTo(i2.title);
				return d1 > d2 ? (-1) : 1;
			}
		});
		
		*/
		
		/*
		 * for (FeedItem item : feedItems) {
		 * 
		 * log.warn("item_date: " + item.date); }
		 */
		Subscription subscription = Subscription.getByUrl(getContentResolver(),
				url);

		if (subscription == null)
			return;

		log.debug("feedItems length: " + feedItems.length);

		List<FeedItem> added = new ArrayList<FeedItem>(feedItems.length);
		for (FeedItem item : feedItems) {
			long d = item.getDate();
			log.debug("item_date: " + item.date);

			if (d <= subscription.lastItemUpdated) {
				log.debug("item lastUpdated =" + d + " feed lastUpdated = "
						+ subscription.lastUpdated);
				log.debug("item date =" + item.date);
				continue;
			}
			log.debug("subscription.id : " + subscription.id);

			log.debug("add item = " + item.title);
			added.add(0,item);

		}
		log.debug("added size: " + added.size());
		subscription.fail_count = 0;
		subscription.update(getContentResolver());
		subscription.title = feedTitle;
		subscription.description = feedDescription;

		if (!added.isEmpty()) {
			subscription.lastItemUpdated = added.get(0).getDate();
			log.debug("MAX item date:==" + added.get(0).date);
		}

		int n = subscription.update(getContentResolver());
		if (n == 1) {
			log.debug("Feed updated: " + url);
		}
		if (added.isEmpty())
			return;
		addItems(subscription, added);

	}

	void addItems(Subscription subscription, List<FeedItem> items) {
		Long sub_id = subscription.id;
		ContentResolver cr = getContentResolver();

		for (FeedItem item : items) {

			item.sub_id = sub_id;
			if(subscription.auto_download>0){
				item.status = ItemColumns.ITEM_STATUS_DOWNLOAD_QUEUE;
			}
			String where = ItemColumns.SUBS_ID + "=" + sub_id + " and "
					+ ItemColumns.RESOURCE + "= '" + item.resource + "'";

			Cursor cursor = cr.query(ItemColumns.URI,
					new String[] { ItemColumns._ID }, where, null, null);

			if (cursor.moveToFirst()) {
				cursor.close();
				log.debug("dup resource: " + where);

				continue;
			} else {
				if(cursor!=null)
					cursor.close();
				Uri uri = item.insert(cr);
				if (uri != null)
					log.debug("Inserted new item: " + uri.toString());
			}

		}
		
		if(subscription.auto_download>0){
			do_download(false);
		}
	}


	@Override
	public void onCreate() {
		super.onCreate();
		log.debug("onCreate()");
		updateSetting();
		log.debug("pref_update_mobile " + pref_update_mobile);

		getSDCardStatus();

		triggerNextTimer(1);

	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		log.debug("onStart()");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		log.debug("onDestroy()");
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		log.debug("onLowMemory()");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	private final IBinder binder = new PodcastBinder();

	public class PodcastBinder extends Binder {
		public PodcastService getService() {
			return PodcastService.this;
		}
	}

	public void updateSetting() {
		SharedPreferences pref = getSharedPreferences(
				"info.xuluan.podcast_preferences", Service.MODE_PRIVATE);

		boolean b = pref.getBoolean("pref_download_only_wifi", true);
		pref_connection_sel = b ? WIFI_CONNECT
				: (WIFI_CONNECT | MOBILE_CONNECT);

		pref_update_wifi = Integer.parseInt(pref.getString("pref_update_wifi",
				"60"));
		pref_update_wifi *= ONE_MINUTE;

		pref_update_mobile = Integer.parseInt(pref.getString(
				"pref_update_mobile", "120"));
		pref_update_mobile *= ONE_MINUTE;

		pref_item_expire = Integer.parseInt(pref.getString("pref_item_expire",
				"7"));
		pref_item_expire *= ONE_DAY;
		pref_download_file_expire = Integer.parseInt(pref.getString(
				"pref_download_file_expire", "7"));
		pref_download_file_expire *= ONE_DAY;
		pref_played_file_expire = Integer.parseInt(pref.getString(
				"pref_played_file_expire", "24"));
		pref_played_file_expire *= ONE_HOUR;

	}

}