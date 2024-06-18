package com.houshengle.flashcards.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Card::class, Group::class], version = 1, exportSchema = false)
abstract class DataBase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun groupDao(): GroupDao
}