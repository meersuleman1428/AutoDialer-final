package com.itskills.pcdautodial

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.*

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pw: WebView? = null
    private var cb: ValueCallback<Array<Uri>>? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url.toString()
                if (u.startsWith("file:///android_asset")) return false
                openExt(u)
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, c: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                cb?.onReceiveValue(null)
                cb = c
                val i = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                startActivityForResult(i, 7)
                return true
            }
        }
        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc == 7) {
            val u = d?.data
            cb?.onReceiveValue(if (res == RESULT_OK && u != null) arrayOf(u) else null)
            cb = null
        }
    }

    private fun openExt(u: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (e: Exception) {}
    }

    inner class Bridge {
        @JavascriptInterface
        fun call(n: String) {
            runOnUiThread {
                val uri = Uri.parse("tel:" + Uri.encode(n))
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                    startActivity(Intent(Intent.ACTION_CALL, uri))
                else {
                    requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
                    startActivity(Intent(Intent.ACTION_DIAL, uri))
                }
            }
        }
        @JavascriptInterface
        fun open(u: String) { runOnUiThread { openExt(u) } }
        @JavascriptInterface
        fun share(t: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t)
                startActivity(Intent.createChooser(i, "Share"))
            }
        }
        @JavascriptInterface
        fun setReminders(j: String) { Rem.save(this@MainActivity, j) }
        @JavascriptInterface
        fun printHtml(h: String) {
            runOnUiThread {
                val w = WebView(this@MainActivity)
                pw = w
                w.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, u: String) {
                        val pm = getSystemService(PRINT_SERVICE) as PrintManager
                        pm.print("PCD Report", v.createPrintDocumentAdapter("PCD Report"), PrintAttributes.Builder().build())
                    }
                }
                w.loadDataWithBaseURL(null, h, "text/html", "utf-8", null)
            }
        }
    }
}
