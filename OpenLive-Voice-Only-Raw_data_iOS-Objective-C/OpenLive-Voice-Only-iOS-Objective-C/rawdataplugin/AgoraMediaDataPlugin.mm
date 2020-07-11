//
//  AgoraMediaRawData.m
//  OpenVideoCall
//
//  Created by CavanSu on 26/02/2018.
//  Copyright © 2018 Agora. All rights reserved.
//

#import "AgoraMediaDataPlugin.h"
#import <AgoraRtcKit/AgoraRtcEngineKit.h>
#import <AgoraRtcKit/IAgoraMediaEngine.h>
#import <AgoraRtcKit/IAgoraRtcEngine.h>

typedef NS_ENUM(int, ObserverType) {
    ObserverTypeAudio
};

@interface AgoraMediaDataPlugin ()

@property (nonatomic, weak) AgoraRtcEngineKit *agoraKit;
@end

class AgoraAudioFrameObserver : public agora::media::IAudioFrameObserver
{
public:
    AgoraMediaDataPlugin *mediaDataPlugin;

    AgoraAudioRawData* getAudioRawDataWithAudioFrame(AudioFrame& audioFrame)
    {
        AgoraAudioRawData *data = [[AgoraAudioRawData alloc] init];
        data.samplesPerChannel = audioFrame.samplesPerChannel;
        data.bytesPerSample = audioFrame.bytesPerSample;
        data.channels = audioFrame.channels;
        data.samplesPerSec = audioFrame.samplesPerSec;
        data.renderTimeMs = audioFrame.renderTimeMs;
        data.buffer = audioFrame.buffer;
        data.bufferLength = audioFrame.samplesPerChannel * audioFrame.channels * audioFrame.bytesPerSample;
        return data;
    }

    
    virtual bool onRecordAudioFrame(AudioFrame& audioFrame) override
    {
        @synchronized(mediaDataPlugin) {
            @autoreleasepool {
                NSLog(@"------------onRecordAudioFrame----------------");
                if (!mediaDataPlugin) return true;
                if ([mediaDataPlugin.audioDelegate respondsToSelector:@selector(mediaDataPlugin:didRecordAudioRawData:)]) {
                    AgoraAudioRawData *data = getAudioRawDataWithAudioFrame(audioFrame);
                   [mediaDataPlugin.audioDelegate mediaDataPlugin:mediaDataPlugin didRecordAudioRawData:data];
                }
            }
            return true;
        }
    }
    
    virtual bool onPlaybackAudioFrame(AudioFrame& audioFrame) override
    {
//        @synchronized(mediaDataPlugin) {
//            @autoreleasepool {
//                if (!mediaDataPlugin) return true;
//                if ([mediaDataPlugin.audioDelegate respondsToSelector:@selector(mediaDataPlugin:willPlaybackAudioRawData:)]) {
//                    AgoraAudioRawData *data = getAudioRawDataWithAudioFrame(audioFrame);
//                    [mediaDataPlugin.audioDelegate mediaDataPlugin:mediaDataPlugin willPlaybackAudioRawData:data];
//                }
//            }
            return true;
//        }
    }
    
    virtual bool onPlaybackAudioFrameBeforeMixing(unsigned int uid, AudioFrame& audioFrame) override
    {
//        @synchronized(mediaDataPlugin) {
//            @autoreleasepool {
//                if (!mediaDataPlugin) return true;
//                if ([mediaDataPlugin.audioDelegate respondsToSelector:@selector(mediaDataPlugin:willPlaybackBeforeMixingAudioRawData:)]) {
//                    AgoraAudioRawData *data = getAudioRawDataWithAudioFrame(audioFrame);
//                    [mediaDataPlugin.audioDelegate mediaDataPlugin:mediaDataPlugin willPlaybackBeforeMixingAudioRawData:data];
//                }
//            }
            return true;
//        }
    }
    
    virtual bool onMixedAudioFrame(AudioFrame& audioFrame) override
    {
//        @synchronized(mediaDataPlugin) {
//            @autoreleasepool {
//                if (!mediaDataPlugin) return true;
//                if ([mediaDataPlugin.audioDelegate respondsToSelector:@selector(mediaDataPlugin:didMixedAudioRawData:)]) {
//                    AgoraAudioRawData *data = getAudioRawDataWithAudioFrame(audioFrame);
//                    [mediaDataPlugin.audioDelegate mediaDataPlugin:mediaDataPlugin didMixedAudioRawData:data];
//                }
//            }
            return true;
//        }
    }
};

static AgoraAudioFrameObserver s_audioFrameObserver;

@implementation AgoraMediaDataPlugin
    
+ (instancetype)mediaDataPluginWithAgoraKit:(AgoraRtcEngineKit *)agoraKit {
    AgoraMediaDataPlugin *source = [[AgoraMediaDataPlugin alloc] init];
    source.agoraKit = agoraKit;
    if (!agoraKit) {
        return nil;
    }
    return source;
}

- (void)setAudioDelegate:(id<AgoraAudioDataPluginDelegate>)audioDelegate {
    _audioDelegate = audioDelegate;
    if (audioDelegate) {
        [self startAudioRawDataCallback];
    }
    else {
        [self stopAudioRawDataCallback];
    }
}


- (void)startAudioRawDataCallback {
    [self registerAudioVideoObserver];
}

- (void)stopAudioRawDataCallback {
    [self unregisterAudioVideoObserver];
}

- (void)registerAudioVideoObserver {
    agora::rtc::IRtcEngine* rtc_engine = (agora::rtc::IRtcEngine*)self.agoraKit.getNativeHandle;
    agora::media::IMediaEngine *mediaEngine;
    rtc_engine->queryInterface(agora::rtc::AGORA_IID_MEDIA_ENGINE, reinterpret_cast<void**>(&mediaEngine));
    if (mediaEngine)
    {
        mediaEngine->registerAudioFrameObserver(&s_audioFrameObserver);
        s_audioFrameObserver.mediaDataPlugin = self;
    }
}

- (void)unregisterAudioVideoObserver {
    agora::rtc::IRtcEngine* rtc_engine = (agora::rtc::IRtcEngine*)self.agoraKit.getNativeHandle;
    agora::media::IMediaEngine *mediaEngine;
    rtc_engine->queryInterface(agora::rtc::AGORA_IID_MEDIA_ENGINE, reinterpret_cast<void**>(&mediaEngine));
    if (mediaEngine)
    {
        mediaEngine->registerAudioFrameObserver(&s_audioFrameObserver);
        s_audioFrameObserver.mediaDataPlugin = nil;
    }
}
@end
