package com.github.yzheka.activityresults

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.gms.common.api.ResolvableApiException

class ActivityResults:Activity() {
    private val extraIntent:Intent? by lazy {
        @Suppress("RemoveExplicitTypeArguments")
        intent.getParcelableExtra<Intent?>(EXTRA_INTENT)
    }
    private val extraOptions:Bundle? by lazy {
        @Suppress("RemoveExplicitTypeArguments")
        intent.getParcelableExtra<Bundle?>(EXTRA_OPTIONS)
    }
    private val extraResultReceiver:ResultReceiver? by lazy {
        @Suppress("RemoveExplicitTypeArguments")
        intent.getParcelableExtra<ResultReceiver?>(EXTRA_RESULT_RECEIVER)
    }

    private val extraPermissions:Array<String> by lazy { intent.getStringArrayExtra(EXTRA_PERMISSIONS)?: emptyArray() }

    private val extraApiException:ResolvableApiException? by lazy { intent.getSerializableExtra(EXTRA_RESOLVABLE_API_EXCEPTION) as? ResolvableApiException? }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when{
            extraIntent!=null->startActivityForResult(extraIntent,0,extraOptions)
            extraPermissions.isNotEmpty()->ActivityCompat.requestPermissions(this,extraPermissions,0)
            extraApiException!=null->extraApiException!!.startResolutionForResult(this,0)
            else->finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted= hashSetOf<String>()
        val denied= hashSetOf<String>()
        val neverAsk= hashSetOf<String>()
        permissions.forEachIndexed { index, permission ->
            when{
                grantResults[index]==PackageManager.PERMISSION_GRANTED->granted+=permission
                ActivityCompat.shouldShowRequestPermissionRationale(this,permission)->denied+=permission
                else->neverAsk+=permission
            }
        }
        extraResultReceiver?.send(RESULT_OK, bundleOf(
            EXTRA_GRANTED_PERMISSIONS to granted.toTypedArray(),
            EXTRA_DENIED_PERMISSIONS to denied.toTypedArray(),
            EXTRA_NEVER_ASK_PERMISSIONS to neverAsk.toTypedArray()
        ))
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        extraResultReceiver?.send(resultCode, bundleOf(EXTRA_INTENT to data))
        finish()
    }

    companion object{
        private val mainHandler=Handler(Looper.getMainLooper())
        private const val EXTRA_INTENT="${BuildConfig.LIBRARY_PACKAGE_NAME}.INTENT"
        private const val EXTRA_OPTIONS="${BuildConfig.LIBRARY_PACKAGE_NAME}.OPTIONS"
        private const val EXTRA_RESULT_RECEIVER="${BuildConfig.LIBRARY_PACKAGE_NAME}.RESULT_RECEIVER"

        private const val EXTRA_PERMISSIONS="${BuildConfig.LIBRARY_PACKAGE_NAME}.PERMISSIONS"
        private const val EXTRA_GRANTED_PERMISSIONS="${BuildConfig.LIBRARY_PACKAGE_NAME}.GRANTED_PERMISSIONS"
        private const val EXTRA_DENIED_PERMISSIONS="${BuildConfig.LIBRARY_PACKAGE_NAME}.DENIED_PERMISSIONS"
        private const val EXTRA_NEVER_ASK_PERMISSIONS="${BuildConfig.LIBRARY_PACKAGE_NAME}.NEVER_ASK_PERMISSIONS"

        private const val EXTRA_RESOLVABLE_API_EXCEPTION="${BuildConfig.LIBRARY_PACKAGE_NAME}.RESOLVABLE_API_EXCEPTION"


        private fun resultReceiverOf(onResult:(Int,Bundle)->Unit):ResultReceiver = object : ResultReceiver(mainHandler){
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) = onResult(resultCode,resultData?:Bundle.EMPTY)
        }

        fun startResolutionForResult(error: ResolvableApiException,onResult: (Int, Intent) -> Unit){
            val intent=Intent(ContextProvider.context,ActivityResults::class.java)
            intent.putExtra(EXTRA_RESOLVABLE_API_EXCEPTION, error)
            intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiverOf { i, bundle ->
                onResult(i,bundle.getParcelable(EXTRA_INTENT)?: Intent())
            })
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ContextProvider.context.startActivity(intent)
        }

        fun requestPermissions(vararg permissions:String,onResult:(granted:Set<String>,denied:Set<String>,neverAsk:Set<String>)->Unit){
            requestPermissions(permissions.toSet(),onResult)
        }

        fun requestPermissions(permissions:Set<String>,onResult:(granted:Set<String>,denied:Set<String>,neverAsk:Set<String>)->Unit){
            val context=ContextProvider.context
            if(permissions.all { ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED }){
                onResult(HashSet(permissions), emptySet(), emptySet())
                return
            }
            val i=Intent(context,ActivityResults::class.java)
            i.putExtra(EXTRA_PERMISSIONS,permissions.toTypedArray())
            i.putExtra(EXTRA_RESULT_RECEIVER, resultReceiverOf { _, bundle ->
                val granted=bundle.getStringArray(EXTRA_GRANTED_PERMISSIONS)?.toSet()?: emptySet()
                val denied=bundle.getStringArray(EXTRA_DENIED_PERMISSIONS)?.toSet()?: emptySet()
                val neverAsk=bundle.getStringArray(EXTRA_NEVER_ASK_PERMISSIONS)?.toSet()?: emptySet()
                onResult(granted, denied, neverAsk)
            })
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }

        fun startActivityForResult(intent:Intent,options:Bundle?=null,callBack:(resultCode:Int,resultData:Intent)->Unit){
            val i=Intent(ContextProvider.context,ActivityResults::class.java)
            i.putExtra(EXTRA_INTENT,intent)
            i.putExtra(EXTRA_OPTIONS,options)
            i.putExtra(EXTRA_RESULT_RECEIVER, resultReceiverOf{code,data->callBack(code,data.getParcelable(EXTRA_INTENT)?: Intent())})
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ContextProvider.context.startActivity(i)
        }
    }
}