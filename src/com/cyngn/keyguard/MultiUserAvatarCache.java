package com.cyngn.keyguard;

import android.graphics.drawable.Drawable;
import java.util.HashMap;

public class MultiUserAvatarCache {
    private final HashMap<Integer, Drawable> mCache = new HashMap();

    public void clear(int userId) {
        this.mCache.remove(Integer.valueOf(userId));
    }

    public Drawable get(int userId) {
        return (Drawable) this.mCache.get(Integer.valueOf(userId));
    }

    public void put(int userId, Drawable image) {
        this.mCache.put(Integer.valueOf(userId), image);
    }
}
