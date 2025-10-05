module.exports = function(config) {
    // Load base config from Kotlin/JS
    const kotlinConfigPath = './build/js/packages/kmp-stellar-sdk-stellar-sdk-test/karma.conf.js';

    // Check if the Kotlin config exists, if so load it
    try {
        const kotlinConfig = require(kotlinConfigPath);
        kotlinConfig(config);
    } catch (e) {
        // If Kotlin config doesn't exist yet, use basic config
        config.set({
            frameworks: ['mocha'],
            browsers: ['ChromeHeadless'],
            singleRun: true
        });
    }

    // Add our setup file to the beginning of files list
    const existingFiles = config.files || [];
    const setupFile = 'src/jsTest/resources/karma-setup.js';

    // Remove setup file if it's already in the list (to avoid duplicates)
    const filteredFiles = existingFiles.filter(f =>
        typeof f === 'string' ? f !== setupFile : f.pattern !== setupFile
    );

    // Add setup file at the beginning
    filteredFiles.unshift(setupFile);

    // Also serve libsodium from node_modules
    filteredFiles.push({
        pattern: 'node_modules/libsodium-wrappers/dist/browsers/sodium.js',
        included: false,
        served: true,
        watched: false
    });

    config.set({
        files: filteredFiles,
        client: {
            mocha: {
                timeout: 10000 // 10 seconds for async libsodium initialization
            }
        }
    });

    console.log('✓ Karma configured with libsodium setup');
};
