package at.msd.friehs_bicha.cdcsvparser.instance

import android.content.Context
import at.msd.friehs_bicha.cdcsvparser.db.AppDatabase
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog

object InstanceVars {

    lateinit var applicationContext: Context
    lateinit var db: AppDatabase

    fun init(context: Context) {
        applicationContext = context
        initLogging(context)
        initDB(context)
    }

    private fun initDB(context: Context) {
        val db = AppDatabase.getInstance(context)
        if (db != null) {
            this.db = db
        } else {
            throw Exception("Database not initialized")
        }
    }

    private fun initLogging(context: Context) {
        FileLog.init(context)
    }

}