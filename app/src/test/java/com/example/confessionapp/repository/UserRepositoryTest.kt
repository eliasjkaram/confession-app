package com.example.confessionapp.repository

import com.example.confessionapp.data.models.PriestUser
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

// Use RobolectricTestRunner for any Android specific classes if needed, though trying to avoid them for pure unit tests.
// If Log.d, etc. from Android are used in UserRepository, Robolectric might be needed or specific mocking for Log.
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk = [28]) // Basic config for Robolectric if Android SDK stubs are needed
class UserRepositoryTest {

    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockCollectionRef: CollectionReference
    private lateinit var mockQuery: Query
    private lateinit var userRepository: UserRepository

    // Task implementation for mocking Firebase calls
    private class MockTask<TResult>(private val result: TResult?, private val exception: Exception?) : Task<TResult>() {
        private var successListener: OnSuccessListener<in TResult>? = null
        private var failureListener: OnFailureListener? = null
        private var complete = false

        override fun isComplete(): Boolean = complete
        override fun isSuccessful(): Boolean = exception == null && complete
        override fun isCanceled(): Boolean = false
        override fun getResult(): TResult = if (exception == null) result!! else throw RuntimeException("Task failed", exception)
        override fun <X : Throwable?> getResult(p0: Class<X>): TResult = result!! // Simplified
        override fun getException(): Exception? = exception

        override fun addOnSuccessListener(p0: Executor, p1: OnSuccessListener<in TResult>): Task<TResult> {
            this.successListener = p1
            // Simulate async completion for testing
            if (exception == null) {
                mockkStatic(android.os.Looper::class)
                every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

                // Directly invoke listener for simplicity in test
                // In a real scenario, you might use a test executor
                 p0.execute { successListener?.onSuccess(result) }
                 complete = true
            }
            return this
        }
         override fun addOnSuccessListener(p1: OnSuccessListener<in TResult>): Task<TResult> {
            this.successListener = p1
            // Simulate async completion for testing
            if (exception == null) {
                 successListener?.onSuccess(result)
                 complete = true
            }
            return this
        }

        override fun addOnFailureListener(p0: Executor, p1: OnFailureListener): Task<TResult> {
            this.failureListener = p1
            if (exception != null) {
                 p0.execute { failureListener?.onFailure(exception) }
                 complete = true
            }
            return this
        }
         override fun addOnFailureListener(p1: OnFailureListener): Task<TResult> {
            this.failureListener = p1
            if (exception != null) {
                 failureListener?.onFailure(exception)
                 complete = true
            }
            return this
        }
    }


    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true) // Initialize MockK
        mockFirestore = mockk()
        mockCollectionRef = mockk()
        mockQuery = mockk()

        every { mockFirestore.collection("users") } returns mockCollectionRef
        every { mockCollectionRef.whereEqualTo(any<String>(), any()) } returns mockQuery
        every { mockQuery.whereArrayContains(any<String>(), any()) } returns mockQuery // For language filter

        userRepository = UserRepository(mockFirestore)

        // Mock Log calls to prevent Android SDK dependency issues in pure JVM tests if not using Robolectric
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll() // Clear mocks after each test
    }

    @Test
    fun `getVerifiedPriests returns list of priests on success`() {
        val mockDocument1 = mockk<DocumentSnapshot>()
        every { mockDocument1.id } returns "priest1"
        every { mockDocument1.getString("name") } returns "Father John"
        every { mockDocument1.getString("email") } returns "john@example.com"
        every { mockDocument1.getString("photoUrl") } returns null
        every { mockDocument1.get("languages") } returns listOf("English", "Spanish")
        every { mockDocument1.getBoolean("isPriestVerified") } returns true

        val mockDocument2 = mockk<DocumentSnapshot>()
        every { mockDocument2.id } returns "priest2"
        every { mockDocument2.getString("name") } returns "Father Mike"
        every { mockDocument2.getString("email") } returns "mike@example.com"
        every { mockDocument2.getString("photoUrl") } returns "url"
        every { mockDocument2.get("languages") } returns listOf("English")
        every { mockDocument2.getBoolean("isPriestVerified") } returns true

        val mockDocument3NotVerified = mockk<DocumentSnapshot>() // Not verified
        every { mockDocument3NotVerified.id } returns "user3"
        every { mockDocument3NotVerified.getString("name") } returns "User NotAPriest"
        every { mockDocument3NotVerified.getBoolean("isPriestVerified") } returns false


        val mockQuerySnapshot = mockk<QuerySnapshot>()
        every { mockQuerySnapshot.documents } returns listOf(mockDocument1, mockDocument2, mockDocument3NotVerified)

        val mockTask = MockTask<QuerySnapshot>(mockQuerySnapshot, null)
        every { mockQuery.get() } returns mockTask

        var resultList: List<PriestUser>? = null
        userRepository.getVerifiedPriests { result ->
            result.onSuccess { priests ->
                resultList = priests
            }
        }

        assertNotNull("Result list should not be null", resultList)
        assertEquals("Should return 2 verified priests", 2, resultList?.size)
        assertEquals("Priest1 name mismatch", "Father John", resultList?.get(0)?.name)
        assertEquals("Priest2 UID mismatch", "priest2", resultList?.get(1)?.uid)
        assertTrue("Priest2 should have English language", resultList?.get(1)?.languages?.contains("English") == true)
    }

    @Test
    fun `getVerifiedPriests with language filter returns filtered list`() {
        val mockDocument1 = mockk<DocumentSnapshot>()
        every { mockDocument1.id } returns "priest1"
        every { mockDocument1.getString("name") } returns "Father Spanish"
        every { mockDocument1.get("languages") } returns listOf("Spanish")
        every { mockDocument1.getBoolean("isPriestVerified") } returns true

        val mockDocument2 = mockk<DocumentSnapshot>() // English only
        every { mockDocument2.id } returns "priest2"
        every { mockDocument2.getString("name") } returns "Father English"
        every { mockDocument2.get("languages") } returns listOf("English")
        every { mockDocument2.getBoolean("isPriestVerified") } returns true

        val mockQuerySnapshot = mockk<QuerySnapshot>()
        every { mockQuerySnapshot.documents } returns listOf(mockDocument1, mockDocument2)

        val mockTask = MockTask<QuerySnapshot>(mockQuerySnapshot, null)
        every { mockQuery.get() } returns mockTask


        var resultList: List<PriestUser>? = null
        userRepository.getVerifiedPriests("Spanish") { result -> // Filter by Spanish
            result.onSuccess { priests -> resultList = priests }
        }

        assertNotNull(resultList)
        assertEquals("Should return 1 priest speaking Spanish", 1, resultList?.size)
        assertEquals("Priest name mismatch", "Father Spanish", resultList?.get(0)?.name)

        verify { mockCollectionRef.whereEqualTo("isPriestVerified", true) } // Base query
        verify { mockQuery.whereArrayContains("languages", "Spanish") } // Language filter
    }


    @Test
    fun `getVerifiedPriests returns failure on Firestore error`() {
        val exception = Exception("Firestore error")
        val mockTask = MockTask<QuerySnapshot>(null, exception)
        every { mockQuery.get() } returns mockTask

        var resultError: Throwable? = null
        userRepository.getVerifiedPriests { result ->
            result.onFailure { error ->
                resultError = error
            }
        }
        assertNotNull("Error should not be null", resultError)
        assertEquals("Error message mismatch", "Firestore error", resultError?.message)
    }

    @Test
    fun `getVerifiedPriests handles document parsing error gracefully`() {
        val mockDocumentCorrect = mockk<DocumentSnapshot>()
        every { mockDocumentCorrect.id } returns "priestCorrect"
        every { mockDocumentCorrect.getString("name") } returns "Father Correct"
        every { mockDocumentCorrect.get("languages") } returns listOf("English")
        every { mockDocumentCorrect.getBoolean("isPriestVerified") } returns true

        val mockDocumentBroken = mockk<DocumentSnapshot>() // This one will cause a parsing error
        every { mockDocumentBroken.id } returns "priestBroken"
        every { mockDocumentBroken.getString("name") } returns "Father Broken"
        // Intentionally make languages a wrong type to simulate parsing error
        every { mockDocumentBroken.get("languages") } returns "not_a_list"
        every { mockDocumentBroken.getBoolean("isPriestVerified") } returns true


        val mockQuerySnapshot = mockk<QuerySnapshot>()
        every { mockQuerySnapshot.documents } returns listOf(mockDocumentCorrect, mockDocumentBroken)

        val mockTask = MockTask<QuerySnapshot>(mockQuerySnapshot, null)
        every { mockQuery.get() } returns mockTask

        var resultList: List<PriestUser>? = null
        userRepository.getVerifiedPriests { result ->
            result.onSuccess { priests -> resultList = priests }
        }

        assertNotNull(resultList)
        assertEquals("Should return only 1 correctly parsed priest", 1, resultList?.size)
        assertEquals("priestCorrect", resultList?.get(0)?.uid)
        verify { Log.e("UserRepository", "Error parsing priest document priestBroken", any()) }
    }
}
