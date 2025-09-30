// Import styles
import '../css/styles.css';

// Import libsodium
import sodium from 'libsodium-wrappers';

// Import the Stellar SDK
// Note: This assumes the SDK is built and available
// In production, this would be: import { KeyPair } from '@stellar/kmp-sdk';
const stellarSdk = require('../../../stellar-sdk/build/dist/js/productionLibrary/kmp-stellar-sdk-stellar-sdk.js');

// Global reference to SDK
let KeyPair = null;
let sdkReady = false;

// Initialize SDK
async function initSDK() {
    try {
        // Initialize libsodium first
        await sodium.ready;

        // Make sodium available globally for the Kotlin/JS code
        window._sodium = sodium;

        console.log('libsodium initialized');

        // Wait for SDK to be ready
        await new Promise(resolve => {
            if (stellarSdk && stellarSdk.com && stellarSdk.com.stellar && stellarSdk.com.stellar.sdk) {
                KeyPair = stellarSdk.com.stellar.sdk.KeyPair;
                resolve();
            } else {
                // Poll for SDK availability
                const checkInterval = setInterval(() => {
                    if (stellarSdk && stellarSdk.com && stellarSdk.com.stellar && stellarSdk.com.stellar.sdk) {
                        KeyPair = stellarSdk.com.stellar.sdk.KeyPair;
                        clearInterval(checkInterval);
                        resolve();
                    }
                }, 100);
            }
        });

        sdkReady = true;

        // Update crypto library name
        const cryptoLibElement = document.getElementById('crypto-lib');
        if (cryptoLibElement && KeyPair && KeyPair.Companion) {
            cryptoLibElement.textContent = KeyPair.Companion.getCryptoLibraryName();
        }

        // Hide loading overlay
        const loadingOverlay = document.getElementById('loading-overlay');
        if (loadingOverlay) {
            loadingOverlay.style.display = 'none';
        }

        console.log('Stellar SDK initialized successfully');
    } catch (error) {
        console.error('Failed to initialize SDK:', error);
        alert('Failed to initialize Stellar SDK. Please refresh the page.');
    }
}

// Generate keypair
function generateKeypair() {
    if (!sdkReady || !KeyPair) {
        alert('SDK not ready yet');
        return;
    }

    try {
        const keypair = KeyPair.Companion.random();
        const accountId = keypair.getAccountId();
        const secretSeed = new TextDecoder().decode(new Uint8Array(keypair.getSecretSeed()));

        // Display results
        const resultBox = document.getElementById('keypair-result');
        const accountIdElement = document.querySelector('#account-id code');
        const secretSeedElement = document.querySelector('#secret-seed code');

        if (accountIdElement) accountIdElement.textContent = accountId;
        if (secretSeedElement) secretSeedElement.textContent = secretSeed;
        if (resultBox) resultBox.classList.remove('hidden');
    } catch (error) {
        console.error('Error generating keypair:', error);
        alert('Error generating keypair: ' + error.message);
    }
}

// Copy to clipboard
function copyToClipboard(elementId) {
    const element = document.querySelector(`#${elementId} code`);
    if (!element) return;

    const text = element.textContent;
    navigator.clipboard.writeText(text).then(() => {
        const btn = document.querySelector(`[data-copy="${elementId}"]`);
        if (btn) {
            const originalText = btn.textContent;
            btn.textContent = 'Copied!';
            btn.classList.add('copied');
            setTimeout(() => {
                btn.textContent = originalText;
                btn.classList.remove('copied');
            }, 2000);
        }
    }).catch(err => {
        console.error('Failed to copy:', err);
    });
}

// Run comprehensive tests
async function runTests() {
    if (!sdkReady || !KeyPair) {
        alert('SDK not ready yet');
        return;
    }

    const resultsContainer = document.getElementById('test-results');
    if (!resultsContainer) return;

    resultsContainer.innerHTML = '';
    resultsContainer.classList.remove('hidden');

    const tests = [];

    // Test 1: Random KeyPair Generation
    tests.push(await runTest('Random KeyPair Generation', () => {
        const kp1 = KeyPair.Companion.random();
        const kp2 = KeyPair.Companion.random();
        if (kp1.getAccountId() === kp2.getAccountId()) {
            throw new Error('Keypairs should be unique');
        }
        if (!kp1.canSign()) {
            throw new Error('Generated keypair should be able to sign');
        }
        return 'Generated unique keypairs successfully';
    }));

    // Test 2: KeyPair from Secret Seed
    tests.push(await runTest('KeyPair from Secret Seed', () => {
        const seed = 'SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE';
        const expected = 'GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D';
        const keypair = KeyPair.Companion.fromSecretSeedString(seed);
        if (keypair.getAccountId() !== expected) {
            throw new Error('Account ID mismatch');
        }
        if (!keypair.canSign()) {
            throw new Error('Should be able to sign');
        }
        return 'Derived correct account ID from seed';
    }));

    // Test 3: KeyPair from Account ID
    tests.push(await runTest('KeyPair from Account ID', () => {
        const accountId = 'GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D';
        const keypair = KeyPair.Companion.fromAccountId(accountId);
        if (keypair.getAccountId() !== accountId) {
            throw new Error('Account ID mismatch');
        }
        if (keypair.canSign()) {
            throw new Error('Public-only keypair should not be able to sign');
        }
        return 'Created public-only keypair successfully';
    }));

    // Test 4: Sign and Verify
    tests.push(await runTest('Sign and Verify', () => {
        const keypair = KeyPair.Companion.random();
        const message = new TextEncoder().encode('Hello Stellar');
        const messageArray = Array.from(message);
        const signature = keypair.sign(messageArray);
        if (!signature || signature.length !== 64) {
            throw new Error('Signature should be 64 bytes');
        }
        if (!keypair.verify(messageArray, signature)) {
            throw new Error('Signature verification failed');
        }
        return 'Signature created and verified successfully';
    }));

    // Test 5: Invalid Secret Seed
    tests.push(await runTest('Invalid Secret Seed', () => {
        try {
            KeyPair.Companion.fromSecretSeedString('INVALID_SEED');
            throw new Error('Should have thrown exception');
        } catch (e) {
            if (e.message === 'Should have thrown exception') throw e;
            return 'Correctly rejected invalid seed';
        }
    }));

    // Test 6: Invalid Account ID
    tests.push(await runTest('Invalid Account ID', () => {
        try {
            KeyPair.Companion.fromAccountId('GINVALID');
            throw new Error('Should have thrown exception');
        } catch (e) {
            if (e.message === 'Should have thrown exception') throw e;
            return 'Correctly rejected invalid account ID';
        }
    }));

    // Test 7: Memory Safety Test
    tests.push(await runTest('Memory Safety (100 keypairs)', () => {
        const keypairs = [];
        for (let i = 0; i < 100; i++) {
            keypairs.push(KeyPair.Companion.random());
        }
        const uniqueAccounts = new Set(keypairs.map(kp => kp.getAccountId()));
        if (uniqueAccounts.size !== 100) {
            throw new Error('All keypairs should be unique');
        }
        return 'Generated 100 unique keypairs successfully';
    }));

    // Test 8: Crypto Library Info
    tests.push(await runTest('Crypto Library Info', () => {
        const libName = KeyPair.Companion.getCryptoLibraryName();
        if (!libName || libName.length === 0) {
            throw new Error('Library name should not be empty');
        }
        return `Using: ${libName}`;
    }));

    // Display summary
    const passedCount = tests.filter(t => t.passed).length;
    const totalCount = tests.length;

    const summaryDiv = document.createElement('div');
    summaryDiv.className = `test-summary ${passedCount === totalCount ? 'success' : 'failure'}`;
    summaryDiv.textContent = `Results: ${passedCount}/${totalCount} tests passed`;
    resultsContainer.appendChild(summaryDiv);

    // Display individual test results
    tests.forEach(test => {
        const testDiv = document.createElement('div');
        testDiv.className = `test-item ${test.passed ? 'pass' : 'fail'}`;

        testDiv.innerHTML = `
            <div class="test-header">
                <span class="test-name">${test.name}</span>
                <span class="test-status ${test.passed ? 'pass' : 'fail'}">
                    ${test.passed ? '✓ PASS' : '✗ FAIL'}
                </span>
            </div>
            <div class="test-message">${test.message}</div>
            <div class="test-duration">Time: ${test.duration}ms</div>
        `;

        resultsContainer.appendChild(testDiv);
    });
}

async function runTest(name, testFn) {
    const startTime = performance.now();
    try {
        const message = testFn();
        const duration = Math.round(performance.now() - startTime);
        return { name, passed: true, message, duration };
    } catch (error) {
        const duration = Math.round(performance.now() - startTime);
        return { name, passed: false, message: `Error: ${error.message}`, duration };
    }
}

// Event listeners
document.addEventListener('DOMContentLoaded', () => {
    // Initialize SDK
    initSDK();

    // Generate keypair button
    const generateBtn = document.getElementById('generate-btn');
    if (generateBtn) {
        generateBtn.addEventListener('click', generateKeypair);
    }

    // Copy buttons
    document.querySelectorAll('.copy-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const elementId = e.target.getAttribute('data-copy');
            if (elementId) copyToClipboard(elementId);
        });
    });

    // Run tests button
    const runTestsBtn = document.getElementById('run-tests-btn');
    if (runTestsBtn) {
        runTestsBtn.addEventListener('click', runTests);
    }
});
