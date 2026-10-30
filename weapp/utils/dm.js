'use strict';

/**
 * Data Matrix ECC200 编码器（纯 JS，无依赖）
 * 支持方形符号 10x10 ~ 48x48，ASCII / 数字对压缩 / Base256（非 ASCII 走 UTF-8）。
 */

// size, 数据码字, 纠错码字, 单个数据区边长, 每边数据区个数
var SYMBOLS = [
  [10, 3, 5, 8, 1],
  [12, 5, 7, 10, 1],
  [14, 8, 10, 12, 1],
  [16, 12, 12, 14, 1],
  [18, 18, 14, 16, 1],
  [20, 22, 18, 18, 1],
  [22, 30, 20, 20, 1],
  [24, 36, 24, 22, 1],
  [26, 44, 28, 24, 1],
  [32, 62, 36, 14, 2],
  [36, 86, 42, 16, 2],
  [40, 114, 48, 18, 2],
  [44, 144, 56, 20, 2],
  [48, 174, 68, 22, 2]
];

var GF_EXP = new Array(512);
var GF_LOG = new Array(256);

(function initGalois() {
  var x = 1;
  for (var i = 0; i < 255; i++) {
    GF_EXP[i] = x;
    GF_LOG[x] = i;
    x <<= 1;
    if (x & 0x100) x ^= 0x12D;
  }
  for (var j = 255; j < 512; j++) GF_EXP[j] = GF_EXP[j - 255];
})();

function gfMul(a, b) {
  if (a === 0 || b === 0) return 0;
  return GF_EXP[GF_LOG[a] + GF_LOG[b]];
}

/** 生成多项式：根为 α^1 ... α^degree（Data Matrix 的生成元起点为 1） */
function generator(degree) {
  var poly = [1];
  for (var d = 0; d < degree; d++) {
    var next = new Array(poly.length + 1);
    for (var i = 0; i < next.length; i++) next[i] = 0;
    var root = GF_EXP[d + 1];
    for (var k = 0; k < poly.length; k++) {
      next[k] ^= poly[k];
      next[k + 1] ^= gfMul(poly[k], root);
    }
    poly = next;
  }
  // poly[0] 为最高次项系数，归一到 1
  var lead = poly[0];
  if (lead !== 1) {
    var inv = GF_EXP[(255 - GF_LOG[lead]) % 255];
    for (var m = 0; m < poly.length; m++) poly[m] = gfMul(poly[m], inv);
  }
  return poly;
}

function eccFor(data, eccCount) {
  var gen = generator(eccCount);
  var buf = data.slice();
  for (var i = 0; i < eccCount; i++) buf.push(0);
  for (var p = 0; p < data.length; p++) {
    var coef = buf[p];
    if (coef === 0) continue;
    for (var j = 0; j < gen.length; j++) {
      buf[p + j] ^= gfMul(gen[j], coef);
    }
  }
  return buf.slice(data.length);
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
    if (code < 0x80) out.push(code);
    else if (code < 0x800) out.push(0xC0 | (code >> 6), 0x80 | (code & 0x3F));
    else if (code < 0x10000) out.push(0xE0 | (code >> 12), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
    else out.push(0xF0 | (code >> 18), 0x80 | ((code >> 12) & 0x3F), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
  }
  return out;
}

function isPureAscii(text) {
  for (var i = 0; i < text.length; i++) {
    if (text.charCodeAt(i) > 127) return false;
  }
  return true;
}

/** ASCII 段：数字成对压缩为 130..229 */
function encodeAscii(text) {
  var cw = [];
  var i = 0;
  while (i < text.length) {
    var c = text.charCodeAt(i);
    var c2 = i + 1 < text.length ? text.charCodeAt(i + 1) : -1;
    if (c >= 48 && c <= 57 && c2 >= 48 && c2 <= 57) {
      cw.push((c - 48) * 10 + (c2 - 48) + 130);
      i += 2;
    } else {
      cw.push(c + 1);
      i++;
    }
  }
  return cw;
}

function encodeBase256(bytes) {
  var body = [];
  if (bytes.length <= 249) {
    body.push(bytes.length);
  } else if (bytes.length <= 1555) {
    body.push(Math.floor(bytes.length / 250) + 249);
    body.push(bytes.length % 250);
  } else {
    throw new Error('内容过长，Data Matrix 单符号最多 1555 字节');
  }
  for (var i = 0; i < bytes.length; i++) body.push(bytes[i]);

  // 231 为锁存符，不参与随机化；长度与数据按“255 状态随机化”逐位处理
  var cw = [231];
  for (var k = 0; k < body.length; k++) {
    var pseudo = ((149 * (cw.length + 1)) % 255) + 1;
    var value = body[k] + pseudo;
    cw.push(value <= 255 ? value : value - 256);
  }
  return cw;
}

function randomize253(value, position) {
  var r = value + ((149 * position) % 253) + 1;
  return r <= 254 ? r : r - 254;
}

function pickSymbol(needed) {
  for (var i = 0; i < SYMBOLS.length; i++) {
    if (SYMBOLS[i][1] >= needed) return SYMBOLS[i];
  }
  return null;
}

function placeBits(codewords, rows, cols) {
  var bits = [];
  for (var y = 0; y < rows; y++) {
    var row = new Array(cols);
    for (var x = 0; x < cols; x++) row[x] = -1;
    bits.push(row);
  }

  function setModule(row, col, pos, bit) {
    if (row < 0) {
      row += rows;
      col += 4 - ((rows + 4) % 8);
    }
    if (col < 0) {
      col += cols;
      row += 4 - ((cols + 4) % 8);
    }
    var value = (codewords[pos] & (1 << (8 - bit))) !== 0 ? 1 : 0;
    bits[row][col] = value;
  }

  function utah(row, col, pos) {
    setModule(row - 2, col - 2, pos, 1);
    setModule(row - 2, col - 1, pos, 2);
    setModule(row - 1, col - 2, pos, 3);
    setModule(row - 1, col - 1, pos, 4);
    setModule(row - 1, col, pos, 5);
    setModule(row, col - 2, pos, 6);
    setModule(row, col - 1, pos, 7);
    setModule(row, col, pos, 8);
  }

  function corner1(pos) {
    setModule(rows - 1, 0, pos, 1);
    setModule(rows - 1, 1, pos, 2);
    setModule(rows - 1, 2, pos, 3);
    setModule(0, cols - 2, pos, 4);
    setModule(0, cols - 1, pos, 5);
    setModule(1, cols - 1, pos, 6);
    setModule(2, cols - 1, pos, 7);
    setModule(3, cols - 1, pos, 8);
  }

  function corner2(pos) {
    setModule(rows - 3, 0, pos, 1);
    setModule(rows - 2, 0, pos, 2);
    setModule(rows - 1, 0, pos, 3);
    setModule(0, cols - 4, pos, 4);
    setModule(0, cols - 3, pos, 5);
    setModule(0, cols - 2, pos, 6);
    setModule(0, cols - 1, pos, 7);
    setModule(1, cols - 1, pos, 8);
  }

  function corner3(pos) {
    setModule(rows - 3, 0, pos, 1);
    setModule(rows - 2, 0, pos, 2);
    setModule(rows - 1, 0, pos, 3);
    setModule(0, cols - 2, pos, 4);
    setModule(0, cols - 1, pos, 5);
    setModule(1, cols - 1, pos, 6);
    setModule(2, cols - 1, pos, 7);
    setModule(3, cols - 1, pos, 8);
  }

  function corner4(pos) {
    setModule(rows - 1, 0, pos, 1);
    setModule(rows - 1, cols - 1, pos, 2);
    setModule(0, cols - 3, pos, 3);
    setModule(0, cols - 2, pos, 4);
    setModule(0, cols - 1, pos, 5);
    setModule(1, cols - 3, pos, 6);
    setModule(1, cols - 2, pos, 7);
    setModule(1, cols - 1, pos, 8);
  }

  function hasBit(col, row) {
    return bits[row][col] >= 0;
  }

  var pos = 0;
  var row = 4;
  var col = 0;
  do {
    if (row === rows && col === 0) corner1(pos++);
    if (row === rows - 2 && col === 0 && cols % 4 !== 0) corner2(pos++);
    if (row === rows - 2 && col === 0 && cols % 8 === 4) corner3(pos++);
    if (row === rows + 4 && col === 2 && cols % 8 === 0) corner4(pos++);
    do {
      if (row < rows && col >= 0 && !hasBit(col, row)) utah(row, col, pos++);
      row -= 2;
      col += 2;
    } while (row >= 0 && col < cols);
    row += 1;
    col += 3;
    do {
      if (row >= 0 && col < cols && !hasBit(col, row)) utah(row, col, pos++);
      row += 2;
      col -= 2;
    } while (row < rows && col >= 0);
    row += 3;
    col += 1;
  } while (row < rows || col < cols);

  if (bits[rows - 1][cols - 1] === -1) {
    bits[rows - 1][cols - 1] = 1;
    bits[rows - 2][cols - 2] = 1;
  }
  return bits;
}

/**
 * 生成 Data Matrix。
 * @param {string} text
 * @returns {{size:number, modules:boolean[][], symbol:number, encodation:string}}
 */
function encode(text) {
  var content = text == null ? '' : String(text);
  var ascii = isPureAscii(content);
  var codewords = ascii ? encodeAscii(content) : encodeBase256(utf8Bytes(content));

  var sym = pickSymbol(codewords.length);
  if (!sym) {
    var bytes = ascii ? encodeBase256(utf8Bytes(content)) : codewords;
    sym = pickSymbol(bytes.length);
    if (sym) {
      codewords = bytes;
      ascii = false;
    }
  }
  if (!sym) {
    throw new Error('内容过长，Data Matrix 最多支持 174 个字符，请缩短内容或改用 QR Code');
  }

  var capacity = sym[1];
  var eccCount = sym[2];
  if (codewords.length < capacity) {
    codewords.push(129);
    while (codewords.length < capacity) {
      codewords.push(randomize253(129, codewords.length + 1));
    }
  }

  var ecc = eccFor(codewords, eccCount);
  var full = codewords.concat(ecc);

  var regionData = sym[3];
  var regions = sym[4];
  var dataSize = regionData * regions;
  var bits = placeBits(full, dataSize, dataSize);

  var total = dataSize + 2 * regions;
  var modules = [];
  for (var i = 0; i < total; i++) {
    var r = new Array(total);
    for (var j = 0; j < total; j++) r[j] = false;
    modules.push(r);
  }

  for (var gy = 0; gy < regions; gy++) {
    for (var gx = 0; gx < regions; gx++) {
      var baseY = gy * (regionData + 2);
      var baseX = gx * (regionData + 2);
      for (var y = 0; y <= regionData + 1; y++) {
        for (var x = 0; x <= regionData + 1; x++) {
          var dark;
          if (y === 0) dark = x % 2 === 0;
          else if (y === regionData + 1) dark = true;
          else if (x === 0) dark = true;
          else if (x === regionData + 1) dark = y % 2 === 1;
          else dark = bits[gy * regionData + (y - 1)][gx * regionData + (x - 1)] === 1;
          modules[baseY + y][baseX + x] = dark;
        }
      }
    }
  }

  return {
    size: total,
    modules: modules,
    symbol: total + 'x' + total,
    encodation: ascii ? 'ASCII' : 'Base256',
    dataCodewords: capacity,
    eccCodewords: eccCount
  };
}

module.exports = {
  encode: encode,
  capacity: function (size) {
    for (var i = 0; i < SYMBOLS.length; i++) if (SYMBOLS[i][0] === size) return SYMBOLS[i][1];
    return 0;
  }
};
