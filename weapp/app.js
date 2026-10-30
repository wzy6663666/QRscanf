'use strict';

var store = require('./utils/store.js');

App({
  globalData: {
    settings: null,
    generateDraft: null,
    lastResult: null,
    scanSession: null
  },

  onLaunch: function () {
    this.globalData.settings = store.getSettings();
  },

  onShow: function () {
    this.globalData.settings = store.getSettings();
  },

  /** 把复刻解析出的参数交给生成页 */
  setGenerateDraft: function (draft) {
    this.globalData.generateDraft = draft;
  },

  takeGenerateDraft: function () {
    var draft = this.globalData.generateDraft;
    this.globalData.generateDraft = null;
    return draft;
  }
});
