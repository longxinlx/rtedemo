package io.agora.openlive.voice.only.ui;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
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

import com.yiming.ym.util.YimingYmUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import io.agora.openlive.voice.only.R;
import io.agora.openlive.voice.only.model.AGEventHandler;
import io.agora.openlive.voice.only.model.ConstantApp;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IAudioEffectManager;
import io.agora.rtc2.IAudioFrameObserver;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;

public class LiveRoomActivity extends BaseActivity implements AGEventHandler ,IAudioFrameObserver{

    private final static Logger log = LoggerFactory.getLogger(LiveRoomActivity.class);

    private volatile boolean mAudioMuted = false;
    private volatile boolean mMaleMagnetic= false;
    private volatile boolean mVocal= false;
    private volatile boolean mAudioMixing = false;
    private volatile boolean mPlayEffect = false;
    private volatile boolean mKTV = false;
    private volatile boolean mOnRecord = false;
    private volatile boolean mOnMixed = false;
    private volatile boolean mOnPlayBack = false;
    private volatile boolean mOnPlayBackBeforeMixing = false;
    private volatile boolean mEar = false;
    public static String SDCARD_DIR;
    private RandomAccessFile randomAccessFileForOnRecord;
    private RandomAccessFile randomAccessFileForOnRecordWAV;
    private RandomAccessFile randomAccessFileForOnMixed;
    private RandomAccessFile randomAccessFileForOnMixedWAV;
    private RandomAccessFile randomAccessFileForPlayBack;
    private RandomAccessFile randomAccessFileForPlayBackWAV;
    private RandomAccessFile randomAccessFileForPlayBackBeforeMixing;
    private RandomAccessFile randomAccessFileForPlayBackBeforeMixingWAV;
    private boolean isRecording = false;
    private boolean isMixing = false;
    private boolean isPlayBack = false;
    private boolean isPlayBackBeforeMixing = false;
    private boolean isMuteMic = false;
    private boolean isDiableAudio = false;
    private boolean isEnableLocalAudio = false;

    private volatile int mAudioRouting = -1; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);
//        createPcmFile(RECORD_FILE);
//        createPcmFile(ONMIXED_FILE);
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
//        worker().getRtcEngine().registerAudioFrameObserver(this);
//        worker().getRtcEngine().setPlaybackAudioFrameParameters(48000 , 2, 0, 480);
//        worker().getRtcEngine().setRecordingAudioFrameParameters(48000 , 1, 0, 480);
//        worker().getRtcEngine().setRecordingAudioFrameParameters(48000 , 2, 0, 480);
//        worker().getRtcEngine().setMixedAudioFrameParameters(48000 , 2,480);
//        worker().getRtcEngine().setPlaybackAudioFrameBeforeMixingParameters(48000 , 2);
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
        stopRecordDraft(LiveRoomActivity.RECORD_FILE);
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

    public void  onRecordClicked (View view) {
        android.util.Log.v("longxin","onRecordClicked");

        Button iv = (Button) view;
        mOnRecord = !mOnRecord;
        if (mOnRecord) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            createPcmFile(RECORD_FILE);
            createPcmFile(RECORD_FILE_WAV);
            worker().getRtcEngine().setRecordingAudioFrameParameters(48000 , 2, 0, 480);
            worker().getRtcEngine().registerAudioFrameObserver(this);
            isRecording = true;
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            isRecording = false;
            stopRecordDraft(LiveRoomActivity.RECORD_FILE);
            worker().getRtcEngine().registerAudioFrameObserver(null);
        }
    }

    public void  onMixedClicked (View view) {
        android.util.Log.v("longxin","onMixedClicked");

        Button iv = (Button) view;
        mOnMixed = !mOnMixed;
        if (mOnMixed) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            createPcmFile(ONMIXED_FILE);
            worker().getRtcEngine().setMixedAudioFrameParameters(48000 , 2,480);
            worker().getRtcEngine().registerAudioFrameObserver(this);
            isMixing = true;
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            isMixing = false;
            stopOnMixedDraft(LiveRoomActivity.ONMIXED_FILE);
            worker().getRtcEngine().registerAudioFrameObserver(null);
        }
    }

    public void  onPlayBackClicked (View view) {
        android.util.Log.v("longxin","onPlayBackClicked");

        Button iv = (Button) view;
        mOnPlayBack = !mOnPlayBack;
        if (mOnPlayBack) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            createPcmFile(PLAYBACK_FILE);
            worker().getRtcEngine().setPlaybackAudioFrameParameters(48000 , 2,0,480);
            worker().getRtcEngine().registerAudioFrameObserver(this);
            isPlayBack = true;
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            isPlayBack = false;
            stopOnPlayBackDraft(LiveRoomActivity.PLAYBACK_FILE);
            worker().getRtcEngine().registerAudioFrameObserver(null);
        }
    }

    public void  onPlayBackBeforeMixingClicked (View view) {
        android.util.Log.v("longxin","onPlayBackBeforeMixingClicked");

        Button iv = (Button) view;
        mOnPlayBackBeforeMixing = !mOnPlayBackBeforeMixing;
        if (mOnPlayBackBeforeMixing) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            createPcmFile(PLAYBACK_BEFORE_MIXING_FILE);
            worker().getRtcEngine().setPlaybackAudioFrameBeforeMixingParameters(48000 ,2);
            worker().getRtcEngine().registerAudioFrameObserver(this);
            isPlayBackBeforeMixing = true;
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            isPlayBackBeforeMixing = false;
            stopOnPlayBackBeforemixingDraft(LiveRoomActivity.PLAYBACK_BEFORE_MIXING_FILE);
            worker().getRtcEngine().registerAudioFrameObserver(null);
        }
    }

    public void  onEarMonitoringClicked (View view) {
        android.util.Log.v("longxin","onEarMonitoringClicked");

        Button iv = (Button) view;
        mEar = !mEar;
        if (mEar) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            worker().getRtcEngine().enableInEarMonitoring(true);
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            worker().getRtcEngine().enableInEarMonitoring(false);
        }
    }

    public void  onMuteMicClicked (View view) {
        android.util.Log.v("longxin","onMuteMicClicked");

        Button iv = (Button) view;
        isMuteMic = !isMuteMic;
        if (isMuteMic) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            worker().getRtcEngine().adjustRecordingSignalVolume(0);
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            worker().getRtcEngine().adjustRecordingSignalVolume(100);
        }
    }

    public void  onDisableAudioClicked (View view) {
        android.util.Log.v("longxin","disableAudioClicked");

        Button iv = (Button) view;
        isDiableAudio = !isDiableAudio;
        if (isDiableAudio) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            worker().getRtcEngine().disableAudio();
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            worker().getRtcEngine().enableAudio();
        }
    }



    public void  onEnableLocalAudioClicked (View view) {
        android.util.Log.v("longxin","onEnableLocalAudioClicked");

        Button iv = (Button) view;
        isEnableLocalAudio = !isEnableLocalAudio;
        if (isEnableLocalAudio) {
            iv.setTextColor(getResources().getColor(R.color.agora_blue));
            worker().getRtcEngine().enableLocalAudio(false);
        } else {
            iv.setTextColor(getResources().getColor(R.color.dark_black));
            worker().getRtcEngine().enableLocalAudio(true);
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

//                if (infos.length == 1 && infos[0].uid == 0) { // local guy, ignore it
//                    android.util.Log.v("longxin","self:"+ infos[0].uid + " volume:"+infos[0].volume);
//                    break;
//                }

                StringBuilder volumeCache = new StringBuilder();
                for (IRtcEngineEventHandler.AudioVolumeInfo each : infos) {
                    peerUid = each.uid;
                    int peerVolume = each.volume;

//                    if (peerUid == 0) {
//                        continue;
//                    }

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
    public boolean onRecordAudioFrame(int type, int samplesPerChannel, int bytesPerSample,
                                      int channels, int samplesPerSec, ByteBuffer buffer, long renderTimeMs, int avsync_type) {
        startRecord(buffer,LiveRoomActivity.RECORD_FILE);
        return false;
    }

    @Override
    public boolean onPlaybackAudioFrame(int i, int i1, int i2, int i3, int i4, ByteBuffer byteBuffer, long l, int i5) {
        startOnPlayBack(byteBuffer,LiveRoomActivity.PLAYBACK_FILE);
        return false;
    }

    @Override
    public boolean onMixedAudioFrame(int i, int i1, int i2, int i3, int i4, ByteBuffer byteBuffer, long l, int i5) {
        startOnMixed(byteBuffer,LiveRoomActivity.ONMIXED_FILE);
        return false;
    }


    @Override
    public boolean onPlaybackAudioFrameBeforeMixing(int i, int i1, int i2, int i3, int i4, int i5, ByteBuffer byteBuffer, long l, int i6) {
        startOnPlayBackBeforemixing(byteBuffer,LiveRoomActivity.PLAYBACK_BEFORE_MIXING_FILE);
        return false;
    }

    private static String RECORD_FILE = "testRecordSave.pcm";
    private static String RECORD_FILE_WAV = "testRecordSave.wav";
    private static String PLAYBACK_FILE = "testPlayBackSave.pcm";
    private static String PLAYBACK_FILE_WAV = "testPlayBackSave.wav";
    private static String PLAYBACK_BEFORE_MIXING_FILE = "testPlayBackBeforeMixingSave.pcm";
    private static String PLAYBACK_BEFORE_MIXING_FILE_WAV = "testPlayBackBeforeMixingSave.wav";
    private static String ONMIXED_FILE = "testMixedSave.pcm";
    private static String ONMIXED_FILE_WAV = "testMixedSave.wav";

    private void createPcmFile(String name){

        SDCARD_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (SDCARD_DIR == null || SDCARD_DIR.equals("")) {
            SDCARD_DIR = "";
        }
        File tmpFile = new File(SDCARD_DIR, name);
        if(tmpFile.exists()) tmpFile.delete();
        try {
            if(LiveRoomActivity.RECORD_FILE.equals(name)) {
                randomAccessFileForOnRecord = new RandomAccessFile(tmpFile, "rw");
            } else if (LiveRoomActivity.ONMIXED_FILE.equals(name)) {
                randomAccessFileForOnMixed = new RandomAccessFile(tmpFile, "rw");
            } else if (LiveRoomActivity.PLAYBACK_FILE.equals(name)) {
                randomAccessFileForPlayBack = new RandomAccessFile(tmpFile, "rw");
            } else if (LiveRoomActivity.PLAYBACK_BEFORE_MIXING_FILE.equals(name)) {
                randomAccessFileForPlayBackBeforeMixing = new RandomAccessFile(tmpFile, "rw");
            } else if (LiveRoomActivity.RECORD_FILE_WAV.equals(name)) {
                android.util.Log.v("longxin","YimingYmUtil.copyFile SDCARD_DIR :"+SDCARD_DIR);
                YimingYmUtil.copyFile(new File(SDCARD_DIR,RECORD_FILE),SDCARD_DIR + "/" + RECORD_FILE_WAV);
                randomAccessFileForOnRecordWAV = new RandomAccessFile(SDCARD_DIR + "/" +  RECORD_FILE_WAV, "rw");
            } else if (LiveRoomActivity.ONMIXED_FILE_WAV.equals(name)) {
                android.util.Log.v("longxin","YimingYmUtil.copyFile SDCARD_DIR :"+SDCARD_DIR);
                YimingYmUtil.copyFile(new File(SDCARD_DIR,ONMIXED_FILE),SDCARD_DIR + "/" + ONMIXED_FILE_WAV);
                randomAccessFileForOnMixedWAV = new RandomAccessFile(SDCARD_DIR + "/" +  ONMIXED_FILE_WAV, "rw");
            } else if (LiveRoomActivity.PLAYBACK_FILE_WAV.equals(name)) {
                android.util.Log.v("longxin","YimingYmUtil.copyFile SDCARD_DIR :"+SDCARD_DIR);
                YimingYmUtil.copyFile(new File(SDCARD_DIR,PLAYBACK_FILE),SDCARD_DIR + "/" + PLAYBACK_FILE_WAV);
                randomAccessFileForPlayBackWAV = new RandomAccessFile(SDCARD_DIR + "/" +  PLAYBACK_FILE_WAV, "rw");
            } else if (LiveRoomActivity.PLAYBACK_BEFORE_MIXING_FILE_WAV.equals(name)) {
                android.util.Log.v("longxin","YimingYmUtil.copyFile SDCARD_DIR :"+SDCARD_DIR);
                YimingYmUtil.copyFile(new File(SDCARD_DIR,RECORD_FILE),SDCARD_DIR + "/" + PLAYBACK_BEFORE_MIXING_FILE_WAV);
                randomAccessFileForPlayBackBeforeMixingWAV = new RandomAccessFile(SDCARD_DIR + "/" +  PLAYBACK_BEFORE_MIXING_FILE_WAV, "rw");
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void startRecord(ByteBuffer byteBuffer,String name) {
        if (isRecording) {
            try {
                if(LiveRoomActivity.RECORD_FILE.equals(name)) {
                    randomAccessFileForOnRecord.write(bytebuffer2ByteArray(byteBuffer));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void stopRecordDraft(String name) {//演绎结束后调用，记录当前演绎信息
        try {
            if(LiveRoomActivity.RECORD_FILE.equals(name) && randomAccessFileForOnRecord != null) {
                randomAccessFileForOnRecord.close();
                createPcmFile(LiveRoomActivity.RECORD_FILE_WAV);
                YimingYmUtil.fwritewav(randomAccessFileForOnRecordWAV);
            }
            isRecording = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startOnMixed(ByteBuffer byteBuffer,String name) {
        if (isMixing) {
            try {
              if (LiveRoomActivity.ONMIXED_FILE.equals(name)) {
                    randomAccessFileForOnMixed.write(bytebuffer2ByteArray(byteBuffer));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void stopOnMixedDraft(String name) {//演绎结束后调用，记录当前演绎信息
        try {
           if (LiveRoomActivity.ONMIXED_FILE.equals(name) && randomAccessFileForOnMixed != null) {
                randomAccessFileForOnMixed.close();
               createPcmFile(LiveRoomActivity.ONMIXED_FILE_WAV);
               YimingYmUtil.fwritewav(randomAccessFileForOnMixedWAV);
            }
            isMixing = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startOnPlayBack(ByteBuffer byteBuffer,String name) {
        if (isPlayBack) {
            try {
                if (LiveRoomActivity.PLAYBACK_FILE.equals(name)) {
                    randomAccessFileForPlayBack.write(bytebuffer2ByteArray(byteBuffer));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void stopOnPlayBackDraft(String name) {//演绎结束后调用，记录当前演绎信息
        try {
            if (LiveRoomActivity.PLAYBACK_FILE.equals(name) && randomAccessFileForPlayBack != null) {
                randomAccessFileForPlayBack.close();
                createPcmFile(LiveRoomActivity.PLAYBACK_FILE_WAV);
                YimingYmUtil.fwritewav(randomAccessFileForPlayBackWAV);
            }
            isPlayBack = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startOnPlayBackBeforemixing(ByteBuffer byteBuffer,String name) {
        if (isPlayBackBeforeMixing) {
            try {
                if (LiveRoomActivity.PLAYBACK_BEFORE_MIXING_FILE.equals(name)) {
                    randomAccessFileForPlayBackBeforeMixing.write(bytebuffer2ByteArray(byteBuffer));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void stopOnPlayBackBeforemixingDraft(String name) {//演绎结束后调用，记录当前演绎信息
        try {
            if (LiveRoomActivity.PLAYBACK_BEFORE_MIXING_FILE.equals(name) && randomAccessFileForPlayBackBeforeMixing != null) {
                randomAccessFileForPlayBackBeforeMixing.close();
                createPcmFile(LiveRoomActivity.PLAYBACK_BEFORE_MIXING_FILE_WAV);
                YimingYmUtil.fwritewav(randomAccessFileForPlayBackBeforeMixingWAV);
            }
            isPlayBackBeforeMixing = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static byte[] bytebuffer2ByteArray(ByteBuffer buffer) {
        int len = buffer.limit() - buffer.position();
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        Log.e("AgoraManager", bytes.length + "");
        return bytes;
    }
}
