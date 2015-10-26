#import "InAppUpdatePlugin.h"
#import <AVFoundation/AVFoundation.h>
#import "Reachability.h"
#import "ZipArchive.h"
#import <Cordova/CDV.h>

@implementation InAppUpdatePlugin

-(int) checkForUpdates {
    NSString *checkUrl = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CheckUrl"];
    NSString *checkAttr = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CheckAttr"];
    
    NSURL *url = [NSURL URLWithString:checkUrl];
    NSData *data = [NSData dataWithContentsOfURL:url];
    NSDictionary* json = nil;

    if (data) {
        json = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:nil];
        if (json[checkAttr] != nil && (BOOL)json[checkAttr] == true) {
            return 1;
        }
    }

    return 0;
}

-(int) isReachableViaWiFi {
    Reachability *reachability = [Reachability reachabilityForInternetConnection];
    [reachability startNotifier];
    NetworkStatus status = [reachability currentReachabilityStatus];
    [reachability stopNotifier];

    if (status == ReachableViaWiFi) {
        return 1;
    }

    return 0;
}

- (void)check:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = nil;
    NSString* result = nil;

    int hasUpdates = [self checkForUpdates];
    int isReachableViaWiFi = [self isReachableViaWiFi];

    if (hasUpdates && isReachableViaWiFi) {
        result = @"{\"hasUpdates\": true}";
    } else {
        result = @"{\"hasUpdates\": true}";
    }

    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)download:(CDVInvokedUrlCommand*)command {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSString *downloadUrl = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"DownloadUrl"];
        NSString *downloadFilename = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"DownloadFilename"];
        
        NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@/%@", downloadUrl, downloadFilename]];
        NSData *data = [NSData dataWithContentsOfURL:url];

        if (data) {
            NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
            NSString  *documentsDirectory = [paths objectAtIndex:0];
            NSString  *filePath = [NSString stringWithFormat:@"%@/%@", documentsDirectory, downloadFilename];

            //saving is done on main thread
            dispatch_async(dispatch_get_main_queue(), ^{
                CDVPluginResult* pluginResult = nil;
                [data writeToFile:filePath atomically:YES];

                NSString *result = @"{\"downloaded\": true}";

                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            });
        } else {
            CDVPluginResult* pluginResult = nil;
            NSString *result = @"{\"downloaded\": true}";

            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    });
}

- (void)install:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = nil;
    NSString *downloadFilename = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"DownloadFilename"];
    NSArray *documentsDirectoryPath = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *documentsDirectory = [documentsDirectoryPath objectAtIndex:0];
    NSString *wwwPath = [NSString stringWithFormat:@"%@/%s", documentsDirectory, "www"];
    NSString *filePath = [NSString stringWithFormat:@"%@/%@", documentsDirectory, downloadFilename];
    NSString *result = nil;

    BOOL success = [[NSFileManager defaultManager] removeItemAtPath:wwwPath error:nil];
    if (success) {
        [ZipArchive unzipFileAtPath:filePath
                toDestination:documentsDirectory];
        //[zipArchive UnzipOpenFile:filepath Password:@"xxxxxx"];

        [[NSFileManager defaultManager] removeItemAtPath:filePath error:nil];

        result = @"{\"installed\": true}";
    } else {
        result = @"{\"installed\": false}";
    }

    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(void)applyUpdate:(CDVInvokedUrlCommand*)command {
    if (self.webView) {
        [self.webView reload];
    }
}

@end