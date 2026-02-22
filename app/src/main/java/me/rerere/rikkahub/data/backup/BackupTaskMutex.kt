package me.rerere.rikkahub.data.backup

import kotlinx.coroutines.sync.Mutex

class BackupTaskMutex {
    val mutex = Mutex()
}

