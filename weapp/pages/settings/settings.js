'use strict';

var store = require('../../utils/store.js');

Page({
  data: {
    settings: {}
  },

  onShow: function () {
    this.setData({ settings: store.getSettings() });
  },

  onSwitch: function (e) {
    var key = e.currentTarget.dataset.key;
    var value = e.detail.value;
    var settings = store.setSetting(key, value);
    getApp().globalData.settings = settings;
    this.setData({ settings: settings });
  },

  clearAll: function () {
    var that = this;
    wx.showModal({
      title: '清空全部数据',
      content: '扫码记录与生成记录都会删除，且无法恢复。',
      confirmColor: '#E5484D',
      success: function (res) {
        if (!res.confirm) return;
        store.clearScanRecords();
        store.clearGenRecords();
        wx.showToast({ title: '已清空', icon: 'none' });
      }
    });
  }
});
