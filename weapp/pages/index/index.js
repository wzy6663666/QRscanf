'use strict';

var store = require('../../utils/store.js');
var meta = require('../../utils/meta.js');
var util = require('../../utils/util.js');

Page({
  data: {
    recent: []
  },

  onShow: function () {
    var list = store.getScanRecords().slice(0, 3).map(function (item) {
      return {
        id: item.id,
        value: util.truncate(item.value, 40),
        formatLabel: meta.label(item.format),
        color: meta.color(item.format),
        type: item.type,
        timeText: util.formatTime(item.time)
      };
    });
    this.setData({ recent: list });
  },

  startScan: function (e) {
    var mode = e.currentTarget.dataset.mode;
    var app = getApp();
    app.globalData.scanIntent = mode;
    wx.switchTab({ url: '/pages/scan/scan' });
  },

  goImage: function () {
    wx.navigateTo({ url: '/pages/image/image' });
  },

  goClone: function () {
    wx.navigateTo({ url: '/pages/clone/clone' });
  },

  goRecords: function () {
    wx.switchTab({ url: '/pages/records/records' });
  },

  goSettings: function () {
    wx.navigateTo({ url: '/pages/settings/settings' });
  },

  openRecord: function (e) {
    var item = this.data.recent[e.currentTarget.dataset.index];
    if (!item) return;
    var page = getCurrentPages();
    var current = page[page.length - 1];
    wx.navigateTo({
      url: '/pages/result/result?id=' + item.id + '&from=list'
    });
  },

  onShareAppMessage: function () {
    return { title: 'QRscanf · 扫码与条码生成', path: '/pages/index/index' };
  }
});
