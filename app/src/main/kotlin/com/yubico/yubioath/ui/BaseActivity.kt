package com.yubico.yubioath.ui

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.SharedPreferences
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import android.util.Log
import android.view.WindowManager
import com.yubico.yubioath.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import nordpol.android.OnDiscoveredTagListener
import nordpol.android.TagDispatcher
import nordpol.android.TagDispatcherBuilder
import org.jetbrains.anko.toast
import kotlin.coroutines.CoroutineContext

abstract class BaseActivity<T : BaseViewModel>(private var modelClass: Class<T>) : AppCompatActivity(), CoroutineScope {
    protected lateinit var viewModel: T
    private lateinit var tagDispatcher: TagDispatcher
    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        job = Job()

        if (prefs.getBoolean("hideThumbnail", true)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        viewModel = ViewModelProviders.of(this).get(modelClass)

        tagDispatcher = TagDispatcherBuilder(this, OnDiscoveredTagListener {
            try {
                viewModel.start(this)
                viewModel.nfcConnected(it)
            } catch (e: Exception) {
                Log.e("yubioath", "Error using NFC device", e)
            }
        }).enableReaderMode(prefs.getBoolean("useNfcReaderMode", false)).enableUnavailableNfcUserPrompt(false).build()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        tagDispatcher.interceptIntent(intent)
    }

    @SuppressLint("NewApi")
    public override fun onPause() {
        super.onPause()
        tagDispatcher.disableExclusiveNfc()
        viewModel.stop()
    }

    @SuppressLint("NewApi")
    public override fun onResume() {
        super.onResume()
        viewModel.start(this)

        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED && !viewModel.ndefConsumed) {
            if(prefs.getBoolean("readNdefData", false)) {
                viewModel.ndefIntentData = (intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0] as NdefMessage).toByteArray()
            }
            viewModel.ndefConsumed = true
            tagDispatcher.interceptIntent(intent)
        }

        val warnNfc = prefs.getBoolean("warnNfc", true) && !viewModel.nfcWarned
        when (tagDispatcher.enableExclusiveNfc()) {
            TagDispatcher.NfcStatus.AVAILABLE_DISABLED -> {
                if (warnNfc) {
                    toast(R.string.nfc_off)
                    viewModel.nfcWarned = true
                }
            }
            TagDispatcher.NfcStatus.NOT_AVAILABLE -> {
                if (warnNfc) {
                    toast(R.string.no_nfc)
                    viewModel.nfcWarned = true
                }
            }
            else -> Unit
        }
    }
}