package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ChatMessage
import com.example.ai.GeminiManager
import com.example.ai.GroundedResponse
import com.example.ai.ImageGenerationResult
import com.example.ai.VideoGenerationResult
import com.example.data.model.Booking
import com.example.data.model.ClientRecord
import com.example.data.model.SalonService
import com.example.data.model.StaffMember
import com.example.data.model.UserProfile
import com.example.data.repository.SalonRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class SalonViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SalonRepository(application.applicationContext)

    // User Profile
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    // Realtime Bookings Flow
    val bookingsState: StateFlow<UiState<List<Booking>>> = repository.observeBookings()
        .map<List<Booking>, UiState<List<Booking>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to observe bookings")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    // Realtime Staff Flow
    val staffState: StateFlow<UiState<List<StaffMember>>> = repository.observeStaff()
        .map<List<StaffMember>, UiState<List<StaffMember>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to observe staff")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    // Realtime Services Flow
    val servicesState: StateFlow<UiState<List<SalonService>>> = repository.observeServices()
        .map<List<SalonService>, UiState<List<SalonService>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to observe services")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    // Realtime Clients Flow
    val clientsState: StateFlow<UiState<List<ClientRecord>>> = repository.observeClients()
        .map<List<ClientRecord>, UiState<List<ClientRecord>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to observe clients")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    // AI Chat state
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "model",
                text = "Welcome to SalonSphere! I am your AI Beauty Consultant and Operations Assistant. Ask me anything about hair color chemistry, appointment scheduling, 2026 styling trends, or aftercare protocols."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    // AI Search Grounding
    private val _trendSearchState = MutableStateFlow<UiState<GroundedResponse>?>(null)
    val trendSearchState: StateFlow<UiState<GroundedResponse>?> = _trendSearchState.asStateFlow()

    // AI Maps Grounding
    private val _supplierSearchState = MutableStateFlow<UiState<GroundedResponse>?>(null)
    val supplierSearchState: StateFlow<UiState<GroundedResponse>?> = _supplierSearchState.asStateFlow()

    // AI High Thinking Optimizer
    private val _optimizationReport = MutableStateFlow<UiState<String>?>(null)
    val optimizationReport: StateFlow<UiState<String>?> = _optimizationReport.asStateFlow()

    // AI Image Creation
    private val _imageGenerationState = MutableStateFlow<ImageGenerationResult?>(null)
    val imageGenerationState: StateFlow<ImageGenerationResult?> = _imageGenerationState.asStateFlow()
    private val _isImageGenerating = MutableStateFlow(false)
    val isImageGenerating: StateFlow<Boolean> = _isImageGenerating.asStateFlow()

    // AI Video Generation
    private val _videoGenerationState = MutableStateFlow<VideoGenerationResult?>(null)
    val videoGenerationState: StateFlow<VideoGenerationResult?> = _videoGenerationState.asStateFlow()
    private val _videoProgressStatus = MutableStateFlow<String?>(null)
    val videoProgressStatus: StateFlow<String?> = _videoProgressStatus.asStateFlow()
    private val _isVideoGenerating = MutableStateFlow(false)
    val isVideoGenerating: StateFlow<Boolean> = _isVideoGenerating.asStateFlow()

    init {
        loadUserProfile()
        checkAndSeedInitialData()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser ?: return@launch
            val res = repository.getUserProfile()
            if (res.isSuccess && res.getOrNull() != null) {
                _userProfile.value = res.getOrNull()
            } else {
                val newProfile = UserProfile(
                    displayName = user.displayName ?: "Salon Director",
                    salonName = "SalonSphere Atelier",
                    email = user.email
                )
                repository.saveUserProfile(newProfile)
                _userProfile.value = newProfile
            }
        }
    }

    private fun checkAndSeedInitialData() {
        viewModelScope.launch {
            // Seed sample salon services if empty
            val servicesSub = repository.observeServices()
            servicesSub.map { it }.catch { emit(emptyList()) }
            // Let's seed initial menu items
            repository.createService(SalonService(name = "Signature Balayage & Gloss", category = "Hair", durationMin = 120L, price = 185.0, description = "Hand-painted dimensional highlights with botanical gloss finish."))
            repository.createService(SalonService(name = "Precision Cut & Blowout", category = "Hair", durationMin = 60L, price = 85.0, description = "Customized sculpting cut with luxurious blowout styling."))
            repository.createService(SalonService(name = "Hydra-Luxe Facial", category = "Skincare", durationMin = 75L, price = 140.0, description = "Deep ultrasonic exfoliation and hyaluronic infusion."))
            repository.createService(SalonService(name = "Gel Polish Spa Manicure", category = "Nails", durationMin = 45L, price = 55.0, description = "Cuticle care, organic hand massage, and long-wear gel coat."))

            // Seed sample staff
            repository.createStaffMember(StaffMember(name = "Chloe Laurent", role = "Master Stylist & Colorist", specialization = "Balayage, Blonding, Creative Cuts", phone = "(555) 234-5678", workingHours = "09:00 AM - 05:00 PM", activeDays = "Tue, Wed, Thu, Fri, Sat"))
            repository.createStaffMember(StaffMember(name = "Julian Vance", role = "Senior Barber & Stylist", specialization = "Precision Fades, Beard Sculpting", phone = "(555) 345-6789", workingHours = "10:00 AM - 07:00 PM", activeDays = "Mon, Tue, Wed, Thu, Fri"))
            repository.createStaffMember(StaffMember(name = "Mia Zhang", role = "Lead Esthetician", specialization = "Glow Facials, Microcurrent, Brow Lamination", phone = "(555) 456-7890", workingHours = "09:30 AM - 05:30 PM", activeDays = "Wed, Thu, Fri, Sat, Sun"))

            // Seed sample bookings
            repository.createBooking(Booking(clientName = "Victoria Sterling", clientPhone = "(555) 890-1234", serviceName = "Signature Balayage & Gloss", staffName = "Chloe Laurent", date = "2026-10-22", timeSlot = "10:00 AM", durationMin = 120L, price = 185.0, status = "CONFIRMED", notes = "Prefers cool ash blonde tones."))
            repository.createBooking(Booking(clientName = "Liam Gallagher", clientPhone = "(555) 901-2345", serviceName = "Precision Cut & Blowout", staffName = "Julian Vance", date = "2026-10-22", timeSlot = "01:30 PM", durationMin = 60L, price = 85.0, status = "CONFIRMED"))
            repository.createBooking(Booking(clientName = "Serena Al-Mansoor", clientPhone = "(555) 912-3456", serviceName = "Hydra-Luxe Facial", staffName = "Mia Zhang", date = "2026-10-23", timeSlot = "11:00 AM", durationMin = 75L, price = 140.0, status = "PENDING"))

            // Seed sample clients
            repository.createClient(ClientRecord(name = "Victoria Sterling", phone = "(555) 890-1234", email = "victoria@sterling.com", notes = "VIP client, loves herbal tea during appointments", totalVisits = 6L))
            repository.createClient(ClientRecord(name = "Liam Gallagher", phone = "(555) 901-2345", email = "liam.g@email.com", notes = "Monthly haircut regular", totalVisits = 4L))
        }
    }

    // --- Bookings Actions ---

    fun createBooking(
        clientName: String,
        clientPhone: String,
        service: SalonService,
        staff: StaffMember,
        date: String,
        timeSlot: String,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val booking = Booking(
                clientName = clientName,
                clientPhone = clientPhone.ifBlank { null },
                serviceId = service.id.ifBlank { null },
                serviceName = service.name,
                staffId = staff.id.ifBlank { null },
                staffName = staff.name,
                date = date,
                timeSlot = timeSlot,
                durationMin = service.durationMin,
                price = service.price,
                status = "CONFIRMED",
                notes = notes.ifBlank { null }
            )
            val res = repository.createBooking(booking)
            if (res.isSuccess) onSuccess() else onError(res.exceptionOrNull()?.message ?: "Failed to save booking")
        }
    }

    fun updateBookingStatus(booking: Booking, newStatus: String) {
        viewModelScope.launch {
            repository.updateBooking(booking.copy(status = newStatus))
        }
    }

    fun deleteBooking(bookingId: String) {
        viewModelScope.launch {
            repository.deleteBooking(bookingId)
        }
    }

    // --- Staff Actions ---

    fun createStaffMember(
        name: String,
        role: String,
        specialization: String,
        phone: String,
        email: String,
        workingHours: String,
        activeDays: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val staff = StaffMember(
                name = name,
                role = role,
                specialization = specialization,
                phone = phone.ifBlank { null },
                email = email.ifBlank { null },
                workingHours = workingHours.ifBlank { "09:00 AM - 05:00 PM" },
                activeDays = activeDays.ifBlank { "Mon-Fri" }
            )
            val res = repository.createStaffMember(staff)
            if (res.isSuccess) onSuccess() else onError(res.exceptionOrNull()?.message ?: "Failed to create staff")
        }
    }

    fun deleteStaff(staffId: String) {
        viewModelScope.launch {
            repository.deleteStaffMember(staffId)
        }
    }

    // --- Service Actions ---

    fun createService(
        name: String,
        category: String,
        durationMin: Long,
        price: Double,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val service = SalonService(
                name = name,
                category = category,
                durationMin = durationMin,
                price = price,
                description = description.ifBlank { null }
            )
            val res = repository.createService(service)
            if (res.isSuccess) onSuccess() else onError(res.exceptionOrNull()?.message ?: "Failed to add service")
        }
    }

    fun deleteService(serviceId: String) {
        viewModelScope.launch {
            repository.deleteService(serviceId)
        }
    }

    // --- Client Actions ---

    fun createClient(
        name: String,
        phone: String,
        email: String,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val client = ClientRecord(
                name = name,
                phone = phone,
                email = email.ifBlank { null },
                notes = notes.ifBlank { null }
            )
            val res = repository.createClient(client)
            if (res.isSuccess) onSuccess() else onError(res.exceptionOrNull()?.message ?: "Failed to add client")
        }
    }

    fun deleteClient(clientId: String) {
        viewModelScope.launch {
            repository.deleteClient(clientId)
        }
    }

    // --- AI Features ---

    fun sendChatMessage(userText: String, useArgon: Boolean = false) {
        if (userText.isBlank()) return
        val current = _chatMessages.value.toMutableList()
        current.add(ChatMessage(role = "user", text = userText))
        _chatMessages.value = current
        _isChatLoading.value = true

        viewModelScope.launch {
            val result = GeminiManager.chatWithConcierge(current, userText, useArgon)
            _isChatLoading.value = false
            val updated = _chatMessages.value.toMutableList()
            if (result.isSuccess) {
                updated.add(ChatMessage(role = "model", text = result.getOrNull() ?: ""))
            } else {
                updated.add(ChatMessage(role = "model", text = "Sorry, I encountered an issue: ${result.exceptionOrNull()?.message}"))
            }
            _chatMessages.value = updated
        }
    }

    fun searchBeautyTrends(query: String) {
        _trendSearchState.value = UiState.Loading
        viewModelScope.launch {
            val result = GeminiManager.searchBeautyTrends(query)
            if (result.isSuccess && result.getOrNull() != null) {
                _trendSearchState.value = UiState.Success(result.getOrNull()!!)
            } else {
                _trendSearchState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Search failed")
            }
        }
    }

    fun searchNearbySuppliers(query: String) {
        _supplierSearchState.value = UiState.Loading
        viewModelScope.launch {
            val result = GeminiManager.findNearbySuppliersOrSalons(query)
            if (result.isSuccess && result.getOrNull() != null) {
                _supplierSearchState.value = UiState.Success(result.getOrNull()!!)
            } else {
                _supplierSearchState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Search failed")
            }
        }
    }

    fun runHighThinkingOptimization(customPrompt: String = "Analyze today's schedule for stylist turnaround, identify overbooking risks, and optimize revenue.") {
        _optimizationReport.value = UiState.Loading
        viewModelScope.launch {
            val bookings = (bookingsState.value as? UiState.Success)?.data ?: emptyList()
            val staff = (staffState.value as? UiState.Success)?.data ?: emptyList()

            val bookingsSummary = bookings.joinToString("\n") {
                "- ${it.date} ${it.timeSlot}: ${it.serviceName} with ${it.staffName} for client ${it.clientName} ($${it.price}, status: ${it.status})"
            }.ifBlank { "No bookings recorded yet." }

            val staffSummary = staff.joinToString("\n") {
                "- ${it.name} (${it.role}): Hours: ${it.workingHours}, Days: ${it.activeDays}, Spec: ${it.specialization}"
            }.ifBlank { "No staff configured yet." }

            val res = GeminiManager.optimizeSalonOperations(customPrompt, bookingsSummary, staffSummary)
            if (res.isSuccess) {
                _optimizationReport.value = UiState.Success(res.getOrNull() ?: "")
            } else {
                _optimizationReport.value = UiState.Error(res.exceptionOrNull()?.message ?: "Optimization failed")
            }
        }
    }

    fun generateStylePreview(prompt: String, baseImageB64: String? = null) {
        _isImageGenerating.value = true
        _imageGenerationState.value = null
        viewModelScope.launch {
            val res = GeminiManager.generateOrEditStyleImage(prompt, baseImageB64)
            _isImageGenerating.value = false
            _imageGenerationState.value = res
        }
    }

    fun generateVideoTransformation(imageB64: String, prompt: String, aspectRatio: String = "9:16") {
        _isVideoGenerating.value = true
        _videoGenerationState.value = null
        _videoProgressStatus.value = "Starting video engine..."
        viewModelScope.launch {
            val res = GeminiManager.generateTransformationVideo(
                imageB64 = imageB64,
                prompt = prompt,
                aspectRatio = aspectRatio,
                onProgress = { _videoProgressStatus.value = it }
            )
            _isVideoGenerating.value = false
            _videoProgressStatus.value = null
            _videoGenerationState.value = res
        }
    }
}
