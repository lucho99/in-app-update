function InAppUpdate () {}

InAppUpdate.prototype.check = function(success, error) {
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
    }, error, 'InAppUpdate', 'check', []);
};

InAppUpdate.prototype.download = function(success, error) {
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
    }, error, 'InAppUpdate', 'download', []);
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