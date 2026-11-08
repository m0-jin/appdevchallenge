package io.crewmate.app

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

/** A preserved design concept, independent from the live Kotlin driver dashboard. */
class PreviewActivity:Activity(){
    private lateinit var web:WebView
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        web=WebView(this)
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.webViewClient=WebViewClient()
        setContentView(web)
        web.loadUrl("file:///android_asset/legacy-web-prototype.html")
    }
    override fun onDestroy(){web.destroy();super.onDestroy()}
}
