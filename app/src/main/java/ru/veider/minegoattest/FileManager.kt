package ru.veider.minegoattest

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.*
import javax.net.ssl.*


interface FileManager {
    val pickResult: PublishSubject<List<Document>>
    fun pickFiles()                                                                 // Запуск интента на выбор файлов для добавления в профиль
    fun registerContract(activity: AppCompatActivity)                               // Регистрация контракта в методе onCreate активности
    fun getFile(file: String, isThumbnail: Boolean): Single<Uri>                    // Получение Uri файла по его имени
    fun getFiles(filesList: List<String>, isThumbnail: Boolean): Single<List<Uri>>  // Получение списка Uri файлов по их имени
    fun showFile(name: String)                                                      // Запуск интента на просмотр файла
    fun cacheDelete(name: String): Single<Boolean>                                  // Удалить файл из кэша
    fun cacheUpdate(name: String): Single<Uri>                                      // Обновить фай в кэше
    fun cachePut(name: String): Single<Uri>                                         // Положить файл в кэш
    fun cacheClean(): Single<Boolean>                                               // Удалить файлы в кэше
    fun cacheCopy(fromName: String, toName: String): Single<Boolean>                 // Копировать файлы

    data class Document(
        val type: MediaType,
        val name: String,
        val uri: Uri
    )

    enum class MediaType {
        IMAGE, VIDEO, PDF, DOC, OTHER;

        companion object {
            fun getTypeFromExt(ext: String) =
                    when (ext.lowercase()) {
                        "jpg", "tif", "png"  -> IMAGE
                        "pdf"                -> PDF
                        "doc", "docx", "rtf" -> DOC
                        "mp4", "avi"         -> VIDEO
                        else                 -> OTHER
                    }
        }
    }
}

class FileManagerImpl(private val context: Context) : FileManager {

    private val localDir get() = context.filesDir
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    private val _pickResult: PublishSubject<List<FileManager.Document>> = PublishSubject.create()
    override val pickResult get() = _pickResult

    override fun registerContract(activity: AppCompatActivity) {
        resultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                try {
                    result.data?.run {
                        val imagesEncodedList = ArrayList<Pair<String, Uri>>()
                        data?.run {
                            val fileName = getFileName(this)
                            if (FileManager.MediaType.getTypeFromExt(getFileExt(fileName)) != FileManager.MediaType.OTHER)
                                imagesEncodedList.add(Pair(getFileName(this), this))
                            else {
                                _pickResult.onError(Throwable("Files of such type are not supported"))
                            }
                        } ?: clipData?.run {
                            for (i in 0 until this.itemCount) {
                                val uri = this.getItemAt(i).uri
                                val fileName = getFileName(uri)
                                if (FileManager.MediaType.getTypeFromExt(getFileExt(fileName)) != FileManager.MediaType.OTHER)
                                    imagesEncodedList.add(Pair(getFileName(uri), uri))
                            }
                            if (this.itemCount > imagesEncodedList.size)
                                Toast.makeText(context, "Only ${imagesEncodedList.size} can be picked", Toast.LENGTH_LONG).show()
                        }
                        if (imagesEncodedList.size == 0)
                            Toast.makeText(context, "Yoy have selected nothing", Toast.LENGTH_LONG).show()
                        else
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
            val ext = getFileExt(filesList[i].first)
            val fileName = "${UUID.randomUUID().toString()}.$ext"
            if (copyFile(filesList[i].second, Uri.fromFile(File(localDir, fileName))))
                docList.add(FileManager.Document(type = FileManager.MediaType.getTypeFromExt(ext), name = fileName,
                                                 uri = Uri.fromFile(File(localDir, fileName))
                )
                )
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

    private fun getFileName(fullName: String) = fullName.substringAfterLast('/', "")

    private fun getFileExt(name: String) = name.substringAfterLast('.', "")

    private fun copyFile(src: Uri, dst: Uri): Boolean {
        var retValue = false
        context.contentResolver.openInputStream(src)?.run {
            context.contentResolver.openOutputStream(dst)?.also {
                try {
                    this.copyTo(it)
                    retValue = true
                } catch (e: IOException) {
                    throw(e)
                } finally {
                    this.close()
                }
            }
        }
        return retValue
    }

    // Запуск интента на выбор файлов для добавления в профиль
    private val pickIntent by lazy {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val mimeTypes = arrayOf("image/*", "application/pdf", "application/msword", "video/*")
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
    override fun getFile(file: String, isThumbnail: Boolean): Single<Uri> = Single.create {
        try {
            if (!isThumbnail) {
                val name = getFileName(file)
                if (name.isNotEmpty()) {
                    if (!isFileCached(name))
                        download(file, File(localDir, name))
                    it.onSuccess(Uri.fromFile(File(localDir, name)))
                } else
                    it.onSuccess(Uri.EMPTY)
            } else if (file.isNotEmpty()) {
                it.onSuccess(Uri.parse(file))
            } else {
                it.onSuccess(Uri.EMPTY)
            }
        } catch (e: Exception) {
            it.onError(e)
        }
    }

    // Получение Uri файла по его имени
    override fun getFiles(filesList: List<String>, isThumbnail: Boolean): Single<List<Uri>> = Single.create {
        try {
            val outList = mutableListOf<Uri>()
            filesList.forEach { file ->
                if (!isThumbnail) {
                    val name = file.substringAfterLast('/', "")
                    if (name.isNotEmpty()) {
                        if (!isFileCached(name))
                            download(file, File(localDir, name))
                        outList.add(Uri.fromFile(File(localDir, name)))
                    } else
                        outList.add(Uri.EMPTY)
                } else {
                    if (file.isNotEmpty())
                        outList.add(Uri.parse(file))
                    else
                        outList.add(Uri.EMPTY)
                }
            }
            it.onSuccess(outList)
        } catch (e: Exception) {
            it.onError(e)
        }
    }

    private fun isFileCached(name: String): Boolean = File(localDir, name).exists()

    private fun download(link: String, outFile: File) {
        URL(link).openStream().use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun mime(uri: Uri): String = context.contentResolver.getType(uri) ?: ""

    private val fileTypes by lazy {
        arrayListOf("jpg", "tif", "png", "pdf", "doc", "docx", "rtf", "mp4", "avi")
    }

    val scope = CoroutineScope(Dispatchers.IO)
    // Запуск интента на просмотр файла
    @OptIn(DelicateCoroutinesApi::class)
    override fun showFile(name: String) {
        if (!fileTypes.contains(getFileExt(name))) {
            Toast.makeText(context, "This file has wrong extension", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            var uri = Uri.parse(name)
            val fileName = getFileName(uri)
            val fileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outFile = File(fileDir, fileName)
            if (isFileCached(fileName))
                uri = Uri.fromFile(File(localDir, fileName))
            else {
                download(name, outFile)
            }
            copyFile(uri, outFile.toUri())
            uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", File(fileDir, fileName))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setDataAndType(uri, mime(uri))
            intent.flags = FLAG_ACTIVITY_NEW_TASK or FLAG_GRANT_READ_URI_PERMISSION
            context.startActivity(intent)
        }
    }

    override fun cacheDelete(name: String): Single<Boolean> = Single.create {
        val file = File(name)
        if (file.exists()) {
            try {
                file.delete()
                it.onSuccess(true)
            } catch (e: Exception) {
                it.onError(e)
            }
        } else {
            it.onError(FileNotFoundException())
        }
    }

    override fun cacheUpdate(name: String): Single<Uri> = Single.create {
        val uri = Uri.parse(name)
        uri.scheme
    }

    override fun cachePut(name: String): Single<Uri> = Single.create {
        val file = File(name)
        if (file.exists())
            it.onError(Throwable("File exists"))
        try {
            val newUri = File(localDir,getFileName(name)).toUri()
            if (!copyFile(file.toUri(),newUri))
                it.onError(IOException("Couldn't copy file"))
            it.onSuccess(newUri)
        } catch (e:Exception){
            it.onError(e)
        }
    }

    override fun cacheClean(): Single<Boolean> = Single.create {
        try {
            File(localDir.path).walkTopDown().forEach {
                if (it.isFile)
                    it.delete()
            }
            it.onSuccess(true)
        } catch (e: Exception) {
            it.onError(e)
        }
    }

    override fun cacheCopy(fromName: String, toName: String): Single<Boolean> {
        TODO("Not yet implemented")
    }

}