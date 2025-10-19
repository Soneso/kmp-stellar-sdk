import shared

// MARK: - Kotlin Interop Extensions

extension KeyPair {
    /// Convert Kotlin CharArray to Swift String
    /// The secret seed is kept as CharArray in the SDK for better security,
    /// so we only convert it to String in the UI layer when needed for display.
    func getSecretSeedAsString() -> String? {
        guard let charArray = getSecretSeed() else { return nil }
        var characters: [Character] = []
        for i in 0..<charArray.size {
            let unicodeValue = UInt16(charArray.get(index: i))
            if let scalar = UnicodeScalar(unicodeValue) {
                characters.append(Character(scalar))
            }
        }
        return String(characters)
    }
}
