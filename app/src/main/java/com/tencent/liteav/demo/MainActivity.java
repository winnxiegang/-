package com.tencent.liteav.demo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.liteav.demo.common.widget.ModuleEntryItemView;
import com.tencent.liteav.demo.liveroom.ui.LiveRoomActivity;
import com.tencent.liteav.demo.play.LivePlayerActivity;
import com.tencent.liteav.demo.play.NewVodPlayerActivity;
import com.tencent.liteav.demo.push.LivePublisherActivity;
import com.tencent.liteav.demo.rtcroom.ui.double_room.RTCDoubleRoomActivity;
import com.tencent.liteav.demo.rtcroom.ui.multi_room.RTCMultiRoomActivity;
import com.tencent.liteav.demo.webrtc.WebRTCActivity;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private ListView mListView;
    private int mSelectedModuleId = 0;
    private ModuleEntryItemView mSelectedView;
    private TextView mMainTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            Log.d(TAG, "brought to front");
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        mMainTitle = (TextView)findViewById(R.id.main_title);
        mMainTitle.setText("视频云 SDK DEMO ");
        mMainTitle.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                File logFile = getLastModifiedLogFile();
                if (logFile != null) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    intent.setPackage("com.tencent.mobileqq");
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(logFile));
                    startActivity(Intent.createChooser(intent, "分享日志"));
                }
                else {
                    Toast.makeText(MainActivity.this.getApplicationContext(), "日志文件不存在！", Toast.LENGTH_SHORT);
                }
                return false;
            }
        });
        mListView = (ListView) findViewById(R.id.entry_lv);
        EntryAdapter adapter = new EntryAdapter();
        mListView.setAdapter(adapter);
    }

    private class EntryAdapter extends BaseAdapter {

        public class ItemInfo {
            String mName;
            int mIconId;
            Class mClass;

            public ItemInfo(String name, int iconId, Class c) {
                mName = name;
                mIconId = iconId;
                mClass = c;
            }
        }

        private ArrayList<ItemInfo> mData = new ArrayList<>();

        public EntryAdapter() {
            super();
            createData();
        }

        private void createData() {
            mData.add(new ItemInfo("直播体验室", R.drawable.room_live, LiveRoomActivity.class));
            mData.add(new ItemInfo("双人音视频", R.drawable.room_double,  RTCDoubleRoomActivity.class));
            mData.add(new ItemInfo("多人音视频", R.drawable.room_multi, RTCMultiRoomActivity.class));
            mData.add(new ItemInfo("WebRTC", R.drawable.room_multi,  WebRTCActivity.class));
            mData.add(new ItemInfo("点播播放器", R.drawable.play, NewVodPlayerActivity.class));
            mData.add(new ItemInfo("RTMP 推流", R.drawable.push, LivePublisherActivity.class));
            mData.add(new ItemInfo("直播播放器", R.drawable.live, LivePlayerActivity.class));
            mData.add(new ItemInfo("低延时播放", R.drawable.realtime_play,  LivePlayerActivity.class));
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (null == convertView) {
                convertView = new ModuleEntryItemView(MainActivity.this);
            }
            ItemInfo info = (ItemInfo) getItem(position);
            ModuleEntryItemView v = (ModuleEntryItemView) convertView;
            v.setContent(info.mName, info.mIconId);
            v.setTag(info);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ItemInfo itemInfo = (ItemInfo) v.getTag();
                    Intent intent = new Intent(MainActivity.this, itemInfo.mClass);
                    intent.putExtra("TITLE", itemInfo.mName);
                    if (itemInfo.mIconId == R.drawable.play) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_VOD_PLAY);
                    } else if (itemInfo.mIconId == R.drawable.live) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_LIVE_PLAY);
                    } else if (itemInfo.mIconId == R.drawable.mic) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_LINK_MIC);
                    } else if (itemInfo.mIconId == R.drawable.cut) {
                    } else if (itemInfo.mIconId == R.drawable.composite) {
                    } else if (itemInfo.mIconId == R.drawable.conf_icon) {
                    } else if (itemInfo.mIconId == R.drawable.realtime_play) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_REALTIME_PLAY);
                    }

                    if(itemInfo.mName.equals("短视频上传")){
                    }
                    if (mSelectedView != null) {
                        mSelectedView.setBackgroudId(R.drawable.block_normal);
                    }
                    mSelectedModuleId = itemInfo.mIconId;
                    mSelectedView = (ModuleEntryItemView)v;
                    mSelectedView.setBackgroudId(R.drawable.block_pressed);
                    MainActivity.this.startActivity(intent);
                }
            });
            if (mSelectedModuleId == info.mIconId) {
                mSelectedView = v;
                mSelectedView.setBackgroudId(R.drawable.block_pressed);
            }

            return convertView;
        }
    }

    private File getLastModifiedLogFile() {
        File retFile = null;

        File directory = new File("/sdcard/log/tencent/liteav");
        if (directory != null && directory.exists() && directory.isDirectory()) {
            long lastModify = 0;
            File files[] = directory.listFiles();
            if (files != null && files.length > 0) {
                for (File file : files) {
                    if (file.getName().endsWith("xlog")) {
                        if (file.lastModified() > lastModify) {
                            retFile = file;
                            lastModify = file.lastModified();
                        }
                    }
                }
            }
        }

        return retFile;
    }
}
