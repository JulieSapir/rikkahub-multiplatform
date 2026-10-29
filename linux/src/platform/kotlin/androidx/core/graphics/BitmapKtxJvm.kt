package androidx.core.graphics

import android.graphics.Bitmap

/**
 * androidx core-ktx 里 Bitmap 扩展的桌面端版本，语义与同名扩展一致：
 * 上游 QRCode.kt 用 createBitmap(size, size) 建可变位图、this[x, y] = color 逐像素填色。
 */
fun createBitmap(width: Int, height: Int): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

operator fun Bitmap.set(x: Int, y: Int, argb: Int) = setPixel(x, y, argb)
