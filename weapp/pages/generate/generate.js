'use strict';

var meta = require('../../utils/meta.js');
var symbol = require('../../utils/symbol.js');
var render = require('../../utils/render.js');
var store = require('../../utils/store.js');
var util = require('../../utils/util.js');

var MASK_RANGE = ['自动'];
for (var m = 0; m < 8; m++) MASK_RANGE.push('掩码 ' + m);

var VERSION_RANGE = ['自动'];
for (var v = 1; v <= 40; v++) VERSION_RANGE.push('版本 ' + v);

var FG_SWATCHES = ['#000000', '#1F2430', '#4453E8', '#0FB981', '#E5484D', '#7B61FF', '#F57C3D'];
var BG_SWATCHES = ['#FFFFFF', '#F7F8FC', '#EEF0FF', '#FFF6EC', '#0B0D13'];

Page({
  data: {
    formats: [],
    format: 'QR_CODE',
    content: '',
    hint: '',
    placeholder: '',
    ecLevels: ['L', 'M', 'Q', 'H'],
    versionRange: VERSION_RANGE,
    versionIndex: 0,
    maskRange: MASK_RANGE,
    maskIndex: 0,
    fgSwatches: FG_SWATCHES,
    bgSwatches: BG_SWATCHES,
    opts: {
      ecLevel: 'M',
      version: 0,
      mask: -1,
      encoding: 'utf8',
      size: 720,
      quiet: 4,
      fg: '#000000',
      bg: '#FFFFFF'
    },
    showOptions: false,
    error: '',
    detail: '',
    inverted: false
  },

  onLoad: function () {
    var list = meta.generatorFormats().map(function (item) {
      return { key: item.key, label: item.label, canGenerate: item.canGenerate };
    });
    this.setData({ formats: list });
    this.applyFormat('QR_CODE');
  },

  onReady: function () {
    var that = this;
    render.getCanvas('#preview').then(function (item) {
      that.canvas = item.node;
      that.assertCanvasSize();
      that.scheduleRender(0);
    }).catch(function () {
      that.setData({ error: '画布初始化失败' });
    });
  },

  onShow: function () {
    var app = getApp();
    var draft = app.takeGenerateDraft ? app.takeGenerateDraft() : null;
    if (!draft) return;
    var that = this;
    var next = { content: draft.content || '' };
    if (draft.format && meta.get(draft.format).canGenerate) {
      next.format = draft.format;
    }
    that.setData(next);
    if (next.format) that.applyFormat(next.format);
    if (draft.opts) {
      var opts = Object.assign({}, this.data.opts, draft.opts);
      that.setData({
        opts: opts,
        versionIndex: opts.version ? opts.version : 0,
        maskIndex: opts.mask >= 0 ? opts.mask + 1 : 0
      });
      that.assertCanvasSize();
    }
    that.scheduleRender(0);
  },

  applyFormat: function (key) {
    var item = meta.get(key);
    this.setData({
      format: key,
      hint: item.hint,
      placeholder: item.canGenerate ? item.hint : item.hint
    });
    if (!item.canGenerate) {
      this.setData({ error: item.hint, detail: '' });
      if (this.canvas) {
        var ctx = this.canvas.getContext('2d');
        ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
      }
    }
  },

  selectFormat: function (e) {
    var key = e.currentTarget.dataset.key;
    this.setData({ format: key });
    this.applyFormat(key);
    this.scheduleRender(120);
  },

  onContentInput: function (e) {
    this.setData({ content: e.detail.value });
    this.scheduleRender(300);
  },

  pasteContent: function () {
    var that = this;
    wx.getClipboardData({
      success: function (res) {
        that.setData({ content: res.data || '' });
        that.scheduleRender(0);
      }
    });
  },

  clearContent: function () {
    this.setData({ content: '' });
    this.scheduleRender(0);
  },

  goClone: function () {
    wx.navigateTo({ url: '/pages/clone/clone' });
  },

  toggleOptions: function () {
    this.setData({ showOptions: !this.data.showOptions });
  },

  setEc: function (e) {
    var opts = Object.assign({}, this.data.opts, { ecLevel: e.currentTarget.dataset.value });
    this.setData({ opts: opts });
    this.scheduleRender(80);
  },

  setEncoding: function (e) {
    var opts = Object.assign({}, this.data.opts, { encoding: e.currentTarget.dataset.value });
    this.setData({ opts: opts });
    this.scheduleRender(80);
  },

  setVersion: function (e) {
    var index = Number(e.detail.value);
    var opts = Object.assign({}, this.data.opts, { version: index });
    this.setData({ versionIndex: index, opts: opts });
    this.scheduleRender(80);
  },

  setMask: function (e) {
    var index = Number(e.detail.value);
    var opts = Object.assign({}, this.data.opts, { mask: index === 0 ? -1 : index - 1 });
    this.setData({ maskIndex: index, opts: opts });
    this.scheduleRender(80);
  },

  onSizeChange: function (e) {
    var opts = Object.assign({}, this.data.opts, { size: Number(e.detail.value) });
    this.setData({ opts: opts });
    this.assertCanvasSize();
    this.scheduleRender(60);
  },

  onQuietChange: function (e) {
    var opts = Object.assign({}, this.data.opts, { quiet: Number(e.detail.value) });
    this.setData({ opts: opts });
    this.scheduleRender(60);
  },

  setColor: function (e) {
    var key = e.currentTarget.dataset.key;
    var value = e.currentTarget.dataset.value;
    var patch = {};
    patch[key] = value;
    var opts = Object.assign({}, this.data.opts, patch);
    this.setData({ opts: opts });
    this.scheduleRender(60);
  },

  onHexInput: function (e) {
    var key = e.currentTarget.dataset.key;
    var value = (e.detail.value || '').trim();
    var patch = {};
    patch[key] = value;
    var opts = Object.assign({}, this.data.opts, patch);
    this.setData({ opts: opts });
    if (/^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value)) {
      this.scheduleRender(200);
    }
  },

  assertCanvasSize: function () {
    if (!this.canvas) return;
    var size = this.data.opts.size;
    if (this.canvas.width !== size) {
      this.canvas.width = size;
      this.canvas.height = size;
    }
  },

  scheduleRender: function (delay) {
    var that = this;
    if (this.renderTimer) clearTimeout(this.renderTimer);
    this.renderTimer = setTimeout(function () { that.render(); }, delay || 0);
  },

  render: function () {
    if (!this.canvas) return;
    var format = this.data.format;
    var content = this.data.content;
    var info = meta.get(format);

    if (!info.canGenerate) {
      this.setData({ error: info.hint, detail: '' });
      return;
    }
    if (!content) {
      var ctx = this.canvas.getContext('2d');
      ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
      this.setData({ error: '', detail: '' });
      return;
    }

    try {
      this.assertCanvasSize();
      var opts = this.data.opts;
      var built = symbol.build(format, content, {
        ecLevel: opts.ecLevel,
        version: opts.version,
        mask: opts.mask,
        encoding: opts.encoding
      });
      this.built = built;
      render.draw(this.canvas, built, {
        size: this.canvas.width,
        quiet: opts.quiet,
        fg: opts.fg,
        bg: opts.bg
      });
      var warn = util.isLightColor(opts.fg) && !util.isLightColor(opts.bg);
      this.setData({ error: '', detail: built.detail, inverted: warn });
    } catch (err) {
      this.built = null;
      var cvs = this.canvas.getContext('2d');
      cvs.clearRect(0, 0, this.canvas.width, this.canvas.height);
      this.setData({ error: err && err.message ? err.message : '生成失败', detail: '' });
    }
  },

  copyContent: function () {
    util.copy(this.data.content);
  },

  saveImage: function () {
    var that = this;
    if (!this.built) {
      util.toast(this.data.error || '先生成可用的条码');
      return;
    }
    wx.showLoading({ title: '正在保存', mask: true });
    wx.canvasToTempFilePath({
      canvas: this.canvas,
      x: 0,
      y: 0,
      width: this.canvas.width,
      height: this.canvas.height,
      destWidth: this.canvas.width,
      destHeight: this.canvas.height,
      success: function (res) {
        wx.saveImageToPhotosAlbum({
          filePath: res.tempFilePath,
          success: function () {
            wx.hideLoading();
            util.toast('已保存到相册');
            that.record();
          },
          fail: function (err) {
            wx.hideLoading();
            var msg = (err && err.errMsg) || '';
            if (msg.indexOf('auth') >= 0 || msg.indexOf('deny') >= 0) {
              wx.showModal({
                title: '需要相册权限',
                content: '请在设置中允许保存图片到相册',
                confirmText: '去设置',
                success: function (r) {
                  if (r.confirm) wx.openSetting();
                }
              });
            } else {
              util.toast('保存失败');
            }
          }
        });
      },
      fail: function () {
        wx.hideLoading();
        util.toast('导出图片失败');
      }
    });
  },

  record: function () {
    if (!store.getSettings().saveGenRecord) return;
    var built = this.built;
    if (!built) return;
    store.addGenRecord({
      id: util.uid(),
      format: this.data.format,
      content: this.data.content,
      time: Date.now(),
      detail: built.detail,
      opts: {
        ecLevel: this.data.opts.ecLevel,
        version: this.data.opts.version,
        mask: this.data.opts.mask,
        encoding: this.data.opts.encoding,
        size: this.data.opts.size,
        quiet: this.data.opts.quiet,
        fg: this.data.opts.fg,
        bg: this.data.opts.bg
      }
    });
  }
});
