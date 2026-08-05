#!/usr/bin/env ruby
# frozen_string_literal: true

require 'bundler/setup'
require 'xdrgen'
require_relative 'lib/xdrgen/generators/kotlin'

# Parse command line arguments.
# If no arguments are given, default to xdr/*.x (downloaded by download-xdr.sh).
input_files = ARGV
if input_files.empty?
  default_dir = File.expand_path('xdr', __dir__)
  input_files = Dir.glob(File.join(default_dir, '*.x')).sort
  if input_files.empty?
    puts "No .x files found in #{default_dir}."
    puts "Run ./download-xdr.sh first, or pass file paths explicitly."
    puts ""
    puts "Usage: #{$PROGRAM_NAME} [<input.x> ...]"
    puts "Example: #{$PROGRAM_NAME} /path/to/stellar-xdr/Stellar-*.x"
    exit 1
  end
  puts "Using .x files from #{default_dir}"
end

# Files to exclude from generation
# Note: Stellar-SCP.x CANNOT be excluded as it's referenced by Stellar-ledger.x (LedgerSCPMessages uses SCPEnvelope)
# Only excluding files with truly internal types not needed for SDK usage:
# - Stellar-exporter.x: LedgerCloseMetaBatch (batch export format)
# - Stellar-internal.x: StoredTransactionSet, PersistedSCPState (Core internal storage)
# - Stellar-overlay.x: Network protocol messages (Hello, Auth, PeerAddress, etc.)
excluded_files = [
  'Stellar-exporter.x',
  'Stellar-internal.x',
  'Stellar-overlay.x'
]

# Filter out excluded files
filtered_files = input_files.reject do |file|
  basename = File.basename(file)
  excluded = excluded_files.include?(basename)
  if excluded
    puts "Excluding: #{basename}"
  end
  excluded
end

if filtered_files.empty?
  puts "Error: All input files were excluded. No files to process."
  exit 1
end

puts "Processing #{filtered_files.length} files:"
filtered_files.each { |f| puts "  - #{File.basename(f)}" }
puts ""

# Output to SDK source directory
output_dir = File.expand_path('../../stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr', __dir__)

# Generate Kotlin code
Xdrgen::Compilation.new(
  filtered_files,
  output_dir: output_dir,
  generator: Xdrgen::Generators::Kotlin,
  namespace: 'com.soneso.stellar.sdk.xdr',
  options: { verify_json_overrides: true }
).compile

puts ""
puts "Kotlin XDR types generated in #{output_dir}"
puts "Generated from #{filtered_files.length} XDR files"