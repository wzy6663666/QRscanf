'use strict';

var store = require('../../utils/store.js');
var meta = require('../../utils/meta.js');
var util = require('../../utils/util.js');

var SCAN_TYPES = ['barCode', 'qrCode', 'datamatrix', 'pdf417'];

function toResult(res) {
  var value = util.cleanValue(res.result || '');
  var format = meta.fromScanType(res.scanType);
  var now = Date.now();
  return {
    id: util.uid(),
    key: format + '|' + value,
    value: value,
    format: format,
    formatLabel: meta.label(format),
    color: meta.color(format),
    type: util.contentType(value),
    time: now,
    timeText: util.formatTime(now),
    length: value.length,
    canClone: format === 'QR_CODE' || format === 'DATA_MATRIX'
  };
}

Page({
  data: {
    mode: 'single',
    scanning: false,
    current: null,
    results: [],
    dropped: 0,
    autoTip: '连续扫码：每识别一个码就会自动回到扫码界面',
    autoContinue: true
  },

  onShow: function () {
    this.settings = store.getSettings();
    var app = getApp();
    if (app.globalData.scanIntent) {
      var intent = app.globalData.scanIntent;
      app.globalData.scanIntent = null;
      this.setData({ mode: intent });
      if (intent === 'continuous') {
        this.startScan();
      }
    }
  },

  switchMode: function (e) {
    var mode = e.currentTarget.dataset.mode;
    if (mode === this.data.mode) return;
    this.setData({ mode: mode, current: null });
  },

  startScan: function () {
    if (this.data.scanning) return;
    var that = this;
    this.setData({ scanning: true });
    wx.scanCode({
      onlyFromCamera: false,
      scanType: SCAN_TYPES,
      success: function (res) {
        that.handleResult(res);
      },
      fail: function (err) {
        var msg = (err && err.errMsg) || '';
        if (msg.indexOf('cancel') < 0) {
          var gesture = /gesture|TAP/i.test(msg);
          if (gesture) {
            that.setData({ autoContinue: false, autoTip: '请点击「开始扫码」继续' });
          } else {
            util.toast('扫码失败，请重试');
          }
        }
      },
      complete: function () {
        that.setData({ scanning: false });
      }
    });
  },

  handleResult: function (res) {
    var item = toResult(res);
    if (!item.value) {
      util.toast('未识别到内容');
      return;
    }
    this.feedback();

    if (this.data.mode === 'single') {
      this.setData({ current: item });
      store.addScanRecord({
        id: item.id,
        value: item.value,
        format: item.format,
        type: item.type,
        time: item.time
      });
      if (this.settings && this.settings.autoCopy) {
        wx.setClipboardData({ data: item.value });
      }
      return;
    }

    var results = this.data.results.slice();
    var dropped = this.data.dropped;
    var duplicated = false;
    var exists = results.some(function (r) { return r.key === item.key; });
    if (exists && this.settings && this.settings.dedupe) {
      dropped += 1;
      duplicated = true;
    } else {
      results.push(item);
      store.addScanRecord({
        id: item.id,
        value: item.value,
        format: item.format,
        type: item.type,
        time: item.time
      });
    }

    this.setData({ results: results, dropped: dropped });

    if (duplicated) {
      util.toast('重复内容，已跳过');
    } else if (this.data.autoContinue) {
      var that = this;
      setTimeout(function () { that.startScan(); }, 500);
    }
  },

  feedback: function () {
    if (this.settings && this.settings.feedback) {
      wx.vibrateShort({ type: 'light', fail: function () {} });
    }
  },

  pickImage: function () {
    this.startScan();
  },

  copyCurrent: function () {
    util.copy(this.data.current.value);
  },

  copyAll: function () {
    var text = this.data.results.map(function (r) { return r.value; }).join('\n');
    util.copy(text, '已复制 ' + this.data.results.length + ' 条');
  },

  removeOne: function (e) {
    var index = e.currentTarget.dataset.index;
    var results = this.data.results.slice();
    results.splice(index, 1);
    this.setData({ results: results });
  },

  clearAll: function () {
    this.setData({ results: [], dropped: 0, current: null });
  },

  saveAllToRecords: function () {
    wx.switchTab({ url: '/pages/records/records' });
  },

  openDetail: function () {
    var current = this.data.current;
    if (!current) return;
    wx.navigateTo({ url: '/pages/result/result?id=' + current.id + '&from=scan' });
  },

  cloneCurrent: function () {
    var current = this.data.current;
    if (!current) return;
    getApp().globalData.generateDraft = {
      content: current.value,
      format: current.format,
      from: 'scan'
    };
    wx.switchTab({ url: '/pages/generate/generate' });
  }
});
