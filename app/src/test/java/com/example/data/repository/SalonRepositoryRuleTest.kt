package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Booking
import com.example.data.model.StaffMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SalonRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createBooking_validPayload_createsDocumentAndReturnsId() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = SalonRepository(firestore, auth)

    val booking = Booking(
      clientName = "Emma Watson",
      serviceName = "Balayage",
      staffName = "Sarah Stylist",
      date = "2026-10-25",
      timeSlot = "10:00 AM",
      durationMin = 90L,
      price = 150.0,
      status = "CONFIRMED"
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createBooking(booking) }
    assertTrue(result.isSuccess)
    val id = result.getOrThrow()
    assertNotNull(id)
    assertTrue(id.isNotBlank())
  }

  @Test
  fun createStaff_validPayload_createsDocumentAndReturnsId() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = SalonRepository(firestore, auth)

    val staff = StaffMember(
      name = "Sarah Miller",
      role = "Senior Colorist",
      specialization = "Balayage & Blonding",
      workingHours = "9:00 AM - 5:00 PM",
      activeDays = "Tue, Wed, Thu, Fri, Sat"
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createStaffMember(staff) }
    assertTrue(result.isSuccess)
    val id = result.getOrThrow()
    assertNotNull(id)
    assertTrue(id.isNotBlank())
  }

  @Test
  fun observeBookings_authenticatedOwner_emitsBookings() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = SalonRepository(firestore, auth)

    val booking = Booking(
      clientName = "Olivia Brown",
      serviceName = "Keratin Treatment",
      staffName = "Sarah Stylist",
      date = "2026-10-26",
      timeSlot = "11:00 AM",
      durationMin = 120L,
      price = 220.0,
      status = "CONFIRMED"
    )

    val bookingId = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createBooking(booking).getOrThrow() }

    val emitted = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeBookings().first { list: List<Booking> -> list.any { it.id == bookingId } }
    }
    assertTrue(emitted.any { it.id == bookingId })
  }

  @Test
  fun unauthenticatedUser_failsWithIllegalState() = runBlocking {
    auth.signOut()
    val repository = SalonRepository(firestore, auth)

    try {
      repository.createBooking(
        Booking(
          clientName = "Anonymous",
          serviceName = "Cut",
          staffName = "Staff",
          date = "2026-10-25",
          timeSlot = "10:00 AM"
        )
      )
      fail("Expected IllegalStateException for unauthenticated user")
    } catch (e: IllegalStateException) {
      assertTrue(e.message?.contains("User must be signed in") == true)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice@testsalon.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
