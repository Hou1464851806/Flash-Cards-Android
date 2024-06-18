package com.houshengle.flashcards.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards", foreignKeys = [ForeignKey(
        entity = Group::class,
        parentColumns = ["id"],
        childColumns = ["group_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Card(
    //被显示的属性
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "front") var front: String,
    @ColumnInfo(name = "back") var back: String,
    @ColumnInfo(name = "created_time") val createdTime: Long,
    @ColumnInfo(name = "updated_time") val updatedTime: Long,
    @ColumnInfo(name = "is_star") var isStar: Boolean,
    //计划复习时间
    @ColumnInfo(name = "plan_review_time") var planReviewTime: Long,
    //@ColumnInfo(name = "is_review") var isReview: Boolean = false,
    //用于计算复习时间的属性
    @ColumnInfo(name = "ease_factor") var easeFactor: Double = 2.5,
    @ColumnInfo(name = "repetitions") var repetitions: Int = 0,
    @ColumnInfo(name = "interval") var interval: Int = 0, //单位为天
    //用于统计的属性
    @ColumnInfo(name = "last_reviewed") var lastReviewed: Long = 0L,
    @ColumnInfo(name = "total_review_times") var totalReviewTimes: Int = 0,

    @ColumnInfo(name = "group_id") val groupId: Int = 0,
    @ColumnInfo(name = "group_name") val groupName: String = "",
)
