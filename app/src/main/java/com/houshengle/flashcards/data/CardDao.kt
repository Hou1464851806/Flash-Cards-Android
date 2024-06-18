package com.houshengle.flashcards.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface CardDao {
    @Query("select * from cards order by plan_review_time")
    fun getAllCards(): LiveData<List<Card>>
    @Query("select * from cards where plan_review_time <= :dueTime  limit 1")
    suspend fun getCardWithDueTimeBefore(dueTime: Long): Card?
    @Query("select * from cards where plan_review_time <= :dueTime")
    suspend fun getAllCardsWithDueTimeBefore(dueTime: Long): List<Card>
    @Query("select count(*) from cards where plan_review_time <= :dueTime ")
    suspend fun getCardsNumberWithDueTimeBefore(dueTime: Long): Int
    @Query("select * from cards where id = :id limit 1")
    suspend fun getCardById(id: Int): Card?
    @Query("SELECT * FROM cards WHERE group_id = :groupId")
    suspend fun getCardsByGroupId(groupId: Int): List<Card>

    @Update
    suspend fun updateCard(card: Card)
    @Insert
    suspend fun insertCard(card: Card)
    @Delete
    suspend fun deleteCard(card: Card)


}