package com.robining.games.frame.utils

import android.annotation.SuppressLint
import com.robining.games.frame.R
import com.robining.games.frame.startup.StartUpContext
import nl.dionsegijn.konfetti.KonfettiView
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size

@SuppressLint("UseCompatLoadingForDrawables")
object ParticleUtil {
    val hintShapes by lazy {
        val ids = arrayOf(
            R.mipmap.co01,
            R.mipmap.co02,
            R.mipmap.co03,
            R.mipmap.co04,
            R.mipmap.co05,
            R.mipmap.co06,
        )
        val context = StartUpContext.context
        Array(ids.size) {
            Shape.DrawableShape(
                context.resources.getDrawable(
                    ids[it],
                    context.theme
                ),
                false
            )
        }
    }
    val shapeDrawables by lazy {
        val ids = arrayOf(
//            R.mipmap.c1,
//            R.mipmap.c2,
//            R.mipmap.c3,
//            R.mipmap.c4,
//            R.mipmap.c5,
//            R.mipmap.c6,
//            R.mipmap.c7,
//            R.mipmap.c8,
            R.mipmap.c9,
            R.mipmap.c10,
            R.mipmap.c11,
            R.mipmap.c12,
            R.mipmap.c13,
            R.mipmap.c14,
            R.mipmap.c15,
            R.mipmap.c16,
            R.mipmap.c17,
            R.mipmap.c18,
            R.mipmap.c19,
            R.mipmap.c20,
            R.mipmap.c21,
            R.mipmap.c22,
            R.mipmap.c23,
            R.mipmap.c24,
            R.mipmap.c25
        )
        val context = StartUpContext.context
        Array(ids.size) {
            Shape.DrawableShape(
                context.resources.getDrawable(
                    ids[it],
                    context.theme
                ),
                false
            )
        }
    }

    fun show(view: KonfettiView, percentY: Float) {
        view.post {
            view.build()
                .setDirection(270.0, 340.0)
                .setSpeed(0f, 10f)
                .setFadeOutEnabled(true)
                .setSpeedDensityIndependent(true)
                .setTimeToLive(2000L)
                .addShapes(*shapeDrawables)
                .addSizes(Size(14, 3f), Size(17, 3.5f), Size(12, 2.5f), Size(10, 2f))
                .setPosition(
                    -100f,
                    view.height * percentY
                )
                .burst(100)


            view.build()
                .setDirection(190.0, 270.0)
                .setSpeed(0f, 10f)
                .setFadeOutEnabled(true)
                .setSpeedDensityIndependent(true)
                .setTimeToLive(2000L)
                .addShapes(*shapeDrawables)
                .addSizes(Size(14, 3f), Size(17, 3.5f), Size(12, 2.5f), Size(10, 2f))
                .setPosition(
                    (view.width + 100).toFloat(),
                    view.height * percentY
                )
                .burst(100)
        }
    }


    fun showHint(view: KonfettiView, x: Float, y: Float) {
        view.post {
            view.build()
                .setDirection(0.0, 360.0)
                .setSpeed(2f, 2f)
                .setGravity(0f)
                .setFadeOutEnabled(true)
                .setSpeedDensityIndependent(false)
                .setTimeToLive(100L)
                .setAccelerationEnabled(false)
                .setRotationEnabled(false)
                .addShapes(*hintShapes)
                .addSizes(Size(6, 10f), Size(12, 10f), Size(3, 10f), Size(1, 10f))
                .setPosition(
                    x,
                    y
                )
                .streamFor(100, 200)
        }
    }
}