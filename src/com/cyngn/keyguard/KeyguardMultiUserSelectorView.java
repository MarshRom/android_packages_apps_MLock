package com.cyngn.keyguard;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class KeyguardMultiUserSelectorView extends FrameLayout implements OnClickListener {
    private static final int FADE_OUT_ANIMATION_DURATION = 100;
    private static final String TAG = "KeyguardMultiUserSelectorView";
    private KeyguardMultiUserAvatar mActiveUserAvatar;
    private UserSwitcherCallback mCallback;
    Comparator<UserInfo> mOrderAddedComparator;
    private ViewGroup mUsersGrid;

    public KeyguardMultiUserSelectorView(Context context) {
        this(context, null, 0);
    }

    public KeyguardMultiUserSelectorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardMultiUserSelectorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mOrderAddedComparator = new Comparator<UserInfo>() {
            public int compare(UserInfo lhs, UserInfo rhs) {
                return lhs.serialNumber - rhs.serialNumber;
            }
        };
    }

    protected void onFinishInflate() {
        this.mUsersGrid = (ViewGroup) findViewById(R.id.keyguard_users_grid);
        this.mUsersGrid.removeAllViews();
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setCallback(UserSwitcherCallback callback) {
        this.mCallback = callback;
    }

    public void addUsers(Collection<UserInfo> userList) {
        UserInfo activeUser;
        try {
            activeUser = ActivityManagerNative.getDefault().getCurrentUser();
        } catch (RemoteException e) {
            activeUser = null;
        }
        ArrayList<UserInfo> users = new ArrayList(userList);
        Collections.sort(users, this.mOrderAddedComparator);
        Iterator i$ = users.iterator();
        while (i$.hasNext()) {
            UserInfo user = (UserInfo) i$.next();
            KeyguardMultiUserAvatar uv = createAndAddUser(user);
            if (user.id == activeUser.id) {
                this.mActiveUserAvatar = uv;
            }
            uv.setActive(false, false, null);
        }
        this.mActiveUserAvatar.lockPressed(true);
    }

    public void finalizeActiveUserView(boolean animate) {
        if (animate) {
            getHandler().postDelayed(new Runnable() {
                public void run() {
                    KeyguardMultiUserSelectorView.this.finalizeActiveUserNow(true);
                }
            }, 500);
        } else {
            finalizeActiveUserNow(animate);
        }
    }

    void finalizeActiveUserNow(boolean animate) {
        this.mActiveUserAvatar.lockPressed(false);
        this.mActiveUserAvatar.setActive(true, animate, null);
    }

    private KeyguardMultiUserAvatar createAndAddUser(UserInfo user) {
        KeyguardMultiUserAvatar uv = KeyguardMultiUserAvatar.fromXml(R.layout.keyguard_multi_user_avatar, this.mContext, this, user);
        this.mUsersGrid.addView(uv);
        return uv;
    }

    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!(event.getActionMasked() == 3 || this.mCallback == null)) {
            this.mCallback.userActivity();
        }
        return false;
    }

    private void setAllClickable(boolean clickable) {
        for (int i = 0; i < this.mUsersGrid.getChildCount(); i++) {
            View v = this.mUsersGrid.getChildAt(i);
            v.setClickable(clickable);
            v.setPressed(false);
        }
    }

    public void onClick(View v) {
        if (v instanceof KeyguardMultiUserAvatar) {
            final KeyguardMultiUserAvatar avatar = (KeyguardMultiUserAvatar) v;
            if (!avatar.isClickable()) {
                return;
            }
            if (this.mActiveUserAvatar == avatar) {
                this.mCallback.showUnlockHint();
                return;
            }
            this.mCallback.hideSecurityView(FADE_OUT_ANIMATION_DURATION);
            setAllClickable(false);
            avatar.lockPressed(true);
            this.mActiveUserAvatar.setActive(false, true, new Runnable() {
                public void run() {
                    KeyguardMultiUserSelectorView.this.mActiveUserAvatar = avatar;
                    try {
                        ActivityManagerNative.getDefault().switchUser(avatar.getUserInfo().id);
                    } catch (RemoteException re) {
                        Log.e(KeyguardMultiUserSelectorView.TAG, "Couldn't switch user " + re);
                    }
                }
            });
        }
    }
}
