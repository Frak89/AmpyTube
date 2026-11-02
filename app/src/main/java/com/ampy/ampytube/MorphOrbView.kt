package com.ampy.ampytube

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MorphOrbView(context:Context):View(context) {
    var intensity=0.65f
    private val pen=Paint(3).apply {
        color=Color.rgb(73,218,235)
        style=Paint.Style.STROKE
        strokeWidth=4f*resources.displayMetrics.density
    }
    private val path=Path()
    private var phase=0.0
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(visibility!=VISIBLE) return
        val r=min(width,height)*0.28
        val cx=width/2f; val cy=height/2f
        path.reset()
        for(i in 0..128) {
            val a=2*PI*i/128
            val distortion=1.0+intensity*0.20*sin(a*5+phase)+intensity*0.08*cos(a*9-phase*0.7)
            val x=(cx+r*distortion*cos(a)).toFloat()
            val y=(cy+r*distortion*sin(a)).toFloat()
            if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        path.close()
        canvas.drawPath(path,pen)
        phase+=0.045
        postInvalidateDelayed(30)
    }
}
