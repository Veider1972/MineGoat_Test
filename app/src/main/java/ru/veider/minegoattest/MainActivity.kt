package ru.veider.minegoattest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers

val TAG = "LOG_TAG"
class MainActivity : AppCompatActivity(R.layout.activity_main) {



    val REQUEST_EXTERNAL_STORAGE = 1
    val PERMISSIONS_STORAGE = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                                      Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    lateinit var pickButton: Button
    lateinit var getButton: Button
    lateinit var imgButton: Button
    lateinit var videoButton: Button
    lateinit var pdfButton: Button
    lateinit var docButton: Button
    lateinit var cleanButton: Button
    lateinit var textView: TextView
    lateinit var imageView: ImageView
    lateinit var fileManager: FileManager

    private val compositeDisposable = CompositeDisposable()

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        checkPermissions()
        fileManager = app.fileManager.apply {
            registerContract(this@MainActivity)
        }.apply {
            pickResult.subscribeBy(
                onNext = { list ->
                    Log.d("TAG", list.toString())
                }
            )
        }
        pickButton = findViewById<Button?>(R.id.pick_button).apply {
            setOnClickListener {
                fileManager.pickFiles()
            }
        }
        getButton = findViewById<Button?>(R.id.get_file_button).apply {
            setOnClickListener {
                val disposable = fileManager.getFile("https://img4.goodfon.com/original/600x1024/b/83/kotenok-glaza-trava-1.jpg", false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(
                        onSuccess = {
                            Log.d("TAG", it.toString())
//                            imageView.setImageURI(it)
                        },
                        onError = {
                            Log.d("TAG", "Error")
                        }
                    )
                compositeDisposable.add(disposable)
            }

        }




        imgButton = findViewById<Button?>(R.id.show_image_button).apply {
            setOnClickListener {
                fileManager.showFile("https://img4.goodfon.com/original/600x1024/b/83/kotenok-glaza-trava-1.jpg")
            }


        }
        videoButton = findViewById<Button?>(R.id.show_video_button).apply {
            setOnClickListener {
                fileManager.showFile("file:///data/user/0/ru.veider.minegoattest/files/4246822b-79ef-410e-849c-da7cb47b4c40.mp4")
            }

        }

        pdfButton = findViewById<Button?>(R.id.show_pdf_button).apply {
            setOnClickListener {
                fileManager.showFile("file:///data/user/0/ru.veider.minegoattest/files/70f39a16-6979-40d0-84f8-e594265e3289.pdf")
            }
        }

        docButton = findViewById<Button?>(R.id.show_doc_button).apply {
            setOnClickListener {
                fileManager.showFile("file:///data/user/0/ru.veider.minegoattest/files/27a519a3-54f2-4c0f-afb3-85876925d7d8.doc")
            }

        }

        cleanButton = findViewById<Button?>(R.id.show_clean_button).apply {
            setOnClickListener {
                fileManager.cacheUpdate("https://megaprikoli.ru/uploads/thumbs/0e547c382-social.jpg").subscribeBy(
                    onSuccess = {
                        Log.d(TAG, "onSuccess: "+ it)
                    },
                    onError = {
                        Log.d(TAG, "onError: " + it.message)
                    }
                )
            }

        }


        textView = findViewById<TextView>(R.id.text)
        imageView = findViewById<ImageView>(R.id.image_view)

    }

    override fun onResume() {
        super.onResume()
        fileManager.cacheUpdate("").subscribeBy(
            onSuccess = {},
            onError = {}
        )
    }


    fun checkPermissions(): Boolean {
        val permission = this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            return true
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                return true
        }
        Toast.makeText(this, "You are not be able to pick documents without granted write permission", Toast.LENGTH_LONG)
            .show()
        return false
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

}

//https://megaprikoli.ru/uploads/thumbs/0e547c382-social.jpg
//https://img4.goodfon.com/original/600x1024/b/83/kotenok-glaza-trava-1.jpg

//"https://elib.bsu.by/bitstream/123456789/201325/1/Cherniak_masters.pdf"