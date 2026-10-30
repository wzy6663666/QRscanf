'use strict';

var analyze = require('../../utils/analyze.js');
var symbol = require('../../utils/symbol.js');
var render = require('../../utils/render.js');
var meta = require('../../utils/meta.js');
var util = require('../../utils/util.js');

var MAX_ANALYZE_SIZE = 900;

Page({
  data: {
    stage: 'idle',
    imagePath: '',
    analysis: null,
    rows: [],
    content: '',
    format: 'QR_CODE',
    formatLabel: '',
    formatColor: '#5B6CF9',
    formatBg: '#EEF0FF',
    failMessage: '',
    noPreview: true
  },

  onReady: function () {
    var that = this;
    render.getCanvas('#work').then(function (item) {
      that.workCanvas = item.node;
    }).catch(function () {});
    render.getCanvas('#gen').then(function (item) {
      that.genCanvas = item.node;
      that.genCanvas.width = 600;
      that.genCanvas.height = 600;
      that.renderPreview();
    }).catch(function () {});
  },

  chooseFromAlbum: function () {
    this.pick('album');
  },

  takePhoto: function () {
    this.pick('camera');
  },

  pick: function (source) {
    var that = this;
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: [source],
      sizeType: ['original', 'compressed'],
      success: function (res) {
        var file = res.tempFiles && res.tempFiles[0];
        if (file) that.analyzeImage(file.tempFilePath);
      }
    });
  },

  analyzeImage: function (path) {
    var that = this;
    this.setData({ stage: 'analyzing', imagePath: path, noPreview: true });
    if (!this.workCanvas) {
      this.setData({ stage: 'fail', failMessage: '画布未初始化，请重试' });
      return;
    }
    wx.getImageInfo({
      src: path,
      success: function (info) {
        var scale = Math.min(1, MAX_ANALYZE_SIZE / Math.max(info.width, info.height));
        var w = Math.max(1, Math.round(info.width * scale));
        var h = Math.max(1, Math.round(info.height * scale));
        var canvas = that.workCanvas;
        canvas.width = w;
        canvas.height = h;
        var ctx = canvas.getContext('2d');
        var img = canvas.createImage();
        img.onload = function () {
          try {
            ctx.clearRect(0, 0, w, h);
            ctx.drawImage(img, 0, 0, w, h);
            var imageData = ctx.getImageData(0, 0, w, h);
            var result = analyze.analyze(imageData);
            that.applyAnalysis(result);
          } catch (e) {
            that.setData({ stage: 'fail', failMessage: '图片解析失败：' + (e && e.message ? e.message : '未知错误') });
          }
        };
        img.onerror = function () {
          that.setData({ stage: 'fail', failMessage: '图片读取失败' });
        };
        img.src = path;
      },
      fail: function () {
        that.setData({ stage: 'fail', failMessage: '图片读取失败' });
      }
    });
  },

  applyAnalysis: function (result) {
    if (!result || !result.ok) {
      this.setData({
        stage: 'fail',
        failMessage: (result && result.note) || '没有识别到可复刻的码'
      });
      return;
    }
    var info = meta.get(result.format);
    var rows = [];
    if (result.format === 'QR_CODE') {
      rows.push({ k: '版本', v: '版本 ' + result.version + '（' + result.dimension + '×' + result.dimension + ' 模块）' });
      rows.push({ k: '掩码图案', v: result.mask === null || result.mask === undefined ? '未解析' : '掩码 ' + result.mask });
      rows.push({ k: '纠错级别', v: result.ecl || '未解析' });
    } else {
      rows.push({ k: '符号尺寸', v: result.symbolSize + '×' + result.symbolSize });
    }
    if (result.moduleSize) rows.push({ k: '模块像素', v: result.moduleSize.toFixed(2) + ' px' });
    if (result.quiet !== undefined && result.quiet !== null) {
      rows.push({ k: '静区', v: '约 ' + result.quiet.toFixed(2) + ' 倍码宽' });
    }
    if (result.box) rows.push({ k: '码区尺寸', v: result.box.width + ' × ' + result.box.height + ' px' });

    this.setData({
      stage: 'done',
      analysis: result,
      rows: rows,
      format: result.format,
      formatLabel: info.label,
      formatColor: info.color,
      formatBg: 'rgba(91,108,249,0.08)'
    });
    this.renderPreview();
  },

  onContentInput: function (e) {
    this.setData({ content: e.detail.value });
    this.renderPreview();
  },

  scanContent: function () {
    var that = this;
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['barCode', 'qrCode', 'datamatrix', 'pdf417'],
      success: function (res) {
        that.setData({ content: util.cleanValue(res.result || '') });
        that.renderPreview();
      },
      fail: function () {}
    });
  },

  copyContent: function () {
    util.copy(this.data.content);
  },

  renderPreview: function () {
    if (!this.genCanvas) return;
    var content = this.data.content;
    var analysis = this.data.analysis;
    if (!content || !analysis) {
      var ctx0 = this.genCanvas.getContext('2d');
      ctx0.clearRect(0, 0, this.genCanvas.width, this.genCanvas.height);
      this.setData({ noPreview: true });
      return;
    }
    var opts = this.buildOpts(analysis);
    var built = null;
    try {
      built = symbol.build(this.data.format, content, opts);
    } catch (e) {
      try {
        built = symbol.build(this.data.format, content, { encoding: 'utf8' });
      } catch (e2) {
        built = null;
      }
    }
    if (!built) {
      this.setData({ noPreview: true });
      return;
    }
    render.draw(this.genCanvas, built, {
      size: this.genCanvas.width,
      quiet: opts.quiet,
      fg: analysis.fg || '#000000',
      bg: analysis.bg || '#FFFFFF'
    });
    this.setData({ noPreview: false });
  },

  buildOpts: function (analysis) {
    var quiet = 4;
    if (analysis.quiet) {
      quiet = Math.max(0, Math.min(8, Math.round(analysis.quiet * 10) / 10));
      if (quiet < 1) quiet = 1;
    }
    return {
      ecLevel: analysis.ecl || 'M',
      version: analysis.version || 0,
      mask: analysis.mask === null || analysis.mask === undefined ? -1 : analysis.mask,
      encoding: 'utf8',
      quiet: Math.round(quiet)
    };
  },

  previewImage: function () {
    if (!this.data.imagePath) return;
    wx.previewImage({ urls: [this.data.imagePath] });
  },

  applyToGenerate: function () {
    var analysis = this.data.analysis;
    if (!analysis) return;
    var opts = this.buildOpts(analysis);
    if (analysis.fg) opts.fg = analysis.fg;
    if (analysis.bg) opts.bg = analysis.bg;
    opts.size = 720;
    getApp().globalData.generateDraft = {
      content: this.data.content,
      format: this.data.format,
      opts: opts,
      from: 'clone'
    };
    wx.switchTab({ url: '/pages/generate/generate' });
  }
});
