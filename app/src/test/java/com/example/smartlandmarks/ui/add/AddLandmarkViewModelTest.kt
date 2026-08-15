package com.example.smartlandmarks.ui.add

import com.example.smartlandmarks.fake.FakeLandmarkRepository
import com.example.smartlandmarks.services.LocationProvider
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Validation is where a lab submission most often loses marks silently — a landmark
 * posted with a typo'd latitude is accepted by the server and then sits at the wrong
 * place on the map forever. These tests pin that behaviour down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddLandmarkViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeLandmarkRepository
    private lateinit var viewModel: AddLandmarkViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeLandmarkRepository()
        viewModel = AddLandmarkViewModel(repository, mockk<LocationProvider>(relaxed = true))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `blank title is rejected and nothing is sent`() = runTest {
        viewModel.onTitleChanged("")
        viewModel.onLatitudeChanged("23.7")
        viewModel.onLongitudeChanged("90.4")

        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.titleError)
        assertEquals(0, repository.createCallCount)
    }

    @Test
    fun `latitude outside the valid range is rejected`() = runTest {
        viewModel.onTitleChanged("Lalbagh Fort")
        viewModel.onLatitudeChanged("233.7")
        viewModel.onLongitudeChanged("90.4")

        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.latitudeError)
        assertEquals(0, repository.createCallCount)
    }

    @Test
    fun `longitude outside the valid range is rejected`() = runTest {
        viewModel.onTitleChanged("Lalbagh Fort")
        viewModel.onLatitudeChanged("23.7")
        viewModel.onLongitudeChanged("-900.4")

        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.longitudeError)
        assertEquals(0, repository.createCallCount)
    }

    @Test
    fun `non-numeric coordinates are rejected`() = runTest {
        viewModel.onTitleChanged("Lalbagh Fort")
        viewModel.onLatitudeChanged("north")
        viewModel.onLongitudeChanged("east")

        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.latitudeError)
        assertNotNull(viewModel.uiState.value.longitudeError)
    }

    @Test
    fun `valid input is submitted and the form resets`() = runTest {
        viewModel.onTitleChanged("Lalbagh Fort")
        viewModel.onLatitudeChanged("23.7192")
        viewModel.onLongitudeChanged("90.3882")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.createCallCount)
        assertEquals("Lalbagh Fort", repository.lastCreatedTitle)
        assertEquals("", viewModel.uiState.value.title)
        assertNull(viewModel.uiState.value.titleError)
    }

    @Test
    fun `editing a field clears its previous error`() = runTest {
        viewModel.submit()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.titleError)

        viewModel.onTitleChanged("Ahsan Manzil")
        assertNull(viewModel.uiState.value.titleError)
    }
}
