//
//  RoomViewController.m
//  OpenLiveVoice
//
//  Created by CavanSu on 2017/9/18.
//  Copyright © 2017 Agora. All rights reserved.
//

#import <AgoraRtcKit/AgoraRtcEngineKit.h>
#import "RoomViewController.h"
#import "KeyCenter.h"
#import "InfoCell.h"
#import "InfoModel.h"
#import "ExternalAudio.h"

@interface RoomViewController () <UITableViewDataSource, UITableViewDelegate, AgoraRtcEngineDelegate>
@property (weak, nonatomic) IBOutlet UILabel *roomNameLabel;
@property (weak, nonatomic) IBOutlet UITableView *tableView;
@property (weak, nonatomic) IBOutlet UIButton *roleButton;
@property (weak, nonatomic) IBOutlet UIButton *speakerButton;
@property (nonatomic, strong) NSMutableArray *infoArray;
@property (nonatomic, strong) AgoraRtcEngineKit *agoraKit;
@property (nonatomic, strong) ExternalAudio *exAudio;
@end

static NSString *cellID = @"infoID";

@implementation RoomViewController
- (void)viewDidLoad {
    [super viewDidLoad];
    [self updateViews];
    [self loadAgoraKit];
}

#pragma mark- setupViews
- (void)updateViews {
    self.roomNameLabel.text = self.channelName;
    self.tableView.backgroundColor = [UIColor clearColor];
}

#pragma mark- initAgoraKit
- (void)loadAgoraKit {
    self.agoraKit = [AgoraRtcEngineKit sharedEngineWithAppId:[KeyCenter AppId] delegate:self];
    
    [self.agoraKit setChannelProfile:AgoraChannelProfileLiveBroadcasting];
    
    AgoraClientRole role;
    
    switch (self.roleType) {
        case RoleTypeBroadcaster:
            role = AgoraClientRoleBroadcaster;
            self.roleButton.selected = NO;
            [self appendInfoToTableViewWithInfo:@"Set Broadcaster"];
            break;
            
        case RoleTypeAudience:
            role = AgoraClientRoleAudience;
            self.roleButton.selected = YES;
            [self appendInfoToTableViewWithInfo:@"Set Audience"];
            break;
    }
    [self.agoraKit setClientRole:role];
//    [self.agoraKit setAudioProfile:AgoraAudioProfileMusicStandardStereo scenario:AgoraAudioScenarioGameStreaming];
    [self.agoraKit setAudioProfile:AgoraAudioProfileMusicHighQualityStereo scenario:AgoraAudioScenarioGameStreaming];
//    [self.agoraKit setEnableSpeakerphone:YES];
    [self.agoraKit setDefaultAudioRouteToSpeakerphone:YES];
//    [self.agoraKit setMixedAudioFrameParametersWithSampleRate:44100 channel:1 samplesPerCall:4410];
    [self.agoraKit setMixedAudioFrameParametersWithSampleRate:48000 channel:1 samplesPerCall:480];
    [self.agoraKit setRecordingAudioFrameParametersWithSampleRate:48000 channel:1 mode:0 samplesPerCall:480];
    self.exAudio = [ExternalAudio sharedExternalAudio];
               NSLog(@"setupExternalAudioWithAgoraKit");
               [self.exAudio setupExternalAudioWithAgoraKit:self.agoraKit sampleRate:AgoraAudioSampleRateType48000 channels:2];
    [self.agoraKit joinChannelByToken:nil channelId:self.channelName info:nil uid:0 joinSuccess:nil];
}

#pragma mark- Append info to tableView to display
- (void)appendInfoToTableViewWithInfo:(NSString *)infoStr {
    InfoModel *model = [InfoModel modelWithInfoStr:infoStr];
    [self.infoArray insertObject:model atIndex:0];
    NSIndexPath *indexPath = [NSIndexPath indexPathForRow:0 inSection:0];
    [self.tableView insertRowsAtIndexPaths:@[indexPath] withRowAnimation:UITableViewRowAnimationLeft];
}

#pragma mark- Click buttons
- (IBAction)clickMuteButton:(UIButton *)sender {
    [self.agoraKit muteLocalAudioStream:sender.selected];
}

- (IBAction)clickHungUpButton:(UIButton *)sender {
    __weak typeof(RoomViewController) *weakself = self;
    [self.agoraKit leaveChannel:^(AgoraChannelStats * _Nonnull stat) {
        [weakself dismissViewControllerAnimated:YES completion:nil];
    }];
    [self.exAudio stopWork];
}

- (IBAction)clickSpeakerButton:(UIButton *)sender {
    if ([[UIDevice currentDevice] userInterfaceIdiom] == UIUserInterfaceIdiomPad) {
        sender.selected = NO;
    }
    else {
        [self.agoraKit setEnableSpeakerphone:!sender.selected];
    }
}

- (IBAction)clickRoleButton:(UIButton *)sender {
    AgoraClientRole role = sender.selected ? AgoraClientRoleAudience : AgoraClientRoleBroadcaster;
    if (role == AgoraClientRoleBroadcaster && self.speakerButton.selected) {
        self.speakerButton.selected = NO;
    }
    [self.agoraKit setClientRole:role];
}

- (IBAction)clickPlayEffect:(UIButton *)sender {
    NSString *path = [[NSBundle mainBundle]pathForResource:@"testeffect" ofType:@"mp3"];
    [self.agoraKit playEffect:arc4random_uniform(255) filePath:path loopCount:1 pitch:1 pan:1 gain:80 publish:YES];
}

- (IBAction)clickStartAudioMixing:(UIButton *)sender {
    NSString *path = [[NSBundle mainBundle]pathForResource:@"audio_leftbigrightsmall" ofType:@"wav"];
       [self.agoraKit startAudioMixing:path loopback:NO replace:NO cycle:1];
}
- (IBAction)publishMic:(UIButton *)sender {
    //大重构新特性，发布麦克风流 + 背景音乐
    AgoraRtcChannelMediaOptions* options1 = [[AgoraRtcChannelMediaOptions alloc] init];
    options1.publishAudioTrack = YES;
    options1.channelProfile = AgoraChannelProfileLiveBroadcasting;
    options1.clientRoleType = AgoraClientRoleBroadcaster;
    options1.publishMediaPlayerAudioTrack = YES;
//    options1.publishCustomAudioTrack = YES;
    [self.agoraKit updateChannelWithMediaOptions:options1];
}
- (IBAction)unpublishMic:(UIButton *)sender {
    //大重构新特性，不发布麦克风流，但是发布背景音乐
    AgoraRtcChannelMediaOptions* options1 = [[AgoraRtcChannelMediaOptions alloc] init];
    options1.publishAudioTrack = NO;
    options1.channelProfile = AgoraChannelProfileLiveBroadcasting;
    options1.clientRoleType = AgoraClientRoleBroadcaster;
    options1.publishMediaPlayerAudioTrack = YES;
//    options1.publishCustomAudioTrack = YES;
    [self.agoraKit updateChannelWithMediaOptions:options1];
}
- (IBAction)muteRecordingSignal:(UIButton *)sender {
    [self.agoraKit muteRecordingSignal:YES];
}
- (IBAction)unmuteRecordingSignal:(UIButton *)sender {
    [self.agoraKit muteRecordingSignal:NO];
}


#pragma mark- <AgoraRtcEngineDelegate>
- (void)rtcEngine:(AgoraRtcEngineKit *)engine didJoinChannel:(NSString*)channel withUid:(NSUInteger)uid elapsed:(NSInteger) elapsed {
    [self appendInfoToTableViewWithInfo:[NSString stringWithFormat:@"Self join channel with uid:%zd", uid]];
//    [self.agoraKit setDefaultAudioRouteToSpeakerphone:YES];
}

- (void)rtcEngine:(AgoraRtcEngineKit *)engine didJoinedOfUid:(NSUInteger)uid elapsed:(NSInteger)elapsed {
    [self appendInfoToTableViewWithInfo:[NSString stringWithFormat:@"Uid:%zd joined channel with elapsed:%zd", uid, elapsed]];
}

- (void)rtcEngineConnectionDidInterrupted:(AgoraRtcEngineKit *)engine {
    [self appendInfoToTableViewWithInfo:@"ConnectionDidInterrupted"];
}

- (void)rtcEngineConnectionDidLost:(AgoraRtcEngineKit *)engine {
    [self appendInfoToTableViewWithInfo:@"ConnectionDidLost"];
}

- (void)rtcEngine:(AgoraRtcEngineKit *)engine didOccurError:(AgoraErrorCode)errorCode {
    [self appendInfoToTableViewWithInfo:[NSString stringWithFormat:@"Error Code:%zd", errorCode]];
}

- (void)rtcEngine:(AgoraRtcEngineKit *)engine didOfflineOfUid:(NSUInteger)uid reason:(AgoraUserOfflineReason)reason {
    [self appendInfoToTableViewWithInfo:[NSString stringWithFormat:@"Uid:%zd didOffline reason:%zd", uid, reason]];
}

- (void)rtcEngine:(AgoraRtcEngineKit *)engine didAudioRouteChanged:(AgoraAudioOutputRouting)routing {
    switch (routing) {
        case AgoraAudioOutputRoutingDefault:
            NSLog(@"AgoraRtc_AudioOutputRouting_Default");
            break;
        case AgoraAudioOutputRoutingHeadset:
            NSLog(@"AgoraRtc_AudioOutputRouting_Headset");
            break;
        case AgoraAudioOutputRoutingEarpiece:
            NSLog(@"AgoraRtc_AudioOutputRouting_Earpiece");
            break;
        case AgoraAudioOutputRoutingHeadsetNoMic:
            NSLog(@"AgoraRtc_AudioOutputRouting_HeadsetNoMic");
            break;
        case AgoraAudioOutputRoutingSpeakerphone:
            NSLog(@"AgoraRtc_AudioOutputRouting_Speakerphone");
            break;
        case AgoraAudioOutputRoutingLoudspeaker:
            NSLog(@"AgoraRtc_AudioOutputRouting_Loudspeaker");
            break;
        case AgoraAudioOutputRoutingHeadsetBluetooth:
            NSLog(@"AgoraRtc_AudioOutputRouting_HeadsetBluetooth");
            break;
        default:
            break;
    }
}

- (void)rtcEngineLocalAudioMixingDidFinish:(AgoraRtcEngineKit *)engine{
    
}



- (void)rtcEngine:(AgoraRtcEngineKit *)engine didClientRoleChanged:(AgoraClientRole)oldRole newRole:(AgoraClientRole)newRole {
    if (newRole == AgoraClientRoleBroadcaster) {
        [self appendInfoToTableViewWithInfo:@"Self changed to Broadcaster"];
    }
    else {
        [self appendInfoToTableViewWithInfo:@"Self changed to Audience"];
    }
}

#pragma mark- <UITableViewDataSource>
- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return self.infoArray.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    InfoCell *cell =  [tableView dequeueReusableCellWithIdentifier:cellID];
    InfoModel *model = self.infoArray[indexPath.row];
    cell.model = model;
    return cell;
}

#pragma mark- <UITableViewDelegate>
- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
    InfoModel *model = self.infoArray[indexPath.row];
    return model.height;
}

- (CGFloat)tableView:(UITableView *)tableView estimatedHeightForRowAtIndexPath:(NSIndexPath *)indexPath {
    return 20;
}

#pragma mark- others
- (NSMutableArray *)infoArray {
    if (!_infoArray) {
        _infoArray = [NSMutableArray array];
    }
    return _infoArray;
}

- (UIStatusBarStyle)preferredStatusBarStyle {
    return UIStatusBarStyleLightContent;
}
@end
