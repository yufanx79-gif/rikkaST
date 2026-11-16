/**
 * rikkaST st-compat: showdown-exclusion.js
 * SillyTavern 1.19.0 full module & export parity shim.
 */
/**
 * [v247 BUGFIX] ST 里这是**函数**（showdown 扩展注册器），我们导出成了空对象 `{}`。
 * 上游 `showdown.setOption('extensions', [...])` 会把它当扩展实例调用 → "is not a function"。
 * 这里返回一个符合 showdown 扩展契约的最小对象（type + listeners），
 * 语义等价于「不做任何额外处理」—— 但**可被调用**。
 */
export function markdownExclusionExt() {
    return { type: 'lang', filter: (text) => String(text ?? '') };
}
