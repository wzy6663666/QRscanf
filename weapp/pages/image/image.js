'use strict';

var store = require('../../utils/store.js');
var meta = require('../../utils/meta.js');
var util = require('../../utils/util.js');

Page({
  data: {
    results: []
  },

  onShow: function () {
    this.settings = store.getSettings();
  },

  pickImage: function () {
    var that = this;
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['barCode', 'qrCode', 'datamatrix', 'pdf417'],
      success: function (res) {
        that.add(res);
      },
      fail: function (err) {
        var msg = (err && err.errMsg) || '';
        if (msg.indexOf('cancel') < 0) util.toast('识别失败，换一张试试');
      }
    });
  },

  add: function (res) {
    var value = util.cleanValue(res.result || '');
    if (!value) {
      util.toast('未识别到内容');
      return;
    }
    var format = meta.fromScanType(res.scanType);
    var now = Date.now();
    var item = {
      id: util.uid(),
      key: format + '|' + value,
      value: value,
      format: format,
      formatLabel: meta.label(format),
      color: meta.color(format),
      type: util.contentType(value),
      timeText: util.formatTime(now)
    };
    var list = this.data.results.slice();
    var exists = list.some(function (r) { return r.key === item.key; });
    if (!exists) {
      list.push(item);
      this.setData({ results: list });
      store.addScanRecord({
        id: item.id,
        value: item.value,
        format: item.format,
        type: item.type,
        time: now
      });
    } else {
      util.toast('这张图的内容已在列表中');
    }
    if (this.settings && this.settings.autoCopy) {
      wx.setClipboardData({ data: value });
    }
    if (this.settings && this.settings.feedback) {
      wx.vibrateShort({ type: 'light', fail: function () {} });
    }
  },

  copyOne: function (e) {
    var item = this.data.results[e.currentTarget.dataset.index];
    if (item) util.copy(item.value);
  },

  copyAll: function () {
    var text = this.data.results.map(function (r) { return r.value; }).join('\n');
    util.copy(text, '已复制 ' + this.data.results.length + ' 条');
  },

  clearAll: function () {
    this.setData({ results: [] });
  }
});
