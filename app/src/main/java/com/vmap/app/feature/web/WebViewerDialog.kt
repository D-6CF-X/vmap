package com.vmap.app.feature.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 伪装成手机 Chrome：B 站会拦没有 UA 的 WebView，直接给个「页面无法打开」。 */
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * 内置浏览器：全屏 Dialog 包 WebView，用来在应用内看 B 站视频 / 演唱会回放。
 *
 * 踩过的坑，别再踩：
 * 1. `update` 里不能无条件 loadUrl —— progress/标题的 state 一变就重组，会反复重载，
 *    表现就是「闪一下然后白屏」。只在 url 真的变了才加载。
 * 2. 必须伪装 UA，否则 B 站直接返回无法打开的页面。
 * 3. 提供「用浏览器打开」出口：B 站有些页面（直播/番剧）就是不欢迎内嵌。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewerDialog(
    url: String,
    title: String = "",
    isDark: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var pageTitle by remember { mutableStateOf(title) }
    var failed by remember { mutableStateOf(false) }

    val openExternal = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val bg = if (isDark) Color(0xFF0F1115) else Color(0xFFF5F7FA)
        val fg = if (isDark) Color(0xFFEDF2F7) else Color(0xFF16202B)

        Column(modifier = Modifier.fillMaxSize().padding(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = fg)
                }
                Text(
                    text = pageTitle.ifBlank { title.ifBlank { "视频" } },
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { openExternal() }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "用浏览器打开", tint = fg)
                }
            }

            if (progress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Color(0xFF00A1D6),
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(
                                AndroidColor.parseColor(if (isDark) "#0F1115" else "#FFFFFF"),
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = MOBILE_UA
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    if (newProgress >= 100) failed = false
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank() && !title.startsWith("http")) {
                                        pageTitle = title
                                    }
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val target = request?.url ?: return false
                                    val scheme = target.scheme.orEmpty()
                                    // bilibili:// 之类的自家 scheme 交给系统（装了就跳 App）
                                    if (scheme != "http" && scheme != "https") {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, target))
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    // 只认主文档的失败，子资源挂了不算
                                    if (request?.isForMainFrame == true) failed = true
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?,
                                ) {
                                    if (request?.isForMainFrame == true &&
                                        (errorResponse?.statusCode ?: 0) >= 400
                                    ) {
                                        failed = true
                                    }
                                }
                            }
                        }
                    },
                    update = { view ->
                        // 只在目标 url 变了才加载，避免 progress/标题变化引发的重载死循环
                        if (view.tag != url) {
                            view.tag = url
                            failed = false
                            view.loadUrl(url)
                        }
                    },
                )

                if (failed) {
                    // 兜底：给一条能走的路，而不是把用户闷在白屏里
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "页面没能加载出来",
                            color = fg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "B 站有时会拒绝在应用内打开。可以换个方式：",
                            color = fg.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = openExternal) { Text("用浏览器打开", color = Color(0xFF00A1D6)) }
                            TextButton(onClick = onDismiss) { Text("关闭", color = fg.copy(alpha = 0.7f)) }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}
