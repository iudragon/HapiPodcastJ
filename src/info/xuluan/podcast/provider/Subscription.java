package info.xuluan.podcast.provider;


import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

public class Subscription {
	
	public final static int ADD_SUCCESS = 0;
	public final static int ADD_FAIL_DUP = -1;
	public final static int ADD_FAIL_UNSUCCESS = -2;
	
	
	public long id;
	public String title;
	public String link;
	public String comment;

	public String url;
	public String description;
	public long lastUpdated;
	public long lastItemUpdated;
	public long fail_count;
	public long auto_download;
	public long suspended;

	public static void view(Activity act, long channel_id) {
		Uri uri = ContentUris.withAppendedId(SubscriptionColumns.URI, channel_id);
		//Subscription channel = Subscription.getById(act.getContentResolver(), channel_id);
		act.startActivity(new Intent(Intent.ACTION_VIEW, uri));
	}

	public static void viewEpisodes(Activity act, long channel_id) {
		Uri uri = ContentUris.withAppendedId(SubscriptionColumns.URI, channel_id);
		act.startActivity(new Intent(Intent.ACTION_EDIT, uri));
	}

	public static Subscription getBySQL(ContentResolver context,String where,String order) 
	{
		Subscription sub = null;
		Cursor cursor = null;
	
		try {		
			cursor = context.query(SubscriptionColumns.URI,
					SubscriptionColumns.ALL_COLUMNS, where, null, order);
			if (cursor.moveToFirst()) {
				sub =Subscription.getByCursor(cursor);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}		
		return sub;			
	}
	
	public static Subscription getByUrl(ContentResolver context, String url) {
		Cursor cursor = null;
		try {
			cursor = context.query(SubscriptionColumns.URI,
					SubscriptionColumns.ALL_COLUMNS, SubscriptionColumns.URL
							+ "=?", new String[] { url }, null);
			if (cursor.moveToFirst()) {
				Subscription sub = new Subscription();
				fetchFromCursor(sub, cursor);
				cursor.close();
				return sub;
			}
		} catch (Exception e) {
			e.printStackTrace();

		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;

	}

	public static Subscription getByCursor(Cursor cursor) {
		//if (cursor.moveToFirst() == false)
		//	return null;
		Subscription sub = new Subscription();
		fetchFromCursor(sub, cursor);
		return sub;
	}

	public static Subscription getById(ContentResolver context, long id) {
		Cursor cursor = null;
		Subscription sub = null;

		try {
			String where = SubscriptionColumns._ID + " = " + id;

			cursor = context.query(SubscriptionColumns.URI,
					SubscriptionColumns.ALL_COLUMNS, where, null, null);
			if (cursor.moveToFirst()) {
				sub = new Subscription();
				fetchFromCursor(sub, cursor);
				cursor.close();
			}
		} catch (Exception e) {
			e.printStackTrace();

		} finally {
			if (cursor != null)
				cursor.close();
		}

		return sub;

	}
	
	private void init() {
		id = -1;
		title = null;
		url = null;
		link = null;
		comment = "";
		description = null;
		lastUpdated = -1;
		fail_count = -1;
		lastItemUpdated = -1;
		auto_download = -1;
		suspended = -1;
	}
	
	public Subscription() {
		init();
	}
	
	public Subscription(String url_link) {
		
		init();
		url = url_link;
		title = url_link;
		link = url_link;

	}	
	
	public int subscribe(ContentResolver context){
		Subscription sub = Subscription.getByUrl(
				context, url);
		if (sub != null) {
			return ADD_FAIL_DUP;
		}



		ContentValues cv = new ContentValues();
		cv.put(SubscriptionColumns.TITLE, title);
		cv.put(SubscriptionColumns.URL, url);
		cv.put(SubscriptionColumns.LINK, link);
		cv.put(SubscriptionColumns.LAST_UPDATED, 0L);
		cv.put(SubscriptionColumns.COMMENT, comment);
		cv.put(SubscriptionColumns.DESCRIPTION, description);
		Uri uri = context.insert(SubscriptionColumns.URI, cv);
		if (uri == null) {
			return ADD_FAIL_UNSUCCESS;
		}
		
		return ADD_SUCCESS;
			
	}

	public void delete(ContentResolver context) {
		Uri uri = ContentUris.withAppendedId(SubscriptionColumns.URI, id);
		context.delete(uri, null, null);
	}

	public int update(ContentResolver context) {
		try {

			ContentValues cv = new ContentValues();
			if (title != null)
				cv.put(SubscriptionColumns.TITLE, title);
			if (url != null)
				cv.put(SubscriptionColumns.URL, url);
			if (description != null)
				cv.put(SubscriptionColumns.DESCRIPTION, description);

			if(fail_count<=0){
				lastUpdated = Long.valueOf(System.currentTimeMillis());
			}else{
				lastUpdated = 0;
			}
				cv.put(SubscriptionColumns.LAST_UPDATED, lastUpdated);
			
			if (fail_count >= 0)
				cv.put(SubscriptionColumns.FAIL_COUNT, fail_count);

			if (lastItemUpdated >= 0)
				cv.put(SubscriptionColumns.LAST_ITEM_UPDATED, lastItemUpdated);

			if (auto_download >= 0)
				cv.put(SubscriptionColumns.AUTO_DOWNLOAD, auto_download);
			
			if (suspended >= 0)
				cv.put(SubscriptionColumns.SUSPENDED, suspended);
			
			return context.update(SubscriptionColumns.URI, cv,
					SubscriptionColumns._ID + "=" + id, null);

		} finally {
		}
	}

	private static void fetchFromCursor(Subscription sub, Cursor cursor) {
		//assert cursor.moveToFirst();
		//cursor.moveToFirst();
		sub.id = cursor.getLong(cursor.getColumnIndex(SubscriptionColumns._ID));
		sub.lastUpdated = cursor.getLong(cursor
				.getColumnIndex(SubscriptionColumns.LAST_UPDATED));
		sub.title = cursor.getString(cursor
				.getColumnIndex(SubscriptionColumns.TITLE));
		sub.url = cursor.getString(cursor
				.getColumnIndex(SubscriptionColumns.URL));		
		sub.comment = cursor.getString(cursor
				.getColumnIndex(SubscriptionColumns.COMMENT));		
		sub.description = cursor.getString(cursor
				.getColumnIndex(SubscriptionColumns.DESCRIPTION));		
		sub.fail_count = cursor.getLong(cursor
				.getColumnIndex(SubscriptionColumns.FAIL_COUNT));
		sub.lastItemUpdated = cursor.getLong(cursor
				.getColumnIndex(SubscriptionColumns.LAST_ITEM_UPDATED));
		sub.auto_download = cursor.getLong(cursor
				.getColumnIndex(SubscriptionColumns.AUTO_DOWNLOAD));
		sub.suspended = cursor.getLong(cursor
				.getColumnIndex(SubscriptionColumns.SUSPENDED));
	}

}
