package io.agora.openlive.voice.only.ui;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import io.agora.openlive.voice.only.R;
import io.agora.openlive.voice.only.model.AGEventHandler;
import io.agora.openlive.voice.only.model.ConstantApp;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IAudioEffectManager;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.IAudioFrameObserver;

public class LiveRoomActivity extends BaseActivity implements AGEventHandler ,IAudioFrameObserver{

    private final static Logger log = LoggerFactory.getLogger(LiveRoomActivity.class);

    private volatile boolean mAudioMuted = false;
    private volatile boolean mMaleMagnetic= false;
    private volatile boolean mVocal= false;
    private volatile boolean mAudioMixing = false;
    private volatile boolean mPlayEffect = false;
    private volatile boolean mKTV = false;

    private volatile int mAudioRouting = -1; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    @Override
    protected void initUIandEvent() {
        event().addEventHandler(this);

        Intent i = getIntent();

        int cRole = i.getIntExtra(ConstantApp.ACTION_KEY_CROLE, 0);

        if (cRole == 0) {
            throw new RuntimeException("Should not reach here");
        }

        String roomName = i.getStringExtra(ConstantApp.ACTION_KEY_ROOM_NAME);

        doConfigEngine(cRole);

        ImageView button1 = (ImageView) findViewById(R.id.switch_broadcasting_id);
        ImageView button2 = (ImageView) findViewById(R.id.mute_local_speaker_id);

        if (isBroadcaster(cRole)) {
            broadcasterUI(button1, button2);
        } else {
            audienceUI(button1, button2);
        }
        worker().getRtcEngine().registerAudioFrameObserver(this);
        worker().getRtcEngine().setPlaybackAudioFrameParameters(48000 , 2, 0, 480);
        worker().getRtcEngine().setRecordingAudioFrameParameters(48000 , 2, 0, 480);
        worker().getRtcEngine().setMixedAudioFrameParameters(48000 , 2,480);
        worker().getRtcEngine().setPlaybackAudioFrameBeforeMixingParameters(48000 , 2);
        worker().joinChannel(roomName, config().mUid);

        TextView textRoomName = (TextView) findViewById(R.id.room_name);
        textRoomName.setText(roomName);

        optional();

        LinearLayout bottomContainer = (LinearLayout) findViewById(R.id.bottom_container);
        FrameLayout.MarginLayoutParams fmp = (FrameLayout.MarginLayoutParams) bottomContainer.getLayoutParams();
        fmp.bottomMargin = virtualKeyHeight() + 16;
    }

    private Handler mMainHandler;

    private static final int UPDATE_UI_MESSAGE = 0x1024;

    EditText mMessageList;

    StringBuffer mMessageCache = new StringBuffer();

    private void notifyMessageChanged(String msg) {
        if (mMessageCache.length() > 10000) { // drop messages
            mMessageCache = new StringBuffer(mMessageCache.substring(10000 - 40));
        }

        mMessageCache.append(System.currentTimeMillis()).append(": ").append(msg).append("\n"); // append timestamp for messages

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                if (mMainHandler == null) {
                    mMainHandler = new Handler(getMainLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            super.handleMessage(msg);

                            if (isFinishing()) {
                                return;
                            }

                            switch (msg.what) {
                                case UPDATE_UI_MESSAGE:
                                    String content = (String) (msg.obj);
                                    mMessageList.setText(content);
                                    mMessageList.setSelection(content.length());
                                    break;

                                default:
                                    break;
                            }

                        }
                    };

                    mMessageList = (EditText) findViewById(R.id.msg_list);
                }

                mMainHandler.removeMessages(UPDATE_UI_MESSAGE);
                Message envelop = new Message();
                envelop.what = UPDATE_UI_MESSAGE;
                envelop.obj = mMessageCache.toString();
                mMainHandler.sendMessageDelayed(envelop, 1000l);
            }
        });
    }

    private void optional() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

//        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    private void optionalDestroy() {
    }

    public void onSwitchSpeakerClicked(View view) {
        log.info("onSwitchSpeakerClicked " + view + " " + mAudioMuted + " " + mAudioRouting);

        RtcEngine rtcEngine = rtcEngine();
        rtcEngine.setEnableSpeakerphone(mAudioRouting != 3);
    }

    private void doConfigEngine(int cRole) {
        worker().configEngine(cRole);
    }

    private boolean isBroadcaster(int cRole) {
        return cRole == Constants.CLIENT_ROLE_BROADCASTER;
    }

    private boolean isBroadcaster() {
        return isBroadcaster(config().mClientRole);
    }

    @Override
    protected void deInitUIandEvent() {
        optionalDestroy();

        doLeaveChannel();
        event().removeEventHandler(this);
    }

    private void doLeaveChannel() {
        worker().leaveChannel(config().mChannel);
    }

    public void onEndCallClicked(View view) {
        log.info("onEndCallClicked " + view);

        quitCall();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        log.info("onBackPressed");
        worker().getRtcEngine().registerAudioFrameObserver(null);
        quitCall();
    }

    private void quitCall() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
//        android.util.Log.v("longxin","destroy");
//        RtcEngine.destroy();
        finish();
    }

    private void doSwitchToBroadcaster(boolean broadcaster) {
        final int uid = config().mUid;
        log.debug("doSwitchToBroadcaster " + (uid & 0XFFFFFFFFL) + " " + broadcaster);

        if (broadcaster) {
            doConfigEngine(Constants.CLIENT_ROLE_BROADCASTER);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    ImageView button1 = (ImageView) findViewById(R.id.switch_broadcasting_id);
                    ImageView button2 = (ImageView) findViewById(R.id.mute_local_speaker_id);
                    broadcasterUI(button1, button2);
                }
            }, 1000); // wait for reconfig engine
        } else {
            stopInteraction(uid);
        }
    }

    private void stopInteraction(final int uid) {
        doConfigEngine(Constants.CLIENT_ROLE_AUDIENCE);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                ImageView button1 = (ImageView) findViewById(R.id.switch_broadcasting_id);
                ImageView button2 = (ImageView) findViewById(R.id.mute_local_speaker_id);
                audienceUI(button1, button2);
            }
        }, 1000); // wait for reconfig engine
    }

    private void audienceUI(ImageView button1, ImageView button2) {
        button1.setTag(null);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object tag = v.getTag();
                if (tag != null && (boolean) tag) {
                    doSwitchToBroadcaster(false);
                } else {
                    doSwitchToBroadcaster(true);
                }
            }
        });
        button1.clearColorFilter();
        button2.setTag(null);
        button2.setVisibility(View.GONE);
        button2.clearColorFilter();
    }

    private void broadcasterUI(ImageView button1, ImageView button2) {
        button1.setTag(true);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object tag = v.getTag();
                if (tag != null && (boolean) tag) {
                    doSwitchToBroadcaster(false);
                } else {
                    doSwitchToBroadcaster(true);
                }
            }
        });
        button1.setColorFilter(getResources().getColor(R.color.agora_blue), PorterDuff.Mode.MULTIPLY);

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object tag = v.getTag();
                boolean flag = true;
                if (tag != null && (boolean) tag) {
                    flag = false;
                }
//                worker().getRtcEngine().muteLocalAudioStream(flag);
                android.util.Log.v("longxin","muteRecordingSignal: "+ flag);
                worker().getRtcEngine().muteRecordingSignal(flag);
                ImageView button = (ImageView) v;
                button.setTag(flag);
                if (flag) {
                    button.setColorFilter(getResources().getColor(R.color.agora_blue), PorterDuff.Mode.MULTIPLY);
                } else {
                    button.clearColorFilter();
                }
            }
        });

        button2.setVisibility(View.VISIBLE);
    }

    public void onVoiceMuteClicked(View view) {
//        log.info("onVoiceMuteClicked " + view + " audio_status: " + mAudioMuted);

        RtcEngine rtcEngine = rtcEngine();
//        rtcEngine.muteLocalAudioStream(mAudioMuted = !mAudioMuted);
        android.util.Log.v("longxin","muteRecordingSignal");
        rtcEngine.muteRecordingSignal(mAudioMuted = !mAudioMuted);
        ImageView iv = (ImageView) view;

        if (mAudioMuted) {
            iv.setColorFilter(getResources().getColor(R.color.agora_blue), PorterDuff.Mode.MULTIPLY);
        } else {
            iv.clearColorFilter();
        }
    }

    public void  onVoiceMagneticClicked(View view) {
        android.util.Log.v("longxin","onVoiceMagneticClicked mMaleMagnetic: " + Constants.GENERAL_BEAUTY_VOICE_MALE_MAGNETIC);

        Button iv = (Button) view;
        mMaleMagnetic = !mMaleMagnetic;
        if (mMaleMagnetic) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            worker().getRtcEngine().setLocalVoiceChanger(Constants.GENERAL_BEAUTY_VOICE_MALE_MAGNETIC);
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            worker().getRtcEngine().setLocalVoiceChanger(Constants.VOICE_CHANGER_OFF);
        }
    }


    public void  onAudioVocalClicked (View view) {
        android.util.Log.v("longxin","onVoiceMagneticClicked AUDIO_REVERB_FX_VOCAL_CONCERT: " + Constants.AUDIO_REVERB_FX_VOCAL_CONCERT);

        Button iv = (Button) view;
        mVocal = !mVocal;
        if (mVocal) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            worker().getRtcEngine().setLocalVoiceReverbPreset(Constants.AUDIO_REVERB_FX_VOCAL_CONCERT);
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            worker().getRtcEngine().setLocalVoiceReverbPreset(Constants.AUDIO_REVERB_OFF);
        }
    }

    public void  onAudioMixingClicked (View view) {
        android.util.Log.v("longxin","onAudioMixingClicked");

        Button iv = (Button) view;
        mAudioMixing = !mAudioMixing;
        if (mAudioMixing) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            worker().getRtcEngine().startAudioMixing("/sdcard/audio_leftbigrightsmall.wav",false,false,-1);
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            worker().getRtcEngine().stopAudioMixing();
        }
    }

    public void  onPlayEffectClicked (View view) {
        android.util.Log.v("longxin","onPlayEffectClicked");

        Button iv = (Button) view;
        mPlayEffect = !mPlayEffect;
        if (mPlayEffect) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            final IAudioEffectManager aeMgr = rtcEngine().getAudioEffectManager();
            aeMgr.playEffect(1, "/sdcard/testeffect.mp3", 1, 1, 1, 80, true);
//            worker().getRtcEngine().startPlayingStream();
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            final IAudioEffectManager aeMgr = rtcEngine().getAudioEffectManager();
            aeMgr.stopAllEffects();
//            worker().getRtcEngine().stopPlayingStream(Constants.AUDIO_REVERB_OFF);
        }
    }

    public void  onAudioKTVClicked (View view) {
        android.util.Log.v("longxin","onAudioKTVClicked AUDIO_REVERB_FX_VOCAL_CONCERT: " + Constants.AUDIO_REVERB_FX_KTV);

        Button iv = (Button) view;
        mKTV = !mKTV;
        if (mKTV) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            worker().getRtcEngine().setLocalVoiceReverbPreset(Constants.AUDIO_REVERB_FX_KTV);
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            worker().getRtcEngine().setLocalVoiceReverbPreset(Constants.AUDIO_REVERB_OFF);
        }
    }

    @Override
    public void onJoinChannelSuccess(String channel, final int uid, int elapsed) {
        String msg = "onJoinChannelSuccess " + channel + " " + (uid & 0xFFFFFFFFL) + " " + elapsed;
        log.debug(msg);

        notifyMessageChanged(msg);

//        worker().getRtcEngine().startAudioMixing("/sdcard/audio_leftbigrightsmall.wav",false,false,-1);
//        worker().getRtcEngine().adjustAudioMixingPublishVolume(10);
//        worker().getRtcEngine().adjustRecordingSignalVolume(200);
          worker().getRtcEngine().enableInEarMonitoring(true);
//        worker().getRtcEngine().setLocalVoiceChanger(Constants.GENERAL_BEAUTY_VOICE_MALE_MAGNETIC);//语聊美声:磁性(男)。此枚举为男声定制化 效果，不不适⽤用于⼥女女声。若⼥女女声使⽤用此⾳音效设 置，则⾳音频可能会产⽣生失真。
//        worker().getRtcEngine().setLocalVoiceChanger(Constants.GENERAL_BEAUTY_VOICE_FEMALE_FRESH);//语聊美声:清新(⼥女女)。此枚举为⼥女女声定制化 效果，不不适⽤用于男声。若男声使⽤用此⾳音效设 置，则⾳音频可能会产⽣生失真。
//        worker().getRtcEngine().setLocalVoiceChanger(Constants.GENERAL_BEAUTY_VOICE_FEMALE_VITALITY);//语聊美声:活⼒力力(⼥女女)。此枚举为⼥女女声定制化 效果，不不适⽤用于男声。若男声使⽤用此⾳音效设 置，则⾳音频可能会产⽣生失真。
//        worker().getRtcEngine().setLocalVoiceReverbPreset(Constants.AUDIO_REVERB_FX_KTV);

    }

    @Override
    public void onUserOffline(int uid, int reason) {
        String msg = "onUserOffline " + (uid & 0xFFFFFFFFL) + " " + reason;
        log.debug(msg);

        notifyMessageChanged(msg);

    }

    @Override
    public void onExtraCallback(final int type, final Object... data) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                doHandleExtraCallback(type, data);
            }
        });
    }

    private void doHandleExtraCallback(int type, Object... data) {
        int peerUid;
        boolean muted;

        switch (type) {
            case AGEventHandler.EVENT_TYPE_ON_USER_AUDIO_MUTED: {
                peerUid = (Integer) data[0];
                muted = (boolean) data[1];

                notifyMessageChanged("mute: " + (peerUid & 0xFFFFFFFFL) + " " + muted);
                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_AUDIO_QUALITY: {
                peerUid = (Integer) data[0];
                int quality = (int) data[1];
                short delay = (short) data[2];
                short lost = (short) data[3];

                notifyMessageChanged("quality: " + (peerUid & 0xFFFFFFFFL) + " " + quality + " " + delay + " " + lost);
                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_SPEAKER_STATS: {
                IRtcEngineEventHandler.AudioVolumeInfo[] infos = (IRtcEngineEventHandler.AudioVolumeInfo[]) data[0];

                if (infos.length == 1 && infos[0].uid == 0) { // local guy, ignore it
//                    android.util.Log.v("longxin","self:"+ infos[0].uid + " volume:"+infos[0].volume);
                    break;
                }

                StringBuilder volumeCache = new StringBuilder();
                for (IRtcEngineEventHandler.AudioVolumeInfo each : infos) {
                    peerUid = each.uid;
                    int peerVolume = each.volume;

                    if (peerUid == 0) {
                        continue;
                    }

                    volumeCache.append("volume: ").append(peerUid & 0xFFFFFFFFL).append(" ").append(peerVolume).append("\n");
//                    android.util.Log.v("longxin","uid:"+ each.uid + " volume:"+each.volume);
                }

                if (volumeCache.length() > 0) {
                    String volumeMsg = volumeCache.substring(0, volumeCache.length() - 1);
                    notifyMessageChanged(volumeMsg);

                    if ((System.currentTimeMillis() / 1000) % 10 == 0) {
                        log.debug(volumeMsg);
                    }
                }
                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_APP_ERROR: {
                int subType = (int) data[0];

                if (subType == ConstantApp.AppError.NO_NETWORK_CONNECTION) {
                    showLongToast(getString(R.string.msg_no_network_connection));
                }

                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_AGORA_MEDIA_ERROR: {
                int error = (int) data[0];
                String description = (String) data[1];

                notifyMessageChanged(error + " " + description);

                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED: {
                notifyHeadsetPlugged((int) data[0]);

                break;
            }
        }
    }

    public void notifyHeadsetPlugged(final int routing) {
        log.info("notifyHeadsetPlugged " + routing);

        mAudioRouting = routing;

        ImageView iv = (ImageView) findViewById(R.id.switch_speaker_id);
        if (mAudioRouting == 3) { // Speakerphone
            iv.setColorFilter(getResources().getColor(R.color.agora_blue), PorterDuff.Mode.MULTIPLY);
        } else {
            iv.clearColorFilter();
        }
    }

    @Override
    public boolean onRecordAudioFrame(int i, int i1, int i2, int i3, int i4, ByteBuffer byteBuffer, long l, int i5) {
//        android.util.Log.v("longxin","onRecordAudioFrame");
        return false;
    }

    @Override
    public boolean onPlaybackAudioFrame(int i, int i1, int i2, int i3, int i4, ByteBuffer byteBuffer, long l, int i5) {
//        android.util.Log.v("longxin","onPlaybackAudioFrame");
        return false;
    }

    @Override
    public boolean onMixedAudioFrame(int i, int i1, int i2, int i3, int i4, ByteBuffer byteBuffer, long l, int i5) {
        android.util.Log.v("longxin","onMixedAudioFrame");
        return false;
    }

    @Override
    public boolean onPlaybackAudioFrameBeforeMixing(int i, int i1, int i2, int i3, int i4, int i5, ByteBuffer byteBuffer, long l, int i6) {
//        android.util.Log.v("longxin","onPlaybackAudioFrameBeforeMixing");
        return false;
    }


}
