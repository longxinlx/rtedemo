#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

@interface PcmToWav : NSObject

// 音频文件的属性
@property (nonatomic,copy) NSDictionary *attrs;

+ (instancetype) shareInstance;

/**
 *  转换pcm到wav
 *
 *  @param pcmPath  pcm文件路径
 *  @param isDelete 转换成功后是否删除源文件
 *
 *  @return NO 失败 YES成功
 */
- (BOOL) pcm2Wav: (NSString *)pcmPath isDeleteSourchFile:(BOOL)isDelete;

/**
 *  为pcm文件写入wav头
 */
- (NSData*) writeWavHead:(NSData *)audioData;

@end
