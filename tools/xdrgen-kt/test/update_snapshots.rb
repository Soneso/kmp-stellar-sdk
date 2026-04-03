# frozen_string_literal: true

# Regenerates all snapshot files from test fixtures.
# Run this when intentional changes are made to the Kotlin generator.
#
# Usage: bundle exec ruby test/update_snapshots.rb

require 'fileutils'
require 'tmpdir'
require 'xdrgen'
require_relative '../lib/xdrgen/generators/kotlin'

SNAPSHOT_DIR = File.expand_path('snapshots', __dir__)
FIXTURE_DIR  = File.expand_path('fixtures', __dir__)

fixtures = Dir.glob(File.join(FIXTURE_DIR, '*.x')).sort
if fixtures.empty?
  warn "No .x fixture files found in #{FIXTURE_DIR}"
  exit 1
end

puts "Generating from #{fixtures.length} fixture files..."
fixtures.each { |f| puts "  - #{File.basename(f)}" }

output = Dir.mktmpdir('xdr_snapshots_')

Xdrgen::Compilation.new(
  fixtures,
  output_dir: output + '/',
  generator: Xdrgen::Generators::Kotlin,
  namespace: 'com.soneso.stellar.sdk.xdr',
  options: {}
).compile

# Clear existing snapshots
FileUtils.mkdir_p(SNAPSHOT_DIR)
Dir.glob(File.join(SNAPSHOT_DIR, '*.kt')).each { |f| File.delete(f) }

# Copy all generated files to snapshots
generated = Dir.glob(File.join(output, '*.kt')).sort
generated.each { |f| FileUtils.cp(f, SNAPSHOT_DIR) }

FileUtils.rm_rf(output)

puts ""
puts "Updated #{generated.length} snapshots in #{SNAPSHOT_DIR}:"
generated.each { |f| puts "  - #{File.basename(f)}" }
