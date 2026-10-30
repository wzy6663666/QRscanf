'use strict';

/**
 * QR Code 编码器（纯 JS，无依赖）
 * 支持：版本 1-40、L/M/Q/H 纠错、数字/字母数字/字节模式、掩码 0-7（可强制）、
 *      UTF-8 与 ISO-8859-1 两种字符编码。
 */

var ECC_CODEWORDS_PER_BLOCK = [
  [-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
  [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],
  [-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
  [-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30]
];

var NUM_ECC_BLOCKS = [
  [-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],
  [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],
  [-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],
  [-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81]
];

var ECL_INDEX = { L: 0, M: 1, Q: 2, H: 3 };
var ECL_FORMAT = [1, 0, 3, 2];
var ALNUM_CHARSET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:';

function getBit(x, i) {
  return ((x >>> i) & 1) !== 0;
}

function gfMul(x, y) {
  var z = 0;
  for (var i = 7; i >= 0; i--) {
    z = (z << 1) ^ ((z >>> 7) * 0x11D);
    z ^= ((y >>> i) & 1) * x;
  }
  return z & 0xFF;
}

function rsDivisor(degree) {
  var result = new Array(degree);
  for (var i = 0; i < degree; i++) result[i] = 0;
  result[degree - 1] = 1;
  var root = 1;
  for (var i2 = 0; i2 < degree; i2++) {
    for (var j = 0; j < degree; j++) {
      result[j] = gfMul(result[j], root);
      if (j + 1 < degree) result[j] ^= result[j + 1];
    }
    root = gfMul(root, 0x02);
  }
  return result;
}

function rsRemainder(data, divisor) {
  var result = [];
  for (var i = 0; i < divisor.length; i++) result.push(0);
  for (var k = 0; k < data.length; k++) {
    var factor = data[k] ^ result.shift();
    result.push(0);
    for (var j = 0; j < result.length; j++) {
      result[j] ^= gfMul(divisor[j], factor);
    }
  }
  return result;
}

function numRawDataModules(ver) {
  var result = (16 * ver + 128) * ver + 64;
  if (ver >= 2) {
    var numAlign = Math.floor(ver / 7) + 2;
    result -= (25 * numAlign - 10) * numAlign - 55;
    if (ver >= 7) result -= 36;
  }
  return result;
}

function numDataCodewords(ver, ecl) {
  return Math.floor(numRawDataModules(ver) / 8) - ECC_CODEWORDS_PER_BLOCK[ecl][ver] * NUM_ECC_BLOCKS[ecl][ver];
}

function alignPositions(ver, size) {
  if (ver === 1) return [];
  var numAlign = Math.floor(ver / 7) + 2;
  var step = (ver === 32) ? 26 : Math.floor((ver * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2)) * 2;
  var result = new Array(numAlign);
  result[numAlign - 1] = size - 7;
  var pos = size - 7 - step;
  for (var i = numAlign - 2; i >= 1; i--) {
    result[i] = pos;
    pos -= step;
  }
  result[0] = 6;
  return result;
}

function utf8Bytes(text) {
  var out = [];
  for (var i = 0; i < text.length; i++) {
    var code = text.charCodeAt(i);
    if (code >= 0xD800 && code <= 0xDBFF && i + 1 < text.length) {
      var low = text.charCodeAt(i + 1);
      if (low >= 0xDC00 && low <= 0xDFFF) {
        code = 0x10000 + ((code - 0xD800) << 10) + (low - 0xDC00);
        i++;
      }
    }
    if (code < 0x80) {
      out.push(code);
    } else if (code < 0x800) {
      out.push(0xC0 | (code >> 6), 0x80 | (code & 0x3F));
    } else if (code < 0x10000) {
      out.push(0xE0 | (code >> 12), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
    } else {
      out.push(0xF0 | (code >> 18), 0x80 | ((code >> 12) & 0x3F), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
    }
  }
  return out;
}

function latin1Bytes(text) {
  var out = [];
  for (var i = 0; i < text.length; i++) out.push(text.charCodeAt(i) & 0xFF);
  return out;
}

function detectMode(text) {
  if (text.length > 0 && /^[0-9]+$/.test(text)) return 'numeric';
  var ok = true;
  for (var i = 0; i < text.length; i++) {
    if (ALNUM_CHARSET.indexOf(text.charAt(i)) < 0) { ok = false; break; }
  }
  return ok ? 'alnum' : 'byte';
}

function ccBitsFor(mode, ver) {
  if (mode === 'numeric') return ver <= 9 ? 10 : (ver <= 26 ? 12 : 14);
  if (mode === 'alnum') return ver <= 9 ? 9 : (ver <= 26 ? 11 : 13);
  return ver <= 9 ? 8 : 16;
}

/**
 * 把内容编码成数据码字。放不下时返回 null。
 */
function buildDataCodewords(text, ver, ecl, encoding) {
  var mode = detectMode(text);
  var ccBits = ccBitsFor(mode, ver);
  var capacity = numDataCodewords(ver, ecl) * 8;
  var bits = [];

  function push(value, len) {
    for (var i = len - 1; i >= 0; i--) bits.push((value >>> i) & 1);
  }

  if (mode === 'numeric') {
    push(1, 4);
    push(text.length, ccBits);
    var idx = 0;
    while (idx < text.length) {
      var take = Math.min(3, text.length - idx);
      push(parseInt(text.substr(idx, take), 10), take * 3 + 1);
      idx += take;
    }
  } else if (mode === 'alnum') {
    push(2, 4);
    push(text.length, ccBits);
    var i = 0;
    while (i < text.length) {
      if (i + 1 < text.length) {
        push(ALNUM_CHARSET.indexOf(text.charAt(i)) * 45 + ALNUM_CHARSET.indexOf(text.charAt(i + 1)), 11);
        i += 2;
      } else {
        push(ALNUM_CHARSET.indexOf(text.charAt(i)), 6);
        i += 1;
      }
    }
  } else {
    var bytes = encoding === 'latin1' ? latin1Bytes(text) : utf8Bytes(text);
    if (bytes.length >= (1 << ccBits)) return null;
    push(4, 4);
    push(bytes.length, ccBits);
    for (var b = 0; b < bytes.length; b++) push(bytes[b], 8);
  }

  if (bits.length > capacity) return null;
  push(0, Math.min(4, capacity - bits.length));
  while (bits.length % 8 !== 0) bits.push(0);

  var codewords = [];
  for (var j = 0; j < bits.length; j += 8) {
    var value = 0;
    for (var k = 0; k < 8; k++) value = (value << 1) | bits[j + k];
    codewords.push(value);
  }
  var pad = 0xEC;
  while (codewords.length < numDataCodewords(ver, ecl)) {
    codewords.push(pad);
    pad = pad === 0xEC ? 0x11 : 0xEC;
  }
  return { codewords: codewords, mode: mode };
}

function addEccAndInterleave(data, ver, ecl) {
  var numBlocks = NUM_ECC_BLOCKS[ecl][ver];
  var blockEccLen = ECC_CODEWORDS_PER_BLOCK[ecl][ver];
  var rawCodewords = Math.floor(numRawDataModules(ver) / 8);
  var numShortBlocks = numBlocks - rawCodewords % numBlocks;
  var shortBlockLen = Math.floor(rawCodewords / numBlocks);
  var divisor = rsDivisor(blockEccLen);
  var blocks = [];
  var k = 0;
  for (var i = 0; i < numBlocks; i++) {
    var len = shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1);
    var dat = data.slice(k, k + len);
    k += len;
    var ecc = rsRemainder(dat, divisor);
    if (i < numShortBlocks) dat.push(0);
    blocks.push(dat.concat(ecc));
  }
  var result = [];
  for (var pos = 0; pos < blocks[0].length; pos++) {
    for (var b = 0; b < blocks.length; b++) {
      if (pos !== shortBlockLen - blockEccLen || b >= numShortBlocks) {
        result.push(blocks[b][pos]);
      }
    }
  }
  return result;
}

function Matrix(size) {
  this.size = size;
  this.modules = [];
  this.fn = [];
  for (var y = 0; y < size; y++) {
    var row = new Array(size);
    var frow = new Array(size);
    for (var x = 0; x < size; x++) { row[x] = false; frow[x] = false; }
    this.modules.push(row);
    this.fn.push(frow);
  }
}

Matrix.prototype.setFn = function (x, y, dark) {
  if (x < 0 || y < 0 || x >= this.size || y >= this.size) return;
  this.modules[y][x] = dark;
  this.fn[y][x] = true;
};

Matrix.prototype.drawFinder = function (x, y) {
  for (var dy = -4; dy <= 4; dy++) {
    for (var dx = -4; dx <= 4; dx++) {
      var dist = Math.max(Math.abs(dx), Math.abs(dy));
      this.setFn(x + dx, y + dy, dist !== 2 && dist !== 4);
    }
  }
};

Matrix.prototype.drawAlignment = function (x, y) {
  for (var dy = -2; dy <= 2; dy++) {
    for (var dx = -2; dx <= 2; dx++) {
      this.setFn(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
    }
  }
};

Matrix.prototype.drawFormat = function (eclIndex, mask) {
  var data = (ECL_FORMAT[eclIndex] << 3) | mask;
  var rem = data;
  for (var i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
  var bits = ((data << 10) | rem) ^ 0x5412;
  var size = this.size;
  var b;
  for (b = 0; b <= 5; b++) this.setFn(8, b, getBit(bits, b));
  this.setFn(8, 7, getBit(bits, 6));
  this.setFn(8, 8, getBit(bits, 7));
  this.setFn(7, 8, getBit(bits, 8));
  for (b = 9; b < 15; b++) this.setFn(14 - b, 8, getBit(bits, b));
  for (b = 0; b < 8; b++) this.setFn(size - 1 - b, 8, getBit(bits, b));
  for (b = 8; b < 15; b++) this.setFn(8, size - 15 + b, getBit(bits, b));
  this.setFn(8, size - 8, true);
};

Matrix.prototype.drawVersion = function (ver) {
  if (ver < 7) return;
  var rem = ver;
  for (var i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
  var bits = (ver << 12) | rem;
  for (var k = 0; k < 18; k++) {
    var bit = getBit(bits, k);
    var a = this.size - 11 + (k % 3);
    var b = Math.floor(k / 3);
    this.setFn(a, b, bit);
    this.setFn(b, a, bit);
  }
};

Matrix.prototype.drawFunctionPatterns = function (ver, eclIndex) {
  var size = this.size;
  for (var i = 0; i < size; i++) {
    this.setFn(6, i, i % 2 === 0);
    this.setFn(i, 6, i % 2 === 0);
  }
  this.drawFinder(3, 3);
  this.drawFinder(size - 4, 3);
  this.drawFinder(3, size - 4);
  var pos = alignPositions(ver, size);
  for (var a = 0; a < pos.length; a++) {
    for (var b = 0; b < pos.length; b++) {
      var cx = pos[a];
      var cy = pos[b];
      var corner = (cx === 6 && cy === 6) || (cx === 6 && cy === size - 7) || (cx === size - 7 && cy === 6);
      if (!corner) this.drawAlignment(cx, cy);
    }
  }
  this.drawFormat(eclIndex, 0);
  this.drawVersion(ver);
};

function maskBit(mask, x, y) {
  switch (mask) {
    case 0: return (x + y) % 2 === 0;
    case 1: return y % 2 === 0;
    case 2: return x % 3 === 0;
    case 3: return (x + y) % 3 === 0;
    case 4: return (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0;
    case 5: return ((x * y) % 2) + ((x * y) % 3) === 0;
    case 6: return (((x * y) % 2) + ((x * y) % 3)) % 2 === 0;
    case 7: return (((x + y) % 2) + ((x * y) % 3)) % 2 === 0;
    default: return false;
  }
}

function penaltyScore(m) {
  var size = m.size;
  var score = 0;
  var dark = 0;
  var x, y, run, color;
  for (y = 0; y < size; y++) {
    run = 0; color = false;
    for (x = 0; x < size; x++) {
      var v = m.modules[y][x];
      if (v) dark++;
      if (x === 0) { color = v; run = 1; }
      else if (v === color) { run++; if (run === 5) score += 3; else if (run > 5) score++; }
      else { color = v; run = 1; }
    }
  }
  for (x = 0; x < size; x++) {
    run = 0; color = false;
    for (y = 0; y < size; y++) {
      var v2 = m.modules[y][x];
      if (y === 0) { color = v2; run = 1; }
      else if (v2 === color) { run++; if (run === 5) score += 3; else if (run > 5) score++; }
      else { color = v2; run = 1; }
    }
  }
  for (y = 0; y < size - 1; y++) {
    for (x = 0; x < size - 1; x++) {
      var c = m.modules[y][x];
      if (c === m.modules[y][x + 1] && c === m.modules[y + 1][x] && c === m.modules[y + 1][x + 1]) score += 3;
    }
  }
  var pattern1 = [true, false, true, true, true, false, true, false, false, false, false];
  var pattern2 = [false, false, false, false, true, false, true, true, true, false, true];
  function matchRow(getter, fixed, start, pattern) {
    for (var k = 0; k < 11; k++) {
      if (getter(fixed, start + k) !== pattern[k]) return false;
    }
    return true;
  }
  for (y = 0; y < size; y++) {
    for (x = 0; x <= size - 11; x++) {
      if (matchRow(function (yy, xx) { return m.modules[yy][xx]; }, y, x, pattern1)) score += 40;
      if (matchRow(function (yy, xx) { return m.modules[yy][xx]; }, y, x, pattern2)) score += 40;
    }
  }
  for (x = 0; x < size; x++) {
    for (y = 0; y <= size - 11; y++) {
      if (matchRow(function (xx, yy) { return m.modules[yy][xx]; }, x, y, pattern1)) score += 40;
      if (matchRow(function (xx, yy) { return m.modules[yy][xx]; }, x, y, pattern2)) score += 40;
    }
  }
  var total = size * size;
  var k2 = Math.ceil(Math.abs(dark * 20 - total * 10) / total) - 1;
  score += Math.max(0, k2) * 10;
  return score;
}

/**
 * 生成二维码。
 * @param {string} text 内容
 * @param {object} opts { ecLevel, version, mask, encoding }
 * @returns {{size:number, modules:boolean[][], version:number, ecl:string, mask:number, mode:string}}
 */
function encode(text, opts) {
  opts = opts || {};
  var eclName = opts.ecLevel && ECL_INDEX[opts.ecLevel] !== undefined ? opts.ecLevel : 'M';
  var ecl = ECL_INDEX[eclName];
  var encoding = opts.encoding === 'latin1' ? 'latin1' : 'utf8';
  var forcedVersion = opts.version && opts.version >= 1 && opts.version <= 40 ? opts.version : 0;
  var forcedMask = typeof opts.mask === 'number' && opts.mask >= 0 && opts.mask <= 7 ? opts.mask : -1;

  var content = text == null ? '' : String(text);
  var start = forcedVersion || 1;
  var built = null;
  var version = start;
  for (; version <= 40; version++) {
    built = buildDataCodewords(content, version, ecl, encoding);
    if (built) break;
  }
  if (!built) {
    throw new Error(forcedVersion ? '内容超出该版本容量，请调大版本或缩短内容' : '内容过长，超出二维码容量');
  }

  var finalCodewords = addEccAndInterleave(built.codewords.slice(), version, ecl);
  var size = version * 4 + 17;
  var m = new Matrix(size);
  m.drawFunctionPatterns(version, ecl);

  var i = 0;
  var total = finalCodewords.length * 8;
  for (var right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5;
    for (var vert = 0; vert < size; vert++) {
      for (var j = 0; j < 2; j++) {
        var x = right - j;
        var upward = ((right + 1) & 2) === 0;
        var y = upward ? size - 1 - vert : vert;
        if (!m.fn[y][x] && i < total) {
          m.modules[y][x] = getBit(finalCodewords[i >>> 3], 7 - (i & 7));
          i++;
        }
      }
    }
  }

  var mask = forcedMask;
  if (mask < 0) {
    var best = Infinity;
    mask = 0;
    for (var k = 0; k < 8; k++) {
      applyMask(m, k);
      m.drawFormat(ecl, k);
      var p = penaltyScore(m);
      applyMask(m, k);
      if (p < best) { best = p; mask = k; }
    }
  }
  applyMask(m, mask);
  m.drawFormat(ecl, mask);

  return {
    size: size,
    modules: m.modules,
    version: version,
    ecl: eclName,
    mask: mask,
    mode: built.mode
  };
}

function applyMask(m, mask) {
  for (var y = 0; y < m.size; y++) {
    for (var x = 0; x < m.size; x++) {
      if (!m.fn[y][x] && maskBit(mask, x, y)) m.modules[y][x] = !m.modules[y][x];
    }
  }
}

module.exports = {
  encode: encode,
  capacity: function (version, eclName) {
    var ecl = ECL_INDEX[eclName] || 1;
    return numDataCodewords(version, ecl) * 8;
  }
};
