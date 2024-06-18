package com.houshengle.flashcards.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlin.math.ceil

class CardView(private val cardDao: CardDao) : ViewModel() {

    val allCards: LiveData<List<Card>> = cardDao.getAllCards()


    fun getCardsByGroupId(groupId: Int) {
        viewModelScope.launch {
            cardDao.getCardsByGroupId(groupId)
        }
    }

    fun insert(card: Card) {
        viewModelScope.launch {
            cardDao.insertCard(card)
        }
    }

    fun delete(card: Card) {
        viewModelScope.launch {
            cardDao.deleteCard(card)
        }
    }

    fun updateCard(card: Card) {
        viewModelScope.launch {
            cardDao.updateCard(card)
        }
    }

    fun getCardById(id: Int, onResult: (Card?) -> Unit) {
        viewModelScope.launch {
            val card = cardDao.getCardById(id)
            onResult(card)
        }
    }

    fun getCardWithDueTimeBefore(dueTime: Long, onResult: (Card?) -> Unit) {
        viewModelScope.launch {
            val card = cardDao.getCardWithDueTimeBefore(dueTime)
            onResult(card)
        }
    }

    fun getAllCardsWithDueTimeBefore(dueTime: Long, onResult: (List<Card>) -> Unit) {
        viewModelScope.launch {
            val cards = cardDao.getAllCardsWithDueTimeBefore(dueTime)
            onResult(cards)
        }
    }

//    fun getCardsNumberWithDueTimeBefore(dueTime: Long, onResult: (Int) -> Unit) {
//        viewModelScope.launch {
//            val cards = cardDao.getAllCardsWithDueTimeBefore(dueTime)
//            onResult(cards.count())
//        }
//    }

    //计算EF，计算间隔时间，计算计划复习时间
    fun reviewCard(card: Card, quality: Int) {
        viewModelScope.launch {
            if (quality >= 3) {
                when (card.repetitions) {
                    0 -> card.interval = 1
                    1 -> card.interval = 6
                    else -> card.interval = ceil(card.interval * card.easeFactor).toInt()
                }
                card.repetitions++
                val newEaseFactor =
                    card.easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
                card.easeFactor = if (newEaseFactor >= 1.3) newEaseFactor else 1.3
            } else {
                card.repetitions = 0
                card.interval = 1
            }
            val currentTime = System.currentTimeMillis()
            card.planReviewTime = currentTime + card.interval * 24 * 60 * 60 * 1000
            card.lastReviewed = currentTime
            card.totalReviewTimes++
            updateCard(card)
        }
    }
}