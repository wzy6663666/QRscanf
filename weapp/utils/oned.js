'use strict';

/**
 * 一维码编码器（Code 128 / Code 39 / Code 93 / EAN-13 / EAN-8 / ITF）
 * 统一输出 { modules: boolean[] }，true 表示有墨（黑条）。
 */

var P128 =
  '2122222221222222211212231213221312221222131223121322122212132213122312121122321221321222311132221231221232212232112211322212312132122231123121313112223211223212213122123221123222112121232123212321211113231311231313211123131321131323112113132311132313111121331123311321311131231133211331213131212113312311312131132133112131313111233113213311213121133123113321113141112214114311111112241114221211241214211411221412211122141124121221141224111421121422112412112211144131112411121341111112421211421212411142121241121242114112124211124212112121412141214121211111431113411311411141131143114111134113111131411141313111414111312114122112142112322331112';

var CODE39_ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%';
var CODE39_ENCODINGS = [52, 289, 97, 352, 49, 304, 112, 37, 292, 100, 265, 73, 328, 25, 280, 88, 13, 268, 76, 28, 259, 67, 322, 19, 274, 82, 7, 262, 70, 22, 385, 193, 448, 145, 400, 208, 133, 388, 196, 168, 162, 138, 42];
var CODE39_STAR = 148;

var CODE93_ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%';
var CODE93_ENCODINGS = [276, 328, 324, 322, 296, 292, 290, 336, 274, 266, 424, 420, 418, 404, 402, 394, 360, 356, 354, 308, 282, 344, 332, 326, 300, 278, 436, 434, 428, 422, 406, 410, 364, 358, 310, 314, 302, 468, 466, 458, 366, 374, 430, 294, 474, 470, 306, 350];

var ITF_PATTERNS = [
  [1, 1, 2, 2, 1], [2, 1, 1, 1, 2], [1, 2, 1, 1, 2], [2, 2, 1, 1, 1], [1, 1, 2, 1, 2],
  [2, 1, 2, 1, 1], [1, 2, 2, 1, 1], [1, 1, 1, 2, 2], [2, 1, 1, 2, 1], [1, 2, 1, 2, 1]
];

var EAN_L = [
  [3, 2, 1, 1], [2, 2, 2, 1], [2, 1, 2, 2], [1, 4, 1, 1], [1, 1, 3, 2],
  [1, 2, 3, 1], [1, 1, 1, 4], [1, 3, 1, 2], [1, 2, 1, 3], [3, 1, 1, 2]
];

var EAN13_PARITY = [
  'OOOOOO', 'OOEOEE', 'OOEEOE', 'OOEEEO', 'OEOOEE',
  'OEEOOE', 'OEEEOO', 'OEOEOE', 'OEOEEO', 'OEEOEO'
];

function pushRun(out, dark, width) {
  for (var i = 0; i < width; i++) out.push(dark);
}

function pad(code) {
  return code < 10 ? '0' + code : '' + code;
}

function code128(content) {
  var text = String(content);
  if (!text.length) throw new Error('请输入内容');
  for (var i = 0; i < text.length; i++) {
    var c = text.charCodeAt(i);
    if (c < 32 || c > 126) {
      throw new Error('Code 128 仅支持英文、数字和 ASCII 字符，包含中文时请使用 QR Code 或 Data Matrix');
    }
  }

  var values = [];
  var units = [];
  var allDigits = /^[0-9]+$/.test(text) && text.length % 2 === 0 && text.length >= 4;

  if (allDigits) {
    values.push(105);
    units.push('CODE_C');
    for (var d = 0; d < text.length; d += 2) values.push(parseInt(text.substr(d, 2), 10));
  } else {
    values.push(104);
    units.push('CODE_B');
    for (var k = 0; k < text.length; k++) values.push(text.charCodeAt(k) - 32);
  }

  var sum = values[0];
  for (var s = 1; s < values.length; s++) sum += values[s] * s;
  values.push(sum % 103);
  values.push(106);

  var modules = [];
  for (var v = 0; v < values.length; v++) {
    var pattern = values[v] === 106 ? P128.substr(636, 7) : P128.substr(values[v] * 6, 6);
    for (var p = 0; p < pattern.length; p++) {
      pushRun(modules, p % 2 === 0, parseInt(pattern.charAt(p), 10));
    }
  }
  return { modules: modules, text: text, detail: units[0] };
}

function code39(content) {
  var text = String(content).toUpperCase();
  if (!/^[0-9A-Z \-.$/+%]+$/.test(text)) {
    throw new Error('Code 39 仅支持大写字母、数字和 - . $ / + % 空格');
  }
  var modules = [];
  function drawChar(bits) {
    for (var i = 0; i < 9; i++) {
      var wide = (bits & (1 << (8 - i))) !== 0;
      pushRun(modules, i % 2 === 0, wide ? 3 : 1);
    }
  }
  drawChar(CODE39_STAR);
  pushRun(modules, false, 1);
  for (var j = 0; j < text.length; j++) {
    var idx = CODE39_ALPHABET.indexOf(text.charAt(j));
    if (idx < 0) throw new Error('Code 39 不支持字符：' + text.charAt(j));
    drawChar(CODE39_ENCODINGS[idx]);
    pushRun(modules, false, 1);
  }
  drawChar(CODE39_STAR);
  return { modules: modules, text: text, detail: '含 * 起止符' };
}

function code93(content) {
  var text = String(content).toUpperCase();
  if (!/^[0-9A-Z \-.$/+%]+$/.test(text)) {
    throw new Error('Code 93 仅支持大写字母、数字和 - . $ / + % 空格');
  }
  var values = [];
  for (var i = 0; i < text.length; i++) values.push(CODE93_ALPHABET.indexOf(text.charAt(i)));

  function check(list) {
    var weight = 1;
    var sum = 0;
    for (var k = list.length - 1; k >= 0; k--) {
      sum += list[k] * weight;
      weight++;
      if (weight > 20) weight = 1;
    }
    return sum % 47;
  }

  values.push(check(values.slice()));
  values.push(check(values.slice()));
  values.unshift(47);
  values.push(47);

  var modules = [];
  for (var v = 0; v < values.length; v++) {
    var bits = CODE93_ENCODINGS[values[v]];
    for (var b = 0; b < 9; b++) modules.push(((bits >> (8 - b)) & 1) === 1);
  }
  modules.push(true);
  return { modules: modules, text: text, detail: '含校验位' };
}

function itf(content) {
  var text = String(content).replace(/\s/g, '');
  if (!/^[0-9]+$/.test(text) || text.length % 2 !== 0) {
    throw new Error('ITF 需要偶数位数字');
  }
  var modules = [];
  pushRun(modules, true, 1); pushRun(modules, false, 1);
  pushRun(modules, true, 1); pushRun(modules, false, 1);
  for (var i = 0; i < text.length; i += 2) {
    var a = ITF_PATTERNS[parseInt(text.charAt(i), 10)];
    var b = ITF_PATTERNS[parseInt(text.charAt(i + 1), 10)];
    for (var k = 0; k < 5; k++) {
      pushRun(modules, true, a[k]);
      pushRun(modules, false, b[k]);
    }
  }
  pushRun(modules, true, 3); pushRun(modules, false, 1); pushRun(modules, true, 1);
  return { modules: modules, text: text, detail: '偶数字符' };
}

function eanCheckDigit(digits) {
  var sum = 0;
  var len = digits.length;
  for (var i = 0; i < len; i++) {
    var d = parseInt(digits.charAt(i), 10);
    var fromRight = len - 1 - i;
    sum += (fromRight % 2 === 0) ? d * 3 : d;
  }
  return (10 - (sum % 10)) % 10;
}

function drawEanDigit(modules, digit, mode) {
  var w = EAN_L[digit];
  var widths = mode === 'G' ? [w[3], w[2], w[1], w[0]] : w;
  var startsDark = mode === 'R';
  for (var i = 0; i < 4; i++) {
    pushRun(modules, startsDark ? i % 2 === 0 : i % 2 === 1, widths[i]);
  }
}

function ean13(content) {
  var text = String(content).replace(/\s/g, '');
  if (!/^[0-9]{12,13}$/.test(text)) throw new Error('EAN-13 需要 12 位或 13 位数字');
  var base = text.substr(0, 12);
  var check = eanCheckDigit(base);
  if (text.length === 13 && parseInt(text.charAt(12), 10) !== check) {
    check = eanCheckDigit(base);
  }
  var full = base + check;
  var modules = [];
  var parity = EAN13_PARITY[parseInt(base.charAt(0), 10)];
  pushRun(modules, true, 1); pushRun(modules, false, 1); pushRun(modules, true, 1);
  for (var i = 1; i <= 6; i++) {
    drawEanDigit(modules, parseInt(base.charAt(i), 10), parity.charAt(i - 1) === 'E' ? 'G' : 'L');
  }
  pushRun(modules, false, 1); pushRun(modules, true, 1);
  pushRun(modules, false, 1); pushRun(modules, true, 1); pushRun(modules, false, 1);
  for (var j = 7; j <= 12; j++) {
    drawEanDigit(modules, parseInt(full.charAt(j), 10), 'R');
  }
  pushRun(modules, true, 1); pushRun(modules, false, 1); pushRun(modules, true, 1);
  return { modules: modules, text: full, detail: '含校验位 ' + check };
}

function ean8(content) {
  var text = String(content).replace(/\s/g, '');
  if (!/^[0-9]{7,8}$/.test(text)) throw new Error('EAN-8 需要 7 位或 8 位数字');
  var base = text.substr(0, 7);
  var check = eanCheckDigit(base);
  var full = base + check;
  var modules = [];
  pushRun(modules, true, 1); pushRun(modules, false, 1); pushRun(modules, true, 1);
  for (var j = 0; j < 4; j++) drawEanDigit(modules, parseInt(base.charAt(j), 10), 'L');
  pushRun(modules, false, 1); pushRun(modules, true, 1);
  pushRun(modules, false, 1); pushRun(modules, true, 1); pushRun(modules, false, 1);
  for (var k = 4; k <= 7; k++) drawEanDigit(modules, parseInt(full.charAt(k), 10), 'R');
  pushRun(modules, true, 1); pushRun(modules, false, 1); pushRun(modules, true, 1);
  return { modules: modules, text: full, detail: '含校验位 ' + check };
}

module.exports = {
  code128: code128,
  code39: code39,
  code93: code93,
  itf: itf,
  ean13: ean13,
  ean8: ean8
};
