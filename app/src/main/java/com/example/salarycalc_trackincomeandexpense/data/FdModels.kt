package com.example.salarycalc_trackincomeandexpense.data

enum class FdStatus { ACTIVE, COMPLETED }

data class Fd(
    val id: Long,
    val name: String,
    val principal: Double,
    val interestRate: Double,
    val tenureYears: Double,
    val startDate: String,
    val status: FdStatus,
    val completedGain: Double,
    val createdAt: Long,
    val updatedAt: Long
)
