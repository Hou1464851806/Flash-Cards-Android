package com.houshengle.flashcards.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups")
    suspend fun getAllGroups(): List<Group>
    @Query("SELECT * FROM groups WHERE name = :groupName LIMIT 1")
    suspend fun getGroupByName(groupName: String): Group?

    @Update
    suspend fun update(group: Group)
    @Delete
    suspend fun delete(group: Group)
    @Insert
    suspend fun insert(group: Group): Long
}