//InAppUpdate.h
//Created by Luis Cal 2015-10-10

#import <Foundation/Foundation.h>
#import <Cordova/CDV.h>

@interface InAppUpdatePlugin : CDVPlugin {}

- (void) check: (CDVInvokedUrlCommand*)command;
- (void) download: (CDVInvokedUrlCommand*)command;
- (void) install: (CDVInvokedUrlCommand*)command;
- (void) applyUpdate:(CDVInvokedUrlCommand*)command;

@end