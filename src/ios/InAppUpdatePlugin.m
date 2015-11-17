#import "InAppUpdatePlugin.h"
#import <AVFoundation/AVFoundation.h>
#import "Reachability.h"
#import "ZipArchive.h"
#import <Cordova/CDV.h>
#import "NSData+MD5.h"

@implementation InAppUpdatePlugin

- (void)download:(CDVInvokedUrlCommand*)command {
    [self.commandDelegate runInBackground:^ {
        CDVPluginResult* pluginResult = nil;
        
        int isReachableViaWiFi = [self isReachableViaWiFi];
        
        if (isReachableViaWiFi) {
            NSString *fileURL = [command.arguments objectAtIndex:0];
            NSString *fileChecksum = [command.arguments objectAtIndex:1];
            
            NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
            NSString  *documentsDirectory = [paths objectAtIndex:0];
            NSString  *filePath = [NSString stringWithFormat:@"%@/%s", documentsDirectory, "www.zip"];
            
            BOOL exists = [[NSFileManager defaultManager] fileExistsAtPath:filePath];
            if (exists) {
                [[NSFileManager defaultManager] removeItemAtPath:filePath error:nil];
            }
            
            NSData *data = [NSData dataWithContentsOfURL:[NSURL URLWithString:fileURL]];
            
            if (data) {
                //saving is done on main thread
                dispatch_async(dispatch_get_main_queue(), ^{
                    [data writeToFile:filePath atomically:YES];
                    CDVPluginResult* pluginResult = nil;
                    NSString* downloadedFileChecksum = [[NSData dataWithContentsOfFile:filePath] MD5];
                    
                    if ([[fileChecksum uppercaseString] isEqualToString:[downloadedFileChecksum uppercaseString]]) {
                        NSString *result = @"{\"downloaded\": true}";
                        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
                        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                    } else {
                        NSString *result = @"{\"downloaded\": false, \"message\":\"Not a valid file.\"}";
                        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
                        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                    }
                });
            } else {
                NSString *result = @"{\"downloaded\": false}";
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
        } else {
            NSString *result = @"{\"downloaded\": false, \"message\": \"No Wifi connection available.\"}";
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    }];
}

- (void)install:(CDVInvokedUrlCommand*)command {
    [self.commandDelegate runInBackground:^ {
        CDVPluginResult* pluginResult = nil;
        
        NSArray *documentsDirectoryPath = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
        NSString *documentsDirectory = [documentsDirectoryPath objectAtIndex:0];
        NSString *wwwPath = [NSString stringWithFormat:@"%@/%s", documentsDirectory, "www"];
        NSString *filePath = [NSString stringWithFormat:@"%@/%s", documentsDirectory, "www.zip"];
        NSString *result = nil;
        
        BOOL exists = [[NSFileManager defaultManager] fileExistsAtPath:wwwPath];
        if (exists) {
            [[NSFileManager defaultManager] removeItemAtPath:wwwPath error:nil];
        }
        
        exists = [[NSFileManager defaultManager] fileExistsAtPath:filePath];
        if (exists) {
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
    }];
}

-(void)applyUpdate:(CDVInvokedUrlCommand*)command {
    if (self.webView) {
        NSArray *path = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
        NSString *documentsDirectory = [path objectAtIndex:0];
        NSString *filepath = [NSString stringWithFormat:@"%@/%s", documentsDirectory, "www"];
        NSURL *appURL = [NSURL URLWithString:[NSString stringWithFormat:@"%@/%@", filepath, @"index.html"]];
        
        [self.webView loadRequest:[NSURLRequest requestWithURL:appURL]];
    }
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

@end