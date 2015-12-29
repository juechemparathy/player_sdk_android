package com.sambatech.player;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.libraries.mediaframework.exoplayerextensions.ExoplayerWrapper;
import com.google.android.libraries.mediaframework.exoplayerextensions.Video;
import com.google.android.libraries.mediaframework.layeredvideo.PlaybackControlLayer;
import com.google.android.libraries.mediaframework.layeredvideo.SimpleVideoPlayer;
import com.sambatech.player.event.SambaEvent;
import com.sambatech.player.event.SambaEventBus;
import com.sambatech.player.event.SambaPlayerListener;
import com.sambatech.player.model.SambaMedia;
import com.sambatech.player.model.SambaMediaConfig;
import com.sambatech.player.plugins.ImaWrapper;

/**
 * Controller for SambaPlayer view.
 *
 * @author Leandro Zanol - 7/12/15
 */
public class SambaPlayerView extends FrameLayout implements SambaPlayer {

	private SimpleVideoPlayer player;
	private SambaMediaConfig media = new SambaMediaConfig();
	private SambaPlayerListener listener;
	private boolean _isReady;

	public SambaPlayerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		applyAttributes(getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SambaPlayerView, 0, 0));

        /*if (!isInEditMode() && media.url != null)
            createPlayer();*/
	}

	/**	Player API **/

	public void setMedia(SambaMedia media) {
		if (media == null) {
			//SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.ERROR, ));
			throw new IllegalArgumentException("Media data is null.");
		}

		this.media = (SambaMediaConfig)media;

        //Creating player
        createPlayer();
	}

	public SambaMedia getMedia() {
		return media;
	}

	public void setListener(SambaPlayerListener listener) {
		this.listener = listener;
	}

	public void play() {
		Log.i("player", "play!");
		player.play();
	}

	public void pause() {
		player.pause();
	}

	public void stop() {
		player.pause();
		// TODO: seekTo(0)
		SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.STOP));
	}

	// TODO: seek(float secs)

	public void setFullscreen(boolean flag) {
		player.setFullscreen(flag);
	}

	public boolean isFullscreen() {
		return player.isFullscreen();
	}

	public void show() {
		player.show();
	}

	public void hide() {
		player.hide();
	}

	public void destroy() {
		if (player == null)
			return;

		stop();
		player.release();
		SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.UNLOAD));
	}

	public float getCurrentTime() {
		return player.getCurrentPosition()/1000f;
	}

	public float getDuration() {
		return player.getDuration()/1000f;
	}

	public View getView() {
		return this;
	}

	public boolean isReady() {
		return _isReady;
	}

	private void createPlayer() {
        if (media.url == null || media.url.isEmpty()) {
			Toast.makeText(getContext(), "The requested media has no URL!", Toast.LENGTH_LONG).show();
			return;
		}

		destroy();

		Video.VideoType videoType = Video.VideoType.OTHER;

		switch (media.type.toLowerCase()) {
			/*case "progressive":
				videoType = Video.VideoType.MP4;
				break;*/
			case "hls":
				videoType = Video.VideoType.HLS;
				break;
			case "dash":
				videoType = Video.VideoType.DASH;
				break;
		}

        player = new SimpleVideoPlayer((Activity)getContext(), this,
                new Video(media.url, videoType),
                media.title, media.adUrl == null || media.adUrl.isEmpty());

		player.setSeekbarColor(media.themeColor);

		if (media.isLive)
			player.setControlsVisible(false);

		// Move the content player's surface layer to the background so that the ad player's surface
		// layer can be overlaid on top of it during ad playback.
		player.moveSurfaceToBackground();

		player.addActionButton(ContextCompat.getDrawable(getContext(), R.drawable.share), getContext().getString(R.string.share_facebook), new OnClickListener() {
			@Override
			public void onClick(View v) {
				Toast.makeText(getContext(), "Share Facebook", Toast.LENGTH_SHORT).show();
			}
		});

		player.addPlaybackListener(new ExoplayerWrapper.PlaybackListener() {
			@Override
			public void onStateChanged(boolean playWhenReady, int playbackState) {
				Log.i("player", "state: " + playWhenReady + " " + playbackState);

				switch (playbackState) {
					case ExoPlayer.STATE_READY:
						if (!playWhenReady)
							SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.PAUSE));
						break;
					case ExoPlayer.STATE_ENDED:
						SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.FINISH));
				}
			}

			@Override
			public void onError(Exception e) {
				Log.i("player", "error", e);
				SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.ERROR, e));
			}

			@Override
			public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
				Log.i("player", "size: " + width + ' ' + height + ' ' + unappliedRotationDegrees + ' ' + pixelWidthHeightRatio);
				SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.RESIZE, width, height, unappliedRotationDegrees, pixelWidthHeightRatio));
			}
		});

		player.setPlayCallback(new PlaybackControlLayer.PlayCallback() {
			@Override
			public void onPlay() {
				SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.PLAY));
			}
		});

		player.setFullscreenCallback(new PlaybackControlLayer.FullscreenCallback() {
			@Override
			public void onGoToFullscreen() {
				SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.FULLSCREEN));
			}

			@Override
			public void onReturnFromFullscreen() {
				SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.FULLSCREEN_EXIT));
			}
		});

		/*setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.i("player", "click!!");
				SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.CLICK));
			}
		});*/

		// TODO: desacoplar
		if (media.adUrl != null && !media.adUrl.isEmpty())
			new ImaWrapper((Activity)getContext(), this, media.adUrl);

		_isReady = true;

		SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.LOAD, this));
	}

	private void applyAttributes(TypedArray attrs) {
		media.url = attrs.getString(R.styleable.SambaPlayerView_url);
		media.title = attrs.getString(R.styleable.SambaPlayerView_title);
		attrs.recycle();
	}
}
