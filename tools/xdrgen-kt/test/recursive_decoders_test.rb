# frozen_string_literal: true

require 'minitest/autorun'
require 'set'
require 'xdrgen'
require_relative '../lib/xdrgen/generators/kotlin'

class RecursiveDecodersTest < Minitest::Test
  def test_cycles_have_balanced_decoder_guards
    directory = File.expand_path('../../../stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr', __dir__)
    bodies = Dir.glob("#{directory}/*.kt").to_h do |path|
      [File.basename(path, '.kt'), File.read(path)[/^    fun decode\(reader: XdrReader\).*?^    \}/m].to_s]
    end
    graph = bodies.transform_values { |body| body.scan(/\b(\w+Xdr)\.decode\(reader\)/).flatten.uniq }
    recursive = graph.keys.select do |origin|
      pending = graph.fetch(origin).dup
      visited = Set.new
      until pending.empty?
        type = pending.pop
        break if type == origin
        pending.concat(graph.fetch(type, [])) if visited.add?(type)
      end
      type == origin
    end.to_set - Set['SCVecXdr', 'SCMapXdr']
    assert_equal recursive, Xdrgen::Generators::Kotlin::RECURSIVE_TYPES.to_set
    bodies.each do |type, body|
      expected = recursive.include?(type) ? 1 : 0
      assert_equal expected, body.scan('reader.enterRecursion(').length, type
      assert_equal expected, body.scan('reader.exitRecursion()').length, type
      next if expected.zero?
      cap = type == 'SorobanDelegateSignatureXdr' ? 129 : 500
      assert_match(/\A[^\n]+\n      reader.enterRecursion\(#{cap}\)\n      try \{/, body, type)
      assert_match(/\} finally \{\n        reader.exitRecursion\(\)\n      \}\n    \}\z/, body, type)
    end
  end
end
