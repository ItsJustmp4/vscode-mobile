package com.vscode.mobile

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    
    private val vsCodeUrl = "https://vscode.dev"
    private val openVsxUrl = "https://open-vsx.org"

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this)
        webView = WebView(this)
        rootLayout.addView(webView)

        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_agenda)
            alpha = 0.75f
            setOnClickListener { showControlMenu() }
        }
        val fabParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 48, 48)
        }
        rootLayout.addView(fab, fabParams)
        setContentView(rootLayout)

        configureWebView()

        if (savedInstanceState == null) {
            webView.loadUrl(vsCodeUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportMultipleWindows(false)

        settings.userAgentString = settings.userAgentString.replace("; wv", "")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback = callback
                filePickerLauncher.launch("*/*")
                return true
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    val cookies = CookieManager.getInstance().getCookie(url)
                    addRequestHeader("cookie", cookies)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Загрузка расширения...")
                    setTitle(filename)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Загрузка началась: $filename", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showControlMenu() {
        val options = arrayOf(
            "📦 Открыть Open VSX Marketplace",
            "💻 Вернуться в VS Code",
            "📥 Как установить плагин (.vsix)?",
            "🌐 Подключить свой сервер (code-server)"
        )

        AlertDialog.Builder(this)
            .setTitle("Плагины и управление")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> webView.loadUrl(openVsxUrl)
                    1 -> webView.loadUrl(vsCodeUrl)
                    2 -> showInstallGuide()
                    3 -> showCustomServerDialog()
                }
            }
            .show()
    }

    private fun showInstallGuide() {
        AlertDialog.Builder(this)
            .setTitle("Установка расширений")
            .setMessage("1. Скачайте .vsix через Open VSX.\n" +
                        "2. В VS Code откройте панель расширений (Ctrl+Shift+X).\n" +
                        "3. Нажмите меню '...' -> 'Install from VSIX...'\n" +
                        "4. Выберите скачанный файл из папки Загрузки.")
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showCustomServerDialog() {
        val input = EditText(this).apply { hint = "http://ip:8080" }
        AlertDialog.Builder(this)
            .setTitle("Свой code-server")
            .setView(input)
            .setPositiveButton("Подключить") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) webView.loadUrl(url)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}