package com.example.modules.storage_search

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * =========================================================================================
 * МОДУЛЬ: FolderEntity (Сущность базы данных для папок и категорий)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Организация аудиозаписей и стенограмм по проектам и тематическим каталогам.
 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#1A237E",
    val iconName: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)
