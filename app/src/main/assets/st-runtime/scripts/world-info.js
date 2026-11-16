/**
 * rikkaST 桥接层：Tavern Helper 产物里的 `../scripts/world-info.js` → 我们的 `st-compat/world-info.js`。
 *
 * 为什么需要：Tavern Helper 的 vite 配置把 `@sillytavern/*` 标成 external，
 * 产物里保留相对路径 import（如 `../scripts/utils.js`），期望运行时能解析到
 * ST 安装目录下的真实模块。我们没有 ST 目录树，所以用这层 re-export 把路径
 * 指到自研的 st-compat 实现。
 *
 * 约束：本文件只做 re-export，**不写任何逻辑**。所有行为在 st-compat 里，
 * 这样升级 Tavern Helper 时只需重新编译产物，桥接层不用动。
 */
export * from '../st-compat/world-info.js';
