'use strict';

var SETTINGS_KEY = 'qrscanf_settings';
var SCAN_KEY = 'qrscanf_scan_records';
var GEN_KEY = 'qrscanf_gen_records';
var MAX_RECORDS = 200;

var DEFAULT_SETTINGS = {
  autoCopy: false,
  feedback: true,
  dedupe: true,
  saveGenRecord: true
};

function read(key, fallback) {
  try {
    var value = wx.getStorageSync(key);
    return value === '' || value === undefined || value === null ? fallback : value;
  } catch (e) {
    return fallback;
  }
}

function write(key, value) {
  try {
    wx.setStorageSync(key, value);
  } catch (e) {
    // 存储失败时静默降级，不影响主流程
  }
}

function getSettings() {
  var saved = read(SETTINGS_KEY, {}) || {};
  var result = {};
  Object.keys(DEFAULT_SETTINGS).forEach(function (k) {
    result[k] = saved[k] === undefined ? DEFAULT_SETTINGS[k] : saved[k];
  });
  return result;
}

function setSetting(key, value) {
  var settings = getSettings();
  settings[key] = value;
  write(SETTINGS_KEY, settings);
  return settings;
}

function getScanRecords() {
  return read(SCAN_KEY, []) || [];
}

function addScanRecord(item) {
  var list = getScanRecords();
  list.unshift(item);
  if (list.length > MAX_RECORDS) list = list.slice(0, MAX_RECORDS);
  write(SCAN_KEY, list);
  return list;
}

function removeScanRecord(id) {
  var list = getScanRecords().filter(function (i) { return i.id !== id; });
  write(SCAN_KEY, list);
  return list;
}

function clearScanRecords() {
  write(SCAN_KEY, []);
}

function getGenRecords() {
  return read(GEN_KEY, []) || [];
}

function addGenRecord(item) {
  var list = getGenRecords();
  list.unshift(item);
  if (list.length > MAX_RECORDS) list = list.slice(0, MAX_RECORDS);
  write(GEN_KEY, list);
  return list;
}

function removeGenRecord(id) {
  var list = getGenRecords().filter(function (i) { return i.id !== id; });
  write(GEN_KEY, list);
  return list;
}

function clearGenRecords() {
  write(GEN_KEY, []);
}

module.exports = {
  getSettings: getSettings,
  setSetting: setSetting,
  getScanRecords: getScanRecords,
  addScanRecord: addScanRecord,
  removeScanRecord: removeScanRecord,
  clearScanRecords: clearScanRecords,
  getGenRecords: getGenRecords,
  addGenRecord: addGenRecord,
  removeGenRecord: removeGenRecord,
  clearGenRecords: clearGenRecords
};
