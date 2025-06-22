package com.example.confessionapp.repository

import com.example.confessionapp.data.models.PriestUser
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Roboelectric might be needed if any Android SDK components are indirectly touched
// For pure Kotlin/Java with Mockk, it might not be strictly necessary,
// but Firestore interactions can sometimes pull in Android context.
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk = [Config.OLDEST_SDK]) // Opt: Sdk version if needed
class UserRepositoryTest {

    private lateinit var firestoreMock: FirebaseFirestore
    private lateinit var collectionReferenceMock: CollectionReference
    private lateinit var queryMock: Query
    private lateinit var documentSnapshotMock: DocumentSnapshot
    private lateinit var querySnapshotMock: QuerySnapshot
    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        // MockK setup
        MockKAnnotations.init(this, relaxUnitFun = true) // relaxUnitFun for void methods

        firestoreMock = mockk()
        collectionReferenceMock = mockk()
        queryMock = mockk(relaxed = true) // relaxed = true to provide default answers for all methods
        documentSnapshotMock = mockk()
        querySnapshotMock = mockk()

        // Basic mocking structure
        every { firestoreMock.collection("users") } returns collectionReferenceMock
        every { collectionReferenceMock.whereEqualTo(any<String>(), any()) } returns queryMock
        every { queryMock.whereEqualTo(any<String>(), any()) } returns queryMock // For isPriestVerified
        every { queryMock.whereArrayContains(any<String>(), any()) } returns queryMock // For languages
        every { queryMock.get() } returns mockTask(querySnapshotMock) // Needs a Task mock

        userRepository = UserRepository(firestoreMock)
    }

    // Helper to mock Firebase Task
    private fun <T> mockTask(result: T?, exception: Exception? = null): Task<T> {
        val task: Task<T> = mockk(relaxed = true)
        every { task.isComplete } returns true
        every { task.isCanceled } returns false
        every { task.isSuccessful } returns (exception == null)
        every { task.result } returns result
        every { task.exception } returns exception

        // Mock addOnSuccessListener and addOnFailureListener
        every { task.addOnSuccessListener(any()) } answers {
            if (exception == null) {
                firstArg<OnSuccessListener<T>>().onSuccess(result)
            }
            task // Return task for chaining
        }
        every { task.addOnFailureListener(any()) } answers {
            if (exception != null) {
                firstArg<OnFailureListener>().onFailure(exception)
            }
            task // Return task for chaining
        }
        return task
    }


    @Test
    fun `getVerifiedPriests no language filter returns all verified priests`() {
        val priest1Doc = mockPriestDocument("uid1", "Priest One", listOf("English"), true)
        val priest2Doc = mockPriestDocument("uid2", "Priest Two", listOf("Spanish"), true)
        val userDoc = mockPriestDocument("uid3", "User Three", emptyList(), false) // Not a priest

        every { querySnapshotMock.documents } returns listOf(priest1Doc, priest2Doc, userDoc)
        // Since the query itself filters by isPriestVerified=true, Firestore would ideally only return priest1Doc and priest2Doc
        // For this test, let's assume the query `whereEqualTo("isPriestVerified", true)` is effective.
        // So, we only provide documents that would match that part of the query.
        every { queryMock.get() } returns mockTask(querySnapshotMock)
        every { querySnapshotMock.documents } returns listOf(priest1Doc, priest2Doc)


        var resultPriests: List<PriestUser>? = null
        userRepository.getVerifiedPriests(null) { result ->
            result.onSuccess { priests ->
                resultPriests = priests
            }
        }

        assertNotNull(resultPriests)
        assertEquals(2, resultPriests?.size)
        assertTrue(resultPriests?.any { it.uid == "uid1" && it.name == "Priest One" } ?: false)
        assertTrue(resultPriests?.any { it.uid == "uid2" && it.name == "Priest Two" } ?: false)

        verify { queryMock.whereEqualTo("isPriestVerified", true) } // Verifies this part of query was built
        verify(exactly = 0) { queryMock.whereArrayContains(any(), any()) } // No language filter
    }

    @Test
    fun `getVerifiedPriests with language filter returns matching verified priests`() {
        val priest1Doc = mockPriestDocument("uid1", "Priest English", listOf("English", "Latin"), true)
        val priest2Doc = mockPriestDocument("uid2", "Priest Spanish", listOf("Spanish"), true)
        val priest3Doc = mockPriestDocument("uid3", "Priest English 2", listOf("English"), true)


        // Simulate Firestore returning only priests matching the language filter and isPriestVerified
        every { querySnapshotMock.documents } returns listOf(priest1Doc, priest3Doc)
        every { queryMock.get() } returns mockTask(querySnapshotMock)


        var resultPriests: List<PriestUser>? = null
        userRepository.getVerifiedPriests("English") { result ->
            result.onSuccess { priests -> resultPriests = priests }
        }

        assertNotNull(resultPriests)
        assertEquals(2, resultPriests?.size)
        assertTrue(resultPriests?.all { it.languages.contains("English") } ?: false)
        assertTrue(resultPriests?.any { it.uid == "uid1" } ?: false)
        assertTrue(resultPriests?.any { it.uid == "uid3" } ?: false)

        verify { queryMock.whereEqualTo("isPriestVerified", true) }
        verify { queryMock.whereArrayContains("languages", "English") }
    }

    @Test
    fun `getVerifiedPriests returns empty list when no priests match language`() {
        val priest1Doc = mockPriestDocument("uid1", "Priest English", listOf("English"), true)

        every { querySnapshotMock.documents } returns emptyList() // Firestore found no docs for "Spanish"
        every { queryMock.get() } returns mockTask(querySnapshotMock)

        var resultPriests: List<PriestUser>? = null
        userRepository.getVerifiedPriests("Spanish") { result ->
            result.onSuccess { priests -> resultPriests = priests }
        }

        assertNotNull(resultPriests)
        assertTrue(resultPriests?.isEmpty() ?: false)
        verify { queryMock.whereArrayContains("languages", "Spanish") }
    }

    @Test
    fun `getVerifiedPriests returns failure on Firestore error`() {
        val exception = FirebaseFirestoreException("Test error", FirebaseFirestoreException.Code.UNAVAILABLE)
        every { queryMock.get() } returns mockTask(null, exception)

        var capturedException: Exception? = null
        userRepository.getVerifiedPriests(null) { result ->
            result.onFailure { ex -> capturedException = ex }
        }

        assertNotNull(capturedException)
        assertTrue(capturedException is FirebaseFirestoreException)
        assertEquals("Test error", capturedException?.message)
    }


    private fun mockPriestDocument(uid: String, name: String, languages: List<String>, isVerified: Boolean): DocumentSnapshot {
        val docMock = mockk<DocumentSnapshot>()
        every { docMock.id } returns uid
        every { docMock.getString("name") } returns name
        every { docMock.getString("email") } returns "$name@example.com" // Dummy email
        every { docMock.getString("photoUrl") } returns null
        every { docMock.get("languages") } returns languages
        every { docMock.getBoolean("isPriestVerified") } returns isVerified
        return docMock
    }

    @After
    fun tearDown() {
        unmockkAll() // Clean up mocks
    }
}
