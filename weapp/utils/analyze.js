'use strict';

/**
 * 图像分析：从图片像素里定位码区、估算参数，用于「扫描填充 / 复刻」。
 * 输入为 canvas getImageData 的结果 { data, width, height }。
 */

function toGray(image) {
  var w = image.width;
  var h = image.height;
  var data = image.data;
  var gray = new Uint8Array(w * h);
  for (var i = 0, p = 0; i < gray.length; i++, p += 4) {
    gray[i] = (data[p] * 299 + data[p + 1] * 587 + data[p + 2] * 114) / 1000;
  }
  return gray;
}

function otsu(gray) {
  var hist = new Array(256);
  for (var i = 0; i < 256; i++) hist[i] = 0;
  for (var k = 0; k < gray.length; k++) hist[gray[k]]++;
  var total = gray.length;
  var sum = 0;
  for (var t = 0; t < 256; t++) sum += t * hist[t];
  var sumB = 0;
  var wB = 0;
  var best = 0;
  var threshold = 128;
  for (var v = 0; v < 256; v++) {
    wB += hist[v];
    if (wB === 0) continue;
    var wF = total - wB;
    if (wF === 0) break;
    sumB += v * hist[v];
    var mB = sumB / wB;
    var mF = (sum - sumB) / wF;
    var between = wB * wF * (mB - mF) * (mB - mF);
    if (between > best) {
      best = between;
      threshold = v;
    }
  }
  return threshold;
}

function binarize(gray, w, h) {
  var threshold = otsu(gray);
  var bits = new Uint8Array(w * h);
  for (var i = 0; i < bits.length; i++) bits[i] = gray[i] <= threshold ? 1 : 0;
  return { bits: denoise(bits, w, h), threshold: threshold };
}

/** 3x3 邻域多数表决，压掉孤立噪点（拍照噪点、JPEG 杂色） */
function denoise(bits, w, h) {
  var out = new Uint8Array(bits.length);
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      if (x === 0 || y === 0 || x === w - 1 || y === h - 1) {
        out[y * w + x] = bits[y * w + x];
        continue;
      }
      var count = 0;
      for (var dy = -1; dy <= 1; dy++) {
        var base = (y + dy) * w + x;
        count += bits[base - 1] + bits[base] + bits[base + 1];
      }
      out[y * w + x] = count >= 5 ? 1 : 0;
    }
  }
  return out;
}

/** 在一行里找 1:1:3:1:1 的定位图案，返回中心横坐标 */
function findPatternInRow(bits, w, y, hits) {
  var x = 0;
  var runs = [];
  while (x < w) {
    var color = bits[y * w + x];
    var start = x;
    while (x < w && bits[y * w + x] === color) x++;
    runs.push({ color: color, start: start, end: x, len: x - start });
  }
  for (var i = 0; i + 4 < runs.length; i++) {
    if (runs[i].color !== 1) continue;
    var a = runs[i];
    var b = runs[i + 1];
    var c = runs[i + 2];
    var d = runs[i + 3];
    var e = runs[i + 4];
    if (b.color !== 0 || c.color !== 1 || d.color !== 0 || e.color !== 1) continue;
    var unit = (a.len + b.len + d.len + e.len) / 4;
    if (unit < 1) continue;
    if (Math.abs(a.len - unit) > unit * 0.7) continue;
    if (Math.abs(b.len - unit) > unit * 0.7) continue;
    if (Math.abs(d.len - unit) > unit * 0.7) continue;
    if (Math.abs(e.len - unit) > unit * 0.7) continue;
    if (c.len < unit * 1.8 || c.len > unit * 4.2) continue;
    hits.push({ x: (a.start + e.end) / 2, y: y, unit: (a.len + b.len + c.len + d.len + e.len) / 7 });
  }
}

function verifyVertical(bits, w, h, cx, cy, unit) {
  var span = Math.max(2, Math.round(unit * 5));
  var y0 = Math.max(0, Math.round(cy - span));
  var y1 = Math.min(h - 1, Math.round(cy + span));
  var x = Math.round(cx);
  if (x < 0 || x >= w) return null;
  var runs = [];
  var y = y0;
  while (y <= y1) {
    var color = bits[y * w + x];
    var start = y;
    while (y <= y1 && bits[y * w + x] === color) y++;
    runs.push({ color: color, start: start, len: y - start });
  }
  for (var i = 0; i + 4 < runs.length; i++) {
    if (runs[i].color !== 1) continue;
    if (runs[i + 1].color !== 0 || runs[i + 2].color !== 1 || runs[i + 3].color !== 0 || runs[i + 4].color !== 1) continue;
    var u = (runs[i].len + runs[i + 1].len + runs[i + 3].len + runs[i + 4].len) / 4;
    if (u < 1) continue;
    if (runs[i + 2].len < u * 1.8 || runs[i + 2].len > u * 4.2) continue;
    return { y: (runs[i].start + runs[i + 4].start + runs[i + 4].len) / 2, unit: u };
  }
  return null;
}

function cluster(points, radius) {
  var groups = [];
  points.forEach(function (p) {
    for (var i = 0; i < groups.length; i++) {
      var g = groups[i];
      if (Math.abs(g.x - p.x) <= radius && Math.abs(g.y - p.y) <= radius) {
        g.items.push(p);
        g.x = (g.x * (g.items.length - 1) + p.x) / g.items.length;
        g.y = (g.y * (g.items.length - 1) + p.y) / g.items.length;
        g.unit = (g.unit * (g.items.length - 1) + p.unit) / g.items.length;
        return;
      }
    }
    groups.push({ x: p.x, y: p.y, unit: p.unit, items: [p] });
  });
  return groups;
}

function dist(a, b) {
  return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
}

function pickThree(groups) {
  if (groups.length < 3) return null;
  var best = null;
  for (var i = 0; i < groups.length; i++) {
    for (var j = i + 1; j < groups.length; j++) {
      for (var k = j + 1; k < groups.length; k++) {
        var pts = [groups[i], groups[j], groups[k]];
        var sides = [dist(pts[1], pts[2]), dist(pts[0], pts[2]), dist(pts[0], pts[1])];
        var maxSide = Math.max(sides[0], sides[1], sides[2]);
        var minSide = Math.min(sides[0], sides[1], sides[2]);
        if (minSide / maxSide < 0.6) continue;
        var score = maxSide;
        if (!best || score > best.score) best = { score: score, pts: pts };
      }
    }
  }
  return best ? best.pts : null;
}

function orderCorners(pts) {
  // 直角点（与另外两点夹角最接近 90°）即左上角
  var bestIndex = 0;
  var bestDot = Infinity;
  for (var i = 0; i < 3; i++) {
    var a = pts[(i + 1) % 3];
    var b = pts[(i + 2) % 3];
    var v1 = { x: a.x - pts[i].x, y: a.y - pts[i].y };
    var v2 = { x: b.x - pts[i].x, y: b.y - pts[i].y };
    var dot = (v1.x * v2.x + v1.y * v2.y) / (Math.sqrt(v1.x * v1.x + v1.y * v1.y) * Math.sqrt(v2.x * v2.x + v2.y * v2.y) + 1e-6);
    if (Math.abs(dot) < Math.abs(bestDot)) { bestDot = dot; bestIndex = i; }
  }
  var tl = pts[bestIndex];
  var rest = pts.filter(function (p, idx) { return idx !== bestIndex; });
  var tr = rest[0];
  var bl = rest[1];
  var cross = (tr.x - tl.x) * (bl.y - tl.y) - (tr.y - tl.y) * (bl.x - tl.x);
  if (cross < 0) {
    var tmp = tr;
    tr = bl;
    bl = tmp;
  }
  return { tl: tl, tr: tr, bl: bl };
}

function bchFormatData(format1, format2) {
  var bestData = -1;
  var bestDistance = 99;
  for (var data = 0; data < 32; data++) {
    var value = data << 10;
    var poly = 0x537;
    while (highestBit(value) >= highestBit(poly)) {
      value ^= poly << (highestBit(value) - highestBit(poly));
    }
    var codeword = ((data << 10) | value) ^ 0x5412;
    var d1 = popcount(codeword ^ format1);
    var d2 = popcount(codeword ^ format2);
    var d = Math.min(d1, d2);
    if (d < bestDistance) {
      bestDistance = d;
      bestData = data;
    }
  }
  return bestDistance <= 3 ? bestData : -1;
}

function highestBit(value) {
  var bit = 0;
  while (value > 1) {
    value >>= 1;
    bit++;
  }
  return bit;
}

function popcount(v) {
  var c = 0;
  while (v) {
    c += v & 1;
    v >>= 1;
  }
  return c;
}

var EC_LEVEL = ['M', 'L', 'H', 'Q'];

var FINDER = [
  [1, 1, 1, 1, 1, 1, 1],
  [1, 0, 0, 0, 0, 0, 1],
  [1, 0, 1, 1, 1, 0, 1],
  [1, 0, 1, 1, 1, 0, 1],
  [1, 0, 1, 1, 1, 0, 1],
  [1, 0, 0, 0, 0, 0, 1],
  [1, 1, 1, 1, 1, 1, 1]
];

/** 校验采样结果是否真的是一个二维码：三个定位图案 + 定时图案 */
function validateQr(matrix, dim) {
  if (dim < 21 || (dim - 17) % 4 !== 0) return false;
  var origins = [[0, 0], [dim - 7, 0], [0, dim - 7]];
  for (var o = 0; o < origins.length; o++) {
    var ox = origins[o][0];
    var oy = origins[o][1];
    var match = 0;
    for (var y = 0; y < 7; y++) {
      for (var x = 0; x < 7; x++) {
        var want = FINDER[y][x] === 1;
        if (matrix[oy + y][ox + x] === want) match++;
      }
    }
    if (match / 49 < 0.86) return false;
  }
  var timing = 0;
  var count = 0;
  for (var i = 8; i <= dim - 9; i++) {
    count += 2;
    if (matrix[6][i] === (i % 2 === 0)) timing++;
    if (matrix[i][6] === (i % 2 === 0)) timing++;
  }
  return count === 0 || timing / count >= 0.8;
}

function readFormat(matrix, dim) {
  function bit(x, y) { return matrix[y][x] ? 1 : 0; }
  var format1 = 0;
  var i;
  for (i = 0; i < 6; i++) format1 = (format1 << 1) | bit(i, 8);
  format1 = (format1 << 1) | bit(7, 8);
  format1 = (format1 << 1) | bit(8, 8);
  format1 = (format1 << 1) | bit(8, 7);
  for (i = 5; i >= 0; i--) format1 = (format1 << 1) | bit(8, i);

  var format2 = 0;
  for (i = dim - 1; i >= dim - 7; i--) format2 = (format2 << 1) | bit(8, i);
  for (i = dim - 8; i < dim; i++) format2 = (format2 << 1) | bit(i, 8);

  var data = bchFormatData(format1, format2);
  if (data < 0) return null;
  return { ecl: EC_LEVEL[(data >> 3) & 0x03], mask: data & 0x07 };
}

function sampleMatrix(bits, w, h, corners, moduleSize) {
  var tl = corners.tl;
  var tr = corners.tr;
  var bl = corners.bl;
  var spanX = dist(tl, tr);
  var spanY = dist(tl, bl);
  var dimension = Math.round(((spanX + spanY) / 2) / moduleSize) + 7;
  var v = Math.round((dimension - 17) / 4);
  if (v < 1) v = 1;
  if (v > 40) v = 40;
  dimension = 17 + v * 4;

  var matrix = [];
  var darkR = 0, darkG = 0, darkB = 0, darkN = 0;
  var lightR = 0, lightG = 0, lightB = 0, lightN = 0;
  for (var y = 0; y < dimension; y++) {
    var row = [];
    for (var x = 0; x < dimension; x++) {
      var u = (x + 0.5 - 3.5) / (dimension - 7);
      var vv = (y + 0.5 - 3.5) / (dimension - 7);
      var sx = Math.round(tl.x + u * (tr.x - tl.x) + vv * (bl.x - tl.x));
      var sy = Math.round(tl.y + u * (tr.y - tl.y) + vv * (bl.y - tl.y));
      var dark = 0;
      if (sx >= 0 && sy >= 0 && sx < w && sy < h) dark = bits[sy * w + sx];
      row.push(dark === 1);
    }
    matrix.push(row);
  }
  return { matrix: matrix, dimension: dimension, version: v };
}

function estimateQuietRatio(bits, w, h, corners) {
  var tl = corners.tl;
  var tr = corners.tr;
  var bl = corners.bl;
  var span = (dist(tl, tr) + dist(tl, bl)) / 2;
  var left = 0;
  for (var x = Math.round(tl.x); x >= 0; x--) {
    var dark = false;
    for (var y = Math.round(tl.y); y < Math.round(bl.y) && y < h; y += 2) {
      if (bits[y * w + x]) { dark = true; break; }
    }
    if (dark) break;
    left++;
  }
  return span > 0 ? left / span : 0;
}

function averageColors(image, bits, w, h) {
  var data = image.data;
  var dark = { r: 0, g: 0, b: 0, n: 0 };
  var light = { r: 0, g: 0, b: 0, n: 0 };
  var step = Math.max(1, Math.floor(Math.sqrt((w * h) / 20000)));
  for (var y = 0; y < h; y += step) {
    for (var x = 0; x < w; x += step) {
      var p = (y * w + x) * 4;
      if (bits[y * w + x]) {
        dark.r += data[p]; dark.g += data[p + 1]; dark.b += data[p + 2]; dark.n++;
      } else {
        light.r += data[p]; light.g += data[p + 1]; light.b += data[p + 2]; light.n++;
      }
    }
  }
  function hex(c) {
    if (!c.n) return null;
    var r = Math.round(c.r / c.n);
    var g = Math.round(c.g / c.n);
    var b = Math.round(c.b / c.n);
    return '#' + [r, g, b].map(function (v) { return (v < 16 ? '0' : '') + v.toString(16); }).join('');
  }
  return { fg: hex(dark), bg: hex(light) };
}

function boundingBox(bits, w, h) {
  var minX = w, minY = h, maxX = -1, maxY = -1;
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      if (bits[y * w + x]) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  if (maxX < 0) return null;
  return { x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1 };
}

var DM_SIZES = [10, 12, 14, 16, 18, 20, 22, 24, 26, 32, 36, 40, 44, 48];

function analyzeDataMatrix(bits, w, h) {
  var box = boundingBox(bits, w, h);
  if (!box) return null;
  var topY = box.y;
  var runs = [];
  var x = box.x;
  var end = box.x + box.width;
  while (x < end) {
    var color = bits[topY * w + x];
    var start = x;
    while (x < end && bits[topY * w + x] === color) x++;
    runs.push(x - start);
  }
  var unit = runs.length ? Math.min.apply(null, runs) : 1;
  var estimate = Math.round(box.width / Math.max(unit, 1));
  var best = DM_SIZES[0];
  var bestDiff = Infinity;
  DM_SIZES.forEach(function (s) {
    var diff = Math.abs(s - estimate);
    if (diff < bestDiff) { bestDiff = diff; best = s; }
  });
  return { box: box, moduleSize: unit, size: best, estimate: estimate };
}

function analyze(image) {
  var w = image.width;
  var h = image.height;
  var gray = toGray(image);
  var bin = binarize(gray, w, h);
  var bits = bin.bits;

  var hits = [];
  for (var y = 2; y < h - 2; y += 2) findPatternInRow(bits, w, y, hits);

  var verified = [];
  hits.forEach(function (hit) {
    var v = verifyVertical(bits, w, h, hit.x, hit.y, hit.unit);
    if (v) verified.push({ x: hit.x, y: v.y, unit: (hit.unit + v.unit) / 2 });
    else verified.push(hit);
  });

  var groups = cluster(verified, Math.max(6, w * 0.01)).filter(function (g) { return g.items.length >= 2; });

  if (groups.length >= 3) {
    var pool = groups.slice().sort(function (a, b) { return b.items.length - a.items.length; }).slice(0, 8);
    var best = null;
    for (var i = 0; i < pool.length; i++) {
      for (var j = i + 1; j < pool.length; j++) {
        for (var k = j + 1; k < pool.length; k++) {
          var trio = [pool[i], pool[j], pool[k]];
          var sides = [dist(trio[1], trio[2]), dist(trio[0], trio[2]), dist(trio[0], trio[1])];
          var maxSide = Math.max(sides[0], sides[1], sides[2]);
          var minSide = Math.min(sides[0], sides[1], sides[2]);
          if (maxSide <= 0 || minSide / maxSide < 0.6) continue;
          var corners = orderCorners(trio);
          var moduleSize = (corners.tl.unit + corners.tr.unit + corners.bl.unit) / 3;
          if (!(moduleSize > 0)) continue;
          var sampled = sampleMatrix(bits, w, h, corners, moduleSize);
          var format = readFormat(sampled.matrix, sampled.dimension);
          var score = trio[0].items.length + trio[1].items.length + trio[2].items.length;
          if (format && validateQr(sampled.matrix, sampled.dimension) && (!best || score > best.score)) {
            best = {
              score: score,
              corners: corners,
              moduleSize: moduleSize,
              sampled: sampled,
              format: format
            };
          }
        }
      }
    }
    if (best) {
      var colors = averageColors(image, bits, w, h);
      return {
        ok: true,
        format: 'QR_CODE',
        version: best.sampled.version,
        mask: best.format.mask,
        ecl: best.format.ecl,
        dimension: best.sampled.dimension,
        moduleSize: best.moduleSize,
        quiet: estimateQuietRatio(bits, w, h, best.corners),
        fg: colors.fg,
        bg: colors.bg,
        box: boundingBox(bits, w, h),
        note: '已解析出版本、掩码与纠错级别'
      };
    }
  }

  var dm = analyzeDataMatrix(bits, w, h);
  if (dm) {
    var c2 = averageColors(image, bits, w, h);
    return {
      ok: true,
      format: 'DATA_MATRIX',
      symbolSize: dm.size,
      moduleSize: dm.moduleSize,
      fg: c2.fg,
      bg: c2.bg,
      box: dm.box,
      quiet: 0.1,
      note: '未识别到 QR 定位图案，按 Data Matrix 估算尺寸'
    };
  }

  return { ok: false, note: '未在图片中定位到可用码区' };
}

module.exports = {
  analyze: analyze,
  DM_SIZES: DM_SIZES
};
