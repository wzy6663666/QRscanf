'use strict';

/**
 * 码制元数据：与安卓版 BarcodeUtils / GeneratorFormat 保持一致
 */

var FORMATS = {
  QR_CODE: {
    key: 'QR_CODE',
    label: 'QR Code',
    full: 'QR Code（二维码）',
    color: '#5B6CF9',
    twoD: true,
    canGenerate: true,
    hint: '输入文本、网址、编号等内容'
  },
  DATA_MATRIX: {
    key: 'DATA_MATRIX',
    label: 'Data Matrix',
    full: 'Data Matrix（DM码）',
    color: '#0FB981',
    twoD: true,
    canGenerate: true,
    hint: '输入文本、编号或零件序列号'
  },
  PDF417: {
    key: 'PDF417',
    label: 'PDF417',
    full: 'PDF417',
    color: '#2F80ED',
    twoD: true,
    canGenerate: false,
    hint: '输入文本、证件信息或较长内容'
  },
  AZTEC: {
    key: 'AZTEC',
    label: 'Aztec',
    full: 'Aztec',
    color: '#8E54E9',
    twoD: true,
    canGenerate: false,
    hint: '输入文本、网址或票据信息'
  },
  CODE_128: {
    key: 'CODE_128',
    label: 'Code 128',
    full: 'Code 128（条形码）',
    color: '#F57C3D',
    twoD: false,
    canGenerate: true,
    hint: '输入英文、数字或 ASCII 字符'
  },
  CODE_39: {
    key: 'CODE_39',
    label: 'Code 39',
    full: 'Code 39（条形码）',
    color: '#F57C3D',
    twoD: false,
    canGenerate: true,
    hint: '仅支持大写字母、数字和 - . $ / + % 空格'
  },
  CODE_93: {
    key: 'CODE_93',
    label: 'Code 93',
    full: 'Code 93（条形码）',
    color: '#F57C3D',
    twoD: false,
    canGenerate: true,
    hint: '仅支持大写字母、数字和常用符号'
  },
  CODABAR: {
    key: 'CODABAR',
    label: 'Codabar',
    full: 'Codabar（条形码）',
    color: '#F57C3D',
    twoD: false,
    canGenerate: false,
    hint: '小程序端暂不支持生成，可正常识别'
  },
  EAN_13: {
    key: 'EAN_13',
    label: 'EAN-13',
    full: 'EAN-13（商品码）',
    color: '#F57C3D',
    twoD: false,
    canGenerate: true,
    hint: '输入 12 位数字（自动补校验位）或 13 位'
  },
  EAN_8: {
    key: 'EAN_8',
    label: 'EAN-8',
    full: 'EAN-8（商品码）',
    color: '#F57C3D',
    twoD: false,
    canGenerate: true,
    hint: '输入 7 位数字（自动补校验位）或 8 位'
  },
  ITF: {
    key: 'ITF',
    label: 'ITF',
    full: 'ITF（交叉二五码）',
    color: '#F57C3D',
    twoD: false,
    canGenerate: true,
    hint: '输入偶数位数字'
  },
  UPC_A: {
    key: 'UPC_A',
    label: 'UPC-A',
    full: 'UPC-A',
    color: '#F57C3D',
    twoD: false,
    canGenerate: false,
    hint: '小程序端暂不支持生成，可正常识别'
  },
  UPC_E: {
    key: 'UPC_E',
    label: 'UPC-E',
    full: 'UPC-E',
    color: '#F57C3D',
    twoD: false,
    canGenerate: false,
    hint: '小程序端暂不支持生成，可正常识别'
  },
  UNKNOWN: {
    key: 'UNKNOWN',
    label: '未知码制',
    full: '未知码制',
    color: '#64748B',
    twoD: false,
    canGenerate: false,
    hint: ''
  }
};

var SCAN_TYPE_MAP = {
  QR_CODE: 'QR_CODE',
  DATA_MATRIX: 'DATA_MATRIX',
  PDF_417: 'PDF417',
  AZTEC: 'AZTEC',
  CODE_128: 'CODE_128',
  CODE_39: 'CODE_39',
  CODE_93: 'CODE_93',
  CODABAR: 'CODABAR',
  EAN_13: 'EAN_13',
  EAN_8: 'EAN_8',
  ITF: 'ITF',
  UPC_A: 'UPC_A',
  UPC_E: 'UPC_E',
  WX_CODE: 'QR_CODE'
};

var GENERATOR_ORDER = ['QR_CODE', 'DATA_MATRIX', 'PDF417', 'AZTEC', 'CODE_128', 'CODE_39', 'CODE_93', 'EAN_13', 'EAN_8', 'ITF', 'CODABAR'];

function fromScanType(type) {
  return SCAN_TYPE_MAP[type] || 'UNKNOWN';
}

function get(key) {
  return FORMATS[key] || FORMATS.UNKNOWN;
}

function generatorFormats() {
  return GENERATOR_ORDER.map(function (key) { return FORMATS[key]; });
}

function label(key) {
  return get(key).label;
}

function fullLabel(key) {
  return get(key).full;
}

function color(key) {
  return get(key).color;
}

module.exports = {
  FORMATS: FORMATS,
  fromScanType: fromScanType,
  get: get,
  generatorFormats: generatorFormats,
  label: label,
  fullLabel: fullLabel,
  color: color
};
