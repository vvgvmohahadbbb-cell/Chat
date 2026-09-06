package com.ishhf.familymap

data class FamilyMember(
    val uid: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = 0,
    val sharing: Boolean = false
)
