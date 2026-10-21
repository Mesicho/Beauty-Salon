package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val displayName: String = "",
    val salonName: String = "",
    val email: String? = null,
    val phone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "displayName" to displayName,
            "salonName" to salonName
        )
        email?.let { map["email"] = it }
        phone?.let { map["phone"] = it }
        createdAt?.let { map["createdAt"] = it }
        updatedAt?.let { map["updatedAt"] = it }
        return map
    }
}

@IgnoreExtraProperties
data class Booking(
    @DocumentId val id: String = "",
    val clientName: String = "",
    val clientPhone: String? = null,
    val serviceId: String? = null,
    val serviceName: String = "",
    val staffId: String? = null,
    val staffName: String = "",
    val date: String = "", // YYYY-MM-DD
    val timeSlot: String = "", // e.g. "10:00 AM"
    val durationMin: Long = 60L,
    val price: Double = 0.0,
    val status: String = "CONFIRMED", // CONFIRMED, PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "clientName" to clientName,
            "serviceName" to serviceName,
            "staffName" to staffName,
            "date" to date,
            "timeSlot" to timeSlot,
            "durationMin" to durationMin,
            "price" to price,
            "status" to status
        )
        clientPhone?.let { map["clientPhone"] = it }
        serviceId?.let { map["serviceId"] = it }
        staffId?.let { map["staffId"] = it }
        notes?.let { map["notes"] = it }
        createdAt?.let { map["createdAt"] = it }
        updatedAt?.let { map["updatedAt"] = it }
        return map
    }
}

@IgnoreExtraProperties
data class StaffMember(
    @DocumentId val id: String = "",
    val name: String = "",
    val role: String = "Master Stylist",
    val specialization: String = "Hair Styling & Coloring",
    val phone: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val workingHours: String? = "09:00 AM - 06:00 PM",
    val activeDays: String? = "Mon, Tue, Wed, Thu, Fri",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "name" to name,
            "role" to role,
            "specialization" to specialization
        )
        phone?.let { map["phone"] = it }
        email?.let { map["email"] = it }
        avatarUrl?.let { map["avatarUrl"] = it }
        workingHours?.let { map["workingHours"] = it }
        activeDays?.let { map["activeDays"] = it }
        notes?.let { map["notes"] = it }
        createdAt?.let { map["createdAt"] = it }
        updatedAt?.let { map["updatedAt"] = it }
        return map
    }
}

@IgnoreExtraProperties
data class SalonService(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = "Hair",
    val durationMin: Long = 60L,
    val price: Double = 50.0,
    val description: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "name" to name,
            "category" to category,
            "durationMin" to durationMin,
            "price" to price
        )
        description?.let { map["description"] = it }
        createdAt?.let { map["createdAt"] = it }
        updatedAt?.let { map["updatedAt"] = it }
        return map
    }
}

@IgnoreExtraProperties
data class ClientRecord(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String? = null,
    val notes: String? = null,
    val preferredStylistId: String? = null,
    val totalVisits: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "totalVisits" to totalVisits
        )
        email?.let { map["email"] = it }
        notes?.let { map["notes"] = it }
        preferredStylistId?.let { map["preferredStylistId"] = it }
        createdAt?.let { map["createdAt"] = it }
        updatedAt?.let { map["updatedAt"] = it }
        return map
    }
}
