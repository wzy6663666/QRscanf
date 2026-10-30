'use strict';

var store = require('../../utils/store.js');
var meta = require('../../utils/meta.js');
var util = require('../../utils/util.js');

Page({
  data: {
    record: null
  },

  onLoad: function (query) {
    var id = query && query.id;
    var list = store.getScanRecords();
    var found = null;
    for (var i = 0; i < list.length; i++) {
      if (list[i].id === id) { found = list[i]; break; }
    }
    if (!found && getApp().globalData.lastResult) {
      found = getApp().globalData.lastResult;
    }
    if (!found) return;

    var value = found.value || '';
    var bytes = 0;
    try {
      for (var k = 0; k < value.length; k++) {
        var code = value.charCodeAt(k);
        bytes += code < 0x80 ? 1 : (code < 0x800 ? 2 : (code < 0x10000 ? 3 : 4));
      }
    } catch (e) {
      bytes = value.length;
    }

    var format = found.format || 'UNKNOWN';
    var info = meta.get(format);
    var lower = value.toLowerCase();
    this.setData({
      record: {
        id: found.id,
        value: value,
        formatLabel: info.full,
        color: info.color,
        type: found.type || util.contentType(value),
        timeText: util.formatTime(found.time),
        length: value.length,
        bytes: bytes,
        lines: value.split('\n').length,
        canOpen: lower.indexOf('http://') === 0 || lower.indexOf('https://') === 0,
        format: format
      }
    });
  },

  copy: function () {
    util.copy(this.data.record.value);
  },

  openLink: function () {
    var url = this.data.record.value;
    wx.setClipboardData({
      data: url,
      success: function () {
        wx.showModal({
          title: '链接已复制',
          content: '小程序内不直接打开外部网页，已复制到剪贴板，可在浏览器中粘贴打开。',
          showCancel: false
        });
      }
    });
  },

  regenerate: function () {
    var record = this.data.record;
    var info = meta.get(record.format);
    var format = info.canGenerate ? record.format : 'QR_CODE';
    getApp().globalData.generateDraft = {
      content: record.value,
      format: format,
      from: 'result'
    };
    wx.switchTab({ url: '/pages/generate/generate' });
  },

  remove: function () {
    var that = this;
    wx.showModal({
      title: '删除这条记录',
      content: '删除后无法恢复',
      confirmColor: '#E5484D',
      success: function (res) {
        if (!res.confirm) return;
        store.removeScanRecord(that.data.record.id);
        wx.navigateBack();
      }
    });
  }
});
