package com.sambatech.player.model;

import com.google.android.libraries.mediaframework.exoplayerextensions.DrmRequest;
import com.sambatech.player.utils.Helpers;

import java.lang.reflect.Field;

/**
 * Internal extension of the media entity for player/plugins config purposes.
 *
 * @author Leandro Zanol - 2/12/15
 */
public class SambaMediaConfig extends SambaMedia {

	public String id;
	public String projectHash;
	public String qualifier;
	public int projectId;
	public int categoryId;
	public String sessionId = Helpers.getSessionId();
	public int themeColor = 0xFF72BE44;
	public String themeColorHex = "#72BE44";
	public String sttmUrl;
	public String sttmKey;
	public SambaMediaRequest request;
	public DrmRequest drmRequest;
	public boolean blockIfRooted;
	public int retriesTotal = 3;
	public int defaultOutputIndex = 0;

	public SambaMediaConfig() {}

	public SambaMediaConfig(SambaMedia media) {
		super(media);

		if (media instanceof SambaMediaConfig) {
			SambaMediaConfig m = (SambaMediaConfig)media;
			id = m.id;
			projectHash = m.projectHash;
			qualifier = m.qualifier;
			projectId = m.projectId;
			categoryId = m.categoryId;
			sessionId = m.sessionId;
			themeColor = m.themeColor;
			themeColorHex = m.themeColorHex;
			sttmUrl = m.sttmUrl;
			sttmKey = m.sttmKey;
			request = m.request;
			drmRequest = m.drmRequest;
			// TODO: Reabilitar após correção (https://github.com/sambatech-desenv/player/issues/589)
			//blockIfRooted = m.blockIfRooted;
			retriesTotal = m.retriesTotal;
		}
	}

	@Override
	public String toString() {
		String desc = "";
		Field[] fields = getClass().getSuperclass().getDeclaredFields();

		try {
			for (Field field : fields)
				desc += field.getName() + ": " + field.get(this) + '\n';
		}
		catch (IllegalAccessException e) {
			e.printStackTrace();
		}

		return desc;
	}
}
