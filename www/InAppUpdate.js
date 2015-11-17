function InAppUpdate () {}

InAppUpdate.prototype.download = function(fileURL, fileChecksum, success, error) {
    cordova.exec(function (response) {
        var data;
        try {
            data = JSON.parse(response);
        } catch (e) {}
        if (data) {
            success(data);
        } else {
            success(response);
        }
    }, error, 'InAppUpdate', 'download', [fileURL, fileChecksum]);
};

InAppUpdate.prototype.install = function(success, error) {
    cordova.exec(function (response) {
        var data;
        try {
            data = JSON.parse(response);
        } catch (e) {}
        if (data) {
            success(data);
        } else {
            success(response);
        }
    }, error, 'InAppUpdate', 'install', []);
};

InAppUpdate.prototype.applyUpdate = function(success, error) {
    cordova.exec(function (response) {
        var data;
        try {
            data = JSON.parse(response);
        } catch (e) {}
        if (data) {
            success(data);
        } else {
            success(response);
        }
    }, error, 'InAppUpdate', 'applyUpdate', []);
};

module.exports = new InAppUpdate();