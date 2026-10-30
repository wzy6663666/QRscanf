'use strict';

var qr = require('./qr.js');
var dm = require('./dm.js');
var oned = require('./oned.js');

var LINEAR = {
  CODE_128: 'code128',
  CODE_39: 'code39',
  CODE_93: 'code93',
  EAN_13: 'ean13',
  EAN_8: 'ean8',
  ITF: 'itf'
};

var UNSUPPORTED = {
  PDF417: 'PDF417 小程序端暂不支持生成，可正常识别',
  AZTEC: 'Aztec 小程序端暂不支持生成，可正常识别',
  CODABAR: 'Codabar 小程序端暂不支持生成，可正常识别',
  UPC_A: 'UPC-A 小程序端暂不支持生成，可正常识别',
  UPC_E: 'UPC-E 小程序端暂不支持生成，可正常识别'
};

/**
 * 构建条码符号。
 * @param {string} format 码制 key
 * @param {string} content 内容
 * @param {object} opts QR 专用参数
 */
function build(format, content, opts) {
  opts = opts || {};
  var text = content == null ? '' : String(content);

  if (format === 'QR_CODE') {
    var q = qr.encode(text, opts);
    return {
      format: format,
      kind: 'matrix',
      size: q.size,
      modules: q.modules,
      text: text,
      params: {
        version: q.version,
        mask: q.mask,
        ecl: q.ecl,
        mode: q.mode === 'numeric' ? '数字' : (q.mode === 'alnum' ? '字母数字' : '字节')
      },
      detail: '版本 ' + q.version + ' · ' + q.ecl + ' 级 · 掩码 ' + q.mask
    };
  }

  if (format === 'DATA_MATRIX') {
    var d = dm.encode(text);
    return {
      format: format,
      kind: 'matrix',
      size: d.size,
      modules: d.modules,
      text: text,
      params: { symbol: d.symbol, encodation: d.encodation },
      detail: d.symbol + ' · ' + d.encodation
    };
  }

  var fn = LINEAR[format];
  if (fn) {
    var r = oned[fn](text);
    return {
      format: format,
      kind: 'linear',
      width: r.modules.length,
      row: r.modules,
      text: r.text,
      params: {},
      detail: r.detail
    };
  }

  throw new Error(UNSUPPORTED[format] || '该码制暂不支持生成');
}

module.exports = {
  build: build,
  UNSUPPORTED: UNSUPPORTED
};
