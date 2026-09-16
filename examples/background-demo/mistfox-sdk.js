(function (global) {
    'use strict';

    var pendingRequests = {};
    var requestIdCounter = 0;

    function call(api, args) {
        return new Promise(function (resolve, reject) {
            var requestId = 'req_' + (++requestIdCounter) + '_' + Date.now();
            pendingRequests[requestId] = { resolve: resolve, reject: reject };

            var payload = JSON.stringify({
                id: requestId,
                api: api,
                args: args || {}
            });

            if (global.MistFoxBridgePort && typeof global.MistFoxBridgePort.postMessage === 'function') {
                global.MistFoxBridgePort.postMessage(payload);
            } else {
                reject({
                    code: 'BRIDGE_UNAVAILABLE',
                    message: 'MistFox WebMessageListener bridge port not available in current context.'
                });
            }
        });
    }

    if (global.MistFoxBridgePort && typeof global.MistFoxBridgePort.addEventListener === 'function') {
        global.MistFoxBridgePort.addEventListener('message', function (event) {
            var data = event.data;
            var response = typeof data === 'string' ? JSON.parse(data) : data;
            var handler = pendingRequests[response.id];
            if (handler) {
                delete pendingRequests[response.id];
                if (response.success) {
                    handler.resolve(response.result);
                } else {
                    handler.reject(response.error || { code: 'UNKNOWN_ERROR', message: 'API call failed' });
                }
            }
        });
    }

    var Mist = {
        call: call,
        app: {
            info: function () { return call('app.info', {}); },
            id: function () { return call('app.id', {}); },
            version: function () { return call('app.version', {}); },
            close: function () { return call('app.close', {}); },
            getLaunchInfo: function () { return call('app.getLaunchInfo', {}); }
        },
        ui: {
            toast: function (text) { return call('ui.toast', { text: text }); },
            vibrate: function (duration) { return call('ui.vibrate', { duration: duration || 200 }); }
        },
        storage: {
            get: function (key) { return call('storage.get', { key: key }); },
            set: function (key, value) { return call('storage.set', { key: key, value: String(value) }); },
            remove: function (key) { return call('storage.remove', { key: key }); },
            clear: function () { return call('storage.clear', {}); },
            has: function (key) { return call('storage.has', { key: key }); },
            keys: function () { return call('storage.keys', {}); }
        },
        files: {
            read: function (path) { return call('files.read', { path: path }); },
            write: function (path, content) { return call('files.write', { path: path, content: content }); },
            delete: function (path) { return call('files.delete', { path: path }); },
            exists: function (path) { return call('files.exists', { path: path }); },
            info: function (path) { return call('files.info', { path: path }); }
        },
        camera: {
            isAvailable: function () { return call('camera.isAvailable', {}); },
            takePhoto: function () { return call('camera.takePhoto', {}); },
            pickPhoto: function () { return call('camera.pickPhoto', {}); },
            getCameras: function () { return call('camera.getCameras', {}); }
        },
        microphone: {
            isAvailable: function () { return call('microphone.isAvailable', {}); },
            requestPermission: function () { return call('microphone.requestPermission', {}); }
        },
        device: {
            info: function () { return call('device.info', {}); }
        },
        network: {
            fetch: function (url, options) {
                options = options || {};
                return call('network.fetch', { url: url, method: options.method || 'GET', body: options.body });
            }
        },
        clipboard: {
            read: function () { return call('clipboard.read', {}); },
            write: function (text) { return call('clipboard.write', { text: text }); }
        },
        share: {
            text: function (text, title) { return call('share.text', { text: text, title: title || 'Share' }); }
        },
        browser: {
            open: function (url) { return call('browser.open', { url: url }); }
        },
        phone: {
            openDialer: function (number) { return call('phone.openDialer', { number: number }); }
        },
        sms: {
            openComposer: function (number, body) { return call('sms.openComposer', { number: number, body: body || '' }); }
        },
        notifications: {
            send: function (title, body) { return call('notifications.send', { title: title, body: body }); }
        },
        sensors: {
            available: function () { return call('sensors.available', {}); }
        },
        location: {
            getCurrent: function () { return call('location.getCurrent', {}); }
        },
        biometric: {
            isAvailable: function () { return call('biometric.isAvailable', {}); },
            authenticate: function () { return call('biometric.authenticate', {}); }
        },
        bluetooth: {
            isAvailable: function () { return call('bluetooth.isAvailable', {}); }
        },
        nfc: {
            isAvailable: function () { return call('nfc.isAvailable', {}); }
        },
        background: {
            schedule: function (taskId, delay) { return call('background.schedule', { taskId: taskId, delay: delay || 60000 }); },
            cancel: function (taskId) { return call('background.cancel', { taskId: taskId }); }
        }
    };

    global.Mist = Mist;
    global.MistFoxSDK = Mist;

    // Deprecated compatibility alias for legacy mini-apps
    global.ZS = Mist;

})(typeof window !== 'undefined' ? window : this);
