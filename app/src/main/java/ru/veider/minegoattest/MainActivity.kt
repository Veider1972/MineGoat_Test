package ru.veider.minegoattest

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    val REQUEST_EXTERNAL_STORAGE = 1
    val PERMISSIONS_STORAGE = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                                      Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    lateinit var button: Button
    lateinit var button1: Button
    lateinit var button2: Button
    lateinit var textView: TextView
    lateinit var fileManager: FileManager

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        fileManager = FileManagerImpl(this.applicationContext).apply {
            registerContract(this@MainActivity)
        }.also {
            it.pickResult.subscribeBy(
                onNext = {
                    Log.d("TAG", it.toString())
                }
            )
        }
        button = findViewById<Button?>(R.id.test_button).apply {
            setOnClickListener {
                fileManager.pickFiles()
            }
        }
        button1 = findViewById<Button?>(R.id.test_button1).apply {
            setOnClickListener {
//                GlobalScope.launch {
                    fileManager.getFile("https://megaprikoli.ru/uploads/thumbs/0e547c382-social.jpg")
                        .subscribeBy(
                            onSuccess = {
                                // TODO
                            }
                        )
//                }

            }

        }


        button2 = findViewById<Button?>(R.id.test_button2).apply {
            setOnClickListener {
                GlobalScope.launch {
                    fileManager.getFiles(arrayListOf("https://megaprikoli.ru/uploads/thumbs/0e547c382-social.jpg",
                                                     "https://img4.goodfon.com/original/600x1024/b/83/kotenok-glaza-trava-1.jpg"
                    )
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy {
                            Log.d("TAG", it.toString())
                        }
                }

            }


        }
        textView = findViewById(R.id.text)


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

}

//https://megaprikoli.ru/uploads/thumbs/0e547c382-social.jpg
//https://img4.goodfon.com/original/600x1024/b/83/kotenok-glaza-trava-1.jpg