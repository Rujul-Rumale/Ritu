package com.haodong.yimalaile.infrastructure.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

const val DATA_STORE_FILE_NAME = "yimalaile.preferences_pb"

// DataStore must be a process-wide singleton — creating two against the same
// file (which happens on every Activity re-creation) throws IllegalStateException.
@Volatile private var sharedDataStore: DataStore<Preferences>? = null
private val dataStoreLock = Any()

fun appDataStore(context: Context): DataStore<Preferences> {
    sharedDataStore?.let { return it }
    return synchronized(dataStoreLock) {
        sharedDataStore ?: PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                context.applicationContext.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath.toPath()
            },
        ).also { sharedDataStore = it }
    }
}