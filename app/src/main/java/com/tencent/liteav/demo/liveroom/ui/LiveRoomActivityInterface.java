package com.tencent.liteav.demo.liveroom.ui;

import com.tencent.liteav.demo.liveroom.LiveRoom;

/**
 * Created by dennyfeng on 2017/11/22.
 */

public interface LiveRoomActivityInterface {
    LiveRoom getLiveRoom();

    String getSelfUserID();//使用者的id就是useid
    String   getSelfUserName();
    void     showGlobalLog(boolean enable);
    void     printGlobalLog(String format, Object... args);
    void     setTitle(String s);
}
