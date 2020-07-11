//
//  AgoraMediaDataPlugin.h
//  OpenVideoCall
//
//  Created by CavanSu on 26/02/2018.
//  Copyright © 2018 Agora. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <AgoraRtcKit/AgoraRtcEngineKit.h>
#import "AgoraAudioRawData.h"

@class AgoraMediaDataPlugin;

@protocol AgoraAudioDataPluginDelegate <NSObject>
@optional
- (void) mediaDataPlugin:(AgoraMediaDataPlugin *)mediaDataPlugin didRecordAudioRawData:(AgoraAudioRawData *)audioRawData;
- (void) mediaDataPlugin:(AgoraMediaDataPlugin *)mediaDataPlugin willPlaybackAudioRawData:(AgoraAudioRawData *)audioRawData;
- (void) mediaDataPlugin:(AgoraMediaDataPlugin *)mediaDataPlugin willPlaybackBeforeMixingAudioRawData:(AgoraAudioRawData *)audioRawData;
- (void) mediaDataPlugin:(AgoraMediaDataPlugin *)mediaDataPlugin didMixedAudioRawData:(AgoraAudioRawData *)audioRawData;
@end

@interface AgoraMediaDataPlugin : NSObject
@property (nonatomic, weak) id<AgoraAudioDataPluginDelegate> audioDelegate;
+ (instancetype)mediaDataPluginWithAgoraKit:(AgoraRtcEngineKit *)agoraKit;
@end
