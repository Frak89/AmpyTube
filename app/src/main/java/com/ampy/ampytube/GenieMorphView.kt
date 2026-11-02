package com.ampy.ampytube

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/** Mesh deformation to a horizontal strip, independent of the player's audio session. */
class GenieMorphView(context:Context, private var snapshot:Bitmap?):View(context) {
    private val nx=12; private val ny=12
    private val mesh=FloatArray((nx+1)*(ny+1)*2)
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var progress=0f
    override fun onDraw(c:Canvas) {
        val b=snapshot ?: return
        val p=progress.coerceIn(0f,1f)
        val e=p*p*(3-2*p)
        var i=0
        for(y in 0..ny) for(x in 0..nx) {
            val u=x.toFloat()/nx; val v=y.toFloat()/ny
            val wave=(sin((u*PI).toFloat())*sin((v*PI).toFloat()))
            mesh[i++]=width*u+(width*(.1f+.8f*u)-width*u)*e+wave*p*(1-p)*width*.1f
            mesh[i++]=height*v+(height*(.4f+.2f*v)-height*v)*e-wave*p*(1-p)*height*.25f
        }
        c.drawBitmapMesh(b,nx,ny,mesh,0,null,0,pen)
    }
    fun recycle() { snapshot?.recycle(); snapshot=null }
}
