package com.tencent.liteav.demo.liveroom.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.os.Handler;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.common.widget.BeautySettingPannel;
import com.tencent.liteav.demo.liveroom.ILiveRoomListener;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.liveroom.LiveRoom;
import com.tencent.liteav.demo.common.misc.ChatMessageAdapter;
import com.tencent.liteav.demo.common.misc.TextChatMsg;
import com.tencent.liteav.demo.liveroom.ui.LiveRoomActivityInterface;
import com.tencent.liteav.demo.common.misc.HintDialog;
import com.tencent.liteav.demo.common.misc.TextMsgInputDialog;
import com.tencent.liteav.demo.common.misc.SwipeAnimationController;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Iterator;

public class LiveRoomChatFragment extends Fragment implements BeautySettingPannel.IOnBeautyParamsChangeListener, ILiveRoomListener {

    private static final String TAG = LiveRoomChatFragment.class.getSimpleName();

    private Handler mHandler;

    private Activity mActivity;
    private LiveRoomActivityInterface mActivityInterface;

    private String mSelfUserId;
    private RoomInfo mRoomInfo;

    private List<PusherInfo> mPusherList = new ArrayList<>();
    private List<RoomVideoView> mPlayerViews = new ArrayList<>();

    private ListView mChatListView;
    private ArrayList<TextChatMsg> mChatMsgList;
    private ChatMessageAdapter mChatMsgAdapter;

    private Button mBtnLinkMic;
    private LinearLayout mOperatorLayout;
    private BeautySettingPannel mBeautyPannelView;
    private TextMsgInputDialog mTextMsgInputDialog;
    private SwipeAnimationController mSwipeAnimationController;

    private int mShowLogFlag = 0;
    private int mBeautyLevel = 5;
    private int mWhiteningLevel = 3;
    private int mRuddyLevel = 2;
    private int mBeautyStyle = TXLiveConstants.BEAUTY_STYLE_SMOOTH;

    private boolean mCreateRoom = false;
    private boolean mPusherMute = false;

    private boolean mPendingRequest = false;
    private boolean mIsBeingLinkMic = false;


    public static LiveRoomChatFragment newInstance(RoomInfo config, String userID, boolean createRoom) {
        LiveRoomChatFragment fragment = new LiveRoomChatFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable("roomInfo", config);
        bundle.putString("userID", userID);
        bundle.putBoolean("createRoom", createRoom);
        fragment.setArguments(bundle);
        return fragment;
    }

    /***********************************************************************************************************************************************
     *
     *                                                      Fragment生命周期函数调用顺序
     *
     *     onAttach() --> onCreateView() --> onActivityCreated() --> onResume() --> onPause() --> onDestroyView() --> onDestroy() --> onDetach()
     *
     ***********************************************************************************************************************************************/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = ((Activity) context);
        mActivityInterface = ((LiveRoomActivityInterface) context);

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = ((Activity) activity);
        mActivityInterface = ((LiveRoomActivityInterface) activity);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_live_room_chat, container, false);

        TXCloudVideoView videoViews[] = new TXCloudVideoView[3];
        videoViews[0] = ((TXCloudVideoView) view.findViewById(R.id.video_player1));
        videoViews[1] = ((TXCloudVideoView) view.findViewById(R.id.video_player2));
        videoViews[2] = ((TXCloudVideoView) view.findViewById(R.id.video_player3));

        Button kickoutBtns[] = {null, null, null};
        kickoutBtns[0] = (Button) view.findViewById(R.id.btn_kick_out1);
        kickoutBtns[1] = (Button) view.findViewById(R.id.btn_kick_out2);
        kickoutBtns[2] = (Button) view.findViewById(R.id.btn_kick_out3);

        FrameLayout loadingBkgs[] = {null, null, null};
        loadingBkgs[0] = (FrameLayout) view.findViewById(R.id.loading_background1);
        loadingBkgs[1] = (FrameLayout) view.findViewById(R.id.loading_background2);
        loadingBkgs[2] = (FrameLayout) view.findViewById(R.id.loading_background3);

        ImageView loadingImgs[] = {null, null, null};
        loadingImgs[0] = (ImageView) view.findViewById(R.id.loading_imageview1);
        loadingImgs[1] = (ImageView) view.findViewById(R.id.loading_imageview2);
        loadingImgs[2] = (ImageView) view.findViewById(R.id.loading_imageview3);

        mPlayerViews.add(new RoomVideoView(videoViews[0], kickoutBtns[0], loadingBkgs[0], loadingImgs[0]));
        mPlayerViews.add(new RoomVideoView(videoViews[1], kickoutBtns[1], loadingBkgs[1], loadingImgs[1]));
        mPlayerViews.add(new RoomVideoView(videoViews[2], kickoutBtns[2], loadingBkgs[2], loadingImgs[2]));

        //切换摄像头
        (view.findViewById(R.id.rtmproom_camera_switcher_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActivityInterface != null)
                    mActivityInterface.getLiveRoom().switchCamera();
            }
        });

        //美颜p图部分
        mBeautyPannelView = (BeautySettingPannel) view.findViewById(R.id.layoutFaceBeauty);
        mBeautyPannelView.setBeautyParamsChangeListener(this);
        mOperatorLayout = (LinearLayout) view.findViewById(R.id.controller_container);
        view.findViewById(R.id.rtmproom_beauty_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBeautyPannelView.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                mOperatorLayout.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            }
        });

        //连麦
        mBtnLinkMic = (Button) view.findViewById(R.id.rtmproom_linkmic_btn);
        mBtnLinkMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsBeingLinkMic) {
                    stopLinkMic();
                } else {
                    startLinkMic();
                }
            }
        });

        //静音推流
        view.findViewById(R.id.rtmproom_mute_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPusherMute = !mPusherMute;
                mActivityInterface.getLiveRoom().setMute(mPusherMute);
                v.setBackgroundResource(mPusherMute ? R.drawable.mic_disable : R.drawable.mic_normal);
            }
        });

        //日志
        (view.findViewById(R.id.rtmproom_log_switcher_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchLog();
            }
        });

        //发送消息
        (view.findViewById(R.id.rtmproom_chat_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputMsgDialog();
            }
        });

        mTextMsgInputDialog = new TextMsgInputDialog(mActivity, R.style.InputDialog);
        mTextMsgInputDialog.setmOnTextSendListener(new TextMsgInputDialog.OnTextSendListener() {
            @Override
            public void onTextSend(String msg, boolean tanmuOpen) {
                sendMessage(msg);
            }
        });

        mCreateRoom = getArguments().getBoolean("createRoom");
        if (mCreateRoom) {
            //大主播隐藏掉连麦入口
            (view.findViewById(R.id.linkmic_btn_view)).setVisibility(View.GONE);
        } else {
            //普通观众隐藏掉切换摄像头、美颜和静音推流的入口
            (view.findViewById(R.id.camera_switch_view)).setVisibility(View.GONE);
            (view.findViewById(R.id.beauty_btn_view)).setVisibility(View.GONE);
            (view.findViewById(R.id.mute_btn_view)).setVisibility(View.GONE);
        }

        mChatMsgList = new ArrayList<>();
        mChatMsgAdapter = new ChatMessageAdapter(mActivity, mChatMsgList);
        mChatListView = ((ListView) view.findViewById(R.id.chat_list_view));
        mChatListView.setAdapter(mChatMsgAdapter);
        mChatListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mOperatorLayout.setVisibility(View.VISIBLE);
                mBeautyPannelView.setVisibility(View.INVISIBLE);
                return false;
            }
        });

        RelativeLayout chatViewLayout = (RelativeLayout) view.findViewById(R.id.chat_layout);
        mSwipeAnimationController = new SwipeAnimationController(mActivity);
        mSwipeAnimationController.setAnimationView(chatViewLayout);

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mOperatorLayout.setVisibility(View.VISIBLE);
                mBeautyPannelView.setVisibility(View.INVISIBLE);
                return mSwipeAnimationController.processEvent(event);
            }
        });

        mActivity.findViewById(R.id.liveroom_global_log_textview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOperatorLayout.setVisibility(View.VISIBLE);
                mBeautyPannelView.setVisibility(View.INVISIBLE);
            }
        });

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Bundle bundle = getArguments();
        mRoomInfo = bundle.getParcelable("roomInfo");
        mSelfUserId = bundle.getString("userID");
        mCreateRoom = bundle.getBoolean("createRoom");

        if (mSelfUserId == null || (!mCreateRoom && mRoomInfo == null)) {
            return;
        }

        mHandler = new Handler();

        mActivityInterface.setTitle(mRoomInfo.roomName);

        TXCloudVideoView videoView = ((TXCloudVideoView) mActivity.findViewById(R.id.video_view_full_screen));
        videoView.setLogMargin(12, 12, 80, 60);

        if (mCreateRoom) {
            mActivityInterface.getLiveRoom().startLocalPreview(videoView);
            mActivityInterface.getLiveRoom().setPauseImage(BitmapFactory.decodeResource(getResources(), R.drawable.pause_publish));
            mActivityInterface.getLiveRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
            mActivityInterface.getLiveRoom().setMute(mPusherMute);
            //todo roomID  xieang_id 可以暂时写死 正常是 后台动态分配的 或者上个界面传入的
            mActivityInterface.getLiveRoom().createRoom(mRoomInfo.roomID, mRoomInfo.roomInfo, new LiveRoom.CreateRoomCallback() {
                @Override
                public void onSuccess(String roomId) {
                    mRoomInfo.roomID = roomId;
                }

                @Override
                public void onError(int errCode, String e) {
                    errorGoBack("创建直播间错误", errCode, e);
                }
            });
        } else {//进入别人的创造好房间 mRoomInfo.roomID
            mActivityInterface.getLiveRoom().enterRoom(mRoomInfo.roomID, videoView, new LiveRoom.EnterRoomCallback() {
                @Override
                public void onError(int errCode, String errInfo) {
                    errorGoBack("进入直播间错误", errCode, errInfo);
                }

                @Override
                public void onSuccess() {

                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mActivityInterface.getLiveRoom().switchToForeground();
    }

    @Override
    public void onPause() {
        super.onPause();
        mActivityInterface.getLiveRoom().switchToBackground();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPlayerViews.clear();
        mActivityInterface.getLiveRoom().stopLocalPreview();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recycleVideoView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
        mActivityInterface = null;
    }

    public void onBackPressed() {
        if (mActivityInterface != null) {
            mActivityInterface.getLiveRoom().exitRoom(new LiveRoom.ExitRoomCallback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "exitRoom Success");
                }

                @Override
                public void onError(int errCode, String e) {
                    Log.e(TAG, "exitRoom failed, errorCode = " + errCode + " errMessage = " + e);
                }
            });
        }

        recycleVideoView();
        backStack();
    }

    private void errorGoBack(String title, int errCode, String errInfo) {
        mActivityInterface.getLiveRoom().exitRoom(null);
        new AlertDialog.Builder(mActivity)
                .setTitle(title)
                .setMessage(errInfo + "[" + errCode + "]")
                .setNegativeButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        recycleVideoView();
                        backStack();
                    }
                }).show();
    }

    private void backStack() {
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mActivity != null) {
                        FragmentManager fragmentManager = mActivity.getFragmentManager();
                        fragmentManager.popBackStack();
                        fragmentManager.beginTransaction().commit();
                    }
                }
            });
        }
    }

    /**
     * 中国移动  161K/s n O HD 会bGrull 87% @1下午3:59[15:55:41] [M] connect success,initialize,time cost 0.25 secs
     * [15:55:41] [IM] online  谢刚
     * [15:55:43] [LiveRoom] 登录成功，userID自己定义的用户名}，sdkApplD{1400047134}
     * [15:55:43] [Activity]LiveRoom初始化成功,userlD{自己定义的用户名}[15:55:43] [IM] login success,time cost 1.25 secs[15:55:43] [IM] login success
     * [15:55:51] [BaseRoom] startLocalPreview[15:55:51][BaseRoom] onResume
     * [15:55:51] [BaseRoom]
     * // todo 直播管理 appid: 1253996937	 bizid : 23493 概述 - API中心 - 腾讯云文档平台 - 腾讯云  https://cloud.tencent.com/document/api/267/5956#.E5.AE.89.E5.85.A8.E6.A3.80.E6.9F.A5
     * //todo 推流地址 是 动态拼接的  3891_自己定义的用户名 分别是bizid 和用户的uesid txSecret  txTime　 这是推流防盗链的生成 KEY+ streamId + txTime
     * <p>
     * 开始推流PushUrl = rtmp://3891.livepush.qclouc/live/3891_自己定义的用户名?bizid=3891&txSecret=3ee43b698831977ba120dcedf79ace6b&txTime=5AF94109[15:55:52] [BaseRoom] 推流成功
     * [15:55:52] [BaseRoom] 创建直播间lD{xiegang_id_test} 成功[15:55:53] [BaseRoom] Enter Room 成功
     * [15:55:53] [IM] 加入群{xiegang_id_test} 成功
     * [15:55:53] [IM] onNewMessage type= GroupTips[15:55:53] [IM] onNewMessage type= GroupSystem[15:56:40] [BaseRoom] onPause
     * [15;56:40] [IM] 退出群{xiegang_id_test} 成功
     * [15:56:40] [IM] onNewMessage type= GroupSystem[15:56:41] [LiveRoom] 解散群成功
     * [15:58:56] [BaseRoom] startLocalPreview[15:58:56] [BaseRoom] onResume
     * [15:58:56] [BaseRoom] 开始推流PushUrl=
     * rtmp://3891.livepush.myqcloud.comie/8自己定义的用户名?
     * bizid=3891&txSecret=f97a4d4cf3889986185284c9217afe&txTime=5AF941C2[15:58:57] [BaseRoom] 推流成功
     * [15:58:57] [BaseRoom] 创建直播间lD{xiegang_id_test} 成功[15:58:58] [BaseRoom] Enter Room 成功
     * [15:58:58] [IM] 加入群{xiegang_id_test} 成功
     * [15:58:58] [IM] onNewMessage type= GroupTips[15:58:58] [IM] onNewMessage type= GroupSystem[15:59:05] [IM] onNewMessage type= GroupTips
     * (  0  &  Log
     * <  口
     */
    private void switchLog() {

        mShowLogFlag++;
        mShowLogFlag = (mShowLogFlag % 3);
        switch (mShowLogFlag) {
            case 0: {
                TXCloudVideoView videoViewFullScreen = ((TXCloudVideoView) mActivity.findViewById(R.id.video_view_full_screen));
                videoViewFullScreen.showLog(false);

                for (RoomVideoView item : mPlayerViews) {
                    if (item.isUsed) {
                        item.videoView.showLog(false);
                    }
                }

                if (mActivityInterface != null) {
                    mActivityInterface.showGlobalLog(false);
                }

                break;
            }

            case 1: {
                TXCloudVideoView videoViewFullScreen = ((TXCloudVideoView) mActivity.findViewById(R.id.video_view_full_screen));
                videoViewFullScreen.showLog(false);

                for (RoomVideoView item : mPlayerViews) {
                    if (item.isUsed) {
                        item.videoView.showLog(false);
                    }
                }

                if (mActivityInterface != null) {
                    mActivityInterface.showGlobalLog(true);
                }

                break;
            }

            case 2: {
                TXCloudVideoView videoViewFullScreen = ((TXCloudVideoView) mActivity.findViewById(R.id.video_view_full_screen));
                videoViewFullScreen.showLog(true);

                for (RoomVideoView item : mPlayerViews) {
                    if (item.isUsed) {
                        item.videoView.showLog(true);
                    }
                }

                if (mActivityInterface != null) {
                    mActivityInterface.showGlobalLog(false);
                }

                break;
            }
        }
    }

    private void addMessageItem(final String userName, final String message, final TextChatMsg.Aligment aligment) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
                mChatMsgList.add(new TextChatMsg(userName, TIME_FORMAT.format(new Date()), message, aligment));

                mChatMsgAdapter.notifyDataSetChanged();
                mChatListView.post(new Runnable() {
                    @Override
                    public void run() {
                        mChatListView.setSelection(mChatMsgList.size() - 1);
                    }
                });
            }
        });
    }

    private void sendMessage(final String message) {
        mActivityInterface.getLiveRoom().sendRoomTextMsg(message, new LiveRoom.SendTextMessageCallback() {
            @Override
            public void onError(int errCode, String errInfo) {
                new AlertDialog.Builder(mActivity, R.style.RtmpRoomDialogTheme).setMessage(errInfo)
                        .setTitle("发送消息失败")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
            }

            @Override
            public void onSuccess() {
                addMessageItem(mActivityInterface.getSelfUserName(), message, TextChatMsg.Aligment.LEFT);
            }
        });
    }

    @Override
    public void onGetPusherList(List<PusherInfo> pusherList) {
        for (PusherInfo pusherInfo : pusherList) {
            mPusherList.add(pusherInfo);
            onPusherJoin(pusherInfo);
        }
    }

    @Override
    public void onPusherJoin(final PusherInfo pusherInfo) {
        if (pusherInfo == null || pusherInfo.userID == null) {
            return;
        }

        final RoomVideoView videoView = applyVideoView(pusherInfo.userID);
        if (videoView == null) {
            return;
        }

        if (mPusherList != null) {
            boolean exist = false;
            for (PusherInfo item : mPusherList) {
                if (pusherInfo.userID.equalsIgnoreCase(item.userID)) {
                    exist = true;
                    break;
                }
            }
            if (exist == false) {
                mPusherList.add(pusherInfo);
            }
        }

        videoView.startLoading();
        mActivityInterface.getLiveRoom().addRemoteView(videoView.videoView, pusherInfo, new LiveRoom.RemoteViewPlayCallback() {
            @Override
            public void onPlayBegin() {
                videoView.stopLoading(mCreateRoom); //推流成功，stopLoading 大主播显示出踢人的button
            }

            @Override
            public void onPlayError() {
                LiveRoomChatFragment.this.onPusherQuit(pusherInfo);
                if (mCreateRoom) {
                    mActivityInterface.getLiveRoom().kickoutSubPusher(pusherInfo.userID);
                }
            }
        }); //开启远端视频渲染
    }

    @Override
    public void onPusherQuit(PusherInfo pusherInfo) {
        if (mPusherList != null) {
            Iterator<PusherInfo> it = mPusherList.iterator();
            while (it.hasNext()) {
                PusherInfo item = it.next();
                if (pusherInfo.userID.equalsIgnoreCase(item.userID)) {
                    it.remove();
                    break;
                }
            }
        }

        mActivityInterface.getLiveRoom().deleteRemoteView(pusherInfo);//关闭远端视频渲染
        recycleVideoView(pusherInfo.userID);
    }

    @Override
    public void onRecvJoinPusherRequest(final String userId, String userName, String userAvatar) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mActivity)
                .setCancelable(true)
                .setTitle("提示")
                .setMessage(userName + "向您发起连麦请求")
                .setPositiveButton("接受", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mActivityInterface.getLiveRoom().acceptJoinPusher(userId);
                        dialog.dismiss();
                        mPendingRequest = false;
                    }
                })
                .setNegativeButton("拒绝", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mActivityInterface.getLiveRoom().rejectJoinPusher(userId, "主播拒绝了您的连麦请求");
                        dialog.dismiss();
                        mPendingRequest = false;
                    }
                });

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mPendingRequest == true) {
                    mActivityInterface.getLiveRoom().rejectJoinPusher(userId, "请稍后，主播正在处理其它人的连麦请求");
                    return;
                }

                if (mPusherList.size() > 3) {
                    mActivityInterface.getLiveRoom().rejectJoinPusher(userId, "主播端连麦人数超过最大限制");
                    return;
                }

                final AlertDialog alertDialog = builder.create();
                alertDialog.setCancelable(false);
                alertDialog.setCanceledOnTouchOutside(false);
                alertDialog.show();

                mPendingRequest = true;

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        alertDialog.dismiss();
                        mPendingRequest = false;
                    }
                }, 10000);
            }
        });
    }

    @Override
    public void onKickOut() {
        stopLinkMic();
    }

    @Override
    public void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String message) {
        addMessageItem(userName, message, TextChatMsg.Aligment.LEFT);
    }

    @Override
    public void onRecvRoomCustomMsg(final String roomID, final String userID, final String userName, final String userAvatar, final String cmd, final String message) {
        //do nothing
    }

    @Override
    public void onRoomClosed(String roomId) {
        if (mCreateRoom == false) {
            new HintDialog.Builder(mActivity)
                    .setTittle("系统消息")
                    .setContent(String.format("直播间【%s】解散了", mRoomInfo != null ? mRoomInfo.roomInfo : "null"))
                    .setButtonText("返回")
                    .setDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            onBackPressed();
                        }
                    }).show();
        }
    }

    @Override
    public void onDebugLog(String line) {
        //do nothing
    }

    @Override
    public void onError(final int errorCode, final String errorMessage) {
        errorGoBack("直播间错误", errorCode, errorMessage);
    }

    @Override
    public void onBeautyParamsChange(BeautySettingPannel.BeautyParams params, int key) {
        LiveRoom liveRoom = mActivityInterface.getLiveRoom();
        switch (key) {
            case BeautySettingPannel.BEAUTYPARAM_EXPOSURE:
                if (liveRoom != null) {
                    liveRoom.setExposureCompensation(params.mExposure);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY:
                mBeautyLevel = params.mBeautyLevel;
                if (liveRoom != null) {
                    liveRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_WHITE:
                mWhiteningLevel = params.mWhiteLevel;
                if (liveRoom != null) {
                    liveRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BIG_EYE:
                if (liveRoom != null) {
                    liveRoom.setEyeScaleLevel(params.mBigEyeLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACE_LIFT:
                if (liveRoom != null) {
                    liveRoom.setFaceSlimLevel(params.mFaceSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER:
                if (liveRoom != null) {
                    liveRoom.setFilter(params.mFilterBmp);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_GREEN:
                if (liveRoom != null) {
                    liveRoom.setGreenScreenFile(params.mGreenFile);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_MOTION_TMPL:
                if (liveRoom != null) {
                    liveRoom.setMotionTmpl(params.mMotionTmplPath);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_RUDDY:
                mRuddyLevel = params.mRuddyLevel;
                if (liveRoom != null) {
                    liveRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY_STYLE:
                mBeautyStyle = params.mBeautyStyle;
                if (liveRoom != null) {
                    liveRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACEV:
                if (liveRoom != null) {
                    liveRoom.setFaceVLevel(params.mFaceVLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACESHORT:
                if (liveRoom != null) {
                    liveRoom.setFaceShortLevel(params.mFaceShortLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_CHINSLIME:
                if (liveRoom != null) {
                    liveRoom.setChinLevel(params.mChinSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_NOSESCALE:
                if (liveRoom != null) {
                    liveRoom.setNoseSlimLevel(params.mNoseScaleLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER_MIX_LEVEL:
                if (liveRoom != null) {
                    liveRoom.setSpecialRatio(params.mFilterMixLevel / 10.f);
                }
                break;
        }
    }

    private void startLinkMic() {
        mBtnLinkMic.setEnabled(false);
        showNoticeToast("等待主播接受......");

        mActivityInterface.getLiveRoom().requestJoinPusher(10, new LiveRoom.RequestJoinPusherCallback() {
            @Override
            public void onAccept() {
                hideNoticeToast();
                Toast.makeText(mActivity, "主播接受了您的连麦请求，开始连麦", Toast.LENGTH_SHORT).show();

                RoomVideoView videoView = mPlayerViews.get(0);
                videoView.setUsed(true);
                videoView.userID = mSelfUserId;

                mActivityInterface.getLiveRoom().startLocalPreview(videoView.videoView);
                mActivityInterface.getLiveRoom().setPauseImage(BitmapFactory.decodeResource(getResources(), R.drawable.pause_publish));
                mActivityInterface.getLiveRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                mActivityInterface.getLiveRoom().joinPusher(new LiveRoom.JoinPusherCallback() {
                    @Override
                    public void onError(int errCode, String errInfo) {
                        stopLinkMic();
                        mBtnLinkMic.setEnabled(true);
                        if (mActivity != null) {
                            Toast.makeText(mActivity, "连麦失败：" + errInfo, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onSuccess() {
                        mBtnLinkMic.setEnabled(true);
                        mBtnLinkMic.setBackgroundResource(R.drawable.linkmic_stop);
                        mIsBeingLinkMic = true;
                    }
                });
            }

            @Override
            public void onReject(String reason) {
                mBtnLinkMic.setEnabled(true);
                hideNoticeToast();
                if (mActivity != null) {
                    Toast.makeText(mActivity, reason, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onTimeOut() {
                mBtnLinkMic.setEnabled(true);
                hideNoticeToast();
                if (mActivity != null) {
                    Toast.makeText(mActivity, "连麦请求超时，主播没有做出回应", Toast.LENGTH_SHORT).show();
                }

            }

            @Override
            public void onError(int code, String errInfo) {
                hideNoticeToast();
                mBtnLinkMic.setEnabled(true);
            }
        });
    }

    private void stopLinkMic() {
        mIsBeingLinkMic = false;

        mBtnLinkMic.setEnabled(true);
        mBtnLinkMic.setBackgroundResource(R.drawable.linkmic_start);

        recycleVideoView(mSelfUserId);

        mActivityInterface.getLiveRoom().stopLocalPreview();
        mActivityInterface.getLiveRoom().quitPusher(new LiveRoom.QuitPusherCallback() {
            @Override
            public void onError(int errCode, String errInfo) {

            }

            @Override
            public void onSuccess() {

            }
        });
    }

    private Toast mNoticeToast;
    private Timer mNoticeTimer;

    private void showNoticeToast(String text) {
        if (mNoticeToast == null) {
            mNoticeToast = Toast.makeText(mActivity, text, Toast.LENGTH_LONG);
        }

        if (mNoticeTimer == null) {
            mNoticeTimer = new Timer();
        }

        mNoticeToast.setText(text);
        mNoticeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mNoticeToast.show();
            }
        }, 0, 3000);

    }

    private void hideNoticeToast() {
        if (mNoticeToast != null) {
            mNoticeToast.cancel();
            mNoticeToast = null;
        }
        if (mNoticeTimer != null) {
            mNoticeTimer.cancel();
            mNoticeTimer = null;
        }
    }

    private void showInputMsgDialog() {
        WindowManager windowManager = mActivity.getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        WindowManager.LayoutParams lp = mTextMsgInputDialog.getWindow().getAttributes();

        lp.width = (display.getWidth()); //设置宽度
        mTextMsgInputDialog.getWindow().setAttributes(lp);
        mTextMsgInputDialog.setCancelable(true);
        mTextMsgInputDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        mTextMsgInputDialog.show();
    }

    private class RoomVideoView {
        TXCloudVideoView videoView;
        FrameLayout loadingBkg;
        ImageView loadingImg;
        Button kickButton;
        String userID;
        boolean isUsed;


        public RoomVideoView(TXCloudVideoView view, Button button, FrameLayout loadingBkg, ImageView loadingImg) {
            this.videoView = view;
            this.videoView.setVisibility(View.GONE);
            this.loadingBkg = loadingBkg;
            this.loadingImg = loadingImg;
            this.isUsed = false;
            this.kickButton = button;
            this.kickButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kickButton.setVisibility(View.INVISIBLE);
                    String userID = RoomVideoView.this.userID;
                    if (userID != null) {
                        for (PusherInfo item : mPusherList) {
                            if (userID.equalsIgnoreCase(item.userID)) {
                                onPusherQuit(item);
                                break;
                            }
                        }
                        mActivityInterface.getLiveRoom().kickoutSubPusher(userID);
                    }
                }
            });
        }

        public void startLoading() {
            kickButton.setVisibility(View.INVISIBLE);
            loadingBkg.setVisibility(View.VISIBLE);
            loadingImg.setVisibility(View.VISIBLE);
            loadingImg.setImageResource(R.drawable.linkmic_loading);
            AnimationDrawable ad = (AnimationDrawable) loadingImg.getDrawable();
            ad.start();
        }

        public void stopLoading(boolean showKickoutBtn) {
            kickButton.setVisibility(showKickoutBtn ? View.VISIBLE : View.GONE);
            loadingBkg.setVisibility(View.GONE);
            loadingImg.setVisibility(View.GONE);
            AnimationDrawable ad = (AnimationDrawable) loadingImg.getDrawable();
            if (ad != null) {
                ad.stop();
            }
        }

        public void stopLoading() {
            kickButton.setVisibility(View.GONE);
            loadingBkg.setVisibility(View.GONE);
            loadingImg.setVisibility(View.GONE);
            AnimationDrawable ad = (AnimationDrawable) loadingImg.getDrawable();
            if (ad != null) {
                ad.stop();
            }
        }

        private void setUsed(boolean used) {
            videoView.setVisibility(used ? View.VISIBLE : View.GONE);
            if (used == false) {
                stopLoading(false);
            }
            this.isUsed = used;
        }

    }

    public synchronized RoomVideoView applyVideoView(String id) {
        if (id == null) {
            return null;
        }

        for (RoomVideoView item : mPlayerViews) {
            if (!item.isUsed) {
                item.setUsed(true);
                item.userID = id;
                return item;
            } else {
                if (item.userID != null && item.userID.equals(id)) {
                    item.setUsed(true);
                    return item;
                }
            }
        }
        return null;
    }

    public synchronized void recycleVideoView(String id) {
        for (RoomVideoView item : mPlayerViews) {
            if (item.userID != null && item.userID.equals(id)) {
                item.userID = null;
                item.setUsed(false);
            }
        }
    }

    public synchronized void recycleVideoView() {
        for (RoomVideoView item : mPlayerViews) {
            item.userID = null;
            item.setUsed(false);
        }
    }
}
