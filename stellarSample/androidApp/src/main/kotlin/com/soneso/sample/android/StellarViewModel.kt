package com.soneso.sample.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soneso.sample.KeyPairInfo
import com.soneso.sample.SorobanDemo
import com.soneso.sample.SorobanDemoResult
import com.soneso.sample.StellarDemo
import com.soneso.sample.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StellarViewModel : ViewModel() {
    private val demo = StellarDemo()
    private val sorobanDemo = SorobanDemo()

    private val _keypair = MutableStateFlow<KeyPairInfo?>(null)
    val keypair: StateFlow<KeyPairInfo?> = _keypair.asStateFlow()

    private val _testResults = MutableStateFlow<List<TestResult>>(emptyList())
    val testResults: StateFlow<List<TestResult>> = _testResults.asStateFlow()

    private val _isRunningTests = MutableStateFlow(false)
    val isRunningTests: StateFlow<Boolean> = _isRunningTests.asStateFlow()

    private val _sorobanResult = MutableStateFlow<SorobanDemoResult?>(null)
    val sorobanResult: StateFlow<SorobanDemoResult?> = _sorobanResult.asStateFlow()

    private val _isRunningSoroban = MutableStateFlow(false)
    val isRunningSoroban: StateFlow<Boolean> = _isRunningSoroban.asStateFlow()

    fun generateRandom() {
        viewModelScope.launch(Dispatchers.Default) {
            _keypair.value = demo.generateRandomKeyPair()
        }
    }

    fun generateFromSeed() {
        viewModelScope.launch(Dispatchers.Default) {
            val testSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
            val result = demo.createFromSeed(testSeed)
            if (result.isSuccess) {
                _keypair.value = result.getOrNull()
            }
        }
    }

    fun runTests() {
        viewModelScope.launch(Dispatchers.Default) {
            _isRunningTests.value = true
            _testResults.value = demo.runTestSuite()
            _isRunningTests.value = false
        }
    }

    fun runSorobanNetworkInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRunningSoroban.value = true
            _sorobanResult.value = sorobanDemo.demonstrateNetworkInfo()
            _isRunningSoroban.value = false
        }
    }

    fun runSorobanSimulation(inputName: String = "World") {
        viewModelScope.launch(Dispatchers.IO) {
            _isRunningSoroban.value = true
            _sorobanResult.value = sorobanDemo.demonstrateSimulation(inputName)
            _isRunningSoroban.value = false
        }
    }

    fun runSorobanFullFlow(inputName: String = "Stellar") {
        viewModelScope.launch(Dispatchers.IO) {
            _isRunningSoroban.value = true
            _sorobanResult.value = sorobanDemo.demonstrateFullInvocationFlow(inputName)
            _isRunningSoroban.value = false
        }
    }

    fun runSorobanEventQuery() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRunningSoroban.value = true
            _sorobanResult.value = sorobanDemo.demonstrateEventQuery()
            _isRunningSoroban.value = false
        }
    }
}
