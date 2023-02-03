package ru.veider.minegoattest

import android.app.Application

class App:Application() {
    lateinit var fileManager:FileManager
    companion object{
        var instance:App? = null
    }

    init {
        instance = this
        fileManager = FileManagerImpl(this)
    }
}

val app get()= App.instance!!