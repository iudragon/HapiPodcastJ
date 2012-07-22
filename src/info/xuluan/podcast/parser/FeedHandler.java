package info.xuluan.podcast.parser;

import info.xuluan.podcastj.R;
import info.xuluan.podcast.fetcher.FeedFetcher;
import info.xuluan.podcast.fetcher.Response;
import info.xuluan.podcast.provider.FeedItem;
import info.xuluan.podcast.provider.ItemColumns;
import info.xuluan.podcast.provider.Subscription;
import info.xuluan.podcast.utils.Log;

import java.io.ByteArrayInputStream;


import android.content.ContentResolver;
import android.database.Cursor;

public class FeedHandler  {
	private static final int REPEAT_UPDATE_FEED_COUNT = 3;
	private final Log log = Log.getLog(getClass());
	
	private ContentResolver cr;
	private int max_valid_size;
	
	public FeedHandler(ContentResolver context, int max_valid_sz){
		cr = context;
		max_valid_size = max_valid_sz;
	}

	public int update(Subscription sub){
		FeedParserListener listener = fetchFeed(sub.url,sub.id);
		if ((listener != null) && (listener.resultCode==0))
			return updateFeed(sub, listener);


		updateFail(sub);
		return 0;
	}
	
	public FeedParserListener fetchFeed(String url, long sub_id) {

		FeedFetcher fetcher = new FeedFetcher();
		FeedParserListener listener = new FeedParserListener(max_valid_size);
		FeedParserHandler handler = new FeedParserHandler(listener, sub_id);
		Response response = null;
		try {
			response = fetcher.fetch(url);

			if (response != null)
				FeedParser.getDefault().parse(
						new ByteArrayInputStream(response.getContentAsString().getBytes()), handler);
			else{
				log.debug("response == null");
				listener.resultCode = R.string.network_fail;
				return listener;
			}

		} catch (Exception e) {
			if(listener.getFeedItemsSize()==0){

				listener.resultCode = R.string.feed_format_error;
				return listener;
			}
		}

		log.debug("fetchFeed getFeedItemsSize = "
						+ listener.getFeedItemsSize());

		if (listener.getFeedItemsSize() <= 0) {
			listener.resultCode = R.string.no_new_items;
		}else{
			listener.resultCode = 0;
		}
		return listener;
	}
	
	public void updateFail(Subscription sub) {
				
		if(sub.fail_count<REPEAT_UPDATE_FEED_COUNT){
			sub.fail_count++;
		}else{
			sub.fail_count=0;
		}
		sub.update(cr);
	}
	
	public int updateFeed(Subscription subscription, FeedParserListener listener) {
		FeedItem[] feedItems = listener.getSortItems();
		log.debug("fetchFeed getSortItemsSize = "
						+ feedItems.length);			 
		long update_date = subscription.lastItemUpdated;
		int add_num = 0;

		for (FeedItem item : feedItems) {
			//Ignore items that are older than the most recently added batch
			long d = item.getDate();
			if (d <= subscription.lastItemUpdated) {
				continue;
			}

			if(d>update_date){
				update_date = d;
			}
			addItem(subscription, item);
			add_num++;

		}
		
		subscription.fail_count = 0;
		subscription.title = listener.getFeedTitle();
		subscription.description = listener.getFeedDescription();
		subscription.lastItemUpdated = update_date;
		subscription.update(cr);
		log.debug("add url: "+subscription.url+"\n add num = "+add_num);
		return add_num;
		
	}
	
	private void addItem(Subscription subscription, FeedItem item){
		Long sub_id = subscription.id;

		item.sub_id = sub_id;
		if(subscription.auto_download>0){
			item.status = ItemColumns.ITEM_STATUS_DOWNLOAD_QUEUE;
		}
		
		String where = ItemColumns.SUBS_ID + "=" + sub_id + " and "
				+ ItemColumns.RESOURCE + "= '" + item.resource + "'";

		Cursor cursor = cr.query(ItemColumns.URI,
				new String[] { ItemColumns._ID }, where, null, null);

		if (cursor.moveToFirst()) {
		} else {
			item.insert(cr);
		}

		if(cursor!=null)
			cursor.close();			

	}	
}
