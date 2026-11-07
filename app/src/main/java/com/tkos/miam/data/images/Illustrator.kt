package com.tkos.miam.data.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.tkos.miam.data.model.Protein
import com.tkos.miam.data.model.RecipeKind
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Illustration « flat » générée sur le téléphone pour une recette sans image (créée ici ou par l'assistant),
 * dans l'esprit des illustrations du catalogue : fond crème, contenant selon le type, couleurs selon la
 * protéine et les ingrédients, vapeur, couvert. Déterministe : même recette → même image.
 */
object Illustrator {
    private const val W = 1200
    private const val H = 800

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(Locale.FRENCH).replace("œ", "oe"), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Couleur dominante d'après les ingrédients / le nom, sinon la protéine. */
    private fun mainColor(name: String, ingredients: List<String>, protein: Protein, kind: RecipeKind): Int {
        val text = norm(name + " " + ingredients.joinToString(" "))
        val byWord = listOf(
            "chocolat" to 0xFF6E3119, "cafe" to 0xFF4A2612, "caramel" to 0xFFC8862E, "tomate" to 0xFFC9302C, "fraise" to 0xFFD63A3A,
            "framboise" to 0xFFC2185B, "cerise" to 0xFFB71C1C, "citron" to 0xFFF2D34B, "orange" to 0xFFF2A33A, "carotte" to 0xFFF2A33A,
            "potiron" to 0xFFE8892B, "butternut" to 0xFFE8892B, "epinard" to 0xFF3F8A34, "brocoli" to 0xFF3F8A34, "courgette" to 0xFF6BBF59,
            "haricot vert" to 0xFF4E9A3B, "petit pois" to 0xFF6BBF59, "avocat" to 0xFF8FD47A, "basilic" to 0xFF4E9A3B, "pesto" to 0xFF4E9A3B,
            "curry" to 0xFFE0A84A, "safran" to 0xFFE9A83A, "lentille" to 0xFFE8892B, "pois chiche" to 0xFFE0C27A, "riz" to 0xFFFFF8E6,
            "pate" to 0xFFF5D27E, "fromage" to 0xFFF2C84B, "creme" to 0xFFF6E3A0, "lait" to 0xFFFFFDF5, "vanille" to 0xFFF6E3A0,
            "banane" to 0xFFF2C94C, "pomme" to 0xFFF2D98A, "myrtille" to 0xFF4B3F8C, "betterave" to 0xFF8E1B4B, "aubergine" to 0xFF5D2C6B,
            "chou" to 0xFF9CCB7A, "poireau" to 0xFF8FD47A, "champignon" to 0xFFE9D8BE, "oeuf" to 0xFFF5CF4A, "miel" to 0xFFE9A83A,
        )
        for ((w, c) in byWord) if (text.contains(w)) return c.toInt()
        return when (protein) {
            Protein.BOEUF, Protein.AGNEAU -> 0xFF7A3A1C.toInt()
            Protein.VEAU, Protein.PORC -> 0xFFB07A4A.toInt()
            Protein.VOLAILLE -> 0xFFE0A84A.toInt()
            Protein.POISSON -> 0xFFF2A07A.toInt()
            Protein.VEGETARIEN -> 0xFF6BBF59.toInt()
            Protein.AUTRE -> when (kind) {
                RecipeKind.DESSERT, RecipeKind.GOUTER -> 0xFFF6E3A0.toInt()
                RecipeKind.BOISSON, RecipeKind.PETIT_DEJEUNER -> 0xFFFFF3C8.toInt()
                else -> 0xFFE8B657.toInt()
            }
        }
    }

    private fun darken(c: Int, f: Float = 0.75f): Int =
        Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    private fun lighten(c: Int, f: Float = 0.35f): Int =
        Color.rgb(Color.red(c) + ((255 - Color.red(c)) * f).toInt(), Color.green(c) + ((255 - Color.green(c)) * f).toInt(), Color.blue(c) + ((255 - Color.blue(c)) * f).toInt())

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = width; strokeCap = Paint.Cap.ROUND }

    fun render(name: String, ingredients: List<String>, protein: Protein, kind: RecipeKind): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rng = Random(norm(name).hashCode())
        val main = mainColor(name, ingredients, protein, kind)

        // Fond crème + bandes
        c.drawPaint(Paint().apply { shader = RadialGradient(600f, 360f, 820f, 0xFFFFF4E3.toInt(), 0xFFF0D8B8.toInt(), Shader.TileMode.CLAMP) })
        c.drawRect(0f, 0f, W.toFloat(), 40f, fill(0x14A5602A))
        c.drawRect(0f, H - 40f, W.toFloat(), H.toFloat(), fill(0x14A5602A))

        val hot = kind in listOf(RecipeKind.PLAT, RecipeKind.ACCOMPAGNEMENT) || (kind == RecipeKind.BOISSON && norm(name).let { it.contains("chaud") || it.contains("cafe") || it.contains("the") })
        when (kind) {
            RecipeKind.BOISSON, RecipeKind.PETIT_DEJEUNER -> drawMug(c, main)
            RecipeKind.DESSERT -> drawGlass(c, main, rng)
            RecipeKind.ACCOMPAGNEMENT, RecipeKind.ENTREE -> drawBowl(c, main, rng)
            else -> drawPlate(c, main, rng)
        }
        if (hot) drawSteam(c, 600f, 230f)
        drawFork(c, 1030f, 300f, 35f)
        return bmp
    }

    private fun shadow(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        val p = fill(0x47000000)
        p.maskFilter = android.graphics.BlurMaskFilter(30f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.drawOval(RectF(cx - rx + 10, cy - ry + 30, cx + rx + 10, cy + ry + 30), p)
    }

    private fun drawPlate(c: Canvas, main: Int, rng: Random) {
        val cx = 600f; val cy = 440f
        shadow(c, cx, cy, 430f, 245f)
        c.drawOval(RectF(cx - 430, cy - 245, cx + 430, cy + 245), Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, cy - 245, 0f, cy + 245, Color.WHITE, 0xFFECE6DC.toInt(), Shader.TileMode.CLAMP) })
        c.drawOval(RectF(cx - 430, cy - 245, cx + 430, cy + 245), stroke(0xFFD9CFC1.toInt(), 4f))
        c.drawOval(RectF(cx - 365, cy - 200, cx + 365, cy + 200), stroke(0xFFE6DDD0.toInt(), 3f))
        // Monticule principal + morceaux
        blob(c, cx, cy, 300f, 150f, main)
        pieces(c, cx, cy - 10f, 230f, 100f, 9, 60f, 40f, darken(main, 0.85f), rng)
        dots(c, cx, cy, 260f, 120f, 26, 4f, 9f, listOf(lighten(main, 0.5f), 0xFFFFF6D6.toInt()), rng)
        herbs(c, listOf(Triple(cx - 120f, cy - 70f, -20f), Triple(cx + 110f, cy + 60f, 30f)))
    }

    private fun drawBowl(c: Canvas, main: Int, rng: Random) {
        val cx = 600f; val cy = 420f; val rx = 290f; val ry = 92f; val depth = 185f
        shadow(c, cx, cy + depth, rx, ry)
        val body = Path().apply {
            moveTo(cx - rx, cy)
            cubicTo(cx - rx, cy + depth, cx - rx * 0.55f, cy + depth + 30, cx, cy + depth + 30)
            cubicTo(cx + rx * 0.55f, cy + depth + 30, cx + rx, cy + depth, cx + rx, cy)
            close()
        }
        c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, cy, 0f, cy + depth + 30, 0xFFF7F1E7.toInt(), 0xFFD8CFC0.toInt(), Shader.TileMode.CLAMP) })
        c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), fill(0xFFF7F1E7.toInt()))
        c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), stroke(0xFFC9BDA8.toInt(), 4f))
        c.drawOval(RectF(cx - rx + 26, cy - ry + 18, cx + rx - 26, cy + ry - 18), fill(main))
        pieces(c, cx, cy - 6f, 220f, 55f, 10, 46f, 30f, darken(main, 0.85f), rng)
        dots(c, cx, cy, 240f, 60f, 30, 3f, 7f, listOf(lighten(main, 0.5f), 0xFF3F8A34.toInt()), rng)
        herbs(c, listOf(Triple(cx - 90f, cy - 30f, -30f), Triple(cx + 100f, cy - 20f, 30f)))
    }

    private fun drawMug(c: Canvas, main: Int) {
        val cx = 600f; val cy = 400f; val rx = 190f; val ry = 60f; val depth = 260f
        shadow(c, cx, cy + depth, rx, ry)
        // anse
        val handle = Path().apply { moveTo(cx + rx + 10, cy + 60); cubicTo(cx + rx + 120, cy + 40, cx + rx + 120, cy + 190, cx + rx + 10, cy + 180) }
        c.drawPath(handle, stroke(0xFFE8E0D4.toInt(), 34f))
        val body = Path().apply {
            moveTo(cx - rx, cy); lineTo(cx - rx + 20, cy + depth)
            arcTo(RectF(cx - rx + 20, cy + depth - (ry - 12), cx + rx - 20, cy + depth + (ry - 12)), 180f, -180f)
            lineTo(cx + rx, cy); close()
        }
        c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, cy, 0f, cy + depth + ry, 0xFFF7F1E7.toInt(), 0xFFD8CFC0.toInt(), Shader.TileMode.CLAMP) })
        c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), fill(0xFFF7F1E7.toInt()))
        c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), stroke(0xFFC9BDA8.toInt(), 4f))
        c.drawOval(RectF(cx - rx + 22, cy - ry + 16, cx + rx - 22, cy + ry - 16), fill(main))
        c.drawOval(RectF(cx - 70, cy - 14, cx + 10, cy + 2), fill(lighten(main, 0.6f)))
    }

    private fun drawGlass(c: Canvas, main: Int, rng: Random) {
        val cx = 600f; val cy = 330f; val rx = 125f; val ry = 40f; val depth = 300f
        val top = cy + depth * 0.15f
        shadow(c, cx, cy + depth, rx, ry)
        val liquid = Path().apply {
            moveTo(cx - rx + 12, top); lineTo(cx - rx + 16, cy + depth)
            arcTo(RectF(cx - rx + 16, cy + depth - (ry - 10), cx + rx - 16, cy + depth + (ry - 10)), 180f, -180f)
            lineTo(cx + rx - 12, top); close()
        }
        c.drawPath(liquid, fill(main))
        c.drawOval(RectF(cx - rx + 12, top - (ry - 6), cx + rx - 12, top + (ry - 6)), fill(lighten(main, 0.25f)))
        c.drawOval(RectF(cx - 70, top - 14, cx + 20, top + 8), fill(0xF0FFF8EA.toInt()))
        dots(c, cx, top, 80f, 16f, 10, 2f, 4f, listOf(darken(main, 0.6f)), rng)
        val glass = Path().apply {
            moveTo(cx - rx, cy); lineTo(cx - rx + 16, cy + depth)
            arcTo(RectF(cx - rx + 16, cy + depth - (ry - 10), cx + rx - 16, cy + depth + (ry - 10)), 180f, -180f)
            lineTo(cx + rx, cy); close()
        }
        c.drawPath(glass, fill(0x47FFFFFF))
        c.drawLine(cx - rx, cy, cx - rx + 16, cy + depth, stroke(0xB3FFFFFF.toInt(), 5f))
        c.drawLine(cx + rx, cy, cx + rx - 16, cy + depth, stroke(0xCCD9CFC1.toInt(), 4f))
        c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), stroke(0xFFD9CFC1.toInt(), 4f))
        drawSpoon(c, 1000f, 330f, 30f)
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        shadowSmall(c, cx, cy, rx, ry)
        c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), fill(color))
        c.drawOval(RectF(cx - rx * 0.6f, cy - ry * 0.75f, cx + rx * 0.4f, cy - ry * 0.2f), fill(lighten(color, 0.3f)))
    }

    private fun shadowSmall(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        val p = fill(0x40000000)
        p.maskFilter = android.graphics.BlurMaskFilter(14f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.drawOval(RectF(cx - rx + 6, cy - ry + 14, cx + rx + 6, cy + ry + 14), p)
    }

    private fun pieces(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, n: Int, w: Float, h: Float, color: Int, rng: Random) {
        repeat(n) {
            val a = rng.nextDouble(0.0, 2 * PI); val d = kotlin.math.sqrt(rng.nextDouble())
            val x = (cx + cos(a) * rx * d).toFloat(); val y = (cy + sin(a) * ry * d).toFloat()
            val rot = rng.nextInt(0, 90).toFloat()
            c.save(); c.rotate(rot, x, y)
            c.drawRoundRect(RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2), 10f, 10f, fill(color))
            c.drawRoundRect(RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2), 10f, 10f, stroke(darken(color, 0.7f), 3f))
            c.restore()
        }
    }

    private fun dots(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, n: Int, rmin: Float, rmax: Float, colors: List<Int>, rng: Random) {
        repeat(n) {
            val a = rng.nextDouble(0.0, 2 * PI); val d = kotlin.math.sqrt(rng.nextDouble())
            val x = (cx + cos(a) * rx * d).toFloat(); val y = (cy + sin(a) * ry * d).toFloat()
            c.drawCircle(x, y, rng.nextDouble(rmin.toDouble(), rmax.toDouble()).toFloat(), fill(colors[rng.nextInt(colors.size)]))
        }
    }

    private fun herbs(c: Canvas, points: List<Triple<Float, Float, Float>>) {
        val p = fill(0xFF4E9A3B.toInt())
        for ((x, y, r) in points) {
            c.save(); c.rotate(r, x, y); c.drawOval(RectF(x - 14, y - 7, x + 14, y + 7), p); c.restore()
            c.save(); c.rotate(r + 60, x + 14, y - 6); c.drawOval(RectF(x + 3, y - 12, x + 25, y), p); c.restore()
        }
    }

    private fun drawSteam(c: Canvas, cx: Float, y: Float) {
        val p = stroke(0x8CFFFFFF.toInt(), 10f)
        for (i in 0 until 3) {
            val x = cx + (i - 1) * 60f
            val path = Path().apply { moveTo(x, y); cubicTo(x - 20, y - 30, x + 20, y - 50, x, y - 80) }
            c.drawPath(path, p)
        }
    }

    private fun drawFork(c: Canvas, x: Float, y: Float, rot: Float) {
        c.save(); c.rotate(rot, x, y)
        val f = fill(0xFFC8CCD2.toInt()); val s = stroke(0xFF9AA0A8.toInt(), 3f)
        c.drawRoundRect(RectF(x - 9, y - 10, x + 9, y + 290), 9f, 9f, f); c.drawRoundRect(RectF(x - 9, y - 10, x + 9, y + 290), 9f, 9f, s)
        val head = Path().apply {
            moveTo(x - 34, y - 100); lineTo(x - 34, y - 10); quadTo(x - 34, y + 10, x - 14, y + 10); lineTo(x + 14, y + 10); quadTo(x + 34, y + 10, x + 34, y - 10)
            lineTo(x + 34, y - 100); lineTo(x + 20, y - 100); lineTo(x + 20, y - 30); lineTo(x + 8, y - 30); lineTo(x + 8, y - 100); lineTo(x - 8, y - 100); lineTo(x - 8, y - 30); lineTo(x - 20, y - 30); lineTo(x - 20, y - 100); close()
        }
        c.drawPath(head, f); c.drawPath(head, s)
        c.restore()
    }

    private fun drawSpoon(c: Canvas, x: Float, y: Float, rot: Float) {
        c.save(); c.rotate(rot, x, y)
        val f = fill(0xFFC8CCD2.toInt()); val s = stroke(0xFF9AA0A8.toInt(), 3f)
        c.drawRoundRect(RectF(x - 9, y - 10, x + 9, y + 280), 9f, 9f, f); c.drawRoundRect(RectF(x - 9, y - 10, x + 9, y + 280), 9f, 9f, s)
        c.drawOval(RectF(x - 34, y - 112, x + 34, y - 8), f); c.drawOval(RectF(x - 34, y - 112, x + 34, y - 8), s)
        c.restore()
    }

    /** Rend et enregistre en JPEG. */
    fun renderToFile(file: File, name: String, ingredients: List<String>, protein: Protein, kind: RecipeKind) {
        val bmp = render(name, ingredients, protein, kind)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        bmp.recycle()
    }
}
