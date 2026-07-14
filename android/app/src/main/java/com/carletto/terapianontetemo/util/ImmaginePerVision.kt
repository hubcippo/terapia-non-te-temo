package com.carletto.terapianontetemo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Lanciata quando la foto scelta non è decodificabile. Messaggio già in italiano, mostrabile in UI. */
class ImmagineIlleggibileException(msg: String) : IOException(msg)

/**
 * Prepara l'immagine della ricetta per il client vision (CONTRACT sez. 9):
 * decodifica l'Uri, ruota secondo l'EXIF se serve, riduce il lato lungo
 * a max 2048 px e comprime in JPEG qualità 85.
 *
 * Usa android.media.ExifInterface del framework (niente androidx.exifinterface,
 * non presente nel catalog) e BitmapFactory con inSampleSize per non caricare
 * in memoria l'immagine a piena risoluzione.
 */
object ImmaginePerVision {

    private const val LATO_MAX = 2048
    private const val QUALITA_JPEG = 85
    private const val MSG_ILLEGGIBILE =
        "Non riesco a leggere la foto. Riprova o scegli un'altra immagine."

    /**
     * @throws ImmagineIlleggibileException (sottotipo di IOException, messaggio
     *         in italiano) se l'immagine non è leggibile.
     */
    fun prepara(context: Context, uri: Uri): ByteArray {
        var bitmap = decodificaRidotta(context, uri, LATO_MAX)
            ?: throw ImmagineIlleggibileException(MSG_ILLEGGIBILE)

        // Ridimensionamento esatto se il lato lungo supera ancora LATO_MAX.
        val latoAttuale = maxOf(bitmap.width, bitmap.height)
        if (latoAttuale > LATO_MAX) {
            val scala = LATO_MAX.toFloat() / latoAttuale
            val ridotta = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scala).toInt().coerceAtLeast(1),
                (bitmap.height * scala).toInt().coerceAtLeast(1),
                true
            )
            if (ridotta != bitmap) bitmap.recycle()
            bitmap = ridotta
        }

        // Compressione JPEG.
        val uscita = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITA_JPEG, uscita)
        bitmap.recycle()
        return uscita.toByteArray()
    }

    /**
     * Decodifica l'Uri con inSampleSize (lato lungo circa [latoTarget], mai sotto)
     * e applica la rotazione EXIF. Null se l'immagine non è leggibile.
     * Usata sia da [prepara] sia dalla miniatura della schermata Conferma.
     */
    fun decodificaRidotta(context: Context, uri: Uri, latoTarget: Int): Bitmap? = runCatching {
        val resolver = context.contentResolver

        // 1) Solo dimensioni, senza caricare i pixel.
        //    NB: con inJustDecodeBounds decodeStream ritorna sempre null:
        //    la leggibilità si verifica su outWidth/outHeight.
        val limiti = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val flussoLimiti = resolver.openInputStream(uri) ?: return null
        flussoLimiti.use { flusso ->
            BitmapFactory.decodeStream(flusso, null, limiti)
        }
        if (limiti.outWidth <= 0 || limiti.outHeight <= 0) return null

        // 2) inSampleSize: massima potenza di 2 che mantiene il lato lungo >= latoTarget.
        val latoLungo = maxOf(limiti.outWidth, limiti.outHeight)
        var campionamento = 1
        while (latoLungo / (campionamento * 2) >= latoTarget) {
            campionamento *= 2
        }

        // 3) Decodifica vera e propria.
        val opzioni = BitmapFactory.Options().apply { inSampleSize = campionamento }
        val bitmap = resolver.openInputStream(uri)?.use { flusso ->
            BitmapFactory.decodeStream(flusso, null, opzioni)
        } ?: return null

        // 4) Rotazione secondo l'EXIF (le foto da fotocamera arrivano spesso ruotate).
        val gradi = gradiRotazioneExif(context, uri)
        if (gradi == 0f) {
            bitmap
        } else {
            val matrice = Matrix().apply { postRotate(gradi) }
            val ruotata = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrice, true
            )
            if (ruotata != bitmap) bitmap.recycle()
            ruotata
        }
    }.getOrNull()

    /** Legge l'orientamento EXIF dall'Uri; 0 se assente o non leggibile. */
    private fun gradiRotazioneExif(context: Context, uri: Uri): Float {
        val orientamento = runCatching {
            context.contentResolver.openInputStream(uri)?.use { flusso ->
                ExifInterface(flusso).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        return when (orientamento) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }
}
