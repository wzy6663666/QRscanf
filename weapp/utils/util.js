'use strict';

function pad2(n) {
  return n < 10 ? '0' + n : '' + n;
}

function formatTime(ts) {
  var d = ts ? new Date(ts) : new Date();
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) +
    ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
}

function shortTime(ts) {
  var d = ts ? new Date(ts) : new Date();
  return pad2(d.getHours()) + ':' + pad2(d.getMinutes());
}

function cleanValue(value) {
  if (value == null) return '';
  var start = 0;
  var str = String(value);
  while (start < str.length) {
    var c = str.charCodeAt(start);
    if (c === 9 || c === 10 || c === 13) break;
    if (c >= 0x20 && c !== 0x7f) break;
    start++;
  }
  return start === 0 ? str : str.slice(start);
}

function contentType(value) {
  var v = cleanValue(value).trim();
  var lower = v.toLowerCase();
  if (lower.indexOf('http://') === 0 || lower.indexOf('https://') === 0) return '网址';
  if (lower.indexOf('wifi:') === 0) return 'WiFi';
  if (lower.indexOf('mailto:') === 0) return '邮箱';
  if (lower.indexOf('tel:') === 0) return '电话';
  if (lower.indexOf('smsto:') === 0 || lower.indexOf('sms:') === 0) return '短信';
  if (lower.indexOf('begin:vcard') === 0) return '名片';
  if (lower.indexOf('geo:') === 0) return '位置';
  if (lower.indexOf('otpauth:') === 0) return '动态口令';
  if (/^[0-9]+$/.test(v)) return '数字';
  return '文本';
}

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

function copy(text, tip) {
  return new Promise(function (resolve) {
    wx.setClipboardData({
      data: String(text == null ? '' : text),
      success: function () {
        wx.showToast({ title: tip || '已复制', icon: 'none' });
        resolve(true);
      },
      fail: function () { resolve(false); }
    });
  });
}

function toast(title) {
  wx.showToast({ title: title, icon: 'none' });
}

function truncate(text, max) {
  var v = String(text == null ? '' : text);
  return v.length > max ? v.slice(0, max) + '…' : v;
}

/** 十六进制颜色比较亮度，用于决定预览文字颜色 */
function isLightColor(hex) {
  var h = String(hex || '#ffffff').replace('#', '');
  if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
  var r = parseInt(h.substr(0, 2), 16);
  var g = parseInt(h.substr(2, 2), 16);
  var b = parseInt(h.substr(4, 2), 16);
  return (r * 299 + g * 587 + b * 114) / 1000 > 150;
}

module.exports = {
  formatTime: formatTime,
  shortTime: shortTime,
  cleanValue: cleanValue,
  contentType: contentType,
  uid: uid,
  copy: copy,
  toast: toast,
  truncate: truncate,
  isLightColor: isLightColor
};
