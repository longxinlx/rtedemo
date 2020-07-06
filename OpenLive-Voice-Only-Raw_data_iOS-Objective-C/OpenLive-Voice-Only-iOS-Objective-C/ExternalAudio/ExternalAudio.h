//
//  ExternalAudio.h
//  AgoraAudioIO
//
//  Created by CavanSu on 22/01/2018.
//  Copyright © 2018 CavanSu. All rights reserved.
//

#import <Foundation/Foundation.h>
//#import "AudioOptions.h"

@class AgoraRtcEngineKit;
//@class ExternalAudio;


@interface ExternalAudio : NSObject


+ (instancetype)sharedExternalAudio;
- (void)setupExternalAudioWithAgoraKit:(AgoraRtcEngineKit *)agoraKit sampleRate:(int)sampleRate channels:(int)channels;
- (void)startWork;
- (void)stopWork;
@end
