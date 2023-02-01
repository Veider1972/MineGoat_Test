package ru.veider.minegoattest

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.*


class RealmDataStorage() {}

interface FileManager {
    val pickResult: PublishSubject<List<FileManager.Document>>
    fun pickFiles()                                          // Запуск интента на выбор файлов для добавления в профиль
    fun registerContract(activity: AppCompatActivity)        // Регистрация контракта в методе onCreate активности
    fun getFile(name: String): Single<Uri>                   // Получение Uri файла по его имени
    fun getFiles(filesList: List<String>): Single<List<Uri>> // Получение списка Uri файлов по их имени

    data class Document(
        val type: FileManagerImpl.MediaType,
        val name: String,
        val uri: Uri
    )
}

class FileManagerImpl(private val context: Context) : FileManager {

    private val localDir = context.filesDir
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    enum class MediaType {
        IMAGE, VIDEO, PDF, WORD, OTHER;

        companion object {
            fun getTypeFromExt(ext: String) =
                    when (ext.lowercase()) {
                        "jpg", "tif", "png"  -> IMAGE
                        "pdf"                -> PDF
                        "doc", "docx", "rtf" -> WORD
                        "mp4", "avi"         -> VIDEO
                        else                 -> OTHER
                    }
        }
    }

    private val _pickResult: PublishSubject<List<FileManager.Document>> = PublishSubject.create()
    override val pickResult get() = _pickResult

    // Регистрация контракта на выбор файлов
    override fun registerContract(activity: AppCompatActivity) {
        resultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                try {
                    result.data?.run {
                        val imagesEncodedList = ArrayList<Pair<String, Uri>>()

                        data?.run {
                            imagesEncodedList.add(Pair(getFileName(this), this))
                        } ?: clipData?.run {
                            for (i in 0 until this.itemCount) {
                                val uri = this.getItemAt(i).uri
                                imagesEncodedList.add(Pair(getFileName(uri), uri))
                            }
                        }
                        _pickResult.onNext(copyMediaToLocalDir(imagesEncodedList))
                    }

                } catch (e: Exception) {
                    _pickResult.onError(e)
                    Toast.makeText(activity, "Something went wrong", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun copyMediaToLocalDir(filesList: ArrayList<Pair<String, Uri>>): List<FileManager.Document> {
        val docList = mutableListOf<FileManager.Document>()
        for (i in filesList.indices) {
            val ext = filesList[i].first.substringAfterLast('.', "")
            val name = UUID.randomUUID().toString()
            val fileName = "$name.$ext"
            if (copyFile(filesList[i].second, fileName))
                docList.add(FileManager.Document(type = MediaType.getTypeFromExt(ext), name = fileName, uri = Uri.fromFile(File(localDir, fileName))))
        }
        return docList.toList()
    }

    @SuppressLint("Recycle")
    private fun getFileName(uri: Uri): String {
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.run {
                if (moveToFirst()) {
                    val columnIndex = getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    return getString(columnIndex)
                }
            }
        }
        return uri.path?.substring((uri.path?.lastIndexOf('/') ?: 0) + 1) ?: ""
    }

    private fun copyFile(src: Uri, dst: String): Boolean {
        val inputStream = context.contentResolver.openInputStream(src)
        val outputStream = context.contentResolver.openOutputStream(Uri.fromFile(File(localDir, dst)))
        var retValue = false
        if (inputStream != null && outputStream != null) {
            try {
                inputStream.copyTo(outputStream)
                retValue = true
            } catch (e: IOException) {
                Toast.makeText(context, "File coping error", Toast.LENGTH_LONG)
                    .show()
            } finally {
                inputStream.close()
            }
        }
        return retValue
    }

    // Запуск интента на выбор файлов для добавления в профиль
    private val pickIntent by lazy {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val mimeTypes =
                    arrayOf("image/jpg", "image/tiff", "image/png", "application/pdf", "application/doc", "application/docx", "application/rtf",
                            "video/mp4",
                            "video/avi"
                    )
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            action = Intent.ACTION_GET_CONTENT
        }
    }

    override fun pickFiles() {
        if (this::resultLauncher.isInitialized)
            resultLauncher.launch(Intent.createChooser(pickIntent, "Select Picture"))
    }

    // Получение Uri файла по его имени
    override fun getFile(file: String): Single<Uri> {
        val name = file.substringAfterLast('/', "")
        if (name.isNotEmpty()) {
            if (!isFileCached(name))
                download(file, File(localDir, name))
            return Single.just(Uri.fromFile(File(localDir, name)))
        }
        return Single.just(Uri.EMPTY)
    }

    // Получение Uri файла по его имени
    override fun getFiles(filesList: List<String>): Single<List<Uri>> {
        val outList = mutableListOf<Uri>()
        filesList.forEach { file ->
            val name = file.substringAfterLast('/', "")
            if (name.isNotEmpty()) {
                if (!isFileCached(name))
                    download(file, File(localDir, name))
                outList.add(Uri.fromFile(File(localDir, name)))
            } else
                outList.add(Uri.EMPTY)
        }
        return Single.just(outList)
    }

    private fun isFileCached(name: String): Boolean = File(localDir, name).exists()

    private fun download(link: String, outFile: File) {
        URL(link).openStream().use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        Log.d("TAG", "Downloading finished")
    }


}
