package com.cyngn.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

class KeyguardMultiUserAvatar extends FrameLayout {
    private static final float ACTIVE_ALPHA = 1.0f;
    private static final float ACTIVE_SCALE = 1.5f;
    private static final float ACTIVE_TEXT_ALPHA = 0.0f;
    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final float INACTIVE_ALPHA = 1.0f;
    private static final float INACTIVE_TEXT_ALPHA = 0.5f;
    private static final int SWITCH_ANIMATION_DURATION = 150;
    private static final String TAG = KeyguardMultiUserAvatar.class.getSimpleName();
    private boolean mActive;
    private final float mActiveAlpha;
    private final float mActiveScale;
    private final float mActiveTextAlpha;
    private final int mFrameColor;
    private final int mFrameShadowColor;
    private KeyguardCircleFramedDrawable mFramed;
    private final int mHighlightColor;
    private final float mIconSize;
    private final float mInactiveAlpha;
    private final float mInactiveTextAlpha;
    private boolean mInit;
    private boolean mPressLock;
    private final float mShadowRadius;
    private final float mStroke;
    private final int mTextColor;
    private boolean mTouched;
    private ImageView mUserImage;
    private UserInfo mUserInfo;
    private UserManager mUserManager;
    private TextView mUserName;
    private KeyguardMultiUserSelectorView mUserSelector;

    public static KeyguardMultiUserAvatar fromXml(int resId, Context context, KeyguardMultiUserSelectorView userSelector, UserInfo info) {
        KeyguardMultiUserAvatar icon = (KeyguardMultiUserAvatar) LayoutInflater.from(context).inflate(resId, userSelector, DEBUG);
        icon.init(info, userSelector);
        return icon;
    }

    public KeyguardMultiUserAvatar(Context context) {
        this(context, null, 0);
    }

    public KeyguardMultiUserAvatar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardMultiUserAvatar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mInit = true;
        Resources res = this.mContext.getResources();
        this.mTextColor = res.getColor(R.color.keyguard_avatar_nick_color);
        this.mIconSize = res.getDimension(R.dimen.keyguard_avatar_size);
        this.mStroke = res.getDimension(R.dimen.keyguard_avatar_frame_stroke_width);
        this.mShadowRadius = res.getDimension(R.dimen.keyguard_avatar_frame_shadow_radius);
        this.mFrameColor = res.getColor(R.color.keyguard_avatar_frame_color);
        this.mFrameShadowColor = res.getColor(R.color.keyguard_avatar_frame_shadow_color);
        this.mHighlightColor = res.getColor(R.color.keyguard_avatar_frame_pressed_color);
        this.mActiveTextAlpha = ACTIVE_TEXT_ALPHA;
        this.mInactiveTextAlpha = INACTIVE_TEXT_ALPHA;
        this.mActiveScale = ACTIVE_SCALE;
        this.mActiveAlpha = INACTIVE_ALPHA;
        this.mInactiveAlpha = INACTIVE_ALPHA;
        this.mUserManager = (UserManager) this.mContext.getSystemService("user");
        this.mTouched = DEBUG;
        setLayerType(1, null);
    }

    protected String rewriteIconPath(String path) {
        return path;
    }

    public void init(UserInfo user, KeyguardMultiUserSelectorView userSelector) {
        this.mUserInfo = user;
        this.mUserSelector = userSelector;
        this.mUserImage = (ImageView) findViewById(R.id.keyguard_user_avatar);
        this.mUserName = (TextView) findViewById(R.id.keyguard_user_name);
        this.mFramed = (KeyguardCircleFramedDrawable) KeyguardViewMediator.getAvatarCache().get(user.id);
        if (this.mFramed == null || !this.mFramed.verifyParams(this.mIconSize, this.mFrameColor, this.mStroke, this.mFrameShadowColor, this.mShadowRadius, this.mHighlightColor)) {
            Bitmap icon = null;
            try {
                icon = this.mUserManager.getUserIcon(user.id);
            } catch (Exception e) {
                if (DEBUG) {
                    Log.d(TAG, "failed to get profile icon " + user, e);
                }
            }
            if (icon == null) {
                icon = BitmapFactory.decodeResource(this.mContext.getResources(), 17302221);
            }
            this.mFramed = new KeyguardCircleFramedDrawable(icon, (int) this.mIconSize, this.mFrameColor, this.mStroke, this.mFrameShadowColor, this.mShadowRadius, this.mHighlightColor);
            KeyguardViewMediator.getAvatarCache().put(user.id, this.mFramed);
        }
        this.mFramed.reset();
        this.mUserImage.setImageDrawable(this.mFramed);
        this.mUserName.setText(this.mUserInfo.name);
        setOnClickListener(this.mUserSelector);
        this.mInit = DEBUG;
    }

    public void setActive(boolean active, boolean animate, Runnable onComplete) {
        if (this.mActive != active || this.mInit) {
            this.mActive = active;
            if (active) {
                ((KeyguardLinearLayout) getParent()).setTopChild(this);
                setContentDescription(this.mUserName.getText() + ". " + this.mContext.getString(R.string.user_switched, new Object[]{""}));
            } else {
                setContentDescription(this.mUserName.getText());
            }
        }
        updateVisualsForActive(this.mActive, animate, SWITCH_ANIMATION_DURATION, onComplete);
    }

    void updateVisualsForActive(boolean active, boolean animate, int duration, final Runnable onComplete) {
        int initTextAlpha;
        float finalScale = INACTIVE_ALPHA;
        final float finalAlpha = active ? this.mActiveAlpha : this.mInactiveAlpha;
        final float initAlpha = active ? this.mInactiveAlpha : this.mActiveAlpha;
        if (!active) {
            finalScale = INACTIVE_ALPHA / this.mActiveScale;
        }
        final float initScale = this.mFramed.getScale();
        final int finalTextAlpha = active ? (int) (this.mActiveTextAlpha * 255.0f) : (int) (this.mInactiveTextAlpha * 255.0f);
        if (active) {
            initTextAlpha = (int) (this.mInactiveTextAlpha * 255.0f);
        } else {
            initTextAlpha = (int) (this.mActiveTextAlpha * 255.0f);
        }
        this.mUserName.setTextColor(this.mTextColor);
        if (animate && this.mTouched) {
            ValueAnimator va = ValueAnimator.ofFloat(new float[]{ACTIVE_TEXT_ALPHA, INACTIVE_ALPHA});
            va.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    float r = animation.getAnimatedFraction();
                    float alpha = ((KeyguardMultiUserAvatar.INACTIVE_ALPHA - r) * initAlpha) + (finalAlpha * r);
                    int textAlpha = (int) (((KeyguardMultiUserAvatar.INACTIVE_ALPHA - r) * ((float) initTextAlpha)) + (((float) finalTextAlpha) * r));
                    KeyguardMultiUserAvatar.this.mFramed.setScale(((KeyguardMultiUserAvatar.INACTIVE_ALPHA - r) * initScale) + (finalScale * r));
                    KeyguardMultiUserAvatar.this.mUserImage.setAlpha(alpha);
                    KeyguardMultiUserAvatar.this.mUserName.setTextColor(Color.argb(textAlpha, 255, 255, 255));
                    KeyguardMultiUserAvatar.this.mUserImage.invalidate();
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            });
            va.setDuration((long) duration);
            va.start();
        } else {
            this.mFramed.setScale(finalScale);
            this.mUserImage.setAlpha(finalAlpha);
            this.mUserName.setTextColor(Color.argb(finalTextAlpha, 255, 255, 255));
            if (onComplete != null) {
                post(onComplete);
            }
        }
        this.mTouched = true;
    }

    public void setPressed(boolean pressed) {
        if (this.mPressLock && !pressed) {
            return;
        }
        if (this.mPressLock || !pressed || isClickable()) {
            super.setPressed(pressed);
            this.mFramed.setPressed(pressed);
            this.mUserImage.invalidate();
        }
    }

    public void lockPressed(boolean pressed) {
        this.mPressLock = pressed;
        setPressed(pressed);
    }

    public UserInfo getUserInfo() {
        return this.mUserInfo;
    }
}
