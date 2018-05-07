/*
 *
 * Copyright 2015 TedXiong xiong-wei@hotmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.liteav.demo.play.wkvideoplayer.view;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.tencent.liteav.demo.DemoApplication;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.play.TXPlayerAuthParam;
import com.tencent.liteav.demo.play.wkvideoplayer.model.Video;
import com.tencent.liteav.demo.play.wkvideoplayer.model.VideoUrl;
import com.tencent.rtmp.ITXVodPlayListener;
import com.tencent.rtmp.TXBitrateItem;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.TXPlayerAuthBuilder;
import com.tencent.rtmp.TXVodPlayConfig;
import com.tencent.rtmp.TXVodPlayer;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

/**
 * SuperVideoPlayer
 */
public class SuperVideoPlayer extends RelativeLayout {
    private final String TAG = "SuperVideoPlayer";

    private static final int MSG_HIDE_CONTROLLER = 10;
    private static final int MSG_UPDATE_PLAY_TIME = 11;
    private static final int MSG_PLAY_ON_TV_RESULT = 12;
    private static final int MSG_EXIT_FORM_TV_RESULT = 13;
    private MediaController.PageType mCurrPageType = MediaController.PageType.SHRINK;//当前是横屏还是竖屏

    private Context mContext;
    private MediaController mMediaController;
    private MediaToolbar mMediaToolbar;
    private Timer mUpdateTimer;
    private VideoPlayCallbackImpl mVideoPlayCallback;
    private ResolutionPanel mResolutionView;
    private MoreSettingPanel mMoreView;
    private DanmuView mDanmuView;
    private View mProgressBarView;

    private ArrayList<Video> mAllVideo;
    private Video mNowPlayVideo;
    private ArrayList<TXPlayerAuthParam> mVodCopyList;

    //是否自动隐藏控制栏
    private boolean mAutoHideController = true;

    private TXVodPlayer mTxplayer;
    private TXVodPlayer mTXPlayerGetInfo;
    private TXCloudVideoView mCloudVideoView;
    private TXVodPlayConfig mPlayConfig;
    private MyHandler mHandler;
    private OnPlayInfoCallback mOnPlayInfoCallback;

    public void setVideoPlayInfoCallback(OnPlayInfoCallback callback) {
        mOnPlayInfoCallback = callback;
    }

    private static class MyHandler extends Handler {
        private final WeakReference<SuperVideoPlayer> mPlayer;

        public MyHandler(SuperVideoPlayer player) {
            mPlayer = new WeakReference<SuperVideoPlayer>(player);
        }

        @Override
        public void handleMessage(Message msg) {
            SuperVideoPlayer player = mPlayer.get();
            if (player != null) {
                if (msg.what == SuperVideoPlayer.MSG_UPDATE_PLAY_TIME) {
                    player.updatePlayTime();
                    player.updatePlayProgress();
                } else if (msg.what == SuperVideoPlayer.MSG_HIDE_CONTROLLER) {
                    player.showOrHideController();
                } else if (msg.what == SuperVideoPlayer.MSG_PLAY_ON_TV_RESULT) {
                } else if (msg.what == SuperVideoPlayer.MSG_EXIT_FORM_TV_RESULT) {
                }
            }
        }
    }

    private View.OnTouchListener mOnTouchVideoListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            int action = motionEvent.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                showOrHideController();
                hideMoreView();
            } else if (action == MotionEvent.ACTION_MOVE) {

            } else if (action == MotionEvent.ACTION_UP) {

            }
            return mCurrPageType == MediaController.PageType.EXPAND;
        }
    };

    private void hideMoreView() {
        if (mMoreView != null && mMoreView.getVisibility() == View.VISIBLE) {
            mMoreView.setVisibility(View.GONE);
        }
    }

    public void playFileID(TXPlayerAuthBuilder authBuilder) {
        if (mTxplayer != null) {
            mTxplayer.startPlay(authBuilder);
        }
    }

    public void onPause() {
        if (mDanmuView != null && mDanmuView.isPrepared()) {
            mDanmuView.pause();
        }
        if (mTxplayer != null) {
            mTxplayer.pause();
        }
        if (mMediaController != null){
            mMediaController.setPlayState(MediaController.PlayState.PAUSE);
        }
    }

    public void onResume() {
        if (mDanmuView != null && mDanmuView.isPrepared() && mDanmuView.isPaused()) {
            mDanmuView.resume();
        }
        if (mTxplayer != null) {
            mTxplayer.resume();
        }
        if (mMediaController != null){
            mMediaController.setPlayState(MediaController.PlayState.PLAY);
        }
    }

    public void setVideoListInfo(ArrayList<TXPlayerAuthParam> vodList) {
        mVodCopyList.clear();
        mVodCopyList.addAll(vodList);
    }

    public void getNextInfo() {
        if (mVodCopyList == null || mVodCopyList.size() == 0)
            return;

        TXPlayerAuthParam playerAuthParam = mVodCopyList.remove(0);

        mTXPlayerGetInfo.setAutoPlay(false);

        TXPlayerAuthBuilder authBuilder = new TXPlayerAuthBuilder();
        try {
            authBuilder.setAppId(Integer.parseInt(playerAuthParam.appId));
            authBuilder.setFileId(playerAuthParam.fileId);
//        authBuilder.setTimeout("5c08d9fa");
//        authBuilder.setUs("someus");
//        authBuilder.setSign("d6581029cb5fbe44f938b1b4fadbb0dc");
            mTXPlayerGetInfo.startPlay(authBuilder);
        } catch (NumberFormatException e) {
            Toast.makeText(mContext, "请输入正确的AppId", Toast.LENGTH_SHORT).show();
        }
    }

    public void setPlayUrl(String vodUrl) {
        mTxplayer.stopPlay(true);
        mTxplayer.setAutoPlay(true);
        int result = mTxplayer.startPlay(vodUrl); // result返回值：0 success;  -1 empty url;
    }

    public void addVodInfo(TXPlayerAuthParam param) {
        mVodCopyList.add(param);
        getNextInfo();
    }

    public void updateUI(String title) {
        mMediaToolbar.udpateTitle(title);
        mMediaController.updateUI();
    }

    private class MediaControl implements MediaController.MediaControlImpl, MediaToolbar.MediaToolbarImpl, ResolutionPanel.ResolutionChangeImpl, MoreSettingPanel.MoreSettingChangeImpl {

        @Override
        public void alwaysShowController() {
            SuperVideoPlayer.this.alwaysShowController();
        }

        @Override
        public void onPlayTurn() {
            if (mTxplayer.isPlaying()) {
                pausePlay(true);
            } else {
                goOnPlay();
            }
        }

        @Override
        public void onPageTurn() {
            mVideoPlayCallback.onSwitchPageType();
        }

        @Override
        public void onResolutionTurn() {
            if (mResolutionView.getVisibility() == GONE) {
                mResolutionView.setVisibility(View.VISIBLE);
            } else {
                mResolutionView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onProgressTurn(MediaController.ProgressState state, int progress) {
            if (mTxplayer == null)
                return;
            if (state.equals(MediaController.ProgressState.START)) {
                mHandler.removeMessages(MSG_HIDE_CONTROLLER);
            } else if (state.equals(MediaController.ProgressState.STOP)) {
                resetHideTimer();
            } else {
                float time = progress * mTxplayer.getDuration() / 100;
                mTxplayer.seek(time);
                updatePlayTime();
            }
        }

        @Override
        public void onDanmaku(boolean on) {
            if (mDanmuView != null) {
                mDanmuView.toggle(on);
            }
        }

        @Override
        public void onSnapshoot() {
            mTxplayer.snapshot(new TXLivePlayer.ITXSnapshotListener() {
                @Override
                public void onSnapshot(Bitmap bmp) {
                    showSnapWindow(bmp);
                }
            });
        }

        @Override
        public void onMoreSetting() {
            if (mMoreView.getVisibility() == GONE) {
                mMoreView.setVisibility(View.VISIBLE);
            } else {
                mMoreView.setVisibility(View.GONE);
            }
        }


        @Override
        public void onResolutionChange(int index) {
            if (mTxplayer == null)
                return;
            mTxplayer.setBitrateIndex(index);
            mMediaController.updateResolutionTxt(index);
        }

        @Override
        public void onRateChange(float rate) {
            if (mTxplayer == null)
                return;
            mTxplayer.setRate(rate);
        }

        @Override
        public void onMirrorChange(boolean change) {
            if (mTxplayer != null)
                mTxplayer.setMirror(change);
        }

        @Override
        public void onBack() {
            mVideoPlayCallback.onBack();
        }
    }

    private void showSnapWindow(Bitmap bmp) {
        PopupWindow popupWindow = new PopupWindow(mContext);
        popupWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        View view = LayoutInflater.from(mContext).inflate(R.layout.layout_new_vod_snap, null);
        ImageView imageView = (ImageView) view.findViewById(R.id.iv_snap);
        imageView.setImageBitmap(bmp);
        popupWindow.setContentView(view);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.showAsDropDown(mMediaToolbar, 1800, 0);
    }

    private MediaControl mMediaControl = new MediaControl();


    private void onRenderStart() {
        mProgressBarView.setVisibility(View.GONE);
    }

    private void onCompletion() {
        stopUpdateTimer();
        stopHideTimer(true);
        mMediaController.playFinish((int) (mTxplayer.getDuration() * 1000));
        mVideoPlayCallback.onPlayFinish();
        Toast.makeText(mContext, "视频播放完成", Toast.LENGTH_SHORT).show();
        mPhoneListener.stopListen();
    }


    public void setVideoPlayCallback(VideoPlayCallbackImpl videoPlayCallback) {
        mVideoPlayCallback = videoPlayCallback;
    }

    public void setPageType(MediaController.PageType pageType) {
        mMediaController.setPageType(pageType);
        mCurrPageType = pageType;
    }

    /***
     * 强制横屏模式
     */
    @SuppressWarnings("unused")
    public void forceLandscapeMode() {
        mMediaController.forceLandscapeMode();
    }


    /**
     * 播放多个视频,默认播放第一个视频，第一个格式
     *
     * @param allVideo 所有视频
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo) {
        loadMultipleVideo(allVideo, 0, 0);
    }

    /**
     * 播放多个视频
     *
     * @param allVideo     所有的视频
     * @param selectVideo  指定的视频
     * @param selectFormat 指定的格式
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo, int selectVideo, int selectFormat) {
        loadMultipleVideo(allVideo, selectVideo, selectFormat, 0);
    }

    /***
     *
     * @param allVideo     所有的视频
     * @param selectVideo  指定的视频
     * @param selectFormat 指定的格式
     * @param seekTime 开始进度
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo, int selectVideo, int selectFormat, int seekTime) {
        if (null == allVideo || allVideo.size() == 0) {
            Toast.makeText(mContext, "视频列表为空", Toast.LENGTH_SHORT).show();
            return;
        }
        mAllVideo.clear();
        mAllVideo.addAll(allVideo);
        mNowPlayVideo = mAllVideo.get(selectVideo);
        mNowPlayVideo.setPlayUrl(selectFormat);
        mMediaController.initVideoList(mAllVideo);
        mMediaController.initPlayVideo(mNowPlayVideo);
        loadAndPlay(mNowPlayVideo.getPlayUrl(), seekTime);
        mPhoneListener.startListen();
    }

    /**
     * 暂停播放
     *
     * @param isShowController 是否显示控制条
     */
    public void pausePlay(boolean isShowController) {
        mTxplayer.pause();
        mMediaController.setPlayState(MediaController.PlayState.PAUSE);
        stopHideTimer(isShowController);
    }

    /***
     * 继续播放
     */
    public void goOnPlay() {
        mTxplayer.resume();
        mMediaController.setPlayState(MediaController.PlayState.PLAY);
        resetHideTimer();
        resetUpdateTimer();
    }

    /**
     * 关闭视频
     */
    public void onDestroy() {
        mMediaController.setPlayState(MediaController.PlayState.PAUSE);
        stopHideTimer(true);
        stopUpdateTimer();
        mTxplayer.stopPlay(false);

        if (mDanmuView != null) {
            mDanmuView.release();
            mDanmuView = null;
        }
    }


    public boolean isAutoHideController() {
        return mAutoHideController;
    }

    public void setAutoHideController(boolean autoHideController) {
        mAutoHideController = autoHideController;
    }

    public SuperVideoPlayer(Context context) {
        super(context);
        initView(context);
    }

    public SuperVideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    public SuperVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    private void initView(Context context) {
        mContext = context;
        View.inflate(context, R.layout.super_vodeo_player_layout, this);
        mCloudVideoView = (TXCloudVideoView) findViewById(R.id.cloud_video_view);
        mMediaController = (MediaController) findViewById(R.id.controller);
        mMediaToolbar = (MediaToolbar) findViewById(R.id.toolbar);
        mProgressBarView = findViewById(R.id.progressbar);
        mResolutionView = (ResolutionPanel) findViewById(R.id.resolutionPanel);
        mMoreView = (MoreSettingPanel) findViewById(R.id.morePanel);
        mDanmuView = (DanmuView) findViewById(R.id.danmaku_view);

        mMediaController.setMediaControl(mMediaControl);
        mMediaToolbar.setMediaControl(mMediaControl);
        mCloudVideoView.setOnTouchListener(mOnTouchVideoListener);
        mResolutionView.setResolutionChangeListener(mMediaControl);
        mMoreView.setMoreSettingChangeControl(mMediaControl);

        mTxplayer = new TXVodPlayer(context);
        mPlayConfig = new TXVodPlayConfig();
        mPlayConfig.setCacheFolderPath(getInnerSDCardPath() + "/txcache");
        mPlayConfig.setMaxCacheItems(5);
        mTxplayer.setConfig(mPlayConfig);
        mTxplayer.setRenderMode(TXLiveConstants.RENDER_MODE_ADJUST_RESOLUTION);
        mTxplayer.setPlayerView(mCloudVideoView);

        mTXPlayerGetInfo = new TXVodPlayer(context);
        mTxplayer.setVodListener(mPlayVodListener);
        mTXPlayerGetInfo.setVodListener(mGetVodInfoListener);
        mAllVideo = new ArrayList<>();
        mVodCopyList = new ArrayList<>();

        mHandler = new MyHandler(this);
    }

    public String getInnerSDCardPath() {
        return Environment.getExternalStorageDirectory().getPath();
    }

    private ITXVodPlayListener mPlayVodListener = new ITXVodPlayListener() {
        @Override
        public void onPlayEvent(TXVodPlayer player, int event, Bundle param) {
            String playEventLog = "receive event: " + event + ", " + param.getString(TXLiveConstants.EVT_DESCRIPTION);
            Log.d(TAG, playEventLog);

            if (event == TXLiveConstants.PLAY_EVT_PLAY_BEGIN) {
                if (mPhoneListener.isInBackground()) {
                    mTxplayer.pause();
                }
                ArrayList<TXBitrateItem> bitrateItems = mTxplayer.getSupportedBitrates();
                Collections.sort(bitrateItems);
                mResolutionView.setDataSource(bitrateItems);
                mMediaController.setDataSource(bitrateItems);
            } else if (event == TXLiveConstants.PLAY_EVT_PLAY_PROGRESS) {

                int progress = param.getInt(TXLiveConstants.EVT_PLAY_PROGRESS_MS);
                int duration = param.getInt(TXLiveConstants.EVT_PLAY_DURATION_MS);
                int playable = param.getInt(TXLiveConstants.EVT_PLAYABLE_DURATION_MS);


                return;
            } else if (event == TXLiveConstants.PLAY_ERR_NET_DISCONNECT || event == TXLiveConstants.PLAY_EVT_PLAY_END || event == TXLiveConstants.PLAY_ERR_FILE_NOT_FOUND) {
                onCompletion();
            } else if (event == TXLiveConstants.PLAY_EVT_PLAY_LOADING) {

            } else if (event == TXLiveConstants.PLAY_EVT_RCV_FIRST_I_FRAME) {
                if (mPhoneListener.isInBackground()) {
                    mTxplayer.pause();
                }
                onRenderStart();
            } else if (event == TXLiveConstants.PLAY_EVT_CHANGE_RESOLUTION) {
            } else if (event == TXLiveConstants.PLAY_ERR_HLS_KEY) {
            } else if (event == TXLiveConstants.PLAY_WARNING_RECONNECT) {
            }

            if (event < 0) {
                Toast.makeText(mContext, param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onNetStatus(TXVodPlayer player, Bundle status) {
            printNetStatusLog(player, status);
        }
    };
    private ITXVodPlayListener mGetVodInfoListener = new ITXVodPlayListener() {
        @Override
        public void onPlayEvent(TXVodPlayer player, int event, Bundle param) {
            String playEventLog = "receive event: " + event + ", " + param.getString(TXLiveConstants.EVT_DESCRIPTION);
            Log.d(TAG, playEventLog);

            if (event == TXLiveConstants.PLAY_EVT_GET_PLAYINFO_SUCC) { // 获取点播文件信息成功
                VodRspData data = new VodRspData();
                data.cover = param.getString(TXLiveConstants.EVT_PLAY_COVER_URL);
                data.duration = param.getInt(TXLiveConstants.EVT_PLAY_DURATION);
                data.url = param.getString(TXLiveConstants.EVT_PLAY_URL);
                data.title = param.getString(TXLiveConstants.EVT_PLAY_DESCRIPTION);
                if (data.title == null || data.title.length() == 0) {
                    data.title = param.getString(TXLiveConstants.EVT_PLAY_NAME);
                }
                if (mVideoPlayCallback != null) {
                    mVideoPlayCallback.onLoadVideoInfo(data);
                }
                getNextInfo();
            }
            if (mOnPlayInfoCallback != null) {
                mOnPlayInfoCallback.onPlayInfoCallback(event);
            }
            if (event < 0) {
                Toast.makeText(mContext, param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onNetStatus(TXVodPlayer player, Bundle status) {
            printNetStatusLog(player, status);
        }
    };

    private void printNetStatusLog(TXVodPlayer player, Bundle status) {
        String str = getNetStatusString(status);
        Log.d(TAG, player.toString() + " Current status CPU:" + status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE) +
                ", RES:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH) + "*" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT) +
                ", SPD:" + status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED) + "Kbps" +
                ", FPS:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS) +
                ", ARA:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE) + "Kbps" +
                ", VRA:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE) + "Kbps");
    }

    /**
     * 加载并开始播放视频
     *
     * @param videoUrl videoUrl
     */
    private void loadAndPlay(VideoUrl videoUrl, int seekTime) {
        showProgressView(seekTime > 0);
        if (TextUtils.isEmpty(videoUrl.getFormatUrl())) {
            Log.e("TAG", "videoUrl should not be null");
            return;
        }
        if (null == mUpdateTimer) resetUpdateTimer();
        resetHideTimer();

        mTxplayer.startPlay(videoUrl.getFormatUrl());
        mMediaController.setPlayState(MediaController.PlayState.PLAY);
    }

    public void loadVideo() {
        if (null == mUpdateTimer) resetUpdateTimer();
        resetHideTimer();
        mMediaController.setPlayState(MediaController.PlayState.PLAY);
    }

    /**
     * 更新播放的进度时间
     */
    private void updatePlayTime() {
        int allTime = (int) (mTxplayer.getDuration() * 1000);
        int playTime = (int) (mTxplayer.getCurrentPlaybackTime() * 1000);
        mMediaController.setPlayProgressTxt(playTime, allTime);
    }

    /**
     * 更新播放进度条
     */
    private void updatePlayProgress() {
        int allTime = (int) (mTxplayer.getDuration() * 1000);
        int playTime = (int) (mTxplayer.getCurrentPlaybackTime() * 1000);
        int bufferTime = (int) (mTxplayer.getBufferDuration() * 1000);
        if (allTime > 0) {
            int progress = playTime * 100 / allTime;
            int loadProgress = bufferTime * 100 / allTime;
            mMediaController.setProgressBar(progress, loadProgress);
        }
    }

    /**
     * 显示loading圈
     *
     * @param isTransparentBg isTransparentBg
     */
    private void showProgressView(Boolean isTransparentBg) {
        mProgressBarView.setVisibility(VISIBLE);
        if (!isTransparentBg) {
            mProgressBarView.setBackgroundResource(android.R.color.black);
        } else {
            mProgressBarView.setBackgroundResource(android.R.color.transparent);
        }
    }

    /***
     *
     */
    private void showOrHideController() {
        if (mResolutionView.getVisibility() == View.VISIBLE) {
            mResolutionView.setVisibility(View.GONE);
            return;
        }

        if (mMediaController.getVisibility() == View.VISIBLE) {
            Animation animation = AnimationUtils.loadAnimation(mContext,
                    R.anim.anim_exit_from_bottom);
            animation.setAnimationListener(new AnimationImp() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    super.onAnimationEnd(animation);
                    mMediaController.setVisibility(View.GONE);
                }
            });
            mMediaController.startAnimation(animation);
        } else {
            mMediaController.setVisibility(View.VISIBLE);
            mMediaController.clearAnimation();
            Animation animation = AnimationUtils.loadAnimation(mContext,
                    R.anim.anim_enter_from_bottom);
            mMediaController.startAnimation(animation);
            resetHideTimer();
        }
        if (mMediaToolbar.getVisibility() == View.VISIBLE) {
            Animation animation = AnimationUtils.loadAnimation(mContext,
                    R.anim.anim_exit_from_top);
            animation.setAnimationListener(new AnimationImp() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    super.onAnimationEnd(animation);
                    mMediaToolbar.setVisibility(View.GONE);
                }
            });
            mMediaToolbar.startAnimation(animation);
        } else {
            mMediaToolbar.setVisibility(View.VISIBLE);
            mMediaToolbar.clearAnimation();
            Animation animation = AnimationUtils.loadAnimation(mContext,
                    R.anim.anim_enter_from_top);
            mMediaToolbar.startAnimation(animation);
        }
    }

    private void alwaysShowController() {
        mHandler.removeMessages(MSG_HIDE_CONTROLLER);
        mMediaController.setVisibility(View.VISIBLE);
        mMediaToolbar.setVisibility(View.VISIBLE);
    }

    private void resetHideTimer() {
        if (!isAutoHideController()) return;
        mHandler.removeMessages(MSG_HIDE_CONTROLLER);
        int TIME_SHOW_CONTROLLER = 4000;
        mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROLLER, TIME_SHOW_CONTROLLER);
    }

    private void stopHideTimer(boolean isShowController) {
        mHandler.removeMessages(MSG_HIDE_CONTROLLER);
        mMediaController.clearAnimation();
        mMediaController.setVisibility(isShowController ? View.VISIBLE : View.GONE);
    }

    private void resetUpdateTimer() {
        mUpdateTimer = new Timer();
        int TIME_UPDATE_PLAY_TIME = 1000;
        mUpdateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
            }
        }, 0, TIME_UPDATE_PLAY_TIME);
    }

    private void stopUpdateTimer() {
        if (mUpdateTimer != null) {
            mUpdateTimer.cancel();
            mUpdateTimer = null;
        }
    }

    private class AnimationImp implements Animation.AnimationListener {

        @Override
        public void onAnimationEnd(Animation animation) {

        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    }

    public interface VideoPlayCallbackImpl {
        void onCloseVideo();

        void onSwitchPageType();

        void onPlayFinish();

        void onBack();

        void onLoadVideoInfo(VodRspData data);
    }

    //公用打印辅助函数
    protected String getNetStatusString(Bundle status) {
        String str = String.format("%-14s %-14s %-12s\n%-14s %-14s %-12s\n%-14s %-14s %-12s\n%-14s %-12s %-12s",
                "CPU:" + status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE),
                "RES:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH) + "*" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT),
                "SPD:" + status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED) + "Kbps",
                "JIT:" + status.getInt(TXLiveConstants.NET_STATUS_NET_JITTER),
                "FPS:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS),
                "ARA:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE) + "Kbps",
                "QUE:" + status.getInt(TXLiveConstants.NET_STATUS_CACHE_SIZE) + "|" + status.getInt(TXLiveConstants.NET_STATUS_CODEC_CACHE),
                "DRP:" + status.getInt(TXLiveConstants.NET_STATUS_CODEC_DROP_CNT) + "|" + status.getInt(TXLiveConstants.NET_STATUS_DROP_SIZE),
                "VRA:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE) + "Kbps",
                "SVR:" + status.getString(TXLiveConstants.NET_STATUS_SERVER_IP),
                "AVRA:" + status.getInt(TXLiveConstants.NET_STATUS_SET_VIDEO_BITRATE),
                "PLA:" + status.getInt(TXLiveConstants.NET_STATUS_PLAYABLE_DURATION));
        return str;
    }

    static class TXPhoneStateListener extends PhoneStateListener implements Application.ActivityLifecycleCallbacks {
        private WeakReference<SuperVideoPlayer> mPlayer;
        int activityCount;

        public TXPhoneStateListener(SuperVideoPlayer superVideoPlayer) {
            mPlayer = new WeakReference<SuperVideoPlayer>(superVideoPlayer);
        }

        public void startListen() {
            SuperVideoPlayer player = mPlayer.get();
            if (player != null) {
                player.startListen();
            }
        }

        public void stopListen() {
            SuperVideoPlayer player = mPlayer.get();
            if (player != null) {
                player.stopListen();
            }
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);

            SuperVideoPlayer player = mPlayer.get();
            if (player != null) {
                player.onCallStateChange(state, activityCount);
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {
            activityCount++;
            Log.d("SuperVideoPlayer", "onActivityResumed" + activityCount);
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {
            activityCount--;
            Log.d("SuperVideoPlayer", "onActivityStopped" + activityCount);
        }

        boolean isInBackground() {
            return (activityCount < 0);
        }
    }

    private void stopListen() {
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);

        DemoApplication.getApplication().unregisterActivityLifecycleCallbacks(mPhoneListener);
    }

    private void startListen() {
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);

        DemoApplication.getApplication().registerActivityLifecycleCallbacks(mPhoneListener);
    }

    private void onCallStateChange(int state, int activityCount) {
        TXVodPlayer player = mTxplayer;
        switch (state) {
            //电话等待接听
            case TelephonyManager.CALL_STATE_RINGING:
                Log.d(TAG, "CALL_STATE_RINGING");
                if (player != null) player.pause();
                break;
            //电话接听
            case TelephonyManager.CALL_STATE_OFFHOOK:
                Log.d(TAG, "CALL_STATE_OFFHOOK");
                if (player != null) player.pause();
                break;
            //电话挂机
            case TelephonyManager.CALL_STATE_IDLE:
                Log.d(TAG, "CALL_STATE_IDLE");
                if (player != null && activityCount >= 0) player.resume();
                break;
        }
    }

    private TXPhoneStateListener mPhoneListener = new TXPhoneStateListener(this);

    public interface OnPlayInfoCallback {
        void onPlayInfoCallback(int ret);
    }

}