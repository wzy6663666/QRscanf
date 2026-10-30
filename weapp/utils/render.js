'use strict';

/**
 * Canvas 绘制：把 symbol.build 的结果画到 canvas 2d 上。
 */

function getCanvas(selector, scope) {
  return new Promise(function (resolve, reject) {
    var query = scope ? scope.createSelectorQuery() : wx.createSelectorQuery();
    query.select(selector).fields({ node: true, size: true }).exec(function (res) {
      var item = res && res[0];
      if (!item || !item.node) {
        reject(new Error('画布尚未准备好'));
        return;
      }
      resolve(item);
    });
  });
}

/**
 * @param {object} canvas 画布节点
 * @param {object} symbol symbol.build 结果
 * @param {object} cfg { size, quiet, fg, bg }
 */
function draw(canvas, symbol, cfg) {
  cfg = cfg || {};
  var ctx = canvas.getContext('2d');
  var size = cfg.size || 300;
  var quiet = cfg.quiet === undefined ? 4 : cfg.quiet;
  var fg = cfg.fg || '#000000';
  var bg = cfg.bg || '#FFFFFF';

  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = fg;

  if (symbol.kind === 'matrix') {
    var total = symbol.size + quiet * 2;
    var module = Math.floor(size / total);
    if (module < 1) module = 1;
    var drawn = module * total;
    var originX = Math.floor((size - drawn) / 2) + module * quiet;
    var originY = originX;
    for (var y = 0; y < symbol.size; y++) {
      var row = symbol.modules[y];
      var started = -1;
      for (var x = 0; x <= symbol.size; x++) {
        var dark = x < symbol.size ? row[x] : false;
        if (dark && started < 0) started = x;
        if (!dark && started >= 0) {
          ctx.fillRect(originX + started * module, originY + y * module, (x - started) * module, module);
          started = -1;
        }
      }
    }
    return { module: module, drawn: drawn };
  }

  var totalModules = symbol.width + quiet * 2;
  var barModule = Math.floor(size / totalModules);
  if (barModule < 1) barModule = 1;
  var usedWidth = barModule * totalModules;
  var startX = Math.floor((size - usedWidth) / 2) + barModule * quiet;
  var marginY = cfg.barMargin === undefined ? Math.round(size * 0.12) : cfg.barMargin;
  var barHeight = size - marginY * 2;

  var i = 0;
  while (i < symbol.row.length) {
    if (!symbol.row[i]) { i++; continue; }
    var run = 0;
    while (i + run < symbol.row.length && symbol.row[i + run]) run++;
    ctx.fillRect(startX + i * barModule, marginY, run * barModule, barHeight);
    i += run;
  }
  return { module: barModule, drawn: usedWidth };
}

module.exports = {
  getCanvas: getCanvas,
  draw: draw
};
