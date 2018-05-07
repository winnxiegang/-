package com.tencent.liteav.demo.liveroom.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.liveroom.ILiveRoomListener;
import com.tencent.liteav.demo.liveroom.LiveRoom;
import com.tencent.liteav.demo.liveroom.ui.fragment.LiveRoomChatFragment;
import com.tencent.liteav.demo.liveroom.ui.fragment.LiveRoomListFragment;
import com.tencent.liteav.demo.roomutil.commondef.BaseRoom;
import com.tencent.liteav.demo.roomutil.commondef.LoginInfo;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.common.misc.CommonAppCompatActivity;
import com.tencent.liteav.demo.common.misc.NameGenerator;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;

//todo 直播+连麦 是在 秀场直播 和 在线教育 场景中经常使用的直播模式，
// todo 它既能支持高并发和低成本的在线直播，又能通过连麦实现主播和观众之间的视频通话互动，具有极强的场景适用性。
public class LiveRoomActivity extends CommonAppCompatActivity implements LiveRoomActivityInterface {

    private static final String TAG = LiveRoomActivity.class.getSimpleName();

    public final Handler uiHandler = new Handler();

    private LiveRoom liveRoom;
    private String userId;
    private String userName = NameGenerator.getRandomName();
    private String userAvatar = "avatar";
    private TextView titleTextView;
    private TextView globalLogTextview;
    private ScrollView globalLogTextviewContainer;
    private Runnable retryInitRoomRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_live_room);

        findViewById(R.id.liveroom_back_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        titleTextView = ((TextView) findViewById(R.id.liveroom_title_textview));
        titleTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (retryInitRoomRunnable != null) {
                    synchronized (LiveRoomActivity.this) {
                        retryInitRoomRunnable.run();
                        retryInitRoomRunnable = null;
                    }
                }
            }
        });

        globalLogTextview = ((TextView) findViewById(R.id.liveroom_global_log_textview));
        globalLogTextview.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new AlertDialog.Builder(LiveRoomActivity.this, R.style.RtmpRoomDialogTheme)
                        .setTitle("Global Log")
                        .setMessage("清除Log")
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        globalLogTextview.setText("");
                        dialog.dismiss();
                    }
                }).show();

                return true;
            }
        });

        globalLogTextviewContainer = ((ScrollView) findViewById(R.id.liveroom_global_log_container));

        liveRoom = new LiveRoom(this.getApplicationContext());
        liveRoom.setLiveRoomListener(new LiveRoomListener());//设置liveroom回调

        initializeLiveRoom();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        liveRoom.setLiveRoomListener(null);
        liveRoom.logout();
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
        if (fragment instanceof LiveRoomChatFragment) {
            ((LiveRoomChatFragment) fragment).onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onPermissionDisable() {
        new AlertDialog.Builder(this, R.style.RtmpRoomDialogTheme)
                .setMessage("需要录音和摄像头权限，请到【设置】【应用】打开")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
    }

    @Override
    public void onPermissionGranted() {

    }

    private class LoginInfoResponse {
        public int code;
        public String message;
        public int sdkAppID;
        public String accType;
        public String userID;
        public String userSig;
    }

    private class HttpInterceptorLog implements HttpLoggingInterceptor.Logger {
        @Override
        public void log(String message) {
            Log.i("HttpRequest", message + "\n");
        }
    }

    // todo 初始化直播间
    private void initializeLiveRoom() {
        setTitle("连接中...");

        SharedPreferences sp = getSharedPreferences("com.tencent.demo", Context.MODE_PRIVATE);
        String userIdFromSp = sp.getString("userID", "xg");
        String loginInfoCgi = BaseRoom.ROOM_SERVICE_DOMAIN + "utils/get_login_info_debug";
        if (!TextUtils.isEmpty(userIdFromSp)) {
            loginInfoCgi = loginInfoCgi + "?userID=" + "自己定义的用户名";
        }
        final Request request = new Request.Builder()
                .url(loginInfoCgi)
                .build();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor(new HttpInterceptorLog()).setLevel(HttpLoggingInterceptor.Level.BODY))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTitle("获取登录信息失败，点击重试");
                        printGlobalLog(String.format("[Activity]获取登录信息失败{%s}", e.getMessage()));
                        retryInitRoomRunnable = new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(LiveRoomActivity.this, "重试...", Toast.LENGTH_SHORT).show();
                                initializeLiveRoom();
                            }
                        };
                    }
                });
            }

            @Override
            public void onResponse(final Call call, final okhttp3.Response response) throws IOException {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String strBody = response.body().string();
                            LoginInfoResponse resp = new Gson().fromJson(strBody, LoginInfoResponse.class);
                            if (resp.code != 0) {
                                setTitle("获取登录信息失败");
                                printGlobalLog(String.format("[Activity]获取登录信息失败：{%s}", resp.message));
                                retryInitRoomRunnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(LiveRoomActivity.this, "重试...", Toast.LENGTH_SHORT).show();
                                        initializeLiveRoom();
                                    }
                                };
                            } else {
//                                {"code":0,"message":"请求成功","userID":"自己定义的用户名","sdkAppID":1400047134,"accType":"18647",
//                                        "userSig":"eJyrVgrxCdZLLCjITIlPLIk3LkpRslIyNDEwMDAxNzQ2UdKByCcn55fmlcSXVBakguQtzEzMoVKZKal5JZlpmalFQIkX7auebt-" +
//                                        "4dN2sJzs7n89qeT5lxbOO7U8n9ELVFqdkx4OtwmZHSWYu2GxTI1Mzc3NLQwuoeGpFQWZRanxiWgnYCiNTSyOgTpiJmelAMV-XQGdPx6xQjxILg1DL" +
//                                        "wBSPcvfUbJf0wML84GyTKMPK1Kqw3Moow8ws94J8DyOjcsdMp-I0Y48k08IoA-eKjJy0RIPwvPBI75TwSmNfXx8TD1-LPFcL92B-J-OSqnJbW6VaALiUYgI_"}
                                Log.d("login", strBody + "/ login ");
                                SharedPreferences sp = getSharedPreferences("com.tencent.demo", Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = sp.edit();
                                editor.putString("userID", resp.userID);
                                editor.commit();
                                internalInitializeLiveRoom(resp);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    /**
     * loginInfo	LoginInfo	登录参数，这部分可以参考 DOC。
     * callback	LoginCallback	登录成功与否的回调
     * 05-07 10:11:26.562 4494-4656/com.tencent.liteav.demo I/HttpRequest: <-- 200 OK https://room.qcloud.com/weapp/utils/get_login_info_debug?userID=user_235e5e37_b22c (217ms)
     05-07 10:11:26.572 4494-4494/com.tencent.liteav.demo D/login: 1400047134
     05-07 10:11:26.573 4494-4494/com.tencent.liteav.demo D/login: user_235e5e37_b22c
     05-07 10:11:26.573 4494-4494/com.tencent.liteav.demo D/login: eJxtjU1TgzAARP9Lzo6EQAg400PrtLbaVlMYZvCSoSQpsVIjCdiP8b*LiLde9*2*vYBkGd-mWivOcsu8moM74PoQQp*4ng9u-nhRfDQHy*xJi18eBj4ZkOLiYJVUou5AY0TNkIcFFh5hW4SKoWX4nvUn1*xWVb0VIxzgCIbhkIujVrVgubS9HOEIdct-o9p12WpK7xcPtHrdzJxYFln7NE8TV362S540pnKm6ZmT9eZlBpFTYn4eq4mJVmi-06qN06wh68UbHWdUBOXjHJptVj2-G0mc9nii5ddoBL5-AOpZVwk_
     05-07 10:11:26.573 4494-4494/com.tencent.liteav.demo D/login: 18647
     05-07 10:11:26.573 4494-4494/com.tencent.liteav.demo D/login: 兰陵王
     05-07 10:11:26.573 4494-4494/com.tencent.liteav.demo D/login: avatar
     *      Log.d("login", resp.sdkAppID + "");D/login: 1400047134
     Log.d("login", resp.userID + "");D/login: user_235e5e37_b22c
     Log.d("login", resp.userSig + "");eJxtjU1TgzAARP9Lzo6EQAg400PrtLbaVlMYZvCSoSQpsVIjCdiP8b。。。。
     Log.d("login", resp.accType + "");18647
     Log.d("login", userName + "");兰陵王
     Log.d("login", userAvatar + "");avatar
     * @param resp
     */
    private void internalInitializeLiveRoom(LoginInfoResponse resp) {
        LoginInfo loginInfo = new LoginInfo();
        loginInfo.sdkAppID = resp.sdkAppID;
        loginInfo.userID = resp.userID;
        loginInfo.userSig = resp.userSig;
        loginInfo.accType = resp.accType;
        loginInfo.userName = userName;
        loginInfo.userAvatar = userAvatar;
        liveRoom.login(BaseRoom.ROOM_SERVICE_DOMAIN + "live_room", loginInfo, new LiveRoom.LoginCallback() {
            @Override
            public void onError(int errCode, String errInfo) {
                setTitle(errInfo);
                printGlobalLog(String.format("[Activity]LiveRoom初始化失败：{%s}", errInfo));
                retryInitRoomRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(LiveRoomActivity.this, "重试...", Toast.LENGTH_SHORT).show();
                        initializeLiveRoom();
                    }
                };
            }

            @Override
            public void onSuccess(String userId) {
                setTitle("直播体验室");
                LiveRoomActivity.this.userId = userId;
                printGlobalLog("[Activity]LiveRoom初始化成功,userID{%s}", userId);
                Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
                if (!(fragment instanceof LiveRoomChatFragment)) {
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    fragment = LiveRoomListFragment.newInstance(userId);
                    ft.replace(R.id.rtmproom_fragment_container, fragment);
                    ft.commit();
                }
            }
        });
    }

    @Override
    public LiveRoom getLiveRoom() {
        return liveRoom;
    }

    @Override
    public String getSelfUserID() {
        return userId;
    }

    @Override
    public String getSelfUserName() {
        return userName;
    }

    @Override
    public void showGlobalLog(final boolean enable) {
        if (uiHandler != null)
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    globalLogTextviewContainer.setVisibility(enable ? View.VISIBLE : View.GONE);
                }
            });
    }

    @Override
    public void printGlobalLog(final String format, final Object... args) {
        if (uiHandler != null) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    SimpleDateFormat dataFormat = new SimpleDateFormat("HH:mm:ss");
                    String line = String.format("[%s] %s\n", dataFormat.format(new Date()), String.format(format, args));
                    globalLogTextview.append(line);
                    if (globalLogTextviewContainer.getVisibility() != View.GONE) {
                        globalLogTextviewContainer.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                }
            });
        }
    }

    @Override
    public void setTitle(final String s) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                int id;
                String ss = NameGenerator.replaceNonPrintChar(s, 20, "...", false);
                if (ss != null && ss.length() > 10) {
                    id = R.dimen.title_size_small;
                } else if (ss != null && ss.length() > 4) {
                    id = R.dimen.title_size_mid;
                } else {
                    id = R.dimen.title_size_big;
                }

                titleTextView.setLinksClickable(false);
                titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(id));
                titleTextView.setText(ss);
            }
        });
    }

    private final class LiveRoomListener implements ILiveRoomListener {
        @Override
        public void onRoomClosed(String roomId) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof LiveRoomChatFragment && fragment.isVisible()) {
                ((LiveRoomChatFragment) fragment).onRoomClosed(roomId);
            }
        }

        @Override
        public void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String msg) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof LiveRoomChatFragment && fragment.isVisible()) {
                ((LiveRoomChatFragment) fragment).onRecvRoomTextMsg(roomid, userid, userName, userAvatar, msg);
            }
        }

        @Override
        public void onRecvRoomCustomMsg(final String roomID, final String userID, final String userName, final String userAvatar, final String cmd, final String message) {
            //do nothing
        }

        @Override
        public void onDebugLog(String line) {
            printGlobalLog(line);
        }

        @Override
        public void onError(final int errorCode, final String errorMessage) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof LiveRoomChatFragment && fragment.isVisible()) {
                ((LiveRoomChatFragment) fragment).onError(errorCode, errorMessage);
            }
        }

        @Override
        public void onGetPusherList(List<PusherInfo> pusherInfoList) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof LiveRoomChatFragment && fragment.isVisible()) {
                ((LiveRoomChatFragment) fragment).onGetPusherList(pusherInfoList);
            }
        }

        @Override
        public void onPusherJoin(PusherInfo pusherInfo) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof LiveRoomChatFragment && fragment.isVisible()) {
                ((LiveRoomChatFragment) fragment).onPusherJoin(pusherInfo);
            }
        }

        @Override
        public void onPusherQuit(PusherInfo pusherInfo) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof LiveRoomChatFragment && fragment.isVisible()) {
                ((LiveRoomChatFragment) fragment).onPusherQuit(pusherInfo);
            }
        }

        @Override
        public void onRecvJoinPusherRequest(String userId, String userName, String userAvatar) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof LiveRoomChatFragment && fragment.isVisible()) {
                ((LiveRoomChatFragment) fragment).onRecvJoinPusherRequest(userId, userName, userAvatar);
            }
        }

        @Override
        public void onKickOut() {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof LiveRoomChatFragment && fragment.isVisible()) {
                ((LiveRoomChatFragment) fragment).onKickOut();
            }
        }
    }
}
