package com.tencent.liteav.demo.liveroom;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.liteav.demo.roomutil.commondef.BaseRoom;
import com.tencent.liteav.demo.roomutil.commondef.LoginInfo;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.roomutil.http.HttpRequests;
import com.tencent.liteav.demo.roomutil.http.HttpResponse;
import com.tencent.liteav.demo.roomutil.im.IMMessageMgr;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.rtmp.ITXLivePlayListener;
import com.tencent.rtmp.TXLivePlayConfig;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.ugc.TXRecordCommon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Vector;
       /* setLiveRoomListener(ILiveRoomListener listener)	设置liveroom回调
        login(serverDomain, loginInfo, loginCallback)	登录到RoomService后台
        logout()	从RoomService后台登出
        getRoomList(int index, int count, GetRoomListCallback callback)	获取房间列表（非必须，可选择使用您自己的房间列表）
        getAudienceList(String roomID, final GetAudienceListCallback callback)	获取某个房间里的观众列表（最多返回最近加入的 30 个观众）
        createRoom(String roomID, String roomInfo, CreateRoomCallback cb)	主播：创建房间 （roomID 可不填）
        enterRoom(String roomID, TXCloudVideoView videoView, EnterRoomCallback cb)	观众：进入房间
        exitRoom(ExitRoomCallback callback)	主播 OR 观众：退出房间
        startLocalPreview(TXCloudVideoView videoView)	主播 OR 连麦观众：开启摄像头预览
        stopLocalPreview()	停止摄像头预览
        requestJoinPusher(int timeout, RequestJoinPusherCallback callback)	观众：发起连麦请求	``
        joinPusher(final JoinPusherCallback cb)	观众：进入连麦状态
        quitPusher(final QuitPusherCallback cb)	观众：退出连麦状态
        acceptJoinPusher(String userID)	主播：接受来自观众的连麦请求
        rejectJoinPusher(String userID, String reason)	主播：拒绝来自观众的连麦请求
        kickoutSubPusher(String userID)	主播：踢掉连麦中的某个观众
        addRemoteView(TXCloudVideoView videoView, PusherInfo pusherInfo, RemoteViewPlayCallback callback)	主播：播放连麦观众的远程视频画面
        deleteRemoteView(PusherInfo pusherInfo)	主播：移除连麦观众的远程视频画面
        sendRoomTextMsg(String message, SendTextMessageCallback callback)	发送文本（弹幕）消息
        sendRoomCustomMsg(String cmd, String message, SendCustomMessageCallback callback)	发送自定义格式的消息（点赞，送花）
        startScreenCapture()	开始屏幕录制 （仅Android）
        stopScreenCapture()	停止屏幕录制 （仅Android）
        switchToBackground()	App 从前台切换到后台
        switchToForeground()	App 从后台切换到前台
        setBeautyFilter(style, beautyLevel, whiteningLevel, ruddyLevel)	设置美颜
        switchCamera()	切换前后置摄像头，支持在推流中动态切换
        setMute(mute)	静音
        setMirror(enable)	画面镜像（此接口仅影响观众端效果，主播一直保持镜像效果）
        playBGM(String path)	开始播放背景音乐 （path 指定音乐文件路径）
        stopBGM()	停止播放背景音乐
        pauseBGM()	暂停播放背景音乐
        resumeBGM()	继续播放背景音乐
        setMicVolume(x)	设置混音时麦克风的音量大小
        setBGMVolume(x)	设置混音时背景音乐的音量大小
        getMusicDuration(fileName)	获取背景音乐时长
        startRecord(recordType)	开始视频录制
        stopRecord()	停止视频录制
        setVideoRecordListener(TXRecordCommon.ITXVideoRecordListener listener)	设置视频录制回调
        incCustomInfo(fieldName, count)	增加房间自定义数值fieldName
        decCustomInfo(fieldName, count)	减少房间自定义数值fieldName
        updateSelfUserInfo(userName, userAvatar)	更新liveroom的用户信息
        setPauseImage(bitmap)	设置后台时推送的图片*/

public class LiveRoom extends BaseRoom {

    private static final int LIVEROOM_ROLE_NONE = 0;
    private static final int LIVEROOM_ROLE_PUSHER = 1;
    private static final int LIVEROOM_ROLE_PLAYER = 2;

    private int mSelfRoleType = LIVEROOM_ROLE_NONE;

    private boolean mJoinPusher = false;

    private boolean mBackground = false;

    private TXLivePlayer mTXLivePlayer;

    private TXLivePlayConfig mTXLivePlayConfig;

    private StreamMixturer mStreamMixturer;

    private RoomListenerCallback mRoomListenerCallback;

    private RequestJoinPusherCallback mJoinPusherCallback;

    private Runnable mJoinPusherTimeoutTask;

    private static final int LIVEROOM_CAMERA_PREVIEW = 0;
    private static final int LIVEROOM_SCREEN_PREVIEW = 1;
    private int mPreviewType = LIVEROOM_CAMERA_PREVIEW;

    /**
     * LiveRoom 直播房间
     */
    public LiveRoom(Context context) {
        super(context);

        mRoomListenerCallback = new RoomListenerCallback(null);

        mStreamMixturer = new StreamMixturer();

        mTXLivePlayConfig = new TXLivePlayConfig();
        mTXLivePlayer = new TXLivePlayer(context);
        mTXLivePlayConfig.setAutoAdjustCacheTime(true);
        mTXLivePlayConfig.setMaxAutoAdjustCacheTime(2.0f);
        mTXLivePlayConfig.setMinAutoAdjustCacheTime(2.0f);
        mTXLivePlayer.setConfig(mTXLivePlayConfig);
        mTXLivePlayer.setRenderMode(TXLiveConstants.RENDER_MODE_FULL_FILL_SCREEN);
        mTXLivePlayer.setPlayListener(new ITXLivePlayListener() {
            @Override
            public void onPlayEvent(int event, Bundle param) {
                if (event == TXLiveConstants.PLAY_ERR_NET_DISCONNECT) {
                    mRoomListenerCallback.onDebugLog("[LiveRoom] 拉流失败：网络断开");
                    mRoomListenerCallback.onError(-1, "网络断开，拉流失败");
                } else if (event == TXLiveConstants.PLAY_EVT_CHANGE_RESOLUTION) {
                    int width = param.getInt(TXLiveConstants.EVT_PARAM1, 0);
                    int height = param.getInt(TXLiveConstants.EVT_PARAM2, 0);
                    if (width > 0 && height > 0) {
                        float ratio = (float) height / width;
                        //pc上混流后的宽高比为4:5，这种情况下填充模式会把左右的小主播窗口截掉一部分，用适应模式比较合适
                        if (ratio > 1.3f) {
                            mTXLivePlayer.setRenderMode(TXLiveConstants.RENDER_MODE_FULL_FILL_SCREEN);
                        } else {
                            mTXLivePlayer.setRenderMode(TXLiveConstants.RENDER_MODE_ADJUST_RESOLUTION);
                        }
                    }
                }
            }

            @Override
            public void onNetStatus(Bundle status) {

            }
        });
    }

    /**
     * 设置房间事件回调
     *
     * @param listener
     */
    public void setLiveRoomListener(ILiveRoomListener listener) {
        mRoomListenerCallback.setRoomMemberEventListener(listener);
    }

    /**
     * todo LiveRoom 登录Callback
     */
    public interface LoginCallback {
        void onError(int errCode, String errInfo);

        void onSuccess(String userId);
    }

    /**
     *  todo 初始化LiveRoom 上下文
     *
     * @param serverDomain 服务器域名地址
     * @param loginInfo    初始化信息
     * @param callback     初始化完成的回调
     */
    public void login(@NonNull String serverDomain, @NonNull final LoginInfo loginInfo, final LoginCallback callback) {
        final MainCallback cb = new MainCallback<LoginCallback, String>(callback);

        super.login(serverDomain, loginInfo, new IMMessageMgr.Callback() {
            @Override
            public void onError(int code, String errInfo) {
                mRoomListenerCallback.printLog("[LiveRoom] 登录失败: %s(%d)", errInfo, code);
                cb.onError(code, errInfo);
            }

            @Override
            public void onSuccess(Object... args) {
                mRoomListenerCallback.printLog("[LiveRoom] 登录成功, userID {%s}, " + "sdkAppID {%s}", mSelfAccountInfo.userID, mSelfAccountInfo.sdkAppID);
                cb.onSuccess(mSelfAccountInfo.userID);
            }
        });
    }

    /**
     * LiveRoom 注销
     */
    public void logout() {
        mRoomListenerCallback.onDebugLog("[LiveRoom] 注销");
        mStreamMixturer = null;
        super.logout();
    }

    /**
     * LiveRoom 获取房间列表Callback
     */
    public interface GetRoomListCallback {
        void onError(int errCode, String errInfo);

        void onSuccess(ArrayList<RoomInfo> roomInfoList);
    }

    /**
     * todo 获取房间列表，分页获取 类似于香蕉球的直播间列表这样
     *
     * @param index    获取的房间开始索引，从0开始计算
     * @param count    获取的房间个数
     * @param callback 拉取房间列表完成的回调，回调里返回获取的房间列表信息，如果个数小于cnt则表示已经拉取所有的房间列表
     */
    public void getRoomList(int index, int count, final GetRoomListCallback callback) {
        if (mHttpRequest == null) {
            if (callback != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(-1, "");
                    }
                });
            }
            return;
        }
        mHttpRequest.getRoomList(index, count, new HttpRequests.OnResponseCallback<HttpResponse.RoomList>() {
            @Override
            public void onResponse(final int retcode, final @Nullable String retmsg, @Nullable HttpResponse.RoomList data) {
                if (retcode != HttpResponse.CODE_OK || data == null || data.rooms == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(retcode, retmsg);
                        }
                    });
                } else {
                    final ArrayList<RoomInfo> arrayList = new ArrayList<>(data.rooms.size());
                    arrayList.addAll(data.rooms);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRoomList = arrayList;
                            callback.onSuccess(arrayList);
                        }
                    });
                }
            }
        });
    }

    /**
     * LiveRoom 创建房间Callback
     */
    public interface CreateRoomCallback {
        void onError(int errCode, String errInfo);

        void onSuccess(String name);
    }

    /**
     * 创建房间 自己创建房间
     *
     * @param roomInfo 房间信息
     * @param cb       房间创建完成的回调，里面会携带roomID
     */
    public void createRoom(final String roomID, final String roomInfo, final CreateRoomCallback cb) {
        mSelfRoleType = LIVEROOM_ROLE_PUSHER;

        //1. 在应用层调用startLocalPreview，启动本地预览

        final MainCallback callback = new MainCallback<CreateRoomCallback, String>(cb);

        //2. 请求CGI:get_push_url，异步获取到推流地址pushUrl
        mHttpRequest.getPushUrl(mSelfAccountInfo.userID, new HttpRequests.OnResponseCallback<HttpResponse.PushUrl>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse.PushUrl data) {
                if (retcode == HttpResponse.CODE_OK && data != null && data.pushURL != null) {
                    final String pushURL = data.pushURL;

                    //3.开始推流
                    startPushStream(pushURL, TXLiveConstants.VIDEO_QUALITY_HIGH_DEFINITION, new PusherStreamCallback() {
                        @Override
                        public void onError(int errCode, String errInfo) {
                            callback.onError(errCode, errInfo);
                        }

                        @Override
                        public void onSuccess() {
                            //推流过程中，可能会重复收到PUSH_EVT_PUSH_BEGIN事件，onSuccess可能会被回调多次，如果已经创建的房间，直接返回
                            if (mCurrRoomID != null && mCurrRoomID.length() > 0) {
                                return;
                            }

                            if (mTXLivePusher != null) {
                                TXLivePushConfig config = mTXLivePusher.getConfig();
                                config.setVideoEncodeGop(5);
                                mTXLivePusher.setConfig(config);
                            }

                            mBackground = false;
                            //4.推流成功，请求CGI:create_room，获取roomID、roomSig
                            doCreateRoom(roomID, roomInfo, new BaseRoom.CreateRoomCallback() {
                                @Override
                                public void onError(int errCode, String errInfo) {
                                    callback.onError(errCode, errInfo);
                                }

                                @Override
                                public void onSuccess(final String newRoomID) {

                                    //5.请求CGI：add_pusher，加入房间
                                    addPusher(newRoomID, pushURL, new AddPusherCallback() {
                                        @Override
                                        public void onError(int errCode, String errInfo) {
                                            callback.onError(errCode, errInfo);
                                        }

                                        @Override
                                        public void onSuccess() {
                                            mCurrRoomID = newRoomID;

                                            //6.调用IM的joinGroup，加入群组
                                            jionGroup(newRoomID, new JoinGroupCallback() {
                                                @Override
                                                public void onError(int errCode, String errInfo) {
                                                    callback.onError(errCode, errInfo);
                                                }

                                                @Override
                                                public void onSuccess() {
                                                    mHeartBeatThread.setUserID(mSelfAccountInfo.userID);
                                                    mHeartBeatThread.setRoomID(mCurrRoomID);
                                                    mHeartBeatThread.startHeartbeat(); //启动心跳
                                                    mStreamMixturer.setMainVideoStream(pushURL);
                                                    callback.onSuccess(newRoomID);
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });

                } else {
                    callback.onError(retcode, "获取推流地址失败");
                }
            }
        });
    }

    /**
     * LiveRoom 进入房间Callback
     */
    public interface EnterRoomCallback {
        void onError(int errCode, String errInfo);

        void onSuccess();
    }

    /**
     * LiveRoom 进入房间
     *
     * @param roomID 房间号
     * @param cb     进入房间完成的回调
     */
    public void enterRoom(@NonNull final String roomID, @NonNull final TXCloudVideoView videoView, final EnterRoomCallback cb) {
        mSelfRoleType = LIVEROOM_ROLE_PLAYER;
        mCurrRoomID = roomID;

        final MainCallback<EnterRoomCallback, Object> callback = new MainCallback<EnterRoomCallback, Object>(cb);

        // 调用IM的joinGroup
        jionGroup(roomID, new JoinGroupCallback() {
            @Override
            public void onError(int code, String errInfo) {
                callback.onError(code, errInfo);
            }

            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String mixedPlayUrl = getMixedPlayUrlByRoomID(roomID);
                        if (mixedPlayUrl != null && mixedPlayUrl.length() > 0) {
                            int playType = getPlayType(mixedPlayUrl);
                            mTXLivePlayer.setPlayerView(videoView);
                            mTXLivePlayer.startPlay(mixedPlayUrl, playType);

                            if (mHttpRequest != null) {
                                String userInfo = "";
                                try {
                                    userInfo = new JSONObject()
                                            .put("userName", mSelfAccountInfo.userName)
                                            .put("userAvatar", mSelfAccountInfo.userAvatar)
                                            .toString();
                                } catch (JSONException e) {
                                    userInfo = "";
                                }
                                mHttpRequest.addAudience(roomID, mSelfAccountInfo.userID, userInfo, null);
                            }
                            callback.onSuccess();
                        } else {
                            callback.onError(-1, "房间不存在");
                        }
                    }
                });
            }
        });
    }

    /**
     * LiveRoom 离开房间Callback
     */
    public interface ExitRoomCallback {
        void onError(int errCode, String errInfo);

        void onSuccess();
    }

    /**
     * 离开房间
     *
     * @param callback 离开房间完成的回调
     */
    public void exitRoom(final ExitRoomCallback callback) {
        final MainCallback cb = new MainCallback<ExitRoomCallback, Object>(callback);

        //1. 结束心跳
        mHeartBeatThread.stopHeartbeat();

        //2. 调用IM的quitGroup
        mIMMessageMgr.quitGroup(mCurrRoomID, new IMMessageMgr.Callback() {
            @Override
            public void onError(int code, String errInfo) {
                //cb.onError(code, errInfo);
            }

            @Override
            public void onSuccess(Object... args) {
                //cb.onSuccess();
            }
        });


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //3. 结束本地推流
                if (mPreviewType == LIVEROOM_CAMERA_PREVIEW) {
                    stopLocalPreview();
                } else {
                    stopScreenCapture();
                }

                //4. 结束所有加速流的播放
                cleanPlayers();

                //5. 结束普通流播放
                if (mTXLivePlayer != null) {
                    mTXLivePlayer.stopPlay(true);
                    mTXLivePlayer.setPlayerView(null);
                }
            }
        });

        //6. 退出房间：请求CGI:delete_pusher，把自己从房间成员列表里删除
        mHttpRequest.delPusher(mCurrRoomID, mSelfAccountInfo.userID, new HttpRequests.OnResponseCallback<HttpResponse>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse data) {
                if (retcode == HttpResponse.CODE_OK) {
                    mRoomListenerCallback.printLog("[LiveRoom] 解散群成功");
                    //cb.onSuccess();
                } else {
                    mRoomListenerCallback.printLog("[LiveRoom] 解散群失败：%s(%d)", retmsg, retcode);
                    //cb.onError(retcode, retmsg);
                }
            }
        });

        if (mHttpRequest != null) {
            mHttpRequest.delAudience(mCurrRoomID, mSelfAccountInfo.userID, null);
        }

        mJoinPusher = false;
        mSelfRoleType = LIVEROOM_ROLE_NONE;
        mCurrRoomID = "";
        mPushers.clear();
        mStreamMixturer.resetMergeState();

        cb.onSuccess();
    }

    public interface GetAudienceListCallback {
        void onError(int errCode, String errInfo);

        void onSuccess(ArrayList<RoomInfo.Audience> audienceList);
    }

    /**
     * 获取房间列表，分页获取
     *
     * @param roomID 获取的房间开始索引，从0开始计算
     *               todo  接口定义：void getAudienceList(String roomID, final GetAudienceListCallback callback)
     *               todo   接口说明：获取某个房间里的观众列表，只返回最近进入房间的 30 位观众。
     *               点击进直播间的人数
     */
    public void getAudienceList(String roomID, final GetAudienceListCallback callback) {
        if (mHttpRequest == null) {
            if (callback != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(-1, "");
                    }
                });
            }
            return;
        }
        mHttpRequest.getAudienceList(roomID, new HttpRequests.OnResponseCallback<HttpResponse.AudienceList>() {
            @Override
            public void onResponse(final int retcode, @Nullable final String retmsg, @Nullable HttpResponse.AudienceList data) {
                if (retcode != HttpResponse.CODE_OK || data == null || data.audiences == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(retcode, retmsg);
                        }
                    });
                } else {
                    final ArrayList<RoomInfo.Audience> arrayList = new ArrayList<>(data.audiences.size());
                    arrayList.addAll(data.audiences);
                    for (RoomInfo.Audience audience :
                            arrayList) {
                        audience.transferUserInfo();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(arrayList);
                        }
                    });
                }
            }
        });
    }

    /**
     * LiveRoom 加入直播Callback
     */
    public interface JoinPusherCallback {
        void onError(int errCode, String errInfo);

        void onSuccess();
    }

    /**
     * 小主播加入直播
     *
     * @param cb
     */
    public void joinPusher(final JoinPusherCallback cb) {
        if (mCurrRoomID == null || mCurrRoomID.length() == 0) {
            cb.onError(-1, "未进入房间，不能发起连麦");
            return;
        }

        //1.在应用层调用startLocalPreview
        final MainCallback<JoinPusherCallback, Object> callback = new MainCallback<JoinPusherCallback, Object>(cb);

        //2.结束播放大主播的普通流，改为播放加速流
        String acceleratePlayUrl = getAcceleratePlayUrlByRoomID(mCurrRoomID);
        if (acceleratePlayUrl != null && acceleratePlayUrl.length() > 0) {
            mTXLivePlayer.stopPlay(true);
            mTXLivePlayer.startPlay(acceleratePlayUrl, TXLivePlayer.PLAY_TYPE_LIVE_RTMP_ACC);
        } else {
            callback.onError(-1, "获取大主播的加速拉流地址失败");
        }

        //3. 请求CGI:get_pushers，异步获取房间里所有正在推流的成员
        updatePushers(true, new UpdatePushersCallback() {
            @Override
            public void onUpdatePushersComplete(int retcode, List<PusherInfo> newPushers, List<PusherInfo> delPushers, HashMap<String, PusherInfo> mergedPushers) {
                //4. 调用listener.onGetPusherList，把房间成员通知出去（应用层代码在收到这个通知后，调用addRemoteView播放每一个成员的流）
                if (retcode == 0) {
                    for (PusherInfo member : newPushers) {
                        mRoomListenerCallback.onPusherJoin(member);
                    }
//                    mRoomListenerCallback.onGetPusherList(newPushers);
                    mPushers = mergedPushers;
                } else {
                    mRoomListenerCallback.onDebugLog("[LiveRoom] getPusherList failed");
                }
            }
        });

        //5. 请求CGI:get_push_url，异步获取到推流地址pushUrl
        mHttpRequest.getPushUrl(mSelfAccountInfo.userID, new HttpRequests.OnResponseCallback<HttpResponse.PushUrl>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, final @Nullable HttpResponse.PushUrl data) {
                if (retcode == HttpResponse.CODE_OK && data != null && data.pushURL != null) {
                    //6. 开始推流
                    startPushStream(data.pushURL, TXLiveConstants.VIDEO_QUALITY_LINKMIC_SUB_PUBLISHER, new PusherStreamCallback() {
                        @Override
                        public void onError(int code, String info) {
                            callback.onError(code, info);
                        }

                        @Override
                        public void onSuccess() {
                            mBackground = false;
                            //7. 推流成功，请求CGI:add_pusher，把自己加入房间成员列表
                            addPusher(mCurrRoomID, data.pushURL, new AddPusherCallback() {
                                @Override
                                public void onError(int code, String info) {
                                    callback.onError(code, info);
                                }

                                @Override
                                public void onSuccess() {
                                    mJoinPusher = true;
                                    mHeartBeatThread.setUserID(mSelfAccountInfo.userID);
                                    mHeartBeatThread.setRoomID(mCurrRoomID);
                                    mHeartBeatThread.startHeartbeat();// 开启心跳
                                    callback.onSuccess();
                                }
                            });
                        }
                    });
                } else {
                    callback.onError(retcode, "获取推流地址失败");
                }
            }
        });

    }

    /**
     * LiveRoom 退出直播Callback
     */
    public interface QuitPusherCallback {
        void onError(int errCode, String errInfo);

        void onSuccess();
    }

    /**
     * 小主播退出直播
     *
     * @param cb
     */
    public void quitPusher(final QuitPusherCallback cb) {
        final MainCallback callback = new MainCallback<QuitPusherCallback, Object>(cb);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //1. 结束本地推流
                if (mPreviewType == LIVEROOM_CAMERA_PREVIEW) {
                    stopLocalPreview();
                } else {
                    stopScreenCapture();
                }

                //2. 结束所有加速流的播放
                cleanPlayers();

                //3. 结束播放大主播的加速流，改为播放普通流
                mTXLivePlayer.stopPlay(true);
                if (!mBackground) {
                    String mixedPlayUrl = getMixedPlayUrlByRoomID(mCurrRoomID);
                    if (mixedPlayUrl != null && mixedPlayUrl.length() > 0) {
                        int playType = getPlayType(mixedPlayUrl);
                        mTXLivePlayer.startPlay(mixedPlayUrl, playType);
                    }
                }
            }
        });

        //4. 结束心跳
        mHeartBeatThread.stopHeartbeat();

        //5. 请求CGI:delete_pusher，把自己从房间成员列表里删除
        mHttpRequest.delPusher(mCurrRoomID, mSelfAccountInfo.userID, new HttpRequests.OnResponseCallback<HttpResponse>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse data) {
                if (retcode == HttpResponse.CODE_OK) {
                    mRoomListenerCallback.printLog("[LiveRoom] 结束连麦成功");
                } else {
                    mRoomListenerCallback.printLog("[LiveRoom] 结束连麦失败：%s(%d)", retmsg, retcode);
                }
            }
        });

        mJoinPusher = false;

        mPushers.clear();

        callback.onSuccess();
    }

    /**
     * 小主播连麦请求Callback
     */
    public interface RequestJoinPusherCallback {
        void onAccept();

        void onReject(String reason);

        void onTimeOut();

        void onError(int code, String errInfo);
    }

    /**
     * 小主播发起连麦请求
     *
     * @param timeout  请求超时时间，单位：秒
     * @param callback
     */
    public void requestJoinPusher(int timeout, final @NonNull RequestJoinPusherCallback callback) {
        try {
            CommonJson<JoinPusherRequest> request = new CommonJson<>();
            request.cmd = "linkmic";
            request.data = new JoinPusherRequest();
            request.data.type = "request";
            request.data.roomID = mCurrRoomID;
            request.data.userID = mSelfAccountInfo.userID;
            request.data.userName = mSelfAccountInfo.userName;
            request.data.userAvatar = mSelfAccountInfo.userAvatar;

            mJoinPusherCallback = callback;

            if (mJoinPusherTimeoutTask == null) {
                mJoinPusherTimeoutTask = new Runnable() {
                    @Override
                    public void run() {
                        if (mJoinPusherCallback != null) {
                            mJoinPusherCallback.onTimeOut();
                            mJoinPusherCallback = null;
                        }
                    }
                };
            }

            mHandler.removeCallbacks(mJoinPusherTimeoutTask);
            mHandler.postDelayed(mJoinPusherTimeoutTask, timeout * 1000);

            String content = new Gson().toJson(request, new TypeToken<CommonJson<JoinPusherRequest>>() {
            }.getType());
            String toUserID = getRoomCreator(mCurrRoomID);
            mIMMessageMgr.sendC2CCustomMessage(toUserID, content, new IMMessageMgr.Callback() {
                @Override
                public void onError(final int code, final String errInfo) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mJoinPusherCallback != null) {
                                callback.onError(code, errInfo);
                            }
                        }
                    });
                }

                @Override
                public void onSuccess(Object... args) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 大主播接受连麦请求
     *
     * @param userID
     */
    public void acceptJoinPusher(String userID) {
        try {
            CommonJson<JoinPusherResponse> response = new CommonJson<>();
            response.cmd = "linkmic";
            response.data = new JoinPusherResponse();
            response.data.type = "response";
            response.data.result = "accept";
            response.data.message = "";
            response.data.roomID = mCurrRoomID;
            String content = new Gson().toJson(response, new TypeToken<CommonJson<JoinPusherResponse>>() {
            }.getType());
            mIMMessageMgr.sendC2CCustomMessage(userID, content, new IMMessageMgr.Callback() {
                @Override
                public void onError(final int code, final String errInfo) {

                }

                @Override
                public void onSuccess(Object... args) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 大主播拒绝连麦请求
     *
     * @param userID
     * @param reason
     */
    public void rejectJoinPusher(String userID, String reason) {
        try {
            CommonJson<JoinPusherResponse> response = new CommonJson<>();
            response.cmd = "linkmic";
            response.data = new JoinPusherResponse();
            response.data.type = "response";
            response.data.result = "reject";
            response.data.message = reason;
            response.data.roomID = mCurrRoomID;
            String content = new Gson().toJson(response, new TypeToken<CommonJson<JoinPusherResponse>>() {
            }.getType());
            mIMMessageMgr.sendC2CCustomMessage(userID, content, new IMMessageMgr.Callback() {
                @Override
                public void onError(final int code, final String errInfo) {

                }

                @Override
                public void onSuccess(Object... args) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 大主播踢掉某一个小主播
     *
     * @param userID
     */
    public void kickoutSubPusher(String userID) {
        try {
            CommonJson<KickoutResponse> response = new CommonJson<>();
            response.cmd = "linkmic";
            response.data = new KickoutResponse();
            response.data.type = "kickout";
            response.data.roomID = mCurrRoomID;
            String content = new Gson().toJson(response, new TypeToken<CommonJson<KickoutResponse>>() {
            }.getType());
            mIMMessageMgr.sendC2CCustomMessage(userID, content, new IMMessageMgr.Callback() {
                @Override
                public void onError(final int code, final String errInfo) {

                }

                @Override
                public void onSuccess(Object... args) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 启动摄像头预览
     *
     * @param videoView 摄像头预览组件
     */
    public synchronized void startLocalPreview(final @NonNull TXCloudVideoView videoView) {
        super.startLocalPreview(videoView);
        mPreviewType = LIVEROOM_CAMERA_PREVIEW;
    }

    /**
     * 停止摄像头预览
     */
    public synchronized void stopLocalPreview() {
        super.stopLocalPreview();
    }

    /**
     * 启动录屏.
     */
    public synchronized void startScreenCapture() {
        initLivePusher();
        if (mTXLivePusher != null) {
            mTXLivePusher.startScreenCapture();
        }
        mPreviewType = LIVEROOM_SCREEN_PREVIEW;
    }

    /**
     * 结束录屏.
     */
    public synchronized void stopScreenCapture() {
        if (mTXLivePusher != null) {
            mTXLivePusher.setPushListener(null);
            mTXLivePusher.stopScreenCapture();
            mTXLivePusher.stopPusher();
            mTXLivePusher = null;
        }
    }

    /**
     * 远端视频播放回调通知
     */
    public interface RemoteViewPlayCallback {
        void onPlayBegin();

        void onPlayError();
    }

    /**
     * 启动远端视频
     *
     * @param videoView  视频预览组件
     * @param pusherInfo 对应视频的成员
     * @param callback   视频播放回调
     */
    public void addRemoteView(final @NonNull TXCloudVideoView videoView, final @NonNull PusherInfo pusherInfo, final RemoteViewPlayCallback callback) {
        super.addRemoteView(videoView, pusherInfo, new PlayCallback() {
            @Override
            public void onPlayBegin() {
                if (callback != null) {
                    callback.onPlayBegin();
                }
            }

            @Override
            public void onPlayError() {
                if (callback != null) {
                    callback.onPlayError();
                }
            }
        });
    }

    /**
     * 停止远端视频
     *
     * @param pusherInfo 对应视频的成员
     */
    public void deleteRemoteView(final @NonNull PusherInfo pusherInfo) {
        super.deleteRemoteView(pusherInfo);
        if (mSelfRoleType == LIVEROOM_ROLE_PUSHER) {
            mStreamMixturer.delSubVideoStream(pusherInfo.accelerateURL);
        }
    }

    /**
     * LiveRoom 发送文本消息Callback
     */
    public interface SendTextMessageCallback {
        void onError(int errCode, String errInfo);

        void onSuccess();
    }

    /**
     * LiveRoom 发送文本消息
     *
     * @param message
     * @param callback
     */
    public void sendRoomTextMsg(@NonNull String message, final SendTextMessageCallback callback) {
        super.sendRoomTextMsg(message, new IMMessageMgr.Callback() {
            @Override
            public void onError(final int code, final String errInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onError(code, errInfo);
                        }
                    }
                });
            }

            @Override
            public void onSuccess(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }
                });
            }
        });
    }

    /**
     * LiveRoom 发送房间自定义消息消息Callback
     */
    public interface SendCustomMessageCallback {
        void onError(int errCode, String errInfo);

        void onSuccess();
    }

    /**
     * LiveRoom 发送自定义消息
     *
     * @param cmd
     * @param message
     * @param callback
     */
    public void sendRoomCustomMsg(@NonNull String cmd, @NonNull String message, final SendCustomMessageCallback callback) {
        super.sendRoomCustomMsg(cmd, message, new IMMessageMgr.Callback() {
            @Override
            public void onError(final int code, final String errInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onError(code, errInfo);
                        }
                    }
                });
            }

            @Override
            public void onSuccess(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }
                });
            }
        });
    }

    /**
     * 房间自定义数据 加操作
     *
     * @param fieldName
     * @param count
     */
    public void decCustomInfo(String fieldName, int count) {
        mHttpRequest.setCustomInfo(mCurrRoomID, fieldName, "dec", count, new HttpRequests.OnResponseCallback<HttpResponse>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse data) {
                Log.d("", "set custominfo ret code :" + retcode);
            }
        });
    }

    /**
     * 房间自定义数据 减操作
     *
     * @param fieldName
     * @param count
     */
    public void incCustomInfo(String fieldName, int count) {
        mHttpRequest.setCustomInfo(mCurrRoomID, fieldName, "inc", count, new HttpRequests.OnResponseCallback<HttpResponse>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse data) {
                Log.d("", "set custominfo ret code :" + retcode);
            }
        });
    }

    /**
     * 从前台切换到后台，关闭采集摄像头数据，推送默认图片
     */
    public void switchToBackground() {
        super.switchToBackground();
        mBackground = true;
        if (mSelfRoleType == LIVEROOM_ROLE_PLAYER) {
            if (mCurrRoomID != null && mCurrRoomID.length() > 0) {
                mTXLivePlayer.stopPlay(true);
            }
        }
    }

    /**
     * 由后台切换到前台，开启摄像头数据采集
     */
    public void switchToForeground() {
        super.switchToForeground();
        mBackground = false;
        if (mSelfRoleType == LIVEROOM_ROLE_PLAYER) {
            if (mCurrRoomID != null && mCurrRoomID.length() > 0) {
                if (mJoinPusher) {
                    String playUrl = getAcceleratePlayUrlByRoomID(mCurrRoomID);
                    if (playUrl != null && playUrl.length() > 0) {
                        mTXLivePlayer.startPlay(playUrl, TXLivePlayer.PLAY_TYPE_LIVE_RTMP_ACC);
                    }
                } else {
                    String playUrl = getMixedPlayUrlByRoomID(mCurrRoomID);
                    if (playUrl != null && playUrl.length() > 0) {
                        mTXLivePlayer.startPlay(playUrl, getPlayType(playUrl));
                    }
                }
            }
        }

        if (mTXLivePusher != null && mTXLivePusher.isPushing()) {
            onPusherChanged();
        }
    }

    /**
     * 切换摄像头
     */
    public void switchCamera() {
        super.switchCamera();
    }

    /**
     * 静音设置
     *
     * @param isMute 静音变量, true 表示静音， 否则 false
     */
    public void setMute(boolean isMute) {
        super.setMute(isMute);
    }

    /**
     * 设置美颜效果.
     *
     * @param style          美颜风格.三种美颜风格：0 ：光滑  1：自然  2：朦胧
     * @param beautyLevel    美颜等级.美颜等级即 beautyLevel 取值为0-9.取值为0时代表关闭美颜效果.默认值:0,即关闭美颜效果.
     * @param whiteningLevel 美白等级.美白等级即 whiteningLevel 取值为0-9.取值为0时代表关闭美白效果.默认值:0,即关闭美白效果.
     * @param ruddyLevel     红润等级.美白等级即 ruddyLevel 取值为0-9.取值为0时代表关闭美白效果.默认值:0,即关闭美白效果.
     * @return 是否成功设置美白和美颜效果. true:设置成功. false:设置失败.
     */
    public boolean setBeautyFilter(int style, int beautyLevel, int whiteningLevel, int ruddyLevel) {
        return super.setBeautyFilter(style, beautyLevel, whiteningLevel, ruddyLevel);
    }

    /**
     * 调整摄像头焦距
     *
     * @param value 焦距，取值 0~getMaxZoom();
     * @return true : 成功 false : 失败
     */
    public boolean setZoom(int value) {
        return super.setZoom(value);
    }

    /**
     * 设置播放端水平镜像与否(tips：推流端前置摄像头默认看到的是镜像画面，后置摄像头默认看到的是非镜像画面)
     *
     * @param enable true:播放端看到的是镜像画面,false:播放端看到的是镜像画面
     */
    public boolean setMirror(boolean enable) {
        return super.setMirror(enable);
    }

    /**
     * 调整曝光
     *
     * @param value 曝光比例，表示该手机支持最大曝光调整值的比例，取值范围从-1到1。
     *              负数表示调低曝光，-1是最小值，对应getMinExposureCompensation。
     *              正数表示调高曝光，1是最大值，对应getMaxExposureCompensation。
     *              0表示不调整曝光
     */
    public void setExposureCompensation(float value) {
        super.setExposureCompensation(value);
    }

    /**
     * 设置麦克风的音量大小.
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @param x: 音量大小,1为正常音量,建议值为0~2,如果需要调大音量可以设置更大的值.
     * @return 是否成功设置麦克风的音量大小. true:设置麦克风的音量成功. false:设置麦克风的音量失败.
     */
    public boolean setMicVolume(float x) {
        return super.setMicVolume(x);
    }

    /**
     * 设置背景音乐的音量大小.
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @param x 音量大小,1为正常音量,建议值为0~2,如果需要调大背景音量可以设置更大的值.
     * @return 是否成功设置背景音乐的音量大小. true:设置背景音的音量成功. false:设置背景音的音量失败.
     */
    public boolean setBGMVolume(float x) {
        return super.setBGMVolume(x);
    }

    public void setBGMNofify(TXLivePusher.OnBGMNotify notify) {
        super.setBGMNofify(notify);
    }

    /**
     * 获取音乐文件时长.
     *
     * @param path 音乐文件路径
     *             path == null 获取当前播放歌曲时长
     *             path != null 获取path路径歌曲时长
     * @return 音乐文件时长, 单位ms.
     */
    public int getMusicDuration(String path) {
        return super.getMusicDuration(path);
    }

    /**
     * 播放背景音乐.
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @param path 背景音乐文件路径.
     * @return 是否成功播放背景音乐. true:播放成功. false:播放失败.
     */
    public boolean playBGM(String path) {
        if (mTXLivePusher != null) {
            return mTXLivePusher.playBGM(path);
        }
        return false;
    }

    /**
     * 停止播放背景音乐.
     * <p>
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @return 是否成功停止播放背景音乐. true:停止播放成功. false:停止播放失败.
     */
    public boolean stopBGM() {
        return super.stopBGM();
    }

    /**
     * 暂停播放背景音乐.
     * <p>
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @return 是否成功暂停播放背景音乐. true:暂停播放成功. false:暂停播放失败.
     */
    public boolean pauseBGM() {
        return super.pauseBGM();
    }

    /**
     * 恢复播放背景音乐.
     * <p>
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     *
     * @return 是否成功恢复播放背景音乐. true:恢复播放成功. false:恢复播放失败.
     */
    public boolean resumeBGM() {
        return super.resumeBGM();
    }

    /**
     * setFilterImage 设置指定素材滤镜特效
     *
     * @param bmp: 指定素材，即颜色查找表图片。注意：一定要用png图片格式！！！
     *             demo用到的滤镜查找表图片位于RTMPAndroidDemo/app/src/main/res/drawable-xxhdpi/目录下。
     */
    public void setFilter(Bitmap bmp) {
        super.setFilter(bmp);
    }

    /**
     * 设置动效贴图文件位置
     *
     * @param specialValue
     */
    public void setMotionTmpl(String specialValue) {
        super.setMotionTmpl(specialValue);
    }

    /**
     * 设置绿幕文件:目前图片支持jpg/png，视频支持mp4/3gp等Android系统支持的格式
     * API要求18
     *
     * @param file ：绿幕文件位置，支持两种方式：
     *             1.资源文件放在assets目录，path直接取文件名
     *             2.path取文件绝对路径
     * @return false:调用失败
     * true:调用成功
     */
    public boolean setGreenScreenFile(String file) {
        return super.setGreenScreenFile(file);
    }

    /**
     * 设置大眼效果.
     *
     * @param level 大眼等级取值为0-9.取值为0时代表关闭美颜效果.默认值:0
     */
    public void setEyeScaleLevel(int level) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setEyeScaleLevel(level);
        }
    }

    /**
     * 设置瘦脸效果.
     *
     * @param level 瘦脸等级取值为0-9.取值为0时代表关闭美颜效果.默认值:0
     */
    public void setFaceSlimLevel(int level) {
        super.setFaceSlimLevel(level);
    }

    /**
     * V 脸
     *
     * @param level
     */
    public void setFaceVLevel(int level) {
        super.setFaceVLevel(level);
    }

    /**
     * setSpecialRatio 设置滤镜效果程度
     *
     * @param ratio: 从0到1，越大滤镜效果越明显，默认取值0.5
     */
    public void setSpecialRatio(float ratio) {
        super.setSpecialRatio(ratio);
    }

    /**
     * 缩脸
     *
     * @param level
     */
    public void setFaceShortLevel(int level) {
        super.setFaceShortLevel(level);
    }

    /**
     * 下巴
     *
     * @param scale
     */
    public void setChinLevel(int scale) {
        super.setChinLevel(scale);
    }

    /**
     * 小鼻
     *
     * @param scale
     */
    public void setNoseSlimLevel(int scale) {
        super.setNoseSlimLevel(scale);
    }


    /**
     * 混响
     *
     * @param reverbType
     */
    public void setReverb(int reverbType) {
        super.setReverb(reverbType);
    }

    /**
     * 变声
     *
     * @param voiceChangerType
     */
    public void setVoiceChangerType(int voiceChangerType) {
        super.setVoiceChangerType(voiceChangerType);
    }

    /**
     * 设置从前台切换到后台，推送的图片
     *
     * @param bitmap
     */
    public void setPauseImage(final @Nullable Bitmap bitmap) {
        super.setPauseImage(bitmap);
    }

    /**
     * 从前台切换到后台，关闭采集摄像头数据
     *
     * @param id 设置默认显示图片的资源文件
     */
    public void setPauseImage(final @IdRes int id) {
        super.setPauseImage(id);
    }

    /**
     * 录制回调接口，需要在启动播放后设置才生效
     *
     * @param listener 录制回调接口.
     */
    public void setVideoRecordListener(TXRecordCommon.ITXVideoRecordListener listener) {
        if (mTXLivePlayer != null) {
            mTXLivePlayer.setVideoRecordListener(listener);
        }
    }

    /**
     * 启动视频录制
     *
     * @param recordType
     * @return 0表示成功，非0表示失败
     */
    public int startRecord(int recordType) {
        if (mTXLivePlayer != null) {
            return mTXLivePlayer.startRecord(recordType);
        }

        return -1;
    }

    /**
     * 停止视频录制.
     *
     * @return 0表示成功，非0表示失败 .
     */
    public int stopRecord() {
        if (mTXLivePlayer != null) {
            return mTXLivePlayer.stopRecord();
        }
        return -1;
    }

    /**
     * 更新自己的用户信息
     *
     * @param userName   昵称
     * @param userAvatar 头像
     */
    public void updateSelfUserInfo(String userName, String userAvatar) {
        super.updateSelfUserInfo(userName, userAvatar);
    }

    @Override
    public void onConnected() {
        mRoomListenerCallback.printLog("[IM] online");
    }

    @Override
    public void onDisconnected() {
        mRoomListenerCallback.printLog("[IM] offline");
    }

    @Override
    public void onPusherChanged() {
        if (mBackground == false) {
            if (mSelfRoleType == LIVEROOM_ROLE_PUSHER || mJoinPusher) {
                mRoomListenerCallback.onDebugLog("[LiveRoom] updatePushers called");
                updatePushers(true, new UpdatePushersCallback() {
                    @Override
                    public void onUpdatePushersComplete(int retcode, List<PusherInfo> newPushers, List<PusherInfo> delPushers, HashMap<String, PusherInfo> mergedPushers) {
                        if (retcode == 0) {
                            mRoomListenerCallback.onDebugLog(String.format("[LiveRoom][updatePushers] new(%d), remove(%d)", newPushers.size(), delPushers.size()));

                            for (PusherInfo member : delPushers) {
                                mRoomListenerCallback.onPusherQuit(member);
                                if (mSelfRoleType == LIVEROOM_ROLE_PUSHER) {
                                    mStreamMixturer.delSubVideoStream(member.accelerateURL);
                                }
                            }

                            for (PusherInfo member : newPushers) {
                                mRoomListenerCallback.onPusherJoin(member);
                                if (mSelfRoleType == LIVEROOM_ROLE_PUSHER) {
                                    mStreamMixturer.addSubVideoStream(member.accelerateURL);
                                }
                            }

                            if (mSelfRoleType == LIVEROOM_ROLE_PUSHER) {
                                if (mPushers.size() == 0 && mergedPushers.size() > 0) {
                                    if (mTXLivePusher != null) {
                                        mTXLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_LINKMIC_MAIN_PUBLISHER, true, false);
                                    }
                                }
                                if (mPushers.size() > 0 && mergedPushers.size() == 0) {
                                    if (mTXLivePusher != null) {
                                        mTXLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_HIGH_DEFINITION, false, false);
                                        TXLivePushConfig config = mTXLivePusher.getConfig();
                                        config.setVideoEncodeGop(5);
                                        mTXLivePusher.setConfig(config);
                                    }
                                }
                            }

                            mPushers.clear();
                            mPushers = mergedPushers;
                        } else {
                            mRoomListenerCallback.onDebugLog("[LiveRoom] updatePushers failed");
                        }
                    }
                });
            }
        }
        ;
    }

    @Override
    public void onGroupDestroyed(final String groupID) {
        mRoomListenerCallback.onDebugLog("[LiveRoom] onGroupDestroyed called , group id is" + groupID);
        if (mCurrRoomID != null && mCurrRoomID.equalsIgnoreCase(groupID)) {
            mRoomListenerCallback.onRoomClosed(mCurrRoomID);
        }
    }

    @Override
    public void onGroupTextMessage(final String groupID, final String senderID, final String userName, final String headPic, final String message) {
        if (mRoomListenerCallback != null) {
            mRoomListenerCallback.onRecvRoomTextMsg(groupID, senderID, userName, headPic, message);
        }
    }

    @Override
    public void onGroupCustomMessage(String groupID, String senderID, String message) {
        CustomMessage customMessage = new Gson().fromJson(message, CustomMessage.class);
        if (mRoomListenerCallback != null) {
            mRoomListenerCallback.onRecvRoomCustomMsg(groupID, senderID, customMessage.userName, customMessage.userAvatar, customMessage.cmd, customMessage.msg);
        }
    }

    @Override
    public void onC2CCustomMessage(String sendId, String message) {
        try {
            JoinPusherRequest request = new Gson().fromJson(message, JoinPusherRequest.class);
            if (request != null && request.type.equalsIgnoreCase("request")) {
                if (request.roomID.equalsIgnoreCase(mCurrRoomID)) {
                    synchronized (this) {
                        if (mPushers.containsKey(request.userID)) {
                            mRoomListenerCallback.onPusherQuit(mPushers.get(request.userID));
                            mPushers.remove(request.userID);
                        }
                    }
                    mRoomListenerCallback.onRecvJoinPusherRequest(request.userID, request.userName, request.userAvatar);
                }
                return;
            }

            JoinPusherResponse response = new Gson().fromJson(message, JoinPusherResponse.class);
            if (response != null && response.type.equalsIgnoreCase("response")) {
                if (response.roomID.equalsIgnoreCase(mCurrRoomID)) {
                    String result = response.result;
                    if (result != null) {
                        if (result.equalsIgnoreCase("accept")) {
                            if (mJoinPusherCallback != null) {
                                mJoinPusherCallback.onAccept();
                                mJoinPusherCallback = null;
                                mHandler.removeCallbacks(mJoinPusherTimeoutTask);
                            }
                            return;
                        } else if (result.equalsIgnoreCase("reject")) {
                            if (mJoinPusherCallback != null) {
                                mJoinPusherCallback.onReject(response.message);
                                mJoinPusherCallback = null;
                                mHandler.removeCallbacks(mJoinPusherTimeoutTask);
                            }
                            return;
                        }
                    }
                    if (mJoinPusherCallback != null) {
                        mJoinPusherCallback.onTimeOut();
                        mJoinPusherCallback = null;
                        mHandler.removeCallbacks(mJoinPusherTimeoutTask);
                    }
                }
                return;
            }

            KickoutResponse kickreq = new Gson().fromJson(message, KickoutResponse.class);
            if (kickreq != null && kickreq.type.equalsIgnoreCase("kickout")) {
                if (kickreq.roomID.equalsIgnoreCase(mCurrRoomID)) {
                    mRoomListenerCallback.onKickOut();
                }
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDebugLog(final String log) {
        mRoomListenerCallback.onDebugLog(log);
    }

    protected void invokeDebugLog(String log) {
        mRoomListenerCallback.onDebugLog(log);
    }

    protected void invokeError(int errorCode, String errorMessage) {
        mRoomListenerCallback.onError(errorCode, errorMessage);
    }

    private class StreamMixturer {
        private final String TAG = StreamMixturer.class.getName();

        private String mMainStreamId = "";
        private Vector<String> mSubStreamIds = new java.util.Vector<String>();
        private int mMainStreamWidth = 540;
        private int mMainStreamHeight = 960;

        public StreamMixturer() {

        }

        public void setMainVideoStream(String streamUrl) {
            mMainStreamId = getStreamIDByStreamUrl(streamUrl);

            Log.e(TAG, "MergeVideoStream: setMainVideoStream " + mMainStreamId);
        }

        public void setMainVideoStreamResolution(int width, int height) {
            if (width > 0 && height > 0) {
                mMainStreamWidth = width;
                mMainStreamHeight = height;
            }
        }

        public void addSubVideoStream(String streamUrl) {
            if (mSubStreamIds.size() > 3) {
                return;
            }

            String streamId = getStreamIDByStreamUrl(streamUrl);

            Log.e(TAG, "MergeVideoStream: addSubVideoStream " + streamId);

            if (streamId == null || streamId.length() == 0) {
                return;
            }

            for (String item : mSubStreamIds) {
                if (item.equalsIgnoreCase(streamId)) {
                    return;
                }
            }

            mSubStreamIds.add(streamId);
            sendStreamMergeRequest(5);
        }

        public void delSubVideoStream(String streamUrl) {
            String streamId = getStreamIDByStreamUrl(streamUrl);

            Log.e(TAG, "MergeVideoStream: delSubVideoStream " + streamId);

            boolean bExist = false;
            for (String item : mSubStreamIds) {
                if (item.equalsIgnoreCase(streamId)) {
                    bExist = true;
                    break;
                }
            }

            if (bExist == true) {
                mSubStreamIds.remove(streamId);
                sendStreamMergeRequest(1);
            }
        }

        public void resetMergeState() {
            Log.e(TAG, "MergeVideoStream: resetMergeState");

            mSubStreamIds.clear();
            mMainStreamId = null;
            mMainStreamWidth = 540;
            mMainStreamHeight = 960;
        }

        private void sendStreamMergeRequest(final int retryCount) {
            if (mMainStreamId == null || mMainStreamId.length() == 0) {
                return;
            }

            final JSONObject requestParam = createRequestParam();
            if (requestParam == null) {
                return;
            }

            internalSendRequest(retryCount, true, requestParam);
        }

        private void internalSendRequest(final int retryIndex, final boolean runImmediately, final JSONObject requestParam) {
            new Thread() {
                @Override
                public void run() {
                    if (runImmediately == false) {
                        try {
                            sleep(2000, 0);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    String streamsInfo = "mainStream: " + mMainStreamId;
                    for (int i = 0; i < mSubStreamIds.size(); ++i) {
                        streamsInfo = streamsInfo + " subStream" + i + ": " + mSubStreamIds.get(i);
                    }

                    Log.e(TAG, "MergeVideoStream: send request, " + streamsInfo + " retryIndex: " + retryIndex + "    " + requestParam.toString());
                    if (mHttpRequest != null) {
                        mHttpRequest.mergeStream(mCurrRoomID, mSelfAccountInfo.userID, requestParam, new HttpRequests.OnResponseCallback<HttpResponse.MergeStream>() {
                            @Override
                            public void onResponse(int retcode, @Nullable String strMessage, @Nullable HttpResponse.MergeStream result) {
                                Log.e(TAG, "MergeVideoStream: recv response, message = " + (result != null ? "[code = " + result.code + " msg = " + result.message + "]" : "null"));

                                if (result != null && result.code == 0) {
                                    return;
                                } else {
                                    int tempRetryIndex = retryIndex - 1;
                                    if (tempRetryIndex > 0) {
                                        internalSendRequest(tempRetryIndex, false, requestParam);
                                    }
                                }
                            }
                        });
                    }
                }
            }.start();
        }

        private JSONObject createRequestParam() {

            JSONObject requestParam = null;

            try {
                // input_stream_list
                JSONArray inputStreamList = new JSONArray();

                // 大主播
                {
                    JSONObject layoutParam = new JSONObject();
                    layoutParam.put("image_layer", 1);

                    JSONObject mainStream = new JSONObject();
                    mainStream.put("input_stream_id", mMainStreamId);
                    mainStream.put("layout_params", layoutParam);

                    inputStreamList.put(mainStream);
                }

                int subWidth = 160;
                int subHeight = 240;
                int offsetHeight = 90;
                if (mMainStreamWidth < 540 || mMainStreamHeight < 960) {
                    subWidth = 120;
                    subHeight = 180;
                    offsetHeight = 60;
                }
                int subLocationX = mMainStreamWidth - subWidth;
                int subLocationY = mMainStreamHeight - subHeight - offsetHeight;

                // 小主播
                int layerIndex = 0;
                for (String item : mSubStreamIds) {
                    JSONObject layoutParam = new JSONObject();
                    layoutParam.put("image_layer", layerIndex + 2);
                    layoutParam.put("image_width", subWidth);
                    layoutParam.put("image_height", subHeight);
                    layoutParam.put("location_x", subLocationX);
                    layoutParam.put("location_y", subLocationY - layerIndex * subHeight);

                    JSONObject subStream = new JSONObject();
                    subStream.put("input_stream_id", item);
                    subStream.put("layout_params", layoutParam);

                    inputStreamList.put(subStream);
                    ++layerIndex;
                }

                // para
                JSONObject para = new JSONObject();
                para.put("app_id", Long.valueOf(mAppID));
                para.put("interface", "mix_streamv2.start_mix_stream_advanced");
                para.put("mix_stream_session_id", mMainStreamId);
                para.put("output_stream_id", mMainStreamId);
                para.put("input_stream_list", inputStreamList);

                // interface
                JSONObject interfaceObj = new JSONObject();
                interfaceObj.put("interfaceName", "Mix_StreamV2");
                interfaceObj.put("para", para);

                // requestParam
                requestParam = new JSONObject();
                requestParam.put("timestamp", System.currentTimeMillis() / 1000);
                requestParam.put("eventId", System.currentTimeMillis() / 1000);
                requestParam.put("interface", interfaceObj);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return requestParam;
        }

        private String getStreamIDByStreamUrl(String strStreamUrl) {
            if (strStreamUrl == null || strStreamUrl.length() == 0) {
                return null;
            }

            //推流地址格式：rtmp://8888.livepush.myqcloud.com/path/8888_test_12345_test?txSecret=aaaa&txTime=bbbb
            //拉流地址格式：rtmp://8888.liveplay.myqcloud.com/path/8888_test_12345_test
            //            http://8888.liveplay.myqcloud.com/path/8888_test_12345_test.flv
            //            http://8888.liveplay.myqcloud.com/path/8888_test_12345_test.m3u8


            String subString = strStreamUrl;

            {
                //1 截取第一个 ？之前的子串
                int index = subString.indexOf("?");
                if (index != -1) {
                    subString = subString.substring(0, index);
                }
                if (subString == null || subString.length() == 0) {
                    return null;
                }
            }

            {
                //2 截取最后一个 / 之后的子串
                int index = subString.lastIndexOf("/");
                if (index != -1) {
                    subString = subString.substring(index + 1);
                }

                if (subString == null || subString.length() == 0) {
                    return null;
                }
            }

            {
                //3 截取第一个 . 之前的子串
                int index = subString.indexOf(".");
                if (index != -1) {
                    subString = subString.substring(0, index);
                }
                if (subString == null || subString.length() == 0) {
                    return null;
                }
            }

            return subString;
        }
    }

    private class RoomListenerCallback implements ILiveRoomListener {
        private final Handler handler;
        private ILiveRoomListener liveRoomListener;

        public RoomListenerCallback(ILiveRoomListener liveRoomListener) {
            this.liveRoomListener = liveRoomListener;
            this.handler = new Handler(Looper.getMainLooper());
        }

        public void setRoomMemberEventListener(ILiveRoomListener liveRoomListener) {
            this.liveRoomListener = liveRoomListener;
        }

        @Override
        public void onPusherJoin(final PusherInfo pusherInfo) {
            printLog("[LiveRoom] onPusherJoin, UserID {%s} PlayUrl {%s}", pusherInfo.userID, pusherInfo.accelerateURL);
            if (liveRoomListener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onPusherJoin(pusherInfo);
                        }
                    }
                });
            }
        }

        @Override
        public void onPusherQuit(final PusherInfo pusherInfo) {
            printLog("[LiveRoom] onPusherQuit, UserID {%s} PlayUrl {%s}", pusherInfo.userID, pusherInfo.accelerateURL);
            if (liveRoomListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onPusherQuit(pusherInfo);
                        }
                    }
                });
        }

        @Override
        public void onRecvJoinPusherRequest(final String userId, final String userName, final String userAvatar) {
            printLog("[LiveRoom] onRecvJoinPusherRequest, UserID {%s} UserName {%s}", userId, userName);
            if (liveRoomListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onRecvJoinPusherRequest(userId, userName, userAvatar);
                        }
                    }
                });
        }

        @Override
        public void onKickOut() {
            printLog("[LiveRoom] onKickedOut");
            if (liveRoomListener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onKickOut();
                        }
                    }
                });
            }
        }

        @Override
        public void onGetPusherList(final List<PusherInfo> pusherInfoList) {
            if (liveRoomListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onGetPusherList(pusherInfoList);
                        }
                    }
                });
        }

        @Override
        public void onRoomClosed(final String roomId) {
            printLog("[LiveRoom] onRoomClosed, RoomId {%s}", roomId);
            if (liveRoomListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onRoomClosed(roomId);
                        }
                    }
                });
        }

        @Override
        public void onRecvRoomTextMsg(final String roomId, final String userID, final String userName, final String headPic, final String message) {
            if (liveRoomListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onRecvRoomTextMsg(roomId, userID, userName, headPic, message);
                        }
                    }
                });
        }

        @Override
        public void onRecvRoomCustomMsg(final String roomID, final String userID, final String userName, final String headPic, final String cmd, final String message) {
            if (liveRoomListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onRecvRoomCustomMsg(roomID, userID, userName, headPic, cmd, message);
                        }
                    }
                });
        }

        void printLog(String format, Object... args) {
            String line = String.format(format, args);
            onDebugLog(line);
        }

        @Override
        public void onDebugLog(final String line) {
            if (liveRoomListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onDebugLog(line);
                        }
                    }
                });
        }

        @Override
        public void onError(final int errorCode, final String errorMessage) {
            if (liveRoomListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (liveRoomListener != null) {
                            liveRoomListener.onError(errorCode, errorMessage);
                        }
                    }
                });
        }
    }

    private class JoinPusherRequest {
        public String type;
        public String roomID;
        public String userID;
        public String userName;
        public String userAvatar;
    }

    private class JoinPusherResponse {
        public String type;
        public String roomID;
        public String result;
        public String message;
    }

    private class KickoutResponse {
        public String type;
        public String roomID;
    }
}
