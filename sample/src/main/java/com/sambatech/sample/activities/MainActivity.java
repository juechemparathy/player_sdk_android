package com.sambatech.sample.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Toast;

import com.sambatech.player.model.SambaMediaRequest;
import com.sambatech.sample.R;
import com.sambatech.sample.adapters.MediasAdapter;
import com.sambatech.sample.model.LiquidMedia;
import com.sambatech.sample.rest.LiquidApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import butterknife.Bind;
import butterknife.BindString;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import de.greenrobot.event.EventBus;
import retrofit.Call;
import retrofit.Callback;
import retrofit.Retrofit;

public class MainActivity extends Activity {

	//Simple map with player hashs and their pids
	private Map<Integer, String> phMap;

	@Bind(R.id.media_list) ListView list;
	@Bind(R.id.progressbar_view) LinearLayout loading;

	//Samba api and Json Tag endpoints
	@BindString(R.string.svapi_stage) String svapi_stage;
	@BindString(R.string.svapi_dev) String svapi_dev;
	@BindString(R.string.mysjon_endpoint) String tag_endpoint;
	@BindString(R.string.svapi_token_prod) String svapi_token_prod;
	@BindString(R.string.svapi_token_dev) String svapi_token_dev;

	MediasAdapter mAdapter;
	Menu menu;

	Boolean adEnabled = false;

	//Array to store requested medias
	ArrayList<LiquidMedia> mediaList;
	//Array to store medias with ad tags
	ArrayList<LiquidMedia> adMediaList;
	//Controls loading
	private Boolean loadingFlag;
	private boolean _autoPlay = true;
	private Drawable _autoPlayIcon;
	private static final int _autoPlayColor = 0xff99ccff;
	private static final int _autoPlayColorDisabled = 0xff999999;

	@OnItemClick(R.id.media_list) public void mediaItemClick(int position) {

		if(loading.getVisibility() == View.VISIBLE) return;

		LiquidMedia media = (LiquidMedia) mAdapter.getItem(position);
		media.highlighted = position == 0;

		EventBus.getDefault().postSticky(media);

		Intent intent = new Intent(MainActivity.this, MediaItemActivity.class);
		intent.putExtra("autoPlay", _autoPlay);
		startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		ButterKnife.bind(this);

		phMap = new HashMap<>();
		// PROD
		phMap.put(4421, "bc6a17435f3f389f37a514c171039b75"); //Hls com AES128
		phMap.put(6050, "2893ae96e3f2fade7391695553400f80"); //DRM com token
		phMap.put(5952, "1190b8e6d5e846c0c749e3db38ed0dcf"); //DRM
		phMap.put(5719, "2dcbb8a0463215c2833dd7b178bc05da"); //DASH

		// DEV
		phMap.put(543, "664a1791fa5d4b0861416d0059da8cda"); //1080p
		phMap.put(562, "b00772b75e3677dba5a59e09598b7a0d"); //DASH

		callCommonList();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		ButterKnife.unbind(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.main_menu, menu);
		this.menu = menu;

		SearchView adTag = (SearchView) menu.findItem(R.id.adTag).getActionView();
		adTag.setQueryHint("myjson id ( ex: 26dyf )");

		adTag.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				getTags(query);
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});

		// Clean magnifier
		int searchCloseButtonId = adTag.getContext().getResources().getIdentifier("android:id/search_mag_icon", null, null);
		ImageView magIcon = (ImageView) adTag.findViewById(searchCloseButtonId);
		magIcon.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
		magIcon.setVisibility(View.INVISIBLE);

		_autoPlayIcon = DrawableCompat.wrap(menu.findItem(R.id.autoPlay).getIcon()).mutate();

		if (_autoPlay)
			DrawableCompat.setTint(_autoPlayIcon, _autoPlayColor);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if(adEnabled) {
			MenuItem dbclick = menu.findItem(R.id.withTag);
			dbclick.setIcon(R.drawable.ic_dbclick_disable);
			showMediasList(mediaList);
		}

		switch (id) {
			case R.id.withTag:
				if (!adEnabled) {
					getTags("4xtfj");
					item.setIcon(R.drawable.ic_dbclick);
				}

				adEnabled = !adEnabled;
				break;

			case R.id.common:
				this.mediaList.clear();
				callCommonList();
				adEnabled = false;
				break;

			case R.id.about:
				Intent about = new Intent(this, AboutActivity.class);
				startActivity(about);
				break;

			case R.id.live:
				this.mediaList.clear();
				list.setAdapter(null);
				this.mediaList = populateLiveMedias();
				showMediasList(this.mediaList);
				adEnabled = false;
				break;

			case R.id.autoPlay:
				if (_autoPlay)
					DrawableCompat.setTint(_autoPlayIcon, _autoPlayColorDisabled);
				else DrawableCompat.setTint(_autoPlayIcon, _autoPlayColor);

				_autoPlay = !_autoPlay;
				break;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Make the request to the Samba Api ( see also http://dev.sambatech.com/documentation/sambavideos/index.html )
	 * @param pid - Project ID
	 */
	private void makeMediasCall(final int pid) {
		boolean isDev = pid == 543 || pid == 562;
		Call<ArrayList<LiquidMedia>> call = LiquidApi.getApi(isDev ? svapi_dev : svapi_stage).
				getMedias(isDev ? svapi_token_dev : svapi_token_prod, pid, true, "VIDEO,AUDIO");
		loadingFlag = true;
		call.enqueue(new Callback<ArrayList<LiquidMedia>>() {
			@Override
			public void onResponse(retrofit.Response<ArrayList<LiquidMedia>> response, Retrofit retrofit) {
				if (response.code() == 200) {
					ArrayList<LiquidMedia> medias = response.body();
					medias = insertExternalData(medias, pid);
					mediaList.addAll(medias);
					showMediasList(mediaList);
					loadingFlag = false;
				}

				loading.setVisibility(View.GONE);
			}

			@Override
			public void onFailure(Throwable t) {
				loadingFlag = false;
			}
		});
	}

	/**
	 * Request the DFP tags ( tags that you would put on the "ad_program" param of our browser player )
	 * @param jsonId - Id for the myjson.com service
	 */
	private void getTags(final String jsonId) {
		//Calling for our tags
		Call<ArrayList<LiquidMedia.AdTag>> tagCall = LiquidApi.getApi(tag_endpoint).getTags(jsonId);

		loading.setVisibility(View.VISIBLE);
		list.setAdapter(null);

		tagCall.enqueue(new Callback<ArrayList<LiquidMedia.AdTag>>() {
			@Override
			public void onResponse(retrofit.Response<ArrayList<LiquidMedia.AdTag>> response, Retrofit retrofit) {
				if (response.code() == 200) {
					ArrayList<LiquidMedia.AdTag> tags = response.body();
					try {
						adMediaList = mediasWithTags(mediaList, tags);
						showMediasList(adMediaList);
					}
					catch (CloneNotSupportedException e) {}
				} else {
					Toast.makeText(MainActivity.this, "Tags não achadas no id: " + jsonId, Toast.LENGTH_SHORT).show();
				}
				loading.setVisibility(View.GONE);
			}

			@Override
			public void onFailure(Throwable t) {
				Toast.makeText(MainActivity.this, "erro requisição: " + jsonId, Toast.LENGTH_SHORT).show();
				loading.setVisibility(View.GONE);
			}
		});
	}

	private void callCommonList() {

		if(mediaList != null ) {
			mediaList.clear();
		}else {
			mediaList = new ArrayList<>();
		}

		// Injected medias

		/*LiquidMedia m;
		LiquidMedia.Thumb thumb = new LiquidMedia.Thumb();
		thumb.url = "http://pcgamingwiki.com/images/thumb/b/b3/DRM-free_icon.svg/120px-DRM-free_icon.svg.png";
		ArrayList<LiquidMedia.Thumb> thumbs = new ArrayList<>(Arrays.asList(new LiquidMedia.Thumb[]{thumb}));*/

		// SBT
		LiquidMedia m = new LiquidMedia();
		m.title = "SBT (com publicidade)";
		m.ph = "25ce5b8513c18a9eae99a8af601d0943";
		m.id = "5db4352a8618fbf794753d2f1170dbf8";
		m.adTag = new LiquidMedia.AdTag();
		m.adTag.name = m.title;
		m.adTag.url = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480|640x480&iu=/1011235/640x480_WebDigital_TheNoite_PreRoll&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&url=[referrer_url]&description_url=[description_url]&correlator=[timestamp]&a=0";
		//m.adTag.url = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/1011235/640x480_WebDigital_PreRoll&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1]&volume=50";
		LiquidMedia.Thumb thumb = new LiquidMedia.Thumb();
		thumb.url = "https://vignette4.wikia.nocookie.net/telepedia/images/4/4e/Logotipo_do_SBT.png/revision/latest?cb=20150603172637&path-prefix=pt-br";
		m.thumbs = new ArrayList<>(Arrays.asList(new LiquidMedia.Thumb[]{thumb}));
		mediaList.add(m);

		//http://fast.player.liquidplatform.com/pApiv2/embed/25ce5b8513c18a9eae99a8af601d0943/5db4352a8618fbf794753d2f1170dbf8?jsApi=true&enableShare=true&wideScreen=true&html5=false&autoStart=true&ad_program=https%253A%252F%252Fpubads.g.doubleclick.net%252Fgampad%252Fads%253Fsz%253D640x480%257C640x480%2526iu%253D%252F1011235%252F640x480_WebDigital_TheNoite_PreRoll%2526impl%253Ds%2526gdfp_req%253D1%2526env%253Dvp%2526output%253Dvast%2526unviewed_position_start%253D1%2526url%253D%255Breferrer_url%255D%2526description_url%253D%255Bdescription_url%255D%2526correlator%253D%255Btimestamp%255D&a=0&parentURL=#http://www.sbt.com.br/sbtvideos/programa/400/The-Noite-com-Danilo-Gentili/categoria/4527/5db4352a8618fbf794753d2f1170dbf8/Adultos-com-voz-de-crianca-porque-fica-engracado.html
		// AXINOM
		/*m = new LiquidMedia();
		m.url = "https://media.axprod.net/TestVectors/v6-MultiDRM/Manifest_1080p.mpd";
		m.validationRequest = getDrmAxinom();
		m.title = "Dash DRM (Axinom)";
		m.type = "dash";
		m.thumbs = thumbs;
		mediaList.add(m);*/

		// IRDETO
		//b00772b75e3677dba5a59e09598b7a0d be4a12397143caf9ec41c9acb98728bf
		/*m = new LiquidMedia();
		m.title = "DRM Irdeto (p#7)";
		m.ph = "b00772b75e3677dba5a59e09598b7a0d";
		m.id = "eec9fa7ab62032a377cff462522f69dc";
		m.url = "http://52.32.88.36/sambatech/stage/MrPoppersPenguins.ism/manifest_mvlist.mpd";
		m.entitlementScheme = new LiquidMedia.EntitlementScheme("MrPoppersPenguins");
		m.environment = SambaMediaRequest.Environment.DEV;
		m.type = "dash";
		m.thumbs = thumbs;
		mediaList.add(m);

		m = new LiquidMedia();
		m.title = "DRM Samba (p#7)";
		m.ph = "b00772b75e3677dba5a59e09598b7a0d";
		m.id = "eec9fa7ab62032a377cff462522f69dc";
		m.url = "http://107.21.208.27/vodd/_definst_/mp4:myMovie.mp4/manifest_mvlist.mpd";
		m.entitlementScheme = new LiquidMedia.EntitlementScheme("samba_p7_test");
		m.environment = SambaMediaRequest.Environment.DEV;
		m.type = "dash";
		m.thumbs = thumbs;
		mediaList.add(m);

		m = new LiquidMedia();
		m.title = "DRM Samba (p#8)";
		m.ph = "b00772b75e3677dba5a59e09598b7a0d";
		m.id = "3153f923ae18c999a01db465d50d0dac";
		m.url = "http://107.21.208.27/vodd/_definst_/mp4:chaves3_480p.mp4/manifest_mvlist.mpd";
		m.entitlementScheme = new LiquidMedia.EntitlementScheme("samba_p8_test");
		m.environment = SambaMediaRequest.Environment.DEV;
		m.type = "dash";
		m.thumbs = thumbs;
		mediaList.add(m);

		m = new LiquidMedia();
		m.title = "DRM Samba (p#9)";
		m.ph = "b00772b75e3677dba5a59e09598b7a0d";
		m.id = "d3c7ec784a4ff90b7c6a0e51b4657a5e";
		m.url = "http://107.21.208.27/vodd/_definst_/mp4:agdq.mp4/manifest_mvlist.mpd";
		m.entitlementScheme = new LiquidMedia.EntitlementScheme("samba_p9_test");
		m.environment = SambaMediaRequest.Environment.DEV;
		m.type = "dash";
		m.thumbs = thumbs;
		mediaList.add(m);*/

		loading.setVisibility(View.VISIBLE);
		list.setAdapter(null);

		// making the call to projects
		for (Map.Entry<Integer, String> kv : phMap.entrySet())
			makeMediasCall(kv.getKey());
	}

	/*private LiquidMedia.ValidationRequest getValidationRequestAxinom() {
		if (validationRequestAxinom != null) return validationRequestAxinom;

		return validationRequestAxinom = new LiquidMedia.ValidationRequest("https://drmIrdeto-quick-start.azurewebsites.net/api/authorization/Axinom%20demo%20video", new LiquidMedia.DrmCallback() {
			public void call(SambaMediaConfig media, String response) {
				if (response == null) return;

				media.drmRequest = new DrmRequest("https://drmIrdeto-widevine-licensing.axtest.net/AcquireLicense");
				media.drmRequest.addHeaderParam("X-AxDRM-Message", response.substring(1, response.length() - 1));
			}
		});
	}*/

	/**
	 * Populates the MediaItem with the given medias
	 * @param medias - Array of objects representing the medias requested
	 */
	private void showMediasList(ArrayList<LiquidMedia> medias) {

		mAdapter = new MediasAdapter(this, medias);
		list.setAdapter(mAdapter);

		mAdapter.notifyDataSetChanged();

	}

	/**
	 * Inserts the corresponded player hashs in each media object given its project id
	 *
	 * @param medias - Array of objects representing the medias requested
	 * @param pid - Project ID
	 * @return
	 */
	private ArrayList<LiquidMedia> insertExternalData(ArrayList<LiquidMedia> medias, int pid) {
		if(medias != null) {

			for(LiquidMedia media : medias) {
				media.ph = phMap.get(pid);
				media.environment = pid == 543 || pid == 562 ? SambaMediaRequest.Environment.DEV
						: pid == 6050 ? SambaMediaRequest.Environment.PROD : SambaMediaRequest.Environment.STAGING;

				// WORKAROUND: to identify which project has DRM
				if (pid == 5952 || pid == 6050 || pid == 5719 || pid == 562)
					media.entitlementScheme = new LiquidMedia.EntitlementScheme();
			}

		}
		return medias;
	}

	/**
	 * Inserts ad tags for DFP Ads.
	 * @param medias - Array of objects representing the medias requested
	 * @param tags - List of DFP tags
	 * @return
	 * @throws CloneNotSupportedException
	 */
	private ArrayList<LiquidMedia> mediasWithTags(ArrayList<LiquidMedia> medias, ArrayList<LiquidMedia.AdTag> tags) throws CloneNotSupportedException{
		int mIndex = 0;
		ArrayList<LiquidMedia> newMedias = new ArrayList<LiquidMedia>();

		for(int i = 0; i < tags.size(); i++) {
			LiquidMedia m = (LiquidMedia) (i < medias.size() ? medias.get(i).clone() : newMedias.get(mIndex++).clone());
			m.adTag = new LiquidMedia.AdTag();
			m.adTag.name = tags.get(i).name;
			m.adTag.url = tags.get(i).url;
			m.description = tags.get(i).name;
			newMedias.add(m);
		}
		return newMedias;
	}

	/**
	 * Populate Live medias
	 * @return
	 */
	private ArrayList<LiquidMedia> populateLiveMedias() {

		final String ph = "bc6a17435f3f389f37a514c171039b75";
		ArrayList<LiquidMedia> medias = new ArrayList<>();
		LiquidMedia.Thumb thumb = new LiquidMedia.Thumb();

		thumb.url = "http://d2wfckeh0j530f.cloudfront.net/wordpress/wp-content/uploads/2015/07/Industry-Broadcast1.png";

		ArrayList<LiquidMedia.Thumb> thumbs = new ArrayList<>(Arrays.asList(new LiquidMedia.Thumb[]{thumb}));

		// HLS 1
		LiquidMedia media = new LiquidMedia();
		media.environment = SambaMediaRequest.Environment.STAGING;
		media.ph = ph;
		media.streamUrl = "http://liveabr2.sambatech.com.br/abr/sbtabr_8fcdc5f0f8df8d4de56b22a2c6660470/livestreamabrsbtbkp.m3u8";
		media.title = "HLS 1";
		media.description = "HLS 1";
		media.thumbs = thumbs;

		medias.add(media);

		// HLS 2
		media = new LiquidMedia();
		media.environment = SambaMediaRequest.Environment.PROD;
		media.ph = ph;
		media.streamUrl = "http://slrp.sambavideos.sambatech.com/liveevent/tvdiario_7a683b067e5eee5c8d45e1e1883f69b9/livestream/playlist.m3u8";
		media.title = "HLS 2";
		media.description = "HLS 2";
		media.thumbs = thumbs;

		medias.add(media);

		// HLS + DVR
		media = new LiquidMedia();
		media.environment = SambaMediaRequest.Environment.STAGING;
		media.ph = "7e1c06038102a6d35995586970de734c";
		media.liveChannelId = "e10c180abe9b717efd006f3eb752e974";
		media.title = "HLS + DVR";
		media.description = "HLS + DVR";
		media.thumbs = thumbs;

		medias.add(media);

		// fallback URL
		media = new LiquidMedia();
		media.environment = SambaMediaRequest.Environment.STAGING;
		media.ph = ph;
		media.streamUrl = "http://liveabr2.sambatech.com.br/abr/sbtabr_8fcdc5f0f8df8d4de56b26660470/wrong_url.m3u8";
		media.backupUrls = new String[]{
				"http://liveabr2.sambatech.com.br/abr/sbtabr_8fcdc5f0f8df8d4de56b26660470/wrong_url2.m3u8",
				"http://liveabr2.sambatech.com.br/abr/sbtabr_8fcdc5f0f8df8d4de56b22a2c6660470/livestreamabrsbtbkp.m3u8"
		};
		media.title = "Fallback URL";
		media.description = "Fallback URL";
		media.thumbs = thumbs;


		medias.add(media);

		// fallback HDS
		media = new LiquidMedia();
		media.environment = SambaMediaRequest.Environment.DEV;
		media.ph = "90fe205bd667e40036dd56619d69359f";
		media.streamUrl = "http://slrp.sambavideos.sambatech.com/liveevent/pajucara3_7fbed8aac5d5d915877e6ec61e3cf0db/livestream/manifest.f4m";
		media.title = "Fallback HDS";
		media.description = "Fallback HDS";
		media.thumbs = thumbs;

		medias.add(media);

		// fallback error
		media = new LiquidMedia();
		media.environment = SambaMediaRequest.Environment.STAGING;
		media.ph = ph;
		media.streamUrl = "http://liveabr2.sambatech.com.br/abr/sbtabr_8fcdc5f0f8df8d4de56b26660470/wrong_url.m3u8";
		media.backupUrls = new String[]{
				"http://liveabr2.sambatech.com.br/abr/sbtabr_8fcdc5f0f8df8d4de56b26660470/wrong_url2.m3u8"
		};
		media.title = "Fallback com erro";
		media.description = "Fallback com erro";
		media.thumbs = thumbs;

		medias.add(media);

		// geo-blocking
		media = new LiquidMedia();
		media.environment = SambaMediaRequest.Environment.DEV;
		media.ph = "90fe205bd667e40036dd56619d69359f";
		media.streamUrl = "http://slrp.sambavideos.sambatech.com/liveevent/tvdiario_7a683b067e5eee5c8d45e1e1883f69b9/livestream/playlist.m3u8";
		media.title = "Geo-blocking";
		media.description = "Geo-blocking";
		media.thumbs = thumbs;

		medias.add(media);

		// audio
		media = new LiquidMedia();
		media.environment = SambaMediaRequest.Environment.PROD;
		media.ph = ph;
		media.streamUrl = "http://slrp.sambavideos.sambatech.com/radio/pajucara4_7fbed8aac5d5d915877e6ec61e3cf0db/livestream/playlist.m3u8";
		media.qualifier = "AUDIO";
		media.title = "Audio Live";
		media.description = "Live de audio";
		media.thumbs = thumbs;

		medias.add(media);

		return medias;
	}

}
