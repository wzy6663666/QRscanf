'use strict';

var store = require('../../utils/store.js');
var meta = require('../../utils/meta.js');
var util = require('../../utils/util.js');

Page({
  data: {
    tab: 'scan',
    scanList: [],
    genList: []
  },

  onShow: function () {
    this.refresh();
  },

  refresh: function () {
    var scanList = store.getScanRecords().map(function (item) {
      return {
        id: item.id,
        value: util.truncate(item.value, 80),
        raw: item.value,
        formatLabel: meta.label(item.format),
        color: meta.color(item.format),
        type: item.type,
        timeText: util.formatTime(item.time)
      };
    });
    var genList = store.getGenRecords().map(function (item) {
      return {
        id: item.id,
        content: util.truncate(item.content, 80),
        formatLabel: meta.label(item.format),
        color: meta.color(item.format),
        detail: item.detail || '',
        timeText: util.formatTime(item.time),
        raw: item
      };
    });
    this.setData({ scanList: scanList, genList: genList });
  },

  switchTab: function (e) {
    this.setData({ tab: e.currentTarget.dataset.tab });
  },

  copyScan: function (e) {
    var item = this.data.scanList[e.currentTarget.dataset.index];
    if (item) util.copy(item.raw);
  },

  openScan: function (e) {
    var item = this.data.scanList[e.currentTarget.dataset.index];
    if (!item) return;
    wx.navigateTo({ url: '/pages/result/result?id=' + item.id + '&from=records' });
  },

  removeGen: function (e) {
    var item = this.data.genList[e.currentTarget.dataset.index];
    if (!item) return;
    store.removeGenRecord(item.id);
    this.refresh();
  },

  openGen: function (e) {
    var item = this.data.genList[e.currentTarget.dataset.index];
    if (!item) return;
    var raw = item.raw;
    getApp().globalData.generateDraft = {
      content: raw.content,
      format: raw.format,
      opts: raw.opts,
      from: 'records'
    };
    wx.switchTab({ url: '/pages/generate/generate' });
  },

  clearScan: function () {
    var that = this;
    wx.showModal({
      title: '清空扫码记录',
      content: '清空后无法恢复，确定继续吗？',
      confirmColor: '#E5484D',
      success: function (res) {
        if (!res.confirm) return;
        store.clearScanRecords();
        that.refresh();
      }
    });
  },

  clearGen: function () {
    var that = this;
    wx.showModal({
      title: '清空生成记录',
      content: '清空后无法恢复，确定继续吗？',
      confirmColor: '#E5484D',
      success: function (res) {
        if (!res.confirm) return;
        store.clearGenRecords();
        that.refresh();
      }
    });
  }
});
