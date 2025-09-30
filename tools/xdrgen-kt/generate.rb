#!/usr/bin/env ruby
# frozen_string_literal: true

require 'bundler/setup'
require 'xdrgen'
require_relative 'lib/xdrgen/generators/kotlin'

# Parse command line arguments
input_files = ARGV
if input_files.empty?
  puts "Usage: #{$PROGRAM_NAME} <input.x> [<input2.x> ...]"
  puts "Example: #{$PROGRAM_NAME} /path/to/stellar-xdr/Stellar-*.x"
  exit 1
end

# Output to SDK source directory
output_dir = File.expand_path('../../stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/xdr', __dir__)

# Generate Kotlin code
Xdrgen::Compilation.new(
  input_files,
  output_dir: output_dir,
  generator: Xdrgen::Generators::Kotlin,
  namespace: 'com.stellar.sdk.xdr',
  options: {}
).compile

puts "Kotlin XDR types generated in #{output_dir}"