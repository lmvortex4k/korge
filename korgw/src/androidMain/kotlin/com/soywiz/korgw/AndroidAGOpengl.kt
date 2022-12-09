package com.soywiz.korgw

import android.content.*
import com.soywiz.kgl.*
import com.soywiz.korag.gl.*

class AndroidAGOpengl constructor(
    val androidContext: Context,
    val agCheck: Boolean = false,
    val mGLView: (() -> KorgwSurfaceView?)? = null,
) : AGOpengl() {
    //override val gl: KmlGl = CheckErrorsKmlGlProxy(KmlGlAndroid())
    override val gl: KmlGl = KmlGlAndroid { mGLView?.invoke()?.clientVersion ?: -1 }.checkedIf(agCheck).logIf(agCheck)
    override val nativeComponent: Any get() = this@AndroidAGOpengl

    override fun repaint() {
        mGLView?.invoke()?.invalidate()
    }

    init {
        println("KorgwActivityAGOpengl: Created ag $this for ${this@AndroidAGOpengl} with gl=$gl")
    }
}
