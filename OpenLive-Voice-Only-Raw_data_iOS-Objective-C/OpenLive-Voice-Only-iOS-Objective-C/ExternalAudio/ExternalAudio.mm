//
//  ExternalAudio.m
//  AgoraAudioIO
//
//  Created by CavanSu on 22/01/2018.
//  Copyright © 2018 CavanSu. All rights reserved.
//

#import "ExternalAudio.h"
#import "AudioWriteToFile.h"
#import <AgoraRtcKit/AgoraRtcEngineKit.h>
#import <AgoraRtcKit/IAgoraRtcEngine.h>
#import <AgoraRtcKit/IAgoraMediaEngine.h>


@interface ExternalAudio ()
//@property (nonatomic, assign) int sampleRate;
//@property (nonatomic, assign) int channelCount;
@property (nonatomic, weak) AgoraRtcEngineKit *agoraKit;
@end

@implementation ExternalAudio

//static NSObject *threadLockCapture;
//static NSObject *threadLockPlay;

#pragma mark - C++ AgoraAudioFrameObserver
class AgoraAudioFrameObserver : public agora::media::IAudioFrameObserver
{
private:
    
    // total buffer length of per second
    enum { kBufferLengthBytes = 480 * 2 * 2 * 50 }; //
    
    // capture
    char byteBuffer[kBufferLengthBytes]; // char take up 1 byte, byterBuffer[] take up 88200 bytes
    int readIndex = 0;
    int writeIndex = 0;
    int availableBytes = 0;
    int channels = 2;
    
public:
    int sampleRate = 48000;
    
#pragma mark- <C++ Capture>

    virtual bool onRecordAudioFrame(AudioFrame& audioFrame) override
    {
            
            int readBytes = sampleRate / 100 * channels * audioFrame.bytesPerSample;
            [AudioWriteToFile writeToFileWithData:audioFrame.buffer length:readBytes];
            return true;
    }
    
#pragma mark- <C++ Render>

    virtual bool onPlaybackAudioFrame(AudioFrame& audioFrame) override
    {
            return true;
    }
    
    virtual bool onPlaybackAudioFrameBeforeMixing(unsigned int uid, AudioFrame& audioFrame) override {
        return true;
    }
    
    virtual bool onMixedAudioFrame(AudioFrame& audioFrame) override {
        NSLog(@"------onMixedAudioFrame");
        return true;
        
    }
};

static AgoraAudioFrameObserver* s_audioFrameObserver;


+ (instancetype)sharedExternalAudio {
    ExternalAudio *audio = [[ExternalAudio alloc] init];
    return audio;
}

- (void)setupExternalAudioWithAgoraKit:(AgoraRtcEngineKit *)agoraKit sampleRate:(int)sampleRate channels:(int)channels {
    
//    threadLockCapture = [[NSObject alloc] init];
//    threadLockPlay = [[NSObject alloc] init];
    
    // Agora Engine of C++
    agora::rtc::IRtcEngine* rtc_engine = (agora::rtc::IRtcEngine*)agoraKit.getNativeHandle;
    agora::media::IMediaEngine* mediaEngine;
    rtc_engine->queryInterface(agora::rtc::AGORA_IID_MEDIA_ENGINE, reinterpret_cast<void**>(&mediaEngine));
    

    NSLog(@"------1");
    if (mediaEngine) {
        s_audioFrameObserver = new AgoraAudioFrameObserver();
        s_audioFrameObserver -> sampleRate = 48000;
          NSLog(@"------2");
        mediaEngine->registerAudioFrameObserver(s_audioFrameObserver);
    }
    
    self.agoraKit = agoraKit;
}

- (void)startWork {
//    [self.audioController startWork];
}

- (void)stopWork {
//    [self.audioController stopWork];
    [self cancelRegiset];
}

- (void)cancelRegiset {
//    agora::rtc::IRtcEngine* rtc_engine = (agora::rtc::IRtcEngine*)self.agoraKit.getNativeHandle;
//    agora::util::AutoPtr<agora::media::IMediaEngine> mediaEngine;
//    mediaEngine.queryInterface(rtc_engine, agora::rtc::AGORA_IID_MEDIA_ENGINE);
    
    agora::rtc::IRtcEngine* rtc_engine = (agora::rtc::IRtcEngine*) self.agoraKit.getNativeHandle;
    agora::media::IMediaEngine* mediaEngine;
    rtc_engine->queryInterface(agora::rtc::AGORA_IID_MEDIA_ENGINE, reinterpret_cast<void**>(&mediaEngine));
    
    mediaEngine->registerAudioFrameObserver(NULL);
}

- (void)dealloc {
    NSLog(@"<ExternalAudio Log>ExAudio dealloc");
}

@end
