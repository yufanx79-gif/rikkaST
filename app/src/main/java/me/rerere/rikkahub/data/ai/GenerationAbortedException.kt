package me.rerere.rikkahub.data.ai

/**
 * 生成在「发出请求之前」就被中止。
 *
 * 存在的意义：历史上有若干条路径（扩展拦截、工具实现缺失、步骤耗尽等）只 `return`/`break`，
 * flow 正常完成 -> ChatService 走 onSuccess -> 日志记 SUCCESS，用户侧看到的是
 * 「转圈消失 + 什么都没发生 + 没有报错」。抛出本异常后由 ChatService.onFailure 统一 addError，
 * 把这批静默失败变成用户可见的错误。
 */
class GenerationAbortedException(
    val reason: String,
    cause: Throwable? = null,
) : IllegalStateException(reason, cause)
