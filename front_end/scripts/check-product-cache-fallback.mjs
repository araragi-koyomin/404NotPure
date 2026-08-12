import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const productView = readFileSync(resolve(scriptDirectory, '../src/views/product/Product.vue'), 'utf8');
const loadProductStart = productView.indexOf('async function loadProductData()');
const loadProductEnd = productView.indexOf('// 新增函数：将当前产品信息保存到sessionStorage', loadProductStart);

const failures = [];
if (loadProductStart < 0 || loadProductEnd < 0) {
  failures.push('无法定位 loadProductData，商品详情页结构可能已经改变');
}
const loadProductData = loadProductStart >= 0 && loadProductEnd >= 0
  ? productView.slice(loadProductStart, loadProductEnd)
  : '';
if (!loadProductData.includes("response?.status === 503") || !loadProductData.includes("response.data?.code === '503'")) {
  failures.push('商品详情页没有明确识别 HTTP 503 和业务码 503');
}
if (!loadProductData.includes("response.data.msg || '商品服务暂时繁忙，请稍后重试'")) {
  failures.push('商品详情页没有优先显示后端统一的暂时繁忙消息');
}
if (/setTimeout\s*\([^)]*getProductById|setInterval\s*\([^)]*getProductById/s.test(loadProductData)) {
  failures.push('商品详情加载函数对 503 增加了自动重试，会放大数据库保护期间的流量');
}
if (loadProductData.includes("ElMessage.error('系统错误：' + error)")) {
  failures.push('商品详情加载函数仍把技术错误对象直接拼进用户提示');
}

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log('商品详情 Redis 故障提示检查通过');
