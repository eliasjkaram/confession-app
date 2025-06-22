package com.example.confessionapp.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.confessionapp.R
import com.example.confessionapp.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.hasItem
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest // Marks these as longer running integration tests
class PriestProfileActivityTest {

    // --- IMPORTANT NOTES FOR ACTUAL IMPLEMENTATION ---
    // 1. Firebase Auth: You'd need to mock FirebaseAuth.getInstance().currentUser and its uid.
    //    This often involves a TestRule or a DI setup to inject a mock FirebaseAuth.
    // 2. UserRepository/Firestore: For true UI tests that don't hit a live backend,
    //    you'd mock UserRepository or FirebaseFirestore. This means your Activity
    //    needs to be able to accept a mocked instance (e.g., via DI with Hilt).
    // 3. Idling Resources: For asynchronous operations like fetching/saving data,
    //    Espresso needs IdlingResources to wait correctly.
    //
    // This conceptual test will assume some mocking capabilities are in place.
    // --- --- --- --- ---

    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockAuth: FirebaseAuth

    // A simplified way to provide a mock user, in real DI this would be cleaner
    companion object {
        val testUid = "testUser123"
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(PriestProfileActivity::class.java)

    @Before
    fun setUp() {
        // --- Mocking (Conceptual - requires DI or other test setup) ---
        mockUserRepository = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)

        // Mock current user
        val mockFirebaseUser = mockk<com.google.firebase.auth.FirebaseUser>(relaxed = true)
        every { mockFirebaseUser.uid } returns testUid
        every { mockAuth.currentUser } returns mockFirebaseUser

        // This is where DI would be great. The activity needs to use this mockAuth.
        // For now, we assume the activity's FirebaseAuth.getInstance() might be interceptable
        // or it's already using a DI setup that can be overridden in tests.
        // Same for UserRepository.

        // Example: If PriestProfileActivity used a ViewModel with injected UserRepository:
        // activityRule.scenario.onActivity { activity ->
        //    val viewModel = // get ViewModel
        //    viewModel.userRepository = mockUserRepository // Replace with mock
        // }
        // --- --- --- ---

        // Mock initial profile load
        every { mockUserRepository.getUserProfile(testUid, any()) } answers {
            val callback = secondArg<(Map<String, Any>?) -> Unit>()
            callback(mapOf("name" to "Test Priest", "languages" to listOf("English")))
        }
    }

    @Test
    fun addAndSaveLanguage_updatesProfile() {
        // 1. Initial state: Check if "English" chip is displayed (from mocked load)
        onView(allOf(withText("English"), isDescendantOfA(withId(R.id.languages_chip_group))))
            .check(matches(isDisplayed()))

        // 2. Type new language
        onView(withId(R.id.language_edit_text)).perform(typeText("French"))

        // 3. Click add language button
        onView(withId(R.id.add_language_button)).perform(click())

        // 4. Verify "French" chip is added to the group
        onView(allOf(withText("French"), isDescendantOfA(withId(R.id.languages_chip_group))))
            .check(matches(isDisplayed()))

        // 5. Click save profile button
        //    Mock the setUserProfile call to succeed
        every { mockUserRepository.setUserProfile(eq(testUid), any(), any()) } answers {
            val profileMap = secondArg<Map<String, Any>>()
            assertTrue(profileMap.containsKey("languages"))
            val languages = profileMap["languages"] as? List<String>
            assertNotNull(languages)
            assertTrue(languages!!.contains("English"))
            assertTrue(languages.contains("French"))

            val callback = thirdArg<(Boolean) -> Unit>()
            callback(true) // Simulate successful save
        }
        onView(withId(R.id.save_profile_button)).perform(click())

        // 6. Verify setUserProfile was called (conceptual, depends on mocking setup)
        //    This verification might happen implicitly if the mock above is set up with `verify`
        //    or you might need an explicit verify block if your mocking framework requires it.
        //    For MockK, the `every ... answers` block for setUserProfile contains assertions.

        // 7. Verify success toast (Espresso needs setup for Toast verification)
        // onView(withText("Profile updated successfully!"))
        //    .inRoot(ToastMatcher()) // ToastMatcher is a custom Espresso matcher
        //    .check(matches(isDisplayed()));
        // This is often tricky and might require custom root matchers or IdlingResources.
    }

    @Test
    fun removeLanguage_updatesProfile() {
        // Initial state: "English" chip from mocked load
        onView(allOf(withText("English"), isDescendantOfA(withId(R.id.languages_chip_group))))
            .check(matches(isDisplayed()))

        // Click the close icon on the "English" chip
        onView(allOf(isDescendantOfA(withText("English")), withClassName(containsString("Chip"))))
            .onChildView(withId(com.google.android.material.R.id.chip_close_icon)) // This ID is internal, might be fragile
            .perform(click())

        // Verify "English" chip is gone
        onView(withText("English")).check(doesNotExist())

        // Click save
        every { mockUserRepository.setUserProfile(eq(testUid), any(), any()) } answers {
            val profileMap = secondArg<Map<String, Any>>()
            val languages = profileMap["languages"] as? List<String>
            assertNotNull(languages)
            assertTrue(languages!!.isEmpty()) // Should be empty now

            thirdArg<(Boolean) -> Unit>().invoke(true)
        }
        onView(withId(R.id.save_profile_button)).perform(click())

        // Add toast verification if set up
    }
}
