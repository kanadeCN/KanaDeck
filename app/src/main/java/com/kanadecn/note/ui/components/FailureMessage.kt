package com.kanadecn.note.ui.components

import com.kanadecn.note.data.remote.FailureReason

fun failureMessage(reason: FailureReason): String = when (reason) {
    FailureReason.INVALID_URL -> "Base URL 格式不正确"
    FailureReason.INSECURE_URL -> "只允许 https:// 地址"
    FailureReason.UNAUTHORIZED -> "认证失败：API Key 无效或无权限"
    FailureReason.NOT_FOUND -> "地址不对（404），检查服务商"
    FailureReason.BAD_REQUEST -> "请求被拒绝（400），检查模型"
    FailureReason.RATE_LIMIT -> "请求过于频繁（429），稍后再试"
    FailureReason.SERVER -> "服务端错误（5xx），稍后再试"
    FailureReason.TIMEOUT -> "连接超时，检查网络"
    FailureReason.NETWORK -> "网络错误，检查网络"
    FailureReason.UNKNOWN -> "未知错误"
}
