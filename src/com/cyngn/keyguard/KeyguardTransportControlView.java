package com.cyngn.keyguard;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.RemoteController;
import android.media.RemoteController.MetadataEditor;
import android.media.RemoteController.OnClientUpdateListener;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.BaseSavedState;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import com.cyngn.keyguard.SlidingChallengeLayout.LayoutParams;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class KeyguardTransportControlView extends FrameLayout {
    private static final boolean ANIMATE_TRANSITIONS = true;
    protected static final boolean DEBUG = false;
    protected static final long QUIESCENT_PLAYBACK_FACTOR = 1000;
    private static final int RESET_TO_METADATA_DELAY = 5000;
    protected static final String TAG = "TransportControlView";
    private static final int TRANSITION_DURATION = 200;
    private AudioManager mAudioManager = new AudioManager(this.mContext);
    private ImageView mBadge;
    private ImageView mBtnNext;
    private ImageView mBtnPlay;
    private ImageView mBtnPrev;
    private int mCurrentPlayState = 0;
    private DateFormat mFormat;
    private final FutureSeekRunnable mFutureSeekRunnable = new FutureSeekRunnable();
    private ViewGroup mInfoContainer;
    private Metadata mMetadata = new Metadata();
    private final TransitionSet mMetadataChangeTransition;
    private ViewGroup mMetadataContainer;
    private final OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                KeyguardTransportControlView.this.mFutureSeekRunnable.setProgress(progress);
                KeyguardTransportControlView.this.delayResetToMetadata();
                KeyguardTransportControlView.this.mTempDate.setTime((long) progress);
                KeyguardTransportControlView.this.mTransientSeekTimeElapsed.setText(KeyguardTransportControlView.this.mFormat.format(KeyguardTransportControlView.this.mTempDate));
                return;
            }
            KeyguardTransportControlView.this.updateSeekDisplay();
        }

        public void onStartTrackingTouch(SeekBar seekBar) {
            KeyguardTransportControlView.this.delayResetToMetadata();
            KeyguardTransportControlView.this.removeCallbacks(KeyguardTransportControlView.this.mUpdateSeekBars);
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };
    private MetadataEditor mPopulateMetadataWhenAttached = null;
    private OnClientUpdateListener mRCClientUpdateListener = new OnClientUpdateListener() {
        public void onClientChange(boolean clearing) {
            if (clearing) {
                KeyguardTransportControlView.this.clearMetadata();
            }
        }

        public void onClientPlaybackStateUpdate(int state) {
            KeyguardTransportControlView.this.updatePlayPauseState(state);
        }

        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs, long currentPosMs, float speed) {
            KeyguardTransportControlView.this.updatePlayPauseState(state);
            KeyguardTransportControlView.this.removeCallbacks(KeyguardTransportControlView.this.mUpdateSeekBars);
            if (KeyguardTransportControlView.this.mTransientSeek.getVisibility() == 0 && KeyguardTransportControlView.playbackPositionShouldMove(KeyguardTransportControlView.this.mCurrentPlayState)) {
                KeyguardTransportControlView.this.postDelayed(KeyguardTransportControlView.this.mUpdateSeekBars, KeyguardTransportControlView.QUIESCENT_PLAYBACK_FACTOR);
            }
        }

        public void onClientTransportControlUpdate(int transportControlFlags) {
            KeyguardTransportControlView.this.updateTransportControls(transportControlFlags);
        }

        public void onClientMetadataUpdate(MetadataEditor metadataEditor) {
            KeyguardTransportControlView.this.updateMetadata(metadataEditor);
        }
    };
    private RemoteController mRemoteController;
    private final Runnable mResetToMetadata = new Runnable() {
        public void run() {
            KeyguardTransportControlView.this.resetToMetadata();
        }
    };
    private boolean mSeekEnabled;
    private Date mTempDate = new Date();
    private TextView mTrackArtistAlbum;
    private TextView mTrackTitle;
    private View mTransientSeek;
    private SeekBar mTransientSeekBar;
    private TextView mTransientSeekTimeElapsed;
    private TextView mTransientSeekTimeTotal;
    private final OnClickListener mTransportCommandListener = new OnClickListener() {
        public void onClick(View v) {
            int keyCode = -1;
            if (v == KeyguardTransportControlView.this.mBtnPrev) {
                keyCode = 88;
            } else if (v == KeyguardTransportControlView.this.mBtnNext) {
                keyCode = 87;
            } else if (v == KeyguardTransportControlView.this.mBtnPlay) {
                keyCode = 85;
            }
            if (keyCode != -1) {
                KeyguardTransportControlView.this.sendMediaButtonClick(keyCode);
                KeyguardTransportControlView.this.delayResetToMetadata();
            }
        }
    };
    TransportControlCallback mTransportControlCallback;
    private int mTransportControlFlags;
    private final OnLongClickListener mTransportShowSeekBarListener = new OnLongClickListener() {
        public boolean onLongClick(View v) {
            if (KeyguardTransportControlView.this.mSeekEnabled) {
                return KeyguardTransportControlView.this.tryToggleSeekBar();
            }
            return KeyguardTransportControlView.DEBUG;
        }
    };
    private final KeyguardUpdateMonitorCallback mUpdateMonitor = new KeyguardUpdateMonitorCallback() {
        public void onScreenTurnedOff(int why) {
            KeyguardTransportControlView.this.setEnableMarquee(KeyguardTransportControlView.DEBUG);
        }

        public void onScreenTurnedOn() {
            KeyguardTransportControlView.this.setEnableMarquee(KeyguardTransportControlView.ANIMATE_TRANSITIONS);
        }
    };
    private final UpdateSeekBarRunnable mUpdateSeekBars = new UpdateSeekBarRunnable();

    class FutureSeekRunnable implements Runnable {
        private boolean mPending;
        private int mProgress;

        FutureSeekRunnable() {
        }

        public void run() {
            KeyguardTransportControlView.this.scrubTo(this.mProgress);
            this.mPending = KeyguardTransportControlView.DEBUG;
        }

        void setProgress(int progress) {
            this.mProgress = progress;
            if (!this.mPending) {
                this.mPending = KeyguardTransportControlView.ANIMATE_TRANSITIONS;
                KeyguardTransportControlView.this.postDelayed(this, 30);
            }
        }
    }

    class Metadata {
        private String albumTitle;
        private String artist;
        private Bitmap bitmap;
        private long duration;
        private String trackTitle;

        Metadata() {
        }

        public void clear() {
            this.artist = null;
            this.trackTitle = null;
            this.albumTitle = null;
            this.bitmap = null;
            this.duration = -1;
        }

        public String toString() {
            return "Metadata[artist=" + this.artist + " trackTitle=" + this.trackTitle + " albumTitle=" + this.albumTitle + " duration=" + this.duration + "]";
        }
    }

    static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        String albumTitle;
        String artist;
        Bitmap bitmap;
        boolean clientPresent;
        long duration;
        String trackTitle;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            this.clientPresent = in.readInt() != 0 ? KeyguardTransportControlView.ANIMATE_TRANSITIONS : KeyguardTransportControlView.DEBUG;
            this.artist = in.readString();
            this.trackTitle = in.readString();
            this.albumTitle = in.readString();
            this.duration = in.readLong();
            this.bitmap = (Bitmap) Bitmap.CREATOR.createFromParcel(in);
        }

        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(this.clientPresent ? 1 : 0);
            out.writeString(this.artist);
            out.writeString(this.trackTitle);
            out.writeString(this.albumTitle);
            out.writeLong(this.duration);
            this.bitmap.writeToParcel(out, flags);
        }
    }

    private class UpdateSeekBarRunnable implements Runnable {
        private UpdateSeekBarRunnable() {
        }

        public void run() {
            if (updateOnce()) {
                KeyguardTransportControlView.this.removeCallbacks(this);
                KeyguardTransportControlView.this.postDelayed(this, KeyguardTransportControlView.QUIESCENT_PLAYBACK_FACTOR);
            }
        }

        public boolean updateOnce() {
            return KeyguardTransportControlView.this.updateSeekBars();
        }
    }

    private static final boolean playbackPositionShouldMove(int playstate) {
        switch (playstate) {
            case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
            case SlidingChallengeLayout.SCROLL_STATE_SETTLING /*2*/:
            case LayoutParams.CHILD_TYPE_EXPAND_CHALLENGE_HANDLE /*6*/:
            case MultiPaneChallengeLayout.LayoutParams.CHILD_TYPE_PAGE_DELETE_DROP_TARGET /*7*/:
            case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
            case 9:
                return DEBUG;
            default:
                return ANIMATE_TRANSITIONS;
        }
    }

    public KeyguardTransportControlView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mRemoteController = new RemoteController(context, this.mRCClientUpdateListener);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int dim = Math.max(dm.widthPixels, dm.heightPixels);
        this.mRemoteController.setArtworkConfiguration(ANIMATE_TRANSITIONS, dim, dim);
        this.mMetadataChangeTransition = null;
    }

    private void updateTransportControls(int transportControlFlags) {
        this.mTransportControlFlags = transportControlFlags;
        setSeekBarsEnabled((transportControlFlags & 256) != 0 ? ANIMATE_TRANSITIONS : DEBUG);
    }

    void setSeekBarsEnabled(boolean enabled) {
        if (enabled != this.mSeekEnabled) {
            this.mSeekEnabled = enabled;
            if (this.mTransientSeek.getVisibility() == 0 && !enabled) {
                this.mTransientSeek.setVisibility(4);
                this.mMetadataContainer.setVisibility(0);
                cancelResetToMetadata();
            }
        }
    }

    public void setTransportControlCallback(TransportControlCallback transportControlCallback) {
        this.mTransportControlCallback = transportControlCallback;
    }

    private void setEnableMarquee(boolean enabled) {
        if (this.mTrackTitle != null) {
            this.mTrackTitle.setSelected(enabled);
        }
        if (this.mTrackArtistAlbum != null) {
            this.mTrackTitle.setSelected(enabled);
        }
    }

    public void onFinishInflate() {
        super.onFinishInflate();
        this.mInfoContainer = (ViewGroup) findViewById(R.id.info_container);
        this.mMetadataContainer = (ViewGroup) findViewById(R.id.metadata_container);
        this.mBadge = (ImageView) findViewById(R.id.badge);
        this.mTrackTitle = (TextView) findViewById(R.id.title);
        this.mTrackArtistAlbum = (TextView) findViewById(R.id.artist_album);
        this.mTransientSeek = findViewById(R.id.transient_seek);
        this.mTransientSeekBar = (SeekBar) findViewById(R.id.transient_seek_bar);
        this.mTransientSeekBar.setOnSeekBarChangeListener(this.mOnSeekBarChangeListener);
        this.mTransientSeekTimeElapsed = (TextView) findViewById(R.id.transient_seek_time_elapsed);
        this.mTransientSeekTimeTotal = (TextView) findViewById(R.id.transient_seek_time_remaining);
        this.mBtnPrev = (ImageView) findViewById(R.id.btn_prev);
        this.mBtnPlay = (ImageView) findViewById(R.id.btn_play);
        this.mBtnNext = (ImageView) findViewById(R.id.btn_next);
        for (View view : new View[]{this.mBtnPrev, this.mBtnPlay, this.mBtnNext}) {
            view.setOnClickListener(this.mTransportCommandListener);
            view.setOnLongClickListener(this.mTransportShowSeekBarListener);
        }
        setEnableMarquee(KeyguardUpdateMonitor.getInstance(this.mContext).isScreenOn());
        setOnLongClickListener(this.mTransportShowSeekBarListener);
    }

    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (this.mPopulateMetadataWhenAttached != null) {
            updateMetadata(this.mPopulateMetadataWhenAttached);
            this.mPopulateMetadataWhenAttached = null;
        }
        this.mMetadata.clear();
        this.mAudioManager.registerRemoteController(this.mRemoteController);
        KeyguardUpdateMonitor.getInstance(this.mContext).registerCallback(this.mUpdateMonitor);
    }

    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        int dim = Math.max(dm.widthPixels, dm.heightPixels);
        this.mRemoteController.setArtworkConfiguration(ANIMATE_TRANSITIONS, dim, dim);
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.mAudioManager.unregisterRemoteController(this.mRemoteController);
        KeyguardUpdateMonitor.getInstance(this.mContext).removeCallback(this.mUpdateMonitor);
        this.mMetadata.clear();
        removeCallbacks(this.mUpdateSeekBars);
    }

    protected Parcelable onSaveInstanceState() {
        SavedState ss = new SavedState(super.onSaveInstanceState());
        ss.artist = this.mMetadata.artist;
        ss.trackTitle = this.mMetadata.trackTitle;
        ss.albumTitle = this.mMetadata.albumTitle;
        ss.duration = this.mMetadata.duration;
        ss.bitmap = this.mMetadata.bitmap;
        return ss;
    }

    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState ss = (SavedState) state;
            super.onRestoreInstanceState(ss.getSuperState());
            this.mMetadata.artist = ss.artist;
            this.mMetadata.trackTitle = ss.trackTitle;
            this.mMetadata.albumTitle = ss.albumTitle;
            this.mMetadata.duration = ss.duration;
            this.mMetadata.bitmap = ss.bitmap;
            populateMetadata();
            return;
        }
        super.onRestoreInstanceState(state);
    }

    void setBadgeIcon(Drawable bmp) {
        this.mBadge.setImageDrawable(bmp);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.0f);
        this.mBadge.setColorFilter(new ColorMatrixColorFilter(cm));
        this.mBadge.setXfermode(new PorterDuffXfermode(Mode.SCREEN));
        this.mBadge.setImageAlpha(239);
    }

    void clearMetadata() {
        this.mPopulateMetadataWhenAttached = null;
        this.mMetadata.clear();
        populateMetadata();
    }

    void updateMetadata(MetadataEditor data) {
        if (isAttachedToWindow()) {
            this.mMetadata.artist = data.getString(13, this.mMetadata.artist);
            this.mMetadata.trackTitle = data.getString(7, this.mMetadata.trackTitle);
            this.mMetadata.albumTitle = data.getString(1, this.mMetadata.albumTitle);
            this.mMetadata.duration = data.getLong(9, -1);
            this.mMetadata.bitmap = data.getBitmap(100, this.mMetadata.bitmap);
            populateMetadata();
            return;
        }
        this.mPopulateMetadataWhenAttached = data;
    }

    private void populateMetadata() {
        CharSequence charSequence;
        if (isLaidOut() && this.mMetadataContainer.getVisibility() == 0) {
            TransitionManager.beginDelayedTransition(this.mMetadataContainer, this.mMetadataChangeTransition);
        }
        Drawable badgeIcon = null;
        try {
            badgeIcon = getContext().getPackageManager().getApplicationIcon(this.mRemoteController.getRemoteControlClientPackageName());
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't get remote control client package icon", e);
        }
        setBadgeIcon(badgeIcon);
        TextView textView = this.mTrackTitle;
        if (TextUtils.isEmpty(this.mMetadata.trackTitle)) {
            charSequence = null;
        } else {
            charSequence = this.mMetadata.trackTitle;
        }
        textView.setText(charSequence);
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(this.mMetadata.artist)) {
            if (sb.length() != 0) {
                sb.append(" - ");
            }
            sb.append(this.mMetadata.artist);
        }
        if (!TextUtils.isEmpty(this.mMetadata.albumTitle)) {
            if (sb.length() != 0) {
                sb.append(" - ");
            }
            sb.append(this.mMetadata.albumTitle);
        }
        String trackArtistAlbum = sb.toString();
        TextView textView2 = this.mTrackArtistAlbum;
        if (TextUtils.isEmpty(trackArtistAlbum)) {
            trackArtistAlbum = null;
        }
        textView2.setText(trackArtistAlbum);
        if (this.mMetadata.duration >= 0) {
            String skeleton;
            setSeekBarsEnabled(ANIMATE_TRANSITIONS);
            setSeekBarDuration(this.mMetadata.duration);
            if (this.mMetadata.duration >= 86400000) {
                skeleton = "DDD kk mm ss";
            } else if (this.mMetadata.duration >= 3600000) {
                skeleton = "kk mm ss";
            } else {
                skeleton = "mm ss";
            }
            this.mFormat = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(getContext().getResources().getConfiguration().locale, skeleton));
            this.mFormat.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        } else {
            setSeekBarsEnabled(DEBUG);
        }
        KeyguardUpdateMonitor.getInstance(getContext()).dispatchSetBackground(this.mMetadata.bitmap);
        int flags = this.mTransportControlFlags;
        setVisibilityBasedOnFlag(this.mBtnPrev, flags, 1);
        setVisibilityBasedOnFlag(this.mBtnNext, flags, 128);
        setVisibilityBasedOnFlag(this.mBtnPlay, flags, 60);
        updatePlayPauseState(this.mCurrentPlayState);
    }

    void updateSeekDisplay() {
        if (this.mMetadata != null && this.mRemoteController != null && this.mFormat != null) {
            this.mTempDate.setTime(this.mRemoteController.getEstimatedMediaPosition());
            this.mTransientSeekTimeElapsed.setText(this.mFormat.format(this.mTempDate));
            this.mTempDate.setTime(this.mMetadata.duration);
            this.mTransientSeekTimeTotal.setText(this.mFormat.format(this.mTempDate));
        }
    }

    boolean tryToggleSeekBar() {
        TransitionManager.beginDelayedTransition(this.mInfoContainer);
        if (this.mTransientSeek.getVisibility() == 0) {
            this.mTransientSeek.setVisibility(4);
            this.mMetadataContainer.setVisibility(0);
            cancelResetToMetadata();
            removeCallbacks(this.mUpdateSeekBars);
        } else {
            this.mTransientSeek.setVisibility(0);
            this.mMetadataContainer.setVisibility(4);
            delayResetToMetadata();
            if (playbackPositionShouldMove(this.mCurrentPlayState)) {
                this.mUpdateSeekBars.run();
            } else {
                this.mUpdateSeekBars.updateOnce();
            }
        }
        this.mTransportControlCallback.userActivity();
        return ANIMATE_TRANSITIONS;
    }

    void resetToMetadata() {
        TransitionManager.beginDelayedTransition(this.mInfoContainer);
        if (this.mTransientSeek.getVisibility() == 0) {
            this.mTransientSeek.setVisibility(4);
            this.mMetadataContainer.setVisibility(0);
        }
    }

    void delayResetToMetadata() {
        removeCallbacks(this.mResetToMetadata);
        postDelayed(this.mResetToMetadata, 5000);
    }

    void cancelResetToMetadata() {
        removeCallbacks(this.mResetToMetadata);
    }

    void setSeekBarDuration(long duration) {
        this.mTransientSeekBar.setMax((int) duration);
    }

    void scrubTo(int progress) {
        this.mRemoteController.seekTo((long) progress);
        this.mTransportControlCallback.userActivity();
    }

    private static void setVisibilityBasedOnFlag(View view, int flags, int flag) {
        if ((flags & flag) != 0) {
            view.setVisibility(0);
        } else {
            view.setVisibility(4);
        }
    }

    private void updatePlayPauseState(int state) {
        if (state != this.mCurrentPlayState) {
            int imageResId;
            int imageDescId;
            switch (state) {
                case SlidingChallengeLayout.SCROLL_STATE_FADING /*3*/:
                    imageResId = R.drawable.ic_media_pause;
                    imageDescId = R.string.keyguard_transport_pause_description;
                    break;
                case KeyguardViewDragHelper.EDGE_BOTTOM /*8*/:
                    imageResId = R.drawable.ic_media_stop;
                    imageDescId = R.string.keyguard_transport_stop_description;
                    break;
                case 9:
                    imageResId = R.drawable.stat_sys_warning;
                    imageDescId = R.string.keyguard_transport_play_description;
                    break;
                default:
                    imageResId = R.drawable.ic_media_play;
                    imageDescId = R.string.keyguard_transport_play_description;
                    break;
            }
            boolean clientSupportsSeek = (this.mMetadata == null || this.mMetadata.duration <= 0) ? DEBUG : ANIMATE_TRANSITIONS;
            setSeekBarsEnabled(clientSupportsSeek);
            this.mBtnPlay.setImageResource(imageResId);
            this.mBtnPlay.setContentDescription(getResources().getString(imageDescId));
            this.mCurrentPlayState = state;
        }
    }

    boolean updateSeekBars() {
        int position = (int) this.mRemoteController.getEstimatedMediaPosition();
        if (position >= 0) {
            this.mTransientSeekBar.setProgress(position);
            return ANIMATE_TRANSITIONS;
        }
        Log.w(TAG, "Updating seek bars; received invalid estimated media position (" + position + "). Disabling seek.");
        setSeekBarsEnabled(DEBUG);
        return DEBUG;
    }

    private void sendMediaButtonClick(int keyCode) {
        this.mRemoteController.sendMediaKeyEvent(new KeyEvent(0, keyCode));
        this.mRemoteController.sendMediaKeyEvent(new KeyEvent(1, keyCode));
        this.mTransportControlCallback.userActivity();
    }

    public boolean providesClock() {
        return DEBUG;
    }
}
