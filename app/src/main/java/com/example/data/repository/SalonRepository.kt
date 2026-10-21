package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.Booking
import com.example.data.model.ClientRecord
import com.example.data.model.SalonService
import com.example.data.model.StaffMember
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SalonRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // Secondary constructor resolving provisioned firestore_database_id from resources
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    // --- User Profile ---

    suspend fun getUserProfile(): Result<UserProfile?> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            val doc = db.document(path).get().await()
            Result.success(doc.toObject(UserProfile::class.java))
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        val docRef = db.document(path)
        return try {
            val existing = docRef.get().await()
            val payload = profile.toMap().toMutableMap()
            if (!existing.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            payload["updatedAt"] = FieldValue.serverTimestamp()
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Bookings ---

    fun observeBookings(): Flow<List<Booking>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/bookings"
        emitAll(
            db.collection("users").document(uid).collection("bookings")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Booking::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createBooking(booking: Booking): Result<String> {
        val uid = requireUserId()
        val path = "users/$uid/bookings"
        val collection = db.collection("users").document(uid).collection("bookings")
        return try {
            val payload = booking.toMap().toMutableMap()
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            val ref = collection.add(payload).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBooking(booking: Booking): Result<Unit> {
        val uid = requireUserId()
        require(booking.id.isNotBlank()) { "Booking ID must not be blank" }
        val path = "users/$uid/bookings/${booking.id}"
        val docRef = db.document(path)
        return try {
            val payload = booking.toMap().toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBooking(bookingId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/bookings/$bookingId"
        return try {
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Staff ---

    fun observeStaff(): Flow<List<StaffMember>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/staff"
        emitAll(
            db.collection("users").document(uid).collection("staff")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(StaffMember::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createStaffMember(staff: StaffMember): Result<String> {
        val uid = requireUserId()
        val path = "users/$uid/staff"
        val collection = db.collection("users").document(uid).collection("staff")
        return try {
            val payload = staff.toMap().toMutableMap()
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            val ref = collection.add(payload).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateStaffMember(staff: StaffMember): Result<Unit> {
        val uid = requireUserId()
        require(staff.id.isNotBlank()) { "Staff ID must not be blank" }
        val path = "users/$uid/staff/${staff.id}"
        val docRef = db.document(path)
        return try {
            val payload = staff.toMap().toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStaffMember(staffId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/staff/$staffId"
        return try {
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Services ---

    fun observeServices(): Flow<List<SalonService>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/services"
        emitAll(
            db.collection("users").document(uid).collection("services")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(SalonService::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createService(service: SalonService): Result<String> {
        val uid = requireUserId()
        val path = "users/$uid/services"
        val collection = db.collection("users").document(uid).collection("services")
        return try {
            val payload = service.toMap().toMutableMap()
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            val ref = collection.add(payload).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateService(service: SalonService): Result<Unit> {
        val uid = requireUserId()
        require(service.id.isNotBlank()) { "Service ID must not be blank" }
        val path = "users/$uid/services/${service.id}"
        val docRef = db.document(path)
        return try {
            val payload = service.toMap().toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteService(serviceId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/services/$serviceId"
        return try {
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Clients ---

    fun observeClients(): Flow<List<ClientRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/clients"
        emitAll(
            db.collection("users").document(uid).collection("clients")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ClientRecord::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createClient(client: ClientRecord): Result<String> {
        val uid = requireUserId()
        val path = "users/$uid/clients"
        val collection = db.collection("users").document(uid).collection("clients")
        return try {
            val payload = client.toMap().toMutableMap()
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            val ref = collection.add(payload).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateClient(client: ClientRecord): Result<Unit> {
        val uid = requireUserId()
        require(client.id.isNotBlank()) { "Client ID must not be blank" }
        val path = "users/$uid/clients/${client.id}"
        val docRef = db.document(path)
        return try {
            val payload = client.toMap().toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteClient(clientId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/clients/$clientId"
        return try {
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
