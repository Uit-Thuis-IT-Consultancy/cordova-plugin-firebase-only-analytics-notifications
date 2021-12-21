var exec = require('cordova/exec');

var PLUGIN_NAME = 'FirebasePlugin';

//
// Cloud Messaging FCM
//
exports.getId = function (success, error) {
  exec(success, error, PLUGIN_NAME, "getId", []);
};

exports.getToken = function (success, error) {
  exec(success, error, PLUGIN_NAME, "getToken", []);
};

exports.hasPermission = function (success, error) {
  exec(success, error, PLUGIN_NAME, "hasPermission", []);
};

exports.grantPermission = function (success, error) {
  exec(success, error, PLUGIN_NAME, "grantPermission", []);
};

exports.setBadgeNumber = function (number, success, error) {
  exec(success, error, PLUGIN_NAME, "setBadgeNumber", [number]);
};

exports.getBadgeNumber = function (success, error) {
  exec(success, error, PLUGIN_NAME, "getBadgeNumber", []);
};

exports.subscribe = function (topic, success, error) {
  exec(success, error, PLUGIN_NAME, "subscribe", [topic]);
};

exports.unsubscribe = function (topic, success, error) {
  exec(success, error, PLUGIN_NAME, "unsubscribe", [topic]);
};

exports.unregister = function (success, error) {
  exec(success, error, PLUGIN_NAME, "unregister", []);
};

exports.onNotificationOpen = function (success, error) {
  exec(success, error, PLUGIN_NAME, "onNotificationOpen", []);
};

exports.onTokenRefresh = function (success, error) {
  exec(success, error, PLUGIN_NAME, "onTokenRefresh", []);
};

exports.clearAllNotifications = function (success, error) {
  exec(success, error, PLUGIN_NAME, "clearAllNotifications", []);
};

//
// Analytics
//
exports.logEvent = function (name, params, success, error) {
  exec(success, error, PLUGIN_NAME, "logEvent", [name, params]);
};

exports.setScreenName = function (name, success, error) {
  exec(success, error, PLUGIN_NAME, "setScreenName", [name]);
};

exports.setUserId = function (id, success, error) {
  exec(success, error, PLUGIN_NAME, "setUserId", [id]);
};

exports.setUserProperty = function (name, value, success, error) {
  exec(success, error, PLUGIN_NAME, "setUserProperty", [name, value]);
};

exports.setAnalyticsCollectionEnabled = function (enabled, success, error) {
  exec(success, error, PLUGIN_NAME, "setAnalyticsCollectionEnabled", [enabled]);
};
